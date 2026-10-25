package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import net.minecraft.block.Blocks;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Places rail, TNT cart and fire, then fires a charged crossbow through the flame.
 * Slot preparation, actions and cleanup are separated by normal movement updates.
 * Silent firing angles are retained through the next movement update.
 */
public class XbowCart extends Feature {

    private final KeybindSetting bind = new KeybindSetting(ModuleText.S0, GLFW.GLFW_KEY_X);
    private final ModeSetting bindMode = new ModeSetting(ModuleText.S1, ModuleText.S2, ModuleText.S3, ModuleText.S2);
    private final ModeSetting detonate = new ModeSetting(ModuleText.S4, ModuleText.S5, ModuleText.S5, ModuleText.S6);
    private final IntSetting handHold = new IntSetting(ModuleText.S7, 3, 0, 20, ModuleText.S8);
    private final BoolSetting silent = new BoolSetting(ModuleText.S9, true);
    private final FloatSetting silentFov = new FloatSetting(ModuleText.S10, 180f, 0f, 180f, 1f, o -> silent.getValue());
    private final com.utilitymod.echo.features.settings.impl.InputSimulationSetting inputSimulation =
            new com.utilitymod.echo.features.settings.impl.InputSimulationSetting(ModuleText.S35, false);

    private boolean toggled, wasBindDown, active;
    private int step, stepTick, oldSlot = -1;
    private BlockPos target, firePos;
    private int sequenceTicks;
    private boolean finishing, shotRotationPending;
    private float shotYaw, shotPitch;
    private int preparedSlot = -1;
    private int simItemCooldown;
    private String status = ModuleText.S14;
    @Override public CharSequence concat() { return status; }

    public XbowCart() {
        super(new FeatureInfo(ModuleText.S11, ModuleText.S12, Category.COMBAT));
        silentFov.describedBy(ModuleText.S13);
        detonate.describedBy(ModuleText.S15);
    }

    @Override
    public void onEnable() { super.onEnable(); reset(); toggled = false; }

    @Override
    public void onDisable() { super.onDisable(); reset(); if (RotationUtils.isControlledBy(this)) RotationUtils.stopTracking(); }

    private void reset() {
        if (active && oldSlot >= 0 && mc.player != null) InventoryUtils.setInvSlot(oldSlot);
        finishing = false; shotRotationPending = false; preparedSlot = -1;
        active = false; step = 0; stepTick = 0; oldSlot = -1; target = null; firePos = null; sequenceTicks = 0;
        simItemCooldown = 0;
    }

    private boolean bindHeld() { return CartPlacement.keyDown(bind.getKey()); }

    private boolean activationWanted() {
        boolean down = bindHeld() && mc.currentScreen == null;
        if (bindMode.is(ModuleText.S3)) { wasBindDown = down; return down; }
        if (down && !wasBindDown) toggled = !toggled;     // rising edge toggles
        wasBindDown = down;
        return toggled;
    }

    @EventSubscribe
    private void onInputComplete(EventHandleInput.Post event) {
        if (isNull()) { active=false; oldSlot=-1; target=null; toggled=false; return; }
        // Actions are dispatched at the return of vanilla's input handling, so every interact
        // rides ahead of this tick's movement packet (Grim's Post check flags actions that
        // arrive after the position packet in the same flush).
        if (finishing) { finish(); return; }
        if (mc.currentScreen != null || !mc.player.isAlive()) { finish(); return; }
        if (mc.player.isUsingItem() || mc.options.useKey.isPressed() || mc.options.attackKey.isPressed()) return;
        boolean want = activationWanted();
        if (!want) { if (active) reset(); if (RotationUtils.isControlledBy(this)) RotationUtils.stopTracking(); return; }

        if (!active) {
            target = aimBlock();
            if (target == null) { if (RotationUtils.isControlledBy(this)) RotationUtils.stopTracking(); return; }
            active = true; sequenceTicks=0; step = 0; stepTick = 0; status=ModuleText.S16; oldSlot = mc.player.getInventory().getSelectedSlot();
        }

        if (++sequenceTicks > 200) { status=ModuleText.S17 + status; finish(); return; }
        if (stepTick > 0) { stepTick--; return; }
        switch (step) {
            case 0 -> { if (CartPlacement.railAt(target) || placeAt(InventoryUtils.findItemWithPredicateInHotbar(this::isRail), target)) { status=ModuleText.S18; advance(); } }
            case 1 -> { if (placeAt(InventoryUtils.findItemWithPredicateInHotbar(s -> s.isOf(Items.TNT_MINECART)), target)) { status=ModuleText.S19; advance(); } }
            case 2 -> { if (prepareFire()) { status=ModuleText.S20; advance(); } }
            case 3 -> { if (shootThroughFire()) { status=ModuleText.S21; finishing=true; } }
            default -> finish();
        }
    }

