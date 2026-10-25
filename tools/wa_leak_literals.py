"""Convert plain string literals in web/ + anubis/ to EncryptedString arrays.

Flavors:
  cloak  -> new EncryptedString(char[],char[]).toString()   (runtime PUA U+E100..U+E1FF)
  plain  -> new EncryptedString(char[],char[]).plainString() (runtime plain, functional only)
Rules per literal:
  0 web tree                      -> plain
  1 functional enclosing call     -> plain
  2 new XxxException/Error        -> plain
  3 no uppercase in value         -> plain
  4 ALLCAPS id shape              -> plain
  5 cloak-module-call args        -> cloak
  6 default                       -> cloak
Skips: annotation args, case labels. Dedupe by value per file, plain wins on conflict.
Backups to ./_wa_backup/<relpath>. Insert nested final class LT before last brace.
"""
import os, re, sys, random, shutil

ROOTS = [
    r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java\com\utilitymod\web',
    r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java\com\utilitymod\anubis',
]
BACKUP = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\_wa_backup'

MASK64 = (1 << 64) - 1
MIX1 = 0x9E3779B97F4A7C15
MIX2 = 0x27D4EB2F165667C5
SEED_CONST = 0x2545F4914F6CDD1D

def mix_into(state, c):
    x = ((state ^ c) * MIX2) & MASK64
    return ((x << 13) & MASK64) | (x >> (64 - 13))

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
    for i, ch in enumerate(plain):
        val[perm[i]] = ord(ch) ^ key[i % len(key)]
    return val

def derive_seed(key, length):
    seed = SEED_CONST ^ ((MIX1 * (length * 31 + len(key))) & MASK64)
    for c in key:
        seed = mix_into(seed, c)
    return seed

ESCAPES = {'n': '\n', 't': '\t', 'b': '\b', 'f': '\f', 'r': '\r', '"': '"',
           "'": "'", '\\': '\\', 's': ' ', '0': '\0'}

def java_unescape(raw):
    out = []
    i = 0
    while i < len(raw):
        c = raw[i]
        if c == '\\' and i + 1 < len(raw):
            n = raw[i + 1]
            if n == 'u':
                j = i + 2
                while j < len(raw) and raw[j] == 'u':
                    j += 1
                out.append(chr(int(raw[j:j + 4], 16)))
                i = j + 4
                continue
            if n in ESCAPES:
                out.append(ESCAPES[n]); i += 2; continue
            if n in '01234567':
                j = i + 1; num = ''
                while j < len(raw) and raw[j] in '01234567' and len(num) < 3:
                    num += raw[j]; j += 1
                out.append(chr(int(num, 8))); i = j; continue
            out.append(n); i += 2; continue
        out.append(c); i += 1
    return ''.join(out)

def lexit(src):
    """Return (literals, codeMaskRangeSafe). literals: list of (start,end_snapshot,raw)"""
    lits = []
    i = 0
    n = len(src)
    state = 'code'
    while i < n:
        c = src[i]
        if state == 'code':
            if c == '/' and i + 1 < n and src[i + 1] == '/':
                state = 'line'; i += 2; continue
            if c == '/' and i + 1 < n and src[i + 1] == '*':
                state = 'block'; i += 2; continue
            if c == '"':
                j = i + 1
                while j < n:
                    d = src[j]
                    if d == '\\' and j + 1 < n:
                        j += 2; continue
                    if d == '"':
                        break
                    if d == '\n':
                        break
                    j += 1
                raw = src[i + 1:j]
                lits.append((i, j + 1, raw))
                i = j + 1
                continue
            if c == "'":
                j = i + 1
                while j < n:
                    d = src[j]
                    if d == '\\' and j + 1 < n:
                        j += 3 if j + 2 < n and src[j + 1] == 'u' else 2
                        continue
                    if d == "'":
                        break
                    j += 1
                i = j + 1
                continue
            i += 1
        elif state == 'line':
            if c == '\n':
                state = 'code'
            i += 1
        elif state == 'block':
            if c == '*' and i + 1 < n and src[i + 1] == '/':
                state = 'code'; i += 2; continue
            i += 1
    return lits

FUNC_CALLS = {
    'contains', 'startsWith', 'endsWith', 'equals', 'equalsIgnoreCase',
    'indexOf', 'matches', 'split', 'replaceAll', 'replace', 'toLowerCase',
    'toUpperCase', 'trim', 'repeat', 'format', 'getLogger', 'getLoggerFactory',
    'warn', 'info', 'error', 'debug', 'trace', 'fail', 'say', 'body', 'text',
    'good', 'bad', 'line', 'get', 'getFirst', 'has', 'addProperty',
    'println', 'putIfAbsent', 'computeIfAbsent', 'sort', 'compare', 'startsWithIgnoreCase',
    'bindId', 'legacy', 'validate', 'iconOf', 'valueOf', 'asMap', 'getInt',
    'startsWith ',  # placeholder cleanup below
}
FUNC_CALLS.discard('startsWith ')
# chat/console render + registry path + format/style families -> plain
FUNC_CALLS |= {'literal', 'stripTrailing', 'strip', 'isEmpty', 'fromString'}

