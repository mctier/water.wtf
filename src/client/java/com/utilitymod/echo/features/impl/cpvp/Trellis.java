package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.ServerRotations;
import com.utilitymod.echo.utils.blocks.BlockUtils;
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
 * OpenPrestige Auto Cart.
 * <ul>
 *   <li><b>Trigger Bind</b>: lay a rail + TNT cart (at the predicted arrow landing or your crosshair), then fire
 *   your bow / charged crossbow at it on the chosen Arc.</li>
 *   <li><b>On Bow Shot</b>: when you release a bow yourself, lay rail + cart where the arrow will land.</li>
 *   <li><b>Overload</b>: stack several carts on the rail (hold Overload Bind, or press it to fire then stack
 *   at the landing while the arrow flies).</li>
 *   <li><b>Auto Hit Placed</b>: while you draw a bow, aim at the nearest placed TNT cart and release on time.</li>
 *   <li>Safe Blocks, server-side Silent Aim, landing path render.</li>
 * </ul>
 */
public class Trellis extends Feature {
    private final IntSetting sdQ = new IntSetting(LT.L1, 2, 0, 20);
    private final IntSetting bowBackDelay = new IntSetting(LT.L2, 0, 0, 20);
    private final IntSetting minCharge = new IntSetting(LT.L3, 1, 1, 20);
    private final IntSetting arc = new IntSetting(LT.L4, 20, 0, 100);
    private final KeybindSetting triggerBind = new KeybindSetting(LT.L5, -1);
    private final IntSetting overload = new IntSetting(LT.L6, 1, 1, 4);
    private final KeybindSetting overloadBind = new KeybindSetting(LT.L7, -1);
    private final RangeSetting overloadDelay = new RangeSetting(LT.L8, 50f, 50f, 0f, 1000f, 1f);
    private final BoolSetting overloadHotbarOnly = new BoolSetting(LT.L9, false);
    private final ModeSetting placeMode = new ModeSetting(LT.L10, LT.L11, LT.L11, LT.L12);
    private final BoolSetting onBowShot = new BoolSetting(LT.L13, true);
    private final BoolSetting autoHit = new BoolSetting(LT.L14, true);
    private final FloatSetting autoHitRange = new FloatSetting(LT.L15, 24f, 5f, 50f, .5f);
    private final IntSetting autoHitMinCharge = new IntSetting(LT.L16, 20, 10, 20);
    private final BoolSetting switchBackToBow = new BoolSetting(LT.L17, true);
    private final BoolSetting safeBlocks = new BoolSetting(LT.L18, true);
    private final BoolSetting sAim = new BoolSetting(LT.L19, true);
    private final FloatSetting fov = new FloatSetting(LT.L20, 180f, 0f, 180f, 1f);
    private final BoolSetting renderPath = new BoolSetting(LT.L21, true);
    private final ColorSetting renderColor = new ColorSetting(LT.L22, 131, 118, 252, 255);

    private enum Step { RAIL, CARTS, SAFE, FIRE, BOW_BACK }

    private final Cpvp.Edge triggerEdge = new Cpvp.Edge(), overloadEdge = new Cpvp.Edge();
    private Step step;
    private BlockPos railPos;
    private int cartsWanted, cartsPlaced, wait, bowSlot = -1, home = -1;
    private boolean fireAfter;           // trigger sequence: shoot at the cart when it is laid
    private long nextCartAt;
    private boolean wasDrawing, waitedBack;
    private float lastYaw, lastPitch;
    private int lastCharge;
    private BlockPos renderLanding;

    public Trellis() {
        super(new FeatureInfo(LT.L23, LT.L24, Category.COMBAT));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        finish();
    }

    private void finish() {
        if (home >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(home);
        home = -1;
        step = null;
        railPos = null;
        fireAfter = false;
    }

    private static boolean rail(ItemStack s) {
        return s.isOf(Items.RAIL) || s.isOf(Items.POWERED_RAIL) || s.isOf(Items.DETECTOR_RAIL) || s.isOf(Items.ACTIVATOR_RAIL);
    }

    private int slotOf(java.util.function.Predicate<ItemStack> p) {
        var inv = mc.player.getInventory();
        if (p.test(inv.getStack(Cpvp.selected()))) return Cpvp.selected();
        for (int i = 0; i < 9; i++) if (p.test(inv.getStack(i))) return i;
        return -1;
    }

    private boolean drawingBow() {
        return mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.BOW);
    }

    private String lastNote;
    private long lastNoteAt;
    private int aimTicks;

