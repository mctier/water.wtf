import os, re, sys, io, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wa_leak_literals as wa

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java'

const_defs = []      # static final String X = "literal"
case_idents = []     # case IDENT:
top_ifaces = []
ann_vals = set()
ann_lines = 0
of_sites = []

RE_CONSTDEF = re.compile(r'(?<![\w.])(?:static\s+)?(?:final\s+)?String\s+([A-Za-z_]\w*)\s*=\s*"')
RE_CASEIDENT = re.compile(r'case\s+([A-Za-z_]\w*)\s*:')
RE_TOPTYPE = re.compile(r'^(?:public\s+|final\s+|abstract\s+)*(?:class|interface|enum|record)\s+(\w+)', re.M)

for dp, dn, fn in os.walk(ROOT):
    for fname in sorted(fn):
        if not fname.endswith('.java'):
            continue
        p = os.path.join(dp, fname)
        rel = os.path.relpath(p, ROOT)
        src = io.open(p, encoding='utf-8', errors='replace').read()
        for m in RE_CONSTDEF.finditer(src):
            const_defs.append((rel, src[:m.start()].count('\n') + 1, m.group(1)))
        for m in RE_CASEIDENT.finditer(src):
            case_idents.append((rel, src[:m.start()].count('\n') + 1, m.group(1)))
        mtop = RE_TOPTYPE.search(src)
        if mtop and mtop.group(1) == 'interface':
            if wa.lexit(src):
                top_ifaces.append((rel, len(wa.lexit(src))))
        # literals strictly inside annotation paren-spans
        for am in re.finditer(r'@(\w+)\s*\(', src):
            d = 0; q = am.end() - 1; span_end = None
            while q < len(src):
                c = src[q]
                if c == '(': d += 1
                elif c == ')':
                    d -= 1
                    if d == 0:
                        span_end = q; break
                elif c in '"\'':
                    e = q + 1
                    while e < len(src):
                        if src[e] == '\\': e += 2; continue
                        if src[e] == c: break
                        e += 1
                    q = e
                q += 1
            if span_end is None:
                continue
            for (s, e, raw) in wa.lexit(src[am.end():span_end]):
                ann_vals.add(wa.java_unescape(raw))
                ann_lines += 1
        for m in re.finditer(r'EncryptedString\.of\(', src):
            of_sites.append((rel, src[:m.start()].count('\n') + 1))

print('const defs (String X = "..." ):', len(const_defs))
for r in const_defs[:60]: print('   ', r)
idents = set(i[2] for i in case_idents)
defs = set(d[2] for d in const_defs)
hazard = sorted(idents & defs)
print('case-ident used as const-def field (COMPILE HAZARD):', hazard)
print('top-level interface files with literals:', top_ifaces)
print('annotation distinct values (%d lits):' % ann_lines, sorted(ann_vals)[:60])
print('EncryptedString.of sites:', len(of_sites))
for r in of_sites[:15]: print('   ', r)
