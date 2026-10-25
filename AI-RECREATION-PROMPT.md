# UtilityMod — Full Recreation Prompt

You are an expert Minecraft Fabric modder. Your task is to recreate a **complete, working Minecraft 1.21.11 utility/utility-mod** named **UtilityMod** (modid `utilitymod`, package root `com.utilitymod`). Everything below is sourced from a live reference build. Recreate it faithfully — same structure, same codec math, same event flow, same behavior. If a value is marked `FILL`, probe it on a test server and refine; ship everything else complete, no stubs, no TODOs, no "left as an exercise".

---

## 0. Ground rules

1. **Pinned platform.** Minecraft `1.21.11`, yarn `1.21.11+build.3`, fabric-loader `0.18.1`, fabric-api `0.136.0+1.21.11`. **Do not update.** Yarn class/method names below are yarn's; if your environment resolves differently, adapt mappings, never the architecture.
2. **Client-only.** `environment: "client"`, split source sets (`splitEnvironmentSourceSets()`): all code lives in the client source set (`src/client/java`), assets/mixins/mod metadata live in `src/main/resources`.
3. **Vanilla packets only.** No custom protocol, no packet tricks beyond vanilla packet shapes. All "PvP-safe" modules must survive on vanilla-lore servers. Rhythm-heavy things must read human: pace by tick + ms double-gate, queue every attack through a per-tick burst accumulator, never fire raw 20+ CPS without a budget.
4. **Fail closed, no exceptions leaked.** Every module tick catch `Throwable` and prints. Broken input produces nothing.
5. **No comments that say what.** Comments carry constraints, decisions, and failure guards only.
6. **Java 21 strict, gradle 9.8.1**, `org.gradle.jvmargs=-Xmx2G`, parallel on.

## 1. Build & plumbing

```gradle
plugins {
    id 'fabric-loom' version '1.13-SNAPSHOT'
    id 'maven-publish'
}
version = project.mod_version                 // 1.0.0
group   = project.maven_group                 // com.utilitymod
base { archivesName = project.archives_base_name } // utilitymod

loom {
    splitEnvironmentSourceSets()
    mods { utilitymod { sourceSet sourceSets.main; sourceSet sourceSets.client } }
}

dependencies {
    minecraft 'com.mojang:minecraft:1.21.11'
    mappings   'net.fabricmc:yarn:1.21.11+build.3:v2'
    modImplementation 'net.fabricmc:fabric-loader:0.18.1'
    modImplementation 'net.fabricmc.fabric-api:fabric-api:0.136.0+1.21.11'
    clientCompileOnly 'org.projectlombok:lombok:1.18.48'
    clientAnnotationProcessor 'org.projectlombok:lombok:1.18.48'
}

tasks.withType(JavaCompile).configureEach {
    it.options.encoding = 'UTF-8'
    it.options.release = 21
}

jar {
    from('LICENSE') { into '' }
    manifest {
        attributes(
            'Main-Class': 'com.utilitymod.inject.Attach',
            'Agent-Class': 'com.utilitymod.inject.Agent',
            'Can-Retransform-Classes': 'true',
            'Can-Redefine-Classes': 'true'
        )
    }
}
```

`gradle.properties`:
```
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.3
loader_version=0.18.1
loom_version=1.14-SNAPSHOT
fabric_api_version=0.136.0+1.21.11
mod_version=1.0.0
maven_group=com.utilitymod
archives_base_name=utilitymod
```
(Note: `loom_version` sits in gradle.properties but build.gradle pins `'fabric-loom' version '1.13-SNAPSHOT'` — build.gradle wins; properties value is dead weight.)

`src/main/resources/fabric.mod.json`:
```json
{
  "schemaVersion": 1,
  "id": "utilitymod",
  "version": "${version}",
  "name": "UtilityMod",
  "description": "Single-player utility mod for 1.21.11 - vanilla packets only, tick-start actions, JVMTI/JNI compatible",
  "authors": ["you"],
  "contact": {},
  "license": "MIT",
  "icon": "assets/utilitymod/icon.png",
  "environment": "client",
  "entrypoints": { "client": ["com.utilitymod.UtilityMod"] },
  "mixins": ["utilitymod.mixins.json"],
  "depends": {
    "fabricloader": ">=0.18.1",
    "fabric-api": "*",
    "minecraft": "1.21.11",
    "java": ">=21"
  }
}
```

