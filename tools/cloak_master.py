"""Master literal-cloak pass over the whole client source.

Flavors per VALUE (global, one flavor everywhere a value occurs):
  cloak  -> new EncryptedString(char[],char[]).toString()     (PUA U+E100..U+E1FF in heap)
  plain  -> new EncryptedString(char[],char[]).plainString()  (runtime plain, CP still clean)

Skips (literal stays in source/CP, impossible to convert safely):
  case labels, annotation values (incl. nested @At/@Inject args), empty literals.

forced-plain (global): functional call contexts, shape rules (descriptor, path,
intermediary id, ALLCAPS, camelCase, no-uppercase), ModeSetting/MultiModeSetting/
StringSetting value args (config + switch round-trip), case/annotation values,
whole trees: web/, mixins/, inject/.

cloak: FeatureInfo args, setting-ctor arg0 (names), other display calls arg0,
default (anything else) with uppercase in the value.

Idempotent: already-converted values have no literal left to find.
Files already holding a nested LT class get LT2. --apply writes, default dry-run.
"""
import os, re, sys, io, random, shutil, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wa_leak_literals as wa

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java'
BACKUP = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\_cloak_backup'
REPORT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\cloak_master_report.txt'

PLAIN_TREES = ('web', 'mixins', 'inject')

FUNC_PLAIN = {
    'contains', 'startsWith', 'endsWith', 'equals', 'equalsIgnoreCase',
    'indexOf', 'lastIndexOf', 'matches', 'split', 'replaceAll', 'replace',
    'replaceFirst', 'toLowerCase', 'toUpperCase', 'trim', 'strip',
    'stripLeading', 'stripTrailing', 'isEmpty', 'isBlank', 'format', 'printf',
    'getLogger', 'warn', 'info', 'error', 'debug', 'trace', 'fail', 'say',
    'println', 'printStackTrace', 'valueOf', 'asMap', 'getInt', 'addProperty',
    'putIfAbsent', 'computeIfAbsent', 'sort', 'compare', 'bindId', 'legacy',
    'validate', 'iconOf', 'fromString', 'forName', 'getMethod',
    'getDeclaredMethod', 'getField', 'getDeclaredField', 'getConstructor',
    'getDeclaredConstructor', 'tryParse', 'compile', 'parse', 'parseInt',
    'parseFloat', 'parseDouble', 'sendChatMessage', 'sendChat', 'sendCommand',
    'sendChatCommand', 'sendCommandExecution', 'runCommand', 'executeCommand',
    'addMessage', 'literal', 'get', 'has', 'put', 'add', 'getAsString',
    'getAsJsonObject', 'getAsJsonArray', 'getAsInt', 'getAsBoolean', 'entrySet',
    'keySet', 'containsKey', 'getProperty', 'getLoggerFactory', 'of', 'key',
    'plain', 'getName', 'valueOf', 'lookup', 'find', 'resolve',
}

CLOAK_CALLS = {
    'is', 'addSetting', 'ModeSetting', 'IntSetting', 'FloatSetting', 'BoolSetting',
    'BooleanSetting', 'NumberSetting', 'ColorSetting', 'KeybindSetting',
    'RangeSetting', 'FeatureInfo', 'describedBy', 'describes', 'visibleWhen',
    'usesNotifications', 'group', 'push', 'confirm', 'setModes', 'apply',
    'capture', 'toggle', 'activate', 'cycle', 'setValue', 'setOption',
}

# ctors whose non-name string args are VALUES (config/switch round-trip)
VALUE_CTORS = {'ModeSetting', 'MultiModeSetting', 'StringSetting',
               'InputSimulationSetting', 'HotbarSelectionSetting'}

RE_CAMEL = re.compile(r'^[a-z][a-z0-9]*([A-Z][a-z0-9]*)+$')
RE_ALLCAPS = re.compile(r'^\$?[A-Z][A-Z0-9_]*$')
RE_INTERMEDIARY = re.compile(r'^(method|field|class)_\d+$')
RE_INTERNAL_PATH = re.compile(r'^[a-z][a-z0-9_]*(/[a-zA-Z0-9_$.]+)+$')


def shape_plain(val):
    if not val:
        return 'empty'
    if val.startswith('(') and (')' in val or val.endswith('V')):
        return 'descriptor'
    if ' ' not in val and ('/' in val or (':' in val and val.count(':') == 1)):
        if val.startswith(('http://', 'https://', 'mailto:')) is False:
            if '/' in val or RE_INTERNAL_PATH.match(val) or ':' in val:
                return 'path-or-id'
    if RE_INTERMEDIARY.match(val):
        return 'intermediary'
    if RE_ALLCAPS.match(val):
        return 'allcaps-id'
    if RE_CAMEL.match(val):
        return 'camelcase-token'
    if not re.search(r'[A-Z]', val):
        return 'no-uppercase'
    return None


