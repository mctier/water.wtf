import re, io, os, collections, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wa_leak_literals as wa
import cloak_master

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java'
EXACT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\exact_leaks.txt'

vals = []
for line in io.open(EXACT, encoding='utf-8', errors='replace'):
    parts = line.rstrip('\n').split('\t')
    if len(parts) >= 4:
        try:
            vals.append(eval(parts[2]))
        except Exception:
            pass

# build occurrence index per value
cat = collections.Counter()
detail = collections.defaultdict(list)

files = {}
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if f.endswith('.java'):
            p = os.path.join(dp, f)
            files[os.path.relpath(p, ROOT)] = io.open(p, encoding='utf-8', errors='replace').read()

for v in set(vals):
    needle = '"' + v + '"'
    needles = [needle]
    if '"' in v:
        needles.append('"' + v.replace('"', '\\"') + '"')
    hits = []
    ann_spans = {}
    for rel, src in files.items():
        ann_spans[rel] = cloak_master.generic_annotation_spans(src)
        for nd in needles:
            for m in re.finditer(re.escape(nd), src):
                hits.append((rel, src[:m.start()].count('\n') + 1, src, m.start()))
    if not hits:
        cat['no-source-literal (enum-injected or javac artifact)'] += 1
        detail['enum-injected'].append(v)
        continue
    classified = set()
    for (rel, ln, src, pos) in hits:
        line = src.split('\n')[ln - 1]
        before = src[:pos]
        last_line_start = before.rfind('\n') + 1
        prefix = src[last_line_start:pos]
        if '//' in prefix or '*' in prefix:
            classified.add('comment (does not ship)')
            continue
        win = src[max(0, pos - 60):pos + len(needle) + 40]
        if re.search(r'case\s+' + re.escape(needle), win):
            classified.add('case label (switch constant)')
            continue
        if pos in ann_spans.get(rel, set()):
            classified.add('annotation value (mixin/agent target)')
            continue
        ctx = src[max(0, pos - 80):pos + len(needle) + 40]
        if re.search(r'\benum\b', ctx) or re.search(r'[,{]\s*' + re.escape(needle[1:-1]) + r'\s*[,}]', ctx):
            classified.add('enum constant name (javac-injected)')
            continue
        classified.add('OTHER/needs-eyeball')
    for c in classified:
        cat[c] += 1
        detail[c].append(v)

for c, n in sorted(cat.items(), key=lambda kv: -kv[1]):
    print('%3d  %s' % (n, c))
for c in detail:
    if 'OTHER' in c or 'no-source' in c or 'enum' in c:
        print('  --', c, ':', sorted(set(detail[c]))[:30])
