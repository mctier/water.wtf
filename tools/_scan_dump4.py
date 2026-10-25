import os

PHRASES = [b'shard_crystal', b'shard_sink', b'verso', b'purge', b'snapfire']

def counts(data):
    low = data.lower()
    return {p.decode(): low.count(p) for p in PHRASES if low.count(p)}

p = os.path.join(os.environ['TEMP'], 'umheap4.bin')
with open(p, 'rb') as fh:
    c = counts(fh.read())
print('umheap4.bin', os.path.getsize(p), c if c else 'CLEAN')
