"""Find runtime decode/concat call sites OUTSIDE generated LT/ModuleText blocks.
Flags per-frame risks: plainString()/TextMask.plain in render-ish files,
string concat with getName()/getValue()/ModuleText/LT refs."""
import io, os, re

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java'
RE_LTBLOCK = re.compile(r'// cloak literals.*?(?=\n\}|\Z)', re.S)
RE_FIELDDEF = re.compile(r'String\s+(L\d+|S\d+)\s*=\s*new\s+com\.utilitymod')

RE_PLAIN = re.compile(r'\.plainString\(\)')
RE_MASK = re.compile(r'TextMask\.plain\(')
RE_CONCAT_NAME = re.compile(r'[^=!<>+]*\+\s*[^;]*(?:getName\(\)|getValue\(\)|ModuleText\.|LT\d?\.L\d+|Plain\()')
RE_DRAW_TEXT = re.compile(r'(drawText|drawString|textRenderer\.draw|drawWithShadow|literal\()')

hits = []
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if not f.endswith('.java'):
            continue
        p = os.path.join(dp, f)
        rel = os.path.relpath(p, ROOT)
        src = io.open(p, encoding='utf-8', errors='replace').read()
        # blank out LT/ModuleText class bodies (keep line numbers via newline pad)
        def blank(m):
            return re.sub(r'[^\n]', ' ', m.group(0))
        scrubbed = RE_LTBLOCK.sub(blank, src)
        # also blank field-def lines themselves
        scrubbed = RE_FIELDDEF.sub(lambda m: ' ' * m.start(), scrubbed)
        lines = scrubbed.split('\n')
        orig = src.split('\n')
        for i, l in enumerate(lines):
            reasons = []
            if RE_PLAIN.search(l):
                reasons.append('plainString')
            if RE_MASK.search(l):
                reasons.append('TextMask.plain')
            if re.search(r'\+\s*[^;]*(?:getName\(\)|getValue\(\))', l):
                reasons.append('concat-name/value')
            if reasons:
                hits.append((rel, i + 1, ','.join(reasons), orig[i].strip()[:130]))

print('call sites outside LT blocks:', len(hits))
for (rel, ln, why, line) in hits:
    print('%s:%d  [%s]\n    %s' % (rel, ln, why, line.encode('ascii', 'backslashreplace').decode()))
