# UtilityMod — From-Scratch Recreation Prompt (no echo layer, detailed build spec)

You are an expert Minecraft Fabric modder. Your task is to recreate a **complete, working Minecraft 1.21.11 client utility mod** named **UtilityMod** (modid `utilitymod`, package root `com.utilitymod`) — built **from true zero**. This variant deliberately ships **without the echo-ported client layer**: no combat modules, no ported feature framework, no echo event bus, no string-encryption codec, no draped package mirrors. You build the plain, clean core: the module system, the SSP movement/utility set, the glass GUI, the hot-attach agent, and everything that makes the mod run as a normal Fabric mod.

Everything below is sourced from a live reference build of the same mod's non-echo core — packets, gates, restore flows, mixin targets and call-sites are the reference-verified ones. Recreate it faithfully — same structure, same plumbing, same behavior. If a value is marked `FILL`, probe it on a test server and refine; ship everything else complete, no stubs, no TODOs, no "left as an exercise".

---

## 0. Ground rules

1. **Pinned platform.** Minecraft `1.21.11`, yarn `1.21.11+build.3`, fabric-loader `0.18.1`, fabric-api `0.136.0+1.21.11`. **Do not update.** All class/method names below are yarn's for 1.21.11 (verified against the reference build where cited); if your environment resolves differently, adapt the mappings, never the architecture.
2. **Client-only.** `environment: "client"`, split source sets (`splitEnvironmentSourceSets()`): all code in `src/client/java`, assets/mixins/mod metadata in `src/main/resources`.
3. **Vanilla packets only.** No custom protocol, no out-of-shape packets, no attack fastlane. Every module must survive on a vanilla test server without a flag: client-only, or exactly one vanilla-shaped packet per tick at most. The two packet-touching modules (`Freecam`, `NoFall`) and their shapes are specified exactly in §6 — copy them, don't restyle them.
4. **Fail closed, no exceptions leaked.** Every module tick path catches `Throwable` and prints. Broken input produces nothing: a failed raycast echoes no panel, a missing world disables the tick body, no stale data.
5. **No comments that say what.** Comments carry constraints, decisions, and failure guards only. Every "why" survives; "what" reads from the code.
6. **Java 21 strict, gradle 9.8.1**, `org.gradle.jvmargs=-Xmx2G`, parallel on. No warnings.
7. **No encryption layer.** From scratch, string literals stay plain UTF-8 identifiers and labels. There is no `EncryptedString`, no `CloakUtil`, no PUA trap, no `ModuleText` indirection, no inner `LT` carriers in this build. If you later graft an anti-string-scan layer, that's a separate phase with its own prompt, not this one.
8. **Bounded everything.** Loops over world/entity/tab data carry ceilings; buffers are size-gated; a corrupted config is refused whole (defaults restored + one stderr line), never half-trusted.

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
    clientCompileOnly 'org.projectlombok:lombok:1.18.48'   // compile-time only, GUI transplant consumes it
    clientAnnotationProcessor 'org.projectlombok:lombok:1.18.48'
}

