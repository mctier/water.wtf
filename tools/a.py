r"""
string_scanner.py - scan a directory and report file + line number.

Two modes:
  1. No search terms given  -> lists EVERY string literal found in the files.
  2. Search terms given     -> lists every line containing those terms.

When the scan is done, the results are printed AND saved to a Markdown file
(scan_results.md by default, change it with -o).

Optional: --encrypt rewrites the string literals in your .java files so they are
stored encrypted (XOR + Base64) and decoded at runtime by a generated helper class
(StringCrypt.java). The originals are never touched: encrypted copies go to a
separate output folder (encrypted_output by default).
  - No search terms  -> every string literal is encrypted
  - Search terms     -> only literals matching your terms are encrypted

Examples:
    python string_scanner.py .                      # every string in every file
    python string_scanner.py ./src -e .py -o all_strings.md
    python string_scanner.py . --min-length 4       # ignore very short strings
    python string_scanner.py . "TODO" "password" -i # search for specific terms
    python string_scanner.py . "def \w+\(" --regex --ext .py
    python string_scanner.py ./src --encrypt                    # encrypt all Java strings
    python string_scanner.py ./src "secret" --encrypt           # only strings containing "secret"
    python string_scanner.py ./src --encrypt --key mykey --encrypt-out build/enc
"""

import argparse
import ast
import base64
import os
import re
import secrets
import sys
import warnings
from collections import Counter, namedtuple
from datetime import datetime

DEFAULT_EXCLUDED_DIRS = {".git", ".hg", ".svn", "node_modules", "__pycache__", ".venv", "venv", ".idea", ".vscode"}
DEFAULT_OUTPUT = "scan_results.md"
DEFAULT_ENCRYPT_OUT = "encrypted_output"
HELPER_NAME = "StringCrypt"

# Quoted strings on a single line, for non-Python files (JS, C, JSON, ...).
STRING_RE = re.compile(r'"(?:[^"\\\n]|\\.)*"|\'(?:[^\'\\\n]|\\.)*\'')


def is_binary(path, blocksize=2048):
    """Treat a file as binary if its first block contains a null byte."""
    try:
        with open(path, "rb") as f:
            return b"\0" in f.read(blocksize)
    except OSError:
        return True


def iter_files(root, extensions, excluded_dirs, skip_paths, skip_dirs=()):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames
                       if d not in excluded_dirs and os.path.abspath(os.path.join(dirpath, d)) not in skip_dirs]
        for name in filenames:
            if extensions and not name.lower().endswith(extensions):
                continue
            full = os.path.join(dirpath, name)
            if os.path.abspath(full) in skip_paths:
                continue
            yield full


def read_text(path):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError as e:
        print(f"[skipped] {path}: {e}", file=sys.stderr)
        return None


def shorten(value, limit=100):
    value = " ".join(value.split())  # collapse newlines / runs of whitespace
    return value if len(value) <= limit else value[: limit - 3] + "..."


# ---------- Java lexer (finds string literals, ignores comments / char literals) ----------

JLit = namedtuple("JLit", "start end line kind raw in_annotation prefix")
ANNOTATION_RE = re.compile(r"@[ \t]*[A-Za-z_][\w.]*[ \t\r\n]*(\()?")
CASE_RE = re.compile(r"\bcase\b[^:;{}]*$")
FINAL_RE = re.compile(r"\bfinal\b[^;{}()]*=")
PACKAGE_RE = re.compile(r"^[\s\ufeff]*package\s+([\w.]+)\s*;", re.M)

SIMPLE_ESC = {"b": "\b", "t": "\t", "n": "\n", "f": "\f", "r": "\r", "s": " ", '"': '"', "'": "'", "\\": "\\"}
ESC_RE = re.compile(r"\\(u+[0-9a-fA-F]{4}|[0-3][0-7]{0,2}|[4-7][0-7]?|[btnfrs\"'\\])")


