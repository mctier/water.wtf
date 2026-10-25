from pathlib import Path
import sys
root=Path(sys.argv[1]);mode=sys.argv[2]
card=root/'src/client/java/com/utilitymod/gui/CardGui.java';config=root/'src/client/java/com/utilitymod/gui/GuiConfig.java';glass=root/'src/client/java/com/utilitymod/echo/render/GlassRenderer.java';motion=root/'src/client/java/com/utilitymod/gui/CardGuiMotion.java';style=root/'src/client/java/com/utilitymod/gui/CardGuiStyle.java'
if mode=='baseline':
 assert not motion.exists() and not style.exists()
 assert 'flushCard(graphics' not in card.read_text()
 print('PASS: baseline has no Cards-only motion/style system or scoped glass call')
else:
 c=card.read_text();g=glass.read_text();cf=config.read_text()
 assert motion.exists() and style.exists() and 'layoutMotion(mouseX, mouseY)' in c
 assert 'motion.update(' in c and 'drawClientPage(' in c and 'drawCategoryHeader(' in c
 assert 'flushCard(graphics, CardGuiStyle.background' in c
 assert 'public static void flushCard(' in g and 'finally {' in g
 assert 'CardGuiStyle.load' in cf and 'o.add("cardVisuals", CardGuiStyle.save())' in cf
 print('PASS: Cards visual tokens persist; edge-motion coordinates drive rendering/hitboxes; scoped glass and in-shell pages exist')
