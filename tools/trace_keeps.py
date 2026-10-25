import re, io, os
ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java'
vals = ['Glow', 'Star', 'Clear', 'None', 'Accent', 'Rainbow', 'Sunset Haze',
        'Bold Circle', 'Soul Wireframe', 'Lava', 'Strict', 'Fists', 'Pink',
        'Russian', 'shielded', 'Liquids', 'Liquids', 'Tinted', 'Thunder']
for v in vals:
    hits = []
    for dp, dn, fn in os.walk(ROOT):
        for f in fn:
            if not f.endswith('.java'):
                continue
            p = os.path.join(dp, f)
            s = io.open(p, encoding='utf-8', errors='replace').read()
            for m in re.finditer(re.escape('"' + v + '"'), s):
                ln = s[:m.start()].count('\n') + 1
                line = s.split('\n')[ln - 1].strip()
                hits.append((os.path.basename(p), ln, line))
    print('== %s  (%d src occurrences)' % (v, len(hits)))
    for (b, ln, line) in hits[:6]:
        print('    %s:%d  %s' % (b, ln, line[:96].encode('ascii', 'backslashreplace').decode()))