tasks.withType(JavaCompile).configureEach {
    it.options.encoding = 'UTF-8'
    it.options.release = 21
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

jar {
    from('LICENSE') { into '' }
    // Dual-mode jar loader-route + hot-attach (see §8):
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
fabric_api_version=0.136.0+1.21.11
mod_version=1.0.0
maven_group=com.utilitymod
archives_base_name=utilitymod
```

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

Build outputs:
- `inject.bat` at repo root — one double-click runner: locates `build\libs\utilitymod-1.0.0.jar`, resolves the running client `javaw` (optional `--pid <n>` arg to pin it), executes `java -jar utilitymod-1.0.0.jar --pid <n>`, and exits non-zero with a clear stderr line on every failure mode (no JVM found → exit 2, attach refused → exit 3, agent load failed → exit 4). Never a silent die.
- Dual-mode jar: **dropped into a Fabric mods folder** it loads through the loader entrypoint; **run with `java -jar`** it self-attaches as an agent (§8). Both paths must reach feature parity from the same artifact, and the parity check is part of `SelfTest` (§7).

## 2. Repo layout

```
utility-mod/
├─ build.gradle / gradle.properties / inject.bat
├─ src/client/java/com/utilitymod/
│  ├─ UtilityMod.java            ← fabric client entrypoint (ClientModInitializer)
│  ├─ Keys.java                  ← global keybind wiring (GUI toggle, panic, per-module binds)
│  ├─ core/
│  │  ├─ Module.java             ← abstract module base
│  │  ├─ ModuleManager.java      ← registry + two fan-outs (tick, hud)
│  │  ├─ TickDispatcher.java     ← START_CLIENT_TICK + HUD render glue, one dispatcher
│  │  └─ Category.java           ← tab enum (MOVEMENT/RENDER/MISC/UTILITY/DONUT)
│  ├─ network/
│  │  └─ PacketQueue.java        ← tick-start packet FIFO (§6.0)
│  ├─ modules/                   ← the 8-module utility set (§6)
│  ├─ mixins/
│  │  └─ NoHurtCamMixin.java     ← the only mandatory mixin (§5)
│  ├─ inject/                    ← hot-attach agent (§8)
│  │  ├─ Attach.java, Agent.java, SelfAttach.java,
│  │  ├─ Patcher.java, Patches.java, Hooks.java
│  └─ gui/                       ← the glass click-GUI + HUD stack (§7)
│     ├─ CardGui.java, FrameManager.java, DropdownGui.java, Rounded.java,
│     ├─ GuiTheme.java, GuiTextures.java, GuiConfig.java, CustomThemes.java,
│     ├─ Fonts.java, Toasts.java, Welcome.java, UiSounds.java, ToggleSounds.java,
│     ├─ ModuleBinds.java, SelfTest.java, ShareCodes.java, Binder.java (picker row plumbing)
└─ src/main/resources/
   ├─ fabric.mod.json / utilitymod.mixins.json
   ├─ assets/utilitymod/
   │  ├─ icon.png
   │  ├─ font/comfortaa.json + ascii.png       ← square-bitmap GUI font (§7)
   │  ├─ textures/gui/…                        ← glass skin sprites + 9-slice specs (§7)
   │  ├─ shaders/core/*.vsh/fsh + shaders/post/*.fsh   ← LiquidGlass port (§9)
   │  └─ soup/sounds/*.ogg + sounds.json       ← toggle/UI sound set (§7)
   └─ shaders/ (none — all shaders live under assets)
└─ config-written-at-runtime: .minecraft/config/utilitymod/config.json (§4)
```

## 3. Core module plumbing

### 3.1 `core/Category.java`
```java
public enum Category {
    MOVEMENT("Movement"),
    RENDER("Render"),
    MISC("Misc"),
    UTILITY("Utility"),
    DONUT("Donut");   // reserved fun tab, one showcase module — §7
    public final String display;
    Category(String display) { this.display = display; }
}
```
Matches the reference usage (`Freecam`/`NoFall` register under `Category.MOVEMENT`). UTILITY is the general tab; DONUT is cosmetic and ships one showcase module.

### 3.2 `core/Module.java`
```java
public abstract class Module {
    protected final String name;
    protected boolean enabled = false;
    protected final String description;
    protected final Category category;

    protected Module(String name, String description) { this(name, description, Category.MISC); }

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
        // toast + toggle sound, safe-caught: a UI failure must never dead-switch a module
        try {
            com.utilitymod.gui.Toasts.onToggle(name, enabled);
            if (!com.utilitymod.gui.Toasts.muted) {
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
    // called ONLY at START_CLIENT_TICK, via TickDispatcher — never mid-frame
    public void onTickStart(MinecraftClient client) {}
    /** Client HUD hook, post-vanilla-HUD, for display-only modules. */
    public void onHudRender(DrawContext context, float tickDelta) {}
    // For JNI/JVMTI bridge - consistent logic path across native injectors (reserved)
    public void onNativeSignal(int code, long payload) {}
}
```
Contract notes:
- `onEnable()` runs **after** `world/player` may still be null (a bind-flip on the title screen) — every `onEnable` body null-guards `mc.player`/`mc.world` itself. Same for `onDisable`.
- `onTickStart` is the only mutation point for gameplay state. It fires once per client tick, before movement is applied, in snapshot order.
- `onHudRender` fires once per frame, post-vanilla-HUD draw, with the real `DrawContext` and matrix stack intact. Render-only work goes here; never mutate gameplay state from a render path.

### 3.3 `core/ModuleManager.java`
```java
public final class ModuleManager {
    private static final Map<String, Module> MODULES = new LinkedHashMap<>();

    private ModuleManager() {}

    public static void init() {
        // SSP-focused + test-server-safe (all vanilla packets only)
        register(new FreecamModule());
        register(new NoFallModule());
        register(new AutoToolModule());
        register(new FullBrightModule());
        register(new NoHurtCamModule());
        register(new InventoryPreviewModule());
        register(new NoRenderModule());
        register(new NametagsModule());
        register(new DonutModule());   // DONUT tab showcase — §7
        // Add more here - all must use PacketQueue.enqueue at tick start
    }

    /** Snapshot for per-tick/per-frame loops: a register() mid-loop can't throw ConcurrentModificationException. */
    private static volatile Module[] snapshot = new Module[0];

    public static synchronized void register(Module m) {
        MODULES.put(m.getName().toLowerCase(), m);
        snapshot = MODULES.values().toArray(new Module[0]);
    }
    public static Module get(String name) { return MODULES.get(name.toLowerCase()); }
    public static Collection<Module> all() { return java.util.List.of(snapshot); }

    public static void onTickStart(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        for (Module m : snapshot) {
            if (m.isEnabled()) {
                try { m.onTickStart(client); } catch (Throwable t) { t.printStackTrace(); }
            }
        }
        PacketQueue.flush();   // same tick start, AFTER the module fan-out — see §6.0
    }

    public static void onHudRender(DrawContext context, float tickDelta) {
        for (Module module : snapshot) {
            if (module.isEnabled()) {
                try { module.onHudRender(context, tickDelta); } catch (Throwable t) { t.printStackTrace(); }
            }
        }
    }
}
```
Contract notes:
- `prepare` order is fixed: tick fan-out first, then `PacketQueue.flush()` — a module enqueueing inside `onTickStart` sends in the same tick start, never between ticks.
- `Mixins/agent` paths both funnel into these two methods only; there is exactly one dispatcher, never two.
- Registration order in `init()` is the GUI list order. Keep it stable.

### 3.4 `core/TickDispatcher.java`
Glue between the mod and the vanilla tick loop:
- `ClientTickEvents.START_CLIENT_TICK` (fabric-api) → `ModuleManager.onTickStart(mc)`.
- `HudRenderCallback` → `ModuleManager.onHudRender(drawContext, tickDelta)`.
- A static `boolean attached` stamped by either path (loader init or agent `Hooks.install`) — `SelfTest` reads it.
- `dispatchKey(GlfwKey)` — the shared keybind consumer used by `Keys` (§7): on a bind press, resolve to a GUI open, a panic, or a module's `KeybindSetting`.
Nothing else hooks the tick or the HUD. One dispatcher, one fan-out.

### 3.5 `network/PacketQueue.java`
```java
public final class PacketQueue {
    private static final java.util.ArrayDeque<net.minecraft.network.packet.Packet<?>> QUEUE = new java.util.ArrayDeque<>();
    private static final int MAX = 64;

    /** Enqueue a vanilla packet to send this tick start. Over-capacity is a refused order, not a stall. */
    public static synchronized void enqueue(net.minecraft.network.packet.Packet<?> packet) {
        if (QUEUE.size() >= MAX) { QUEUE.clear(); }   // bounded drop: never an unbounded server-facing backlog
        QUEUE.addLast(packet);
    }

    /** Flush at tick start, AFTER all modules ran (see ModuleManager.onTickStart). */
    public static synchronized void flush() {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) { QUEUE.clear(); return; }
        while (!QUEUE.isEmpty()) mc.getNetworkHandler().sendPacket(QUEUE.pollFirst());
    }
}
```
Contract notes:
- **Order law:** enqueue silently during any module tick; flush once, right after the tick fan-out. A packet queued during tick N sends at tick N's start, never between ticks, never mid-frame.
- On world leave / respawn / player null: flush clears instead of sending — no stale position packets flying at a new world.
- Over-capacity drops the queue whole and keeps going (the gate exists so a bug can't spam the server; failing open here means a client freeze, not a crash).

## 4. Settings framework (own, minimal)

Package `com.utilitymod.settings`. Base `Setting`:
```java
public abstract class Setting {
    private final String name, description;
    private java.util.function.Predicate<Void> visibility;   // null = always visible
    public void setDependency(java.util.function.Predicate<Void> p) { this.visibility = p; }
    public boolean isVisible() { return visibility == null || visibility.test(null); }
    public abstract net.minecraft.nbt.NbtCompound toNbt();   // or a JSON tree — choose one, same renderer everywhere
    public abstract void fromNbt(net.minecraft.nbt.NbtCompound nbt);
    protected void markDirty() { com.utilitymod.gui.GuiConfig.saveSoon(); }
}
```

Classes and constructor shapes:
| Setting | Shape | Behavior contract |
|---|---|---|
| `BoolSetting` | `(name, boolean def)` | click-toggle in GUI; persists |
| `IntSetting` | `(name, int def, int min, int max)` + optional `desc`, optional visibility callback `o -> expr` | drag slider; **value quantizes to whole ints on release**; hard-clamped min/max at set-time |
| `FloatSetting` | `(name, float def, float min, float max, float step)` | slider; step quantization on release; render format `"%.Nf"` with the step's decimals |
| `StringSetting` | `(name, String def)` | GUI text row; Escape cancels, Enter commits |
| `ModeSetting` | `(name, String defMode, String... modes)` | cycle chips; `is(String)` exact compare; persists the index, refuses an unknown saved value (falls back to default + one stderr line) |
| `KeybindSetting` | `(name, int glfwCode)` | click row → `isListening()` true → capture the next keypress (keyboard only; mouse 5-button optional escape to clear). Escape always clears. Two binds per module max. |
| `ColorSetting` | `(name, int argb def)` | swatch row + alpha slider; ARGB int is the single stored form |

Persistence — `com.utilitymod.gui.GuiConfig`:
- File: `.minecraft/config/utilitymod/config.json` (create parent dirs; atomic write — write temp, move, on any IO error keep the last good file and print one stderr line).
- Shape:
```json
{
  "modules": { "nocam": { "enabled": false }, "freecam": { "enabled": true } },
  "settings": { "freecam.speed": 1.0, "nametags.scale": 1.5, "freecam.reachmode": "Free" },
  "binds": { "freecam.toggle": 341, "gui.open": 343 },
  "windows": { "MOVEMENT": { "x": 12, "y": 60, "w": 260, "h": 220, "open": true } },
  "themes": { "active": "glassday", "custom": [ { "name": "glassday", "accent": 114, "accentAlpha": 190 } ] }
}
```
- Save triggers: GUI close, panic press, client stop. Save runs on the client thread only — no background thread touches the JSON.
- Load: on `UtilityMod.onInitializeClient` — unknown keys in the file are dropped with one summed stderr line, known keys restored wholesale before any GUI opens.
- `GuiConfig.saveSoon()` coalesces multiple `markDirty()` writes into one save on the next tick.

**No `InputSimulationSetting`, no packet-shaping settings.** Input simulation was a combat-adjacent tool; the utility set never fakes clicks.

## 5. Mixins — straight, no bus

From scratch, this module set needs exactly one interposition, and it's a direct mixin, not an event framework:

`com/utilitymod/mixins/NoHurtCamMixin.java` (reference-verified):
```java
package com.utilitymod.mixins;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class NoHurtCamMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void onHurtTilt(CallbackInfo ci) {
        if (com.utilitymod.inject.Hooks.cancelHurtTilt()) ci.cancel();
    }
}
```
- `Hooks.cancelHurtTilt()` is the shared gate both the loader path and the agent path consult (§8): it returns true only when `NoHurtCamModule` is enabled, reads at render time, and always returns false when the module manager isn't initialized yet (boot-time calls can't read a half-built registry).
- The mixin carries a static `boolean applied` set true in the mixin's `<clinit>` — `SelfTest` reads it to verify the mixin actually landed (a pinned wrong yarn name here is a boot-visible failure: mixin load refuses the config with `defaultRequire = 1`, the mod still loads but the `SelfTest` row shows a FAIL, and `NoHurtCamModule` keeps working as a no-op).
- Everyone else implements through `onTickStart(MinecraftClient)` or `onHudRender(DrawContext, float)`. When a future module genuinely needs an event-shaped hook, add the minimal mixin then — one mixin per concern. No `PacketQueue` abuse, no reflection event bus, no `Echo*` shapes.

## 6. Module inventory — the complete utility set

Registered in this exact order in `ModuleManager.init()` (GUI list order follows):

```
Freecam                  ← camera-only flight + server-position hold (§6.1)
NoFallModule             ← fall-damage softener, vanilla-shape packet (§6.2)
AutoToolModule           ← best-tool hotbar swap when block break starts (§6.3)
FullBrightModule         ← gamma/brightness override (§6.4)
NoHurtCamModule          ← hurt-tilt cancel, paired with NoHurtCamMixin (§6.5)
InventoryPreviewModule   ← equipment/armor peek UI on entity hover (§6.6)
NoRenderModule           ← overlay effect suppression set (§6.7)
NametagsModule           ← player name tags, distance-scaled, health bar (§6.8)
ButtonsModule            ← DONUT showcase ring behind the GUI (§7)
```
Eight working modules + one showcase. Every one ships with at least two settings, a real description, and all `FILL` items surfaced in one consolidated report by `SelfTest` (§7).

### 6.1 Freecam — camera-only flight + server-position hold (reference-verified packet flow)

Persuade the server you haven't moved while the camera flies. The reference-verified shape:

```java
public class FreecamModule extends Module {
    private Vec3d savedPos;
    private float savedYaw, savedPitch;
    private net.minecraft.client.option.KeyBinding toggle;   // module keybind, default rebindable

