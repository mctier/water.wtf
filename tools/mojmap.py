"""Mojang <-> intermediary <-> Yarn name lookup for 1.21.11.

Used to port echo (Mojang-mapped) mixins into Patcher entries, which need the
runtime (intermediary) names, and echo code into this Yarn project.

  python tools/mojmap.py class LocalPlayer
  python tools/mojmap.py method LocalPlayer sendPosition
  python tools/mojmap.py field Minecraft player
  python tools/mojmap.py dump > tools/mojmap.tsv   # full Mojang->Yarn table
"""
import os
import sys

ROOT = os.path.expanduser("~/.gradle/caches/fabric-loom/1.21.11")
MOJ = os.path.join(ROOT, "loom.mappings.1_21_11.layered+hash.2198-v2", "mappings.tiny")
YARN = os.path.join(ROOT, "net.fabricmc.yarn.1_21_11.1.21.11+build.3-v2", "mappings.tiny")


def load(path):
    """-> classes {inter: named}, members {(inter_owner, kind, inter_name): (named, desc_official)}"""
    classes, members = {}, {}
    official = {}
    owner = None
    with open(path, encoding="utf-8") as f:
        next(f)
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if parts[0] == "c":
                owner = parts[2]
                official[parts[1]] = parts[2]
                classes[owner] = parts[3] if len(parts) > 3 else parts[2]
            elif len(parts) >= 5 and parts[0] == "" and parts[1] in ("m", "f"):
                kind, desc, _off, inter = parts[1], parts[2], parts[3], parts[4]
                named = parts[5] if len(parts) > 5 else inter
                members[(owner, kind, inter)] = (named, desc)
    OFFICIAL.update(official)
    return classes, members


OFFICIAL = {}


def idesc(desc):
    """official descriptor -> intermediary descriptor"""
    out, i = [], 0
    while i < len(desc):
        if desc[i] == "L":
            j = desc.index(";", i)
            name = desc[i + 1:j]
            out.append("L" + OFFICIAL.get(name, name) + ";")
            i = j + 1
        else:
            out.append(desc[i])
            i += 1
    return "".join(out)


def main():
    moj_c, moj_m = load(MOJ)
    yarn_c, yarn_m = load(YARN)
    moj_by_simple = {}
    for inter, named in moj_c.items():
        moj_by_simple.setdefault(named.rsplit("/", 1)[-1].replace("$", "."), []).append(inter)
        moj_by_simple.setdefault(named, []).append(inter)

    def classes_for(name):
        return moj_by_simple.get(name.replace(".", "/"), []) or moj_by_simple.get(name, [])

    cmd = sys.argv[1] if len(sys.argv) > 1 else "help"
    if cmd == "class":
        for inter in classes_for(sys.argv[2]):
            print(f"{moj_c[inter]}\t{inter}\t{yarn_c.get(inter, '?')}")
    elif cmd in ("method", "field"):
        kind = "m" if cmd == "method" else "f"
        for inter in classes_for(sys.argv[2]):
            for (o, k, mi), (named, desc) in moj_m.items():
                if o == inter and k == kind and named == sys.argv[3]:
                    y = yarn_m.get((o, k, mi), (mi, desc))[0]
                    print(f"{moj_c[inter]}.{named}\t{inter}.{mi}\t{yarn_c.get(inter, '?')}.{y}\t{idesc(desc)}")
    elif cmd == "dump":
        for inter, named in sorted(moj_c.items()):
            print(f"c\t{named}\t{inter}\t{yarn_c.get(inter, inter)}")
        for (o, k, mi), (named, desc) in sorted(moj_m.items()):
            y = yarn_m.get((o, k, mi), (mi, desc))[0]
            print(f"{k}\t{moj_c.get(o, o)}\t{named}\t{mi}\t{y}\t{desc}")
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