def java_literals(source):
    """Return every string literal / text block in Java source, with some context about where it sits."""
    lits = []
    n = len(source)
    i = 0
    line = 1
    ann_depth = 0     # >0 while inside @Annotation( ... )
    stmt_start = 0    # index just after the last ; { or }
    while i < n:
        c = source[i]
        if c == "\n":
            line += 1
            i += 1
        elif source.startswith("//", i):
            j = source.find("\n", i)
            i = n if j == -1 else j
        elif source.startswith("/*", i):
            j = source.find("*/", i + 2)
            j = n if j == -1 else j + 2
            line += source.count("\n", i, j)
            i = j
        elif source.startswith('"""', i):
            j = i + 3
            while j < n and not source.startswith('"""', j):
                j += 2 if source[j] == "\\" else 1
            end = min(j + 3, n)
            lits.append(JLit(i, end, line, "textblock", source[i + 3:j], ann_depth > 0, source[stmt_start:i]))
            line += source.count("\n", i, end)
            i = end
        elif c == '"':
            j = i + 1
            while j < n and source[j] not in '"\n':
                j += 2 if source[j] == "\\" else 1
            if j < n and source[j] == '"':
                lits.append(JLit(i, j + 1, line, "string", source[i + 1:j], ann_depth > 0, source[stmt_start:i]))
                i = j + 1
            else:
                i = j  # unterminated string, bail out of it
        elif c == "'":
            j = i + 1
            while j < n and source[j] not in "'\n":
                j += 2 if source[j] == "\\" else 1
            i = j + 1
        elif c == "@":
            m = ANNOTATION_RE.match(source, i)
            if m and not source.startswith("@interface", i):
                if m.group(1):
                    ann_depth += 1
                line += source.count("\n", i, m.end())
                i = m.end()
            else:
                i += 1
        else:
            if ann_depth:
                if c == "(":
                    ann_depth += 1
                elif c == ")":
                    ann_depth -= 1
            if c in ";{}":
                stmt_start = i + 1
            i += 1
    return lits


def unescape_java(raw):
    """Turn the inside of a Java string literal into the real string. None = can't be handled safely."""
    if "\\" in ESC_RE.sub("", raw):
        return None

    def repl(m):
        e = m.group(1)
        if e[0] == "u":
            return chr(int(e.lstrip("u"), 16))
        if e[0].isdigit():
            return chr(int(e, 8))
        return SIMPLE_ESC[e]

    value = ESC_RE.sub(repl, raw)
    try:  # join surrogate pairs (\ud83d\ude00); lone surrogates can't be encoded
        return value.encode("utf-16", "surrogatepass").decode("utf-16")
    except UnicodeError:
        return None


# ---------- mode 1: every string literal ----------

def strings_in_python(source):
    """Use the AST so multi-line, triple-quoted and f-string parts are handled properly."""
    with warnings.catch_warnings():
        # Scanned files may contain odd escapes like "\w"; don't spam warnings about them.
        warnings.simplefilter("ignore")
        tree = ast.parse(source)
    found = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            found.append((node.lineno, node.value))
    return sorted(found, key=lambda x: x[0])


def strings_by_regex(source):
    found = []
    for lineno, line in enumerate(source.splitlines(), start=1):
        for match in STRING_RE.finditer(line):
            found.append((lineno, match.group(0)[1:-1]))
    return found


def strings_in_java(source):
    found = []
    for lit in java_literals(source):
        value = unescape_java(lit.raw) if lit.kind == "string" else lit.raw
        found.append((lit.line, value if value is not None else lit.raw))
    return found


def find_all_strings(path, source):
    if path.endswith(".py"):
        try:
            return strings_in_python(source)
        except SyntaxError:
            pass  # fall back to the regex scan below
    elif path.endswith(".java"):
        return strings_in_java(source)
    return strings_by_regex(source)


# ---------- mode 2: search terms ----------

def build_patterns(terms, use_regex, ignore_case):
    flags = re.IGNORECASE if ignore_case else 0
    patterns = []
    for term in terms:
        try:
            patterns.append((term, re.compile(term if use_regex else re.escape(term), flags)))
        except re.error as e:
            sys.exit(f"Invalid regex '{term}': {e}")
    return patterns


