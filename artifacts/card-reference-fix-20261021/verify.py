from pathlib import Path
import sys
r=Path(sys.argv[1]);mode=sys.argv[2]
c=(r/'src/client/java/com/utilitymod/gui/CardGui.java').read_text();t=(r/'src/client/java/dev/koifih/client/ui/component/TextInput.java').read_text()
if mode=='baseline':
 assert 'ofPattern(LT.L14' in c and 'search.setPlaceholder(' not in c
 assert 'Text.wrap(desc,' in c and 'customSurface' not in t
 print('PASS: baseline reproduces empty placeholder, literal date pattern and unwrapped cloaked spaces')
else:
 assert 'ofPattern("d MMM uuuu, HH:mm"' in c
 assert 'search.setPlaceholder("Search modules...")' in c and 'search.setSurface(' in c
 assert 'profileInput.setFocused(true)' in c and 'search.setFocused(true)' in c
 assert "desc.replace('\\uE120', ' ')" in c
 assert 'Text.fit(lines.get(i), w-28*scale' in c
 assert 'customSurface' in t and 'else field(graphics)' in t
 assert 'glassReady ? .22f' in c and 'CardGuiIcons.draw' in c
 print('PASS: search skin/focus, real date, bounded wrapping, translucent material and outline icons verified')
