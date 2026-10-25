"""Boundary patches: unmask PUA carriers only at functional seams."""
import os, io

ROOT = r'C:\Users\fakemoon\Documents\Default Project\utility-mod\src\client\java\com\utilitymod'

PATCHES = [
    # ModuleChat: chat components render through the vanilla font; module names arrive as
    # PUA carriers, so unmask at the literal() build site.
    (ROOT + r'\anubis\chat\ModuleChat.java',
     '          .append(Text.literal(module.name()).withColor(16777215))',
     '          .append(Text.literal(com.utilitymod.anubis.util.TextMask.plain(module.name())).withColor(16777215))'),
    # Web adapter: enum option compare + option values cross the wire as plain text.
    (ROOT + r'\web\GameWebAdapter.java',
     'for(T t:values)if(t.name().equals(v.getAsString())){write.accept(t);return;}',
     'for(T t:values)if(plain(t.name()).equals(v.getAsString())){write.accept(t);return;}'),
    (ROOT + r'\web\GameWebAdapter.java',
     'o.addProperty("value",value.toString());',
     'o.addProperty("value",plain(value));'),
    # Bind restore: config holds plain keys; module names are carriers.
    (ROOT + r'\gui\ModuleBinds.java',
     'if (am.name().toLowerCase(Locale.ROOT).equals(name)) {',
     'if (com.utilitymod.anubis.util.TextMask.key(am.name()).equals(name)) {'),
]

for path, old, new in PATCHES:
    src = io.open(path, encoding='utf-8').read()
    if new in src:
        print('already', os.path.basename(path), '->', new[:40])
        continue
    if old not in src:
        print('MISS', os.path.basename(path), '->', old[:60])
        continue
    src = src.replace(old, new)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(src)
    print('patched', os.path.basename(path), '->', new[:48])