def find_terms(source, patterns):
    found = []
    for lineno, line in enumerate(source.splitlines(), start=1):
        for term, pattern in patterns:
            if pattern.search(line):
                found.append((lineno, f"[{term}] {line.strip()}"))
    return found


# ---------- Java string encryption ----------

HELPER_TEMPLATE = """package __PACKAGE__;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/** Generated by string_scanner.py - decodes the strings that were encrypted at build time. */
public final class StringCrypt {
    private static final byte[] KEY = new byte[] { __KEY__ };
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private StringCrypt() {
    }

    public static String d(String encoded) {
        return CACHE.computeIfAbsent(encoded, StringCrypt::decode);
    }

    private static String decode(String encoded) {
        byte[] data = Base64.getDecoder().decode(encoded);
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (data[i] ^ KEY[i % KEY.length] ^ (i & 0xFF));
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
"""


def helper_source(package, key):
    key_text = ", ".join(f"(byte) 0x{b:02x}" for b in key)
    return HELPER_TEMPLATE.replace("__PACKAGE__", package).replace("__KEY__", key_text)


def encrypt_value(value, key):
    data = bytearray(value.encode("utf-8"))
    for i in range(len(data)):
        data[i] ^= key[i % len(key)] ^ (i & 0xFF)
    return base64.b64encode(bytes(data)).decode("ascii")


def read_java(path):
    with open(path, "r", encoding="utf-8", errors="surrogateescape", newline="") as f:
        return f.read()


def write_java(path, text):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", errors="surrogateescape", newline="") as f:
        f.write(text)


def encrypt_java_source(source, patterns, key, helper_call, min_length, include_constants):
    """Return (new_source, number_encrypted, [(line, reason), ...] for literals left alone)."""
    replacements = []
    skipped = []
    for lit in java_literals(source):
        if lit.kind == "textblock":
            if patterns and not any(p.search(lit.raw) for _, p in patterns):
                continue
            skipped.append((lit.line, "text block (\"\"\"...\"\"\") not supported"))
            continue

        value = unescape_java(lit.raw)
        if value is None:
            skipped.append((lit.line, "contains an escape/character that can't be encoded safely"))
            continue
        if value == "" or len(value.strip()) < min_length:
            continue
        if patterns and not any(p.search(value) for _, p in patterns):
            continue

        if lit.in_annotation:
            skipped.append((lit.line, "annotation argument (must stay a compile-time constant)"))
        elif CASE_RE.search(lit.prefix):
            skipped.append((lit.line, "switch case label (must stay a compile-time constant)"))
        elif not include_constants and FINAL_RE.search(lit.prefix):
            skipped.append((lit.line, "final field/constant (use --include-constants to encrypt)"))
        else:
            replacements.append((lit, encrypt_value(value, key)))

    out = source
    for lit, encoded in reversed(replacements):
        out = out[:lit.start] + f'{helper_call}("{encoded}")' + out[lit.end:]
    return out, len(replacements), skipped


def run_encryption(args, patterns, excluded, skip_paths, skip_dirs, out_dir):
    java_files = [p for p in iter_files(args.directory, (".java",), excluded, skip_paths, skip_dirs)
                  if os.path.basename(p) != HELPER_NAME + ".java"]
    if not java_files:
        print("\n[encrypt] No .java files found, nothing to encrypt.")
        return None

    sources = {}
    packages = Counter()
    for path in java_files:
        try:
            src = read_java(path)
        except OSError as e:
            print(f"[skipped] {path}: {e}", file=sys.stderr)
            continue
        sources[path] = src
        m = PACKAGE_RE.search(src)
        if m:
            packages[m.group(1)] += 1

    package = args.crypt_package or (packages.most_common(1)[0][0] if packages else None)
    if not package:
        sys.exit("[encrypt] Couldn't find a Java package to put StringCrypt in. Use --crypt-package com.example.mod")

    key = args.key.encode("utf-8") if args.key else secrets.token_bytes(16)
    helper_call = f"{package}.{HELPER_NAME}.d"
    min_length = 1 if patterns else args.min_length

    total = 0
    changed_files = 0
    skipped_all = []  # (path, line, reason)
    package_rel_dir = None

    for path, src in sources.items():
        rel = os.path.relpath(path, args.directory)
        new_src, count, skipped = encrypt_java_source(src, patterns, key, helper_call, min_length,
                                                      args.include_constants)
        write_java(os.path.join(out_dir, rel), new_src)
        total += count
        changed_files += 1 if count else 0
        skipped_all.extend((path, line, reason) for line, reason in skipped)
        m = PACKAGE_RE.search(src)
        if package_rel_dir is None and m and m.group(1) == package:
            package_rel_dir = os.path.dirname(rel)

    if package_rel_dir is None:
        package_rel_dir = package.replace(".", os.sep)
    helper_path = os.path.join(out_dir, package_rel_dir, HELPER_NAME + ".java")
    write_java(helper_path, helper_source(package, key))

    return {
        "out_dir": os.path.abspath(out_dir),
        "helper_path": os.path.abspath(helper_path),
        "package": package,
        "encrypted": total,
        "files_changed": changed_files,
        "files_written": len(sources),
        "skipped": skipped_all,
    }