    /** Tell the player why a step stopped (rate-limited so it never spams). */
    private void note(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastNote) && now - lastNoteAt < 1500) return;
        lastNote = msg;
        lastNoteAt = now;
        com.utilitymod.echo.utils.ChatUtils.chat(LT.L25 + msg);
    }

    private void fail(String msg) { note(msg); finish(); }

    private boolean aim(Vec3d point, BlockPos block) {
        return Cpvp.aimed(this, sAim.getValue(), fov.getValue(), point, block);
    }

    // ---- main loop ---------------------------------------------------------------------------

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        if (isNull() || mc.currentScreen != null) return;
        boolean drawing = drawingBow();
        int charge = drawing ? mc.player.getItemUseTime() : 0;
        renderLanding = null;
        if (drawing) {
            BlockHitResult land = Ballistics.landing(mc.player.getYaw(), mc.player.getPitch(), Ballistics.bowSpeed(charge));
            if (land != null) renderLanding = land.getBlockPos();
        }

        // passive: you just released a bow yourself -> cart at its landing
        if (step == null && wasDrawing && !drawing && onBowShot.getValue() && lastCharge >= 3) {
            BlockHitResult land = Ballistics.landing(lastYaw, lastPitch, Ballistics.bowSpeed(lastCharge));
            if (land != null) start(spotFrom(land), false, overloadEdge.held(overloadBind.getKey()) ? overload.getValue() : 1);
        }
        wasDrawing = drawing;
        if (drawing) { lastYaw = mc.player.getYaw(); lastPitch = mc.player.getPitch(); lastCharge = charge; }

        if (step == null) {
            if (triggerEdge.pressed(triggerBind.getKey())) {
                BlockPos spot = targetSpot();
                if (spot != null) start(spot, true, overloadEdge.held(overloadBind.getKey()) ? overload.getValue() : 1);
            } else if (overloadEdge.pressed(overloadBind.getKey())) {
                overloadShot();
            } else if (autoHit.getValue() && drawing) {
                autoHitPlaced(charge);
            }
        }
        if (step != null) run();
    }

    /** Press Overload Bind without a trigger: fire toward your aim, then stack carts at the landing. */
    private void overloadShot() {
        float speed;
        if (drawingBow()) speed = Ballistics.bowSpeed(mc.player.getItemUseTime());
        else if (mc.player.getMainHandStack().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(mc.player.getMainHandStack())) speed = Ballistics.CROSSBOW_SPEED;
        else return;
        BlockHitResult land = Ballistics.landing(mc.player.getYaw(), mc.player.getPitch(), speed);
        if (drawingBow()) mc.interactionManager.stopUsingItem(mc.player);
        else mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (land != null) start(spotFrom(land), false, overload.getValue());
    }

    private void start(BlockPos spot, boolean fire, int carts) {
        if (spot == null) { note(LT.L26); return; }
        if (!Cpvp.inReach(spot)) {
            note(String.format(LT.L27,
                    Math.sqrt(mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(spot)))));
            return;
        }
        railPos = spot;
        aimTicks = 0;
        cartsWanted = Math.max(1, carts);
        cartsPlaced = 0;
        fireAfter = fire;
        step = Step.RAIL;
        wait = 0;
        waitedBack = false;
        home = Cpvp.selected();
        bowSlot = slotOf(s -> s.isOf(Items.BOW));
    }

    private BlockPos targetSpot() {
        if (placeMode.is(LT.L12)) {
            var hit = Cpvp.crosshairBlock();
            return hit == null ? null : spotFrom(hit);
        }
        int charge = drawingBow() ? Math.max(mc.player.getItemUseTime(), minCharge.getValue()) : 20;
        BlockHitResult land = Ballistics.landing(mc.player.getYaw(), mc.player.getPitch(), Ballistics.bowSpeed(charge));
        return land == null ? null : spotFrom(land);
    }

    /** The air block a rail goes into for a hit on {@code hit}. */
    private BlockPos spotFrom(BlockHitResult hit) {
        BlockPos p = hit.getBlockPos();
        BlockPos up = mc.world.getBlockState(p).isReplaceable() ? p : p.offset(hit.getSide() == Direction.DOWN ? Direction.UP : hit.getSide());
        if (!mc.world.getBlockState(up.down()).isSideSolidFullSquare(mc.world, up.down(), Direction.UP)) up = p.up();
        return up.toImmutable();
    }

    private void run() {
        if (wait > 0) { wait--; return; }
        if (railPos == null) { finish(); return; }
        if (!Cpvp.inReach(railPos)) { fail(LT.L28); return; }
        switch (step) {
            case RAIL -> {
                if (!mc.world.getBlockState(railPos).isAir() && !mc.world.getBlockState(railPos).isReplaceable()) { step = Step.CARTS; return; }
                int s = slotOf(Trellis::rail);
                var hit = BlockUtils.findPlacementHit(railPos, false);
                if (s < 0) { fail(LT.L29); return; }
                if (hit == null) { fail(LT.L30); return; }
                Cpvp.select(s, LT.L31);
                if (!aim(hit.hitResult().getPos(), hit.hitResult().getBlockPos())) { if (++aimTicks > 12) fail(LT.L32); return; }
                aimTicks = 0;
                if (Cpvp.place(railPos)) { step = Step.CARTS; wait = sdQ.getValue(); }
                else fail(LT.L33);
            }
            case CARTS -> {
                if (cartsPlaced >= cartsWanted) { step = safeBlocks.getValue() ? Step.SAFE : fireAfter ? Step.FIRE : Step.BOW_BACK; return; }
                if (System.currentTimeMillis() < nextCartAt) return;
                int s = slotOf(st -> st.isOf(Items.TNT_MINECART));
                if (s < 0 && !overloadHotbarOnly.getValue() && cartsPlaced > 0) {
                    int src = Cpvp.findInventory(Items.TNT_MINECART, false);
                    if (src >= 0) { Cpvp.swapInto(src, Cpvp.selected()); s = Cpvp.selected(); }
                }
                if (s < 0) { if (cartsPlaced > 0) { step = Step.SAFE; return; } fail(LT.L34); return; }
                if (!mc.world.getBlockState(railPos).isIn(net.minecraft.registry.tag.BlockTags.RAILS)) { fail(LT.L35); return; }
                Cpvp.select(s, LT.L31);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(railPos).add(0, -.4, 0), Direction.UP, railPos, false);
                if (!aim(hit.getPos(), railPos)) { if (++aimTicks > 12) fail(LT.L36); return; }
                aimTicks = 0;
                if (!BlockUtils.interactWithBlock(hit, true).isAccepted()) { fail(LT.L37); return; }
                {
                    cartsPlaced++;
                    nextCartAt = Cpvp.next(overloadDelay);
                    if (cartsPlaced >= cartsWanted) wait = sdQ.getValue();
                }
            }
            case SAFE -> {
                BlockPos cover = coverSpot();
                int s = slotOf(st -> st.isOf(Items.OBSIDIAN) || st.isOf(Items.COBBLESTONE));
                if (cover != null && s >= 0) {
                    var hit = BlockUtils.findPlacementHit(cover, false);
                    if (hit != null) {
                        Cpvp.select(s, LT.L31);
                        if (!aim(hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
                        Cpvp.place(cover);
                        wait = sdQ.getValue();
                    }
                }
                step = fireAfter ? Step.FIRE : Step.BOW_BACK;
            }
            case FIRE -> fire();
            case BOW_BACK -> {
                if (!waitedBack && bowBackDelay.getValue() > 0) { waitedBack = true; wait = bowBackDelay.getValue(); return; }
                if (switchBackToBow.getValue() && bowSlot >= 0) { mc.player.getInventory().setSelectedSlot(bowSlot); home = -1; }
                finish();
            }
        }
    }

    /** Shoot the placed cart: charged crossbow if you have one, else draw the bow to Min Charge and release. */
    private void fire() {
        TntMinecartEntity cart = cartAt(railPos);
        if (cart == null) { finish(); return; }
        Vec3d point = cart.getBoundingBox().getCenter();
        int xbow = slotOf(st -> st.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(st));
        if (xbow >= 0) {
            Cpvp.select(xbow, LT.L31);
            float[] r = Ballistics.solve(point, Ballistics.CROSSBOW_SPEED, arc.getValue());
            if (r == null) { finish(); return; }
            if (shoot(r, false)) finish();
            return;
        }
        int bow = slotOf(st -> st.isOf(Items.BOW));
        if (bow < 0) { finish(); return; }
        Cpvp.select(bow, LT.L31);
        if (!drawingBow()) { mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); return; }
        int charge = mc.player.getItemUseTime();
        float[] r = Ballistics.solve(point, Ballistics.bowSpeed(Math.max(charge, minCharge.getValue())), arc.getValue());
        if (r == null) { if (charge >= 20) finish(); return; }
        if (charge < minCharge.getValue()) { request(r); return; }
        if (shoot(r, true)) finish();
    }

    private void request(float[] r) {
        if (sAim.getValue()) ServerRotations.request(this, r[0], r[1], .6f, 12);
    }

    /** Aim at {yaw,pitch}; once the server has it, release the bow / fire the crossbow with that look. */
    private boolean shoot(float[] r, boolean bow) {
        if (sAim.getValue()) {
            request(r);
            Vec3d want = Ballistics.dir(r[0], r[1]);
            if (Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, want.dotProduct(ServerRotations.sentLook()))))) > 1.5) return false;
        }
        var p = mc.player;
        float yaw = p.getYaw(), pitch = p.getPitch();
        try {
            p.setYaw(r[0]);
            p.setPitch(r[1]);
            if (bow) mc.interactionManager.stopUsingItem(p);
            else mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        } finally {
            if (sAim.getValue()) { p.setYaw(yaw); p.setPitch(pitch); }
        }
        return true;
    }

    /** While you draw a bow: aim at the nearest placed TNT cart and let go when charged and on target. */
    private void autoHitPlaced(int charge) {
        TntMinecartEntity best = null;
        double bd = autoHitRange.getValue() * autoHitRange.getValue();
        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof TntMinecartEntity c) || !c.isAlive()) continue;
            double d = c.squaredDistanceTo(mc.player);
            if (d < bd && mc.player.canSee(c)) { bd = d; best = c; }
        }
        if (best == null) return;
        float[] r = Ballistics.solve(best.getBoundingBox().getCenter(), Ballistics.bowSpeed(Math.max(charge, autoHitMinCharge.getValue())), arc.getValue());
        if (r == null) return;
        if (charge < autoHitMinCharge.getValue()) { request(r); return; }
        shoot(r, true);
    }

    private TntMinecartEntity cartAt(BlockPos p) {
        for (Entity en : mc.world.getOtherEntities(null, new Box(p).expand(.6)))
            if (en instanceof TntMinecartEntity t && t.isAlive()) return t;
        return null;
    }

    private BlockPos coverSpot() {
        Vec3d from = Vec3d.ofCenter(railPos), to = mc.player.getEyePos();
        Vec3d dir = to.subtract(from).normalize();
        for (double d = 1; d < 3.5; d += .25) {
            BlockPos p = BlockPos.ofFloored(from.add(dir.multiply(d)));
            if (p.equals(railPos) || p.equals(mc.player.getBlockPos()) || p.equals(mc.player.getBlockPos().up())) continue;
            if (Cpvp.canPlace(p) && Cpvp.inReach(p)) return p;
        }
        return null;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onRender(EventRender3D e) {
        if (!renderPath.getValue() || renderLanding == null || isNull()) return;
        e.getDraw3D().getMinecraftTarget().box(renderLanding.getX(), renderLanding.getY(), renderLanding.getZ(), 1, 1, 1, Cpvp.argb(renderColor, 255));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8bc7,0x2742,0x31da,0xabe7,0xc5ae,0x612b,0x1a28,0xfa86,0x746b,0x1edc,0x6e5f,0x3769}, new char[]{0x7438,0x31ad,0x3700,0xc5da,0x1ebf,0x272a,0x8be7,0x1a6c,0x6e3a,0xab8b,0xfae7,0x6152}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbbda,0x89e3,0xd162,0x4551,0x05de,0xe027,0x044c,0xbf87,0x9105,0x00cc,0x7f5a,0x61fd,0x8d2a,0x8247}, new char[]{0xbfc5,0x05b1,0x8994,0x61dd,0x040e,0xd103,0x8224,0xbbb1,0x8d0a,0xe063,0x9160,0x453d,0x00ad,0x7f23}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6985,0x493d,0x7ab1,0xa627,0x6d61,0x4613,0x3bf5,0x538d,0xe109,0x7b8b}, new char[]{0x53c0,0x4954,0x467d,0xa607,0xe14a,0x7ad9,0x7bea,0x3b87,0x69e2,0x6d04}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7377,0xf52d,0xe349}, new char[]{0xe308,0xf55f,0x7314,0x2921}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbff2,0xf8eb,0x919f,0xefca,0x2bfb,0x96c7,0x3687,0x0a4c,0x9945,0x87ee,0x11a2,0x4850}, new char[]{0x9693,0xefb8,0x992c,0xf88c,0xbf95,0x11c7,0x91ed,0x87ce,0x2bb9,0x4839,0x0a22,0x36e3}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe495,0xcd00,0x981c,0x2e6d,0xb942,0x7877,0x016c,0xee01}, new char[]{0xee4e,0xcd76,0x0109,0x2e1f,0x9870,0x7818,0xb923,0xe4f1}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6799,0xa555,0xe88f,0x64ac,0x8785,0xf363,0x3037,0x7525,0x0ea4,0xdd15,0x6520,0x6196,0xb18a}, new char[]{0x61d9,0x0ed2,0x67fc,0x7557,0xa539,0x64c3,0x87e4,0xf307,0xdd35,0xb1c8,0xe8e6,0x3059,0x6544}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd879,0xd837,0x8518,0xf9ed,0x2494,0x2713,0x071f,0xc6cc,0x88b3,0x12a4,0x4d33,0x71f5,0xb597,0xa08a}, new char[]{0xb5d8,0x856e,0xc6a9,0x88c1,0xa0e6,0x24fb,0xf98c,0xd853,0x073f,0x12e0,0x4d56,0x277f,0xd818,0x718c}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5336,0xca5c,0x144a,0x3d10,0xd722,0x8b2b,0xe71b,0x4b95,0x774c,0x8f06,0x781a,0xf77e,0xac9b,0x078b,0xdcfa,0x69e6,0xf370,0x3fcb,0xc46a,0x4e34}, new char[]{0xdcb5,0x8f70,0x07ee,0xca2e,0xe777,0xc405,0x5357,0xf314,0x783a,0x8b63,0x1425,0x7738,0x3d72,0x3faa,0x6994,0x4bb5,0xf731,0x4e5a,0xacf7,0xd75b}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe684,0xac90,0xb58c,0x114e,0x028d,0x063d,0x4845,0x8174,0x65f9,0x62f4}, new char[]{0x02dd,0x6298,0x6598,0xe6e7,0x112b,0xb5ac,0x0670,0xacff,0x4821,0x8111}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x938f,0x5c80,0x7527,0xc706,0x9fdf,0x43be,0x6bf1,0xf00f,0x2429,0x1133}, new char[]{0x93db,0x5cf2,0x6b90,0xf065,0x244c,0x43dd,0x1147,0x7548,0x9fad,0xc77f}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x162a,0x5c2c,0x6d68,0x3841,0x75b1,0x1d90,0xd86a,0xe3f0,0xcd13}, new char[]{0xcd50,0x75c3,0x5c43,0x1659,0xd819,0x6d00,0x1df1,0xe399,0x3833}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcc87,0x3a70,0xb74b,0xae40,0xaf67,0xe8ee,0x4c4e,0x88c4,0x1410,0x36cb,0x4165}, new char[]{0x3a3f,0xcce9,0xb76b,0xae02,0x147f,0x4112,0xaf47,0xe8bd,0x88ac,0x36a4,0x4c3a}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xab0d,0xfe6d,0xe286,0x6b84,0x4232,0x13c0,0x3ac9,0x9f6d,0xbf4e,0x5b38,0x5ad9,0x9b9f,0x041a,0x30d1,0x8284}, new char[]{0x5a98,0x9f18,0xab79,0xbf21,0x3ae9,0x6bcc,0xe2ef,0x13b4,0x4212,0x82d4,0xfe01,0x9bfe,0x5b5b,0x047f,0x30b5}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xee54,0x1763,0xc5b3,0x5890,0xa9a3,0xf249,0x9b66,0x3d49,0xe064,0xd0e4,0xf0f0,0x6908,0x3f25,0x5a5e}, new char[]{0xf208,0xa9d6,0x9b12,0xd08b,0xc593,0x6940,0x3d20,0xf084,0xe044,0xee06,0x1702,0x58fe,0x3f42,0x5a3b}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2630,0xf1a4,0xa470,0xab67,0x782b,0x3fac,0x85c3,0xca1f,0x3cd3,0x5e94,0x2b09,0x30b4,0x77c6,0x0633,0xdfcb,0xb6aa,0x3eee,0xce65,0x8f89}, new char[]{0xa431,0x2b7c,0xce11,0x3fc3,0x3ece,0x3c9b,0x5efd,0xab13,0xca3f,0x30f9,0x8fe0,0x7845,0x85e3,0x7785,0xf1cc,0x2651,0x0641,0xdfac,0xb6cf}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x458b,0xa90c,0x8fc9,0xaefa,0xb817,0xf0e4,0xb3a3,0x0194,0x8581,0x7262,0x6985,0xa041,0xaa1d,0xcd14,0xd713,0xfd60,0x44e2,0x8a5b}, new char[]{0xd740,0x85f6,0x01fd,0xb863,0xb3c0,0xa964,0xa061,0x69c7,0x8fa8,0xcd77,0x45e0,0xaa3d,0x7236,0x8a34,0x44c2,0xfd22,0xae95,0xf093}).toString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c43,0xe7eb,0xfc16,0x795b,0x0b8e,0xc3c1,0x76ff,0x9757,0x163e,0x25f3,0x7c14}, new char[]{0x166d,0xc3a0,0xfc70,0xe78e,0x9777,0x25b1,0x7937,0x7690,0x7c77,0x0c28,0x0bfd}).toString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x69bd,0x58b6,0x63d8,0x61fb,0x95b1,0x0c40,0x9f4f,0x7c8f,0xd4ec,0xbb6b}, new char[]{0x7cdc,0x58df,0xd480,0x95d4,0x69d3,0x618f,0xbb4b,0x6399,0x0c29,0x9f22}).toString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4791,0xe59c,0xc1ac,0x412a,0xdc1c,0xda1f,0x53e0,0x1568,0x9a9d,0x512d,0x7a7d,0xc3c1,0x2cda,0x8dd4}, new char[]{0x53b3,0x9af4,0xda73,0x47f4,0xe5f2,0xc3b5,0x8df4,0xc1ed,0x4143,0x7a10,0xdc3c,0x2c9c,0x1527,0x517b}).toString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f0b,0x3360,0xb21d,0x7f0b,0x7db9,0x7f79,0x1774,0x658f,0x5f0a,0xfc3d,0xf130}, new char[]{0xf162,0xfc58,0xb273,0x5f6e,0x3305,0x7dcb,0x65af,0x7f5b,0x6f6a,0x1700,0x7f11}).toString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4477,0x38f8,0x90a3,0x42a7,0x0411,0x3214,0xe661,0x27c4,0x47ee,0xb594,0x00ef,0xd90d}, new char[]{0xd95f,0x008a,0x90cd,0xe605,0x0474,0x42d5,0x38d8,0x47ad,0x327b,0xb5f8,0x4418,0x27b6}).toString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x70b6,0xbd7e,0xcae8,0x217c,0x9a2a,0xd37c,0xa35a,0xdf05,0xb65c}, new char[]{0x9a6b,0xca9d,0xdf71,0x2113,0xbd5e,0xd33f,0x70d7,0xa328,0xb628}).toString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdc0b,0x632a,0x957f,0x71f1,0xd40f,0xf72e,0x0e08,0xa841,0x5ee6,0xf3b9,0xc584,0x4adb,0x8db9,0x5fef,0x23d8,0x2b88,0x6213,0x692f,0x5bc1,0x6e8b,0x5d41,0xbd90,0xd67d,0x11d6,0x4bf1,0xb725,0xf3d6,0xe4d5,0x04d1,0xc77f,0x9263,0x1fc9,0xe4fa,0x398f,0xdfa8,0x699e,0xe3e3,0xe8fd,0x1d24,0x0197,0x7529,0xd836,0x47d4,0xe120,0xe282,0x1a01,0x93aa,0x6cb6,0x43f4,0x4397,0x21be,0x35e4,0x817f,0x14c5,0xbb38,0x2f31,0x308c,0x4397,0x391d,0x3483,0x5b67,0xd100,0x9516,0xed65,0xe22c,0x551a,0x7fe5,0xfb30,0xeb92,0x5296,0x3b0a,0xfb53,0x2520,0x95b7,0x1f06,0xda09,0xd560,0xa670,0x3e06,0x56a4,0xf092,0x4d61,0x2a40,0xab63,0x8666,0xe3d9,0xa3b0,0x6e1e,0xb759,0x0088}, new char[]{0x1497,0x634b,0x47bd,0x3e6a,0xf75d,0xed45,0xe141,0x52f8,0x2f55,0x23f8,0xb771,0x43d9,0xa3e4,0x219e,0xd16d,0x6e77,0xc5ea,0xf3dc,0xd46c,0x7548,0x4386,0xe2f6,0x95c4,0x5684,0x5f8e,0x2a34,0xda29,0x93d3,0x6cd9,0x00fd,0xd512,0x393d,0x2be9,0x11a4,0x8dcb,0x719e,0x3593,0xa866,0x4aa8,0xdf88,0xe4b9,0x5ba0,0x0e66,0x5d25,0xf0fb,0x1d4a,0x6274,0xbb18,0x9565,0xe25c,0xd859,0xf3a2,0xa65c,0x34a3,0xdc7c,0x04b8,0x810b,0xfb3b,0x39af,0x69ea,0x6ef9,0x5e8f,0x43f0,0xe384,0x9206,0xfb42,0x1f2a,0x955f,0x30e3,0x5b11,0x557f,0xe488,0x860a,0x7f8a,0xd61c,0xb73d,0xe3f9,0x01f6,0xe893,0x2544,0x4d41,0x1fa8,0xebe7,0x695b,0x4b9e,0xbdbd,0x1a69,0xc716,0x3b7e,0xab4d}).toString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0223,0x5208,0xd521,0x05c2,0x4069,0xb65f,0x21bf,0x4203,0x9602,0x78c1,0x1842,0x3d8d,0x4ae6,0x080b,0x774b,0x5250}, new char[]{0x18e5,0x3de9,0x0850,0x770a,0x9677,0x5224,0x21d0,0x0203,0x4240,0xb63e,0x4a94,0x401d,0x789c,0x0565,0x527a,0xd501}).toString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x998a,0x6747,0x44fa,0xabfa,0xe191,0xe365,0x30f2,0xb0f8,0x3ec9,0xca4a,0x8c1d,0x8558,0x87b0,0xae6a,0xc19c,0x9a94,0x09a9,0x9e79,0xa8b3}, new char[]{0xae04,0xca25,0x9e59,0xb08b,0x8c6d,0x4495,0xc1e8,0x9ab4,0x99fe,0xe1fe,0x8790,0x09d9,0xab96,0x3093,0x6724,0x3eac,0x8578,0xe30a,0xa8dd}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2246,0x6325,0xbd17,0x1efc,0x4ad6,0x80cb,0x6dc3,0x98e8,0x608b,0x745e,0xa7da,0x75a6,0x9acf,0x55b5,0xfe1c,0x4f98,0x5ed1,0xf4fe,0x8ce0,0x2951,0xeabf,0xb554,0x5db9,0x54d3,0xc78d,0x1d7a,0x7245,0xbcdf,0x22b3,0x1486,0xab7e,0x9f71,0x4543,0x38d6,0x898a,0x128d,0x15d8,0x5a82,0x2ad5,0x5078,0x6590,0xb3fb,0xa200,0x35ca,0xda6a,0xd1b0,0x4440,0x1a1a,0x5a29,0xe2fb,0x3b74,0x0019,0x542d,0xe98e,0x7e55,0xedda,0xcef2,0x76d5,0xd18e,0x2265,0xe162,0x8134,0x4029,0xa061,0x106d,0x2fb5,0x8f0e,0x398f,0x0af3,0x16e9,0xecc5,0x6514,0x8313,0x9354,0xd3d3,0x4317,0xb836,0x8431,0xb026}, new char[]{0xbcb3,0x8f6f,0x657a,0x8150,0x15b1,0x933a,0x501f,0x0039,0x837a,0x4433,0x7586,0xb53b,0xb843,0x4363,0x65b0,0x5dd6,0x22d5,0xedfa,0x4fea,0xce97,0xe9ef,0x7e36,0xd3bb,0x1d5a,0x38fe,0x9f54,0xd19e,0xecf4,0xc7eb,0x8cc0,0xa262,0xda06,0x76ba,0xb045,0x0a98,0xeacc,0xa7f3,0x89aa,0x39a2,0x2971,0x1e9b,0x2223,0x1019,0x14a6,0xab1d,0x845d,0x4046,0xbd64,0x54b6,0x55c7,0xe2db,0x5ebe,0xd1fc,0x540d,0xfe69,0x3b07,0xf49b,0x2245,0x5ad2,0x60e7,0x2ab4,0x743d,0xe107,0x6de3,0x1a57,0x80a4,0x988c,0x4ab3,0x2f8f,0x6305,0xb3b8,0x12ff,0x1686,0x7236,0x4530,0xa009,0x5a48,0x9aa6,0x35b8}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x14eb,0xe571,0xe0d3,0xf705,0x123c,0xa70e,0x1f0f,0xbe13,0xe3dc,0xfa6b,0xf667,0x7cde,0x1ccb,0x9927,0xbc98,0xfa5a,0xaa6e,0x7cf1,0x8d86,0xbf3d,0xe1db,0x1245,0x5add,0xfb55,0x020f,0x2fdc,0x91db}, new char[]{0xbf4f,0xe0b2,0xf76c,0x5ab1,0xbcb8,0xaa1d,0x149b,0xe1b4,0x1cbf,0x121c,0xf610,0x91be,0xfa34,0xfa1f,0xe551,0x8de9,0x027a,0x9953,0xe3fc,0x2fb3,0x7cb8,0xfb75,0x1f7d,0x1220,0x7c90,0xbe70,0xa766}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1fbd,0x2fea,0xcba7,0xf75c,0x52d6,0x9452,0xd3b6,0x2241,0x3a2b,0xfd0a,0x6732,0xf50a,0xb8ee,0xe879,0x8ffe,0xab58,0x856a,0xb3cd,0x429a,0x8155,0xa3e3,0xc9e3}, new char[]{0xb880,0x8505,0xa3c3,0x3a59,0xab39,0x2f83,0x8139,0xe859,0xcbce,0x675c,0xf52a,0xb3b4,0x1fd2,0xf729,0x2233,0x9472,0xfd62,0xd3d9,0xc997,0x42f8,0x8f9f,0x52a4}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x83c4,0xb22d,0x539a,0x1b5a,0x1baa,0xf3fa,0xc83a,0x43e7,0x4f81,0xd0bf,0xcc50,0x2762,0x3cd5,0x9c18,0x2f2b,0xfaca,0xe243,0x0420,0x8cce,0xb122,0x9a6f,0x66e9,0x35c1,0xa191,0xd033,0xc33b,0xaf05,0x54fe,0xdfda,0xc7f3,0xb019,0x47ea}, new char[]{0xc355,0xf395,0x9c6c,0x5496,0x8ca7,0x1bc4,0x4fe6,0x47ca,0xd0cc,0xe22c,0xa1fd,0x1b33,0x83a0,0x0400,0xb156,0xb242,0xc81a,0x6699,0x4392,0xdfae,0xaf25,0x3ca1,0x2f43,0xb07c,0xfaea,0xcc22,0x9a0e,0x35a8,0x270e,0xd013,0xc79c,0x53f4}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf465,0x49a1,0x6524,0x5e98}, new char[]{0x6577,0x5eef,0x49c0,0xf415}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b2e,0x8381,0xa6b3,0x057d,0x94aa,0xb2f8,0x99ff,0x1141,0x01b3,0xe228,0x1045,0xb909,0xea44,0x081d,0x5fc4,0xb932,0x15a1,0xebc2,0x5b28,0xd941,0x971a,0xddea,0x7c3f,0xc8fe,0x07a0,0x4cf5,0xeaa6,0xe84d,0x077c,0x5792,0x62bb,0x4e9e,0xe56a,0x90e5,0x42d3,0x1a41}, new char[]{0xa6d0,0x57fd,0x976f,0x5b44,0xe829,0xdd84,0xe20f,0x5fb0,0xea64,0xb299,0xc897,0xb95f,0xd961,0xeac7,0xe51e,0x1581,0x0509,0x1b46,0x1a24,0x42f3,0xebb0,0x1024,0x4ef7,0x4c99,0x075c,0x086e,0xb979,0x9990,0x94de,0x629b,0x0788,0x01f5,0x110e,0x90b3,0x83be,0x7c16,0xc7c6,0xe4c6,0x480f,0x1710,0xd803,0x3273,0xec17,0x0b51,0x9d20,0x4cb9,0x21d1}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b39,0xa6f6,0x131b,0x5cf6,0x4a8b,0x153d,0xfda3,0x2776,0xbfd8,0x91e1,0x51d9,0x9250,0x53f5,0xcfa3,0x8a95,0x368f,0x0d0c,0x45e3,0x3960,0xa771,0x8f40,0xc9f3,0x36c1,0x4e56,0x8b56,0xa08b}, new char[]{0x8ae7,0x4582,0x51b0,0x390c,0x36e1,0x2706,0x8f2c,0x5c97,0xc990,0x8b33,0x36e2,0x1b5c,0xa0e5,0x4aff,0x91c1,0xbfaf,0x9231,0x1368,0xa751,0x4e24,0x0d69,0xfdc5,0x5380,0xa685,0xcfc6,0x1559}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x01b0,0x623f,0x2963,0xb004,0x189b,0x6933,0xe8b1,0x0dae,0x54bc,0x7840,0x8290,0xd7cc,0xe6d5,0xe155,0x05a2,0x8620,0x1673,0x70bf,0x7bc7,0xf342,0x5b55,0xbd0a,0x1fa2,0x3be9,0x5fbb,0xa397,0x2b37,0xb123,0x7732,0x05ab}, new char[]{0x18f5,0x775d,0xa3b7,0xf316,0x0de0,0xb177,0x2943,0x1fcf,0x70d6,0xb06a,0x05c7,0x1610,0x82f1,0xbd78,0xd7b8,0x549c,0x3b80,0x6251,0x5b75,0x2b4e,0x7ba8,0xe6a0,0x6941,0xe175,0x01d8,0x864f,0xe8c5,0x05c9,0x5fda,0x7832}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3701,0x8150,0x327e,0x14fd,0xde9c,0xfa52,0xd0d2,0x07ab,0x2b0d,0xf5a2,0x7765,0xb64f,0x57f7,0x8215,0x046e,0xd904,0xf089,0x8107,0x526c,0xd133,0xa9af,0xcc15,0xaa15,0x6abc,0x836b,0xcdcd,0xcc5a,0xa752,0x37ea,0x734f,0x4fca,0xf916,0xb2e8,0x2182,0x47b8,0xf662,0x6156,0x654d,0x2e4e,0x191e,0xd88c,0xb1ee,0x9ff4,0x9b12,0x138b,0xf765,0x6d52}, new char[]{0xd0a6,0x2e26,0xa9ca,0x044e,0x6124,0xaa74,0xb187,0xfa3e,0x14dd,0x3783,0xd977,0xd8e2,0xf645,0xcc2e,0x656d,0x831f,0xcc7d,0x5792,0xcdbf,0xb62a,0x3721,0x4fe2,0x47d1,0x07df,0x325e,0xf5c6,0x6d3b,0x197a,0x8169,0xd114,0x9b66,0x524c,0x13fb,0xf0e5,0x8274,0x2b6e,0xdef9,0x7745,0xf70a,0x8122,0x21a2,0xa730,0xb29a,0xf979,0x6ad7,0x732a,0x9fdd}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4568,0x918b,0x8b07,0x3229,0xd8b0,0xa7c9,0x3d2a,0x5579,0xbacc,0xef64,0x2180,0x16c6,0xc77b,0x7010,0xa04a,0x5ed9,0xddd1,0x2500,0x6246,0x4839,0x276e,0xa651,0x571f,0xcdd5,0xd873,0xbb7f,0x17bb,0x8997,0xb41b,0x7db8,0x6af9}, new char[]{0x8b64,0xd81c,0xbb0a,0xa026,0x551d,0xef0a,0xcdf2,0xdda5,0x179b,0x89f6,0x2707,0x21ed,0x6ad9,0x5eb8,0x6232,0x7d98,0xa7bd,0x4851,0x16a3,0x2520,0xc709,0x4509,0x5776,0xa63d,0x3209,0xd898,0xb45d,0x3d65,0xba9a,0x702f,0x91a2,0x6af6,0xddcd,0x2350,0x542b,0x3c1a,0xfa4e,0x75f2,0x3a2b,0x113c,0xfa78,0x7c8e}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdad9,0x11bf,0x8d4c,0xf4c7,0xed0a,0x40dd,0x7ce9,0xd10e,0xd303,0xec28,0x1b02,0x165d,0xacdf,0x65ce,0xd4bf,0x995c,0x6a13,0xdc2a,0x5679,0xa638,0x937f,0x0833,0x2822,0x4df3,0xa55c,0x133b}, new char[]{0xdaba,0x65af,0x2850,0x560d,0x997c,0xed7a,0xd36f,0x11de,0xec4b,0x7c8c,0x1b6f,0x1638,0x6a7d,0xa528,0x40fd,0xd179,0x4d92,0xf4b4,0x8d6c,0xd4cd,0xacba,0xa65e,0x134e,0x0840,0x931a,0xdc4e}).plainString();
    }
}