CLOAK_CALLS = {
    'is', 'addSetting', 'ModeSetting', 'IntSetting', 'FloatSetting', 'BoolSetting',
    'BooleanSetting', 'NumberSetting', 'ColorSetting', 'StringSetting', 'FeatureInfo',
    'describedBy', 'describes', 'visibleWhen', 'usesNotifications', 'group',
    'push', 'confirm', 'setModes', 'apply', 'capture', 'toggle', 'activate',
}

def enclosing_call(src, pos):
    """Backward balanced walk to nearest enclosing '(' at depth 0.
    Returns (openPos, name, argIndex) or (None, '', -1)."""
    depth = 0
    i = pos - 1
    open_ = None
    while i >= 0:
        c = src[i]
        if c == ')':
            depth += 1
            i -= 1
            continue
        if c == '(':
            if depth == 0:
                open_ = i
                break
            depth -= 1
            i -= 1
            continue
        i -= 1
    if open_ is None:
        return None, '', -1, False
    j = open_ - 1
    while j >= 0 and src[j].isspace():
        j -= 1
    k = j
    while k >= 0 and (src[k].isalnum() or src[k] in '_$'):
        k -= 1
    name = src[k + 1:j + 1]
    # detect 'new' keyword before the identifier
    m2 = j
    while m2 >= 0 and src[m2].isspace():
        m2 -= 1
    w2 = m2
    while w2 >= 0 and (src[w2].isalnum() or src[w2] in '_$'):
        w2 -= 1
    isNew = src[w2 + 1:m2 + 1] == 'new'
    # arg index: forward count of top-level commas between open_ and pos
    d = 0
    idx = 0
    q = open_ + 1
    while q < len(src) and q < pos + 4096:
        ch = src[q]
        if ch == '"':
            e = q + 1
            while e < len(src):
                if src[e] == '\\':
                    e += 2
                    continue
                if src[e] == '"':
                    break
                e += 1
            q = e + 1
            continue
        if ch == "'":
            e = q + 1
            while e < len(src):
                if src[e] == '\\':
                    e += 2
                    continue
                if src[e] == "'":
                    break
                e += 1
            q = e + 1
            continue
        if ch in '([{':
            d += 1
        elif ch in ')]}':
            d -= 1
        elif ch == ',' and d == 0:
            idx += 1
        q += 1
    return open_, name, idx, isNew

def annotations_span_map(src, lits):
    """Set of literal start indices living inside an annotation arg list."""
    skip = set()
    ann_pat = re.compile(r'@(Override|SuppressWarnings|SafeVarargs|Deprecated|FunctionalInterface|Retention|Target|NotNull|Nullable)')
    for m in ann_pat.finditer(src):
        # find arg paren if any
        k = m.end()
        while k < len(src) and src[k].isspace():
            k += 1
        if k < len(src) and src[k] == '(':
            d = 0
            q = k
            while q < len(src):
                if src[q] == '(':
                    d += 1
                elif src[q] == ')':
                    d -= 1
                    if d == 0:
                        break
                q += 1
            for (s, e, r) in lits:
                if s > k and e < q:
                    skip.add(s)
    return skip

def case_label_mask(src, lits):
    skip = set()
    lit_ends = {e: s for (s, e, r) in lits}
    for (s, e, r) in lits:
        j = s - 1
        while j >= 0 and src[j].isspace():
            j -= 1
        if j >= 3 and src[j - 3:j + 1] == 'case':
            skip.add(s)
            continue
        # grouped label: case "a", "b" -> walk the , - chain back to case
        k = j
        while k >= 1 and src[k] == ',':
            k -= 1
            while k >= 0 and src[k].isspace():
                k -= 1
            if k + 1 in lit_ends:
                k = lit_ends[k + 1] - 1
                while k >= 0 and src[k].isspace():
                    k -= 1
                continue
            break
        if k >= 3 and src[k - 3:k + 1] == 'case':
            skip.add(s)
    return skip

def flavor_for(rel, value, call_name, prev_new):
    if rel.startswith('web'):
        return 'plain', 'web-tree'
    if call_name in FUNC_CALLS:
        return 'plain', 'func-call:' + call_name
    if prev_new and prev_new.endswith(('Exception', 'Error')):
        return 'plain', 'new-' + prev_new
    if not re.search(r'[A-Z]', value):
        return 'plain', 'no-upper'
    if re.fullmatch(r'\$?[A-Z][A-Z0-9_]*', value):
        return 'plain', 'allcaps-id'
    if call_name in CLOAK_CALLS:
        return 'cloak', 'cloak-call:' + call_name
    return 'cloak', 'default'

