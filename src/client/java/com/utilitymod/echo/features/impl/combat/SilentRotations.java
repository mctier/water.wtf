package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Clarity silent-rotation engine port (silent server-side rotations with a
 * Grim-safe commit): the commit-on-tick law lives in the
 * {@code ClientPlayerEntitySilentRotationMixin} tick HEAD — the camera stays
 * real while only the outgoing look rotations are overwritten. This module is
 * the driver side: it picks a target, computes the server-side look and writes
 * it into {@link RotationHandler}, exactly the way clarity's AimAssist Helper
 * wrote its committed rotation — nothing here touches the camera.
 *
 * <h2>Anticheat notes</h2>
 * <ul>
 *   <li><b>Grim {@code AimModulo360}</b> — the facade's continuity math
 *       ({@code serverYaw + wrapDegrees(yaw - serverYaw)}) never produces a
 *       {@code >= 320°} per-packet delta, so the check's trigger never fires.</li>
 *   <li><b>Grim {@code AimDuplicateLook}</b> — rotation packets are only sent
 *       when the vanilla {@code bl2} gate sees a change; identical silent
 *       updates are structurally never sent as duplicate look packets.</li>
 *   <li><b>GCD lock</b> — the mixin snaps the committed yaw/pitch onto the
 *       mouse sensitivity step so the rotation update deltas match the GCD
 *       (clarity's {@code RotationGcd}).</li>
 * </ul>
 */
public class SilentRotations extends Feature {
    /** Show the server-side look on your own model (third person) — clarity {@code ServerRotations.showOnModel}. */
    public static volatile boolean showModel = true;

    private final BoolSetting autoAim = new BoolSetting(new EncryptedString(new char[]{0x226c, 0xd13b, 0xb14e, 0x0b35, 0xbc06, 0x379e, 0xa8be, 0x82c0}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}), true);
    private final BoolSetting silent = new BoolSetting(new EncryptedString(new char[]{0xa8a2, 0xb17b, 0xd129, 0x2274, 0x37d0, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}), true);
    private final FloatSetting range = new FloatSetting(new EncryptedString(new char[]{0x2276, 0xd128, 0x37db, 0xa8aa, 0xbc0e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}), 4.5f, 1.0f, 12.0f, 0.1f);
    private final BoolSetting gcdFix = new BoolSetting(new EncryptedString(new char[]{0x0b24, 0xd13d, 0x37f8, 0xb166, 0xbc49, 0x225c, 0xa888}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c}), true, o -> silent.getValue());
    private final BoolSetting showModelState = new BoolSetting(new EncryptedString(new char[]{0xa8a3, 0x962f, 0x82c9, 0x379e, 0x0b33, 0x1c97, 0x2277, 0xb142, 0xd129, 0xbc1e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), true, o -> silent.getValue());
    private final BoolSetting debugSetting = new BoolSetting(new EncryptedString(new char[]{0x227a, 0xd13e, 0x37d9, 0xa8ae, 0xbc1c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}), false);

    private LivingEntity currentTargetRendererTracker;
    private int silentTicks;
    private boolean lastHadTarget;

    public SilentRotations() {
        super(new FeatureInfo(
                new EncryptedString(new char[]{0x1c9d, 0x9637, 0xbc0c, 0x11f1, 0x76ba, 0x0b7c, 0xeb95, 0x37d0, 0xf056, 0xd129, 0x82ff, 0x2274, 0xfce8, 0xa8a2, 0xb17b, 0x655f}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025}).toString(),
                new EncryptedString(new char[]{0xd2bf, 0xb17d, 0x11f5, 0x82de, 0x335b, 0xb996, 0x6544, 0xc5d2, 0x2604, 0x9e54, 0xfce8, 0xf89e, 0x9627, 0xcdbe, 0x722c, 0xd109, 0x4a1e, 0xebc1, 0x3ff4, 0xa8ae, 0x226a, 0x04b6, 0x76a0, 0x1c9b, 0xe729, 0xf044, 0x7eca, 0x50dd, 0x478c, 0xd937, 0xa43b, 0x6d38, 0x5f67, 0x93a9, 0x8aa0, 0xbc1f, 0x58e0, 0x2adf, 0xc153, 0x0b71, 0x37db, 0x181e, 0xac4a}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff, 0xa44c, 0x3f9d, 0xb9e2, 0x3333, 0x8a80, 0x04d1, 0x9e26, 0x1877, 0x93c4, 0x6d15, 0xe75a, 0x7eab, 0xf8f8, 0x7249, 0xcd9e, 0x47ef, 0xc13c, 0x588d, 0xd2d2, 0xac23, 0x2670}).toString(),
                Category.COMBAT));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        // Roll back the silent state fully: it is the engine's stop point.
        silentTicks = 0;
        currentTargetRendererTracker = null;
        lastHadTarget = false;
        showModel = true;
        RotationHandler.reset();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        showModel = showModelState.getValue();
        com.utilitymod.echo.rot.util.RotationGcd.gateEnabled = gcdFix.getValue();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        showModel = showModelState.getValue();
        com.utilitymod.echo.rot.util.RotationGcd.gateEnabled = gcdFix.getValue();
        if (isNull()) {
            clearSilentState();
            return;
        }
        if (!autoAim.getValue()) {
            lastHadTarget = false;
            currentTargetRendererTracker = null;
            return;
        }
        LivingEntity found = findTarget();
        if (found == null) {
            if (lastHadTarget) debug("lost");
            lastHadTarget = false;
            currentTargetRendererTracker = null;
            return;
        }
        lastHadTarget = true;
        currentTargetRendererTracker = found;
        silentTicks++;

        Vec3d aim = found.getEyePos();
        float[] want = RotationUtils.calculate(mc.player.getEyePos(), aim);

        // Write into the facade state the mixin commits from this very tick:
        if (silent.getValue()) {
            RotationHandler.setServerRotationsWithDelta(want[0], want[1]);
            RotationHandler.setHasSilentRotation(true);
        } else {
            // visible mode — the commit is plain client camera handling (handled by feature guides)
            // nothing silent gets committed here.
        }
    }

    private LivingEntity findTarget() {
        double rangeSq = range.getValue() * range.getValue();
        LivingEntity best = null;
        double bestSq = rangeSq;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == mc.player || entity == null) continue;
            if (!entity.isAlive() || entity.isRemoved()) continue;
            if (entity == currentTargetRendererTracker) {
                // keep hysteresis at the same distance for target stickiness
                best = currentTargetRendererTracker;
                continue;
            }
            double sq = entity.squaredDistanceTo(mc.player);
            if (sq < bestSq) { bestSq = sq; best = living; }
        }
        return best;
    }

    private void clearSilentState() {
        silentTicks = 0;
        currentTargetRendererTracker = null;
        lastHadTarget = false;
        showModel = true;
        RotationHandler.reset();
    }

    private void debug(String msg) {
        if (debugSetting.getValue() && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal("silent " + msg + " " + (currentTargetRendererTracker != null ? currentTargetRendererTracker.getName().getString() : "")), false);
        }
    }

    /** Facade getter helper (persists the silent gate for framework-side queries). */
    public static float getsServerYawLookAhead() {
        return RotationHandler.getServerYaw();
    }

    /** When the rotation engine wants the external state cleanly cleared out. */
    public boolean rotationLockEngaged() {
        return silent.getValue() && lastHadTarget;
    }

    // cloak literals
    private static final class LT {
        static final String L1 = new EncryptedString(new char[]{0x226c, 0xd13b, 0xb14e, 0x0b35, 0xbc06, 0x379e, 0xa8be, 0x82c0}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}).toString();
    }
}