# ---------- markdown output ----------

def md_code(text):
    """Wrap text in an inline code span that survives backticks inside the text."""
    longest = max((len(m) for m in re.findall(r"`+", text)), default=0)
    fence = "`" * (longest + 1)
    pad = " " if text.startswith("`") or text.endswith("`") else ""
    return f"{fence}{pad}{text}{pad}{fence}"


def write_markdown(path, directory, terms, results, files_scanned, show_term_mode, enc=None):
    by_file = {}
    for file_path, lineno, text in results:
        by_file.setdefault(file_path, []).append((lineno, text))

    lines = ["# String scan results", ""]
    lines.append(f"- **Directory:** {md_code(os.path.abspath(directory))}")
    lines.append("- **Mode:** " + (f"search terms {', '.join(md_code(t) for t in terms)}"
                                   if show_term_mode else "every string literal"))
    lines.append(f"- **Scanned:** {files_scanned} file(s)")
    lines.append(f"- **Found:** {len(results)} result(s) in {len(by_file)} file(s)")
    lines.append(f"- **Date:** {datetime.now():%Y-%m-%d %H:%M}")
    lines.append("")

    if enc:
        lines.append("## Encryption")
        lines.append("")
        lines.append(f"- **Encrypted:** {enc['encrypted']} string(s) in {enc['files_changed']} Java file(s)")
        lines.append(f"- **Output folder:** {md_code(enc['out_dir'])} ({enc['files_written']} Java file(s) written)")
        lines.append(f"- **Helper class:** {md_code(enc['package'] + '.' + HELPER_NAME)} at {md_code(enc['helper_path'])}")
        lines.append(f"- **Left as plain text:** {len(enc['skipped'])} literal(s)")
        lines.append("")
        if enc["skipped"]:
            lines.append("### Not encrypted")
            lines.append("")
            for file_path, line, reason in enc["skipped"]:
                lines.append(f"- {md_code(file_path)} line {line}: {reason}")
            lines.append("")

    lines.append("## Results")
    lines.append("")
    if not results:
        lines.append("_Nothing found._")
        lines.append("")
    for file_path, hits in by_file.items():
        lines.append(f"### {md_code(file_path)} ({len(hits)})")
        lines.append("")
        for lineno, text in hits:
            lines.append(f"- **line {lineno}:** {md_code(text)}")
        lines.append("")

    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines).rstrip() + "\n")


