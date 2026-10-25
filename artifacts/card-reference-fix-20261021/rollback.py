from pathlib import Path
import json,shutil,sys
base=Path(__file__).resolve().parent;root=Path(sys.argv[1]).resolve()
for name,h in json.loads((base/'manifest.json').read_text()).items():
 p=root/name
 if h is None:
  if p.exists(): p.unlink()
 else:
  p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(base/'baseline'/name,p)
print('PASS: original files restored; new icon helper removed')
