package com.utilitymod.core;

import com.utilitymod.modules.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleManager {
    private static final Map<String, Module> MODULES = new LinkedHashMap<>();

    private ModuleManager() {}

    public static void init() {
        // SSP-focused + test-server-safe (all vanilla packets only)
        // Freecam migrated to Echo: no extra flying packets, spoofs vanilla's single flying
        // to savedPos via EventMove.Pre. Old FreecamModule intentionally dropped â€” it enqueued
        // a second flying/tick which tripped Grim Timer + froze the player when combined with
        // StunCob (range checks against camera pos vs server pos).
        register(new com.utilitymod.echo.features.impl.movement.Freecam());
        register(new NoFallModule());
        register(new AutoToolModule());
        register(new FullBrightModule());
        register(new NoHurtCamModule());
        register(new InventoryPreviewModule());
        register(new NoRenderModule());
        register(new NametagsModule());
        // Ported from echo (com.utilitymod.echo.features.impl): full combat + movement tabs.
        register(new com.utilitymod.echo.features.impl.combat.TrigAttack());
        register(new com.utilitymod.echo.features.impl.combat.HitboxMod());
        register(new com.utilitymod.echo.features.impl.combat.Shrike());
        register(new com.utilitymod.echo.features.impl.combat.PointerSync());
        register(new com.utilitymod.echo.features.impl.combat.Crystalline());
        register(new com.utilitymod.echo.features.impl.combat.AutoCity());
        register(new com.utilitymod.echo.features.impl.combat.CrystalBurst());
        register(new com.utilitymod.echo.features.impl.combat.AnchorFlow());
        register(new com.utilitymod.echo.features.impl.combat.AutoMace());
        register(new com.utilitymod.echo.features.impl.combat.MaceSwap());
        register(new com.utilitymod.echo.features.impl.combat.Backoff());
        register(new com.utilitymod.echo.features.impl.combat.AegisSplit());
        register(new com.utilitymod.echo.features.impl.combat.SpearLungeModule());
        register(new com.utilitymod.echo.features.impl.combat.SpearReachModule());
        register(new com.utilitymod.echo.features.impl.combat.TotemHitModule());
        register(new com.utilitymod.echo.features.impl.movement.Sprint());
        register(new com.utilitymod.echo.features.impl.movement.KeepSprintModule());
        register(new com.utilitymod.echo.features.impl.movement.Speed());
        register(new com.utilitymod.echo.features.impl.movement.StepModule());
        register(new com.utilitymod.echo.features.impl.movement.NoSlowModule());
        register(new com.utilitymod.echo.features.impl.movement.NoWebModule());
        register(new com.utilitymod.echo.features.impl.movement.JumpInstant());
        register(new com.utilitymod.echo.features.impl.movement.NoLungeCooldown());
        register(new com.utilitymod.echo.features.impl.movement.JumpReset());
        register(new com.utilitymod.echo.features.impl.movement.VelocityModule());
        register(new com.utilitymod.echo.features.impl.movement.ComboHelper());
        register(new com.utilitymod.echo.features.impl.movement.SnapTap());
        register(new com.utilitymod.echo.features.impl.movement.FastStairsModule());
        register(new com.utilitymod.echo.features.impl.movement.AutoWalkModule());
        register(new com.utilitymod.echo.features.impl.movement.EdgeStep());
        register(new com.utilitymod.echo.features.impl.movement.GuiWalk());
        register(new com.utilitymod.echo.features.impl.movement.TimerExploit());
        register(new com.utilitymod.echo.features.impl.render.PlayerESP());
        register(new com.utilitymod.echo.features.impl.render.SwingPace());
        register(new com.utilitymod.echo.features.impl.render.TargetHud());
        register(new com.utilitymod.echo.features.impl.render.HealthIndicators());
        register(new com.utilitymod.echo.features.impl.render.ArmorDurability());
        register(new com.utilitymod.echo.features.impl.misc.MouseTweaks());
        register(new com.utilitymod.echo.features.impl.render.TrajectoryRenderModule());
        register(new com.utilitymod.echo.features.impl.render.AimVisualizerModule());
        register(new com.utilitymod.echo.features.impl.render.HudOverlay());
        register(new com.utilitymod.echo.features.impl.render.GlassPanel());
        register(new com.utilitymod.echo.features.impl.render.Notifications());
        register(new com.utilitymod.echo.features.impl.combat.StunCob());
        register(new com.utilitymod.echo.features.impl.render.GlassHotbar());
        register(new com.utilitymod.echo.features.impl.render.EffectsHud());
        register(new com.utilitymod.echo.features.impl.render.Bearing());
        register(new com.utilitymod.echo.features.impl.render.SessionStats());
        register(new com.utilitymod.echo.features.impl.render.KillEffects());
        register(new com.utilitymod.echo.features.impl.render.HitParticles());
        register(new com.utilitymod.echo.features.impl.render.BlockOutline());
        register(new com.utilitymod.echo.features.impl.render.GlassNametags());
        register(new com.utilitymod.echo.features.impl.render.WorldTime());
        register(new com.utilitymod.echo.features.impl.render.CustomCape());
        register(new com.utilitymod.echo.features.impl.render.AnonymousSkin());
        register(new com.utilitymod.echo.features.impl.render.TargetMarker());
        register(new com.utilitymod.echo.features.impl.render.HitSounds());
        register(new com.utilitymod.echo.features.impl.render.MotionBlur());
        register(new com.utilitymod.echo.features.impl.render.ProjectileEffects());
        register(new com.utilitymod.echo.features.impl.render.WeaponTrails());
        register(new com.utilitymod.echo.features.impl.render.BlockEffects());
        register(new com.utilitymod.echo.features.impl.render.ShieldEffects());
        register(new com.utilitymod.echo.features.impl.render.TeleportEffects());
        register(new com.utilitymod.echo.features.impl.render.VisualProfiler());
        register(new com.utilitymod.echo.features.impl.render.BlockFill());
        register(new com.utilitymod.echo.features.impl.render.SkyArt());
        register(new com.utilitymod.echo.features.impl.render.VillageFinder());
        register(new com.utilitymod.echo.features.impl.render.AncientCityFinder());
        register(new com.utilitymod.echo.features.impl.render.BaseFinder());
        register(new com.utilitymod.echo.features.impl.misc.MinerFlow());
        register(new com.utilitymod.echo.features.impl.misc.AutoBlockMiner());
        register(new com.utilitymod.echo.features.impl.misc.SchematicBuilder());
        register(new com.utilitymod.echo.features.impl.render.Fullbright());
        register(new com.utilitymod.echo.features.impl.render.PortalFinder());
        register(new com.utilitymod.echo.features.impl.render.StrongholdFinder());
        register(new com.utilitymod.echo.features.impl.render.BastionFinder());
        register(new com.utilitymod.echo.features.impl.render.NetherFortressFinder());
        register(new com.utilitymod.echo.features.impl.misc.ChatWaypoints());
        register(new com.utilitymod.echo.features.impl.misc.TotemFlow());
        register(new com.utilitymod.echo.features.impl.misc.Keystone());
        register(new com.utilitymod.echo.features.impl.misc.AutoEat());
        register(new com.utilitymod.echo.features.impl.misc.AutoTool());
        register(new com.utilitymod.echo.features.impl.misc.ArmorAlert());
        register(new com.utilitymod.echo.features.impl.misc.VisualRange());
        register(new com.utilitymod.echo.features.impl.misc.LogoutSpots());
        register(new com.utilitymod.echo.features.impl.render.WaypointsModule());
        register(new com.utilitymod.echo.features.impl.misc.AutoReconnect());
        register(new com.utilitymod.echo.features.impl.render.CustomCrosshair());
        register(new com.utilitymod.echo.features.impl.render.Zoom());
        register(new com.utilitymod.echo.features.impl.render.Trails());
        register(new com.utilitymod.echo.features.impl.render.JumpCircles());
        register(new com.utilitymod.echo.features.impl.render.HitMarkers());
        register(new com.utilitymod.echo.features.impl.render.StorageEsp());
        register(new com.utilitymod.echo.features.impl.misc.TargetControlModule());
        register(new com.utilitymod.echo.features.impl.misc.Teams());
        register(new com.utilitymod.echo.features.impl.misc.ExitGate());
        // LegacyPort crystal-pvp ports + shared server-side rotations
        com.utilitymod.echo.handlers.ServerRotations.init();
        register(new com.utilitymod.echo.features.impl.cpvp.Verrik());
        register(new com.utilitymod.echo.features.impl.cpvp.PredictDoubleHand());
        register(new com.utilitymod.echo.features.impl.cpvp.Pylon());
        register(new com.utilitymod.echo.features.impl.cpvp.Kervo());
        register(new com.utilitymod.echo.features.impl.cpvp.Netsu());
        register(new com.utilitymod.echo.features.impl.cpvp.Octave());
        register(new com.utilitymod.echo.features.impl.cpvp.AnchorExploder());
        register(new com.utilitymod.echo.features.impl.cpvp.Tessel());
        register(new com.utilitymod.echo.features.impl.cpvp.AnchorPearlCatch());
        register(new com.utilitymod.echo.features.impl.cpvp.AutoDrain());
        register(new com.utilitymod.echo.features.impl.cpvp.AutoSafeCart());
        register(new com.utilitymod.echo.features.impl.cpvp.CartAura());
        register(new com.utilitymod.echo.features.impl.cpvp.CartPearlCatch());
        register(new com.utilitymod.echo.features.impl.cpvp.CartRefill());
        register(new com.utilitymod.echo.features.impl.cpvp.CartStealer());
        register(new com.utilitymod.echo.features.impl.cpvp.Trellis());
        register(new com.utilitymod.echo.features.impl.cpvp.XbowCart());
        register(new com.utilitymod.echo.features.impl.cpvp.Phial());
        com.utilitymod.echo.hud.WaterHud.init();
        com.utilitymod.gui.SettingDefaults.capture();   // before GuiConfig.load: these are factory values   // water.lol HUD set (after every module registered its elements)
        // Add more here - all must use PacketQueue.enqueue at tick start
    }

    /** Snapshot for per-tick/per-frame loops: a register() mid-loop can't throw ConcurrentModificationException. */
    private static volatile Module[] snapshot = new Module[0];

    public static synchronized void register(Module m) {
        MODULES.put(m.getName().toLowerCase(), m);
        snapshot = MODULES.values().toArray(new Module[0]);
    }

    public static void onTickStart(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        for (Module m : snapshot) {
            if (m.isEnabled()) {
                try { m.onTickStart(client); } catch (Throwable t) { t.printStackTrace(); }
            }
        }
    }

    public static Module get(String name) {
        // map keys are heap-cloaked (PUA) names; cloak the request the same way
        return MODULES.get(com.utilitymod.echo.utils.CloakUtil.heapSafe(name).toLowerCase());
    }
    public static Collection<Module> all() { return java.util.List.of(snapshot); }

    public static void onHudRender(DrawContext context, float tickDelta) {
        for (Module module : snapshot) {
            if (module.isEnabled()) {
                try { module.onHudRender(context, tickDelta); } catch (Throwable t) { t.printStackTrace(); }
            }
        }
    }
}
