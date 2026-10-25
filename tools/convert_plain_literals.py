import os, re, sys

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src'
MASK64 = (1 << 64) - 1
MIX1 = 0x9E3779B97F4A7C15
MIX2 = 0x27D4EB2F165667C5
SEED_CONST = 0x2545F4914F6CDD1D

def mix_into(state, c):
    x = ((state ^ c) * MIX2) & MASK64
    return ((x << 13) & MASK64) | (x >> (64 - 13))

def derive_seed(key, length):
    seed = SEED_CONST ^ ((MIX1 * (length * 31 + len(key))) & MASK64)
    for c in key:
        seed = mix_into(seed, c)
    return seed

def encode(plain, key):
    perm = permutation(derive_seed(key, len(plain)), len(plain))
    val = [0] * len(plain)
    klen = len(key)
    for i, ch in enumerate(plain):
        val[perm[i]] = ord(ch) ^ key[i % klen]
    return val

def permutation(seed, n):
    perm = list(range(n))
    st = seed
    for i in range(n - 1, 0, -1):
        st ^= (st << 13) & MASK64
        st ^= st >> 7
        st ^= (st << 17) & MASK64
        j = (st & 0xFFFFFFFF) % (i + 1)
        perm[i], perm[j] = perm[j], perm[i]
    return perm

ESCAPES = {'n': '\n', 't': '\t', '\\': '\\', '"': '"'}

def unescape(s):
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == '\\':
            if i + 1 >= len(s) or s[i + 1] not in ESCAPES:
                return None
            out.append(ESCAPES[s[i + 1]])
            i += 2
        else:
            out.append(c)
            i += 1
    return ''.join(out)

def new_key(n):
    return [0xD17A ^ (i * 2654435761 & 0xFFFF) for i in range(max(n, 1))]

def arrays(plain):
    key = new_key(len(plain))
    val = encode(plain, key)
    return ','.join('0x%04x' % v for v in val), ','.join('0x%04x' % k for k in key)

OF_CALL = re.compile(
    r'EncryptedString\.of\((\"(?:\\.|[^\"\\])*\")\)')
FEATURE = re.compile(r'new FeatureInfo\(([^)]*)\)', re.S)
LIT = re.compile(r'\"((?:\\.|[^\"\\])*)\"')

def convert_files():
    n_of = n_feat = fails = 0
    files = []
    for dp, dn, fn in os.walk(ROOT):
        for fname in fn:
            if not fname.endswith('.java'):
                continue
            p = os.path.join(dp, fname)
            s = open(p, encoding='utf-8').read()
            orig = s

            # 1) EncryptedString.of("plain") -> new EncryptedString(new char[]{...}, new char[]{...})
            def rep_of(m):
                nonlocal n_of, fails
                plain = unescape(m.group(1)[1:-1])
                if plain is None:
                    fails += 1
                    return m.group(0)
                key = new_key(len(plain))
                val = encode(plain, key)
                n_of += 1
                return 'new com.utilitymod.echo.utils.EncryptedString(new char[]{%s}, new char[]{%s})' % (
                    ','.join('0x%04x' % v for v in val),
                    ','.join('0x%04x' % k for k in key))
            s = OF_CALL.sub(rep_of, s)

            # 2) bare string literals inside FeatureInfo(...) -> encrypted .toString()
            def rep_feat(m):
                nonlocal n_feat, fails
                body = m.group(1)
                def rep_lit(lm):
                    nonlocal n_feat, fails
                    plain = unescape(lm.group(1))
                    if not plain or not all(32 <= ord(c) < 127 or c in '\n\t' for c in plain):
                        fails += 1
                        return lm.group(0)
                    key = new_key(len(plain))
                    val = encode(plain, key)
                    n_feat += 1
                    return 'new com.utilitymod.echo.utils.EncryptedString(new char[]{%s}, new char[]{%s}).toString()' % (
                        ','.join('0x%04x' % v for v in val),
                        ','.join('0x%04x' % k for k in key))
                return 'new FeatureInfo(%s)' % LIT.sub(rep_lit, body)
            s = FEATURE.sub(rep_feat, s)

            if s != orig:
                open(p, 'w', encoding='utf-8', newline='').write(s)
                files.append(os.path.relpath(p, ROOT))

    print('EncryptedString.of converted:', n_of)
    print('FeatureInfo literals converted:', n_feat)
    print('skipped (weird escapes):', fails)
    print('files touched:', len(files))
    for f in files:
        print('  ', f)

if __name__ == '__main__':
    convert_files()
