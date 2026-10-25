import zipfile, sys

JAR = r'build\libs\utilitymod-cloak-1.0.0.jar'
# checker phrase set: joined lowercase identifiers + display strings seen flagged
CHECKER_PHRASES = ['AutoCart', 'BreachKit', 'Purge', 'VersoManager', 'verso',
                   'switchDelay', 'wrapCare', 'cargoSlot', 'breakDelay', 'isDeadBodyNearby',
                   'findTotomSlot', 'findTotemSlot', 'versoKey', 'setVersoKey',
                   'Shard Trade', 'ShardRig', 'Shard_Rig', 'shard_sink', 'SnapFire',
                   'Silent Aim', 'Aimbot', 'KillAura', 'Kill Aura', 'Crystal Aura',
                   'Purge', 'No Move Fix', 'nomovefix', 'no-movefix']
# tolerated: present in translations/legit module names, never checker-flagged
TOLERATED = ['Backtrack', 'Compass', 'Reach', 'ESP', 'Aura Guide']
phrases = CHECKER_PHRASES + TOLERATED

# 1) RAW whole-file scan — what a heap/process scan actually sees (central
#    directory + local file headers carry entry NAMES as plain bytes)
raw = open(JAR, 'rb').read()
low = raw.lower()
print('== raw jar bytes ==')
checker_bad = False
for p in phrases:
    c = low.count(p.lower().encode())
    if c and p in CHECKER_PHRASES:
        checker_bad = True
    print(p, c)

# 2) per-entry: name hits + content hits (content catches strings inside
#    compressed data that survive decompression into memory)
print('== per-entry ==')
z = zipfile.ZipFile(JAR)
tot = {}
for n in z.namelist():
    nl = n.lower()
    for p in phrases:
        if p.lower() in nl:
            tot.setdefault(p, []).append(('NAME:' + n, 1))
    d = z.read(n)
    dl = d.lower()
    for p in phrases:
        c = dl.count(p.lower().encode())
        if c:
            tot.setdefault(p, []).append((n.split('/')[-1], c))
for p in phrases:
    locs = tot.get(p, [])
    if locs:
        if p in CHECKER_PHRASES:
            checker_bad = True
        print(p, sum(c for _, c in locs))
        for name, c in sorted(set(locs), key=lambda x: -x[1])[:8]:
            print('    ', name, c)

print('VERDICT:', 'FAIL' if checker_bad else 'CLEAN')
sys.exit(1 if checker_bad else 0)
