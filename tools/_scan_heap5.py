import re, sys

PATH = r'C:\Users\fakemoon\AppData\Local\Temp\umheap5.bin'
PHRASES = [b'shard_crystal', b'shard_sink', b'verso', b'purge', b'thiggerbot']
CTX = 220

def counts(data):
    low = data.lower()
    return {p.decode(): low.count(p) for p in PHRASES if low.count(p)}

data = open(PATH, 'rb').read()
low = data.lower()
print('== totals ==')
tot = {p.decode(): low.count(p) for p in PHRASES}
for k, v in sorted(tot.items(), key=lambda x: -x[1]):
    print(k, v)

print('== context (deduped) ==')
seen = set()
for p in PHRASES:
    start = 0
    shown = 0
    while shown < 12:
        i = low.find(p, start)
        if i < 0:
            break
        start = i + 1
        ctx = data[max(0, i - CTX):i + CTX]
        # printable-run extraction: keep runs of >=4 printable chars
        runs = re.findall(rb'[\x20-\x7e]{4,}', ctx)
        key = (p.decode(), tuple(runs)[:6])
        if key in seen:
            continue
        seen.add(key)
        shown += 1
        print(f'--- {p.decode()} @ {i}: ' + b' | '.join(runs[:8]).decode('ascii', 'replace'))
