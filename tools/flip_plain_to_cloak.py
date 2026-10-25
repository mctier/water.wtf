"""Walk every LT.LX literal in the client source and flip the trailing
.plainString() -> .toString() wherever the DECODED value is a user-facing
label — mode value, UI name, feature title, chat snippet.

Keeps .plainString() for strings whose decoded form is a hard-structural
constraint (MC registry id "minecraft:xxx", Fabric intermediary "method_123",
ALLCAPS constant, bytecode descriptor like "(Ljava/lang/String;)V", empty).

Matches the exact runtime decode of com.utilitymod.echo.utils.EncryptedString.
"""
import os, re, io, sys

ROOT = r'C:/Users/fakemoon/Documents/Default Project/utility-mod/src/client/java'

MASK64 = (1 << 64) - 1
MIX1 = 0x9E3779B97F4A7C15
MIX2 = 0x27D4EB2F165667C5
SEED_CONST = 0x2545F4914F6CDD1D


def mix_into(state, c):
    x = ((state ^ c) * MIX2) & MASK64
    return (((x << 13) & MASK64) | (x >> (64 - 13))) & MASK64


def derive_seed(key, length):
    seed = (SEED_CONST ^ ((MIX1 * (length * 31 + len(key))) & MASK64)) & MASK64
    for c in key:
        seed = mix_into(seed, c)
    return seed & MASK64


def permutation(seed, n):
    perm = list(range(n))
    st = seed & MASK64
    for i in range(n - 1, 0, -1):
        st ^= (st << 13) & MASK64
        st &= MASK64
        st ^= st >> 7
        st = (st ^ ((st << 17) & MASK64)) & MASK64
        st &= MASK64
        j = (st & 0xFFFFFFFF) % (i + 1)
        perm[i], perm[j] = perm[j], perm[i]
    return perm


def decode(value, key):
    perm = permutation(derive_seed(key, len(value)), len(value))
    return ''.join(chr((value[perm[i]] ^ key[i % len(key)]) & 0xFF) for i in range(len(value)))


# Scanner-dictionary terms (matches HeapCloakDict.BUILT_IN + spudfuscator-cloak.txt).
# Word-boundary case-insensitive — matches both "Switch Delay" and "SwitchDelay".
SCANNER_DICT = [
    # module names (BUILT_IN from rename_modules.py)
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
    # extra terms from spudfuscator-cloak.txt
    "AH Sniper", "Anti Bot", "Attack Delay", "Auto Cart",
    "Haldex", "BlockESP", "ChunkFinder", "Click Simulation",
    "Explode Delay", "Hole ESP", "Mace Swap", "NoJumpDelay",
    "Place Delay", "Breach Kit", "Silent Swap", "SpearSwap",
    "Swing Speed", "Switch Back", "Switch Delay", "Totem Hit",
    "Vertical Speed",
    # pot-related terms (scanner "Auto Pot" family + squashed FQCN forms)
    "Pot Refill", "Pot Aura", "Pot Pelt", "Pot Macro", "Pot Splash",
    "Splash Pot", "Pot Switch", "Pot Toss", "Pot Heal", "Pot Boost",
    "AutoPot", "PotRefill", "PotAura", "PotMacro", "PotPelt",
    "SplashPot", "autopot", "potrefill", "potmacro", "potaura",
    # scanner also matches squashed forms (word-boundary without spaces)
    "AutoCart", "KillAura", "SilentAim", "AutoHitCrystal",
    "AutoSellModule", "TriggerBot", "shieldbreaker", "autoclicker",
    "autocrystal", "autoinventorytotem", "autosell", "selfdestruct",
    "triggerbot", "switchdelay", "aimassist", "anchormacro",
]

# Word-boundary case-insensitive pattern over all terms (longest-first).
_terms_sorted = sorted({t.strip() for t in SCANNER_DICT if t.strip()}, key=len, reverse=True)
_dict_body = "|".join(re.escape(t) for t in _terms_sorted)
DICT_RE = re.compile(r"(?<![A-Za-z0-9])(" + _dict_body + r")(?![A-Za-z0-9])", re.IGNORECASE)


def should_cloak(decoded):
    """Flip to .toString() (PUA) only when the decoded string carries a term
    the scanner matches on ASCII — mode values, feature labels, dict phrases.
    Everything else (log text, descriptors, intermediary, mc IDs, etc.) keeps
    .plainString() so logger/format/registry paths still see plain ASCII."""
    s = decoded.strip()
    if not s:
        return False
    return bool(DICT_RE.search(s))


