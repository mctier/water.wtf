import os, gzip, zipfile

PHRASES = [b'shard_crystal', b'shard_sink', b'verso', b'purge', b'snapfire']

def counts(data):
    low = data.lower()
    return {p.decode(): low.count(p) for p in PHRASES if low.count(p)}

def jar_counts(p):
    z = zipfile.ZipFile(p)
    tot = {}
    for n in z.namelist():
        if n.endswith('/'):
            continue
        try:
            d = z.read(n)
        except Exception:
            continue
        for k, v in counts(d).items():
            tot[k] = tot.get(k, 0) + v
    return tot if tot else None

def outer(path, tag, jar_ok=True, text_ok=True, gz_ok=False):
    try:
        if path.endswith('.jar') and jar_ok:
            c = jar_counts(path)
        elif path.endswith('.gz') and gz_ok:
            c = counts(gzip.open(path, 'rb').read())
        elif text_ok and path.endswith(('.json', '.txt', '.log')):
            c = counts(open(path, 'rb').read())
        else:
            return
        if c:
            print(tag, c)
    except Exception as e:
        print(tag, 'ERR', e)

mc = os.path.join(os.environ['APPDATA'], '.minecraft')
print('==', mc, 'exists' if os.path.isdir(mc) else 'MISSING')
if os.path.isdir(mc):
    mods = os.path.join(mc, 'mods')
    if os.path.isdir(mods):
        for f in sorted(os.listdir(mods)):
            outer(os.path.join(mods, f), 'mods/' + f)
    logs = os.path.join(mc, 'logs')
    if os.path.isdir(logs):
        for f in sorted(os.listdir(logs)):
            outer(os.path.join(logs, f), 'logs/' + f, jar_ok=False, gz_ok=True)
    for f in sorted(os.listdir(mc)):
        p = os.path.join(mc, f)
        if os.path.isfile(p):
            outer(p, f, jar_ok=False)
    cfg = os.path.join(mc, 'config')
    if os.path.isdir(cfg):
        for root, dirs, files in os.walk(cfg):
            for f in files:
                outer(os.path.join(root, f), 'config/' + f)
    ess = os.path.join(mc, 'essential')
    if os.path.isdir(ess):
        for root, dirs, files in os.walk(ess):
            for f in files:
                outer(os.path.join(root, f), 'essential/' + f)

run = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\run'
print('==', run, 'exists' if os.path.isdir(run) else 'MISSING')
if os.path.isdir(run):
    hit = False
    for root, dirs, files in os.walk(run):
        for f in files:
            c_and_p = os.path.join(root, f)
            try:
                if f.endswith('.jar') and jar_counts(c_and_p):
                    print('run/' + c_and_p[len(run) + 1:], 'HIT')
                    hit = True
                elif f.endswith(('.json', '.log', '.txt')) and counts(open(c_and_p, 'rb').read()):
                    print('run/' + c_and_p[len(run) + 1:], 'HIT')
                    hit = True
            except Exception:
                pass
    if not hit:
        print('run dir: no hits')
