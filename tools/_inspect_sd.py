import zipfile, re
z = zipfile.ZipFile(r'build\libs\utilitymod-cloak-1.0.0.jar')
names = [n for n in z.namelist() if 'Purge' in n]
print(names)
for n in names:
    d = z.read(n)
    consts = []
    for m in re.finditer(b'([ -~]{4,120})', d):
        s = m.group(1).decode()
        if s not in consts:
            consts.append(s)
    print('==', n)
    for c in consts[:16]:
        print('   ', repr(c))
