"""Mechanical Mojang -> Yarn source translation for porting echo files.

  python tools/port.py <echo .java> <out .java> [--package from=to ...]

What it does (1.21.11 mappings, see mojmap.py):
  * net.minecraft imports -> Yarn FQNs, imported simple names -> Yarn simple names
  * `.member` / `.member(` -> Yarn name, resolved against every imported MC class
    plus its full superclass/interface chain (tools/hierarchy.tsv); overloads are
    split by call arity. Unresolvable/ambiguous names keep the Mojang name and
    get a /*PORT?a|b*/ marker for manual review.
It is deliberately conservative: the compiler then flags whatever it missed.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
import mojmap  # noqa: E402

HERE = os.path.dirname(__file__)


JAVA_RECEIVERS = {"Optional", "List", "Map", "Set", "Math", "String", "Objects", "Arrays", "Stream",
                  "Collectors", "ThreadLocalRandom", "System", "Float", "Double", "Integer", "Long",
                  "Boolean", "Character", "Collections", "StrictMath", "Thread", "Comparator", "this",
                  "Concat", "EncryptedString", "I18nText"}


# Generic Java / echo method names that also exist (with other Yarn names) on some MC
# class; renaming them on non-MC receivers breaks code, and on MC receivers the
# compiler flags the rare real miss.
NEVER_RENAME = {"get", "set", "add", "remove", "contains", "size", "isEmpty", "equals", "hashCode",
                "toString", "of", "apply", "accept", "test", "run", "stream", "map", "filter", "min", "max",
                "values", "keySet", "put", "clear", "box", "line", "tracer", "matches", "reset", "call",
                "cancel", "update", "getValue", "setValue", "is", "toggle", "getName", "getKey", "length",
                "forEach", "iterator", "next", "hasNext", "push", "pop", "peek", "poll", "offer", "join"}


def _echo_classes():
    """simple names of every class in the echo port (receivers whose methods are never MC)"""
    out = set()
    lst = os.path.join(HERE, "echo-portlist.txt")
    if os.path.exists(lst):
        for line in open(lst):
            line = line.strip()
            if line:
                out.add(os.path.basename(line)[:-5])
    return out | {"Echo", "Concat", "EncryptedString", "I18nText", "Imports", "MixinAccessorCompat", "Accessors",
                  "MinecraftCompat", "ChatCompat", "EntityMotionPacketCompat", "SlimeCompat", "NetworkUtil",
                  "RenderUtil", "GlassUIConstants"}


ECHO_CLASSES = _echo_classes()


def load_hierarchy():
    sup = {}
    with open(os.path.join(HERE, "hierarchy.tsv"), encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            sup[parts[0]] = [p for p in parts[1:] if p]
    return sup


def ancestors(inter, sup):
    out, stack = [], [inter]
    while stack:
        c = stack.pop()
        if c in out:
            continue
        out.append(c)
        stack.extend(s for s in sup.get(c, []) if s.startswith("net/minecraft/"))
    return out


def arity(desc):
    """number of params in a JVM descriptor"""
    params = desc[1:desc.index(")")]
    n, i = 0, 0
    while i < len(params):
        c = params[i]
        if c == "L":
            i = params.index(";", i) + 1
        elif c == "[":
            i += 1
            continue
        else:
            i += 1
        n += 1
    return n


def call_arity(src, open_idx):
    """count top-level args of the call whose '(' is at open_idx"""
    depth, i, n, empty = 0, open_idx, 0, True
    while i < len(src):
        c = src[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return 0 if empty else n + 1
        elif c == "," and depth == 1:
            n += 1
        elif depth >= 1 and not c.isspace():
            empty = False
        elif c == '"':
            j = i + 1
            while j < len(src) and src[j] != '"':
                j += 2 if src[j] == "\\" else 1
            i = j
        i += 1
    return -1


def main():
    src_path, out_path = sys.argv[1], sys.argv[2]
    renames = [a.split("=", 1) for a in sys.argv[3:] if "=" in a]
    moj_c, moj_m = mojmap.load(mojmap.MOJ)
    yarn_c, yarn_m = mojmap.load(mojmap.YARN)
    sup = load_hierarchy()
    moj_to_inter = {v: k for k, v in moj_c.items()}

    src = open(src_path, encoding="utf-8").read()

    # --- imports + simple names -------------------------------------------------
    imported = {}  # moj simple -> inter
    def fix_import(m):
        fqn = m.group(1)
        path = fqn.replace(".", "/")
        # nested classes: try progressively replacing trailing dots with $
        parts = path.split("/")
        for k in range(len(parts), 0, -1):
            cand = "/".join(parts[:k]) + ("$" + "$".join(parts[k:]) if k < len(parts) else "")
            if cand in moj_to_inter:
                inter = moj_to_inter[cand]
                yarn = yarn_c.get(inter, inter)
                imported[parts[-1]] = inter
                if k < len(parts):  # import of nested type keeps outer.inner form
                    outer_y = yarn_c.get(moj_to_inter["/".join(parts[:k])], "")
                    return "import " + outer_y.replace("/", ".") + "." + ".".join(
                        yarn.rsplit("$", len(parts) - k)[1:]) + ";"
                return "import " + yarn.replace("/", ".").replace("$", ".") + ";"
        return m.group(0) + " /*PORT? unknown class*/"

    # wildcard imports: expand to the classes of that Mojang package the file uses
    def fix_wildcard(m):
        pkg = m.group(1).replace(".", "/") + "/"
        used = set(re.findall(r"\b([A-Z]\w*)\b", src))
        out = []
        for moj, inter in moj_to_inter.items():
            if moj.startswith(pkg) and "/" not in moj[len(pkg):] and "$" not in moj[len(pkg):] \
                    and moj[len(pkg):] in used:
                out.append(fix_import(re.match(r"(.*)", moj.replace("/", "."))))
        return "\n".join(sorted(out)) if out else m.group(0)


    # static member imports: map the class, then the member
    statics = {}

    def fix_static(m):
        cls, member = m.group(1).rsplit(".", 1)
        line = fix_import(re.match(r"(.*)", cls))
        if "PORT?" in line:
            return m.group(0) + " /*PORT? unknown class*/"
        inter = imported[cls.rsplit(".", 1)[-1]]
        yname = member
        for (o, k, mi), (named, desc) in moj_m.items():
            if o == inter and named == member:
                yname = yarn_m.get((o, k, mi), (mi, desc))[0]
                break
        statics[member] = yname
        return "import static " + line[len("import "):-1] + "." + yname + ";"


    src = re.sub(r"import\s+((?:net\.minecraft|com\.mojang\.blaze3d)\.[\w.]+);", fix_import, src)
    src = re.sub(r"import\s+static\s+((?:net\.minecraft|com\.mojang\.blaze3d)\.[\w.]+);", fix_static, src)
    src = re.sub(r"import\s+((?:net\.minecraft|com\.mojang\.blaze3d)\.[\w.]+)\.\*;", fix_wildcard, src)

    # nested types referenced as Outer.Inner from imported outers
    for simple, inter in list(imported.items()):
        prefix = moj_c[inter] + "$"
        for i2, n2 in moj_c.items():
            if n2.startswith(prefix) and "$" not in n2[len(prefix):]:
                imported[simple + "." + n2[len(prefix):]] = i2

    def yarn_simple(inter):
        y = yarn_c.get(inter, inter)
        return y.rsplit("/", 1)[-1].replace("$", ".")

    last_import = src.rfind("\nimport ")
    code_start = src.index("\n", last_import + 1) if last_import >= 0 else 0
    head, body = src[:code_start], src[code_start:]
    # String literals are user-visible text (module/setting names): never rewrite inside them.
    strings = []

    def stash(m):
        strings.append(m.group(0))
        return "\"\u0000" + str(len(strings) - 1) + "\u0000\""

    body = re.sub(r'"(?:\\.|[^"\\\n])*"', stash, body)

    for simple in sorted(imported, key=len, reverse=True):
        ys = yarn_simple(imported[simple])
        if "." in simple:
            body = re.sub(r"(?<![\w.])" + re.escape(simple) + r"\b", ys, body)
        else:
            ys_top = yarn_c.get(imported[simple], "").rsplit("/", 1)[-1].split("$")[-1]
            body = re.sub(r"(?<![\w.$])" + re.escape(simple) + r"\b(?!\s*\()", ys_top, body)
            body = re.sub(r"\bnew\s+" + re.escape(simple) + r"\b", "new " + ys_top, body)

    for member, yname in statics.items():
        if member != yname:
            body = re.sub(r"(?<![\w.])" + re.escape(member) + r"\b", yname, body)

    # names this file declares itself (fields, methods, locals) are never MC members
    own = set(re.findall(r"\b(?:[\w<>\[\],.?]+)\s+(\w+)\s*[=;(,)]", body))

    # --- members --------------------------------------------------------------------
    classes = set()
    for inter in imported.values():
        classes.update(ancestors(inter, sup))
    for base in ("net/minecraft/class_310", "net/minecraft/class_746", "net/minecraft/class_1297",
                 "net/minecraft/class_1309", "net/minecraft/class_1657", "net/minecraft/class_638",
                 "net/minecraft/class_315", "net/minecraft/class_304", "net/minecraft/class_243",
                 "net/minecraft/class_238", "net/minecraft/class_1799", "net/minecraft/class_1937",
                 "net/minecraft/class_3532", "net/minecraft/class_636", "net/minecraft/class_634"):
        classes.update(ancestors(base, sup))
    by_name = {}  # moj name -> {(yarn name, arity or -1 for field)}
    for (o, k, mi), (named, desc) in moj_m.items():
        if o not in classes:
            continue
        y = yarn_m.get((o, k, mi), (mi, desc))[0]
        by_name.setdefault(named, set()).add((y, arity(desc) if k == "m" else -1))

    out, i = [], 0
    pat = re.compile(r"\.(\w+)\b")
    unresolved = set()
    for m in pat.finditer(body):
        name = m.group(1)
        out.append(body[i:m.start()])
        i = m.end()
        before = body[:m.start()]
        recv = re.search(r"(\w+)\s*$", before)
        r = recv.group(1) if recv else None
        cands = None if (r in JAVA_RECEIVERS or r in ECHO_CLASSES or name in own or name in NEVER_RENAME) else by_name.get(name)
        rest = body[m.end():]
        is_call = rest.lstrip().startswith("(")
        if not cands:
            out.append("." + name)
            continue
        if is_call:
            ar = call_arity(body, m.end() + (len(rest) - len(rest.lstrip())))
            opts = {y for y, a in cands if a == ar} or {y for y, a in cands if a >= 0}
        else:
            opts = {y for y, a in cands if a == -1}
        if len(opts) > 1 and name in opts:
            opts = {name}  # same name in both mappings: compiler flags the rare miss
        if len(opts) == 1:
            chosen = next(iter(opts))
            if os.environ.get("PORT_REPORT") and chosen != name:
                with open(os.environ["PORT_REPORT"], "a", encoding="utf-8") as rep:
                    rep.write(f"{name}->{chosen}\t{r}\t{os.path.basename(src_path)}\n")
            out.append("." + opts.pop())
        elif not opts:
            out.append("." + name)
        else:
            out.append("." + name + "/*PORT?" + "|".join(sorted(opts)) + "*/")
            unresolved.add(name)
    out.append(body[i:])
    body = "".join(out)

    body = re.sub("\"\u0000(\\d+)\u0000\"", lambda m: strings[int(m.group(1))], body)
    result = head + body
    for a, b in renames:
        result = result.replace(a, b)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    open(out_path, "w", encoding="utf-8").write(result)
    print(f"{os.path.basename(src_path)}: {len(imported)} MC types, ambiguous members: {sorted(unresolved) or 'none'}")


if __name__ == "__main__":
    main()
