"""heap_scan.py — brute-force sensitive-string scanner for HotSpot HPROF dumps.

Mirrors the external scanner's model: byte-level search for UTF-16LE (String
value char[] on pre-9 layout / char arrays) and Latin-1/UTF-8 (Java 9+ compact
string byte[]) encodings of each sensitive word. Run against GC.heap_dump output
from a live javaw that has the cloak jar injected. Counts every raw hit.

Usage:
  python heap_scan.py C:\\path\\heap.bin
  python heap_scan.py C:\\path\\heap.bin --words my_words.txt
  python heap_scan.py C:\\path\\utilitymod-cloak-1.0.0.jar   # jar re-verify
"""
import sys
import os

# words observed in external scanner output + BYPASS_GUIDE dict + module names
DEFAULT_WORDS = [
    "Silent Aim", "Click Simulation", "Aura Guide", "Auto Cart", "Shard Hack",
    "Auto Eat", "Auto Mine", "Shard Trade", "Auto Sprint", "Auto Tool", "Auto Totem",
    "Auto Mace", "Auto Block Miner", "Backtrack", "Trig Attack", "Stun Cob",
    "Target HUD", "Player ESP", "Storage ESP", "Netherite Finder", "Health Indicators",
    "No Jump Delay", "Fast Bridge", "Purge", "Auto Reconnect", "AH Sniper",
    "AH Seller", "Panic Pay", "Sniper Bot", "Sell Routine", "Tp Macro",
    "Advanced Finder", "World Time", "Sky Art", "Shader ESP", "Custom Crosshair",
    "Glass Nametags", "Target Marker", "Hud Overlay", "Schematic Builder",
    "Miner Flow", "Timer Exploit", "Sprint", "Waypoints", "Compass",
]

def encodings(word: str):
    b16 = word.encode("utf-16-le")
    b8 = word.encode("utf-8")
    b8l = word.encode("latin-1", errors="ignore")
    out = [(b16, "utf16"), (b8, "utf8")]
    if b8l and b8l != b8:
        out.append((b8l, "latin1"))
    return out

def _is_path_noise(data: bytes, i: int, pat: bytes) -> bool:
    # class-name/path structural hit: preceded by / \ . or followed by .class
    lo = max(0, i - 2)
    hi = min(len(data), i + len(pat) + 6)
    window = data[lo:hi]
    if any(sep in data[lo:i] for sep in (b"/", b"\\")):
        return True
    tail = data[i + len(pat):i + len(pat) + 6]
    if tail.startswith(b".class") or tail.startswith(b"Class") or tail.startswith(b"\x00"):
        return False
    return False

def _ctx(data: bytes, i: int, n: int, tag: str) -> str:
    lo = max(0, i - 12)
    hi = min(len(data), i + n + 12)
    raw = data[lo:hi]
    if tag == "utf16":
        return raw.decode("utf-16-le", errors="replace").replace("\n", "\\n")
    return raw.decode("latin-1", errors="replace").replace("\n", "\\n")

def scan_file(path: str, words):
    size = os.path.getsize(path)
    with open(path, "rb") as f:
        data = f.read()
    print(f"scanning {path} ({size} bytes) against {len(words)} words")
    total = 0
    for w in words:
        if not w:
            continue
        counts = {}
        examples = []
        for pat, tag in encodings(w):
            c, start = 0, 0
            while True:
                i = data.find(pat, start)
                if i < 0:
                    break
                c += 1
                start = i + 1
                if tag == "utf8" and _is_path_noise(data, i, pat):
                    continue
                if len(examples) < 3:
                    examples.append((tag, _ctx(data, i, len(pat), tag)))
            if c:
                counts[tag] = c
        if counts:
            total += sum(counts.values())
            detail = ", ".join(f"{k}={v}" for k, v in counts.items())
            print(f"  [HIT] {w!r}: {detail}")
            for tag, ex in examples:
                print(f"        {tag} ctx: {ex!r}")
    if total == 0:
        print("[ CLEAN ] no sensitive words found")
    else:
        print(f"total raw hits: {total}")

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    path = sys.argv[1]
    words = DEFAULT_WORDS
    if "--words" in sys.argv:
        i = sys.argv.index("--words")
        with open(sys.argv[i + 1], encoding="utf-8") as f:
            words = [ln.strip() for ln in f if ln.strip()]
    if not os.path.exists(path):
        print(f"not found: {path}")
        sys.exit(2)
    scan_file(path, words)

if __name__ == "__main__":
    main()
