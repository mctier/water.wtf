import json, os, re, sys

RULES_JSON = r'C:\Users\fakemoon\Downloads\MeowClientFucker-decompiled\ocean-javaw.json'
SRC = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src'

d = json.load(open(RULES_JSON, encoding='utf-8'))
rules = sorted(set(s['stringValue'] for s in d['strings']))
# skip rules that are too short/generic for source scanning (word noise)
rules = [r for r in rules if len(r) >= 4]
pats = [(r, re.compile(r'(?<![A-Za-z0-9_])' + re.escape(r) + r'(?![A-Za-z0-9_])', re.IGNORECASE)) for r in rules]

hits = {}
for root, dd, fs in os.walk(SRC):
    for f in fs:
        if not f.endswith('.java'):
            continue
        fp = os.path.join(root, f)
        try:
            lines = open(fp, encoding='utf-8').read().splitlines()
        except Exception:
            continue
        for i, ln in enumerate(lines, 1):
            if '//' in ln:
                code = ln.split('//', 1)[0]
            else:
                code = ln
            for r, p in pats:
                if p.search(code):
                    hits.setdefault(r, []).append((fp, i, ln.strip()[:140]))

print('plain-literal leak rules:', len(hits))
for r, v in sorted(hits.items()):
    print('==', repr(r), 'x', len(v))
    for fp, i, txt in v[:6]:
        print('   ', os.path.relpath(fp, SRC), i, txt)
