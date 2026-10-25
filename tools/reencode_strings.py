import os, re, sys

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src'
MASK64 = (1 << 64) - 1
MIX1 = 0x9E3779B97F4A7C15
MIX2 = 0x27D4EB2F165667C5
SEED_CONST = 0x2545F4914F6CDD1D
MIXR = 13  # mask for rotate-left of 13

def mix_into(state, c):
    x = ((state ^ c) * MIX2) & MASK64
    return ((x << 13) & MASK64) | (x >> (64 - 13))  # rotate left 13

def derive_seed(key, length):
    seed = SEED_CONST ^ ((MIX1 * (length * 31 + len(key))) & MASK64)
    for c in key:
        seed = mix_into(seed, c)
    return seed

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

def encode(plain, key):
    perm = permutation(derive_seed(key, len(plain)), len(plain))
    val = [0] * len(plain)
    klen = len(key)
    for i, ch in enumerate(plain):
        val[perm[i]] = ord(ch) ^ key[i % klen]
    return val

def decode_old(value, key):
    return ''.join(chr(value[i] ^ key[i % len(key)]) for i in range(len(value)))

def decode_new(value, key):
    perm = permutation(derive_seed(key, len(value)), len(value))
    return ''.join(chr(value[perm[i]] ^ key[i % len(key)]) for i in range(len(value)))

CALL = re.compile(
    r'new\s+(?:com\.utilitymod\.echo\.utils\.)?EncryptedString\(\s*'
    r'new char\[\]\s*\{([^{}]*?)\}\s*,\s*new char\[\]\s*\{([^{}]*?)\}\s*\)', re.S)

def parse_ints(text):
    return [int(tok.strip(), 16) if tok.strip().lower().startswith('0x') else int(tok.strip())
            for tok in text.split(',') if tok.strip()]

def fmt(vals):
    return ','.join('0x%04x' % v for v in vals)

def main():
    files = hits = fails = 0
    for dp, dn, fn in os.walk(ROOT):
        for fname in fn:
            if not fname.endswith('.java'):
                continue
            p = os.path.join(dp, fname)
            s = open(p, encoding='utf-8').read()
            out = CALL.sub(lambda m: m.group(0), s)  # placeholder, replaced below
            matches = list(CALL.finditer(s))
            if not matches:
                continue
            changed = 0
            for m in reversed(matches):
                try:
                    val_old = parse_ints(m.group(1))
                    key = parse_ints(m.group(2))
                except Exception:
                    fails += 1
                    continue
                try:
                    plain = decode_old(val_old, key)
                except Exception:
                    fails += 1
                    continue
                if not all(32 <= ord(c) < 127 or c in '\n\t' for c in plain):
                    fails += 1
                    continue
                val_new = encode(plain, key)
                if decode_new(val_new, key) != plain:
                    fails += 1
                    continue
                rep = 'new %sEncryptedString(new char[]{%s}, new char[]{%s})' % (
                    'com.utilitymod.echo.utils.' if m.group(0).startswith('new com') else '',
                    fmt(val_new), ','.join('0x%04x' % k for k in key))
                s = s[:m.start()] + rep + s[m.end():]
                changed += 1
            if changed:
                open(p, 'w', encoding='utf-8', newline='').write(s)
                files += 1
                hits += changed
    print('files changed:', files, 'literals re-encoded:', hits, 'parse fails:', fails)

if __name__ == '__main__':
    main()
