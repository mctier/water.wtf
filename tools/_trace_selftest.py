"""_trace_selftest.py — build a synthetic HPROF and check hprof_trace chains.

Expected: target "Silent Aim" found in byte[] 0x9002, chain
  java.lang.String.value -> com.utilitymod.FakeModule.name
plus STATIC com.utilitymod.FakeModule.inst.
"""
import struct
import subprocess
import sys

IDZ = 4
def pid(x): return struct.pack(">I", x)
def u4(x): return struct.pack(">I", x)
def u2(x): return struct.pack(">H", x)

def rec(tag, body):
    return bytes([tag]) + u4(0) + u4(len(body)) + body

def sub(tag, body):
    return bytes([tag]) + body

def sid(i, s):
    return rec(0x01, pid(i) + s.encode())

def loadcls(serial, cid, nid):
    return rec(0x02, u4(serial) + pid(cid) + u4(0) + pid(nid))

def clsdump(cid, super_, statics, fields):
    b = pid(cid) + u4(0) + pid(super_)
    b += pid(0) * 5
    b += u4(64)
    b += u2(0)                      # constant pool
    b += u2(len(statics))
    for nid, t, v in statics:
        b += pid(nid) + bytes([t]) + v
    b += u2(len(fields))
    for nid, t in fields:
        b += pid(nid) + bytes([t])
    return sub(0x20, b)

def inst(oid, cid, data):
    return sub(0x21, pid(oid) + u4(0) + pid(cid) + u4(len(data)) + data)

def parr(aid, typ, data):
    return sub(0x23, pid(aid) + u4(0) + u4(len(data)) + bytes([typ]) + data)

def oarr(aid, classid, elems):
    b = pid(aid) + u4(0) + u4(len(elems))
    b += pid(classid) + b"".join(pid(e) for e in elems)
    return sub(0x22, b)

# ids: 0x1000+ string table, 0x5000+ classes, 0x9000+ objects
S_CLS_FAKE = 0x1000
S_CLS_STRING = 0x1001
S_NAME = 0x1002
S_VALUE = 0x1003
S_HASH = 0x1005
S_INST = 0x1006

C_FAKE = 0x5000
C_STRING = 0x5001
C_OBJECT = 0x5002

O_STRING = 0x9001
O_BYTES = 0x9002
O_FAKE = 0x9003

head = b"JAVA PROFILE 1.0.2\x00" + u4(IDZ) + struct.pack(">Q", 0)
out = [head]
out.append(sid(S_CLS_FAKE, "com/utilitymod/FakeModule"))
out.append(sid(S_CLS_STRING, "java/lang/String"))
out.append(sid(S_NAME, "name"))
out.append(sid(S_VALUE, "value"))
out.append(sid(S_HASH, "hash"))
out.append(sid(S_INST, "inst"))
out.append(loadcls(1, C_FAKE, S_CLS_FAKE))
out.append(loadcls(2, C_STRING, S_CLS_STRING))
out.append(loadcls(3, C_OBJECT, 0x1007))

heap = b""
heap += clsdump(C_OBJECT, 0, [], [])
heap += clsdump(C_STRING, C_OBJECT, [], [(S_VALUE, 2), (S_HASH, 10)])
heap += clsdump(C_FAKE, C_OBJECT, [(S_INST, 2, pid(O_FAKE))],
                [(S_NAME, 2)])
heap += parr(O_BYTES, 8, b"Silent Aim")
heap += inst(O_STRING, C_STRING, pid(O_BYTES) + u4(0))
heap += inst(O_FAKE, C_FAKE, pid(O_STRING))
heap += oarr(0x9004, C_OBJECT, [O_FAKE])
out.append(rec(0x0C, heap))

path = "tools/_selftest.bin"
with open(path, "wb") as f:
    f.write(b"".join(out))

r = subprocess.run([sys.executable, "tools/hprof_trace.py", path],
                   capture_output=True, text=True, encoding="utf-8",
                   errors="replace")
print(r.stdout)
print(r.stderr, file=sys.stderr)
checks = {
    "word": "Silent Aim" in r.stdout,
    "str_value": "java/lang/String.value" in r.stdout,
    "mod_name": "FakeModule.name" in r.stdout,
    "static": "STATIC com/utilitymod/FakeModule.inst" in r.stdout,
}
print({k: v for k, v in checks.items()})
ok = all(checks.values())
print("SELFTEST", "PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)
