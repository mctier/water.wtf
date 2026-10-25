package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.render3.api.FramebufferTarget;
import com.utilitymod.echo.screens.frame.glass.GlassUIConstants;
import com.utilitymod.echo.utils.DrawUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.utilitymod.echo.utils.EncryptedString;

import java.awt.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Backoff extends Feature {
    private static final CharSequence RANGE_SEPARATOR = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd157}, new char[]{0xd17a});
    private static final CharSequence DELAY_SUFFIX = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8b8,0xd117}, new char[]{0xd17a,0xa8cb});

    public Backoff() {
        super(new FeatureInfo(
                new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b3d,0x1c99,0x82ce,0x37ca,0xbc02,0xa8aa,0xb17d,0x227b,0xd138}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}),
                new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a1e,0xa1b2,0x58fe,0x6559,0x261f,0x8af4,0x37c9,0x2a8b,0x4780,0x82d4,0xb985,0x1816,0x9be8,0xf89d,0x723f,0xac53,0x0b7c,0x1c9d,0x508e,0x76b5,0x9636,0x5f6b,0xeb95,0x04a2,0xa8a7,0xa42d,0x93b0,0x3356,0xfca7,0x6d35,0xb17c,0xd13b,0xbc06,0xf051,0xe72a,0x9e06,0xd932,0x3fef,0xd2f2,0xb513,0x866a,0x3b7f,0x0cdd,0x0024,0xc149,0x7ed9,0xcdf7,0xc5da,0x11b0,0x2274}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b}),
                Category.COMBAT
        ));
        delayMs.describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a90,0xa8a4,0x50cd,0x2387,0xb160,0x158d,0xb98c,0x1cd2,0xfce2,0x37d2,0x8625,0xe73f,0xf41c,0x66ef,0x9bf3,0xbd5d,0xcdee,0xc59b,0x0cc6,0x8aa0,0x11ff,0x7269,0x4fa1,0x226f,0xac57,0x4305,0xa9c5,0xf88c,0x7ec5,0xd2b7,0x3354,0xef19,0xe0ab,0xdad5,0xbc49,0x0807,0xd132,0x04bc,0xebc1,0xa1e1,0xf044,0xc978,0x5f61,0xb508,0x58e6,0x82ca,0x2e23,0x76b8,0x4a13,0x6d78,0x2603,0x7a58,0xa421,0x0b32,0xc15f,0x6552,0x8eac,0x5426,0x9e49,0x003e,0x93a1,0x3ff4,0x1801,0x3b70,0x9637,0x34ee,0x478e,0xd937}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406,0x2e57,0xa9a4,0x23f5,0xbd3a,0x348b,0x8ed8,0x0829}));
        entityRangeBlocks.describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdac5,0xa429,0xd979,0x9bbb,0x2260,0x6d7b,0x9e47,0x2a9a,0x4a4a,0xb547,0x58f9,0x6557,0x1c96,0x2390,0x7a4f,0x2616,0xeb95,0xa8aa,0xc5cf,0xe0be,0x866d,0xef4d,0xbc00,0x7228,0xb17a,0x0cc0,0x002f,0x2e25,0x3b64,0x66fa,0x93ad,0xb9c2,0xf888,0x962a,0xd137,0x3357,0x34a5,0x5467,0x04bd,0x37d3,0xc977,0xf040,0xf416,0xd2a1,0x8ae5,0xcdfd,0xe73d,0x1589,0xbd4e,0xc159,0x7e8b,0x828d,0xa1ae,0x5f67,0x50c5,0x4784,0x11e3,0x3fed,0x4fa7,0x180e,0xac03,0x4340,0x0b31,0x76b7,0xfce9,0xa9c3}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406,0x2e57,0xa9a4,0x23f5,0xbd3a,0x348b}));
        fillColor.describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3fe4,0x11b0,0xe72e,0xd13c,0x93a3,0x58e4,0xa1a3,0x0b33,0x7e8b,0x1c9d,0x04a5,0xbc05,0x6d70,0x4a02,0xa8a2,0x3b79,0xa42d,0x8aa0,0xb51f,0x9631,0xf888,0xb987,0x76f4,0x0c9a,0xac4d,0x5f28,0x379e,0xf051,0x82c1,0x2a93,0x50cb,0x2274,0x7226,0xcded,0x6559,0xc148,0x2650,0x4786,0xb16c,0xd2bd,0xfcf5,0x1805,0xd93d,0xc5de,0x3357,0xeb87,0x9e47}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4}));
        outlineColor.describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a18,0xa1ae,0x58fe,0x6544,0x2619,0x8af9,0x37d7,0x2adf,0x479f,0x828d,0xb98e,0x1857,0x9bb5,0xf89d,0x723d,0xac57,0x0b39,0x1c91,0x50cb,0x76b2,0x962c,0x5f7c,0xeb8e,0x04b4,0xa8be,0xa428,0x93b0,0x3352,0xfca7,0x6d74,0xb161,0xd135,0xbc05,0xf04a,0xe728,0x9e42,0xd931,0x3ff8,0xd2bb,0xb547,0x866a,0x3b78,0x0cd6,0x0032,0xc153,0x7ecc,0xcdbe,0xc59b,0x11fc,0x226c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b}));
    }

    private final RangeSetting delayMs = new RangeSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0xd13e,0x37c7,0xa8ae,0xbc08}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}),150f, 250f, 0f, 1000f, 1f, new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226b,0xa8a6,0xd15a}, new char[]{0xd17a,0xa8cb,0x2218}));
    private final IntSetting entityRangeBlocks = new IntSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2276,0xd128,0x37db,0xa8aa,0xbc0e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), 6, 0, 16, new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b2f,0xd15a,0x37dd,0xb164,0xbc06,0x2274,0xa8a9}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}));
    private final ColorSetting fillColor = new ColorSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a2,0x9631,0x82c1,0x379e,0x0b33,0x1c9d,0x2274,0xb14c,0xd13c,0xbc05}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}),
            GlassUIConstants.ACCENT_FOR_RENDERING.getRGB(),
            true
    );
    private final ColorSetting outlineColor = new ColorSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226c,0x962c,0x11fc,0xeb8e,0x828d,0x0b39,0xa8be,0xbc05,0x1cb1,0xd135,0x37d7,0x6544,0xb161}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}),
            Color.WHITE.getRGB(),
            true
    );

    @Override
    public CharSequence concat() {
        return EncryptedString.of(
            EncryptedString.ofFloat(delayMs.getMinValue()),
            RANGE_SEPARATOR,
            EncryptedString.ofFloat(delayMs.getMaxValue()),
            DELAY_SUFFIX
        );
    }

    private static final class QueuePacket {
        final Packet<?> packet;
        final long timeStamp;

        QueuePacket(Packet<?> packet, long timeStamp) {
            this.packet = packet;
            this.timeStamp = timeStamp;
        }
    }

    private final Queue<QueuePacket> delayedPackets = new ConcurrentLinkedDeque<>();
    private Entity target;
    private Vec3d trackedTargetPosition;
    private TrackedPosition trackedTargetPositionCodec;
    private float activeDelayMs;

    @Override
    public void onEnable() {
        delayedPackets.clear();
        clearTrackedTargetPosition();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        flushPackets(false);
        clearTrackedTargetPosition();
        super.onDisable();
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onAttackEntity(EventOnAttackEntity.Pre event) {
        if (isNull()) return;
        target = event.getTarget();
        if (com.utilitymod.echo.utils.combat.TargetUtils.isFriend(target)) {
            target = null;
            return;
        }
        clearTrackedTargetPosition();
    }

    private boolean hasValidTarget() {
        if (target == null) return false;
        if (!target.isAlive()) return false;

        double maxDistance = (entityRangeBlocks.getValue());
        return mc.player.squaredDistanceTo(target) <= (maxDistance * maxDistance);
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onPacketRecieve(EventPacketReceive event) {
        if (isNull()) return;
        if (mc.isConnectedToLocalServer()) return;

        Packet<?>  packet = event.getPacket();
        if (packet == null) return;

        if (!hasValidTarget()) {
            flushPackets(false);
            clearTrackedTargetPosition();
            return;
        }

        if (packet instanceof ChatMessageC2SPacket
        ||  packet instanceof GameMessageS2CPacket
        ||  packet instanceof CommandExecutionC2SPacket) {
            return;
        }

        //on tp or dc we need to flush
        if (packet instanceof PlayerPositionLookS2CPacket || packet instanceof DisconnectS2CPacket) {
            flushPackets(false);
            clearTrackedTargetPosition();
            return;
        }

        // Flush on own death
        if (packet instanceof HealthUpdateS2CPacket) {
            if (((HealthUpdateS2CPacket) packet).getHealth() <= 0.0) {
                flushPackets(false);
                clearTrackedTargetPosition();
                return;
            }
        }


        if (packet instanceof PlaySoundS2CPacket) {
            if (((PlaySoundS2CPacket) packet).getSound().value() == SoundEvents.ENTITY_PLAYER_HURT) {
                return;
            }
        }

        if (activeDelayMs <= 0.0f) {
            activeDelayMs = delayMs.getRandom();
        }

        float currentDelayMs = activeDelayMs;
        if (currentDelayMs <= 0) {
            flushPackets(false);
            return;
        }

        flushPackets(true);

        trackTargetPosition(packet);
        delayedPackets.add(new QueuePacket(packet, System.currentTimeMillis()));
        event.cancel();

        QueuePacket oldest = delayedPackets.peek();
        if (oldest != null && System.currentTimeMillis() - oldest.timeStamp > currentDelayMs) {
            flushPackets(false);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void flushPackets(boolean onlyExpiredPackets) {
        ClientPlayNetworkHandler connection = mc.getNetworkHandler();
        if (connection == null) {
            delayedPackets.clear();
            clearTrackedTargetPosition();
            return;
        }

        long now = System.currentTimeMillis();

        while (true) {
            QueuePacket queued = delayedPackets.peek();
            if (queued == null) break;

            if (onlyExpiredPackets) {
                long age = now - queued.timeStamp;
                if (age < activeDelayMs) {
                    break;
                }
            }

            delayedPackets.poll();
            try {
                Packet packet = queued.packet;
                packet.apply((PacketListener) connection);
            } catch (Throwable ignored) {
            }
        }

        if (!onlyExpiredPackets) {
            delayedPackets.clear();
            clearTrackedTargetPosition();
            return;
        }

        if (delayedPackets.isEmpty()) {
            clearTrackedTargetPosition();
        }
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onRender3D(EventRender3D event) {
        if (isNull()) return;
        if (!hasValidTarget()) {
            clearTrackedTargetPosition();
            return;
        }

        boolean renderFill = fillColor.getAlpha() > 0;
        boolean renderOutline = outlineColor.getAlpha() > 0;
        if (!renderFill && !renderOutline) return;

        Box actualBox = getTrackedTargetBox();
        if (actualBox == null) return;

        FramebufferTarget draw = event.getDraw3D().getMinecraftTarget();
        if (renderFill) {
            draw.box(
                    actualBox.minX,
                    actualBox.minY,
                    actualBox.minZ,
                    actualBox.getLengthX(),
                    actualBox.getLengthY(),
                    actualBox.getLengthZ(),
                    fillColor.getARGB()
            );
        }

        if (renderOutline) {
            DrawUtils.drawBoxOutline(draw, actualBox, outlineColor.getARGB());
        }
    }

    private void trackTargetPosition(Packet<?> packet) {
        if (!hasValidTarget()) return;
        if (mc.world == null) return;

        if (packet instanceof EntityS2CPacket movePacket) {
            if (!isTargetMovePacket(movePacket)) return;
            trackMovePacket(movePacket);
            return;
        }

        if (packet instanceof EntityPositionSyncS2CPacket syncPacket) {
            if (syncPacket.id() != target.getId()) return;
            setTrackedTargetPosition(syncPacket.values().position());
            return;
        }

        if (packet instanceof EntityPositionS2CPacket teleportPacket) {
            if (teleportPacket.entityId() != target.getId()) return;
            trackTeleportPacket(teleportPacket);
        }
    }

    private void trackMovePacket(EntityS2CPacket packet) {
        ensureTrackedTargetPosition();
        if (trackedTargetPositionCodec == null) return;
        if (!packet.isPositionChanged()) return;

        Vec3d actualPosition = trackedTargetPositionCodec.withDelta(
                packet.getDeltaX(),
                packet.getDeltaY(),
                packet.getDeltaZ()
        );

        trackedTargetPosition = actualPosition;
        trackedTargetPositionCodec.setPos(actualPosition);
    }

    private void trackTeleportPacket(EntityPositionS2CPacket packet) {
        ensureTrackedTargetPosition();
        if (trackedTargetPosition == null) return;

        Vec3d change = packet.change().position();
        double x = packet.relatives().contains(PositionFlag.X) ? trackedTargetPosition.x + change.x : change.x;
        double y = packet.relatives().contains(PositionFlag.Y) ? trackedTargetPosition.y + change.y : change.y;
        double z = packet.relatives().contains(PositionFlag.Z) ? trackedTargetPosition.z + change.z : change.z;

        setTrackedTargetPosition(new Vec3d(x, y, z));
    }

    private void ensureTrackedTargetPosition() {
        if (trackedTargetPosition != null && trackedTargetPositionCodec != null) return;
        if (target == null) return;

        setTrackedTargetPosition(target.getTrackedPosition().getPos());
    }

    private void setTrackedTargetPosition(Vec3d position) {
        trackedTargetPosition = position;
        trackedTargetPositionCodec = new TrackedPosition();
        trackedTargetPositionCodec.setPos(position);
    }

    private Box getTrackedTargetBox() {
        if (trackedTargetPosition == null) return null;

        Vec3d boxOffset = trackedTargetPosition.subtract(target.getEntityPos());
        return target.getBoundingBox().offset(boxOffset);
    }

    private boolean isTargetMovePacket(EntityS2CPacket packet) {
        Entity movedEntity = packet.getEntity(mc.world);
        if (movedEntity == null) return false;

        return movedEntity.getId() == target.getId();
    }

    private void clearTrackedTargetPosition() {
        trackedTargetPosition = null;
        trackedTargetPositionCodec = null;
        activeDelayMs = 0.0f;
    }
}