`src/main/resources/utilitymod.mixins.json`:
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.utilitymod.mixins",
  "compatibilityLevel": "JAVA_21",
  "refmap": "utilitymod.refmap.json",
  "client": ["NoHurtCamMixin"],
  "injectors": { "defaultRequire": 1 }
}
```

Also ship:
- `inject.bat` — one double-click runner: finds `build\libs\utilitymod-1.0.0.jar`, reruns `java -jar utilitymod.jar` against a running `javaw` (self-attach), reports success/failure to console.
- The same jar must work both ways: **dropped into a Fabric mods folder as a normal mod** (loader entrypoint) **AND** hot-injected (`java -jar` self-attach agent).

## 2. Repo layout

```
utility-mod/
├─ build.gradle / gradle.properties / inject.bat
├─ src/client/java/com/utilitymod/
│  ├─ UtilityMod.java            ← fabric client entrypoint (ClientModInitializer)
│  ├─ Keys.java                  ← global keybind wiring (GUI toggle, panic, etc.)
│  ├─ inject/                    ← self-attach agent
│  │  ├─ Attach.java             ← MAIN-Class; attach to running JVM, load self as agent
│  │  ├─ Agent.java              ← AGENT-Class; agentmain, calls Patcher
│  │  ├─ SelfAttach.java         ← finds the running javaw, attaches
│  │  ├─ Patcher.java            ← bytecode patch driver
│  │  ├─ Patches.java            ← named patch set (hooks cross-check live classes)
│  │  └─ Hooks.java              ← runtime hook installs (event fan-out, entrance mix-in)
│  ├─ core/                      ← mod-native core
│  │  ├─ Module.java             ← abstract module base
│  │  ├─ ModuleManager.java      ← registry + tick dispatcher + hud fan-out
│  │  └─ TickDispatcher.java     ← START_CLIENT_TICK glue
│  ├─ modules/                   ← legacy starter modules (plain Module subclasses)
│  │  ├─ NoFallModule.java, AutoToolModule.java, FullBrightModule.java,
│  │  ├─ NoHurtCamModule.java, InventoryPreviewModule.java, NoRenderModule.java,
│  │  ├─ NametagsModule.java, FreecamModule.java  (Freecam superseded — see §9.1)
│  ├─ echo/                      ← the ported client layer (the whole feature set)
│  │  ├─ api/Accessors.java      ← reflection/vanilla access bridge helpers
│  │  ├─ event/ …                ← event bus, annotation, concrete events (§6)
│  │  ├─ features/Category.java, Feature.java, FeatureInfo.java
│  │  ├─ features/settings/ …    ← settings framework (§5)
│  │  ├─ features/impl/…         ← all modules (§9)
│  │  ├─ handlers/…              ← InputHandler etc.
│  │  └─ utils/ …                ← EncryptedString, CloakUtil, math, combat, world utils
│  ├─ gui/                       ← the whole click-GUI + HUD frame stack (§10)
│  └─ dev/koifih/client/render/… ← text renderers that decode cloak text for drawing
└─ src/main/resources/
   ├─ fabric.mod.json, utilitymod.mixins.json
   ├─ assets/utilitymod/…  (icon, font/comfortaa.json + ascii.png, soup/* textures + sounds)
   └─ assets/adin/…        (transplanted UI skin: brand logos, icons, shaders, 5 langs)
```

## 3. Core module plumbing

### 3.1 `core/Module.java`
```java
public abstract class Module {
    protected final String name;
    protected boolean enabled = false;
    protected final String description;
    protected final Category category;

    protected Module(String name, String description) { this(name, description, Category.MISC); }

    protected Module(String name, String description, Category category) {
        // every module name/description is heap-cloaked — see com.utilitymod.echo.utils.CloakUtil
        this.name = com.utilitymod.echo.utils.CloakUtil.heapSafe(name);
        this.description = com.utilitymod.echo.utils.CloakUtil.heapSafe(description);
        this.category = category;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
        // Notifications.onToggle → toast + toggle sound, safe-caught
        try {
            com.utilitymod.echo.features.impl.render.Notifications.onToggle(name, enabled);
            if (!com.utilitymod.echo.features.impl.render.Notifications.muted) {
                com.utilitymod.gui.Welcome.moduleToggled();
                com.utilitymod.gui.ToggleSounds.play(category, enabled);
            }
        } catch (Throwable ignored) {
        }
    }
    public boolean isEnabled() { return enabled; }
    public final String getName() { return name; }
    public final String getDescription() { return description; }
    public final Category getCategory() { return category; }
    protected void onEnable() {}
    protected void onDisable() {}
    // Called ONLY at START_CLIENT_TICK via TickDispatcher
    public void onTickStart(MinecraftClient client) {}
    /** Client HUD hook for display-only modules. */
    public void onHudRender(DrawContext context, float tickDelta) {}
    // For JNI/JVMTI bridge - consistent logic path
    public void onNativeSignal(int code, long payload) {}
}
```

### 3.2 `core/ModuleManager.java`
- Static `LinkedHashMap<String, Module>` keyed by **cloaked** lowercase name.
- `register(Module m)` — synchronized; then **rebuilds a `volatile Module[] snapshot`**.
- `get(String name)` — cloaks the lookup so it matches PUA keys:
  ```java
  return MODULES.get(com.utilitymod.echo.utils.CloakUtil.heapSafe(name).toLowerCase());
  ```
- `onTickStart(MinecraftClient)` — iterate **snapshot** (never the map — a mid-loop `register` must not throw `ConcurrentModificationException`); for each enabled module, `try { m.onTickStart(client) } catch (Throwable t) { t.printStackTrace(); }`.
- `onHudRender(DrawContext, float)` — same snapshot pattern.
- Register list is one block (§9); the constructor comment marks the policy: "SSP-focused + test-server-safe (all vanilla packets only)". New modules must use `PacketQueue.enqueue` at tick start.

## 4. The `echo/` layer

### 4.1 `FeatureInfo` (echo features.FeatureInfo)
```java
public record FeatureInfo(CharSequence name, CharSequence description, Category category,
                          int key, boolean visible) {
    public FeatureInfo { category = category != null ? category : Category.INTERNALS; }
    public FeatureInfo(CharSequence n, CharSequence d, Category c, int k) { this(n,d,c,k,true); }
    public FeatureInfo(CharSequence n, CharSequence d, Category c, boolean v) { this(n,d,c,-1,v); }
    public FeatureInfo(CharSequence n, CharSequence d, Category c) { this(n,d,c,-1,true); }
}
```

### 4.2 `Feature extends Module`
- Holds `Category echoCategory`, `info`, `key`, `visible`, `ArrayList<Setting> settings`.
- Constructor passes `CloakUtil.heapSafe(info.name())` / `heapSafe(info.description())` to `Module`.
- `initSettings()` — one-shot reflection walk up the class chain (`this.class` → just above `Feature`), collecting every declared field assignable to `Setting`, `setAccessible(true)`, dedup by identity. Also flags every discovered `InputSimulationSetting` with `setOwner(this)`.
- `onEnable()` → `EventManager.register(this)` (subscribes `@EventSubscribe` methods); `onDisable()` → `EventManager.unregister(this)`.
- Expose: `getEchoCategory()`, `getDescriptionSequence()`, `toggle()`, `hasSettings()`, `setKey/getKey`, `isVisible/setVisible`, `getInfo()`, `concat()` (suffix next to module name in GUI), `isNull()` (`mc.player == null || mc.world == null`).

### 4.3 `Category` enum
| echo category | display (cloak literal) | GUI tab |
|---|---|---|
| COMBAT | COMBAT | COMBAT |
| MOVEMENT | MOVEMENT | MOVEMENT |
| RENDER | RENDER | VISUAL |
| FINDER | FINDER | FINDER |
| MACROS | MACROS | MISC |
| UTILITY | UTILITY | MISC |
| INTERNALS | INTERNALS | MISC |
| DONUT | DONUT | DONUT |

Fields `public final CharSequence name; public final Category tab;` — literals come from `EncryptedString` via an inner `LT` carrier class (§7).

## 5. Settings framework

Package `com.utilitymod.echo.features.settings` + `impl`. Base `Setting` carries: key (`name`), description, visibility callback (`onChanged` value-side), serialization via `SettingIO`.

| Setting | Constructor shape |
|---|---|
| `BoolSetting` | `(name, boolean default)` |
| `IntSetting` | `(name, int default, int min, int max)` + optional desc + visibility callback `o -> expr` |
| `FloatSetting` | `(name, float default, float min, float max, float step)` + optional callback |
| `RangeSetting` | `(name, min, max, step, hardMax, granularity)` |
| `StringSetting` | `(name, String default)` |
| `ModeSetting` | `(name, defaultMode, CharSequence... modes)` — `is(CharSequence)` compares cloak-equal |
| `MultiModeSetting` | multiple active selections |
| `KeybindSetting` | keyboard binding (state = GLFW code) |
| `ColorSetting` | ARGB with alpha |
| `BlockPickerSetting` / `ItemPickerSetting` / `RegistryPickerSetting` | registry-driven picker UI (`RegistryPickerScreen`) |
| `HotbarSelectionSetting` | picks a hotbar slot |
| `InputSimulationSetting` | master bool + per-slot companion key binds (`getInputSlots()` each with `getKey()` + `isListening()`); `setOwner(Feature)`, `isReady(nanos)` anti-spam; used everywhere click simulation routes through the real input pipeline |

Serialization: `SettingIO` writes/reads a single JSON config via `GuiConfig` — module enable state, each setting by key, bind assignments, GUI layout. Save on GUI close + on client stop (+ join event).

## 6. Event bus

`echo/event`:
- `Event` (base, `setCancelled`/`isCancelled`), `EventManager` — reflective `@EventSubscribe` discovery on register, keying on the event parameter type; `Event_Subscribe` annotation; `call()` iterates and early-outs on cancellation.
- Concrete events, each mapped to a vanilla hook you must create (mixin or the agent's retransform hooks):

```
EventTick, EventMove (Pre/post — vec/movement each tick), EventHandleInput,
EventMovementInput, MouseUpdateEvent, SprintEvent, EventSlowdown,
EventStartAttack (Pre — fires before vanilla doAttack when crosshair is an entity),
EventStartUseItem, EventOnAttackEntity, EventSwingHand,
EventPacketSend (packet object + cancelled), EventPacketReceive (same),
EventRender3D (world-space draw callback), EventRender2DGui (HUD-space draw callback)
```
Plus `Profiler.java` in the same package. Recreation note: equivalent mixin points on 1.21.11 fine as long as the event shapes stay. `Hooks.java` handles the same fan-out for the self-attach path.

## 7. Cloak pipeline — the codec (exact)

### 7.1 Why
Every string that could read as "cheat"/"attack" text at rest — names, descriptions, setting labels, GUI strings, search keys — exists in the heap as **PUA-mapped glyph strings**, not plain ASCII. Only the renderer decodes, one char at a time; no full plaintext ever rests on the heap.

### 7.2 `EncryptedString` — the only codec
```java
public class EncryptedString {
    private static final long SEED_CONST = 0x2545F4914F6CDD1DL;
    private static final long MIX1       = 0x9E3779B97F4A7C15L;
    private static final long MIX2       = 0x27D4EB2F165667C5L;
    private final char[] value;   // ciphertext chars
    private final char[] key;     // house master prefix slice

