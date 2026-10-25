import re, io, sys, random, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wa_leak_literals as wa

MIX1 = 0x9E3779B97F4A7C15
MIX2 = 0x27D4EB2F165667C5
SEED_CONST = 0x2545F4914F6CDD1D
M64 = (1 << 64) - 1


def rotl(x, n):
    x &= M64
    return ((x << n) | (x >> (64 - n))) & M64


def mix_into(state, c):
    return rotl(((state ^ c) * MIX2) & M64, 13)


def derive_seed(key, length):
    seed = SEED_CONST ^ ((MIX1 * ((length * 31 + len(key)) & M64)) & M64)
    for c in key:
        seed = mix_into(seed, c)
    return seed & M64


def permutation(seed, n):
    perm = list(range(n))
    st = seed
    for i in range(n - 1, 0, -1):
        st ^= (st << 13) & M64
        st &= M64
        st ^= st >> 7
        st ^= (st << 17) & M64
        st &= M64
        j = (st & 0xFFFFFFFF) % (i + 1)
        perm[i], perm[j] = perm[j], perm[i]
    return perm


def java_plainstring(value, key):
    perm = permutation(derive_seed(key, len(value)), len(value))
    out = []
    for i in range(len(value)):
        out.append(chr((value[perm[i]] ^ key[i % len(key)]) & 0xFF))
    return ''.join(out)


def java_tostring(value, key):
    perm = permutation(derive_seed(key, len(value)), len(value))
    return ''.join(chr(0xE100 | ((value[perm[i]] ^ key[i % len(key)]) & 0xFF)) for i in range(len(value)))


# 1) roundtrip of wa.encode pipeline (as used by cloak_master)
random.seed(0xC10A45)
tests = ['utilitymod', 'Grim Entity', 'Pre-Arm On Fall', 'AutoMace Finds things', '1 Tap', 'a']
ok = True
for t in tests:
    key = [random.randint(0, 0xFFFF) for _ in range(max(4, len(t)))]
    enc = wa.encode(t, key)
    dec = java_plainstring(enc, key)
    pua = java_tostring(enc, key)
    good = dec == t
    ok = ok and good
    print('%s dec=%r pua_first=%04x' % ('OK ' if good else 'FAIL', dec, ord(pua[0])))

# 2) decode a REAL pre-baked field from a converted file: expect readable ascii
src = io.open(r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java\com\utilitymod\UtilityMod.java',
              encoding='utf-8').read()
m = re.search(r'L1 = new com\.utilitymod\.echo\.utils\.EncryptedString\(new char\[\]\{([^}]*)\}, new char\[\]\{([^}]*)\}\)\.(plainString|toString)', src)
if m:
    val = [int(x, 16) for x in m.group(1).split(',')]
    key = [int(x, 16) for x in m.group(2).split(',')]
    if m.group(3) == 'plainString':
        print('UtilityMod.L1 decodes to:', repr(java_plainstring(val, key)))
    else:
        print('UtilityMod.L1 pua decodes to:', repr(java_tostring(val, key)))
else:
    print('no L1 match')
print('roundtrip all-ok:', ok)
