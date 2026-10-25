import os, re, sys, io
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wa_leak_literals as wa

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java'
RE_CONSTDEF = re.compile(r'(?<![\w.])(?:static\s+)?(?:final\s+)?String\s+([A-Za-z_]\w*)\s*=\s*"')
RE_ANNOTUSE = re.compile(r'@(\w+)\s*\(')

names = set()
defsites = {}
for dp, dn, fn in os.walk(ROOT):
    for fname in sorted(fn):
        if not fname.endswith('.java'): continue
        p = os.path.join(dp, fname)
        rel = os.path.relpath(p, ROOT)
        src = io.open(p, encoding='utf-8', errors='replace').read()
        for m in RE_CONSTDEF.finditer(names_src := src):
            names.add(m.group(1)); defsites.setdefault(m.group(1), []).append(rel)
        # also nested/qualified defs like `String FOO` inside any decl already covered
haz = []
for dp, dn, fn in os.walk(ROOT):
    for fname in sorted(fn):
        if not fname.endswith('.java'): continue
        p = os.path.join(dp, fname)
        rel = os.path.relpath(p, ROOT)
        src = io.open(p, encoding='utf-8', errors='replace').read()
        for am in RE_ANNOTUSE.finditer(src):
            d = 0; q = am.end() - 1; span_end = None
            while q < len(src):
                c = src[q]
                if c == '(': d += 1
                elif c == ')':
                    d -= 1
                    if d == 0: span_end = q; break
                elif c in '"\'':
                    e = q + 1
                    while e < len(src):
                        if src[e] == '\\': e += 2; continue
                        if src[e] == c: break
                        e += 1
                    q = e
                q += 1
            if span_end is None: continue
            span = src[am.end():span_end]
            line = src[:am.start()].count('\n') + 1
            for n in names:
                if re.search(r'(?<![\w.])' + re.escape(n) + r'\b', span):
                    haz.append((rel, line, am.group(1), n))
print('annotation uses of String const fields (compile hazard if converted):', len(haz))
for h in haz: print('   ', h)

# what does an annotation span look like in a mixin? print one sample
for dp, dn, fn in os.walk(ROOT):
    if 'mixins' in dp:
        for fname in fn:
            if not fname.endswith('.java'): continue
            p = os.path.join(dp, fname)
            src = io.open(p, encoding='utf-8', errors='replace').read()
            m = re.search(r'@Inject\(', src)
            if m:
                print('--- sample', os.path.relpath(p, ROOT))
                print(src[m.start()-200:m.start()+300])
                break
        break
