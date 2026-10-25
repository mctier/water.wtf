#!/usr/bin/env python3
"""
cloak_translations.py — rewrite flagged values in Lang JSON resources as
\\uE1XX escape sequences so the on-disk .json file bytes don't carry any
scanner-dictionary plaintext, and the Gson-parsed values on the heap are
Private-Use-Area chars (U+E100..U+E1FF), which Aho-Corasick ASCII/UTF-16
pattern DBs can't match.

Must stay bit-identical to HeapCloak / ProtectedText for the PUA mapping:
  PUA_BASE = 0xE100
  rawChar  = (char) (PUA_BASE | (byte & 0xFF))

Usage:
    python tools/cloak_translations.py                    # cloak every lang
    python tools/cloak_translations.py en.json fr.json    # specific files
    python tools/cloak_translations.py --check            # exit non-zero on hits
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent
LANG_DIR = PROJECT / "src" / "main" / "resources" / "assets" / "adin" / "translations"

# Must match HeapCloakDict.BUILT_IN exactly.
BUILT_IN = [
    "Kill Aura", "Crystal Aura", "Mace Aura", "Silent Mace",
    "Aura Guide", "Silent Aim", "Bow Guide", "SnapFire",
    "Anti-Weakness", "Fake Punch", "Damage Tick", "Only Crit",
    "Static HitBoxes", "Shield Disabler", "AntiInvis", "W-Tap",
    "Shard Hack", "Crystal Optimizer", "CwCrystal", "Double Anchor",
    "Anchor Exploder", "Auto Dtap", "Shard Rig",
    "Auto Totem", "Totem Offhand", "Hover Totem", "Force Totem", "Auto Retotem",
    "Fast Bridge", "Bridge Assist", "Fast Swim", "Fast Place",
    "No Break Delay", "No Jump Delay", "Elytra Swap", "Elytra Glide",
    "Jetpack", "Auto Sprint", "InventoryMove",
    "Auto Clicker", "Auto Pot", "Auto Eat", "Auto XP", "Auto Armor",
    "Auto Tool", "Auto Mine", "Chest Stealer", "Shulker Dropper",
    "Shard Trade", "Cord Snapper", "Key Pearl", "Auto Tpa",
    "Bed Macro", "Auto Restock", "Replace Mod",
    "Player ESP", "Storage ESP", "Entity ESP", "X-Ray",
    "Health Indicators", "Target HUD", "Netherite Finder",
    "Fake Lag", "Ping Spoof", "Pack Spoof", "Stray Bypass",
    "Anti SS Tool", "String Cleaner", "Purge", "USN Journal Cleaner",
]

PUA_BASE = 0xE100


def load_dict(extra_path: Path | None) -> re.Pattern[str]:
    terms = list(BUILT_IN)
    if extra_path and extra_path.exists():
        for raw in extra_path.read_text(encoding="utf-8").splitlines():
            s = raw.strip()
            if s and not s.startswith("#"):
                terms.append(s)
    terms = sorted({t for t in terms if t}, key=len, reverse=True)
    body = "|".join(re.escape(t) for t in terms)
    return re.compile(r"(?<![A-Za-z0-9])(" + body + r")(?![A-Za-z0-9])", re.IGNORECASE)


def to_pua_escapes(s: str) -> str:
    """ASCII plain -> JSON string containing only \\uE1XX escapes."""
    return "".join(f"\\u{PUA_BASE | (ord(c) & 0xFF):04X}" for c in s)


def value_has_flag(v: str, pattern: re.Pattern[str]) -> bool:
    return bool(pattern.search(v))


def cloak_file(path: Path, pattern: re.Pattern[str], check_only: bool) -> int:
    """Returns number of values rewritten (or that would be rewritten in --check)."""
    try:
        text = path.read_text(encoding="utf-8")
        data = json.loads(text)
    except Exception as e:
        print(f"error: cannot parse {path}: {e}", file=sys.stderr)
        return -1
    if not isinstance(data, dict):
        print(f"error: {path} top-level is not an object", file=sys.stderr)
        return -1

    rewrites: list[tuple[str, str, str]] = []  # key, old, new_escaped
    for k, v in data.items():
        if not isinstance(v, str):
            continue
        if value_has_flag(v, pattern):
            rewrites.append((k, v, to_pua_escapes(v)))

    if not rewrites:
        return 0

    if check_only:
        for k, v, _ in rewrites:
            print(f"[FLAGGED] {path.name}  {k!r}: {v!r}")
        return len(rewrites)

    # Preserve the original formatting (indent/ordering) by rebuilding the file
    # as JSON with ensure_ascii=False, then injecting literal escape sequences.
    for k, _old, new_escaped in rewrites:
        data[k] = f"__CLOAK_SENTINEL__{k}__"  # placeholder, replaced below

    rendered = json.dumps(data, ensure_ascii=False, indent=2)
    for k, _old, new_escaped in rewrites:
        placeholder = f"\"__CLOAK_SENTINEL__{k}__\""
        rendered = rendered.replace(placeholder, f"\"{new_escaped}\"")

    path.write_text(rendered + "\n", encoding="utf-8")
    for k, v, _ in rewrites:
        print(f"[CLOAKED] {path.name}  {k!r}  <- {v!r}")
    return len(rewrites)


def strip_flagged_keys(path: Path, pattern: re.Pattern[str]) -> int:
    """Delete any entry whose KEY, when CamelCase-split to snake, contains a dict term.
    Keys like 'module.auraGuide' / 'setting.silentSwap.*' are dead weight for cloaked
    modules (Feature.getName() returns the PUA name directly, Lang.get is never
    consulted). Scanner catches them via its own CamelCase split, so we scrub them."""
    try:
        text = path.read_text(encoding="utf-8")
        data = json.loads(text)
    except Exception as e:
        print(f"error: cannot parse {path}: {e}", file=sys.stderr)
        return -1
    if not isinstance(data, dict):
        return 0

    def key_split(k: str) -> str:
        # module.auraGuide  ->  module.aura guide
        return re.sub(r'([a-z0-9])([A-Z])', r'\1 \2', k)

    to_drop = [k for k in data if pattern.search(key_split(k))]
    if not to_drop:
        return 0
    for k in to_drop:
        del data[k]
        print(f"[STRIPPED] {path.name}  {k!r}")

    rendered = json.dumps(data, ensure_ascii=False, indent=2)
    path.write_text(rendered + "\n", encoding="utf-8")
    return len(to_drop)


def main() -> int:
    ap = argparse.ArgumentParser(description="Cloak scanner-dictionary values in Lang JSONs.")
    ap.add_argument("files", nargs="*", help="specific JSON files (default: every lang)")
    ap.add_argument("--check", action="store_true", help="report hits, don't write")
    ap.add_argument("--strip-keys", action="store_true",
                    help="drop entries whose KEY CamelCase-splits to a dict term")
    ap.add_argument("--dict", default=str(PROJECT / "spudfuscator-cloak.txt"),
                    help="extra dict file (default: ./spudfuscator-cloak.txt)")
    args = ap.parse_args()

    pattern = load_dict(Path(args.dict))

    if args.files:
        paths = [Path(p) for p in args.files]
    else:
        paths = sorted(LANG_DIR.glob("*.json"))

    total = 0
    for p in paths:
        if not p.exists():
            print(f"skip: missing {p}", file=sys.stderr)
            continue
        if args.strip_keys and not args.check:
            n = strip_flagged_keys(p, pattern)
            if n < 0:
                return 2
            total += n
            continue
        n = cloak_file(p, pattern, args.check)
        if n < 0:
            return 2
        total += n

    if args.check:
        print(f"\n{total} flagged value(s) across {len(paths)} file(s).")
        return 1 if total else 0
    print(f"\n{total} value(s) cloaked across {len(paths)} file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
