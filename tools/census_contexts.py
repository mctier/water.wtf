import re, os, collections, io

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src', 'client', 'java')
stats = collections.Counter()
sites = collections.defaultdict(list)

def rel(p):
    return os.path.relpath(p, ROOT).replace('\\', '/')

for dp, dn, fn in os.walk(ROOT):
    for fname in sorted(fn):
        if not fname.endswith('.java'):
            continue
        p = os.path.join(dp, fname)
        with io.open(p, encoding='utf-8', errors='replace') as f:
            for i, line in enumerate(f, 1):
                if '"' not in line:
                    continue
                code = re.sub(r'//.*', '', line)
                if re.search(r'\bcase\s+"', code):
                    stats['case-label'] += 1
                    sites['case-label'].append('%s:%d' % (rel(p), i))
                for m in re.finditer(r'@(\w+)\s*\(\s*"', code):
                    stats['annotation'] += 1
                    sites['annotation'].append('%s:%d @%s' % (rel(p), i, m.group(1)))
                if re.search(r'(String\.format|printf)\s*\(\s*"', code):
                    stats['format'] += 1
                    sites['format'].append('%s:%d' % (rel(p), i))
                if re.search(r'\bswitch\s*\(', code) and '"' in code:
                    stats['switch-with-string'] += 1
                    sites['switch-with-string'].append('%s:%d' % (rel(p), i))
                if re.search(r'\b(Class\.forName|getDeclaredMethod|getMethod|getDeclaredField|getField|forMethod|forField|findMethod|findField|srg|remap)\s*\(', code) and '"' in code:
                    stats['reflect-name'] += 1
                    sites['reflect-name'].append('%s:%d' % (rel(p), i))
                if re.search(r'Identifier\.(of|tryParse|ofDefaultPath)\s*\(', code) and '"' in code:
                    stats['identifier'] += 1
                    sites['identifier'].append('%s:%d' % (rel(p), i))
                if re.search(r'(Pattern\.compile|Pattern\.matches)\s*\(\s*"', code):
                    stats['regex'] += 1
                    sites['regex'].append('%s:%d' % (rel(p), i))
                if re.search(r'\.equals\s*\(\s*"', code) or re.search(r'"[A-Za-z][^"]*"\s*\.\s*equals\s*\(', code):
                    stats['equals'] += 1
                    sites['equals'].append('%s:%d' % (rel(p), i))

print('=== risky-context census (line counts) ===')
for k, v in stats.most_common():
    print('%-20s %d' % (k, v))
print()
for k in sites:
    print('--- %s ---' % k)
    for s in sites[k][:40]:
        print(' ', s)
    if len(sites[k]) > 40:
        print('  ... +%d more' % (len(sites[k]) - 40))
