import os, re, zipfile, hashlib

PHRASES = [b'shard_crystal', b'shard_sink', b'verso', b'purge', b'snapfire']

def scan_bytes(data, tag):
    low = data.lower()
    out = {}
    for p in PHRASES:
        c = low.count(p)
        if c:
            out[p.decode()] = c
    return out

def scan_zip(path):
    try:
        z = zipfile.ZipFile(path)
    except Exception as e:
        return None
    tot = {}
    for n in z.namelist():
        if n.endswith('/'):
            continue
        try:
            d = z.read(n)
        except Exception:
            continue
        for k, v in scan_bytes(d, n).items():
            tot[k] = tot.get(k, 0) + v
    return tot

# 1. all jars in build/libs
libs = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\build\libs'
print('== build/libs jars ==')
for f in sorted(os.listdir(libs)):
    if not f.endswith('.jar'):
        continue
    p = os.path.join(libs, f)
    r = scan_zip(p)
    if r:
        print(f, r)

# 2. essential temp jars - dedupe by size+hash of first sample per size-group
print('== essential temp jars (deduped) ==')
tmp = os.environ['TEMP']
groups = {}
for f in os.listdir(tmp):
    if f.startswith('essential-') and f.endswith('.jar'):
        p = os.path.join(tmp, f)
        sz = os.path.getsize(p)
        groups.setdefault(sz, []).append(p)
for sz, paths in sorted(groups.items()):
    with open(paths[0], 'rb') as fh:
        h = hashlib.md5(fh.read()).hexdigest()
    variants = {h}
    for p in paths[1:6]:
        with open(p, 'rb') as fh:
            variants.add(hashlib.md5(fh.read()).hexdigest())
    r = scan_zip(paths[0])
    print(f'{sz} bytes x{len(paths)} files, {len(variants)} unique variants', r if r else 'CLEAN')
