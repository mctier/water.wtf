import re, os, io, collections
ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java'
idcase = collections.Counter()
idcase_sites = collections.defaultdict(list)
textblock = []
for dp, dn, fn in os.walk(ROOT):
    for fname in fn:
        if not fname.endswith('.java'):
            continue
        p = os.path.join(dp, fname)
        src = io.open(p, encoding='utf-8', errors='replace').read()
        if '"""' in src:
            textblock.append(fname)
        for m in re.finditer(r'\bcase\s+([A-Za-z_$][\w$]*)\s*[:{]', src):
            idcase[m.group(1)] += 1
            idcase_sites[m.group(1)].append(fname + ':' + str(src.count('\n', 0, m.start()) + 1))
print('identifier-case labels:')
for k, v in idcase.most_common(60):
    print('  %-30s %d  %s' % (k, v, idcase_sites[k][:3]))
print('files with text blocks:', textblock)