    public FreecamModule() { super("Freecam", "SSP freecam — camera flies, the player stays server-put.", Category.MOVEMENT); }

    @Override protected void onEnable() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) { setEnabled(false); return; }   // fail closed: boot-time flip with no world is a no-op
        savedPos = new Vec3d(c.player.getX(), c.player.getY(), c.player.getZ());
        savedYaw = c.player.getYaw();
        savedPitch = c.player.getPitch();
        c.player.setNoGravity(true);
    }

    @Override protected void onDisable() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null && savedPos != null) {
            // restore, in this order: position first (packets read it), then rotation, then flags
            c.player.setPos(savedPos.x, savedPos.y, savedPos.z);
            c.player.setYaw(savedYaw);
            c.player.setPitch(savedPitch);
            savedPos = null;
        }
        if (c.player != null) c.player.setNoGravity(false);
    }

    @Override public void onTickStart(MinecraftClient client) {
        if (client.player == null || savedPos == null) return;
        // Keep the server believing we're at savedPos — one vanilla-shaped packet at tick start only
        PacketQueue.enqueue(new PlayerMoveC2SPacket.PositionAndOnGround(
            savedPos.x, savedPos.y, savedPos.z, client.player.isOnGround(), false));
        client.player.setNoGravity(true);   // every tick — vanilla may clear the flag on a tick path
        client.player.noClip = true;        // camera/visual body flight through blocks
    }
}
```
Rules and edge cases:
- **The only packets in flight are in flight while in reach:** Disabling and re-enabling after a disconnect restores correctly; if `savedPos` was cleared, `onDisable` skips the position restore (no garbage restore into a fresh world).
- **While enabled**, the player never, even once in the middle of a tick, shows movement other than the origin block movement to the server. Every other packet shaped from gameplay (`use item`, `attack` etc. interpositions) would send an origin-blocked hitbox — for this build, none are interposed: this mod doesn't attack.
- **World-leave guard:** on world change / respawn / disconnect, disable yourself (`onDisable` runs) — a cross-world carried `savedPos` is not a feature.
- **HUD truth:** while active, a small glass sticker near the crosshair reads `freecam` + distance-from-player in blocks.
- **Flight controls:** WASD on the level, Space/Shift up/down, scroll adjusts speed between 0.25–5.0 (`FloatSetting speed`); optional `Pitch Lock` toggle to keep the player rotation held at saved rotation; ESC or the same toggle keybind closes again. Restore flow of the player's own yaw/pitch on disable is the reference form above.
- **No clip is client-visual-only;** server never perceives movement — `PositionAndOnGround` pins the server's view at the saved spot, and the same tick start owns both the enqueue and your client's move call, in the exact order shown.

### 6.2 NoFallModule — fall-damage softener (reference-verified packet shape)

```java
public class NoFallModule extends Module {
    public NoFallModule() { super("NoFall", "Vanilla NoFall — OnGroundOnly at tick start, no custom packets.", Category.MOVEMENT); }

