package com.utilitymod.echo.features.impl.movement;

import java.util.Optional;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.api.EntityMotionPacketCompat;
import com.utilitymod.echo.utils.player.PlayerUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Velocity reducer / canceller patched against OpenVulcan combat/velocity/VelocityA|B.
 *
 * <p><b>VelocityA (vertical, OpenVulcan)</b> flags when {@code firstTickResponse} fails to
 * decay by at least the hardcoded {@code 0.999} multiplier for 3 ticks after a velocity packet,
 * i.e. when the player's Y velocity is HIGHER than expected (an amplifier). A reducer / canceller
 * is NOT caught — amount3 ≤ 0 in that path. Cancellation of Y velocity therefore stays silent.
 *
 * <p><b>VelocityB (horizontal, OpenVulcan)</b> flags when
 * {@code (actual_horizontal_speed - expected_horizontal_speed) / expected > 0.21}, i.e. only
 * when horizontal motion EXCEEDS the server-approved knockback by more than 21%. Reduction /
 * cancellation of XZ velocity never crosses the threshold.
 *
 * <p><b>Grim Simulation (OffsetHandler)</b> is the actual ceiling: prediction offset ≥ 0.001
 * flags verbosely, ≥ 0.1 triggers an immediate violation setback. Grim re-runs the knockback
 * packet match-for-match, so the DELETED portion of a reduced knockback lands as the sim offset
 * for that tick. {@link #MAX_DELETED_KB} caps the deleted magnitude at 0.095 so every reduced
 * tick stays inside Grim's sub-setback band — verbose flags only, never the teleport loop.
 *
 * <p><b>Modes</b>
 * <ul>
 *   <li><i>Modify</i> — reduce the knockback vector to {@code amount} of its magnitude on every
 *       velocity packet. Grim-safe up to 0.095 deleted magnitude per tick.
 *   <li><i>Reduce</i> — on {@code hurtTime == 7} (3 ticks into the hurt animation, i.e. 3 ticks
 *       after the velocity packet landed) force a near-stationary descent that matches Grim's
 *       predicted idle Y velocity.
 *   <li><i>Vulcan Decay</i> — the OpenVulcan-specific bypass: preserve the FULL vertical
 *       component of the knockback (so VelocityA's 0.999 decay check sees natural physics),
 *       reduce horizontal by {@code amount} freely (VelocityB only catches amplification).
 *       The 3-tick window is handled implicitly by vanilla physics after the packet lands.
 * </ul>
 */
public class VelocityModule extends Feature {

	public VelocityModule() {
		super(new FeatureInfo(
			Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0xd12c,0xb166,0x0b28,0xbc06,0x37dd,0xa8ae,0x82d4}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}).toString()),
			Concat.of(LT.L1),
			Category.MOVEMENT
		));
	}
    private final ModeSetting mode = new ModeSetting(
            Concat.of(LT.L2),
            Concat.of(LT.L3),
            Concat.of(LT.L3),
            Concat.of(LT.L4),
            Concat.of("Vulcan Decay")
    );
    private final FloatSetting amount = new FloatSetting(
            Concat.of(LT.L5),
            0.0f,
            0.0f,
            1.0f,
            0.01f,
            p -> mode.is(Concat.of(LT.L3)) || mode.is(Concat.of("Vulcan Decay"))
    );
    private final MultiModeSetting sources = new MultiModeSetting(
            Concat.of(LT.L6),
            Concat.of(LT.L7),
            Concat.of(LT.L8)
    );
    private boolean justProcessedExplosion = false;

    // Grim Simulation (OffsetHandler): prediction offset >= 0.001 flags, >= 0.1 executes
    // an immediate violation setback. A knockback packet is predicted match-for-match by
    // the engine, so the DELETED portion of a reduced knockback lands as the offset for
    // that tick. Capping the deleted magnitude at 0.095 keeps every reduced tick inside
    // the sub-setback band - verbose flags only, never the teleport loop.
    private static final double MAX_DELETED_KB = 0.095;

    // OpenVulcan VelocityA uses a 3-tick window with 0.999 Y-decay expectation. We track the
    // age of the player when a velocity packet arrives; while age - lastKbTick < 3, the module's
    // Vulcan Decay mode keeps Y untouched so natural physics produce the server-expected decay.
    private int lastKbTick = -1;
    private static final int VULCAN_TRACK_TICKS = 3;

    private Vec3d cappedKeep(Vec3d packetVelocity, float amountValue) {
        double len = packetVelocity.length();
        if (len <= 1.0e-9) return Vec3d.ZERO;
        double keep = Math.max(amountValue, 1.0 - MAX_DELETED_KB / len);
        return packetVelocity.multiply(keep);
    }

    /** Horizontal-only cap that matches cappedKeep's math but only against the XZ magnitude. */
    private Vec3d cappedKeepXZ(Vec3d packetVelocity, float amountValue) {
        double xzLen = Math.sqrt(packetVelocity.x * packetVelocity.x + packetVelocity.z * packetVelocity.z);
        if (xzLen <= 1.0e-9) return packetVelocity;
        double keep = Math.max(amountValue, 1.0 - MAX_DELETED_KB / xzLen);
        return new Vec3d(packetVelocity.x * keep, packetVelocity.y, packetVelocity.z * keep);
    }

    @EventSubscribe
    public void onVelocity(EventPacketReceive event) {
        if (isNull()) return;
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket) {
            if (mc.player == null || EntityMotionPacketCompat.id(velocityPacket) != mc.player.getId()) return;

            boolean isExplosionRelated = justProcessedExplosion && sources.isEnabled(Concat.of(LT.L8));
            justProcessedExplosion = false;

            if (isExplosionRelated && !sources.isEnabled(Concat.of(LT.L7))) {
                if (mode.is(Concat.of(LT.L3)) || mode.is(Concat.of("Vulcan Decay"))) {
                    event.cancel();
                }
                return;
            }

            if (!sources.isEnabled(Concat.of(LT.L7))) {
                return;
            }

            if (mode.is(Concat.of(LT.L3))) {
                if (amount.getValue() == 1.0f) return;
                float amountValue = amount.getValue();
                if (amountValue == 0.0f) {
                    event.cancel();
                } else {
                    Vec3d packetVelocity = EntityMotionPacketCompat.movement(velocityPacket);
                    Vec3d kept = cappedKeep(packetVelocity, amountValue);
                    event.cancel();
                    mc.player.setVelocity(kept.x, kept.y, kept.z);
                }
            } else if (mode.is(Concat.of(LT.L4))) {
                if (mc.player.hurtTime == 7) {
                    PlayerUtils.setMotionX(mc.player.getVelocity().getX() * 0.25);
                    PlayerUtils.setMotionY(-0.0535f);
                    PlayerUtils.setMotionZ(mc.player.getVelocity().getZ() * 0.25);
                }
            } else if (mode.is(Concat.of("Vulcan Decay"))) {
                // OpenVulcan VelocityA passes only when Y velocity decays at >= 0.999 per tick
                // for 3 ticks after this packet. Preserve the full Y component and let vanilla
                // physics (gravity + air drag) produce a decay the server considers natural.
                // VelocityB flags amplification only, so XZ reduction via `amount` is silent.
                Vec3d packetVelocity = EntityMotionPacketCompat.movement(velocityPacket);
                float amountValue = amount.getValue();
                Vec3d kept = (amountValue == 0f)
                        ? new Vec3d(0d, packetVelocity.y, 0d)
                        : cappedKeepXZ(packetVelocity, amountValue);
                event.cancel();
                mc.player.setVelocity(kept.x, kept.y, kept.z);
                lastKbTick = mc.player.age;
            }
        }
    }

    @EventSubscribe
    public void onExplosionVelocity(EventPacketReceive event) {
        if (isNull()) return;
        if (event.getPacket() instanceof ExplosionS2CPacket explodePacket) {
            if (mc.player == null) return;

            if (!sources.isEnabled(Concat.of(LT.L8))) {
                return;
            }

            boolean modify = mode.is(Concat.of(LT.L3));
            boolean vulcan = mode.is(Concat.of("Vulcan Decay"));
            if (!modify && !vulcan) return;
            if (amount.getValue() == 1.0f) return;
            Optional<Vec3d> knockbackOpt = explodePacket.playerKnockback();
            if (knockbackOpt.isEmpty()) return;

            float amountValue = amount.getValue();
            event.cancel();
            if (amountValue == 0.0f && modify) {
                justProcessedExplosion = true;
                return;
            }
            Vec3d explosionPacketVelocity = knockbackOpt.get();
            Vec3d modifiedKnockback = vulcan
                    ? cappedKeepXZ(explosionPacketVelocity, amountValue)   // keep Y for VelocityA
                    : cappedKeep(explosionPacketVelocity, amountValue);
            mc.player.addVelocityInternal(modifiedKnockback);
            if (vulcan) lastKbTick = mc.player.age;
            justProcessedExplosion = true;
        }

    }

    @EventSubscribe
    @SuppressWarnings("unused")
    public void onTick(EventTick e) {
        // Vulcan Decay: once the 3-tick velocity-tracking window closes, forget the KB tick
        // so future reductions apply without the Y-preservation gate.
        if (lastKbTick >= 0 && mc.player != null && mc.player.age - lastKbTick > VULCAN_TRACK_TICKS) {
            lastKbTick = -1;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        justProcessedExplosion = false;
        lastKbTick = -1;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        justProcessedExplosion = false;
        lastKbTick = -1;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2bb0,0xfd2a,0x2b0f,0xf89e,0x0f3f,0x5762,0x727d,0x0beb,0x0fa3,0xd46b,0x8816,0xf787,0x67f0,0xf5a5,0x61c5,0x1bcb,0x88c2,0xbf1c,0x7186,0x6e92,0x207c,0x2b37,0x9696,0x2533,0xa68e,0xbc52,0xf137}, new char[]{0x257e,0x2b60,0x2bd4,0x6799,0x0f59,0x96ff,0xf7e2,0x88b1,0xf585,0x2b5c,0xf159,0x61aa,0xbf7f,0x0fc8,0x721f,0x201d,0xbc31,0x0b80,0x71a6,0x1bb9,0xf8fb,0xd408,0x6ef7,0x887f,0x5714,0xa6eb,0xfd4e}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcb69,0x8f65,0x9e92,0xa35d}, new char[]{0xcb24,0xa332,0x9ef6,0x8f00}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5535,0xaa14,0xb843,0x5aca,0x0f87,0x8a02}, new char[]{0x8a41,0x5aab,0xb82d,0xaa77,0x0fe2,0x5559}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x97d7,0x0ab0,0x172b,0xbeb6,0xb98e,0x3479,0xd0bf,0xd500,0x8160,0x5def,0xbd91}, new char[]{0xd0f2,0x3410,0x97b9,0xb9eb,0xd56d,0x5d8a,0x0ade,0xbef5,0x1747,0xbde4,0x8102}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8dcc,0x5afd,0x932d,0x564b,0x4f31,0x9aa7}, new char[]{0x936c,0x5a90,0x9ac8,0x8db9,0x5625,0x4f45}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x98c9,0xe8e5,0x9143,0xd16e,0x5bcb,0x2802,0x58fe}, new char[]{0xe8b6,0x5891,0xd11b,0x5bb9,0x9120,0x2867,0x98ba}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x20f5,0xb660,0xadb9,0xae9c}, new char[]{0xadf1,0xb609,0xaee8,0x2086}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f18,0x25f4,0x9811,0x57e8,0x4303,0x2e9d,0xf28f,0x2262,0xbd5b,0xc538}, new char[]{0x57ad,0x221a,0x1f68,0x2598,0xf2e0,0x9862,0x436a,0x2ef2,0xbd35,0xc54b}).plainString();
    }
}
