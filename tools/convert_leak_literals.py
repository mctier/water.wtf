import re, sys
sys.path.insert(0, r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools')
from convert_plain_literals import encode, new_key

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java\com\utilitymod\echo\features\impl'

ORD_LEAKS = [
    ('combat/customer', 'Stun Cob: '),
    ('movement/NoWebModule.java', 'No Web Slowdown'),
    ('misc/AutoReconnect.java', 'Auto Reconnect'),
    ('misc/ChatWaypoints.java', 'Chat Waypoints'),
    ('misc/LogoutSpots.java', 'Logout Spots'),
    ('misc/VisualRange.java', 'Visual Range'),
]

FILES = {
    'combat\\StunCob.java': ['combat', 'Stun Cob: '],
    'movement\\NoWebModule.java': ['movement', 'No Web Slowdown'],
    'misc\\AutoReconnect.java': ['misc', 'Auto Reconnect'],
    'misc\\ChatWaypoints.java': ['misc', 'Chat Waypoints'],
    'misc\\LogoutSpots.java': ['misc', 'Logout Spots'],
    'misc\\VisualRange.java': ['misc', 'Visual Range'],
}

def replacement(plain):
    key = new_key(len(plain))
    val = encode(plain, key)
    return 'new com.utilitymod.echo.utils.EncryptedString(new char[]{%s}, new char[]{%s}).toString()' % (
        ','.join('0x%04x' % v for v in val),
        ','.join('0x%04x' % k for k in key))

for rel, plain in FILES.items():
    p = ROOT + '\\' + rel
    s = open(p, encoding='utf-8').read()
    lit = '"' + plain + '"'
    n = s.count(lit)
    if n == 0:
        print('MISS', rel)
        continue
    s = s.replace(lit, replacement(plain))
    open(p, 'w', encoding='utf-8', newline='').write(s)
    print('converted', n, 'literal(s) in', rel)
