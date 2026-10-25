<div align="center">

# ⚡ UtilityMod

### late-injected Fabric utility client for Minecraft 1.21.11

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)
![Fabric](https://img.shields.io/badge/Fabric-0.18.1-DBD0B4?style=for-the-badge)
![No Mixin](https://img.shields.io/badge/injection-pure%20ASM-8A63D2?style=for-the-badge)
![License](https://img.shields.io/badge/license-GPL--3.0-4C9ED9?style=for-the-badge)

*No mod loader entry. No `mods` folder. Launch the game, then attach the jar.*

</div>

---

> **UtilityMod** hooks a running `1.21.11` client through **JVM Attach + ASM bytecode patching** —
> no Mixin, no `fabric.mod.json` init. **103 core module registrations + 12 Donut/Anubis modules**, a liquid-glass client menu, a HUD editor,
> waypoints, profiles and shareable config codes.

```
  Minecraft   1.21.11            Injection   com.utilitymod.inject.Attach  (Main-Class)
  Loader      Fabric 0.18.1                  com.utilitymod.inject.Agent   (Agent-Class)
  Mappings    Yarn 1.21.11+b3    Patcher     HEAD · RETURN · REDIRECT · FIELD_STORE
  Java        21  (required)     Mixin       ✗  none
```

---

## Expanded feature overview

The current codebase covers **50+ individual features** across combat, movement, visuals,
finding, automation and configuration. The inventory below documents existing features and
additions together, rather than claiming they all arrived in one release. Counts refer to
source registrations, not unique visible labels; Web UI is registered separately.

- **Combat:** Auto Cart, Xbow Cart, Auto Mace, Mace Swap, Stun Cob, shield breaking and spear utilities.
- **Movement:** Instant Jump and Edge Step alongside the movement features listed below.
- **Finding:** Nether Fortress, Bastion and Stronghold scanning alongside the existing finders.
- **Visuals:** Health Indicators, Armor Durability, Trajectory Render, Aim Visualizer, Effects HUD,
  Block Outline, Block Fill, Custom Crosshair, Jump Circles, Swing Pace, No Hurt Cam and No Render.
- **Utility:** Mouse Tweaks, Inventory Preview, Target Control, Teams and Purge.
- **Latest removal:** **Move Fix is removed**. Its movement-input correction and movement-rotation
  overrides are gone; movement hooks preserve vanilla values while other rotation hooks remain.

## ✨ Features

<details open>
<summary><b>⚔️  Combat</b></summary>

> Aura Guide · Shard Hack · Auto Anchor · Auto Mace + Mace Swap · Breach Kit · SnapFire ·
> Backtrack · Hitbox · Totem Hit · **Stun Cob** (web-below on shield stun) · Spear Lunge / Reach · Auto Cart · Xbow Cart

</details>

<details>
<summary><b>🏃  Movement</b></summary>

> **Combo Helper** — `W-Tap` · `S-Tap` · `Strafe` · `Round` *(cycles every reset)* · `Jump` · `Smart`
> *(best per situation)* + a **Distance keeper** that holds `1–3` blocks from your target
> <br>Speed · Step · Velocity · Sprint / Keep-Sprint · Snap-Tap · Jump Reset · No Slow · No Web ·
> No Lunge Cooldown · Fast Stairs · Timer · Auto Walk · GUI Walk · Freecam · No Fall

</details>

<details>
<summary><b>🎨  Visual</b></summary>

> Target HUD `2D/3D` · Liquid-Glass Hotbar / Nametags / Panels · Player ESP · Storage ESP ·
> **Block ESP** (picker + texture colours + shader styles) · **Ambience / Sky Art** (animated sky + your
> own images) · Motion Blur · Trails · Weapon Trails · Hit Particles / Markers / Sounds · Kill Effects ·
> Projectile / Shield / Teleport / Block effects · Custom Cape · Anonymous Skin · Target Marker · Compass ·
> World Time · Zoom · Fullbright · Session Stats · Notifications · HUD Overlay · Visual Profiler

</details>

<details>
<summary><b>🧭  Base Finding</b> &nbsp;<sub><i>— its own client menu tab</i></sub></summary>

> **Village Finder** *(requires villagers)* · **Ancient City Finder** *(reinforced deepslate / deep shriekers)*
> · **Base Finder** *(ender chests, enchant tables, anvils, beacons, shulkers, villagers → scored)* ·
> **Portal Finder** / **Stronghold Finder** / **Bastion Finder** / **Nether Fortress Finder**
> <br>Each marks finds in-world with optional notifications + auto-waypoints.

</details>

<details>
<summary><b>🛠️  Utility</b></summary>

> **Pathfinder** — Baritone-style `#mine` / `#goto` with digging, bridging, pillaring *(off by default,
> pauses in combat)* · **Auto Miner** *(mark two corners → clears the box)* · **Schematic Builder**
> *(`.litematic`)* · Auto Drain · Auto Totem · Auto Eat · Auto Tool · Armor Alert · Visual Range ·
> Logout Spots · **Chat Waypoints** *(coords in chat → waypoint)* · Auto Reconnect · Waypoints

</details>

---

## Donut / Anubis tools

- **Scanning:** Sus Chunk Scan, Prime Chunk Scan, Hole ESP and Netherite Finder.
- **Automation:** Donut No Fall, Shard Trade, AH Sell, AH Sniper, Order AH Seller,
  Order Dropper, TP Macro and Panic Pay.
- The Donut tab integrates these modules through the Anubis bridge.

## GUI, configuration and live Web UI

- **Cards GUI:** category sidebar, two-column module cards, live toggles and a settings sheet
  opened with the gear or right-click; available alongside the existing GUI styles.
- HUD editing, keybinds, profiles and shareable configuration codes.
- Enable **Web UI** under **Misc** to open the paired browser dashboard for live module toggles
  and settings editing. No separate Node server or database is required.
- Disable Web UI to stop the server and revoke the session.
- See [the Web UI guide](WEB-UI.md) for controls and setup.

<details>
<summary><b>Full source-backed module inventory (115 registrations)</b></summary>

Implementation names link to their registered classes; in-game display names can differ.
This list includes existing features as well as additions.

**Movement** (18)

[Freecam](src/client/java/com/utilitymod/echo/features/impl/movement/Freecam.java) / [Sprint](src/client/java/com/utilitymod/echo/features/impl/movement/Sprint.java) / [KeepSprintModule](src/client/java/com/utilitymod/echo/features/impl/movement/KeepSprintModule.java) / [Speed](src/client/java/com/utilitymod/echo/features/impl/movement/Speed.java) / [StepModule](src/client/java/com/utilitymod/echo/features/impl/movement/StepModule.java) / [NoSlowModule](src/client/java/com/utilitymod/echo/features/impl/movement/NoSlowModule.java) / [NoWebModule](src/client/java/com/utilitymod/echo/features/impl/movement/NoWebModule.java) / [JumpInstant](src/client/java/com/utilitymod/echo/features/impl/movement/JumpInstant.java) / [NoLungeCooldown](src/client/java/com/utilitymod/echo/features/impl/movement/NoLungeCooldown.java) / [JumpReset](src/client/java/com/utilitymod/echo/features/impl/movement/JumpReset.java) / [VelocityModule](src/client/java/com/utilitymod/echo/features/impl/movement/VelocityModule.java) / [ComboHelper](src/client/java/com/utilitymod/echo/features/impl/movement/ComboHelper.java) / [SnapTap](src/client/java/com/utilitymod/echo/features/impl/movement/SnapTap.java) / [FastStairsModule](src/client/java/com/utilitymod/echo/features/impl/movement/FastStairsModule.java) / [AutoWalkModule](src/client/java/com/utilitymod/echo/features/impl/movement/AutoWalkModule.java) / [EdgeStep](src/client/java/com/utilitymod/echo/features/impl/movement/EdgeStep.java) / [GuiWalk](src/client/java/com/utilitymod/echo/features/impl/movement/GuiWalk.java) / [TimerExploit](src/client/java/com/utilitymod/echo/features/impl/movement/TimerExploit.java)

**Core utilities** (7)

[NoFallModule](src/client/java/com/utilitymod/modules/NoFallModule.java) / [AutoToolModule](src/client/java/com/utilitymod/modules/AutoToolModule.java) / [FullBrightModule](src/client/java/com/utilitymod/modules/FullBrightModule.java) / [NoHurtCamModule](src/client/java/com/utilitymod/modules/NoHurtCamModule.java) / [InventoryPreviewModule](src/client/java/com/utilitymod/modules/InventoryPreviewModule.java) / [NoRenderModule](src/client/java/com/utilitymod/modules/NoRenderModule.java) / [NametagsModule](src/client/java/com/utilitymod/modules/NametagsModule.java)

**Combat** (15)

[AimVector](src/client/java/com/utilitymod/echo/features/impl/combat/AimVector.java) / [SpearTip](src/client/java/com/utilitymod/echo/features/impl/combat/SpearTip.java) / [HitboxMod](src/client/java/com/utilitymod/echo/features/impl/combat/HitboxMod.java) / [Crystalline](src/client/java/com/utilitymod/echo/features/impl/combat/Crystalline.java) / [AnchorFlow](src/client/java/com/utilitymod/echo/features/impl/combat/AnchorFlow.java) / [AutoCart](src/client/java/com/utilitymod/echo/features/impl/combat/AutoCart.java) / [XbowCart](src/client/java/com/utilitymod/echo/features/impl/combat/XbowCart.java) / [AutoMace](src/client/java/com/utilitymod/echo/features/impl/combat/AutoMace.java) / [MaceSwap](src/client/java/com/utilitymod/echo/features/impl/combat/MaceSwap.java) / [Backoff](src/client/java/com/utilitymod/echo/features/impl/combat/Backoff.java) / [BreachKit](src/client/java/com/utilitymod/echo/features/impl/combat/BreachKit.java) / [SpearLungeModule](src/client/java/com/utilitymod/echo/features/impl/combat/SpearLungeModule.java) / [SpearReachModule](src/client/java/com/utilitymod/echo/features/impl/combat/SpearReachModule.java) / [TotemHitModule](src/client/java/com/utilitymod/echo/features/impl/combat/TotemHitModule.java) / [StunCob](src/client/java/com/utilitymod/echo/features/impl/combat/StunCob.java)

**Render** (47)

[PlayerESP](src/client/java/com/utilitymod/echo/features/impl/render/PlayerESP.java) / [SwingPace](src/client/java/com/utilitymod/echo/features/impl/render/SwingPace.java) / [TargetHud](src/client/java/com/utilitymod/echo/features/impl/render/TargetHud.java) / [HealthIndicators](src/client/java/com/utilitymod/echo/features/impl/render/HealthIndicators.java) / [ArmorDurability](src/client/java/com/utilitymod/echo/features/impl/render/ArmorDurability.java) / [TrajectoryRenderModule](src/client/java/com/utilitymod/echo/features/impl/render/TrajectoryRenderModule.java) / [AimVisualizerModule](src/client/java/com/utilitymod/echo/features/impl/render/AimVisualizerModule.java) / [HudOverlay](src/client/java/com/utilitymod/echo/features/impl/render/HudOverlay.java) / [GlassPanel](src/client/java/com/utilitymod/echo/features/impl/render/GlassPanel.java) / [Notifications](src/client/java/com/utilitymod/echo/features/impl/render/Notifications.java) / [GlassHotbar](src/client/java/com/utilitymod/echo/features/impl/render/GlassHotbar.java) / [EffectsHud](src/client/java/com/utilitymod/echo/features/impl/render/EffectsHud.java) / [Bearing](src/client/java/com/utilitymod/echo/features/impl/render/Bearing.java) / [SessionStats](src/client/java/com/utilitymod/echo/features/impl/render/SessionStats.java) / [KillEffects](src/client/java/com/utilitymod/echo/features/impl/render/KillEffects.java) / [HitParticles](src/client/java/com/utilitymod/echo/features/impl/render/HitParticles.java) / [BlockOutline](src/client/java/com/utilitymod/echo/features/impl/render/BlockOutline.java) / [GlassNametags](src/client/java/com/utilitymod/echo/features/impl/render/GlassNametags.java) / [WorldTime](src/client/java/com/utilitymod/echo/features/impl/render/WorldTime.java) / [CustomCape](src/client/java/com/utilitymod/echo/features/impl/render/CustomCape.java) / [AnonymousSkin](src/client/java/com/utilitymod/echo/features/impl/render/AnonymousSkin.java) / [TargetMarker](src/client/java/com/utilitymod/echo/features/impl/render/TargetMarker.java) / [HitSounds](src/client/java/com/utilitymod/echo/features/impl/render/HitSounds.java) / [MotionBlur](src/client/java/com/utilitymod/echo/features/impl/render/MotionBlur.java) / [ProjectileEffects](src/client/java/com/utilitymod/echo/features/impl/render/ProjectileEffects.java) / [WeaponTrails](src/client/java/com/utilitymod/echo/features/impl/render/WeaponTrails.java) / [BlockEffects](src/client/java/com/utilitymod/echo/features/impl/render/BlockEffects.java) / [ShieldEffects](src/client/java/com/utilitymod/echo/features/impl/render/ShieldEffects.java) / [TeleportEffects](src/client/java/com/utilitymod/echo/features/impl/render/TeleportEffects.java) / [VisualProfiler](src/client/java/com/utilitymod/echo/features/impl/render/VisualProfiler.java) / [BlockFill](src/client/java/com/utilitymod/echo/features/impl/render/BlockFill.java) / [SkyArt](src/client/java/com/utilitymod/echo/features/impl/render/SkyArt.java) / [VillageFinder](src/client/java/com/utilitymod/echo/features/impl/render/VillageFinder.java) / [AncientCityFinder](src/client/java/com/utilitymod/echo/features/impl/render/AncientCityFinder.java) / [BaseFinder](src/client/java/com/utilitymod/echo/features/impl/render/BaseFinder.java) / [Fullbright](src/client/java/com/utilitymod/echo/features/impl/render/Fullbright.java) / [PortalFinder](src/client/java/com/utilitymod/echo/features/impl/render/PortalFinder.java) / [StrongholdFinder](src/client/java/com/utilitymod/echo/features/impl/render/StrongholdFinder.java) / [BastionFinder](src/client/java/com/utilitymod/echo/features/impl/render/BastionFinder.java) / [NetherFortressFinder](src/client/java/com/utilitymod/echo/features/impl/render/NetherFortressFinder.java) / [WaypointsModule](src/client/java/com/utilitymod/echo/features/impl/render/WaypointsModule.java) / [CustomCrosshair](src/client/java/com/utilitymod/echo/features/impl/render/CustomCrosshair.java) / [Zoom](src/client/java/com/utilitymod/echo/features/impl/render/Zoom.java) / [Trails](src/client/java/com/utilitymod/echo/features/impl/render/Trails.java) / [JumpCircles](src/client/java/com/utilitymod/echo/features/impl/render/JumpCircles.java) / [HitMarkers](src/client/java/com/utilitymod/echo/features/impl/render/HitMarkers.java) / [StorageEsp](src/client/java/com/utilitymod/echo/features/impl/render/StorageEsp.java)

**Misc** (16)

[MouseTweaks](src/client/java/com/utilitymod/echo/features/impl/misc/MouseTweaks.java) / [AutoDrain](src/client/java/com/utilitymod/echo/features/impl/misc/AutoDrain.java) / [MinerFlow](src/client/java/com/utilitymod/echo/features/impl/misc/MinerFlow.java) / [AutoBlockMiner](src/client/java/com/utilitymod/echo/features/impl/misc/AutoBlockMiner.java) / [SchematicBuilder](src/client/java/com/utilitymod/echo/features/impl/misc/SchematicBuilder.java) / [ChatWaypoints](src/client/java/com/utilitymod/echo/features/impl/misc/ChatWaypoints.java) / [TotemFlow](src/client/java/com/utilitymod/echo/features/impl/misc/TotemFlow.java) / [AutoEat](src/client/java/com/utilitymod/echo/features/impl/misc/AutoEat.java) / [AutoTool](src/client/java/com/utilitymod/echo/features/impl/misc/AutoTool.java) / [ArmorAlert](src/client/java/com/utilitymod/echo/features/impl/misc/ArmorAlert.java) / [VisualRange](src/client/java/com/utilitymod/echo/features/impl/misc/VisualRange.java) / [LogoutSpots](src/client/java/com/utilitymod/echo/features/impl/misc/LogoutSpots.java) / [AutoReconnect](src/client/java/com/utilitymod/echo/features/impl/misc/AutoReconnect.java) / [TargetControlModule](src/client/java/com/utilitymod/echo/features/impl/misc/TargetControlModule.java) / [Teams](src/client/java/com/utilitymod/echo/features/impl/misc/Teams.java) / [ExitGate](src/client/java/com/utilitymod/echo/features/impl/misc/ExitGate.java)

**Donut / Anubis** (12)

[SusChunkScan](src/client/java/com/utilitymod/anubis/module/impl/render/SusChunkScan.java) / [PrimeChunkScan](src/client/java/com/utilitymod/anubis/module/impl/render/PrimeChunkScan.java) / [HoleEspModule](src/client/java/com/utilitymod/anubis/module/impl/render/HoleEspModule.java) / [NetheriteFinderModule](src/client/java/com/utilitymod/anubis/module/impl/misc/NetheriteFinderModule.java) / [DonutNoFallModule](src/client/java/com/utilitymod/anubis/module/impl/misc/DonutNoFallModule.java) / [AutoSellModule](src/client/java/com/utilitymod/anubis/module/impl/donutmacros/AutoSellModule.java) / [AhSellModule](src/client/java/com/utilitymod/anubis/module/impl/donutmacros/AhSellModule.java) / [AhSniperModule](src/client/java/com/utilitymod/anubis/module/impl/donutmacros/AhSniperModule.java) / [OrderAhSellerModule](src/client/java/com/utilitymod/anubis/module/impl/donutmacros/OrderAhSellerModule.java) / [OrderDropperModule](src/client/java/com/utilitymod/anubis/module/impl/donutmacros/OrderDropperModule.java) / [TpMacroModule](src/client/java/com/utilitymod/anubis/module/impl/donutmacros/TpMacroModule.java) / [PanicPayModule](src/client/java/com/utilitymod/anubis/module/impl/donutmacros/PanicPayModule.java)

</details>

## 🔨 Building

> requires **JDK 21** and **Gradle 8.14.5** (used for the verified build).
> This checkout has wrapper configuration but no `gradlew` launcher; use an installed Gradle distribution.

```bash
gradle build          # output → build/libs/
```

The game locks a jar once injected, so for repeated runs build under a fresh name:

```bash
gradle build -Parchives_base_name=utilitymod-dev
```

## 💉 Injecting

```
1.  launch Minecraft 1.21.11   (on Java 21)
2.  attach:
```
```bash
"C:\Program Files\Java\jdk-21.0.12\bin\java.exe" -jar utilitymod-<name>.jar
```

> 🪄 Or just run **`inject.bat`** (bundled beside the jar) — it finds/installs JDK 21, waits for the game
> and attaches for you. **Double-click the `.bat`, not the `.jar`.** Default open bind: **Right Shift**.
>
> ⚠️ A Java error / installer popup from double-clicking a jar is a *file-association* problem on that PC,
> not the jar. Always launch through `inject.bat`.

---

## 🙌 Credits

| | |
| **Jacquesqwq**, **Kalanaki** | liquid-glass shaders *(MIT — see `shaders/glass/LICENSE`)*
| **Pa-dej / SoupAPI** | visual asset inspiration *(capes, markers, particles, sounds)* |

<details>
<summary><b>💎 Worth skidding</b></summary>

| Thing | Where |
|---|---|
| Late ASM injection (no Mixin) on 1.21.11 | `inject/Patcher` · `Patches` · `Hooks` |
| Liquid-glass GUI + framebuffer shader passes | `echo/render/GlassRenderer` · `render3/api` |
| Silent-aim rotation handler (GCD / WindMouse) | `echo/utils/rotation` |
| World→GUI projection for ESP under injection | `echo/Render3D` · `EchoEvents.lastFrame()` |
| Chunk-scan finders (villages, cities, bases) | `echo/features/impl/render/*Finder` |

</details>

---

<div align="center">

### 📜 License

**GPL-3.0** — use, modify and redistribute it, *including commercially* (GPL can't carry a no-selling clause).
<br>Keep the §7(b) attribution: **“Contains code from UtilityMod by fakemoon”** and this notice.
<br>Third-party assets keep their own licenses.

<sub>made with ⚡ by <b>fakemoon</b></sub>

</div>