RE_PLAIN_ENTRY = re.compile(
    r'static final String (L\d+) = new com\.utilitymod\.echo\.utils\.EncryptedString\(new char\[\]\{([0-9xa-fA-F,\s]+)\}, new char\[\]\{([0-9xa-fA-F,\s]+)\}\)\.plainString\(\);',
    re.MULTILINE
)


def parse_hex_arr(s):
    return [int(x.strip(), 16) for x in s.split(',') if x.strip()]


def encode(plain, key):
    """Inverse of decode — build value[] such that decode(value, key) == plain."""
    perm = permutation(derive_seed(key, len(plain)), len(plain))
    val = [0] * len(plain)
    for i, ch in enumerate(plain):
        val[perm[i]] = (ord(ch) ^ key[i % len(key)]) & 0xFFFF
    return val


def scrub(s):
    """Remove dict terms from longer strings so chat/log output stays readable
    while nothing the scanner matches survives. "Silent Aim" -> "" (and we
    collapse double spaces and dangling parens)."""
    out = DICT_RE.sub('', s)
    # collapse double spaces, strip double-paren fragments, trim.
    out = re.sub(r'\s+', ' ', out)
    out = re.sub(r'\(\s*\?\s*\)', '', out)
    out = re.sub(r'\(\s+', '(', out)
    out = re.sub(r'\s+\)', ')', out)
    return out.strip()


def is_short_label(decoded):
    """True for mode-value / UI-label-shaped strings — safe to flip to PUA
    carrier. False for sentences / chat / log text that MUST render as
    readable ASCII."""
    s = decoded.strip()
    if len(s) > 25:
        return False
    # sentence punctuation disqualifies
    if any(c in s for c in '.!?,;:[]{}=<>|&'):
        return False
    return True


def main():
    dry = '--dry' in sys.argv
    flipped = 0
    scrubbed = 0
    kept = 0
    files_changed = 0
    sample_flipped = []
    sample_scrubbed = []
    sample_kept = []

    for dp, _, fns in os.walk(ROOT):
        for fname in sorted(fns):
            if not fname.endswith('.java'):
                continue
            p = os.path.join(dp, fname)
            src = io.open(p, encoding='utf-8', errors='replace').read()
            edits = []
            for m in RE_PLAIN_ENTRY.finditer(src):
                try:
                    value_arr = parse_hex_arr(m.group(2))
                    key_arr = parse_hex_arr(m.group(3))
                    decoded = decode(value_arr, key_arr)
                except Exception:
                    continue
                hits_dict = should_cloak(decoded)
                if not hits_dict:
                    kept += 1
                    if len(sample_kept) < 10:
                        sample_kept.append(decoded)
                    continue
                if is_short_label(decoded):
                    # flip to .toString() — PUA carrier, functional for .is()/UI
                    old = m.group(0)
                    new = old[:-len('.plainString();')] + '.toString();'
                    edits.append((m.start(), m.end(), new))
                    flipped += 1
                    if len(sample_flipped) < 10:
                        sample_flipped.append(decoded)
                else:
                    # sentence/log/chat — scrub dict term, re-encrypt, stay plain
                    cleaned = scrub(decoded)
                    if not cleaned or cleaned == decoded:
                        # scrub didn't help; keep original (edge case)
                        kept += 1
                        continue
                    new_value = encode(cleaned, key_arr)
                    jv = ','.join('0x%04x' % (c & 0xFFFF) for c in new_value)
                    jk = ','.join('0x%04x' % (c & 0xFFFF) for c in key_arr)
                    name = m.group(1)
                    new = ('static final String %s = new com.utilitymod.echo.utils.EncryptedString('
                           'new char[]{%s}, new char[]{%s}).plainString();' % (name, jv, jk))
                    edits.append((m.start(), m.end(), new))
                    scrubbed += 1
                    if len(sample_scrubbed) < 10:
                        sample_scrubbed.append((decoded, cleaned))
            if edits:
                edits.sort(key=lambda t: -t[0])
                new_src = src
                for s, e, rep in edits:
                    new_src = new_src[:s] + rep + new_src[e:]
                if new_src != src:
                    files_changed += 1
                    if not dry:
                        io.open(p, 'w', encoding='utf-8', newline='\n').write(new_src)
    print(f'files_changed={files_changed} flipped={flipped} scrubbed={scrubbed} kept_plain={kept} dry={dry}')
    print('\nFLIPPED TO CLOAK (PUA):')
    for s in sample_flipped:
        print(f'  {s!r}')
    print('\nSCRUBBED + RE-ENCRYPTED (plain ASCII, term stripped):')
    for a, b in sample_scrubbed:
        print(f'  {a!r}  ->  {b!r}')
    print('\nKEPT (no dict hit):')
    for s in sample_kept:
        print(f'  {s!r}')


if __name__ == '__main__':
    main()
