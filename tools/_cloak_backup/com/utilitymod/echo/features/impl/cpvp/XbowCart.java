package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.handlers.ServerRotations;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * OpenPrestige Xbow Cart (one-shot): rail + TNT cart at your crosshair, fire on a block next to the cart,
 * then a charged crossbow shot through the fire into the cart; turns itself off when done. Each step is
 * separated by Switch Delay ticks; Silent Aim does the aiming server-side.
 */
public class XbowCart extends Feature {
    private final IntSetting switchDelay = new IntSetting("Switch Delay", 2, 0, 20);
    private final BoolSetting silentAim = new BoolSetting("Silent Aim", true);
    private final FloatSetting fov = new FloatSetting("Silent Aim FOV", 180f, 0f, 180f, 1f);

    private enum Step { RAIL, CART, FIRE, SHOOT }

    private Step step;
    private BlockPos railPos, firePos;
    private int wait, home = -1, timeout;

    public XbowCart() {
        super(new FeatureInfo("Xbow Cart", "Rail, TNT cart, fire and a crossbow shot at your crosshair in one go.", Category.COMBAT));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        step = null;
        railPos = null;
        firePos = null;
        timeout = 0;
        if (mc.player == null) return;
        home = Cpvp.selected();
        var hit = Cpvp.crosshairBlock();
        if (hit == null) { setEnabled(false); return; }
        BlockPos p = mc.world.getBlockState(hit.getBlockPos()).isReplaceable() ? hit.getBlockPos() : hit.getBlockPos().offset(hit.getSide());
        railPos = p.toImmutable();
        step = Step.RAIL;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (home >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(home);
        home = -1;
        step = null;
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

    private void done() { setEnabled(false); }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        if (isNull() || step == null) return;
        if (++timeout > 100) { done(); return; }   // 5 s safety
        if (wait > 0) { wait--; return; }
        switch (step) {
            case RAIL -> {
                if (cartAt() != null || mc.world.getBlockState(railPos).isIn(net.minecraft.registry.tag.BlockTags.RAILS)) { step = Step.CART; return; }
                int s = slotOf(XbowCart::rail);
                var hit = BlockUtils.findPlacementHit(railPos, false);
                if (s < 0 || hit == null) { done(); return; }
                Cpvp.select(s, "Swap");
                if (!aim(hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
                if (Cpvp.place(railPos)) { step = Step.CART; wait = switchDelay.getValue(); }
            }
            case CART -> {
                if (cartAt() != null) { step = Step.FIRE; return; }
                int s = slotOf(st -> st.isOf(Items.TNT_MINECART));
                if (s < 0) { done(); return; }
                Cpvp.select(s, "Swap");
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(railPos).add(0, -.4, 0), Direction.UP, railPos, false);
                if (!aim(hit.getPos(), railPos)) return;
                if (BlockUtils.interactWithBlock(hit, true).isAccepted()) { step = Step.FIRE; wait = switchDelay.getValue(); }
            }
            case FIRE -> {
                TntMinecartEntity c = cartAt();
                if (c == null) { done(); return; }
                if (firePos == null) firePos = fireSpot(c);
                if (firePos == null) { done(); return; }
                var st = mc.world.getBlockState(firePos);
                if (st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE)) { step = Step.SHOOT; return; }
                int s = slotOf(x -> x.isOf(Items.FLINT_AND_STEEL));
                if (s < 0) { done(); return; }
                Cpvp.select(s, "Swap");
                BlockPos below = firePos.down();
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below).add(0, .5, 0), Direction.UP, below, false);
                if (!aim(hit.getPos(), below)) return;
                if (BlockUtils.interactWithBlock(hit, true).isAccepted()) { step = Step.SHOOT; wait = switchDelay.getValue(); }
            }
            case SHOOT -> {
                TntMinecartEntity c = cartAt();
                int s = slotOf(x -> x.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(x));
                if (c == null || s < 0) { done(); return; }
                Cpvp.select(s, "Swap");
                // aim through the fire into the cart
                Vec3d point = firePos != null ? Vec3d.ofCenter(firePos).lerp(c.getBoundingBox().getCenter(), .7) : c.getBoundingBox().getCenter();
                float[] r = Ballistics.solve(c.getBoundingBox().getCenter(), Ballistics.CROSSBOW_SPEED, 0);
                if (r == null) {
                    var d = point.subtract(mc.player.getEyePos());
                    r = new float[]{(float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f, (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalLength()))};
                }
                if (silentAim.getValue()) {
                    ServerRotations.request(this, r[0], r[1], .6f, 12);
                    Vec3d want = Ballistics.dir(r[0], r[1]);
                    if (Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, want.dotProduct(ServerRotations.sentLook()))))) > 1.5) return;
                }
                var p = mc.player;
                float yaw = p.getYaw(), pitch = p.getPitch();
                try {
                    p.setYaw(r[0]);
                    p.setPitch(r[1]);
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                } finally {
                    if (silentAim.getValue()) { p.setYaw(yaw); p.setPitch(pitch); }
                }
                done();
            }
        }
    }

    private TntMinecartEntity cartAt() {
        if (railPos == null) return null;
        for (Entity en : mc.world.getOtherEntities(null, new Box(railPos).expand(.6)))
            if (en instanceof TntMinecartEntity t && t.isAlive()) return t;
        return null;
    }

    private BlockPos fireSpot(TntMinecartEntity c) {
        Vec3d dir = mc.player.getEyePos().subtract(c.getEntityPos());
        Direction toward = Direction.getFacing(dir.x, 0, dir.z);
        for (Direction d : new Direction[]{toward, toward.rotateYClockwise(), toward.rotateYCounterclockwise()}) {
            BlockPos p = c.getBlockPos().offset(d);
            if (mc.world.getBlockState(p).isAir()
                    && mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world, p.down(), Direction.UP)
                    && Cpvp.inReach(p.down())) return p.toImmutable();
        }
        return null;
    }
}
