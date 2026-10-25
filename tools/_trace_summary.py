"""_trace_summary.py — collapse hprof_trace report into a per-word holder matrix.

For every chain, the DIRECT holder of the plain String is chain[1]
(chain[0] is always java/lang/String.value).  Static chains carry
"STATIC ..." as the last entry.
"""
import re
import sys
from collections import Counter, defaultdict

path = sys.argv[1] if len(sys.argv) > 1 else "tools/_trace.out"

word = None
direct = defaultdict(Counter)      # word -> holder-class/field -> n
terminals = defaultdict(Counter)   # word -> last entry -> n
chains = defaultdict(list)

with open(path, encoding="utf-8", errors="replace") as f:
    for line in f:
        m = re.match(r"^== (.+?) ==", line)
        if m:
            word = m.group(1)
            continue
        m = re.match(r"^\s+(\d+)x\s+(.*)$", line)
        if m and word:
            n = int(m.group(1))
            chain = [c.strip() for c in m.group(2).split("->")]
            if len(chain) >= 2:
                d = chain[1]
            else:
                d = "(bare string)"
            if d.startswith("java/lang/String"):
                d = chain[-1] if len(chain) > 1 else d
            direct[word][d] += n
            terminals[word][chain[-1]] += n
            chains[word].append((n, chain))


def top(c, k=8):
    return c.most_common(k)


print("### DIRECT HOLDER (chain[1]) per word")
for w in sorted(direct):
    print(f"\n{w}:")
    for h, n in top(direct[w]):
        print(f"   {n:5d}  {h}")

print("\n\n### TERMINAL (deepest) per word, STATIC + own only")
for w in sorted(terminals):
    keep = [(h, n) for h, n in terminals[w].most_common(40)
            if h.startswith("STATIC ") or "utilitymod" in h
            or "adin" in h or "potato" in h]
    if keep:
        print(f"\n{w}:")
        for h, n in keep[:10]:
            print(f"   {n:5d}  {h}")

print("\n\n### class-name-shaped holders (parallelLockMap etc)")
for w in sorted(direct):
    for h, n in direct[w].items():
        if "parallelLockMap" in h or "ClassLoader" in h:
            print(f"   {w}: {n}x {h}")