def generic_annotation_spans(src):
    """Start offsets of literals inside any @Name(...) argument list."""
    skip = set()
    for m in re.finditer(r'@(\w+)\s*\(', src):
        d = 0
        q = m.end() - 1
        while q < len(src):
            c = src[q]
            if c == '(':
                d += 1
            elif c == ')':
                d -= 1
                if d == 0:
                    break
            elif c == '"' or c == "'":
                e = q + 1
                while e < len(src):
                    if src[e] == '\\':
                        e += 2
                        continue
                    if src[e] == c:
                        break
                    e += 1
                q = e
            q += 1
        span_end = q
        for lm in re.finditer(r'"', src[m.end():span_end]):
            skip.add(m.end() + lm.start())
    return skip


def tree_plain(rel):
    parts = rel.replace('\\', '/').split('/')
    return any(p in PLAIN_TREES for p in parts)


def collect():
    """phase 1: all files, all convertible literals + skip reasons."""
    files = {}
    forced_plain = {}       # val -> reason
    kept_above = set()      # chars > 0xFF: carrier decode masks & 0xFF, cannot convert
    for dp, dn, fn in os.walk(ROOT):
        for fname in sorted(fn):
            if not fname.endswith('.java'):
                continue
            p = os.path.join(dp, fname)
            rel = os.path.relpath(p, ROOT)
            src = io.open(p, encoding='utf-8', errors='replace').read()
            lits = wa.lexit(src)
            if not lits:
                continue
            ann = generic_annotation_spans(src)
            cas = wa.case_label_mask(src, lits)
            items = []
            for (s, e, raw) in lits:
                val = wa.java_unescape(raw)
                if not val or not val.strip():
                    continue
                if any(ord(c) > 0xFF for c in val):
                    kept_above.add(val)
                    continue
                if s in ann:
                    forced_plain.setdefault(val, 'annotation-value')
                    continue
                if s in cas:
                    forced_plain.setdefault(val, 'case-label')
                    continue
                open_, name, idx, is_new = wa.enclosing_call(src, s)
                reason = shape_plain(val)
                if reason:
                    forced_plain.setdefault(val, 'shape:' + reason)
                if tree_plain(rel):
                    forced_plain.setdefault(val, 'tree-force-plain')
                if name in FUNC_PLAIN:
                    forced_plain.setdefault(val, 'func:' + name)
                if name in VALUE_CTORS and idx > 0:
                    forced_plain.setdefault(val, 'value-ctor-arg%d' % idx)
                items.append((s, e, raw, val, name or '', idx, is_new))
            if items:
                files[rel] = (p, src, items)
    return files, forced_plain, kept_above


PLAIN_REQUIRED_SHAPES = {
    'shape:descriptor',          # bytecode descriptor "(Ljava/lang/String;)V"
    'shape:intermediary',        # Fabric intermediary "method_12345"
    'shape:path-or-id',          # MC registry id "minecraft:iron_sword"
    'shape:allcaps-id',          # enum/constant tokens
    'shape:empty',
}

PLAIN_REQUIRED_HARD = {
    'annotation-value',          # Java compile-time constants
    'case-label',                # switch case labels
    'tree-force-plain',          # web/, mixins/, inject/ trees
}

# Keep forced-plain on these calls — downstream consumer treats String as ASCII
# bytes (format strings, parsers, loggers, reflection, JSON keys, MC registry
# lookups, chat text that renders to glyphs).
PLAIN_REQUIRED_FUNCS = {
    'parseInt', 'parseFloat', 'parseDouble', 'parse', 'tryParse',
    'format', 'printf',
    'compile', 'matches', 'split', 'replace', 'replaceAll', 'replaceFirst',
    'startsWith', 'endsWith', 'contains', 'indexOf', 'lastIndexOf',
    'toUpperCase', 'toLowerCase', 'trim', 'strip', 'stripLeading', 'stripTrailing',
    'isEmpty', 'isBlank', 'equals', 'equalsIgnoreCase', 'valueOf',
    'getLogger', 'warn', 'info', 'error', 'debug', 'trace', 'fail', 'say',
    'println', 'printStackTrace',
    'fromString', 'forName',
    'getField', 'getMethod', 'getDeclaredField', 'getConstructor',
    'getDeclaredMethod', 'getDeclaredConstructor',
    'sendChatMessage', 'sendChat', 'sendCommand', 'sendChatCommand',
    'sendCommandExecution', 'runCommand', 'executeCommand', 'addMessage', 'literal',
    'bindId', 'iconOf', 'lookup', 'find', 'resolve', 'getName', 'legacy',
    'get', 'getInt', 'getAsString', 'getAsJsonObject', 'getAsJsonArray',
    'getAsInt', 'getAsBoolean', 'entrySet', 'keySet', 'containsKey',
    'has', 'put', 'add', 'addProperty', 'putIfAbsent', 'computeIfAbsent',
    'getProperty', 'getLoggerFactory',
    'asMap', 'sort', 'compare', 'of', 'key', 'plain', 'validate',
}


