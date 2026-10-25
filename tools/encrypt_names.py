#!/usr/bin/env python3
"""
encrypt_names.py — build-time helper for ProtectedText.fromInts(...).

Encoding must stay identical to:
  - spudfuscator-api/.../ProtectedText.java   (source-side)
  - .../bytecode/transform/HeapCloak.java     (bytecode-side)
  - the RT.p([B) runtime method                (materialized runtime)

Formula: enc[i] = plain[i] ^ ((0x1E ^ (i * 0x37)) & 0xFF)

Usage:
    python tools/encrypt_names.py "Aura Guide" "Silent Aim" "Yaw Assist"
    python tools/encrypt_names.py --decode 0x5F 0x30 0x18 ...      # sanity-check a paste-back
    python tools/encrypt_names.py --stdin < strings.txt            # one per line
"""
from __future__ import annotations

import argparse
import sys
from typing import Iterable

KEY_A = 0x1E
KEY_STEP = 0x37


def key(i: int) -> int:
    return (KEY_A ^ (i * KEY_STEP)) & 0xFF


def encrypt(plain: str) -> list[int]:
    data = plain.encode("utf-8")
    return [(b ^ key(i)) & 0xFF for i, b in enumerate(data)]


def decrypt(enc: Iterable[int]) -> str:
    out = bytearray()
    for i, b in enumerate(enc):
        out.append((b ^ key(i)) & 0xFF)
    return out.decode("utf-8", errors="replace")


def fmt_java(enc: list[int]) -> str:
    hex_bytes = ",".join(f"0x{b:02X}" for b in enc)
    return f"ProtectedText.fromInts(new int[]{{{hex_bytes}}})"


def main() -> int:
    ap = argparse.ArgumentParser(description="Encrypt plaintext for ProtectedText.fromInts(...).")
    ap.add_argument("strings", nargs="*", help="plaintext strings to encrypt")
    ap.add_argument("--stdin", action="store_true", help="read one string per line from stdin")
    ap.add_argument("--decode", action="store_true", help="decode int array (positional args)")
    ap.add_argument("--raw", action="store_true", help="emit only the int list (no Java wrapper)")
    args = ap.parse_args()

    if args.decode:
        try:
            ints = [int(x, 0) & 0xFF for x in args.strings]
        except ValueError as e:
            print(f"error: bad int in --decode input: {e}", file=sys.stderr)
            return 2
        print(decrypt(ints))
        return 0

    inputs: list[str] = []
    if args.stdin:
        inputs.extend(line.rstrip("\n") for line in sys.stdin if line.strip())
    inputs.extend(args.strings)

    if not inputs:
        ap.print_help(sys.stderr)
        return 2

    for s in inputs:
        enc = encrypt(s)
        if args.raw:
            print(",".join(f"0x{b:02X}" for b in enc))
        else:
            print(f"// {s!r}")
            print(fmt_java(enc) + ",")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
