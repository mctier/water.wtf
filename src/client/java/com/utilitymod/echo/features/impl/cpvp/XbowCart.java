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
    private final IntSetting sdQ = new IntSetting(LT.L1, 2, 0, 20);
    private final BoolSetting sAim = new BoolSetting(LT.L2, true);
    private final FloatSetting fov = new FloatSetting(LT.L3, 180f, 0f, 180f, 1f);

    private enum Step { RAIL, CART, FIRE, SHOOT }

    private Step step;
    private BlockPos railPos, firePos;
    private int wait, home = -1, timeout;

    public XbowCart() {
        super(new FeatureInfo(LT.L4, LT.L5, Category.COMBAT));
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
        return Cpvp.aimed(this, sAim.getValue(), fov.getValue(), point, block);
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
                Cpvp.select(s, LT.L6);
                if (!aim(hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
                if (Cpvp.place(railPos)) { step = Step.CART; wait = sdQ.getValue(); }
            }
            case CART -> {
                if (cartAt() != null) { step = Step.FIRE; return; }
                int s = slotOf(st -> st.isOf(Items.TNT_MINECART));
                if (s < 0) { done(); return; }
                Cpvp.select(s, LT.L6);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(railPos).add(0, -.4, 0), Direction.UP, railPos, false);
                if (!aim(hit.getPos(), railPos)) return;
                if (BlockUtils.interactWithBlock(hit, true).isAccepted()) { step = Step.FIRE; wait = sdQ.getValue(); }
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
                Cpvp.select(s, LT.L6);
                BlockPos below = firePos.down();
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below).add(0, .5, 0), Direction.UP, below, false);
                if (!aim(hit.getPos(), below)) return;
                if (BlockUtils.interactWithBlock(hit, true).isAccepted()) { step = Step.SHOOT; wait = sdQ.getValue(); }
            }
            case SHOOT -> {
                TntMinecartEntity c = cartAt();
                int s = slotOf(x -> x.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(x));
                if (c == null || s < 0) { done(); return; }
                Cpvp.select(s, LT.L6);
                // aim through the fire into the cart
                Vec3d point = firePos != null ? Vec3d.ofCenter(firePos).lerp(c.getBoundingBox().getCenter(), .7) : c.getBoundingBox().getCenter();
                float[] r = Ballistics.solve(c.getBoundingBox().getCenter(), Ballistics.CROSSBOW_SPEED, 0);
                if (r == null) {
                    var d = point.subtract(mc.player.getEyePos());
                    r = new float[]{(float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f, (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalLength()))};
                }
                if (sAim.getValue()) {
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
                    if (sAim.getValue()) { p.setYaw(yaw); p.setPitch(pitch); }
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


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1a9f,0x605f,0xef1d,0xf6e6,0xb46b,0x06f3,0x50b9,0x8b72,0x2eb3,0xd07d,0x9aca,0xc681}, new char[]{0x8b21,0xf691,0xc6e8,0xd009,0x50da,0xef75,0x06d3,0x601b,0x9aaf,0x1af3,0x2ed2,0xb412}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0aeb,0x9ed6,0x3bb9,0xc11f,0x84d6,0x06f1,0x0cf6,0x341a,0x7d3d,0x22bf}, new char[]{0xc14c,0x3bd0,0x069d,0x7d58,0x0a85,0x22cb,0x9ef6,0x345b,0x0c9f,0x84bb}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x650e,0x4ac8,0x0f92,0x030a,0xaec8,0xb5aa,0x9306,0x2f60,0xf1b1,0xc8d9,0x2187,0x4e2a,0xccc9,0x86da}, new char[]{0x8689,0xcca0,0xc8b5,0x21e2,0x0ffc,0xf1c5,0x032a,0x2f21,0x4e43,0x936b,0xb58a,0xae8e,0x4a87,0x6558}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d0d,0xa7da,0x0e77,0xe224,0xd41d,0x121a,0x5322,0x3f01,0xaf70}, new char[]{0xd445,0x6d6f,0x3f6e,0xe253,0x123a,0xa799,0x0e16,0xaf02,0x5356}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc46f,0xb2b2,0x131c,0xb02f,0x467b,0x9d42,0x8e59,0xad9c,0x0828,0x3d1b,0x0805,0x7dc9,0xdf16,0xffc8,0x238c,0x68ac,0xbb55,0x3451,0xab18,0xcc8e,0x4ba1,0xda19,0x5125,0xc4aa,0x5bba,0x9b6c,0x5e40,0x9b08,0xe390,0xbd9f,0x5370,0x8ac9,0xff97,0x7061,0x0658,0x4a9e,0xf6b8,0x3fb2,0xe44e,0xa8f1,0xe3ff,0xbcff,0x6d99,0xcf89,0xb5a8,0x1a75,0xd941,0x537d,0x3ceb,0xc662,0x5b69,0x5051,0x8c3b,0x6378,0xbe2c,0x134a,0x184a,0x534f,0x0e46,0x5f69,0xa22b,0x2c6b,0xc24e,0x457b,0x9495,0x94bc,0x7d10,0x24e2,0xcd32}, new char[]{0x134e,0x182b,0xb2db,0xbe40,0x8ae5,0x0e66,0xcfdd,0xb061,0x68f8,0x536f,0x1a16,0xb5c9,0x3c99,0xc41b,0x5f45,0x24c2,0xbc99,0x2c02,0x7d62,0xbb30,0x4b81,0xadfd,0x5313,0xf6dc,0x23ac,0x5311,0x465b,0x5e23,0x94e7,0x6317,0x1339,0x8c48,0xffaa,0x700e,0x085f,0xdf36,0xda6a,0x4af6,0x8e36,0xc4de,0xe3b0,0xbdfe,0xffe3,0xcd12,0x6de0,0x514a,0x0870,0xe43c,0x3d3b,0x5032,0xd933,0x9b03,0x4508,0x3fc1,0xcce6,0x9d23,0x9b61,0x7dbb,0xe3df,0x94d5,0xc220,0xc642,0x5b06,0x0636,0x3434,0xab38,0x5bdd,0xa244,0xa8df}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd23c,0xf5e3,0x5900,0xe8d3}, new char[]{0xe880,0x5977,0xd25d,0xf593}).plainString();
    }
}
