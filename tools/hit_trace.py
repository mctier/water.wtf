"""Map every LT/ModuleText field to its decoded value + flavor, then match
the scanner hits. Also count hot-path decode call sites (plainString,
TextMask.plain) per file."""
import re, io, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from roundtrip_test import java_plainstring

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java'
HITS = ['AH Sniper', 'Aura Guide', 'Attack Delay', 'Auto Cart', 'Shard Hack',
        'Auto Eat', 'Auto Mine', 'Shard Trade', 'Auto sprint', 'Auto Tool',
        'Auto Totem', 'Click Simulation', 'Explode Delay', 'Fast Bridge',
        'Health Indicators', 'Hole ESP', 'Mace Swap', 'Netherite Finder',
        'No Jump Delay', 'Place Delay', 'Player ESP', 'Purge',
        'Silent Aim', 'Silent Swap', 'Storage ESP', 'Swing Speed',
        'Switch Back', 'Switch Delay', 'Target HUD', 'Totem Hit',
        'Vertical Speed']

RE_FIELD = re.compile(
    r'String\s+(L\d+|S\d+)\s*=\s*new\s+com\.utilitymod\.echo\.utils\.EncryptedString'
    r'\(new char\[\]\{([^}]*)\},\s*new char\[\]\{([^}]*)\}\)\.(\w+)')

field_map = []  # (file, field, flavor, value)
hotpath = {}    # file -> count of plainString()/TextMask.plain in loops-ish
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if not f.endswith('.java'):
            continue
        p = os.path.join(dp, f)
        rel = os.path.relpath(p, ROOT)
        src = io.open(p, encoding='utf-8', errors='replace').read()
        for m in RE_FIELD.finditer(src):
            try:
                val = [int(x, 16) for x in m.group(2).split(',')]
                key = [int(x, 16) for x in m.group(3).split(',')]
                dec = java_plainstring(val, key)
            except Exception:
                dec = '<decode-error>'
            field_map.append((rel, m.group(1), m.group(4), dec))
        n_plain = len(re.findall(r'\.plainString\(\)', src))
        n_mask = len(re.findall(r'TextMask\.plain\(', src))
        n_tostr = len(re.findall(r'\.toString\(\)', src))
        if n_plain or n_mask:
            hotpath[rel] = (n_plain, n_mask, n_tostr)

hitset = set(HITS)
print('=== scanner hit -> field flavor ===')
for (rel, fld, fl, dec) in field_map:
    if dec in hitset:
        print('%-10s %-6s %-12s %s' % (fl, fld, dec, rel))
print()
print('=== fields whose DECODED value matches (case-insensitive contains) ===')
low_hits = [h.lower() for h in HITS]
for (rel, fld, fl, dec) in field_map:
    d = dec.lower()
    for h in low_hits:
        if h in d:
            print('%-10s %-6s %-40s %s' % (fl, fld, dec[:40], rel))
            break
print()
print('=== files with plainString()/TextMask.plain call sites ===')
for rel in sorted(hotpath):
    np_, nm, nt = hotpath[rel]
    print('%3d plainString  %3d TextMask.plain  %3d toString   %s' % (np_, nm, nt, rel))
