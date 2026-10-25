package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import com.utilitymod.echo.utils.simulation.BowSimulation;
import dev.koifih.client.render.Draw;
import dev.koifih.client.util.Colors;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

/**
 * Cart-mode combat helper: places rails and TNT minecarts (at the crosshair or at the predicted
 * arrow landing) and can silent-aim a flame bow at a placed cart and auto-release it at full charge.
 * Silent Aim keeps every aim server-side so the camera never moves.
 */
public class Locomotive extends Feature {

    private final IntSetting handHold = new IntSetting(ModuleText.S0, 0, 0, 20, ModuleText.S1);
    private final IntSetting bowBackDelay = new IntSetting(ModuleText.S2, 0, 0, 20, ModuleText.S1);
    private final IntSetting minCharge = new IntSetting(ModuleText.S3, 1, 1, 20, ModuleText.S1);
    private final FloatSetting arc = new FloatSetting(ModuleText.S4, 20f, 0f, 100f, 1f);
    private final IntSetting overload = new IntSetting(ModuleText.S6, 1, 1, 10, ModuleText.S7);
    private final ModeSetting placeMode = new ModeSetting(ModuleText.S8, ModuleText.S9, ModuleText.S9, ModuleText.S10);
    private final BoolSetting onBowShot = new BoolSetting(ModuleText.S11, true);
    private final BoolSetting autoHit = new BoolSetting(ModuleText.S12, false);
    private final FloatSetting autoHitRange = new FloatSetting(ModuleText.S13, 24f, 1f, 60f, 0.5f, o -> autoHit.getValue());
    private final IntSetting autoHitMinCharge = new IntSetting(ModuleText.S14, 20, 1, 20, o -> autoHit.getValue());
    private final BoolSetting switchBackToBow = new BoolSetting(ModuleText.S15, true);
    private final BoolSetting safeBlocks = new BoolSetting(ModuleText.S16, true);
    private final BoolSetting silent = new BoolSetting(ModuleText.S17, true);
    private final FloatSetting silentFov = new FloatSetting(ModuleText.S18, 180f, 0f, 180f, 1f, o -> silent.getValue());
    private final BoolSetting renderPath = new BoolSetting(ModuleText.S19, false);
    private final com.utilitymod.echo.features.settings.impl.InputSimulationSetting inputSimulation =
            new com.utilitymod.echo.features.settings.impl.InputSimulationSetting(ModuleText.S32, false);

    public Locomotive() {
        super(new FeatureInfo(ModuleText.S20, ModuleText.S21, Category.COMBAT));
        arc.describedBy(ModuleText.S22);
        overload.describedBy(ModuleText.S23);
        autoHitMinCharge.describedBy(ModuleText.S24);
        silentFov.describedBy(ModuleText.S25);
    }

    // placement queue -------------------------------------------------------
    private BlockPos pending;    // landing block to build a cart stack on
    private int phase;           // 0 rail, 1 carts, 2 safe block, 3 bow-back, 4 done
    private int phaseTick, cartsLeft, oldSlot = -1;
    private boolean prevUsingBow;
    private int releaseCharge, queueTicks, awaitingArrowTicks;
    private int simItemCooldown;
    private final Set<Integer> seenArrows = new HashSet<>();
    private String status = ModuleText.S26;
    @Override public CharSequence concat() { return status; }

    @Override
    public void onEnable() { super.onEnable(); clearQueue(); seenArrows.clear(); awaitingArrowTicks=0; prevUsingBow=false; }

    @Override
    public void onDisable() { super.onDisable(); clearQueue(); seenArrows.clear(); awaitingArrowTicks=0; prevUsingBow=false; if (RotationUtils.isControlledBy(this)) RotationUtils.stopTracking(); }

