package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.handlers.ServerRotations;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.combat.ExplosionUtils;
import com.utilitymod.echo.utils.simulation.PearlSimulation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * OpenPrestige Cart Pearl Catch: predicts where an enemy pearl lands, lays a rail and a TNT minecart next to
 * the landing spot while the pearl flies, and once the enemy lands detonates it - with flint & steel + a
 * charged crossbow shot through the fire (Prefer Xbow), or a bow shot. Aims server-side when Silent Aim is on.
 */
public class CartPearlCatch extends Feature {
    private final FloatSetting scanRange = new FloatSetting(LT.L1, 48f, 16f, 96f, 1f);
    private final FloatSetting minDamage = new FloatSetting(LT.L2, 15f, 0f, 40f, .5f);
    private final FloatSetting maxSelf = new FloatSetting(LT.L3, 6f, 0f, 30f, .5f);
    private final BoolSetting preferXbow = new BoolSetting(LT.L4, true);
    private final IntSetting sdQ = new IntSetting(LT.L5, 2, 0, 20);
    private final BoolSetting sAim = new BoolSetting(LT.L6, true);
    private final FloatSetting fov = new FloatSetting(LT.L7, 180f, 0f, 180f, 1f);
    private final BoolSetting render = new BoolSetting(LT.L8, true);

    private enum Step { RAIL, CART, WAIT, FIRE, SHOOT, BOW_DRAW }

    private EnderPearlEntity pearl;
    private PlayerEntity victim;
    private Vec3d landing;
    private BlockPos railPos, firePos;
    private Step step;
    private int wait, drawTicks, home = -1;

    public CartPearlCatch() {
        super(new FeatureInfo(LT.L9, LT.L10, Category.COMBAT));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }

    private void reset() {
        if (mc.player != null && mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
        if (home >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(home);
        home = -1;
        pearl = null; victim = null; landing = null; railPos = null; firePos = null; step = null; drawTicks = 0;
    }

    private static boolean rail(ItemStack s) {
        return s.isOf(Items.RAIL) || s.isOf(Items.POWERED_RAIL) || s.isOf(Items.DETECTOR_RAIL) || s.isOf(Items.ACTIVATOR_RAIL);
    }

    private int slotOf(java.util.function.Predicate<ItemStack> p) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) if (p.test(inv.getStack(i))) return i;
        return -1;
    }