    public EncryptedString(char[] value, char[] key) { this.value = value; this.key = key; }

    public String plainString() {
        int n = value.length;
        char[] out = new char[n];
        int[] perm = permutation(deriveSeed(key, n), n);
        for (int i = 0; i < n; i++) out[i] = (char) ((value[perm[i]] ^ key[i % key.length]) & 0xFF);
        return new String(out);
    }

    @Override public String toString() { // heap-cloak view
        char[] out = plainString().toCharArray();
        for (int i = 0; i < out.length; i++) out[i] += 0xE100;   // shift into private-use area
        return new String(out);
    }

    private static long deriveSeed(char[] key, int len) {
        long st = SEED_CONST ^ (MIX1 * (len * 31L + key.length));
        for (char c : key) st = Long.rotateLeft((st ^ c) * MIX2, 13);
        return st;
    }

    private static int[] permutation(long seed, int length) {
        int[] perm = new int[length];
        for (int i = 0; i < length; i++) perm[i] = i;
        long st = seed;
        for (int j = length - 1; j > 0; j--) {
            st ^= st << 13;
            st ^= st >>> 7;
            st ^= st << 17;
            int k = Integer.remainderUnsigned((int) st, j + 1);   // LOW 32 bits — exact
            int t = perm[j]; perm[j] = perm[k]; perm[k] = t;
        }
        return perm;
    }
}
```

**Critical invariant, replicate exactly:** the permutation consumes `Integer.remainderUnsigned((int) st, j + 1)` — **LOW 32 bits of the xorshift state**, never the full 64-bit value. A generator using 64-bit `st % (j+1)` diverges mid-permutation and garbles any label ≥ 4 chars. Every generated pair must round-trip `plainString()` to its expected plaintext.

### 7.3 House master key prefix
```
0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad,
0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025,
0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff, 0xa44c, 0x3f9d,
0xb9e2, 0x3333, 0x8a80, 0x04d1, 0x9e26, 0x1877, 0x93c4, 0x6d15,
0xe75a, 0x7eab, 0xf8f8, 0x7249, 0xcd9e, 0x47ef, 0xc13c, 0x588d,
0xd2d2, 0xac23, 0x2670, 0xa1c1, 0x3b16, 0xb567, 0x0cb4, 0x8605,
0x004a, 0x9b9b, 0x15e8, 0xef39, 0x668e, 0xe0df, 0x7a2c, 0xf47d,
0x4fc2, 0xc913, 0x4360, 0xdab1, 0x5406, 0x2e57, 0xa9a4, 0x23f5, 0xbd3a
```
For a string of length `n` the key is the first `n` chars of the master prefix. Encode (generator side, mirror of `plainString()`): for the same `perm`, `value[perm[i]] = (char)(plain[i] ^ key[i % key.length])`. Validate every pair with a harness (keep the harness out of the repo — temp dir only).

### 7.4 Render path (the only place plaintext materializes, per char)
```java
char c = cloakChest.charAt(i);
if (c >= 0xE100 && c <= 0xE1FF) c &= 0xFF;   // pull back to printable
```
Lives in `dev/koifih/client/render/Text.java` + `StringRenderer` equivalents. All HUD, GUI, and settings draw through it.

### 7.5 `CloakUtil`
```java
public final class CloakUtil {
    public static String heapSafe(String raw) { /* shift to PUA zone, wrap immutable */ }
    public static String fromPUA(String pua) { /* inverse for cross-library lookups */ }
    public static boolean cloakChar(char c) { return c >= 0xE100 && c <= 0xE1FF; }
}
```
Used by `Module` (names + descriptions), `ModuleManager.get()`, GUI filters, config keys.

## 8. Literal-carrier conventions

- `ModuleText` class with static `S1..Sxx` fields — human-facing label literals as `EncryptedString` pairs. No inline `new char[]` at call sites.
- `ProtectedText.fromInts(new int[]{…})` — legacy helper for a few non-codec strings, shift-back at use time.
- Inner `LT` classes in modules (AegisSplit, Category, …) — module-local cloak literals.
- All user-facing descriptions pull the same pipeline.

## 9. Module inventory (the full register block)

~103 registered modules. Ship all of them. COMBAT/MOVEMENT are the deep ones with §9.1/§9.2 contracts; the rest behave as the name says. All `@EventSubscribe`-able. New registrations must use `PacketQueue.enqueue` at tick start.

```
// --- legacy starter set (com.utilitymod.modules.*) ---
NoFallModule, AutoToolModule, FullBrightModule, NoHurtCamModule,
InventoryPreviewModule, NoRenderModule, NametagsModule (+ Freecam echo-migrated §9.1)

