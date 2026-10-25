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
public class AutoCart extends Feature {
    private final IntSetting switchDelay = new IntSetting("Switch Delay", 2, 0, 20);
    private final IntSetting bowBackDelay = new IntSetting("Bow Back Delay", 0, 0, 20);
    private final IntSetting minCharge = new IntSetting("Min Charge", 1, 1, 20);
    private final IntSetting arc = new IntSetting("Arc", 20, 0, 100);
    private final KeybindSetting triggerBind = new KeybindSetting("Trigger Bind", -1);
    private final IntSetting overload = new IntSetting("Overload", 1, 1, 4);
    private final KeybindSetting overloadBind = new KeybindSetting("Overload Bind", -1);
    private final RangeSetting overloadDelay = new RangeSetting("Overload Delay", 50f, 50f, 0f, 1000f, 1f);
    private final BoolSetting overloadHotbarOnly = new BoolSetting("Overload Hotbar Only", false);
    private final ModeSetting placeMode = new ModeSetting("Place Mode", "Trajectory", "Trajectory", "Crosshair");
    private final BoolSetting onBowShot = new BoolSetting("On Bow Shot", true);
    private final BoolSetting autoHit = new BoolSetting("Auto Hit Placed", true);
    private final FloatSetting autoHitRange = new FloatSetting("Auto Hit Range", 24f, 5f, 50f, .5f);
    private final IntSetting autoHitMinCharge = new IntSetting("Auto Hit Min Charge", 20, 10, 20);
    private final BoolSetting switchBackToBow = new BoolSetting("Switch Back To Bow", true);
    private final BoolSetting safeBlocks = new BoolSetting("Safe Blocks", true);
    private final BoolSetting silentAim = new BoolSetting("Silent Aim", true);
    private final FloatSetting fov = new FloatSetting("Silent Aim FOV", 180f, 0f, 180f, 1f);
    private final BoolSetting renderPath = new BoolSetting("Render Path", true);
    private final ColorSetting renderColor = new ColorSetting("Render Color", 131, 118, 252, 255);

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

    public AutoCart() {
        super(new FeatureInfo("Auto Cart", "Rails and TNT minecarts at your arrow's landing spot, with trigger, overload and auto-hit.", Category.COMBAT));
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
        com.utilitymod.echo.utils.ChatUtils.chat("§d[Auto Cart]§r " + msg);
    }

    private void fail(String msg) { note(msg); finish(); }

    private boolean aim(Vec3d point, BlockPos block) {
        return Cpvp.aimed(this, silentAim.getValue(), fov.getValue(), point, block);
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
        if (spot == null) { note("no spot to place on"); return; }
        if (!Cpvp.inReach(spot)) {
            note(String.format("landing is out of reach (%.1f blocks) - get closer or use Place Mode: Crosshair",
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
        if (placeMode.is("Crosshair")) {
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
        if (!Cpvp.inReach(railPos)) { fail("rail spot went out of reach"); return; }
        switch (step) {
            case RAIL -> {
                if (!mc.world.getBlockState(railPos).isAir() && !mc.world.getBlockState(railPos).isReplaceable()) { step = Step.CARTS; return; }
                int s = slotOf(AutoCart::rail);
                var hit = BlockUtils.findPlacementHit(railPos, false);
                if (s < 0) { fail("no rail in your hotbar"); return; }
                if (hit == null) { fail("nothing solid to put the rail on"); return; }
                Cpvp.select(s, "Swap");
                if (!aim(hit.hitResult().getPos(), hit.hitResult().getBlockPos())) { if (++aimTicks > 12) fail("couldn't aim at the rail spot (Silent Aim FOV?)"); return; }
                aimTicks = 0;
                if (Cpvp.place(railPos)) { step = Step.CARTS; wait = switchDelay.getValue(); }
                else fail("rail placement was refused");
            }
            case CARTS -> {
                if (cartsPlaced >= cartsWanted) { step = safeBlocks.getValue() ? Step.SAFE : fireAfter ? Step.FIRE : Step.BOW_BACK; return; }
                if (System.currentTimeMillis() < nextCartAt) return;
                int s = slotOf(st -> st.isOf(Items.TNT_MINECART));
                if (s < 0 && !overloadHotbarOnly.getValue() && cartsPlaced > 0) {
                    int src = Cpvp.findInventory(Items.TNT_MINECART, false);
                    if (src >= 0) { Cpvp.swapInto(src, Cpvp.selected()); s = Cpvp.selected(); }
                }
                if (s < 0) { if (cartsPlaced > 0) { step = Step.SAFE; return; } fail("no TNT minecart in your hotbar"); return; }
                if (!mc.world.getBlockState(railPos).isIn(net.minecraft.registry.tag.BlockTags.RAILS)) { fail("the rail isn't there (it didn't place or broke)"); return; }
                Cpvp.select(s, "Swap");
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(railPos).add(0, -.4, 0), Direction.UP, railPos, false);
                if (!aim(hit.getPos(), railPos)) { if (++aimTicks > 12) fail("couldn't aim at the rail (Silent Aim FOV?)"); return; }
                aimTicks = 0;
                if (!BlockUtils.interactWithBlock(hit, true).isAccepted()) { fail("cart placement was refused"); return; }
                {
                    cartsPlaced++;
                    nextCartAt = Cpvp.next(overloadDelay);
                    if (cartsPlaced >= cartsWanted) wait = switchDelay.getValue();
                }
            }
            case SAFE -> {
                BlockPos cover = coverSpot();
                int s = slotOf(st -> st.isOf(Items.OBSIDIAN) || st.isOf(Items.COBBLESTONE));
                if (cover != null && s >= 0) {
                    var hit = BlockUtils.findPlacementHit(cover, false);
                    if (hit != null) {
                        Cpvp.select(s, "Swap");
                        if (!aim(hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
                        Cpvp.place(cover);
                        wait = switchDelay.getValue();
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
            Cpvp.select(xbow, "Swap");
            float[] r = Ballistics.solve(point, Ballistics.CROSSBOW_SPEED, arc.getValue());
            if (r == null) { finish(); return; }
            if (shoot(r, false)) finish();
            return;
        }
        int bow = slotOf(st -> st.isOf(Items.BOW));
        if (bow < 0) { finish(); return; }
        Cpvp.select(bow, "Swap");
        if (!drawingBow()) { mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); return; }
        int charge = mc.player.getItemUseTime();
        float[] r = Ballistics.solve(point, Ballistics.bowSpeed(Math.max(charge, minCharge.getValue())), arc.getValue());
        if (r == null) { if (charge >= 20) finish(); return; }
        if (charge < minCharge.getValue()) { request(r); return; }
        if (shoot(r, true)) finish();
    }

    private void request(float[] r) {
        if (silentAim.getValue()) ServerRotations.request(this, r[0], r[1], .6f, 12);
    }

    /** Aim at {yaw,pitch}; once the server has it, release the bow / fire the crossbow with that look. */
    private boolean shoot(float[] r, boolean bow) {
        if (silentAim.getValue()) {
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
            if (silentAim.getValue()) { p.setYaw(yaw); p.setPitch(pitch); }
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
}
