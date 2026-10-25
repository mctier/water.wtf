import re, zipfile, json, collections

MD = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\scan_results.md'
JAR = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\build\libs\utilitymod-cloak-1.0.0.jar'
RULES = r'C:\Users\fakemoon\Downloads\MeowClientFucker-decompiled\ocean-javaw.json'
SPOD = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\spudfuscator-cloak.txt'

txt = open(MD, encoding='utf-8', errors='replace').read()
entries = []
cur = None
for line in txt.splitlines():
    if line.startswith('## '):
        cur = line[3:]
    m = re.match(r'^- \*\*line (\d+):\*\* (.*)$', line)
    if m and cur:
        s = m.group(2).strip()
        if s.startswith('`') and s.endswith('`') and len(s) >= 2:
            s = s[1:-1]
        if s.startswith('``') and s.endswith('``') and len(s) >= 4:
            s = s[2:-2]
        entries.append((cur, int(m.group(1)), s))
print('parsed entries:', len(entries))

# sensitive rules
sens = set()
d = json.load(open(RULES, encoding='utf-8'))
for s in d['strings']:
    sens.add(s['stringValue'])
for line in open(SPOD, encoding='utf-8'):
    t = line.strip()
    if t and not t.startswith('#'):
        sens.add(t)

def mutf8(s):
    out = bytearray()
    for ch in s:
        o = ord(ch)
        if o == 0:
            out += b'\xc0\x80'
        elif o < 0x80:
            out.append(o)
        elif o < 0x800:
            out.append(0xC0 | (o >> 6)); out.append(0x80 | (o & 0x3F))
        else:
            out.append(0xE0 | (o >> 12)); out.append(0x80 | ((o >> 6) & 0x3F)); out.append(0x80 | (o & 0x3F))
    return bytes(out)

z = zipfile.ZipFile(JAR)
blob = b''.join(z.read(i) for i in z.infolist() if i.filename.endswith('.class'))
blobL = blob.lower()

def in_jar(s):
    variants = set()
    try:
        variants.add(s.encode('utf-8'))
        variants.add(s.encode('utf-16le'))
    except Exception:
        pass
    variants.add(mutf8(s))
    hit = sum(blob.count(v) for v in variants if v)
    hitL = sum(blobL.count(v.lower()) for v in variants if v)
    return hit, hitL

# group strings
by_str = collections.defaultdict(list)
for f, ln, s in entries:
    by_str[s].append((f, ln))

found = []
for s, locs in by_str.items():
    if len(s) < 3:
        continue
    h, hl = in_jar(s)
    if h or hl:
        is_sens = any(sr and (sr.lower() == s.lower() or (len(sr) >= 5 and sr.lower() in s.lower())) for sr in sens)
        found.append((s, h + hl, is_sens, locs))

print('unique strings tested:', sum(1 for s in by_str if len(s) >= 3))
print('FOUND IN JAR:', len(found))
print('=== SENSITIVE (rule match) ===')
for s, n, se, locs in sorted([f for f in found if f[2]], key=lambda x: -x[1]):
    print(f'  {n:4d}x  {s!r}  @ {locs[0][0].split(chr(92))[-1]}:{locs[0][1]} ({len(locs)} sites)')
print('=== OTHER IN-JAR (len>=4) ===')
for s, n, se, locs in sorted([f for f in found if not f[2] and len(f[0]) >= 4], key=lambda x: -x[1]):
    print(f'  {n:4d}x  {s!r}  @ {locs[0][0].split(chr(92))[-1]}:{locs[0][1]} ({len(locs)} sites)')
