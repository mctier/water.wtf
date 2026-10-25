package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.utils.simulation.ArrowSimulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BowItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Arrow landing prediction and arc solving for the cart modules (vanilla arrow physics via ArrowSimulation). */
final class Ballistics {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    static final float CROSSBOW_SPEED = 3.15f;

    private Ballistics() {}

    /** Launch speed of a bow drawn for {@code ticks} (vanilla: pull progress * 3). */
    static float bowSpeed(int ticks) { return BowItem.getPullProgress(ticks) * 3f; }

    static Vec3d dir(float yaw, float pitch) {
        float p = pitch * MathHelper.RADIANS_PER_DEGREE, y = -yaw * MathHelper.RADIANS_PER_DEGREE;
        float c = MathHelper.cos(p);
        return new Vec3d(MathHelper.sin(y) * c, -MathHelper.sin(p), MathHelper.cos(y) * c);
    }

    /** Where an arrow fired along (yaw, pitch) at {@code speed} hits a block, or null. */
    static BlockHitResult landing(float yaw, float pitch, float speed) {
        if (mc.player == null || speed <= 0.05f) return null;
        ArrowSimulation sim = new ArrowSimulation(mc.player.getEyePos(), dir(yaw, pitch).multiply(speed), mc.player);
        BlockHitResult hit = sim.simulateUntilHit(200);
        return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** Closest the arrow path gets to {@code target} (blocks). */
    private static double miss(float yaw, float pitch, float speed, Vec3d target) {
        ArrowSimulation sim = new ArrowSimulation(mc.player.getEyePos(), dir(yaw, pitch).multiply(speed), mc.player);
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 120 && !sim.isInGround(); i++) {
            sim.simulateTick();
            best = Math.min(best, sim.getPosition().distanceTo(target));
            if (sim.getPosition().y < target.y - 6) break;
        }
        return best;
    }

    /**
     * {yaw, pitch} that puts the arrow on {@code target}. Arc 0 = flattest valid arc, 100 = highest lob;
     * only arcs that actually pass within ~0.5 blocks of the target are considered. Null when none reach.
     */
    static float[] solve(Vec3d target, float speed, float arc) {
        if (mc.player == null) return null;
        Vec3d d = target.subtract(mc.player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        List<Float> ok = new ArrayList<>();
        for (float pitch = 60f; pitch >= -70f; pitch -= 0.75f) {
            if (miss(yaw, pitch, speed, target) < 0.55) ok.add(pitch);
        }
        if (ok.isEmpty()) return null;
        // ok is ordered from lowest aim (largest pitch) to highest lob (smallest pitch)
        int idx = Math.round(MathHelper.clamp(arc, 0, 100) / 100f * (ok.size() - 1));
        return new float[]{yaw, ok.get(idx)};
    }
}