    private void advance() { step++; stepTick = Math.max(1, handHold.getValue()); }

    private void finish() {
        toggled = false;                 // Toggle fires once per press
        reset();
        if (RotationUtils.isControlledBy(this)) RotationUtils.stopTracking();
    }

    private BlockPos aimBlock() {
        HitResult hr = mc.crosshairTarget;
        if (hr == null || hr.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult b = (BlockHitResult) hr;
        BlockPos pos = CartPlacement.target(b);
        if (!CartPlacement.supported(pos)) { status=ModuleText.S22; return null; }
        if (TargetUtils.friendNear(Vec3d.ofCenter(pos), 6.5)) return null;
        return pos;
    }

    private boolean isRail(ItemStack s) {
        return s.isOf(Items.RAIL) || s.isOf(Items.POWERED_RAIL) || s.isOf(Items.DETECTOR_RAIL) || s.isOf(Items.ACTIVATOR_RAIL);
    }

    private boolean placeAt(int slot, BlockPos pos) {
        if(slot<0) { status=ModuleText.S23; return false; }
        BlockHitResult hit=CartPlacement.hit(slot,pos);
        if(hit==null) { status=ModuleText.S24; return false; }
        if(inputSimulation.getValue()) return placeAtSim(pos,hit);
        if (!prepareSlot(slot) || !aimPoint(hit.getPos())) return false;
        sendInteractLook(hit.getPos());
        return CartPlacement.use(slot,hit);
    }

    /** Every interact must ride a look packet in the same flush (Grim Order/Post): send the aim
     *  angles explicitly instead of waiting on the movement relay that fires later in the tick. */
    private void sendInteractLook(Vec3d point) {
        Vec3d delta = point.subtract(mc.player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90f;
        float pitch = -(float) Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    /**
     * Simulated real-input placement for the current step: turns the actual camera, releases
     * the use key through the input pipe, and lets the step verify the spawned state
     * (rail block / cart entity / fire block) on the following movements.
     */
    private boolean placeAtSim(BlockPos pos, BlockHitResult hit) {
        if (step == 1 && placedCart() != null) return true;
        if (step == 2 && firePos != null) {
            var fireState = mc.world.getBlockState(firePos);
            if (fireState.isOf(Blocks.FIRE) || fireState.isOf(Blocks.SOUL_FIRE)) return true;
        }
        if (!RotationUtils.rotateTo(hit.getPos(), silentFov.getValue(), silentFov.getValue(), false,
                RotationUtils.AimType.REGULAR, this, EventSubscribe.Priority.HIGH)) return false;
        Vec3d delta = hit.getPos().subtract(mc.player.getEyePos());
        float wantedYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90f;
        float wantedPitch = -(float) Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));
        if (Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - wantedYaw)) > 2f
                || Math.abs(mc.player.getPitch() - wantedPitch) > 2f) return false;
        if (simItemCooldown-- > 0) return false;
        InputHandler.simulateClick(mc.options.useKey, inputSimulation);
        simItemCooldown = 3;
        return false;
    }

    private TntMinecartEntity placedCart() {
        for(var cart:mc.world.getEntitiesByClass(TntMinecartEntity.class,new Box(target).expand(0.75),e->e.isAlive())) return cart;
        return null;
    }

    /** Locate fire on the actual eye-to-cart line, never on the occupied rail cell. */
    private BlockPos fireOnPath(TntMinecartEntity cart, boolean existingOnly) {
        Vec3d eye=mc.player.getEyePos();
        Vec3d end=cart.getBoundingBox().getCenter();
        BlockPos candidate=null;
        for(int i=18;i>=2;i--) {
            BlockPos p=BlockPos.ofFloored(eye.lerp(end,i/20.0));
            if(p.equals(target) || mc.player.getBoundingBox().intersects(new Box(p))) continue;
            var state=mc.world.getBlockState(p);
            if(state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) return p;
            if(!existingOnly && candidate==null && state.isAir()
                    && mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world,p.down(),Direction.UP)) candidate=p;
        }
        return candidate;
    }

    private boolean prepareFire() {
        var cart=placedCart();
        if(cart==null) { status=ModuleText.S25; return false; }
        firePos=fireOnPath(cart,true);
        if(firePos!=null) return true;
        if(detonate.is(ModuleText.S6)) { status=ModuleText.S26; return false; }
        int slot=InventoryUtils.findItemWithPredicateInHotbar(s->s.isOf(Items.FLINT_AND_STEEL));
        if(slot<0) { status=ModuleText.S27; return false; }
        firePos=fireOnPath(cart,false);
        if(firePos==null) { status=ModuleText.S28; return false; }
        return placeAt(slot,firePos);
    }

    private boolean shootThroughFire() {
        var cart=placedCart();
        if(cart==null) { status=ModuleText.S29; return false; }
        BlockPos flame=fireOnPath(cart,true);
        if(flame==null) { status=ModuleText.S30; return false; }
        int slot=InventoryUtils.findItemWithPredicateInHotbar(s->s.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(s));
        if(slot<0) { status=ModuleText.S31; return false; }
        Vec3d point=cart.getBoundingBox().getCenter();
        var obstruction=mc.world.raycast(new net.minecraft.world.RaycastContext(mc.player.getEyePos(),point,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,mc.player));
        if(obstruction.getType()!=HitResult.Type.MISS) { status=ModuleText.S32; return false; }
        if(!aimPoint(point)) { status=ModuleText.S33; return false; }
        Vec3d delta=point.subtract(mc.player.getEyePos());
        float wantedYaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90f;
        float wantedPitch=-(float)Math.toDegrees(Math.atan2(delta.y,delta.horizontalLength()));
        if(inputSimulation.getValue()) {
            // Real-camera aim so the simulated use click fires the charged crossbow itself.
            if (!RotationUtils.rotateTo(point,silentFov.getValue(),silentFov.getValue(),false,RotationUtils.AimType.REGULAR,this,EventSubscribe.Priority.HIGH)) return false;
            if(Math.abs(MathHelper.wrapDegrees(mc.player.getYaw()-wantedYaw))>2f || Math.abs(mc.player.getPitch()-wantedPitch)>2f) { status=ModuleText.S34; return false; }
            if (!prepareSlot(slot)) return false;
            // The crossbow fires on press; once it uncharges the shot went through and we finish.
            if (!CrossbowItem.isCharged(mc.player.getMainHandStack())) { finishing = true; return true; }
            if (simItemCooldown-- > 0) return false;
            InputHandler.simulateClick(mc.options.useKey, inputSimulation);
            simItemCooldown = 3;
            return false;
        }
        float yaw=silent.getValue()?RotationUtils.getSilentYaw():mc.player.getYaw();
        float pitch=silent.getValue()?RotationUtils.getSilentPitch():mc.player.getPitch();
        if(Math.abs(MathHelper.wrapDegrees(yaw-wantedYaw))>2f || Math.abs(pitch-wantedPitch)>2f) { status=ModuleText.S34; return false; }
        if (!prepareSlot(slot)) return false;
        float previousYaw=mc.player.getYaw(), previousPitch=mc.player.getPitch();
        try {
            // interactItem carries the firing angles; send them on a look packet first (Grim
            // Order/Post) and restore the camera immediately for silent aim.
            if(silent.getValue()) { mc.player.setYaw(yaw); mc.player.setPitch(pitch); }
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            boolean accepted = mc.interactionManager.interactItem(mc.player,Hand.MAIN_HAND).isAccepted();
            // Even a rejected use can emit a packet; keep its angles through the next movement.
            shotYaw = yaw; shotPitch = pitch; shotRotationPending = true;
            return accepted;
        } finally {
            if(silent.getValue()) { mc.player.setYaw(previousYaw); mc.player.setPitch(previousPitch); }
        }
    }

    /** Select now, then allow vanilla slot synchronization and a movement boundary before use. */
    private boolean prepareSlot(int slot) {
        if (slot < 0) return false;
        if (preparedSlot != slot || mc.player.getInventory().getSelectedSlot() != slot) {
            InventoryUtils.setInvSlot(slot);
            preparedSlot = slot;
            return false;
        }
        return true;
    }

    private boolean aimPoint(Vec3d point) {
        return RotationUtils.rotateTo(point,silentFov.getValue(),silentFov.getValue(),
                silent.getValue(),RotationUtils.AimType.REGULAR,this,EventSubscribe.Priority.HIGH);
    }

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (isNull()) return;
        if (shotRotationPending) {
            event.setYaw(shotYaw);
            event.setPitch(shotPitch);
            shotRotationPending = false;
            return;
        }
        if (!silent.getValue() || !RotationUtils.isControlledBy(this)) return;
        if (!RotationUtils.hasSilentRotation()) return;
        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    // Generated literals use the existing EncryptedString decoder. Keep decoded values stable.
    private static final class ModuleText {
        private static final String S0 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95f6,0x9aca,0x5175,0x2147}, new char[]{0x9a88,0x511c,0x9598,0x2123,0x66ca,0x8bf0,0xbea0,0x8482}).toString();
        private static final String S1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb971,0xfde2,0x93e6,0x8ff1,0xcb57,0xb956,0xa634,0xfda3,0x6c37}, new char[]{0xb933,0x938f,0xfdcd,0xa650,0xfdc2,0x6c7a,0x8f9e,0xcb33}).toString();
        private static final String S2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb0a7,0x2a50,0xad86,0x664b,0x39eb,0x9220}, new char[]{0x39bf,0xade9,0x2a37,0xb0c0,0x6627,0x9245,0x1831,0x71a0}).toString();
        private static final String S3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x72b9,0x2db5,0x69e2,0x84f6}, new char[]{0x69aa,0x72d6,0x2dd9,0x8492,0xc6ee,0xef90,0xd804,0x43af}).toString();
        private static final String S4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ea1,0x758c,0x5465,0x078d,0x0e6f,0x5da0,0xe10f,0x10d5}, new char[]{0x1091,0x5dc5,0xe17b,0x75e3,0x3ecf,0x5404,0x07f9,0x0e0a}).toString();
        private static final String S5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb8d3,0xe553,0x33a6,0xe54b,0x105f,0x0e4c,0xfbd1,0x33b3,0xb8cb,0x79d6,0x36fe,0x0e40,0x36f5}, new char[]{0x33f5,0xe53f,0x0e25,0x3690,0xb8bf,0xfbf1,0x1079,0x79f6}).toString();
        private static final String S6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x86ab,0x245e,0xb114,0xe03f,0xcf15,0x3b67,0x00b5,0x269e}, new char[]{0x86e8,0x00c7,0x3b08,0x242d,0xb167,0xcf77,0xe050,0x26e9}).toString();
        private static final String S7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5bce,0x4248,0x3473,0x8de6,0xdfa0,0x0091,0xfc3f,0x96d5,0x4245,0xfc24,0x3445,0x8dee}, new char[]{0x3420,0xfc48,0x8d87,0x4231,0x5bad,0x96bd,0xdf80,0x00d5}).toString();
        private static final String S8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9d54,0xba3a,0xddec,0x880e,0xa148,0x5c67}, new char[]{0x882e,0x9d20,0xdd85,0xba59,0xa123,0x5c14,0xa16d,0x75b9}).toString();
        private static final String S9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x515f,0x4a1c,0x5165,0x8dc1,0x10a3,0x33e4,0x1615,0xbf03,0x098a,0x1611}, new char[]{0x5136,0x167c,0x3388,0x09ef,0xbf6d,0x4a68,0x1083,0x8d80}).toString();
        private static final String S10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb9a5,0x80ea,0x3ffa,0xb986,0x051d,0x7ba1,0x3fd8,0x369c,0x36a6,0x7ba5,0x3caa,0x3ce6,0x46a9,0x053c}, new char[]{0x36f5,0x7bc8,0x3c8a,0xb9e3,0x0552,0x3f8e,0x80ca,0x46e8}).toString();
        private static final String S11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0ca5,0x1d99,0x1db5,0xf38d,0xfcec,0xffde,0x3788,0x8887,0x6332}, new char[]{0x1dc1,0xffbc,0x37e7,0x0cd2,0x88a7,0x6371,0xf3ec,0xfc9e}).toString();
        private static final String S12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x15eb,0xeba0,0x98cc,0x98ce,0x15aa,0x32b3,0xebe5,0xd0cb,0x98c1,0xebe3,0x5a7c,0x9888,0xb20a,0xb1c9,0xb210,0x32b9,0x15eb,0x5a79,0xb202,0xebe3,0x98da,0x15aa,0xb1c4,0xb1da,0xd087,0xb1d9,0xebf3,0xb201,0xb1c8,0xd098,0xd09f,0x32b6,0xd084,0x32b6,0x15e4,0x15ef,0x15aa,0xb18d,0xb1c8,0x5a74,0x98dc,0xd099,0xeba0,0x5a70,0x15e5,0x32b8,0xebe3,0xb206,0x98db,0xd0cb,0xb243,0x98f8,0xebf4,0xb1c8,0x32a0,0x98dc,0x5a70,0x5a79,0x32a3,0xb202,0xb20d,0xd087,0x5a6b,0x32b9,0x32b4,0xd08c,0x5a38,0xb20a,0xd086,0xb243,0x5a6a,0xb18d,0x32b9,0xb1d9,0x15f8,0x98c7,0xebe5}, new char[]{0x98a8,0xd0eb,0xb263,0x32d7,0xb1ad,0xeb80,0x5a18,0x158a}).toString();
        private static final String S13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b43,0xa9bc,0x0477,0x9e23,0x130c,0xa9a6,0x6b04,0x1302,0xeb7d,0x1349,0x9e23,0x047f,0xeb75,0x9e64,0xa9a6,0xf55e,0x9e77,0xb8ed,0xa9ab,0xa9a9,0xb8bd,0x1319,0x6b4b,0xf54b,0x6b03,0xa9e1,0xf55c,0x6b0a,0xeb76,0x9e6f,0xf55a,0xf551,0x047b,0x0467,0x046c,0x047b,0xb8ee,0x045d,0x131d,0xb8bd,0x6b42,0x9e70,0xb8ee,0xb8e9,0x1349,0xf55e,0xeb63,0x6b0e,0xf54b,0xeb30,0xeb79,0xa9bd,0xb8bd,0x131e}, new char[]{0x041e,0xf53f,0x1369,0xb89d,0x6b6b,0xa9cf,0xeb10,0x9e03}).toString();
        private static final String S14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3640,0xbbcc,0x79d1,0x395d,0xbd4b,0x77fc,0x394b,0x365a,0xbbd6,0x77ae,0xf160,0x365d,0x395a,0x77fc,0xf124,0xf163,0x79f3,0xbbcd,0xc6e7,0xbd46,0x79c6,0xc6f4}, new char[]{0x79a3,0x77dc,0x393f,0xbbbf,0x3633,0xf104,0xc693,0xbd2e}).toString();
        private static final String S15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfd6c,0xe3d3,0x1767,0xe3d3,0xe3d7,0xe3da,0xdefc,0xcb3e,0xfd6c,0x3d77,0xe3cc,0xbd43,0xcb32,0xe39b,0xbd0a,0x3d6d,0xbd43,0x173c,0xe3cb,0xfd0a,0xdee0,0x1732,0xe3cf,0x325a,0xdefd,0x3d61,0x324f,0xfd20,0xfd2f,0xfd62,0x3251,0xbd59,0xdefc,0xcb2a,0xdeae,0x3d64,0x3d23,0xe3d2,0x3d64,0xfd29,0x321f,0xbd0b,0x1733,0xfd1f,0xe3c8,0xbd0b,0x3d75,0x321f,0xfd38,0xbd17,0x1734,0xfd6c,0xdeae,0x1734,0x3d60,0x173e,0x321f,0x324b,0xbd4d,0xcb33,0x3d76,0xdeeb,0xdeeb,0x173a,0xcb66,0xdee1,0x1735,0x325e,0xbd10,0x3248,0x1738,0x324d,0xdefa,0xe39b,0x3d76,0x325a,0x177d,0x3d25,0xcb20,0xdeae,0xcb21,0x321f,0xfd6c,0xbd05,0xcb32,0xcb32,0xdeeb,0xbd43,0x173c,0xbd01,0xe3dd,0xe3c9,0x1771,0x324b,0xbd0a,0x325d,0xcb23,0xe3d5,0x1734,0xdefc,0xbd06,0x3d6a,0xcb2e,0xcb29,0xdee0,0xe3d5,0xcb32,0x171e,0x3d60,0x3d6c,0xdeae,0xfd2d,0xbd10,0xfd25,0xfd23,0xcb66,0xfd2f,0xcb35}, new char[]{0xfd4c,0xe3bb,0x175d,0xde8e,0xcb46,0xbd63,0x3d05,0x323f}).toString();
        private static final String S16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdd18,0xf074,0xe33c,0x2ef2,0xbabf,0xf07b,0xe331,0xdd10,0xaaa7,0xba9d,0x4a63,0x9946}, new char[]{0xbacd,0xe35d,0xdd79,0xf017,0x992f,0x2e9c,0xaac0,0x4a43}).toString();
        private static final String S17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdcd7,0x2e54,0xf8cd,0xdccf,0xc751,0xe582,0xc751,0x28f2,0x63ca,0x639f,0x2881,0x63d2,0x58b3,0x28d4,0xdc84,0xc740,0xe5c1,0xf8cd,0x2e5e,0x58b9}, new char[]{0x28a1,0xc725,0xdcbe,0x63bf,0xf8a8,0x58dd,0xe5a2,0x2e3b}).toString();
        private static final String S18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x13b5,0x9574,0xd371,0x6f3b,0x957b,0x6f28,0xe014,0x9315,0x2fcc,0x13b4,0xee55,0x6f20,0xe01d,0x2fdd,0x9315,0xd338,0xee58,0x2fee,0x956c,0xee58}, new char[]{0x2fbe,0xee39,0x6f49,0x9518,0xd351,0x13db,0xe073,0x9335}).toString();
        private static final String S19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4f1d,0x2737,0x983f,0xc9e8,0x951a,0xd7aa,0x4f03,0xc9ec,0x9824,0xa665,0x21fa,0x21f0,0xc9e6,0x4f08,0x2738,0x952b,0x272d,0xd7fe,0x9823,0x955c,0x21fa,0xd7fe,0xa631}, new char[]{0x957c,0xc989,0x984d,0xd78a,0x2193,0x2759,0x4f7a,0xa611}).toString();
        private static final String S20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7dbb,0x7d8a,0xe021,0xd771,0xc721,0x1ebb,0x143c,0xd77e,0x1430,0x143a,0x1ea9,0xc73a,0xe02f,0x1427,0xd763,0x6263,0x1eb2,0xe022,0x627f,0x6276,0x1efd,0xe028,0x959d,0xc73d,0x95d8,0xc73a,0x95da,0x959d,0x7dfd,0x6274,0xd778,0x7dae}, new char[]{0x7ddd,0xe040,0x1455,0x1edd,0xd717,0xc753,0x6211,0x95bd}).toString();
        private static final String S21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08c1,0xcd7f,0x08c0,0x8a2a,0x21fb,0x7819,0xb324,0x08c2,0xcd1c,0xfedc,0xb337,0xfecc,0x21f5,0x1ae9,0xcd4f,0x7813,0x1aee,0x1af5,0x7812,0xfedb,0x8a20,0xb329,0x21fc,0x8a62}, new char[]{0xcd3c,0x08b2,0x2194,0x1a9a,0xfeaf,0x8a42,0x7876,0xb340}).toString();
        private static final String S22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2914,0xc6dd,0x8cb8,0x8cf7,0x74a6,0xc6c0,0xda04,0x8ca5,0xe18b,0xda11,0xe1c6,0xda11,0xe1d8,0x09ca,0xe1de,0x74bc,0xc6db,0x1601,0x74e8,0xda00,0x74a9,0x290f,0x09d6,0x09d0,0x8cbb,0x1630,0x295d,0x290d,0x1660,0x09dd,0x1625,0xc6dd}, new char[]{0x1640,0x09b9,0xe1ab,0x297d,0xda70,0xc6b4,0x8cd7,0x74c8}).toString();
        private static final String S23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe0d8,0x8475,0xe100,0x2683,0xd713,0xd70f,0xd712,0x8454,0x26c7,0xed7a,0x983b,0xd71e,0xe0c3,0xe118,0x26d0,0xed33,0xe10a,0xe0d4,0x3e40,0x8426,0x846f,0xed38,0x3e5f,0x9824,0x9825,0x3e53,0x982f,0xe119}, new char[]{0x8406,0xd77b,0x984a,0xe16d,0xed5a,0x3e32,0xe0b1,0x26a3}).toString();
        private static final String S24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc005,0xd451,0xd410,0xcbe5,0x1e55,0xd456,0xda82,0x1e1b,0x1784,0xc012,0x1783,0xcbff,0x1785,0xc007,0x1788,0xc014,0xc014,0xcbe9,0xd445,0x0e79,0xcbe9,0xdadc,0xdacb,0x179f,0xdac2,0x1799,0xb351,0x0e7a,0xda8e,0xb34a,0xd451,0x0e7f,0x0e72,0xcbe9,0x1e57,0xb340,0x1e1b,0x0e79,0xb305,0xb344,0xc010,0x1e55,0x1e48,0x0e7e,0xd467,0xcbeb,0xb348}, new char[]{0xd430,0xcb8a,0x17ed,0xb325,0x0e1b,0x1e3b,0xc060,0xdaae}).toString();
        private static final String S25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6847,0x308e,0xfff5,0xe10a,0xe104,0x400d,0xb2aa,0xe100,0xa0a5,0xa0a3,0x30d8,0x4011,0x6850,0xa0ba,0xb2aa,0xbf1e,0xbf03,0xfff7,0xbf0b,0x6850,0x308c,0xb2fe,0x4029,0xb2fe,0xe10d,0xa0ac,0xfffc,0xe145,0xffe0,0x684b,0xa0bf,0x6851,0xb2fe,0xffb2,0x4018,0xbf09,0x3088,0xbf0f,0x30d8,0x400a}, new char[]{0x407e,0xe165,0x6822,0x30f8,0xbf6a,0xa0cd,0xff92,0xb2de}).toString();
        private static final String S26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c26,0x3d01,0x2880,0xb718,0x2c55,0xfe8d,0x18b7,0x7c21,0xfe8d,0x7c2c,0x3d01,0xfd64,0xfd72,0x3d16,0x2884,0xfed5,0x2c55,0x3d13,0xb714,0x2c43,0xfd65,0x1886,0xfd69,0xb710,0x3d17,0xb718,0xfd66,0x2c45,0x2899,0x18e5,0xfe8d,0xfecb,0xfeda,0xb71f,0x18a0,0xfd21,0x3d05,0x28cd,0x7c3b,0x2c44,0x7c37,0x2c10,0x3d0a,0x18a1,0x289f,0x2889,0x18b1,0x18ab,0x2c55,0x28cd,0x28cd,0xb712,0xb71e,0x18a0,0x7c3a,0x7c3a,0xfd6e,0xfecf}, new char[]{0x18c5,0x28ed,0x7c55,0xfd01,0x2c30,0xfead,0xb771,0x3d64}).toString();
        private static final String S27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x26de,0xc050,0x26d0,0xbb76,0xa427,0xbabd,0x3f5f,0x3f42,0xa434,0xcf5d,0xbae9,0xbaf3,0xa466,0x2dec,0x2db8,0xc015,0xa42a,0xbb6f,0x2da5,0xcf5b,0xcf54,0x3f43,0xa42f,0xbabd,0xcf5f,0x26d8,0xbb71,0x2da9,0xbb6c,0x3f59,0xcf7f,0x3f43,0xc01e,0x26d3,0xc018}, new char[]{0xcf39,0xa446,0x3f30,0xbb02,0x2dcc,0xc070,0x26bf,0xba9d}).toString();
        private static final String S28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfcd6,0xe5c4,0xe590,0x6c50,0x6c59,0xe58b,0xfc83,0x8735,0xfc97,0xfa13,0x14de,0x14cc,0x6c4f,0x877a,0xd889,0x1c76,0x1c75,0x1c33,0xd89c,0xd89c,0xfc82,0x148a,0x14da,0xfa50,0xd890,0xd897,0x14d9,0x872e,0xfa41,0x14ce,0x873b,0x1c61,0x1c60,0xfa56,0x8734,0xe596,0x6c5c,0xfa5a,0x6c57,0xfa7d,0x8735,0x1c33,0xfc84,0xe5c4}, new char[]{0xfa33,0x875a,0x1c13,0x14aa,0xfcf6,0x6c3f,0xd8f9,0xe5e4}).toString();
        private static final String S29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ba3,0xc539,0x1f73,0x1b92,0xc60e,0x0ab4,0xa2e6,0x57b4,0xc522,0x57a1,0x45ff,0x0ae0,0x1f7c,0x45f5,0xc600,0xa2b2}, new char[]{0x1bf4,0xc66f,0xc550,0xa2c6,0x4596,0x1f1d,0x57d3,0x0ac0}).toString();
        private static final String S30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6649,0xd702,0x6643,0x5ebd,0x673b,0x6647,0x664f,0x673a,0x5ef4,0xc9fe,0x21f0,0x59d2,0xc9d0,0xd705,0x5981,0xdabc,0xd756,0x673d,0x5ebc,0xdaba,0xdabc,0xdae8,0x59c9,0xc9f8,0x59c4,0xc9e6,0x21a4,0x21f0}, new char[]{0xc996,0x6626,0xdac8,0x59a1,0x5ed4,0x6754,0xd776,0x21d0}).toString();
        private static final String S31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x42ce,0x40a7,0xb404,0x751f,0x40ab,0x4fc3,0xb467,0x42cb,0x9778,0xb424,0xef30,0x40ac,0x972f,0xba0a,0xba04,0xb467,0x42d4,0x7514,0xba17,0x42c4,0x7518,0x42c0,0xba04,0x4fd7,0xef31,0x4fd7,0x4fc9,0xef2c,0x9737,0x755d,0xef31,0x40ad,0xb433,0x973f,0xef30,0xba0c}, new char[]{0xb447,0x42a6,0xba65,0xef43,0x4fa4,0x757d,0x40c3,0x9758}).toString();
        private static final String S32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f7a,0xeeb8,0x76e7,0xeeec,0x76f8,0x0515,0xb672,0x3e6d,0xd3e0,0xeeb8,0x3e66,0x3f66,0x05e0,0x05b2,0x3e68,0xd3e7,0x3e6b,0x05f0,0x76fa,0xeedb,0x0512,0x0512,0x76ed,0xb666,0xb662,0xd3fc,0x3f60}, new char[]{0xee98,0x7688,0x3e09,0x0561,0xd394,0x0592,0x3f0f,0xb611}).toString();
        private static final String S33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x41e7,0x046a,0x7d1a,0x9ea7,0x9ea6,0x41d6,0x2379,0x7d01,0x965b,0xff8d,0x965b,0x72a0,0xff83,0x72a6,0xff8d,0x2379,0x9eac,0x960f,0x41d3,0x0462,0x72a1,0x0479,0x7d1d}, new char[]{0x41b0,0xffe2,0x7d73,0x962f,0x040b,0x72cf,0x9ecb,0x2359}).toString();
        private static final String S34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf559,0x620c,0xafd5,0x6240,0x5190,0x0759,0xafc2,0xf55f,0x0753,0xafd9,0x625c,0xf55e,0x074f,0x621e,0x51d3,0xf551,0x625d,0x45f6,0x92e8,0x45fe,0x0755,0x92f7,0x6211,0x620a,0x92bf,0x519f,0x92de,0x625c,0x45f6,0x51d3}, new char[]{0x929f,0x51f3,0x6278,0xf536,0x622e,0x073c,0xafb7,0x4591}).toString();
        private static final String S35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdfa7,0x42b1,0xdf84,0x77a8,0x77e6,0x1333,0x132a,0x1317,0x42ad}, new char[]{0x135e,0x77c6,0xdfd7,0x42c4}).toString();
    }
}