// --- combat (com.utilitymod.echo.features.impl.combat.*) ---
SpearTip, HitboxMod, PointerSync (aura-guide), Crystalline (ShardRig),
AnchorFlow, Locomotive, XbowCart, AutoMace, MaceSwap, Backoff, AegisSplit (SSD),
SpearLunge, SpearReach, TotemHit, StunCob

// --- movement (com.utilitymod.echo.features.impl.movement.*) ---
Sprint, KeepSprint, Speed, Step, NoSlow, NoWeb, JumpInstant, NoLungeCooldown,
JumpReset, Velocity, ComboHelper, SnapTap, FastStairs, AutoWalk, EdgeStep,
GuiWalk, TimerExploit

// --- render (com.utilitymod.echo.features.impl.render.*) ---
PlayerESP, SwingPace, TargetHud, HealthIndicators, ArmorDurability,
TrajectoryRender, AimVisualizer, HudOverlay, GlassPanel, Notifications,
GlassHotbar, EffectsHud, Bearing, SessionStats, KillEffects, HitParticles,
BlockOutline, GlassNametags, WorldTime, CustomCape, AnonymousSkin, TargetMarker,
HitSounds, MotionBlur, ProjectileEffects, WeaponTrails, BlockEffects,
ShieldEffects, TeleportEffects, VisualProfiler, BlockFill, SkyArt,
VillageFinder, AncientCityFinder, BaseFinder, Fullbright, PortalFinder,
StrongholdFinder, BastionFinder, NetherFortressFinder, WaypointsModule,
CustomCrosshair, Zoom, Trails, JumpCircles, HitMarkers, StorageEsp