    private void clearQueue() {
        if (oldSlot >= 0 && mc.player != null) InventoryUtils.setInvSlot(oldSlot);
        pending = null; phase = 0; phaseTick = 0; cartsLeft = 0; oldSlot = -1;
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) { pending=null; oldSlot=-1; prevUsingBow=false; awaitingArrowTicks=0; seenArrows.clear(); return; }
        if (mc.currentScreen != null) { clearQueue(); awaitingArrowTicks=0; prevUsingBow=false; return; }

        boolean usingBow = mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.BOW);
        int charge = usingBow ? mc.player.getItemUseTime() : 0;

        // 1) Snap Shot: silent-aim a drawn bow at the nearest placed TNT cart and release at min charge.
        TntMinecartEntity cart = (autoHit.getValue() && !aimingAtLiving()) ? nearestCart() : null;
        boolean aiming = false;
        if (usingBow && cart != null) {
            aimAt(cart.getBoundingBox().getCenter());
            aiming = true;
            if (charge >= autoHitMinCharge.getValue()) mc.interactionManager.stopUsingItem(mc.player);
        }

        // Wait for the real fired arrow: never recompute from the post-release crosshair.
        if (onBowShot.getValue() && prevUsingBow && !usingBow && pending == null
                && releaseCharge >= minCharge.getValue() && mc.player.getMainHandStack().isOf(Items.BOW)) {
            awaitingArrowTicks = 40;
        }
        observeShot();
        prevUsingBow = usingBow;
        if (usingBow) releaseCharge = charge;

        // While a placement is queued, face it server-side so each interact passes as looked-at.
        if (pending != null) aiming = true;

        // keep the camera still while any silent aim is engaged
        if (!aiming && RotationUtils.isControlledBy(this)) RotationUtils.stopTracking();

        processQueue();
    }

    private void observeShot() {
        Set<Integer> live = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ArrowEntity || entity instanceof SpectralArrowEntity)) continue;
            ProjectileEntity arrow = (ProjectileEntity) entity;
            if (arrow.getOwner() != mc.player) continue;
            live.add(arrow.getId());
            if (!seenArrows.contains(arrow.getId()) && awaitingArrowTicks > 0 && arrow.age <= 40
                    && arrow.getVelocity().lengthSquared() > 0.001 && pending == null) {
                BlockHitResult landing = new BowSimulation(arrow).simulateUntilHit(200);
                if (landing != null && landing.getType() == HitResult.Type.BLOCK) {
                    queue(CartPlacement.target(landing));
                }
                awaitingArrowTicks = 0;
            }
        }
        seenArrows.clear();
        seenArrows.addAll(live);
        if (awaitingArrowTicks > 0) awaitingArrowTicks--;
    }

    private void queue(BlockPos landing) {
        if (landing == null || !CartPlacement.supported(landing) || TargetUtils.friendNear(Vec3d.ofCenter(landing), 6.5)) return;
        status = ModuleText.S27; queueTicks = 0; pending = landing; phase = 0; phaseTick = 0; cartsLeft = Math.max(1, overload.getValue());
        oldSlot = mc.player.getInventory().getSelectedSlot();
    }

    private void processQueue() {
        if (pending == null) return;
        if (++queueTicks > 160) { status=ModuleText.S28; clearQueue(); return; }
        if (phaseTick > 0) { phaseTick--; return; }
        switch (phase) {
            case 0 -> { if (CartPlacement.railAt(pending) || placeItem(InventoryUtils.findItemWithPredicateInHotbar(this::isRail), pending)) { status=ModuleText.S29; step(); } }
            case 1 -> {
                int slot = InventoryUtils.findItemWithPredicateInHotbar(s -> s.isOf(Items.TNT_MINECART));
                if (placeItem(slot, pending)) { if (--cartsLeft <= 0) step(); else phaseTick = handHold.getValue(); }
                else status=ModuleText.S30;
            }
            case 2 -> { if (!safeBlocks.getValue() || placeSafeBlock()) step(); }
            case 3 -> {
                if (switchBackToBow.getValue()) {
                    int bow = InventoryUtils.findItemWithPredicateInHotbar(s -> s.isOf(Items.BOW));
                    if (bow >= 0) { InventoryUtils.setInvSlot(bow); oldSlot = -1; }
                } else if (oldSlot >= 0) InventoryUtils.setInvSlot(oldSlot);
                phase = 4; phaseTick = bowBackDelay.getValue(); status=ModuleText.S31;
            }
            default -> clearQueue();
        }
    }

    private void step() { phase++; phaseTick = handHold.getValue(); }

    // placement -------------------------------------------------------------
    private boolean placeItem(int slot, BlockPos pos) {
        BlockHitResult hit = CartPlacement.hit(slot, pos);
        if (hit == null) return false;
        if (inputSimulation.getValue()) return placeItemSim(slot, pos, hit);
        if (!RotationUtils.rotateTo(hit.getPos(), silentFov.getValue(), silentFov.getValue(),
                true, RotationUtils.AimType.REGULAR, this, EventSubscribe.Priority.HIGH)) return false;
        Vec3d delta = hit.getPos().subtract(mc.player.getEyePos());
        float wantedYaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x))-90f;
        float wantedPitch = -(float)Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));
        float yaw = RotationUtils.getSilentYaw(), pitch = RotationUtils.getSilentPitch();
        if (Math.abs(MathHelper.wrapDegrees(yaw-wantedYaw)) > 2f || Math.abs(pitch-wantedPitch) > 2f) return false;
        // Send the placement rotation before the use packet without moving the camera.
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        return CartPlacement.use(slot, hit);
    }

    /**
     * Simulated real-input placement: turns the actual camera instead of sending silent
     * rotations, releases the use key through the input pipe, and lets each queue phase
     * verify the spawned state (rail block / cart entity) on the following ticks.
     */
    private boolean placeItemSim(int slot, BlockPos pos, BlockHitResult hit) {
        if (slot < 0) return false;
        if (phase == 1 && cartAt(pos)) return true;
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            InventoryUtils.setInvSlot(slot);
            simItemCooldown = 1;
            return false;
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

    private boolean cartAt(BlockPos pos) {
        return !mc.world.getEntitiesByClass(TntMinecartEntity.class,
                new Box(pos).expand(0.75), Entity::isAlive).isEmpty();
    }

    private boolean placeSafeBlock() {
        int slot = InventoryUtils.findItemWithPredicateInHotbar(s -> s.getItem() instanceof BlockItem
                && mc.world.getBlockState(BlockPos.ofFloored(mc.player.getEyePos())).isAir());
        if (slot < 0) return true; // nothing to place with; don't stall
        Vec3d toCart = Vec3d.ofCenter(pending).subtract(mc.player.getEyePos());
        BlockPos between = BlockPos.ofFloored(mc.player.getEyePos().add(toCart.normalize()));
        if (!mc.world.getBlockState(between).isAir()) return true;
        return placeItem(slot, between);
    }

    // targeting -------------------------------------------------------------
    private TntMinecartEntity nearestCart() {
        double r = autoHitRange.getValue(), best = r * r; TntMinecartEntity found = null;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof TntMinecartEntity tnt)) continue;
            double d = mc.player.squaredDistanceTo(e);
            if (d <= best && !TargetUtils.friendNear(tnt.getBoundingBox().getCenter(), 6.5)) { best = d; found = tnt; }
        }
        return found;
    }

    private BlockPos predictedLanding(int charge) {
        float pt = mc.getRenderTickCounter().getTickProgress(false);
        BlockHitResult hit = new BowSimulation(mc.player, Math.max(1, charge), pt).simulateUntilHit(200);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) return CartPlacement.target(hit);
        return crosshairBlock();
    }

    /** True when you are looking at a living entity, so Snap Shot must not pull your aim to a cart. */
    private boolean aimingAtLiving() {
        return mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult e
                && e.getEntity() instanceof net.minecraft.entity.LivingEntity;
    }

    private BlockPos crosshairBlock() {
        HitResult hr = mc.crosshairTarget;
        if (hr != null && hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult b = (BlockHitResult) hr;
            return CartPlacement.target(b);
        }
        return null;
    }

    /** Direct silent aim at a block to place on (no arc lob). */
    private void aimPlace(BlockPos pos) {
        RotationUtils.rotateTo(Vec3d.ofCenter(pos), silentFov.getValue(), silentFov.getValue(),
                silent.getValue(), RotationUtils.AimType.REGULAR, this, EventSubscribe.Priority.HIGH);
    }

    private void aimAt(Vec3d point) {
        // Arc lobs the shot upward; 0 keeps the native line, 100 adds a strong upward bias.
        Vec3d aim = point.add(0, arc.getValue() / 100.0 * point.subtract(mc.player.getEyePos()).horizontalLength() * 0.5, 0);
        RotationUtils.rotateTo(aim, silentFov.getValue(), silentFov.getValue(),
                silent.getValue(), RotationUtils.AimType.REGULAR, this, EventSubscribe.Priority.HIGH);
    }

    private boolean bowHasFlame() {
        ItemStack bow = mc.player.getActiveItem();
        if (!bow.isOf(Items.BOW)) {
            int s = InventoryUtils.findItemWithPredicateInHotbar(st -> st.isOf(Items.BOW));
            if (s < 0) return false;
            bow = mc.player.getInventory().getStack(s);
        }
        var entry = mc.world.getRegistryManager().getOptionalEntry(Enchantments.FLAME);
        return entry.isEmpty() || EnchantmentHelper.getLevel(entry.get(), bow) > 0;
    }

    private boolean isRail(ItemStack s) {
        return s.isOf(Items.RAIL) || s.isOf(Items.POWERED_RAIL) || s.isOf(Items.DETECTOR_RAIL) || s.isOf(Items.ACTIVATOR_RAIL);
    }

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (isNull() || !RotationUtils.isControlledBy(this)) return;
        if (!RotationUtils.hasSilentRotation()) return;
        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        if (!renderPath.getValue() || isNull() || mc.options.hudHidden) return;
        if (!(mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.BOW))) return;
        Render3D frame = EchoEvents.lastFrame();
        if (frame == null) return;
        BlockPos land = predictedLanding(mc.player.getItemUseTime());
        if (land == null) return;
        Vector4f clip = frame.toClip(land.getX() + 0.5, land.getY() + 0.5, land.getZ() + 0.5);
        if (clip.w() <= 0.05f) return;
        float[] g = Render3D.toGui(clip);
        DrawContext ctx = event.getContext();
        Draw.rect(ctx, Math.round(g[0] - 5), Math.round(g[1] - 5), 10, 10, 3, Colors.withAlpha(0xFF9BD3FA, 0.85f));
        Draw.rect(ctx, Math.round(g[0] - 3), Math.round(g[1] - 3), 6, 6, 2, 0xFF0B0E13);
    }

    // Generated literals use the existing EncryptedString decoder. Keep decoded values stable.
    private static final class ModuleText {
        private static final String S0 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8df6,0x8ded,0x4e8a,0x8b51,0x4aeb,0x7b30,0x3419,0x3414,0x76bb,0x361f,0x7b38,0x8b67}, new char[]{0x8b34,0x8d81,0x7b51,0x346d,0x4ee9,0x3677,0x4acb,0x76ff}).toString();
        private static final String S1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6040,0x81ab,0x8196,0xd3bc,0x5909,0x0a58}, new char[]{0x6060,0x0a2c,0xd3d5,0x596a,0x81c0,0x81e5,0xdf03,0x105c}).toString();
        private static final String S2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe6b9,0xe692,0x81b0,0x78e1,0x5f44,0xad67,0x74e1,0xad44,0x81a2,0x6694,0x74ad,0x1b72,0x66f6,0x5f5c}, new char[]{0x66b4,0xe6d6,0x81d5,0x748d,0xad25,0x5f3d,0x7882,0x1b19}).toString();
        private static final String S3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xad72,0xc1df,0xaf37,0x10e1,0x88b8,0x6aaf,0x6aa3,0x696c,0x33a4,0x8892}, new char[]{0x88f5,0x6aca,0xad1c,0x10c1,0x33e7,0xc1b7,0xaf56,0x691e}).toString();
        private static final String S4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x45e0,0x6da1,0x7d5f}, new char[]{0x45a1,0x7d2d,0x6dc2,0x8fab,0xe23e,0xe44b,0x08b0,0x51dc}).toString();
        private static final String S6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd855,0x29c3,0x0b8a,0x536e,0x2da9,0x2a62,0xbf5c,0x619b}, new char[]{0x0bc5,0xd823,0x29a6,0x531c,0x61f7,0xbf33,0x2dc8,0x2a06}).toString();
        private static final String S7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2e58,0x5c60,0x0ed4,0x6a03,0xb644,0xbcf5}, new char[]{0xb664,0x2e3b,0x0eb5,0x5c12,0xbc81,0x6a70,0xc815,0x8bb0}).toString();
        private static final String S8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc6eb,0x1f33,0xed4c,0xbc56,0x53e1,0x27ae,0x53d5,0x4343,0x27a7,0x273b}, new char[]{0x53b1,0x27c2,0x1f52,0xbc35,0xed29,0x271b,0xc6a6,0x432c}).toString();
        private static final String S9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x51ef,0xcbd2,0x9a60,0x5b0e,0x7ef2,0xcbf4,0x547e,0x8679,0x8672,0x0639}, new char[]{0xcba0,0x8600,0x541f,0x5185,0x9a05,0x7e91,0x064d,0x5b61}).toString();
        private static final String S10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf9b7,0x94b3,0xc921,0x6920,0x9062,0x7a48,0x754e,0x35c0,0x6911}, new char[]{0x6952,0x7a3a,0x35af,0xc952,0xf9c4,0x94db,0x9003,0x7527}).toString();
        private static final String S11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c01,0x4e27,0x9489,0x4e73,0xb136,0x9488,0x421a,0x8922,0x1691,0x16b6,0x9f47}, new char[]{0x16de,0x94e6,0x4e53,0x8960,0x8c6e,0x9f30,0xb116,0x4249}).toString();
        private static final String S12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf332,0xba3b,0xe3ad,0xa5cf,0xba16,0x05f9,0xb58f,0x4dc3,0x4ddb,0xf371,0x05f7,0xe3a0,0xa5ae,0x658e,0x65ab}, new char[]{0xa5ef,0x65fb,0x4db7,0x0596,0xf351,0xba73,0xe3c9,0xb5fb}).toString();
        private static final String S13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x05f7,0x2bca,0x1646,0x2bed,0xcf19,0x94be,0xcf34,0x92a1,0x1601,0x56e2,0x05e2,0x0708,0x94bf,0x92c0}, new char[]{0x92e0,0x2bbf,0x0583,0x94d0,0x1621,0xcf7c,0x568b,0x077c}).toString();
        private static final String S14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcb9b,0x6a68,0xfc32,0xfc2f,0x4571,0xe96a,0xbc97,0x4549,0xba45,0x6a63,0x1121,0x1173,0xba45,0xe96b,0xbc96,0xfc23,0x4563,0x1140,0xcb8e}, new char[]{0x1101,0x4504,0xfc46,0xbcf9,0xba65,0x6a20,0xe903,0xcbfa}).toString();
        private static final String S15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe07a,0xac23,0xd0aa,0xf466,0x227d,0xf464,0x8975,0xf8f8,0x227d,0x5df4,0xd0aa,0xf8f6,0xe02e,0xf8ca,0x8975,0x2269,0x5df3,0xac14}, new char[]{0xf899,0x220a,0xf40f,0xe00e,0xac77,0x5d9b,0xd08a,0x8937}).toString();
        private static final String S16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x689f,0x1358,0x20ad,0x76f3,0xcc6a,0x65e3,0x65d3,0x6895,0x024a,0x069b,0xcc7f}, new char[]{0x65b0,0x68f4,0xcc19,0x022f,0x208d,0x76b1,0x06f7,0x1337}).toString();
        private static final String S17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3dac,0xffc6,0x2fe8,0xbdfb,0x3da8,0x0a24,0x1daf,0x7cf9,0x1d95,0x40e0}, new char[]{0x1dfc,0x3dc5,0xffaa,0x4085,0x7c97,0x2f9c,0x0a04,0xbdba}).toString();
        private static final String S18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1faa,0x20e1,0x5a09,0x8d77,0x1f89,0x91c6,0x20e5,0x3cc5,0x91fc,0x9d76,0x3c89,0x8c0f,0x9d57,0x5a2b}, new char[]{0x9195,0x2088,0x3ca9,0x1fec,0x9d39,0x5a7d,0x8c2f,0x8d36}).toString();
        private static final String S19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcd2b,0x9703,0xba8d,0xc5f4,0xd8e8,0x80ba,0xc848,0x9712,0xc5f2,0xc013,0xc87b}, new char[]{0xc829,0x9766,0xc59c,0x80de,0xc076,0xd89a,0xbaad,0xcd7b}).toString();
        private static final String S20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2e26,0x95d3,0xc6c8,0xc133,0x7d3e,0xc6fd,0x4504,0xb7d6,0x0b2b}, new char[]{0xc6bc,0xc146,0x2e52,0x0b44,0x95f3,0xb795,0x4565,0x7d4c}).toString();
        private static final String S21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f46,0x2d14,0x2d0e,0x7f8d,0x5f58,0x2cf5,0x5f49,0xef3f,0x7fd9,0x4937,0x3ad1,0x5f59,0x4926,0x4922,0x2cb9,0x5f44,0xef27,0x3a90,0x2cb3,0x2d16,0x7fc2,0x2cb1,0x7fc2,0x4d7a,0xef34,0x4920,0x4926,0x2cbb,0x4926,0x2d05,0x4d35,0x4d67,0x5f46,0x3a94,0x2ca1,0x7fde,0x3a92,0xef26,0x7fdf,0xef72,0x3a92,0x2d03,0x4d74,0x4d7c,0x4d45,0x4d35,0xef7f,0x2d12,0x2cb4,0x3ad1}, new char[]{0x4d15,0x2cd5,0x4943,0x3af1,0x2d77,0x7fad,0xef52,0x5f2b}).toString();
        private static final String S22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xddd7,0xd0b3,0xcb71,0x176c,0x176c,0xd89c,0x98b0,0x8cf1,0x8cbe,0x98b7,0xddd5,0x1738,0xcb35,0xd0a5,0x1723,0xddc6,0x8cff,0x8cea,0x1724,0x1729,0x1570,0xd0a8,0x156a,0x8cee,0x98e3,0xd883,0xd896,0xd0e0,0x1724,0x98af,0x1538,0xcb77,0xdd92,0x8cfd,0xdd92,0x98b7,0x1525,0xddc0,0xd88c,0x1762,0x1522,0xd8c5,0x156a,0x98e3,0x177c,0xcb70,0x1738,0xcb76,0xddc6,0xcb60,0xddd0,0x8cf0,0xddd1,0xdd83,0xd8c9,0x177c,0xddc0,0x98e3,0x172d,0x1523,0xd0e0,0x1739,0x8cfb,0x152d,0xd0e0,0x8cea,0x1525,0xcb71,0x98a4,0xd0b2,0x8cfb,0x98b1,0x152b,0x98b6,0x8cf7,0x8ca3,0xd0a8,0x98a2,0xddd3,0xd0a8,0xd88d,0x1538,0x98e3,0xd0b2,0x8ced,0xddda,0xd882,0xdd9c,0x98a5,0x98e3,0xd889,0xd891,0xd0b3,0x152f,0xcb76,0xd0e0,0xcb25,0xcb60,0x1519,0xd88c,0xcb60,0xd08f,0xd889,0x8cea,0xcb25,0xd0fd,0x153c,0xcb66,0xd884,0x153e,0xd897,0x176c,0xcb71,0xddd5}, new char[]{0x154a,0xddb2,0x174c,0xcb05,0xd0c0,0x8c9e,0x98c3,0xd8e5}).toString();
        private static final String S23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x28bc,0xa191,0xb91a,0x3da6,0x28bd,0xe86b,0x3da6,0xa1d5,0xe82a,0x28bc,0x28ef,0xa191,0x490c,0xe828,0xa1c5,0x4949,0x3da6,0x28bb,0x7822,0xe86b,0x4905,0x490c,0xb939,0xb96a,0x4908,0x289b,0xb927,0xa1df,0x28fe,0xb917,0xa1d0,0xe82c,0xb91b,0xb952,0xe824,0xe81f,0x782f,0xb92d,0x28aa,0x783d,0x3df5,0x4901,0xa1c3,0xb906,0x787b,0xa1d7,0xb952,0x490a,0x3def,0xe876,0x7829,0x491a,0xb923,0x782c,0x3dc8,0xb921,0x3de8,0xa1d4,0xb906,0xe83e,0x7875,0xb96a,0x7833,0x28a6,0x3df4,0xb91a,0xb921,0xb95c,0x4906,0x787b,0xb93e,0x3de7,0xb924}, new char[]{0xb972,0x28cf,0x3d86,0xe84b,0xb94a,0x4969,0xa1b1,0x785b}).toString();
        private static final String S24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8a1d,0x1e73,0x7e53,0xaad2,0x1e20,0x0f02,0x700a,0xaad7,0x1e6c,0xc50d,0x0f5a,0x0f54,0xdbca,0xdb9d,0xc541,0xaa83,0xc55f,0x7045,0x1e30,0x8a45,0x7004,0xaad0,0xc50d,0x7e51,0xdb8f,0x8a5f,0x7e51,0x7e58,0xdb89,0x0f1f,0xc55f,0x8a10,0x7009,0x1e2e,0xc55f,0x1e65,0x8a51,0x8a43,0xaad0,0xdb8b,0xaacb,0xdbca,0x7005,0x1e69,0x1e6f,0x7e56,0x8a59,0x0f5e,0x700a,0x0f5d,0xc55f,0xdbb8,0x0f4a,0xdbca,0x0f4b,0xc505,0x8a1e,0x7e06,0x7019,0x7e51,0xaac0,0xaac6,0x7003,0x7e40}, new char[]{0xdbea,0x0f3f,0xaaa3,0x706c,0x8a30,0xc52d,0x7e34,0x1e00}).toString();
        private static final String S25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2264,0x719d,0x3d5e,0x7bdc,0xee84,0x3d74,0x4322,0xee8b,0x433a,0x7b94,0xc1dd,0x7183,0xee84,0xc1da,0xee89,0xeec3,0x226b,0x6d13,0xee9f,0x4325,0x6d43,0xc1d9,0x3d52,0x3d56,0x2271,0x7b90,0x7bdd,0x6d47,0xc189,0x7199,0x3d52,0x71d0,0x4376,0x7195,0xee9e,0x3d4e,0x3d45,0x4376,0x7bd5,0x4331,0x6d58,0x6d56,0x6d13,0x2264,0xc189,0x7196,0xc1da,0x6d44,0x2260,0x2271,0xc189,0x7b9a,0x7b9d,0x2266}, new char[]{0x3d37,0x2205,0x6d33,0xc1a9,0x7bf5,0xeeed,0x71f0,0x4356}).toString();
        private static final String S26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb155,0x1607,0x99d7,0x7dc1,0x3ef9,0xd606,0x99d6,0x7dc1,0xb155,0x1607,0x2ba2,0xb146,0x7dc1,0xb145,0xd61b,0x1a4d,0x2b86,0x7dc1,0x1a4d,0xd61c,0x99d6,0x2bb3,0x3ef7,0x1602,0x1a4a,0x99dc,0x3efc,0xd615,0x1a56,0x1612,0xb101,0x2bb7,0x3ebe,0x1a4b,0x2ba5,0xd61d}, new char[]{0x2bd1,0xd674,0x1a24,0xb121,0x3e9e,0x99b9,0x1675,0x7de1}).toString();
        private static final String S27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f4f,0x3f42,0x8adc,0x0018,0xeade,0xfb09,0x003a,0x67ae,0xa0ee,0x8a63,0x8ad3,0xfb01}, new char[]{0x006a,0x3f2e,0xfb60,0x8abf,0x8a0a,0xa080,0xeab9,0x678e}).toString();
        private static final String S28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc61a,0x839a,0x089e,0x0883,0xd9d5,0xb4cd,0xdf6f,0xdf7a,0xc6d7,0xb499,0xdf74,0xc614,0xc61c,0xc61c,0xd9d1,0xe2d9,0xb4cb,0x83df,0x0886,0xc682,0xd994,0xe28a,0xc6cc,0xc60c,0xdf78,0xe2d5,0x839a,0xc6c9,0x83d4,0xe290,0x83c9,0xe2d9,0x83ca,0xd9c0,0xdf4b,0xb4cd,0x0886,0xb4cd,0x088b,0xdf74,0xc682,0xc6d0,0xdf37,0xc61a,0x088a,0xc6ce,0xb4d8,0xd9d9,0xd9c7,0xb4dd,0xe2d3}, new char[]{0xdf1b,0xc6a2,0xb4b9,0xe2b0,0xd9b4,0xc679,0x08ee,0x83ba}).toString();
        private static final String S29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa9d5,0x6389,0xc58e,0x4ca8,0xd1e6,0x4ca5,0x98b3,0xd1f1,0x4f6f,0x07b2,0x98b2,0xd1e9,0x07bb,0xc5ac,0x6389,0xa99c,0x4ca5,0x4f74,0x4f67,0xc5bd}, new char[]{0xc5de,0x4cc4,0x4f06,0xd185,0xa9f5,0x98dd,0x07d5,0x63a9}).toString();
        private static final String S30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3a6e,0x914a,0x9151,0x3a00,0x9118,0x26cc,0x268c,0x8e3a,0x26b7,0x5eaf,0xd182,0x5ea1,0x28f1,0x28f1,0x5ea1,0x8e3a,0xf1ab,0x28fe,0x9154,0x26c0,0xf1e8,0x2692,0x3a3a,0x8e3a,0xf1bc,0x915c,0xf1a9,0x3a21,0xd18f,0x8e21,0x5eab,0xd182,0x3a27,0x28b0,0xf1a7,0x8e1c,0x8e2b,0x2686,0x916c,0xd18f,0x3a6e,0xf1a1,0x5ebe,0xd1ce,0x28b0,0x28fb}, new char[]{0x26e0,0xf1c8,0x9138,0x3a4e,0x8e48,0x2890,0x5ec8,0xd1ee}).toString();
        private static final String S31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7443,0x4177,0x7406,0x3ece,0xde70,0x4169,0x7452,0x3ed0,0xb6b8,0x584b,0x1f46,0x5853,0x1f40,0x5865,0xde76,0xb6bc,0x9f88,0xb6a9,0x3ed2,0x9fcd,0x1f57,0x9f9e}, new char[]{0x5826,0xb6d9,0x3ebc,0x1f23,0x7426,0x9fed,0xde13,0x4118}).toString();
        private static final String S32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9f6e,0xe3fc,0x539f,0x53bf,0x3a8d,0xd215,0xd216,0xe69a,0xb3cc}, new char[]{0x53d6,0xd27b,0xb3bc,0x3af8,0xe388,0xe6ba,0x9f3d}).toString();
    }
}