    private boolean aim(Vec3d point, BlockPos block) {
        return Cpvp.aimed(this, sAim.getValue(), fov.getValue(), point, block);
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        if (isNull() || mc.currentScreen != null) return;
        if (step == null && !acquire()) return;
        if (victim == null || !victim.isAlive() || victim.squaredDistanceTo(mc.player) > 400) { reset(); return; }
        if (wait > 0) { wait--; return; }
        if (home < 0) home = Cpvp.selected();

        switch (step) {
            case RAIL -> {
                if (!mc.world.getBlockState(railPos).isAir()) { step = Step.CART; return; }
                int s = slotOf(CartPearlCatch::rail);
                var hit = BlockUtils.findPlacementHit(railPos, false);
                if (s < 0 || hit == null) { reset(); return; }
                Cpvp.select(s, LT.L11);
                if (!aim(hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
                if (Cpvp.place(railPos)) { step = Step.CART; wait = sdQ.getValue(); }
            }
            case CART -> {
                if (cart() != null) { step = Step.WAIT; return; }
                int s = slotOf(st -> st.isOf(Items.TNT_MINECART));
                if (s < 0) { reset(); return; }
                Cpvp.select(s, LT.L11);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(railPos).add(0, -.4, 0), Direction.UP, railPos, false);
                if (!aim(hit.getPos(), railPos)) return;
                if (BlockUtils.interactWithBlock(hit, true).isAccepted()) { step = Step.WAIT; wait = sdQ.getValue(); }
            }
            case WAIT -> {
                boolean landed = (pearl == null || pearl.isRemoved()) && landing != null && victim.getEntityPos().distanceTo(landing) < 3;
                if (!landed) { if (pearl != null && pearl.isRemoved() && victim.getEntityPos().distanceTo(landing) > 6) reset(); return; }
                boolean xbow = preferXbow.getValue() && slotOf(st -> st.isOf(Items.FLINT_AND_STEEL)) >= 0
                        && slotOf(st -> st.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(st)) >= 0;
                step = xbow ? Step.FIRE : Step.BOW_DRAW;
            }
            case FIRE -> {
                TntMinecartEntity c = cart();
                if (c == null) { reset(); return; }
                firePos = fireSpot(c);
                if (firePos == null) { step = Step.BOW_DRAW; return; }
                int s = slotOf(st -> st.isOf(Items.FLINT_AND_STEEL));
                Cpvp.select(s, LT.L11);
                BlockPos below = firePos.down();
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below).add(0, .5, 0), Direction.UP, below, false);
                if (!aim(hit.getPos(), below)) return;
                if (BlockUtils.interactWithBlock(hit, true).isAccepted()) { step = Step.SHOOT; wait = sdQ.getValue(); }
            }
            case SHOOT -> {
                TntMinecartEntity c = cart();
                int s = slotOf(st -> st.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(st));
                if (c == null || s < 0) { reset(); return; }
                Cpvp.select(s, LT.L11);
                Vec3d point = c.getBoundingBox().getCenter();
                if (!fireAt(point)) return;
                reset();
            }
            case BOW_DRAW -> {
                TntMinecartEntity c = cart();
                int s = slotOf(st -> st.isOf(Items.BOW));
                if (c == null || s < 0) { reset(); return; }
                Cpvp.select(s, LT.L11);
                Vec3d point = c.getBoundingBox().getCenter().add(0, Math.min(1.2, mc.player.getEyePos().distanceTo(c.getEntityPos()) * .02), 0);
                if (sAim.getValue()) ServerRotations.lookAt(this, point, .6f, 10);
                if (!mc.player.isUsingItem()) { mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND); drawTicks = 0; return; }
                if (++drawTicks < 20) return;
                if (sAim.getValue() && !Cpvp.sentNear(point, 2f)) return;
                releaseWithSentLook();
                reset();
            }
        }
    }

    /** Aim at the cart and use the held (charged) crossbow with the look the server already has. */
    private boolean fireAt(Vec3d point) {
        if (!sAim.getValue()) {
            mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
            return true;
        }
        if (ServerRotations.yawOffset(point) > fov.getValue()) return false;
        ServerRotations.lookAt(this, point, .6f, 10);
        if (!Cpvp.sentNear(point, 2f)) return false;
        Cpvp.useItemWithSentLook();
        return true;
    }

    private void releaseWithSentLook() {
        var p = mc.player;
        float yaw = p.getYaw(), pitch = p.getPitch();
        if (sAim.getValue()) {
            Vec3d look = ServerRotations.sentLook();
            p.setYaw((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
            p.setPitch((float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, look.y)))));
        }
        try {
            mc.interactionManager.stopUsingItem(p);
        } finally {
            p.setYaw(yaw);
            p.setPitch(pitch);
        }
    }

    private TntMinecartEntity cart() {
        if (railPos == null) return null;
        for (Entity en : mc.world.getOtherEntities(null, new Box(railPos).expand(.6)))
            if (en instanceof TntMinecartEntity t && t.isAlive()) return t;
        return null;
    }

    /** Air block next to the cart, toward us, with a solid top below it: where fire goes. */
    private BlockPos fireSpot(TntMinecartEntity c) {
        Vec3d dir = mc.player.getEyePos().subtract(c.getEntityPos());
        Direction toward = Direction.getFacing(dir.x, 0, dir.z);
        for (Direction d : new Direction[]{toward, toward.rotateYClockwise(), toward.rotateYCounterclockwise()}) {
            BlockPos p = c.getBlockPos().offset(d);
            if (mc.world.getBlockState(p).isAir() && mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world, p.down(), Direction.UP)
                    && Cpvp.inReach(p.down())) return p;
        }
        return null;
    }

    private boolean acquire() {
        if (slotOf(CartPearlCatch::rail) < 0 || slotOf(st -> st.isOf(Items.TNT_MINECART)) < 0) return false;
        double r2 = scanRange.getValue() * scanRange.getValue();
        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof EnderPearlEntity p) || p.squaredDistanceTo(mc.player) > r2) continue;
            if (!(p.getOwner() instanceof PlayerEntity owner) || owner == mc.player
                    || !com.utilitymod.echo.utils.combat.TargetUtils.isTargetAllowed(owner)) continue;
            PearlSimulation sim = new PearlSimulation(p.getEntityPos(), p.getVelocity(), p);
            if (sim.simulateUntilHit(200) == null) continue;
            Vec3d land = sim.getPosition();
            BlockPos spot = railSpot(land);
            if (spot == null) continue;
            pearl = p; victim = owner; landing = land; railPos = spot; step = Step.RAIL; wait = 0;
            return true;
        }
        return false;
    }

    private BlockPos railSpot(Vec3d land) {
        BlockPos base = BlockPos.ofFloored(land);
        BlockPos best = null;
        float bestScore = -1;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 1; dy++) {
            BlockPos p = base.add(dx, dy, dz);
            if (p.equals(base)) continue;
            if (!mc.world.getBlockState(p).isAir()) continue;
            if (!mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world, p.down(), Direction.UP)) continue;
            if (!Cpvp.inReach(p)) continue;
            Vec3d c = Vec3d.ofBottomCenter(p);
            float dmg = Cpvp.damageAt(land, p) * 0.8f;   // TNT cart (power 4) vs anchor (5)
            float self = ExplosionUtils.getExplosionDamageTo(mc.player, c, 4f);
            if (dmg < minDamage.getValue() || self > maxSelf.getValue() || Cpvp.lethal(self)) continue;
            float score = dmg - self;
            if (score > bestScore) { bestScore = score; best = p.toImmutable(); }
        }
        return best;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onRender(EventRender3D e) {
        if (!render.getValue() || isNull()) return;
        var out = e.getDraw3D().getMinecraftTarget();
        if (landing != null) out.box(landing.x - .3, landing.y, landing.z - .3, .6, 1.8, .6, 0xFF50AAFF);
        if (railPos != null) out.box(railPos.getX(), railPos.getY(), railPos.getZ(), 1, .7, 1, 0xFFFF5040);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7f25,0xe72c,0xc9e8,0x8fee,0x1edf,0x3a40,0x44a1,0xecd9,0x47f4,0x3b4a}, new char[]{0x1e8c,0xecba,0x7f44,0x3a2e,0x4481,0xe77e,0x3b2b,0xc986,0x8f89,0x4791}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa989,0x0a59,0x08c8,0x3502,0x7b6d,0xf04d,0xeda2,0x9086,0x73ff,0x05cc}, new char[]{0x90cb,0xf024,0x08a6,0xa9a9,0x7b29,0x739e,0x356f,0x0a38,0x05ab,0xedc7}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe6db,0xbc5a,0x606c,0x6fed,0x3d95,0x4e11,0x14d0,0x7df4,0xe6b6,0x981b,0x7361,0xfe5c,0xc0ff,0x140a,0x3268}, new char[]{0xe6fb,0x3df4,0x7d8c,0x983b,0x323b,0x6f88,0x6000,0x7307,0x14f0,0xbc1e,0xe6ba,0xc092,0xfe3d,0x146d,0x4e74}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0515,0x533a,0x5477,0x5c8b,0xd17e,0x6826,0x36aa,0x9a66,0x24ef,0xc5b0,0xfd04}, new char[]{0x536a,0xc5c2,0x5412,0xfd62,0x248a,0x9a14,0xd15e,0x687e,0x36c8,0x5ce4,0x0562}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x171f,0xe541,0x949a,0xb653,0xf609,0x1396,0x2e34,0x3919,0xf8a1,0xeae6,0x29f1,0xc580}, new char[]{0x2e67,0xe536,0x1776,0x94ee,0xc5e3,0x2999,0xf881,0x395d,0xf66c,0x13fa,0xea87,0xb62a}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x97c6,0x111b,0x9ba4,0xc54c,0x7fb7,0x6155,0x7bff,0xada3,0x1bcd,0x0c81}, new char[]{0xc51f,0x7fde,0x0ced,0x7b9a,0x1ba3,0x116f,0x6175,0x9be5,0x97af,0xadce}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbf42,0x2f15,0x4414,0x94a3,0x12d1,0xda97,0xf04f,0x755d,0x5ff7,0x30ea,0xeb21,0xdea2,0xa9db,0x2afd}, new char[]{0xdac4,0x94ca,0x2f79,0x5f92,0x2a93,0xf03b,0xbf62,0xa99a,0xeb48,0x12bc,0x30ca,0xdee4,0x445b,0x750b}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaaf8,0x33cc,0x88fe,0xdab9,0x3ad3,0x3a94}, new char[]{0x3ac6,0x3ab6,0x33a2,0xdadd,0x889b,0xaa8a}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf9db,0x2170,0x7626,0xf98d,0xe927,0x157a,0xd6cd,0xe19e,0x3318,0x8d05,0xb48a,0xf7e4,0xb440,0x3648,0xab97,0x4e0f}, new char[]{0x2133,0x8d64,0x336a,0xd6b9,0xabb7,0xf98b,0x362d,0xe946,0x7654,0xb4e6,0xb460,0xe1dd,0xf9ec,0xf790,0x1519,0x4e67}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f41,0x3752,0xd8ff,0x5e79,0x7b6c,0xd883,0x70dd,0xf896,0xf1de,0x53db,0x670a,0x5b0d,0x5397,0x77b7,0xa3c4,0x9289,0xb2b9,0x9378,0x81f1,0xd75c,0x7641,0x836e,0x6dc1,0xca09,0x5bde,0x3e4c,0x5586,0x9b5e,0xea2d,0x1898,0xeee1,0xe104,0x76ac,0xe72a,0x700f,0x6c75,0x64d4,0x328b,0x5c72,0x0839,0x8db3,0xd999,0xc338,0x6b74,0x0bf4,0xc586,0x8eb8,0xfa6a,0x52db,0xdeed,0x13ab,0xd179,0x1416,0xe61c,0xee10,0x60c0,0x81ef,0x9e40,0x1b96,0xd758,0xd2bf,0x20d8,0xdf17}, new char[]{0x5f02,0x8190,0xe75e,0x3731,0x8ed0,0x3e29,0x76df,0xd778,0xb2dc,0x5e17,0x7624,0xd8ee,0x8317,0xc318,0x9b2e,0x5c17,0x64b5,0x77c5,0x6b18,0x18eb,0xd159,0x930f,0x55ef,0xea59,0x1bfe,0xd77c,0x8dd2,0xf1fe,0x5b8a,0xee5e,0x6c21,0x672a,0x70b0,0x13c2,0xf8f8,0x53f2,0x5b6e,0x0858,0x707d,0x6db5,0x0bd4,0x32ea,0x52af,0xe63c,0xa3b0,0x53b3,0xdf72,0x9e29,0x20aa,0xd29f,0xd9f5,0x1477,0xc5e8,0xe160,0x60a9,0xd891,0xfa0d,0xeec1,0xca7a,0x819f,0x7b03,0xde99,0x92a7}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2386,0x49f7,0xbe42,0xf4cc}, new char[]{0x23d5,0xf4bb,0x4996,0xbe32}).plainString();
    }
}