def new_call_name_at(src, open_pos):
    j = open_pos - 1
    while j >= 0 and src[j].isspace():
        j -= 1
    k = j
    while k >= 0 and (src[k].isalnum() or src[k] in '_$'):
        k -= 1
    name = src[k + 1:j + 1]
    return name  # currently unused; isNew handled by enclosing_call

def main():
    random.seed(676767)
    payloads = set()
    for line in open(r'C:\Users\fakemoon\AppData\Local\Temp\opencode\ocean_cheat_modules.txt', encoding='utf-8'):
        try:
            name, payload, enc = line.rstrip('\n').split(' :: ')
            p = payload.strip().lower()
            if p:
                payloads.add(p)
        except ValueError:
            pass
    os.makedirs(BACKUP, exist_ok=True)
    report = []
    for root in ROOTS:
        mod = 'web' if root.endswith('web') else 'anubis'
        for dirpath, dirs, files in os.walk(root):
            for f in sorted(files):
                if not f.endswith('.java'):
                    continue
                p = os.path.join(dirpath, f)
                rel = mod + os.sep + os.path.relpath(p, root)
                src = open(p, encoding='utf-8', errors='ignore').read()
                lits = lexit(src)
                if not lits:
                    continue
                skip_ann = annotations_span_map(src, lits)
                skip_case = case_label_mask(src, lits)
                entries = []
                for (s, e, raw) in lits:
                    if s in skip_ann or s in skip_case:
                        continue
                    val = java_unescape(raw)
                    if not val.strip() or not re.search(r'[A-Za-z]{2,}', val):
                        continue
                    open_, name, idx, isNew = enclosing_call(src, s)
                    prev_new = name if isNew else ''
                    flavor, why = flavor_for(mod, val, name, prev_new)
                    entries.append((s, e, raw, val, flavor, why, prev_new))
                if not entries:
                    continue
                # dedupe by value; plain wins over cloak
                consts = {}
                order = []
                repl = []
                for (s, e, raw, val, flavor, why, cn) in entries:
                    col = any(pl.lower() in val.lower() for pl in payloads)
                    key = (val, flavor if flavor == 'cloak' else 'plain')
                    if key[1] == 'plain' and (val, 'plain') not in consts and (val, 'cloak') in consts:
                        key = (val, 'plain')  # upgrade to plain
                    if key not in consts:
                        consts[key] = None
                        order.append((val, flavor))
                    repl.append((s, e, val, flavor, why, cn, col))
                    if col:
                        report.append((rel, 'PAYLOAD-COLLISION', val[:70], flavor, why))
                    else:
                        report.append((rel, 'convert', val[:70], flavor, why))
                # build constant names L1.. per unique value+flavor
                names = {}
                seq = 0
                body = []
                for (val, flavor) in order:
                    seq += 1
                    fid = 'L%d' % seq
                    names[(val, 'plain' if flavor != 'cloak' else 'cloak')] = fid
                    kf = [random.randint(0, 0xFFFF) for _ in range(max(4, len(val)))]
                    enc = encode(val, kf)
                    join = ','.join('0x%04x' % (c & 0xFFFF) for c in enc)
                    joink = ','.join('0x%04x' % (c & 0xFFFF) for c in kf)
                    tail = '.toString()' if flavor == 'cloak' else '.plainString()'
                    body.append('            static final String %s = new com.utilitymod.echo.utils.EncryptedString(new char[]{%s}, new char[]{%s})%s;' % (fid, join, joink, tail))
                # apply back-to-front replacements
                edits = []
                for (s, e, val, flavor, why, cn, col) in repl:
                    fkey = 'cloak' if flavor == 'cloak' else 'plain'
                    fid = names.get((val, fkey))
                    if fid is None:
                        fid = names.get((val, 'plain'))
                    edits.append((s, e, 'LT.' + fid))
                edits.sort(key=lambda t: -t[0])
                for (s, e, rep) in edits:
                    src = src[:s] + rep + src[e:]
                # insert LT class before last '}' of the (possibly edited) source
                last = src.rfind('}')
                block = '\n\n    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)\n    private static final class LT {\n' + '\n'.join(body) + '\n    }\n'
                src = src[:last] + block + src[last:]
                bdir = os.path.join(BACKUP, mod)
                os.makedirs(bdir, exist_ok=True)
                shutil.copy2(p, os.path.join(bdir, f))
                open(p, 'w', encoding='utf-8', newline='\n').write(src)
                print('done', rel, len(repl), 'literals ->', len(order), 'consts')
    with open(r'C:\Users\fakemoon\AppData\Local\Temp\opencode\wa_report.txt', 'w', encoding='utf-8') as rp:
        for row in report:
            rp.write(' | '.join(map(str, row)) + '\n')
    print('report -> wa_report.txt', 'rows:', len(report))

if __name__ == '__main__':
    main()
