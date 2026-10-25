"""Generates EncryptedString(value char[], key char[]) literals for new setting names.

Mirrors the runtime decode in com/utilitymod/echo/utils/EncryptedString.java:
    out[i] = (char)( value[perm[i]] ^ key[i % key.length] )  ->  plaintext char i
with perm = unsigned Fisher-Yates permutation over deriveSeed(key, len).

Usage:
    python tools/gen_encrypted_string.py "Drain" "1-Tap On Break" ...
Prints one ready-to-paste literal per line.
"""
import sys

MASK64 = (1 << 64) - 1
MIX1 = 0x9E3779B97F4A7C15
MIX2 = 0x27D4EB2F165667C5
SEED_CONST = 0x2545F4914F6CDD1D

# ~10-char key (printed as the array text; values are chars)
KEY = [0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643]


def mix_into(state, c):
    x = ((state ^ c) * MIX2) & MASK64
    return ((x << 13) & MASK64) | (x >> (64 - 13)) & MASK64


def derive_seed(key, length):
    seed = (SEED_CONST ^ ((MIX1 * (length * 31 + len(key))) & MASK64)) & MASK64
    for c in key:
        seed = mix_into(seed, c)
    return seed & MASK64


def permutation(seed, n):
    perm = list(range(n))
    st = seed & MASK64
    for i in range(n - 1, 0, -1):
        st ^= (st << 13) & MASK64
        st &= MASK64
        st ^= st >> 7
        st = (st ^ ((st << 17) & MASK64)) & MASK64
        st &= MASK64
        j = (st & 0xFFFFFFFF) % (i + 1)
        perm[i], perm[j] = perm[j], perm[i]
    return perm


def encode(plain, key):
    perm = permutation(derive_seed(key, len(plain)), len(plain))
    val = [0] * len(plain)
    for i, ch in enumerate(plain):
        val[perm[i]] = (ord(ch) ^ key[i % len(key)]) & 0xFFFF
    return val


def decode(value, key):
    perm = permutation(derive_seed(key, len(value)), len(value))
    return ''.join(chr((value[perm[i]] ^ key[i % len(key)]) & 0xFF) for i in range(len(value)))


def literal(plain):
    val = encode(plain, KEY)
    assert decode(val, KEY) == plain, (plain, decode(val, KEY))
    v = ','.join('0x%04x' % x for x in val)
    k = ','.join('0x%04x' % x for x in KEY)
    return 'new com.utilitymod.echo.utils.EncryptedString(new char[]{%s}, new char[]{%s})' % (v, k)


for arg in sys.argv[1:]:
    print('%-24s %s' % (arg, literal(arg)))
