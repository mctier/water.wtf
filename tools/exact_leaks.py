import zipfile, struct, collections, re, json

JAR = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\build\libs\utilitymod-cloak-1.0.0.jar'
MD = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\scan_results.md'
OUT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\exact_leaks.txt'
RULES = r'C:\Users\fakemoon\Downloads\MeowClientFucker-decompiled\ocean-javaw.json'
SPOD = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\spudfuscator-cloak.txt'

FIXED = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
         15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}


def read_utf8(data, i):
    ln = struct.unpack('>H', data[i:i + 2])[0]
    return data[i + 2:i + 2 + ln].decode('utf-8', errors='replace'), i + 2 + ln


def parse_cp(data):
    """return (set of utf8 strings, set of String-referenced strings)"""
    utf8, strings = set(), set()
    if data[:4] != b'\xca\xfe\xba\xbe':
        return utf8, strings
    cp_count = struct.unpack('>H', data[8:10])[0]
    i, idx = 10, 1
    slots = {}
    while idx < cp_count:
        tag = data[i]; i += 1
        if tag == 1:
            s, i = read_utf8(data, i)
            slots[idx] = s
            utf8.add(s)
        elif tag == 8:
            slots[idx] = ('REF', struct.unpack('>H', data[i:i + 2])[0])
            i += 2
        elif tag in FIXED:
            i += FIXED[tag]
            if tag in (5, 6):
                idx += 1
        else:
            raise ValueError('tag %d' % tag)
        idx += 1
    for v in slots.values():
        if isinstance(v, tuple) and v[0] == 'REF':
            s = slots.get(v[1])
            if isinstance(s, str):
                strings.add(s)
    return utf8, strings


all_utf8, all_strlit = set(), set()
z = zipfile.ZipFile(JAR)
for name in z.namelist():
    if name.endswith('.class'):
        u, s = parse_cp(z.read(name))
        all_utf8 |= u
        all_strlit |= s
print('utf8:', len(all_utf8), 'string literals:', len(all_strlit))

# --- source literals from scan_results.md
entries = []
cur = None
for line in open(MD, encoding='utf-8', errors='replace').read().splitlines():
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

# --- sensitive rules
sens = set()
for s in json.load(open(RULES, encoding='utf-8'))['strings']:
    sens.add(s['stringValue'])
for line in open(SPOD, encoding='utf-8'):
    t = line.strip()
    if t and not t.startswith('#'):
        sens.add(t)

by_str = collections.defaultdict(list)
for f, ln, s in entries:
    by_str[s].append((f, ln))

exact_strlit = [(s, locs) for s, locs in by_str.items() if len(s) >= 3 and s in all_strlit]
exact_utf8_only = [(s, locs) for s, locs in by_str.items() if len(s) >= 3 and s in all_utf8 and s not in all_strlit]
print('source literals that are exact CP String constants:', len(exact_strlit))
print('source literals that are utf8-only (names/meta):', len(exact_utf8_only))

def is_sensitive(s):
    sl = s.lower()
    return any(sr and (sr.lower() == sl or (len(sr) >= 5 and sr.lower() in sl)) for sr in sens)

n_sens = sum(1 for s, _ in exact_strlit if is_sensitive(s))
print('sensitive among exact strlits:', n_sens)

with open(OUT, 'w', encoding='utf-8') as f:
    for s, locs in sorted(exact_strlit, key=lambda x: x[1][0]):
        f.write('%s\t%d\t%s\t%s\n' % ('SENS' if is_sensitive(s) else 'plain', len(locs), repr(s), locs[0][0].split('\\')[-1] + ':' + str(locs[0][1])))
print('wrote', OUT)
