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
    private final FloatSetting scanRange = new FloatSetting("Scan Range", 48f, 16f, 96f, 1f);
    private final FloatSetting minDamage = new FloatSetting("Min Damage", 15f, 0f, 40f, .5f);
    private final FloatSetting maxSelf = new FloatSetting("Max Self Damage", 6f, 0f, 30f, .5f);
    private final BoolSetting preferXbow = new BoolSetting("Prefer Xbow", true);
    private final IntSetting switchDelay = new IntSetting("Switch Delay", 2, 0, 20);
    private final BoolSetting silentAim = new BoolSetting("Silent Aim", true);
    private final FloatSetting fov = new FloatSetting("Silent Aim FOV", 180f, 0f, 180f, 1f);
    private final BoolSetting render = new BoolSetting("Render", true);

    private enum Step { RAIL, CART, WAIT, FIRE, SHOOT, BOW_DRAW }

    private EnderPearlEntity pearl;
    private PlayerEntity victim;
    private Vec3d landing;
    private BlockPos railPos, firePos;
    private Step step;
    private int wait, drawTicks, home = -1;

    public CartPearlCatch() {
        super(new FeatureInfo("Cart Pearl Catch", "Catches enemy pearls with a TNT minecart at their landing spot.", Category.COMBAT));
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
        return Cpvp.aimed(this, silentAim.getValue(), fov.getValue(), point, block);
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
                Cpvp.select(s, "Swap");
                if (!aim(hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
                if (Cpvp.place(railPos)) { step = Step.CART; wait = switchDelay.getValue(); }
            }
            case CART -> {
                if (cart() != null) { step = Step.WAIT; return; }
                int s = slotOf(st -> st.isOf(Items.TNT_MINECART));
                if (s < 0) { reset(); return; }
                Cpvp.select(s, "Swap");
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(railPos).add(0, -.4, 0), Direction.UP, railPos, false);
                if (!aim(hit.getPos(), railPos)) return;
                if (BlockUtils.interactWithBlock(hit, true).isAccepted()) { step = Step.WAIT; wait = switchDelay.getValue(); }
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
                Cpvp.select(s, "Swap");
                BlockPos below = firePos.down();
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below).add(0, .5, 0), Direction.UP, below, false);
                if (!aim(hit.getPos(), below)) return;
                if (BlockUtils.interactWithBlock(hit, true).isAccepted()) { step = Step.SHOOT; wait = switchDelay.getValue(); }
            }
            case SHOOT -> {
                TntMinecartEntity c = cart();
                int s = slotOf(st -> st.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(st));
                if (c == null || s < 0) { reset(); return; }
                Cpvp.select(s, "Swap");
                Vec3d point = c.getBoundingBox().getCenter();
                if (!fireAt(point)) return;
                reset();
            }
            case BOW_DRAW -> {
                TntMinecartEntity c = cart();
                int s = slotOf(st -> st.isOf(Items.BOW));
                if (c == null || s < 0) { reset(); return; }
                Cpvp.select(s, "Swap");
                Vec3d point = c.getBoundingBox().getCenter().add(0, Math.min(1.2, mc.player.getEyePos().distanceTo(c.getEntityPos()) * .02), 0);
                if (silentAim.getValue()) ServerRotations.lookAt(this, point, .6f, 10);
                if (!mc.player.isUsingItem()) { mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND); drawTicks = 0; return; }
                if (++drawTicks < 20) return;
                if (silentAim.getValue() && !Cpvp.sentNear(point, 2f)) return;
                releaseWithSentLook();
                reset();
            }
        }
    }

    /** Aim at the cart and use the held (charged) crossbow with the look the server already has. */
    private boolean fireAt(Vec3d point) {
        if (!silentAim.getValue()) {
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
        if (silentAim.getValue()) {
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
}
