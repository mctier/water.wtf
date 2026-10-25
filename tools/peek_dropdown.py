import io, re, sys
p = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java\com\utilitymod\gui\DropdownGui.java'
lines = io.open(p, encoding='utf-8', errors='replace').read().split('\n')
print('total lines', len(lines))
for i, l in enumerate(lines, 1):
    if 'plainString' in l or 'TextMask.plain' in l:
        print('%5d  %s' % (i, l.strip()[:150].encode('ascii', 'backslashreplace').decode()))
