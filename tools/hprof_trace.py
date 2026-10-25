"""hprof_trace.py — find what HOLDS sensitive words in an HPROF heap dump.

Record layouts (OpenJDK heapDumper.cpp, copied from hprof_io.c):
  STRING        id sid, utf8 text
  LOAD_CLASS    u4 serial, id cid, u4 serial, id name_sid
  CLASS_DUMP    id cid, u4 serial, id super, id loader, id signers, id domain,
                id res1, id res2, u4 instsize, u2 ncp, [u2 idx, u1 t, v]*,
                u2 nstatic, [id name, u1 t, v]*, u2 nfields, [id name, u1 t]*
  INSTANCE      id oid, u4 serial, id cid, u4 nbytes, bytes
  OBJ_ARRAY     id aid, u4 serial, u4 n, id classid, id[n]
  PRIM_ARRAY    id aid, u4 serial, u4 n, u1 type, values

Walks tracked ids upward: byte[] -> String -> ... -> real holder, printing
chains per word. Static class fields checked every layer.

Usage: python hprof_trace.py C:\\path\\dump.bin [--words file] [--depth N]
"""
import sys
import re
import struct
import time
from collections import Counter

DEFAULT_WORDS = [
    "AH Sniper", "Aura Guide", "Attack Delay", "Auto Cart", "Shard Hack",
    "Auto Eat", "Auto Mine", "Shard Trade", "Auto sprint", "Auto Tool",
    "Auto Totem", "Backtrack", "Click Simulation", "Compass", "Explode Delay",
    "Fast Bridge", "Health Indicators", "Hole ESP", "Mace Swap",
    "Netherite Finder", "No Jump Delay", "Panic Pay", "Place Delay",
    "Player ESP", "Purge", "Silent Aim", "Silent Swap", "Sky Art",
    "Shader ESP", "Storage ESP", "Swing Speed", "Switch Back", "Switch Delay",
    "Target HUD", "Totem Hit", "Vertical Speed",
]