    @Override public void onTickStart(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.fallDistance > 3.0f && !client.player.isOnGround()) {
            // Enqueue — flushed at the same tick start, never between ticks
            PacketQueue.enqueue(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
        }
    }
}
```
Rules and edge cases:
- **`fallDistance > 3.0f`** is the reference gate — under it, fall damage would be too small to fake a safe landing at all; over it, this one `OnGroundOnly(true)` per falling tick spells the server "I'm on the ground now," dropping the damage formula's output to zero per the vanilla landing flow.
- **`isOnGround()==false`** is the second gate — a module that sends "on ground" to the server while the client itself is grounded just stutter-stamped the position twice with nothing gained.
- **Only this packet shape**, flush at tick start by law of §6.0, and no mode gates/toggle settings beyond a `Min Fall` IntSetting (3–20, default 3) to retune the gate. No horizontal intervention, no "TPS-boost" logic.
- **Server-side truth check:** the `boolean horizontalCollision` param is `false` (that's the second constructor arg), so no accidental lateral collision reports.

### 6.3 AutoToolModule — best-tool hotbar swap

Contract:
- Trigger: the client `START_CLIENT_TICK` raycast (`mc.crosshairTarget`) resolves a `BlockHitResult` **and** the attack key is being pressed, or the block has ≥1 stage left having already been under mining pressure (hold-state tracked between ticks by module field).
- Pick: scan hotbar slots (`PlayerInventory.main`), each `EntityStack` compared by the block-aware ölçüt (`ItemStack.getMiningSpeedMultiplier(blockState)` via the item registry attributes), pick the best if it beats the current main hand by a fresh `> 0.0` margin (`ties by earlier hotbar slot`). Only counts tools with a break-time estimate; a tool that can't break the block at all (e.g., a sword for mining obsidian) never wins.
- Switch: set `mc.player.getInventory().selectedSlot = slot` (client-side slot swap; the vanilla client syncs it, and hotbar-swap-native).
- Gate: only when nothing oppressive about the block (not in GUI open, not freecamed).
- `SwapBack` BoolSetting — default on; when the block finishes or you release the hold, **return** to the pre-lift selected slot (a module field `lastMainSlot`, restored once).
- FILL note for exact 1.21.11 spark-across-mappings: inventory library's tool-effekt data component lookup (`data components` component map) — if the 1.21.11 component map wing doesn't have the `mining speed` name visible in yarn for you, probe that mapping via `ItemStack.getComponents()`.

### 6.4 FullBrightModule — brightness override

Contract:
- `isEnabled` mirrors a straightforward `Options` gamma override: hold `mc.options.gamma` above the natural max while enabled (float slot `Brightness` IntSetting 1–15 on a 1.0–15.0 scale; default 12).
- Tick-start set + on-disable restore: keep the previous gamma value in a module field and restore it before `no-op` — an unmigrated gamma restoration is a dead-bright game after a toggle-off.
- FILL the exact 1.21.11 yarn name path for the gamma option (option name has changed wings across versions; the reference shipped builds against its verified wing — under your mapping, resolve it once and note it in one stderr line at enable time if different).
- Clean edge: a hostile other-mod (Fabulous graphics, shader packs) can wrap the lightmap — when your set doesn't hold value cleanly across a `GraphicsMode` toggled by another mod, hold the value through a `SelfTest` row and show the issue once, not every frame.

### 6.5 NoHurtCamModule — hurt-tilt cancel

Contract:
- One BoolSetting (`Cancel While GUI Open` — default true: tilt never came back to a UI click wellness; a half-mixin gate is not an option).
- Behavior is in `NoHurtCamMixin` + `Hooks.cancelHurtTilt()` (§5). No other rendering gate, no general camera-tilt changes; **only the hurt tilt interposition** — user's played camera bobbing stays untouched.
- `SelfTest` reads `NoHurtCamMixin.applied`: a no-mixin build shows a FAIL row and the module self-reports it once in stderr (never per-frame).

### 6.6 InventoryPreviewModule — equipment/armor peek panel

Contract:
- In `onHudRender`, raycast the crosshair-target `EntityHitResult` → `PlayerEntity` within 8 blocks.
- Read **server-truth shopping only**: `getEquippedStack(EquipmentSlot.HEAD/CHEST/LEGS/FEET/MAINHAND/OFFHAND)` — these are the synchronized views of another player's inventory (their full inner inventory is not server-synced to a client; don't fake a fake preview).
- Panel: glass-backed `Rounded` frame near the crosshair: four armor slots, main hand, off hand, each with item glyph, **armor durability percentage** (`maxDamage - damage) / maxDamage` for items with a damage bar) and a small color dot against the item's rarity accent.
- `Scale` FloatSetting (0.75–1.5, default 1.0), `Width` IntSetting (120–260 px, default 180), `Opacity` IntSetting (40–100, default 85).
- Render-only — no packets, no entity invocation; a freshly moved target updates its panel at the next render tick without holdback.
- Detail edge: `getEquippedStack` on a dead player (post-respawn state) is skipped; a shadowed raycast (`!canSee` from camera to entity) still shows the panel — the view is informative, not an exploit.

### 6.7 NoRenderModule — overlay effect suppression set

Contract:
- `Overlay set` Multi-Bool panel (each an independent `BoolSetting`): `pumpkin mask`, `hurt orange flash`, `vignette darkening`, `data/shield/5 blink`, `fire overlay while on fire`, `suffocation darkening`, `portal presence sway`.
- Each overlay class has its own interposition point (one mixin hook target each per overlay class when needed; the *suppression* itself is a HEAD-cancel at the overlay's draw call, ONLY when its row's setting is on and the module is enabled).
- All client-only; no packet change; no entity-invocation change.
- `FILL`: the exact 1.21.11 overlay draw call-site per overlay (yarn name surfaced per row under your mappings — the reference wing carried these mappings; a miss loses one row and shows in `SelfTest`, and does not kill the mod boot).
- `Default state`: rows default true for the pumpkin and the fire two; the vignette and portal rows default false (some users like vignette as bodily feedback).

### 6.8 NametagsModule — name tags with distance scale + health bar

Contract:
- In `onHudRender`: iterate `mc.world.getEntities()` (bounded: only vectors inside camera frustum AND only in radius ≤ 128 blocks, `IntSetting Range` 16–128 default 64) — only `PlayerEntity` class.
- Render per entity: a `Rounded` glass-back name chip above the entity's head: the vanilla `entity.getDisplayName()` text drawn **in the custom bitmap font** (Comfortaa §7) scaled by distance scale factor (farther = smaller; span 0.5–2.0 by the world-to-camera projection, `FloatSetting ScaleMultiplier`), and an under-bar health punch (`player.getHealth() / player.getMaxHealth()` from the synced values; sync is real for other players — packed `getHealth()` per `PlayerEntity` sync path), width per label width, color lerp red→green over health fraction.
- `Max Distance`, `Scale`, `Always Show Own` BoolSettings.
- **No double-draw:** suppress the vanilla nameplate pass for the entity at your interposition point (it is a HEAD-cancel at the nameplate draw call — one mixin hook entry) so nothing renders the same label twice.
- HUD set passes with the vanilla sprite-atlas text only: no colored string sanitization of tags in the module.

## 7. GUI — the glass skin, every class on it

Package `com.utilitymod.gui`. One working tab set + the reserved fun tab:

- **`CardGui`** (extends `net.minecraft.client.gui.screen.Screen`) — the front door: full glass aesthetic, tab strip up top, one panel list per tab. `Keys.openGui()` (default keybind: rebindable) sets this screen; ESC closes and saves the layout. On close, config save fires.
- **`FrameManager`** — owns the tab windows: window positions/sizes from `GuiConfig` (`windows` tree in §4), drag by title bar (mouse-move delta, snap-to-edge at ±8 px), scroll-wheel VerticalList with the scrollbar when the module list overflows the window, per-window collapse toggle (title-bar chevron), and the "array snapshot" contract: window state flushes to `GuiConfig` on drag end, not per-frame.
- **`DropdownGui`** — the per-module settings panel: expand animation 150 ms ease-out; BoolSetting row toggle, slider rows (drag tracks with grip square, quantization on release), ModeSetting cyclical chips, KeybindSetting capture rows ("press a key…" prompt while listening), a ColorSetting row (swatch + alpha slider). Rows respect `Setting.isVisible()` — a hidden setting never renders, never toggles, never persists differently.
- **`Rounded`** — rounded-rect geometry: GL rounded-rect draw with radius + AA-passed shader; NaN-safe (a degenerate rect draws nothing, never hangs the program with NaN → the LL-drain float story of `GLDriver` never replays here). All glass surfaces call this.
- **`GuiTheme`** — accent/secondary colors as ARGB ints from `GuiConfig` themes block; per-theme override set. Default theme `glassday`: accent `#6C63FF`, secondary `#1F2128`, surface `45% alpha`. `CustomThemes` — named presets in the config, save/load row in the theme panel.
- **`GuiTextures`** — sprite atlas + 9-slice spec rows (glass frame corners, scrolls, chevrons, checkboxes). The 9-slice spec is JSON (`textures/gui/*.json`) following the standard 9-slice pattern (left/top/right/bottom/border margins).
- **`Fonts`** — square-bitmap text renderer: `font/comfortaa.json` (`png` file + a JSON METADATA: charcode → (u, v, w, h, advance)) and all GUI/HUD text draws through it. The reference matches Comfortaa; any clean legible bitmap works, but ship the correct 2× hi-fi and 1× sizes if you go your own way.
- **`Toasts`** — top-right glass HUD toasts: max 3 visible; 4 s fade; slide-in from the right; mute flag persisted. `Module.setEnabled` calls `Toasts.onToggle(name, enabled)` (§3.2 — safe-caught). Stack rolls up when overflowed; a toast reads one line, never two.
- **`Welcome`** — splash + toggle orchestration fired once per client launch on init: brief glass panel with the mod name+version, 2.5 s hold, and a first-boot row printing the `SelfTest` summary to stderr (one line, never a dialog).
- **`UiSounds` / `ToggleSounds`** — play the soup sound set from `soup/sounds/*.ogg` indexed by a `sounds.json`: on/off toggle pair ("on.ogg"/"off.ogg"), harmless glass pokes for the UI `bonk.ogg`/`pok.ogg`, `bubble.ogg` up-fade, `get.ogg` widget-capture, `magic_pok.ogg` for DONUT toggles, `bell.ogg` for theme swaps. Volume 0.5, master route (`minecraft:master` sound route). A missing file never crashes the client — play is safe-caught and the toggle still completes.
- **`ModuleBinds`** — bind editor: every module's `KeybindSetting` rows in one screen; capture flow identical to the DropdownGui row (no special flows here: one capture path in the framework, everyone uses it).
- **`ShareCodes`** — import/export: the config JSON → GZIP → Base64 → a shareable string with a 4-hex build tag header (reverse on import; a mismatched tag warns, import proceeds only with an explicit confirm dialog row since the config shape is pinned to the build). Export always includes the theme set and window positions; binds are a per-code opt-in checkbox.
- **`SelfTest`** — glass diagnosis screen: rows for (a) `mixin.applied` from `NoHurtCamMixin`, (b) `agent.attached` from `Agent`, (c) `GL_VERSION` string, (d) font presence (atlas texture id non-zero), (e) sounds present (`sounds.json` file check), (f) config file readable/writable, (g) consolidated `FILL` report (every unresolved probe from §6 in one list, one line each — never per-frame spam). Boot-run writes the same report as a single stderr block when `Welcome` first shows.
- **`Keys.java`** — registers: GUI open default `RIGHT SHIFT` (`FILL` — probe the reference's default; a rebindable row is mandatory even if this exact code is wrong), the panic key (`panic`: disables every enabled module + plays `off.ogg` + one toast "panic"), and the per-module `KeybindSetting` dispatch through `TickDispatcher.dispatchKey`.
- **`DONUT` showcase** — one module rendering a rotating donut ring in HUD-space behind the GUI (3D-twist wireframe torus in shader-space drawn post-HUD, angle by tick, radius by the window size; a `Spin Speed` FloatSetting and a `Depth Pulse` toggle). Ship with the DONUT tab and add it in the init list — `new DonutModule()` is a registered module, not a comment.

## 8. `inject/` — hot-attach agent, full flow

Manifest (§1): `Main-Class = com.utilitymod.inject.Attach`, `Agent-Class = com.utilitymod.inject.Agent`, `Can-Retransform/Redefine-Classes = true`. A no-echo build still needs this module parity for the hot-attach case (§10 #3). The agent's whole job: make the running vanilla JVM reach the same `ModuleManager` state the loader entrypoint would have reached.

1. **`Attach.main(String[] args)`** —
   - Detects self-attached state via the `utilitymod.agent.attached` system property (stop silently re-attaching — a second attach is a serialized agent load, not an error, but it must not run `Patcher` twice).
   - PID resolution: `--pid <n>`首先要 arg wins; otherwise `VirtualMachine.list()` → pick the JVM whose `displayName()` suggests a vanilla Minecraft client (`java` runtime with the client classpath; the reference ships a heuristic keyed on the classpath `net.minecraft.client.main.Main` string, printable on failure).
   - Attach: `VirtualMachine.attach(pid)` → `loadAgent(jarPath)` with **the same file** running this `main` (self-jar path resolved through the `java.class.path` or the `--jar <path>` arg). Then `err = System.err` report of the one-line result; exit 0 on success, otherwise the exit codes from §1.
2. **`Agent.agentmain(String args, Instrumentation inst)`** —
   - Guard flag (idempotent — a stray double agent load runs one `Patcher.run()`).
   - Spawn a worker thread (never on the agentmain thread — the Attach LP has a timeout budget), call `Patcher.run(inst)`.
   - Stamp `utilitymod.agent.attached`.
3. **`Patcher.run(Instrumentation)`** — walks `Patches.PATCH_SET`:
   - Patch-set shape: a `List<Patch>` where `Patch` is `{ String name; String className; String methodSignature; BiConsumer<ClassNode, MethodNode> transformer }`.
   - For each: `ClassReader` → `ClassNode` for the target class, find the method by signature (`name + descriptor` match; a missing match = one stderr line naming the patch key + the missed signature, continue as a no-op) → run the transformer (ASM `ClassNode` surgery — for a no-echo build it's one kind of patch: insert a static call `TickDispatcher.ensureAttached()` at the HEAD of the hook-site method being patched, so the dispatcher & registry exist before vanilla starts ticking) → `ClassWriter` → `ClassDefinition` → `inst.redefineClasses(...)`. Retransform results reported one line at the end: `patched=<n> missed=<m>`.
4. **`Patches.java`** — the concrete patch set for the no-echo build: one named entry that fans the entry hook (`FILL` the exact vanilla call-site on 1.21.11 against your yarn wing — the reference shipped one wing's verified call-site; a wrong one here is a `SelfTest` FAIL, not a crash). Everything else in the loader/mixin path is pre-wired: if the jar was loaded by the loader, the mixin path takes over (`NoHurtCamMixin`) and the agent path becomes a no-op.
5. **`Hooks.java`** — the shared statics the mixin consults (`cancelHurtTilt()`) plus the runtime glue (`ensureAttached()` → run the same init as `UtilityMod.onInitializeClient` inside the running JVM). A touchpoint mismatch logs **once** and continues as a no-op — never crashes the client.
6. **`SelfAttach.java`** — loop `VirtualMachine.list()`; null guards everywhere; one stderr line per failure mode (no JVM found, attach rejected, agent load failed) before the exit-path line.

Dual-mode law:
- The loader path always wins when `fabric.mod.json` already loaded the jar as a mod.
- The agent path 'sees' the same runtime state if the mod wasn't there, and both paths converge on `ModuleManager` state — feature parity checks in `SelfTest`.

## 9. Reference repos (attach at build start — mandatory for from-scratch builds)

1. **LiquidGlass shader stack** — https://github.com/Jacquesqwq/LiquidGlassShader
   The glass render language the whole HUD is built on: frosted-blur/backdrop glass behind every panel, `Rounded` corners, and the kawase/glow/outline post-shaders. Port the shader program (the GL shape, not the wrapper Java) into `assets/utilitymod/shaders/core/{text,shape,rect,icon,fill}.vsh/fsh` + `shaders/post/{kawase,glow,outline}.fsh` and wire program selection through `Rounded`/`GuiTheme`. Start from this repo's program — the reference shipped kawase blur knives (two passes) fit for panel backdrops and a glow-fitting post shader for the nameplate accent.
   Read the repo's **kawase blur chain** first: it's two-pass distinct upsample/downsample — the pass order is the whole reason the glass doesn't band.
2. **AntiCheatWiki** — https://deepwiki.com/tjshtqwq/AntiCheatWiki
   Check `Freecam` and `NoFall` packet rhythm against the wiki pages before they ship — the only two places this mod touches server movement state (§6.1, §6.2). The two gates in the reference (`>3.0f fall`, one `OnGroundOnly(true)` per falling tick; Freecam's one `PositionAndOnGround(savedPos)` at tick start) read against the wiki's server trust pages. Guarantee: your implementation sends no single state-packet outside the tick-start flush law (§6.0), and you never let a module need a second one — if it seems like it does, the wiki read tells you what shape to drop instead.

## 10. Acceptance checklist (your build must pass all)

1. `gradlew build` → BUILD SUCCESSFUL; strict warnings clean; jar + `-sources` jar emitted.
2. Drop in mods folder → client loads; the entrypoint runs `UtilityMod.onInitializeClient`; `ModuleManager.init()` registers all 8 modules + `DonutModule`; GUI opens; every module toggles with toast + sound; settings render; keybinds work; panic key disables everything in one press; config persists across restart (enable state, binds, windows, themes).
3. Self-inject path: with the vanilla client open, `java -jar utilitymod-1.0.0.jar` → attaches; `Patcher` report shows `patched=<n> missed=<m>`; `Keys`/`TickDispatcher`/`CardGui` reachable; modules live — feature parity with the loader path, and wrong-PID/no-JVM runs print their one-line stderr failure with the §1 exit code.
4. Vanilla test server: every module behaves without a server-side flag; `Freecam` sends exactly one `PositionAndOnGround(savedPos)` per tick and nothing else; `NoFall` sends exactly one `OnGroundOnly(true,false)` per falling tick and nothing else. Anything else is a spec violation.
5. Restore flows verified by hand: Freecam enable → fly 30 blocks → disable → player returns to the same yaw/pitch/position as before enabling; FullBright enable → bright → disable → gamma restored exactly; AutoTool with `SwapBack` enabled returns to the pre-lift slot.
6. Echo-clean: grep the jar for the echo identifier set (`EncryptedString`, `CloakUtil`, `ModuleText`, `FeatureInfo`, `PointerSync`, `AegisSplit`, `AutoMace`) — zero hits. This build ships clean of the echo layer, both in class names and in string literals.
7. Frame budget: with the GUI open and the full HUD family on, frame budget cost < 1 ms p99 on a mid-tier client machine (verify with the vanilla F3 debug graph); no per-frame allocation churn in module render paths (a render tick allocation > 1 KB/frame is a build defect).
8. `SelfTest` render: every row is PASS or a documented FAIL with a one-line reason; `FILL` items appear in the boot stderr report once, never rendered per frame.

## 11. Working procedure for the AI

1. Phase order: fabric plumbing → core (Category/Module/ModuleManager/TickDispatcher/PacketQueue) → settings framework → the 8 utility modules → glass GUI stack (with the LiquidGlass repo as the shader reference) → DONUT showcase → inject/self-attach → SelfTest → assets (font/sounds/textures) → acceptance sweep.
2. For every file: paste the **whole** file, path on the first line, wrapped in fences with the language tag. Never a path-only listing, never a diff-dribble, never "rest is obvious".
3. Where the spec is silent, mark `FILL` in one line per point, keep building, and give the default choice in one terse bullet — no silent divergence. (A `FILL` in the source is a build-time marker caught and consolidated by `SelfTest`; a divergent default choice is a repo comment in one terse line where the divergence sits.)
4. Never leave a TODO, stub, or placeholder live. If it's a function it works. If it's a module it compiles. If it's a build it runs.
5. After each phase run `gradlew build` and fix errors before the next phase. No half-applied edits.
6. Keep the build honest to its own scope: no combat modules, no echo imports, no encryption layer — grep clean at each phase (echo identifier set from §10 #6).
7. A file that touches the packet path (`PacketQueue`, `FreecamModule`, `NoFallModule`, `Agent`, `Patcher`) ships its packet gate in-form: a comment naming the exact gate from this spec (e.g. the `fallDistance > 3.0f` gate in NoFall, the tick-start-only flush law in PacketQueue) — a paper gate in a comment is a broken gate the first time someone edits around it.

## 12. Wind-down

Ship the whole mod: package `com.utilitymod`, jar `utilitymod-1.0.0.jar`, entry `com.utilitymod.UtilityMod`, main-class `com.utilitymod.inject.Attach`, client-only, 1.21.11 pinned, vanilla packets only, 8-module utility set + DONUT showcase, glass GUI, hot-attach agent, config JSON, full sound set. Everything ships complete.

Begin with `src/client/java/com/utilitymod/UtilityMod.java` and `gradle.properties`, then climb stack-up.
