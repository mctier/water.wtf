#!/usr/bin/env python3
"""
rename_modules.py — source-tree scanner. Walks .java under a root and reports
any plaintext string literal that carries a scanner-dictionary term with
word-boundary framing. With --decrypt it also decodes ProtectedText.fromInts
arrays and checks the plaintext for the same terms (in case the encrypted
name itself contains a flagged phrase).

Exits non-zero when hits > 0 so CI can gate builds.

Usage:
    python tools/rename_modules.py                       # scan ./src
    python tools/rename_modules.py path/to/src
    python tools/rename_modules.py --decrypt src         # also inspect encrypted names
    python tools/rename_modules.py --dict extra.txt src  # add custom terms
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from typing import Iterable

# Must match HeapCloakDict.BUILT_IN exactly.
BUILT_IN = [
    # combat
    "Kill Aura", "Crystal Aura", "Mace Aura", "Silent Mace",
    "Aura Guide", "Silent Aim", "Bow Aimbot", "SnapFire",
    "Anti-Weakness", "Fake Punch", "Damage Tick", "Only Crit",
    "Static HitBoxes", "Shield Disabler", "AntiInvis", "W-Tap",
    # crystal
    "Shard Hack", "Crystal Optimizer", "CwCrystal", "Double Anchor",
    "Anchor Exploder", "Auto Dtap", "Shard Rig",
    # totem
    "Auto Totem", "Totem Offhand", "Hover Totem", "Force Totem", "Auto Retotem",
    # movement
    "Fast Bridge", "Bridge Assist", "Fast Swim", "Fast Place",
    "No Break Delay", "No Jump Delay", "Elytra Swap", "Elytra Glide",
    "Jetpack", "Auto Sprint", "InventoryMove",
    # utility
    "Auto Clicker", "Auto Pot", "Auto Eat", "Auto XP", "Auto Armor",
    "Auto Tool", "Auto Mine", "Chest Stealer", "Shulker Dropper",
    "Auto Sell", "Cord Snapper", "Key Pearl", "Auto Tpa",
    "Bed Macro", "Auto Restock", "Replace Mod",
    # esp
    "Player ESP", "Storage ESP", "Entity ESP", "X-Ray",
    "Health Indicators", "Target HUD", "Netherite Finder",
    # evasion
    "Fake Lag", "Ping Spoof", "Pack Spoof", "Stray Bypass",
    "Anti SS Tool", "String Cleaner", "Purge", "USN Journal Cleaner",
]

STRING_LITERAL = re.compile(r'"(?:[^"\\]|\\.)*"')
PROTECTED_TEXT_CALL = re.compile(
    r'ProtectedText\.fromInts\(new\s+int\s*\[\s*\]\s*\{([^}]+)\}\)',
    re.MULTILINE,
)

KEY_A = 0x1E
KEY_STEP = 0x37


def key(i: int) -> int:
    return (KEY_A ^ (i * KEY_STEP)) & 0xFF


def decrypt(ints: Iterable[int]) -> str:
    out = bytearray()
    for i, b in enumerate(ints):
        out.append((b ^ key(i)) & 0xFF)
    return out.decode("utf-8", errors="replace")


def compile_pattern(terms: list[str]) -> re.Pattern[str]:
    terms = sorted({t.strip() for t in terms if t and t.strip()}, key=len, reverse=True)
    body = "|".join(re.escape(t) for t in terms)
    return re.compile(r"(?<![A-Za-z0-9])(" + body + r")(?![A-Za-z0-9])", re.IGNORECASE)


def line_of(src: str, offset: int) -> int:
    return src.count("\n", 0, offset) + 1


def scan_file(path: str, pattern: re.Pattern[str], decrypt_arrays: bool) -> list[tuple[str, int, str, str]]:
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            src = f.read()
    except OSError:
        return []
    hits: list[tuple[str, int, str, str]] = []
    for m in STRING_LITERAL.finditer(src):
        body = m.group(0)[1:-1]
        for hit in pattern.finditer(body):
            hits.append((path, line_of(src, m.start()), hit.group(1), m.group(0)))
    if decrypt_arrays:
        for m in PROTECTED_TEXT_CALL.finditer(src):
            raw_ints = m.group(1)
            try:
                ints = [int(x.strip(), 0) & 0xFF for x in raw_ints.split(",") if x.strip()]
            except ValueError:
                continue
            plain = decrypt(ints)
            for hit in pattern.finditer(plain):
                hits.append((
                    path,
                    line_of(src, m.start()),
                    hit.group(1),
                    f'[decrypted] "{plain}"',
                ))
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description="Scan a source tree for plaintext scanner-dictionary hits.")
    ap.add_argument("root", nargs="?", default="src", help="source root to walk (default: src)")
    ap.add_argument("--decrypt", action="store_true",
                    help="also decode ProtectedText.fromInts arrays and re-check")
    ap.add_argument("--dict", default=None, help="path to extra dictionary file (one term per line)")
    ap.add_argument("--json", action="store_true", help="emit findings as JSON")
    args = ap.parse_args()

    terms = list(BUILT_IN)
    if args.dict and os.path.exists(args.dict):
        with open(args.dict, "r", encoding="utf-8") as f:
            for line in f:
                s = line.strip()
                if s and not s.startswith("#"):
                    terms.append(s)
    pattern = compile_pattern(terms)

    if not os.path.exists(args.root):
        print(f"error: root does not exist: {args.root}", file=sys.stderr)
        return 2

    all_hits: list[tuple[str, int, str, str]] = []
    for dirpath, _, filenames in os.walk(args.root):
        for fn in filenames:
            if fn.endswith(".java"):
                all_hits.extend(scan_file(os.path.join(dirpath, fn), pattern, args.decrypt))

    if args.json:
        import json
        print(json.dumps([
            {"file": p, "line": l, "term": t, "context": c[:200]}
            for p, l, t, c in all_hits
        ], indent=2))
    else:
        for path, line, term, ctx in all_hits:
            snippet = ctx if len(ctx) <= 120 else ctx[:117] + "..."
            print(f"[FLAGGED] {path}:{line}  term={term!r}  in {snippet}")
        print()
        if all_hits:
            print(f"{len(all_hits)} flagged plaintext occurrence(s).")
        else:
            print("[ CLEAN ] no plaintext dictionary hits under " + args.root)
    return 1 if all_hits else 0


if __name__ == "__main__":
    raise SystemExit(main())