TSIZE = {2: None, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
JDK_PFX = ("java.", "javax.", "jdk.", "sun.", "com.sun.",
           "[Ljava.", "[Ljdk.", "[Lsun.", "[Ljavax.")


def rd(f, n):
    b = f.read(n)
    if len(b) != n:
        raise EOFError(f"short read {len(b)}/{n} @0x{f.tell():x}")
    return b


def u2(f):
    return struct.unpack(">H", rd(f, 2))[0]


def u4(f):
    return struct.unpack(">I", rd(f, 4))[0]


def read_header(f):
    buf = bytearray()
    while True:
        c = f.read(1)
        if not c or c == b"\x00":
            break
        buf += c
    idsz = u4(f)
    rd(f, 8)
    return buf.decode("ascii", "replace"), idsz


def skip_val(f, t, idsz):
    if t == 2:
        f.read(idsz)
        return
    n = TSIZE.get(t)
    if n is None:
        raise RuntimeError(f"bad value type {t} @0x{f.tell():x}")
    f.read(n)


class Meta:
    __slots__ = ("super", "fields", "statics", "name")

    def __init__(self):
        self.super = None
        self.fields = []
        self.statics = []
        self.name = "?"


def parse_sub(f, end, idsz, h):
    while f.tell() < end:
        st = f.read(1)[0]
        if st == 0xFF:
            f.read(idsz)
        elif st == 0x01:
            f.read(idsz * 2)
        elif st in (0x02, 0x03):
            f.read(idsz + 8)
        elif st in (0x04, 0x06):
            f.read(idsz + 4)
        elif st in (0x05, 0x07):
            f.read(idsz)
        elif st == 0x08:
            f.read(idsz + 8)
        elif st in (0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x90):
            f.read(idsz)
        elif st == 0x8E:
            f.read(idsz + 8)
        elif st == 0xFE:
            f.read(4 + idsz)
        elif st == 0x20:
            h.cls_dump(f, idsz)
        elif st == 0x21:
            h.inst(f, idsz)
        elif st == 0x22:
            h.oarr(f, idsz)
        elif st == 0x23:
            h.parr(f, idsz)
        else:
            raise RuntimeError(f"bad subrecord 0x{st:02x} @0x{f.tell() - 1:x}")


def skip_cls_dump(f, idsz):
    f.read(idsz + 4 + idsz * 6 + 4)
    for _ in range(u2(f)):
        f.read(2)
        skip_val(f, f.read(1)[0], idsz)
    for _ in range(u2(f)):
        f.read(idsz)
        skip_val(f, f.read(1)[0], idsz)
    for _ in range(u2(f)):
        f.read(idsz + 1)


def top_walk(f, idsz, h):
    while True:
        hdr = f.read(9)
        if len(hdr) < 9:
            return
        tag = hdr[0]
        ln = struct.unpack(">I", hdr[5:9])[0]
        end = f.tell() + ln
        if tag == 0x01:
            sid = rd(f, idsz)
            text = f.read(ln - idsz)
            h.string(sid, text)
        elif tag == 0x02:
            body = rd(f, ln)
            cid = body[4:4 + idsz]
            nid = body[4 + idsz + 4:4 + idsz + 4 + idsz]
            h.load_class(cid, nid)
        elif tag in (0x0C, 0x1C):
            parse_sub(f, end, idsz, h)
            if f.tell() != end:
                raise RuntimeError(
                    f"segment desync: at {f.tell()} expected {end}")
        else:
            f.seek(end)


class Pass1:
    def __init__(self, words, strings, cls, class_names, targets, statics):
        self.pats = [(w, w.encode("latin-1"), w.encode("utf-16-be"))
                     for w in words]
        self.strings = strings
        self.cls = cls
        self.class_names = class_names
        self.targets = targets
        self.statics = statics
        self.cp_hits = []

    def string(self, sid, text):
        self.strings[sid] = text
        for w, p, _ in self.pats:
            if p == text:
                self.cp_hits.append(w)
                break

    def load_class(self, cid, nid):
        self.class_names[cid] = nid

    def cls_dump(self, f, idsz):
        cid = rd(f, idsz)
        f.read(4)
        sup = rd(f, idsz)
        f.read(idsz * 5)
        f.read(4)
        for _ in range(u2(f)):
            f.read(2)
            skip_val(f, f.read(1)[0], idsz)
        m = self.cls.setdefault(cid, Meta())
        m.super = sup
        for _ in range(u2(f)):
            nid = rd(f, idsz)
            t = f.read(1)[0]
            v = rd(f, idsz) if t == 2 else (
                rd(f, TSIZE[t]) if TSIZE.get(t) else b"")
            if TSIZE.get(t) is None and t != 2:
                raise RuntimeError(f"bad static type {t}")
            m.statics.append((nid, t, v))
            self.statics.append((cid, nid, t, v))
        for _ in range(u2(f)):
            nid = rd(f, idsz)
            t = f.read(1)[0]
            m.fields.append((nid, t))

    def inst(self, f, idsz):
        f.read(idsz + 4 + idsz)
        f.seek(u4(f), 1)

    def oarr(self, f, idsz):
        f.read(idsz + 4)
        n = u4(f)
        f.read(idsz)
        f.seek(n * idsz, 1)

    def parr(self, f, idsz):
        aid = rd(f, idsz)
        f.read(4)
        n = u4(f)
        t = f.read(1)[0]
        sz = TSIZE.get(t)
        if sz is None:
            raise RuntimeError(f"bad prim type {t} @0x{f.tell():x}")
        if t not in (8, 5) or n * sz > (1 << 26):
            f.seek(n * sz, 1)
            return
        data = rd(f, n * sz)
        for w, p, u in self.pats:
            if p in data or u in data:
                self.targets.setdefault(aid, []).append(w)


class LayerWalk:
    def __init__(self, rx, hits):
        self.rx = rx
        self.hits = hits

    def string(self, sid, text):
        pass

    def load_class(self, cid, nid):
        pass

    def cls_dump(self, f, idsz):
        skip_cls_dump(f, idsz)

    def inst(self, f, idsz):
        oid = rd(f, idsz)
        f.read(4)
        cid = rd(f, idsz)
        data = rd(f, u4(f))
        seen = set()
        for m in self.rx.finditer(data):
            r = m.group(0)
            if r in seen:
                continue
            seen.add(r)
            self.hits.append((oid, cid, r, m.start(), None))

    def oarr(self, f, idsz):
        aid = rd(f, idsz)
        f.read(4)
        n = u4(f)
        acid = rd(f, idsz)
        data = rd(f, n * idsz)
        seen = set()
        for m in self.rx.finditer(data):
            r = m.group(0)
            if r in seen:
                continue
            seen.add(r)
            self.hits.append((aid, None, r, m.start(), acid))

    def parr(self, f, idsz):
        rd(f, idsz)
        f.read(4)
        n = u4(f)
        t = f.read(1)[0]
        sz = TSIZE.get(t)
        if sz is None:
            raise RuntimeError(f"bad prim type {t} @0x{f.tell():x}")
        f.seek(n * sz, 1)


def main():
    path = sys.argv[1]
    words = list(DEFAULT_WORDS)
    depth_max = 6
    if "--words" in sys.argv:
        with open(sys.argv[sys.argv.index("--words") + 1], encoding="utf-8") as fh:
            words = [l.strip() for l in fh if l.strip()]
    if "--depth" in sys.argv:
        depth_max = int(sys.argv[sys.argv.index("--depth") + 1])

    t0 = time.time()
    print(f"trace {path}  words={len(words)} depth<={depth_max}", flush=True)

    strings = {}
    cls = {}
    class_names = {}
    targets = {}
    statics = []

    p1 = Pass1(words, strings, cls, class_names, targets, statics)
    with open(path, "rb", buffering=1 << 20) as f:
        ver, idsz = read_header(f)
        print(f"header {ver} idsz={idsz}", flush=True)
        top_walk(f, idsz, p1)

    for cid, m in cls.items():
        nid = class_names.get(cid)
        if nid is not None and nid in strings:
            m.name = strings[nid].decode("utf-8", "replace")

    # targets: fix the assignment loop from Pass1.parr (word -> array id)
    # (Pass1 collected raw matches; rebind here properly)
    print(f"[p1] strings={len(strings)} classes={len(cls)} "
          f"targets={len(targets)} statics={len(statics)} "
          f"cp-hits={len(set(p1.cp_hits))} ({time.time() - t0:.1f}s)",
          flush=True)

    words_with_targets = sorted({w for ws in targets.values() for w in ws})
    for w in words_with_targets:
        n = sum(1 for ws in targets.values() if w in ws)
        print(f"  target {w!r} x{n}", flush=True)
    for w in sorted(set(words) - set(words_with_targets)):
        print(f"  no-array {w!r}", flush=True)

    # tracked: object id -> list of (word, chain)
    # chain = holder entries from byte[] upward (byte[] itself has chain [])
    tracked = {}
    for aid, ws in targets.items():
        tracked[aid] = [(w, []) for w in ws]

    chains = []

    def statics_check(tag):
        n = 0
        for cid, nid, t, v in statics:
            if t != 2:
                continue
            for word, chain in tracked.get(v, ()):
                m = cls.get(cid)
                cname = m.name if m else "?"
                fname = strings.get(nid, b"?").decode("utf-8", "replace")
                chains.append((word, chain + [f"STATIC {cname}.{fname}"]))
                n += 1
        if n:
            print(f"  [statics@{tag}] {n} chains", flush=True)

    statics_check(0)

    lay_cache = {}

    def layout(cid):
        if cid in lay_cache:
            return lay_cache[cid]
        out = {}
        chain_metas = []
        c = cid
        depth = 0
        while c in cls and depth < 64:
            chain_metas.append(cls[c])
            c = cls[c].super
            depth += 1
        off = 0
        for m in chain_metas:
            for nid, t in m.fields:
                sz = idsz if t == 2 else TSIZE.get(t, 1)
                out[off] = (m.name, strings.get(nid, b"?").decode(
                    "utf-8", "replace"))
                off += sz
        lay_cache[cid] = out
        return out

    def is_jdk(entry):
        core = entry[7:] if entry.startswith("STATIC ") else entry
        core = core.replace("/", ".")
        if core == "?":
            return True
        if core.startswith("["):
            core = core[1:]
            if core.startswith("L"):
                core = core[1:]
            else:
                return True
        return core.startswith(JDK_PFX)

    for depth in range(1, depth_max + 1):
        tw = time.time()
        rx = re.compile(b"|".join(re.escape(k) for k in tracked))
        hits = []
        lw = LayerWalk(rx, hits)
        with open(path, "rb", buffering=1 << 20) as f:
            read_header(f)
            top_walk(f, idsz, lw)

        new = {}
        for oid, cid, ref, off, acid in hits:
            for word, chain in tracked.get(ref, ()):
                if acid is not None:
                    am = cls.get(acid)
                    entry = f"{am.name if am else '?'}[]"
                elif cid is not None:
                    lo = layout(cid)
                    owner, fname = lo.get(off, ("?", "?"))
                    entry = f"{owner}.{fname}"
                else:
                    entry = "?"
                nchain = chain + [entry]
                chains.append((word, nchain))
                if oid:
                    new.setdefault(oid, []).append((word, nchain))

        added = 0
        for oid, lst in new.items():
            if oid in tracked:
                continue
            tracked[oid] = lst
            added += 1

        statics_check(depth)
        print(f"[layer {depth}] hits={len(hits)} new={added} "
              f"tracked={len(tracked)} ({time.time() - tw:.1f}s)", flush=True)
        if added == 0:
            break
        if len(tracked) > 400000:
            print("tracked cap hit, stopping", flush=True)
            break

    # ---- report ----
    by_word = {}
    for word, ch in chains:
        by_word.setdefault(word, Counter())[tuple(ch)] += 1

    print("\n" + "=" * 70, flush=True)
    interesting_words = 0
    for w in words:
        cnts = by_word.get(w)
        tcount = sum(1 for ws in targets.values() if w in ws)
        if not cnts:
            if tcount:
                print(f"\n== {w} ==  ({tcount} raw byte[], no Java holder)")
            continue
        lines = cnts.most_common(40)
        marked = [(c, n) for c, n in lines
                  if any(not is_jdk(e) for e in c)]
        if marked:
            interesting_words += 1
        print(f"\n== {w} ==  arrays={tcount} chains={sum(cnts.values())}"
              f"{'  *OWNED*' if marked else ''}")
        for c, n in lines[:25]:
            mark = " *" if any(not is_jdk(e) for e in c) else ""
            print(f"  {n:5d}x  {' -> '.join(c)}{mark}")

    print(f"\nwords with own-class holders: {interesting_words}/{len(words)}",
          flush=True)
    print(f"total chains {len(chains)}  ({time.time() - t0:.1f}s)", flush=True)


if __name__ == "__main__":
    main()
