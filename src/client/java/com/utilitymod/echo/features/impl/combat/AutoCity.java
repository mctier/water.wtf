package com.utilitymod.echo.features.impl.combat;

import java.util.List;
import java.util.function.Predicate;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.EncryptedString;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Crystal family: Auto City â€” breaks the block in front of the target once a totem pop shows,
 * opening the city spot so follow-up crystals pour through. Buffered vanilla mining path only:
 * {@code updateBlockBreakingProgress} at tick start (MinerFlow / AutoBlockMiner law), no packet
 * sent off the tick flush, no lookaway packet, no exposure burst.
 */
public class AutoCity extends Feature {

    // -- Label carriers (generated codec-faithful â€” round-trip verified in harness) --
    private static final EncryptedString K_NAME = new EncryptedString(new char[]{0x0b35, 0x1c8b, 0x82d9, 0x379e, 0xbc06, 0xa8be, 0xb14c, 0x226c, 0xd13b}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2});
    private static final EncryptedString K_DESCRIPTION = new EncryptedString(new char[]{0xbc08, 0x7edf, 0x3313, 0x723b, 0x37d5, 0x2613, 0x66ae, 0x5f66, 0xd2bd, 0x2a8d, 0xf899, 0x478a, 0x4a4a, 0x58ad, 0x50c8, 0xc148, 0xac4d, 0xa423, 0x7a43, 0x3ff3, 0xa8b9, 0xe77a, 0x9bef, 0x76b7, 0xf04e, 0x3b36, 0x158d, 0x04b7, 0x0b7c, 0xfce8, 0xd979, 0x9e06, 0x0c94, 0xc5d2, 0x11b0, 0x227d, 0xeb83, 0x8aef, 0xcdf9, 0x8671, 0x1803, 0xa1a4, 0xb506, 0x6d70, 0xd138, 0x1c9a, 0x9626, 0x0025, 0xb17c, 0x655a, 0xb996, 0x4fb1, 0x82d9, 0xe0af, 0xef54, 0x93ac, 0xf40d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff, 0xa44c, 0x3f9d, 0xb9e2, 0x3333, 0x8a80, 0x04d1, 0x9e26, 0x1877, 0x93c4, 0x6d15, 0xe75a, 0x7eab, 0xf8f8, 0x7249, 0xcd9e, 0x47ef, 0xc13c, 0x588d, 0xd2d2, 0xac23, 0x2670, 0xa1c1, 0x3b16, 0xb567, 0x0cb4, 0x8605, 0x004a, 0x9b9b, 0x15e8, 0xef39, 0x668e, 0xe0df, 0x7a2c, 0xf47d, 0x4fc2});
    private static final EncryptedString K_RANGE = new EncryptedString(new char[]{0x2276, 0xd128, 0x37db, 0xa8aa, 0xbc0e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});
    private static final EncryptedString K_THROUGH = new EncryptedString(new char[]{0x226a, 0x9622, 0x11fc, 0xeb8d, 0x828d, 0x0b34, 0xa8a3, 0xbc06, 0x1ca5, 0xd12e, 0x37cb, 0x6545, 0xb168}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536});
    private static final EncryptedString K_ON_TOTEM = new EncryptedString(new char[]{0x2238, 0xd135, 0xb17b, 0x0b39, 0xbc3d, 0x37d1, 0xa8a5, 0x82c0}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad});
    private static final EncryptedString K_WINDOW = new EncryptedString(new char[]{0x2274, 0xa8a4, 0xd132, 0xbc0d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
    private static final EncryptedString K_DELAY = new EncryptedString(new char[]{0x11e9, 0xa8b9, 0xbc08, 0x227d, 0x1c9e, 0x82c8, 0x0b18, 0xd138, 0xb12f, 0x9622, 0x37d5}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190});

    private final FloatSetting range = new FloatSetting(K_RANGE, 4.5f, 0.0f, 6.0f, 0.5f);
    private final BoolSetting throughWalls = new BoolSetting(K_THROUGH, false);
    private final BoolSetting onTotem = new BoolSetting(K_ON_TOTEM, true);
    private final IntSetting breakWindow = new IntSetting(K_WINDOW, 10, 1, 40);
    private final IntSetting restDelay = new IntSetting(K_DELAY, 10, 0, 80);

    private static final int MINING_TICK_CAP = 60;

    private PlayerEntity target;
    private boolean totemInHands;
    private boolean armed;
    private int armTicksLeft;
    private int cooldownTicks;
    private BlockPos pendingBlock;
    private int miningTicks;

    public AutoCity() {
        super(new FeatureInfo(K_NAME, K_DESCRIPTION, Category.COMBAT));
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (!isEnabled() || isNull()) return;
        if (cooldownTicks > 0) { cooldownTicks--; return; }

        PlayerEntity want = pickTarget();
        if (want != target) {
            target = want;
            totemInHands = false;
            armed = false;
            armTicksLeft = 0;
            pendingBlock = null;
            miningTicks = 0;
        }
        if (target == null) return;

        boolean holdingNow = carryingTotem(target);
        if (onTotem.getValue()) {
            if (totemInHands && !holdingNow) arm(breakWindow.getValue());
            else if (armed && --armTicksLeft <= 0) { armed = false; pendingBlock = null; }
        } else {
            armed = true;
        }
        totemInHands = holdingNow;
        if (!armed) return;

        if (pendingBlock != null && (mc.world.getBlockState(pendingBlock).isAir() || ++miningTicks > MINING_TICK_CAP)) {
            pendingBlock = null;
            armed = false;
            cooldownTicks = restDelay.getValue();
            return;
        }
        if (pendingBlock == null) {
            BlockPos spot = citySpot(target);
            if (spot == null || !arrive(spot)) return;
            pendingBlock = spot;
            miningTicks = 0;
        }

        if (mc.interactionManager.updateBlockBreakingProgress(pendingBlock, breakFaceProperty())) mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Mining face aimed back at us; server reach reads the face, so keep it become fact. */
    private Direction breakFaceProperty() {
        Vec3d eye = mc.player.getEyePos();
        Vec3d c = Vec3d.ofCenter(pendingBlock);
        return Direction.getFacing(eye.x - c.x, eye.y - c.y, eye.z - c.z);
    }

    private void arm(int holdTicks) {
        armed = true;
        armTicksLeft = holdTicks;
    }

    private PlayerEntity pickTarget() {
        Box area = mc.player.getBoundingBox().expand(range.getValue());
        Predicate<PlayerEntity> filter = e -> e != mc.player
                && e.isAlive()
                && !e.isSpectator()
                && !e.isCreative();
        List<PlayerEntity> pool = mc.world.getEntitiesByClass(PlayerEntity.class, area, filter);
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        double rangeSq = range.getValue() * (double) range.getValue();
        for (PlayerEntity e : pool) {
            if (!e.isAlive() || e.isSpectator() || e.isCreative()) continue;
            double dist = mc.player.squaredDistanceTo(e);
            if (dist > rangeSq) continue;
            if (!throughWalls.getValue() && !mc.player.canSee(e)) continue;
            if (dist < bestDist) { bestDist = dist; best = e; }
        }
        return best;
    }

    private boolean carryingTotem(PlayerEntity e) {
        return e.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || e.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    private BlockPos citySpot(PlayerEntity e) {
        int tx = e.getBlockX(), ty = e.getBlockY(), tz = e.getBlockZ();
        double rad = Math.toRadians(e.getYaw());
        int fx = -(int) Math.round(Math.sin(rad));
        int fz = (int) Math.round(Math.cos(rad));
        BlockPos feet = BlockPos.ofFloored(tx + fx, ty, tz + fz);
        BlockPos head = feet.up();
        if (isCityBlock(feet)) return feet;
        if (isCityBlock(head)) return head;
        return null;
    }

    private boolean isCityBlock(BlockPos p) {
        var st = mc.world.getBlockState(p);
        return !st.isAir() && st.getHardness(mc.world, p) >= 0;
    }

    /** Reach check with a visible-face side (MinerFlow.reachable law); walls block unless opted in. */
    private boolean arrive(BlockPos p) {
        Vec3d eye = mc.player.getEyePos();
        float reachSq = range.getValue() * range.getValue();
        if (eye.squaredDistanceTo(Vec3d.ofCenter(p)) > reachSq) return false;
        if (!throughWalls.getValue()) {
            for (Direction d : Direction.values()) {
                if (!mc.world.getBlockState(p.offset(d)).isReplaceable() && !mc.world.getBlockState(p.offset(d)).isAir()) continue;
                Vec3d face = Vec3d.ofCenter(p).add(d.getOffsetX() * 0.5, d.getOffsetY() * 0.5, d.getOffsetZ() * 0.5);
                BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, face, RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE, mc.player));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(p)) return true;
            }
            return false;
        }
        return true;
    }
}
