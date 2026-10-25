import zipfile, struct, collections

JAR = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\build\libs\utilitymod-cloak-1.0.0.jar'
OUT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\jar_strings.txt'

TAG_Utf8, TAG_Integer, TAG_Float, TAG_Long, TAG_Double = 1, 3, 4, 5, 6
TAG_Class, TAG_String = 7, 8
# tag -> extra bytes after tag byte
FIXED = {
    3: 4, 4: 4,          # Integer, Float
    5: 8, 6: 8,          # Long, Double (take two slots)
    7: 2, 8: 2,          # Class, String
    9: 4, 10: 4, 11: 4,  # Fieldref, Methodref, InterfaceMethodref
    12: 4,               # NameAndType
    15: 3,               # MethodHandle
    16: 2,               # MethodType
    17: 4, 18: 4,        # Dynamic, InvokeDynamic
    19: 2, 20: 2,        # Module, Package
}


def read_utf8(data, i):
    ln = struct.unpack('>H', data[i:i + 2])[0]
    raw = data[i + 2:i + 2 + ln]
    # class-file UTF-8: modified UTF-8 (handles \xc0\x80 for NUL, surrogate pairs)
    try:
        return raw.decode('utf-8'), i + 2 + ln
    except UnicodeDecodeError:
        return raw.decode('utf-8', errors='replace'), i + 2 + ln


def cp_strings(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return []
    cp_count = struct.unpack('>H', data[8:10])[0]
    i = 10
    out = []
    idx = 1
    while idx < cp_count:
        tag = data[i]
        i += 1
        if tag == TAG_Utf8:
            s, i = read_utf8(data, i)
            out.append(s)
            idx += 1
            continue
        if tag not in FIXED:
            raise ValueError('bad cp tag %d at %d' % (tag, i - 1))
        i += FIXED[tag]
        if tag in (5, 6):
            idx += 1  # Long/Double occupy two cp slots
        idx += 1
    return out


seen = collections.defaultdict(set)
z = zipfile.ZipFile(JAR)
for name in z.namelist():
    if not name.endswith('.class'):
        continue
    for s in cp_strings(z.read(name)):
        seen[s].add(name)

with open(OUT, 'w', encoding='utf-8') as f:
    for s in sorted(seen):
        f.write('%d\t%r\n' % (len(seen[s]), s))
print('unique utf8 constants:', len(seen))
