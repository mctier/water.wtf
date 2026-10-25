from pathlib import Path
import sys,json,shutil,hashlib
archive=Path(__file__).resolve().parent
target=Path(sys.argv[1]).resolve()
manifest=json.loads((archive/'manifest.json').read_text())
for p,h in manifest.items():
 source=archive/'baseline'/p
 assert hashlib.sha256(source.read_bytes()).hexdigest()==h
 dest=target/p;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
assert all(hashlib.sha256((target/p).read_bytes()).hexdigest()==h for p,h in manifest.items())
print('ROLLBACK PASS: 2 original SHA-256 hashes restored; original material restored')