def flavor_of(val, forced_plain):
    """Cloak everything the scanner can match on ASCII. Only MC / Fabric /
    JVM-structural paths keep the plain-ASCII `plainString()` tail; our own
    code paths (ModeSetting.is, settings UI, PUA-aware equalsIgnoreCase) now
    receive PUA-carrier Strings on both sides and compare correctly via
    CloakUtil.plainChar() masking."""
    reason = forced_plain.get(val)
    if reason is None:
        return 'cloak'
    if reason in PLAIN_REQUIRED_HARD:
        return 'plain'
    if reason in PLAIN_REQUIRED_SHAPES:
        return 'plain'
    if reason.startswith('func:'):
        return 'plain' if reason[5:] in PLAIN_REQUIRED_FUNCS else 'cloak'
    # value-ctor-argN, shape:camelcase-token, shape:no-uppercase -> cloak
    return 'cloak'


def class_name_for(src):
    """Pick the next unused LT/LT2/LT3/... slot by scanning all existing LT inner-class
    declarations in the file. Prior rule only checked LT & LT2 in isolation and double-
    allocated LT2 when both LT and LT2 were already present from earlier cloak passes."""
    used = set(re.findall(r'\bclass\s+(LT\d*)\s*\{', src))
    if 'LT' not in used:
        return 'LT'
    i = 2
    while ('LT%d' % i) in used:
        i += 1
    return 'LT%d' % i


def is_interface_file(src):
    return bool(re.search(r'(?<!@)\binterface\s+\w+', src)) and \
        not re.search(r'\b(class|enum|record)\s+\w+', src.split('interface')[0][-200:] if 'interface' in src else '')


def main():
    apply = '--apply' in sys.argv
    random.seed(0xC10A45)
    files, forced_plain, kept_above = collect()
    report = []
    n_cloak = n_plain = n_files = 0
    for rel in sorted(files):
        p, src, items = files[rel]
        flavors = collections.Counter()
        order = []
        seen = set()
        for (s, e, raw, val, name, idx, is_new) in items:
            fl = flavor_of(val, forced_plain)
            flavors[fl] += 1
            if val not in seen:
                seen.add(val)
                order.append((val, fl))
        if not items:
            continue
        cname = class_name_for(src)
        iface = is_interface_file(src)
        body = []
        names = {}
        for i, (val, fl) in enumerate(order, 1):
            key = [random.randint(0, 0xFFFF) for _ in range(max(4, len(val)))]
            enc = wa.encode(val, key)
            jv = ','.join('0x%04x' % (c & 0xFFFF) for c in enc)
            jk = ','.join('0x%04x' % (c & 0xFFFF) for c in key)
            tail = '.toString()' if fl == 'cloak' else '.plainString()'
            names[val] = 'L%d' % i
            body.append('        static final String L%d = new com.utilitymod.echo.utils.EncryptedString(new char[]{%s}, new char[]{%s})%s;'
                        % (i, jv, jk, tail))
        edits = [(s, e, '%s.%s' % (cname, names[val])) for (s, e, raw, val, *rest) in items]
        edits.sort(key=lambda t: -t[0])
        for (s, e, rep) in edits:
            src = src[:s] + rep + src[e:]
        vis = '' if iface else 'private '
        block = ('\n\n    // cloak literals (cloak=PUA carrier / plain=functional decode)\n'
                 '    %sstatic final class %s {\n' % (vis, cname) +
                 '\n'.join(body) + '\n    }\n')
        last = src.rfind('}')
        src = src[:last] + block + src[last:]
        n_cloak += flavors['cloak']
        n_plain += flavors['plain']
        n_files += 1
        report.append('%s\tcloak=%d\tplain=%d\tclass=%s' % (rel, flavors['cloak'], flavors['plain'], cname))
        if apply:
            bdir = os.path.join(BACKUP, os.path.dirname(rel))
            os.makedirs(bdir, exist_ok=True)
            shutil.copy2(p, os.path.join(bdir, os.path.basename(p)))
            io.open(p, 'w', encoding='utf-8', newline='\n').write(src)
    for val, why in sorted(forced_plain.items(), key=lambda kv: kv[1]):
        report.append('FORCED\t%s\t%s' % (why, repr(val)[:120]))
    for val in sorted(kept_above):
        report.append('KEEP\tabove-latin1\t%s' % repr(val)[:120])
    summary = 'files=%d cloak=%d plain=%d forced-plain-values=%d kept-above-latin1=%d apply=%s' % (
        n_files, n_cloak, n_plain, len(forced_plain), len(kept_above), apply)
    print(summary)
    io.open(REPORT, 'w', encoding='utf-8').write(summary + '\n' + '\n'.join(report))
    print('report ->', REPORT)


if __name__ == '__main__':
    main()
