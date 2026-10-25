from pathlib import Path
import sys,hashlib,shutil
a=Path(__file__).resolve().parent
b=a/"shader-test/baseline/lg_tinted.frag"
assert hashlib.sha256(b.read_bytes()).hexdigest()==(a/"original.sha256").read_text()
p=Path(sys.argv[1])/"src/main/resources/assets/utilitymod/shaders/glass/lg_tinted.frag"
p.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(b,p)
assert p.read_bytes()==b.read_bytes()
print("ROLLBACK PASS: original shader hash and single-gradient material restored")
