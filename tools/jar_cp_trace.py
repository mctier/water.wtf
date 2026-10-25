import zipfile, struct, io

JAR = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\build\libs\utilitymod-cloak-1.0.0.jar'
TARGETS = ['None', 'Lava', 'Strict', 'Fists', 'Pink', 'Russian', 'Liquids',
           'Tinted', 'English', 'Thunder', 'shielded', 'Liquids', 'Added',
           'TRANSLATIONS', 'Sources', 'Thickness', 'Bubble', 'Editing',
           'Presets', 'trigger', 'WARNING', 'jumpBoost']

z = zipfile.ZipFile(JAR)


def cp_entries(data):
    (magic, minor, major, cnt) = struct.unpack_from('>IHHH', data, 0)
    i = 10
    out = []
    n = 1
    while n < cnt:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack_from('>H', data, i + 1)[0]
            s = data[i + 3:i + 3 + ln]
            out.append(('utf8', n, s.decode('utf-8', 'replace')))
            i += 3 + ln
            n += 1
        elif tag in (7, 8, 16, 19, 20):
            out.append((tag, n, struct.unpack_from('>H', data, i + 1)[0]))
            i += 3; n += 1
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            out.append((tag, n, None)); i += 5; n += 1
        elif tag in (5, 6):
            out.append((tag, n, None)); i += 9; n += 2
        elif tag == 15:
            out.append((tag, n, None)); i += 4; n += 1
        else:
            raise ValueError('tag %d @%d' % (tag, i))
        if tag == 7:
            pass
    # second pass: string constants = tag8 -> utf8 idx
    strings = set()
    classes = set()
    for (t, idx, v) in out:
        if t == 8:
            strings.add(v)
        if t == 7 and isinstance(v, int):
            classes.add(v)
    utf8 = {idx: v for (t, idx, v) in out if t == 'utf8'}
    str_const = {utf8[v] for v in strings if v in utf8}
    cls_names = {utf8[v] for v in classes if v in utf8}
    return str_const, cls_names, utf8


for name in z.namelist():
    if not name.endswith('.class'):
        continue
    data = z.read(name)
    str_const, cls_names, utf8 = cp_entries(data)
    hits = [t for t in TARGETS if t in str_const]
    if hits:
        print('%-70s STRINGS %s' % (name, hits))
