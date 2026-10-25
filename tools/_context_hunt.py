import os, re, zipfile

PHRASES = [b'shard_crystal', b'shard_sink', b'verso', b'purge', b'snapfire']

# 1) context around every hit in umheap4.bin
p = os.path.join(os.environ['TEMP'], 'umheap4.bin')
with open(p, 'rb') as fh:
    data = fh.read()
low = data.lower()
shown = 0
seen_kinds = set()
for ph in PHRASES:
    start = 0
    while True:
        i = low.find(ph, start)
        if i < 0:
            break
        start = i + 1
        ctx = data[max(0, i - 140):i + 140]
        printable = re.sub(rb'[^\x20-\x7e]', b'.', ctx)
        # dedupe similar contexts
        key = printable[:60]
        if key in seen_kinds:
            continue
        seen_kinds.add(key)
        print(b'CONTEXT %s @%d:' % (ph, i), printable)
        shown += 1
        if shown > 24:
            break
    if shown > 24:
        break

# 2) full .minecraft sweep (all extensions, skip huge asset dirs)
mc = os.path.join(os.environ['APPDATA'], '.minecraft')

def jar_scan(path):
    try:
        z = zipfile.ZipFile(path)
        tot = {}
        for n in z.namelist():
            if n.endswith('/'):
                continue
            from zipfile import ZipInfo
            info = z.getinfo(n)
            if info.file_size > 60 * 1024 * 1024:
                continue
            try:
                d = z.read(n)
            except Exception:
                continue
            for k, v in counts(d).items():
                tot[k] = tot.get(k, 0) + v
        return tot
    except Exception:
        return None

SKIP_DIRS = {'assets'}  # vanilla assets = gigabytes of unrelated data
print('== full .minecraft sweep ==')
for root, dirs, files in os.walk(mc):
    dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
    for f in files:
        path = os.path.join(root, f)
        try:
            sz = os.path.getsize(path)
        except OSError:
            continue
        rel = os.path.relpath(path, mc)
        try:
            if f.endswith('.jar') or sz > 8 * 1024 * 1024:
                r = jar_scan(path)
                if r:
                    print(rel, r)
            else:
                with open(path, 'rb') as fh:
                    d = fh.read()
                r = counts(d)
                if r:
                    print(rel, r)
        except Exception as e:
            print(rel, 'ERR', e)

# 3) desktop new folder + checker folder
print('== desktop\\New folder sweep ==')
dnf = r'C:\Users\fakemoon\Desktop\New folder'
if os.path.isdir(dnf):
    for root2, dirs2, files2 in os.walk(dnf):
        for f in files2:
            path = os.path.join(root2, f)
            try:
                if f.endswith('.jar'):
                    r = jar_scan(path)
                else:
                    with open(path, 'rb') as fh:
                        d = fh.read()
                    r = counts(d)
                if r:
                    print(os.path.relpath(path, dnf), r)
            except Exception:
                pass
else:
    print('MISSING')