// --- misc (com.utilitymod.echo.features.impl.misc.*) ---
MouseTweaks, MinerFlow, AutoBlockMiner, SchematicBuilder, ChatWaypoints,
TotemFlow, AutoEat, AutoTool, ArmorAlert, VisualRange, LogoutSpots,
AutoReconnect, TargetControlModule, Teams, ExitGate
```
`FILL` exact one-line descriptions of anything not inferable from name + package. **DONUT category** — reserved fun tab; one showcase module rendering a donut ring behind the GUI.

### 9.1 Combat behavior contracts (the deep ones)

**Freecam (echo-migrated)** — legacy `FreecamModule` dropped: it enqueued a second `flying` per tick which tripped Grim Timer and froze the player vs `StunCob` range checks. The port instead spoofs vanilla's single `flying` flag per tick to the saved position via **`EventMove.Pre`**. Camera-only divergence, no dupe flying packets.

**SpearTip** — snapfire; click when a target enters the crosshair hit-zone; range slider, hold-time float.

**HitboxMod** — expand hitbox rendering/interaction locally (visual + attack cone).

**PointerSync (aura-guide)** —
- Used as "pointer, not aura": each render tick, if a target is found by `TargetUtils`, rotate the player's pitch+yaw toward the nearest-hit hitbox point (`AimMath`), respecting FOV gates: separate `yawFov`/`pitchFov`/`multipoint` floats; range; through-walls gates.
- Mode surface: `Mode` = `Regular / Blatant / WindMouse / Straight`, `Targeting` = `Closest / Nearest / FOV / HurtTick / Single / Split`, `On Click` bool, `Range`, `FOV Mode` = `FOV / Yaw FOV / Pitch FOV / Multipoint`, `Horizontal` / `Vertical`, `Item Whitelist`; HUD suffix via `concat()`; inner `LT` carrier for labels (K_AURA_GUIDE, K_AIM_MATH, K_AIM_VECTOR, K_TARGET_PRIORITY, K_RANDOM, K_SILENT_ROTATIONS, K_THROUGH_WALLS, K_HORIZONTAL, K_VERTICAL, K_ON_CLICK, K_RANGE, K_FOV_MODE, K_FOV, K_YAW_FOV, K_PITCH_FOV, K_MULTIPOINT, K_ITEM_WHITELIST, plus LT L1..L10 mode labels: Regular, Blatant, WindMouse, Straight, Closest, Nearest, FOV, HurtTick, Single, Split).
- Grim-ready: an attack carried by a look that doesn't reach the target reads as aimbot from the server — wait a tick until the visible rotation carries the hit (`rotationAligned(target)` gate).

**Crystalline (ShardRig)** — full placement suite: endcrystal + attack packets, server timing gates, damage calc, dead-crystal blacklist, `Min Damage` range, omni place/attack toggles, Grim-compat mode (delay packets until placed; no pre/post-queue overflow). Large module — port with per-tick placement search.

**AnchorFlow** — respawn anchor PvP: right-click charge/break flow, `Range`, `Min Damage`, mode `New / Yorker`, target inhibitors.

**Locomotive** — crystal-survival movement; `Vanilla / Grim / NCP` modes; ground speed boost; anti-hit.

**XbowCart** — crossbow×minecart trap kit; auto-fire pre-aimed at target-intersecting-rail flow.

**AutoMace (full spec)**
- Trigger: `Keybind`; settings: `Auto Aim`, `Aim Math`, `Aim Vector`, `Target Priority`, `Random`, `Silent Rotations`, `Through Walls`, `Horizontal`, `Vertical`, `On Click`, `Range`, `FOV Mode`, `FOV`, `Yaw FOV`, `Pitch FOV`, `Multipoint`, `Item Whitelist` (all carried via `LT`).
- Shot pipeline:
  1. fall tracking `effectiveFall()` — vanilla zeroes `fallDistance` on touchdown, so a landing smash would read fall 0 → Breach (wrong mace); hold `recentFall` (max fall since last tick) with `fallHold = 6` ticks after landing.
  2. `preArmFalling()` — while `fall > Min Fall` and an enemy is in radius `6 + fall`, hotbar-select the correct mace before reach (`selectWeapon(slot, key, tick)`).
  3. stun slam: axe disables the shield, *instant* same-tick swap to mace, smash in the SAME tick (server processes the axe hit first). `stunSlamDelay` 0..3: 0 = instant; >0 = `pendingSlamTarget/Until/Slot` follow-up letting the shield-break replicate; racing hits inside one combo is a tell — gaps read as human re-aim.
  4. fall > `Density` threshold → Density mace (higher fall damage), fallback plain mace.
  5. `slamRecoveryTarget/Until` (`SLAM_RECOVERY_TICKS = 30`) — one stun-slam combo per target then lockout so the next tick doesn't re-roll against the still-"shielded" target.
- Cadence: `AutoMacePolicy cadence` with two gates — `cadence.ready(tick, swingDelay)` tick-gate and `cadence.readyMs(now, nextIntervalMs)` ms-gate, `nextIntervalMs = AutoMacePolicy.intervalMsForCps(cps.getRandom())` = `round(1000/cps)`.
- Shield drain: per-tick burst accumulator:
  ```java
  // Attacks to send this tick for shield drain, from the configured CPS (20 tps base; >20 CPS bursts multiple hits per tick)
  drainAccum = Math.min(5.0, drainAccum + Math.min(100, cps)/20.0);
  burst = Math.min(5, (int) Math.floor(drainAccum));
  drainAccum -= burst;   // consumed once per tick, not double-counted
  ```
- The attack (`sendOneAttack`) — the event-pipeline path:
  ```java
  HitResult previousHit = mc.crosshairTarget;
  var previousEntity = mc.targetedEntity;
  mc.crosshairTarget = new EntityHitResult(target, attackPoint(target)); // synthetic crosshair
  Accessors.setAttackStrengthTicker(mc.player, 1000);                    // full strength
  try {
      if (draining && inputSimulation.getValue()) {
          InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
          if (!attackObserved) { mc.interactionManager.attackEntity(mc.player, target); mc.player.swingHand(Hand.MAIN_HAND); }
      } else {
          Accessors.resetAttackCooldown();
          Accessors.invokeStartAttack();                                 // vanilla doAttack via events
          if (!attackObserved) { attackEntity fallback + swing }
      }
  } finally {
      attacking = false; attackingTarget = null;
      mc.crosshairTarget = previousHit; mc.targetedEntity = previousEntity;
      Accessors.setAttackCooldown(10);
  }
  ```
- Grim gate: `if (!rotationAligned(target)) return;` — don't fire a hit the visible look doesn't carry.
- HUD status via `concat()` with `showStatus` bool.

**MaceSwap** — same-tick weapon swap to the mace under crosshair on demand.

**Backoff** — target retreat layer; mode Normal/Smart; repositions away.

**AegisSplit (SSD — Shield-Stun-Drain, full spec)**
- Modes `ModeSetting`: `Click` (manual), `Normal`, `Drain`.
- `Normal`: while target is blocking and `canBreakShield`: if target offhand-shields and `Unblock Shield` — press their `useKey` off (`hasPlayerUnblocked`); if main hand is AxeItem and `attackTimer.hasReached(delayAttack)` → `clickAttack(); pendingTarget = target; attackTimer.reset(); noteDrainHit(target);` else `Auto Swap To Axe` behind `reactionDelay` + `initialDelay` ticks → hotbar-find an axe → `SwapStateManager.swapTo(owner, slot, inputSimulation, swapBackDelay, returnBack, callback)`.
- `Drain`: after the axe disables the shield (5s window), keep swinging through the disable window and every re-block so absorbed hits burn shield durability. State: `drainTarget`, `shieldDamageSeen` (max observed shield damage), `drainShieldSeenAt`, `drainLastHitAt`. `recentlySeen = drainTarget == target && now - drainShieldSeenAt < 2000L`; skip when `facingAway` (`WorldUtils.isShieldFacingAway(target)`). When shield item is gone: `resetDrainState()`; if `slamOnBreak` and `now - drainLastHitAt < 1500L` → `pendingTarget = target` and hand the kill to MaceSwap for the one-tap slam.
- `reShield()` — after swap-back with `Return Back`: if right-mouse is held and you hold a shield offhand, re-press use.
- `TimerUtils` (`utils/math`): `currentTimeMillis()` via `System.nanoTime()/1_000_000`, `currentTick()` via `player.age`, `hasReached(ms)` (0 = always true), `hasReachedTicks(ticks)`, `getElapsedTime()`, `getElapsedTicks()`, `reset()` stamps both.
- `clickAttack()` — `InputHandler.simulateClick(mc.options.attackKey, inputSimulation)`, doubled when `stun` bool is on.

**SpearLunge / SpearReach / TotemHit / StunCob** — spear lunge cooldown-dissolve attacking from distance (`Spear Fallback` inside AutoMace), spear reach markers; TotemHit — totem-holder attack filter; StunCob — cobweb stun-lock kit (place cobweb, slow the target, melee sequence).

### 9.2 Movement behavior contracts
- `Speed` — SSP-safe mode-based speed mod; per-row values under Grim-class `Timer` accents.
- `TimerExploit` — modifies client tick rate only locally (no server timer statements).
- `Velocity` — cancellable knockbacks with % horizontal/vertical sliders, per-mode.
- `Step`, `NoSlow`, `NoWeb`, `FastStairs`, `EdgeStep` — vanilla-packet-native movement helpers.
- `JumpInstant` / `JumpReset` — jump-fight early combos (auto jump reset on hurt).
- `ComboHelper` — opponent combo-count state; HUD suffix.
- `GuiWalk` — walk while GUI open. `SnapTap` — rotate strafe toward the hit target on click.

### 9.3 Render & misc (name-inferable; refine to taste)
- `Notifications` — top-right toasts, module toggle feed, mute flag.
- `HudOverlay` — module list watermark + CPS counters; `cps(ArrayDeque<Long> q, bool down, bool was)` feed by `InputHandler.SIMULATED_CLICKS`.
- `GlassPanel` / `GlassHotbar` / `GlassNametags` — glass-styled HUD pieces.
- Visual family: `MotionBlur`, `KillEffects`, `HitParticles`, `Trails`, `BlockFill`, `SkyArt`, `Bearing`, `SessionStats`, `EffectsHud`, `WorldTime`, `CustomCape`, `AnonymousSkin`, `CustomCrosshair`, `Zoom`, `JumpCircles`, `HitMarkers`, `TargetMarker`, `ProjectileEffects`, `WeaponTrails`, `BlockEffects`, `ShieldEffects`, `TeleportEffects`, `VisualProfiler` — each its own `Feature` with `RangeSetting`/`ColorSetting` surfaces.
- Finders: `VillageFinder`, `AncientCityFinder`, `BaseFinder`, `PortalFinder`, `StrongholdFinder`, `BastionFinder`, `NetherFortressFinder` — world structure finders, block-scan radius cached per dimension.
- Miner/meta: `MinerFlow`, `AutoBlockMiner`, `SchematicBuilder`.
- Utility: `ChatWaypoints`, `TotemFlow`, `AutoEat`, `AutoTool`, `ArmorAlert`, `VisualRange`, `LogoutSpots`, `AutoReconnect`, `TargetControlModule`, `Teams`, `ExitGate`.

## 10. GUI

Package `com.utilitymod.gui`:
- `CardGui` — main tabbed panel. Tabs = `Category`. `FrameManager` owns each tab's window: drag/scroll/collapse, module list with name draw + `concat()` suffix + description on hover; `DropdownGui` renders a module's settings as dropdown panels.
- Visual set: `Rounded` (rounded rect drawing), `GuiTheme` (accent/secondary colors), `GuiTextures` (icons + `adin/` spritesheet), `GuiConfig` (window positions, sizes, tab state), `CustomThemes` (user theme presets), `Fonts` (Comfortaa bitmap font; ship `assets/utilitymod/font/comfortaa.json` + `ascii.png` — any legible square-bit font works to start).
- Chrome: `Toasts` (HUD popups), `Welcome` (splash + toggle-sound orchestration), `UiSounds` / `ToggleSounds` (play `soup/sounds/*.ogg`: on/off/bonk/pok/bubble/get/magic_pok/bell), `ModuleBinds` (bind editor), `Waypoints` (3D waypoint screen), `ShareCodes` (import/export config share strings), `SelfTest` (self-diagnosis screen), `RegistryPickerScreen`, `Category` (tab enum shared with echo).
- Keybind wiring via `Keys.java`: GUI open key rebindable; panic key disables everything; per-module `KeybindSetting`.

Asset note — the reference UI skin is transplanted from the **adin** client: `assets/adin/brand/logo.png` + `.json` 9-slice specs, `icons/adin*.png` + material icon sheets, `shaders/core/{text,shape,rect,icon,fill}.vsh/.fsh` + `shaders/post/{kawase,glow,outline,outline_scan}.fsh`, langs `en/fr/pl/hr/ru` (`translations/*.json` + `lang/en_us.json`), snow/rain/thunder world overlays. Recreate the same transplant shape: shader set, brand pack, 5 langs; the mod is skin-agnostic on top of `GuiTheme`.

## 11. Input & packet discipline

- `InputHandler.simulateClick(keyMapping, InputSimulationSetting)` — routes the master-input path: `directAttack(keyMapping)` handles the attack-key entity case (fires `EventStartAttack.Pre`, calls vanilla `doAttack`, plays the click sound); else simulates the real mouse button press AND release `simulatedMouseClick(button, millis, keyMapping)` through Minecraft's `Mouse` (same path as the real GLFW callback, so CPS/keystrokes HUD mixins see it), plus `sendCompanionInputs` for configured companion keys.
- Every attack from combat modules goes through exactly one of two pipelines:
  1. `simulateClick` (feed the real input pipeline, human-shaped clicks), or
  2. `sendOneAttack`-style synthetic crosshair + `invokeStartAttack` (still respects events).
- `PacketQueue.enqueue` — all outbound packet-touching actions queue at tick start, never fire inline from arbitrary threads.
- `SIMULATED_CLICKS` — `ConcurrentLinkedQueue<int[]>` capped at 64, drained for HUD counting in `showClick(keyMapping, button)` with a ~45ms `ScheduledExecutorService` release; if the physical state says pressed, do not release a real key/button (`!physical` guard).

## 12. `inject/` — hot-attach

Manifest: `Main-Class = com.utilitymod.inject.Attach`, `Agent-Class = com.utilitymod.inject.Agent`, `Can-Retransform-Classes / Can-Redefine-Classes = true`. Flow:
1. `Attach.main` — enumerate running JVMs via the Attach API; pick the javaw running the vanilla client and not this process (fall back: PID from `inject.bat` args); attach and load **this same jar** as an agent.
2. `Agent.agentmain` — spawn the agent hook thread; call `Patcher.run()`.
3. `Patcher` — walk `Patches.PATCH_SET`; for each entry, find the live class, verify method shape (signature mismatch = one-line error naming the method), retransform/install.
4. `Hooks` — install where event fan-out is needed (same touchpoints mixins would hit). Any touchpoint mismatch logs and continues as a no-op, never a crash.
5. `SelfAttach` — loop `VirtualMachine.list()`; null guards everywhere; the loader path comes first when `fabric.mod.json` already loaded the jar as a mod.

## 13. Acceptance checklist (your build must pass all)

1. `gradlew build` → BUILD SUCCESSFUL; strict warnings clean; jar + `-sources` jar emitted.
2. Drop in mods folder → client loads; `ModuleManager.init()` registers all ~103 modules; GUI opens; every tab populated; every module toggles with notification + sound; settings render; keybinds fire.
3. Self-inject path: with the vanilla client open, `java -jar utilitymod-1.0.0.jar` → attaches; `Patcher` report shows all hooks; modules live.
4. Codec: for every `EncryptedString` in the build, decode round-trip returns the expected plaintext (`Aura Guide`, `Range`, `FOV`, `Automatically aims at targets`, `Target Priority`, `Random`, `Silent Rotations`, `Through Walls`, `Horizontal`, `Vertical`, `On Click`, `FOV Mode`, `Yaw FOV`, `Pitch FOV`, `Multipoint`, `Item Whitelist`, `Regular`, `Blatant`, `WindMouse`, `Straight`, `Closest`, `Nearest`, `HurtTick`, `Single`, `Split` …). No permutation divergence (§7.2 low-32 rule). No garble on screen for any label.
5. Scan-clean: a byte sweep of the jar for raw forbidden names (`auraguide`, `killaura`, `client menu`, `ExitGate`) must return zero hits. All strings sit inside the carriers.
6. Vanilla-packet safety: on a vanilla test server, MOVEMENT/COMBAT modules behave Grim-non-flagging or degrade; drain and mace-stun paths stay within their documented budgets.
7. No plaintext-at-rest for module names in the heap; config saves are PUA-keyed; heap dumps don't harvest raw names.

## 14. Working procedure for the AI

1. Phase order: fabric plumbing → core/Module/ModuleManager/Category → settings framework → event bus → EncryptedString/CloakUtil → render text + GuiTheme → CardGui frames → legacy modules → echo ports (movement first, then combat: SpearTip/HitboxMod/PointerSync, then AutoMace, then AegisSplit/Crystalline family) → inject/self-attach last.
2. For every file: paste the **whole** file, path on the first line, wrapped in fences with the language tag. Never a path-only listing, never a diff-dribble, never "rest is obvious".
3. Where the spec is silent, mark `FILL` in one line per point, keep building, and give the default choice in one terse bullet — no silent divergence.
4. Never leave a TODO, stub, or placeholder live. If it's a function it works. If it's a module it compiles. If it's a build it runs.
5. After each phase run `gradlew build` and fix errors before the next phase. No half-applied edits.
6. The codec survives everything: if a string is user-facing it belongs in an `EncryptedString` carrier. If it can't be carried directly, it's a `ModuleText.Sxx` entry generated at build time.

## 15. Wind-down

House rule: internal labels (`PORT-echo-…`, `build.PointerSync`, …) live behind the carrier, never in plain source strings. Ship the whole client: package `com.utilitymod`, jar `utilitymod-1.0.0.jar`, entry `com.utilitymod.UtilityMod`, main-class `com.utilitymod.inject.Attach`.

Begin with `src/client/java/com/utilitymod/UtilityMod.java` and `gradle.properties`, then climb stack-up. Everything ships complete.

## 16. Reference repos (attach at build start — mandatory for from-scratch builds)

When building from scratch, pull from these two live references before touching the visual glass stack or any combat pacing:

1. **LiquidGlass shader stack** — https://github.com/Jacquesqwq/LiquidGlassShader
   The glass render language this mod's HUD is built on: frosted-blur/backdrop glass behind `GlassPanel`, `GlassHotbar`, `GlassNametags`, the `Rounded` corners, and the transplanted `assets/adin/shaders/post/{kawase,glow,outline,outline_scan}.fsh` post-shaders. Port its shader shape into `assets/` (`shaders/core/rect|fill|icon.vsh/fsh` + `shaders/post/kawase|glow|outline.fsh`), wire it through `GuiTheme`/`Rounded`, and the whole glass aesthetic lands instead of being reverse-engineered. Start from this repo's shader program, not zero.

2. **AntiCheatWiki** — https://deepwiki.com/tjshtqwq/AntiCheatWiki
   The bypass doctrine every combat contract in §9.1/§9.2 enforces. Before shipping any COMBAT/MOVEMENT module, check its packet rhythm against the wiki's pages on the relevant anti-cheat (Grim, NCP, etc.). The gates this spec already hardcodes — tick + ms double-gate cadence, per-tick burst accumulators capped at 5, hits held until the visible rotation carries the hit (`rotationAligned`), one stun-slam combo per target with a 30-tick lockout, no dupe flying packets in Freecam — all read against this wiki. If your module's rhythm deviates from the wiki's pacing rules, that's a flag on the server; fix the rhythm, never weaken the wiki gate.
