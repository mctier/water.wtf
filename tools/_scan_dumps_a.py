import os

PHRASES = [b'shard_crystal', b'shard_sink', b'verso', b'purge', b'snapfire']

def counts(data):
    low = data.lower()
    return {p.decode(): low.count(p) for p in PHRASES if low.count(p)}

tmp = os.environ['TEMP']
for f in ['umheap.bin', 'umheap2.bin', 'umheap3.bin', 'umheap_scan.txt']:
    p = os.path.join(tmp, f)
    if not os.path.exists(p):
        print(f, 'MISSING')
        continue
    with open(p, 'rb') as fh:
        c = counts(fh.read())
    print(f, os.path.getsize(p), c if c else 'CLEAN')