def main():
    parser = argparse.ArgumentParser(description="Scan a directory for strings and show file + line number.")
    parser.add_argument("directory", help="Directory to scan")
    parser.add_argument("terms", nargs="*",
                        help="Strings to search for. Leave empty to list EVERY string literal.")
    parser.add_argument("-i", "--ignore-case", action="store_true", help="Case-insensitive search (term mode)")
    parser.add_argument("-r", "--regex", action="store_true", help="Treat search terms as regular expressions")
    parser.add_argument("-m", "--min-length", type=int, default=1,
                        help="In 'every string' mode, skip strings shorter than this (default 1)")
    parser.add_argument("-e", "--ext", nargs="+", metavar="EXT",
                        help="Only scan files with these extensions (e.g. .py .js)")
    parser.add_argument("-x", "--exclude-dir", nargs="+", default=[], metavar="DIR",
                        help="Extra directory names to skip")
    parser.add_argument("--no-default-excludes", action="store_true",
                        help="Don't skip .git, node_modules, venv, etc.")
    parser.add_argument("-o", "--output", default=DEFAULT_OUTPUT,
                        help=f"Markdown file to save the results to (default: {DEFAULT_OUTPUT})")

    enc_group = parser.add_argument_group("Java string encryption")
    enc_group.add_argument("--encrypt", action="store_true",
                           help="Write encrypted copies of the .java files (originals are not modified)")
    enc_group.add_argument("--encrypt-out", default=DEFAULT_ENCRYPT_OUT, metavar="DIR",
                           help=f"Where the encrypted Java files go (default: {DEFAULT_ENCRYPT_OUT})")
    enc_group.add_argument("--key", help="Passphrase used as the key (default: random key per run)")
    enc_group.add_argument("--crypt-package", metavar="PKG",
                           help="Java package for the generated StringCrypt class "
                                "(default: the most common package in your sources)")
    enc_group.add_argument("--include-constants", action="store_true",
                           help="Also encrypt strings in 'final' fields/constants "
                                "(can break code that uses them in annotations or switch cases)")
    args = parser.parse_args()

    if not os.path.isdir(args.directory):
        # An unquoted path with spaces gets split by the shell; try gluing the pieces back together.
        parts = [args.directory] + args.terms
        for n in range(2, len(parts) + 1):
            candidate = " ".join(parts[:n])
            if os.path.isdir(candidate):
                args.directory, args.terms = candidate, parts[n:]
                break

    if not os.path.isdir(args.directory):
        sys.exit(f"Not a directory: {args.directory}\n"
                 "If the path contains spaces, wrap it in quotes: python string_scanner.py \"C:\\My Folder\\src\"")

    excluded = set(args.exclude_dir)
    if not args.no_default_excludes:
        excluded |= DEFAULT_EXCLUDED_DIRS

    extensions = tuple(e.lower() if e.startswith(".") else "." + e.lower() for e in args.ext) if args.ext else None
    patterns = build_patterns(args.terms, args.regex, args.ignore_case) if args.terms else None

    # Don't scan our own previous outputs if they live inside the scanned directory.
    output_path = args.output if args.output.lower().endswith(".md") else args.output + ".md"
    skip_paths = {os.path.abspath(output_path)}
    skip_dirs = {os.path.abspath(args.encrypt_out)}

    results = []  # (file path, line number, text)
    files_scanned = 0

    for path in iter_files(args.directory, extensions, excluded, skip_paths, skip_dirs):
        if is_binary(path):
            continue
        source = read_text(path)
        if source is None:
            continue
        files_scanned += 1

        if patterns:
            for lineno, text in find_terms(source, patterns):
                results.append((path, lineno, text))
        else:
            for lineno, value in find_all_strings(path, source):
                if len(value.strip()) >= args.min_length:
                    results.append((path, lineno, shorten(value)))

    for path, lineno, text in results:
        print(f"{path}:{lineno}: {text}")

    kind = "match(es)" if patterns else "string(s)"
    print(f"\n{len(results)} {kind} found in {files_scanned} file(s) scanned.")

    enc = None
    if args.encrypt:
        enc = run_encryption(args, patterns, excluded, skip_paths, skip_dirs, args.encrypt_out)
        if enc:
            print(f"[encrypt] {enc['encrypted']} string(s) encrypted in {enc['files_changed']} Java file(s), "
                  f"{len(enc['skipped'])} left as plain text.")
            print(f"[encrypt] Encrypted copies: {enc['out_dir']}")
            print(f"[encrypt] Helper class:     {enc['helper_path']}")

    write_markdown(output_path, args.directory, args.terms, results, files_scanned, bool(patterns), enc)
    print(f"Done! Results saved to {os.path.abspath(output_path)}")


if __name__ == "__main__":
    main()