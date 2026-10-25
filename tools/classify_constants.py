import collections

SRC = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\jar_strings.txt'
OUT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\leak_candidates.txt'

PREFIXES = ('java/', 'javax/', 'jdk/', 'sun/', 'org/', 'com/google/', 'it/', 'kotlin/',
            'net/fabricmc/', 'net/minecraft/', 'class_', 'method_', 'field_',
            'com/intellij', 'io/netty', 'gnu/', 'cpw/', 'com/mojang')

META = ('sourceFile', 'LineNumberTable', 'LocalVariableTable', 'StackMapTable', 'Code',
        'InnerClasses', 'Signature', 'Exceptions', 'ConstantValue', 'BootstrapMethods',
        'NestHost', 'NestMembers', 'PermittedSubtitles', 'RuntimeVisibleAnnotations',
        'SourceDebugExtension', 'methodParameters', 'descriptor', 'weave', 'remapped',
        'intermediary', 'named', 'searge', 'this', 'lambda$', 'access$')

STRIP_EXT = ('.json', '.png', '.ogg', '.txt', '.ttf', '.class', '.java', '.html', '.js',
             '.css', '.tga', '.properties', '.mcmeta', '.glsl', '.fsh', '.vsh', '.zip')

buckets = collections.Counter()
leaks = []
for line in open(SRC, encoding='utf-8'):
    cnt, s = line.rstrip('\n').split('\t')
    s = eval(s)
    if len(s) < 4:
        buckets['tiny'] += 1
        continue
    if any(s.endswith(e) for e in STRIP_EXT):
        buckets['asset/meta'] += 1
        continue
    if s.startswith(PREFIXES):
        buckets['keep: pkg/ident'] += 1
        continue
    if s.startswith(('(', '[')) or (s.startswith('L') and '/' in s):
        buckets['keep: descriptor'] += 1
        continue
    if s in META or s.startswith(META):
        buckets['keep: jvm meta'] += 1
        continue
    if all(c.isdigit() or c in '.-+eE' for c in s):
        buckets['keep: numeric'] += 1
        continue
    if ' ' not in s and ('/' in s or '.' in s):
        buckets['keep: token'] += 1
        continue
    leaks.append((s, int(cnt)))

print('buckets:')
for k, v in buckets.most_common():
    print(' ', k, v)
print('candidates:', len(leaks))
with open(OUT, 'w', encoding='utf-8') as f:
    for s, cnt in sorted(leaks, key=lambda x: -x[1]):
        f.write('%d\t%r\n' % (cnt, s))
