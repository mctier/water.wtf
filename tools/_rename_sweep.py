import os, re, glob, shutil

ROOT = r'src'

def sweep_file(fp):
    txt = open(fp, encoding='utf-8', errors='strict').read()
    orig = txt
    subs = [
        (r'setVersoKey', 'setPanelKey'),
        (r'versoKey', 'panelKey'),
        (r'wrapCare', 'safetyGate'),
        (r'cargoSlot', 'burstRow'),
        (r'breakDelay', 'crackCooldown'),
        (r'isDeadBodyNearby', 'corpseNearby'),
        (r'findTotemSlot', 'charmIndex'),
        (r'switchDelay', 'handHold'),
        (r'VersoManager', 'FrameManager'),
        (r'\bVersoCore\b', 'Wing'),
        (r'\bAutoCart\b', 'Locomotive'),
        (r'\bBreachKit\b', 'AegisSplit'),
        (r'\bPurge\b', 'ExitGate'),
        (r'ui\.verso', 'ui.wing'),
        (r'screens\.verso', 'screens.frame'),
        (r'(?i)verso', 'wing'),
    ]
    n = 0
    for pat, rep in subs:
        txt, k = re.subn(pat, rep, txt)
        n += k
    if txt != orig:
        open(fp, 'w', encoding='utf-8', newline='').write(txt)
    return n

total = 0
touched = []
for fp in glob.glob(os.path.join(ROOT, '**', '*.java'), recursive=True):
    k = sweep_file(fp)
    if k:
        touched.append((fp.replace('src' + os.sep, ''), k))
        total += k

print('files touched:', len(touched))
for t in touched:
    print('  ', t[0], t[1])
print('total subs:', total)

# directory + file renames
moves = [
    (r'src\client\java\dev\koifih\client\ui\verso', r'src\client\java\dev\koifih\client\ui\wing'),
    (r'src\client\java\com\utilitymod\echo\screens\verso', r'src\client\java\com\utilitymod\echo\screens\frame'),
]
for src, dst in moves:
    if os.path.isdir(src):
        shutil.move(src, dst)
        print('dir moved:', src, '->', os.path.basename(dst))
    else:
        print('MISSING DIR:', src)

class_moves = {
    r'src\client\java\com\utilitymod\echo\features\impl\combat\AutoCart.java': r'src\client\java\com\utilitymod\echo\features\impl\combat\Locomotive.java',
    r'src\client\java\com\utilitymod\echo\features\impl\combat\BreachKit.java': r'src\client\java\com\utilitymod\echo\features\impl\combat\AegisSplit.java',
    r'src\client\java\com\utilitymod\echo\features\impl\misc\Purge.java': r'src\client\java\com\utilitymod\echo\features\impl\misc\ExitGate.java',
    r'src\client\java\com\utilitymod\gui\VersoManager.java': r'src\client\java\com\utilitymod\gui\FrameManager.java',
    r'src\client\java\dev\koifih\client\ui\wing\VersoCore.java': r'src\client\java\dev\koifih\client\ui\wing\Wing.java',
}
for src, dst in class_moves.items():
    if os.path.isfile(src):
        shutil.move(src, dst)
        print('file moved:', os.path.basename(src), '->', os.path.basename(dst))
    else:
        print('MISSING FILE:', src)
