package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Auto Drain: scoops nearby liquid source blocks (the liquids you pick) with empty buckets.
 * When no empty bucket is left in the hotbar it falls back to cobwebs, which replace the source.
 *
 * <h2>Grim notes</h2>
 * <ul>
 *   <li>Silent aim is routed through {@link RotationUtils#aim(Feature)} /
 *       {@link RotationHandler}: the single vanilla flying packet per tick carries the aimed
 *       yaw/pitch, so no raw {@code LookAndOnGround} is ever sent and Grim's
 *       {@code AimDuplicateLook} and {@code Post (block_place)} checks don't fire.</li>
 *   <li>The interact is executed only once {@link RotationUtils#hasSilentRotation()} is set and
 *       {@link RotationHandler#getServerYaw()} / {@link RotationHandler#getServerPitch()} match
 *       the aim target within 0.5&deg;, so the vanilla flying arrives before the interact and the
 *       interact packet carries the same rotation.</li>
 *   <li>Freecam suppresses this feature while active via {@code Freecam.isActive()}.</li>
 * </ul>
 */
public class AutoDrain extends Feature {
    private final MultiModeSetting liquids = new MultiModeSetting(LT.L1, LT.L2, LT.L3);
    private final FloatSetting range = new FloatSetting(LT.L4, 4.5f, 2f, 6f, 0.1f);
    private final IntSetting delay = new IntSetting(LT.L5, 2, 0, 20);
    private final BoolSetting cobwebFallback = new BoolSetting(LT.L6, true);
    private final BoolSetting sAim = new BoolSetting(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4D,0x40,0x1C,0xDE,0xAC,0x79,0x74,0xDE,0xCF,0x9C}), true);
    private final BoolSetting swapBack = new BoolSetting(LT.L7, true);
    private final BoolSetting notUnderFeet = new BoolSetting(LT.L8, true);
    private final BoolSetting ignoreMine = new BoolSetting(LT.L9, true);
    private final IntSetting mineMemory = new IntSetting(LT.L10, 120, 5, 1800, o -> ignoreMine.getValue());

    /** Where I emptied a water / lava bucket -> when (ms). */
    private final java.util.Map<BlockPos, Long> mine = new java.util.HashMap<>();

    private int cooldown;

    /** Target of the current aim-and-interact cycle. */
    private BlockPos pendingTarget;
    private Vec3d pendingAim;
    private boolean pendingIsPlace;
    private BlockHitResult pendingHit;
    private int pendingSlot = -1;
    private int aimTicks;

    public AutoDrain() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8be,0x962d,0x82cc,0x379e,0x0b2e,0x1c9b,0x226c,0xb14b,0xd13b,0xbc06}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d76,0x76ad,0x1cd2,0xe731,0xe0b0,0xd2be,0x58ec,0x866e,0xb995,0x3b74,0x50c7,0x227b,0x1815,0x47cf,0xc971,0xf40a,0xc15a,0x7a4e,0x4fa7,0x962d,0xd12a,0xa8a2,0x66ed,0xd92c,0x0b29,0x1587,0x04b9,0x2603,0xb12f,0x0cd7,0x6544,0xb506,0x5f79,0xfce5,0xf005,0xf88c,0xeb80,0xa1e1,0x93b1,0xac4f,0x006a,0x9bef,0x335a,0xc5d2,0x37cd,0x11f5,0x4a06,0x4313,0x9e06,0xef19,0x7ece,0x82dd,0xcdb2,0x2a9b,0xbc02,0xa43f,0x723a,0x3fbd,0x8af4}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360}).toString(), Category.UTILITY));
        liquids.setOption(LT.L2, true);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RotationUtils.cleanup(this, sAim.getValue());
        clearPending();
    }

    private boolean wanted(FluidState fs) {
        if (!fs.isStill()) return false; // source blocks only
        if (fs.isOf(Fluids.WATER)) return liquids.isEnabled(LT.L2);
        if (fs.isOf(Fluids.LAVA)) return liquids.isEnabled(LT.L3);
        return false;
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.currentScreen != null || mc.interactionManager == null) return;
        // Freecam puts the player entity client-side far from the server-known position; any
        // interact would land outside server range and get rejected (desync / freeze), so stand
        // down while the free camera is in use.
        if (com.utilitymod.echo.features.impl.movement.Freecam.isActive()) {
            if (pendingTarget != null) RotationUtils.cleanup(this, sAim.getValue());
            clearPending();
            return;
        }

        if (cooldown > 0) { cooldown--; return; }

        long now = System.currentTimeMillis();
        long keep = mineMemory.getValue() * 1000L;
        mine.values().removeIf(t -> now - t > keep);

        if (pendingTarget != null && !stillValidPending()) clearPending();

        if (pendingTarget == null) {
            BlockPos target = nearestSource();
            if (target == null) {
                RotationUtils.cleanup(this, sAim.getValue());
                return;
            }
            armPending(target);
            if (pendingTarget == null) return;
        }

        // Request rotation (silent routes it through RotationHandler, so vanilla's flying packet
        // carries the aimed yaw/pitch — no extra Look packet is ever sent).
        boolean okRotation = RotationUtils.aim(this)
                .priority(EventSubscribe.Priority.NORMAL)
                .silent(sAim.getValue())
                .speed(360f)
                .aimType(RotationUtils.AimType.BLATANT)
                .to(pendingAim);
        if (!okRotation) return;

        aimTicks++;
        if (!rotationReady()) return;

        performPending();
    }

    private boolean stillValidPending() {
        if (pendingTarget == null) return false;
        if (pendingIsPlace) {
            if (!mc.world.getBlockState(pendingTarget).isReplaceable()
                    && !mc.world.getFluidState(pendingTarget).isStill()) return false;
            return pendingHit != null;
        }
        return wanted(mc.world.getFluidState(pendingTarget));
    }

    private void armPending(BlockPos target) {
        int bucket = InventoryUtils.findItemWithPredicateInHotbar(s -> s.isOf(Items.BUCKET));
        if (bucket != -1) {
            pendingTarget = target;
            pendingAim = Vec3d.ofCenter(target);
            pendingIsPlace = false;
            pendingHit = null;
            pendingSlot = bucket;
            aimTicks = 0;
            return;
        }
        if (!cobwebFallback.getValue()) return;
        int web = InventoryUtils.findItemWithPredicateInHotbar(s -> s.isOf(Items.COBWEB));
        if (web == -1) return;
        BlockHitResult hit = support(target);
        if (hit == null) return;
        if (mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > range.getValue() * range.getValue()) return;
        pendingTarget = target;
        pendingAim = hit.getPos();
        pendingIsPlace = true;
        pendingHit = hit;
        pendingSlot = web;
        aimTicks = 0;
    }

    private boolean rotationReady() {
        if (!sAim.getValue()) return true;
        if (!RotationUtils.hasSilentRotation()) return aimTicks >= 1; // treat non-silent control as ready after one tick
        float[] want = RotationUtils.calculate(mc.player.getEyePos(), pendingAim);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(RotationHandler.getServerYaw() - want[0]));
        float pitchDiff = Math.abs(RotationHandler.getServerPitch() - want[1]);
        return yawDiff <= 0.5f && pitchDiff <= 0.5f;
    }

    private void performPending() {
        int previous = mc.player.getInventory().getSelectedSlot();
        if (pendingSlot != previous) InventoryUtils.setInvSlot(pendingSlot);
        try {
            if (pendingIsPlace) {
                BlockUtils.interactWithBlock(pendingHit, true);
            } else {
                var result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            }
        } finally {
            if (swapBack.getValue() && pendingSlot != previous) InventoryUtils.setInvSlot(previous);
            cooldown = delay.getValue();
            clearPending();
        }
    }

    private void clearPending() {
        pendingTarget = null;
        pendingAim = null;
        pendingIsPlace = false;
        pendingHit = null;
        pendingSlot = -1;
        aimTicks = 0;
    }

    /** Inject the silent rotation into the single vanilla flying packet (one flying/tick). */
    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (!sAim.getValue() || pendingTarget == null) return;
        if (!RotationUtils.isControlledBy(this) || !RotationUtils.hasSilentRotation()) return;
        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    private BlockPos nearestSource() {
        Vec3d eye = mc.player.getEyePos();
        int r = (int) Math.ceil(range.getValue());
        double max = range.getValue() * range.getValue();
        BlockPos feet = mc.player.getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    if (notUnderFeet.getValue() && (m.equals(feet) || m.equals(feet.up()))) continue;
                    if (!wanted(mc.world.getFluidState(m))) continue;
                    if (ignoreMine.getValue() && mine.containsKey(m)) continue;
                    double d = eye.squaredDistanceTo(Vec3d.ofCenter(m));
                    if (d <= max && d < bestD) {
                        bestD = d;
                        best = m.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    /** Remember the spot whenever I empty a water / lava bucket (block click or air use). */
    @EventSubscribe
    private void onSend(com.utilitymod.echo.event.impl.EventPacketSend event) {
        if (isNull()) return;
        if (event.getPacket() instanceof net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket p) {
            if (!holdsFilledBucket(p.getHand())) return;
            BlockHitResult hit = p.getBlockHitResult();
            remember(hit.getBlockPos());
            remember(hit.getBlockPos().offset(hit.getSide()));
        } else if (event.getPacket() instanceof net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket p) {
            if (!holdsFilledBucket(p.getHand())) return;
            float yaw = p.getYaw(), pitch = p.getPitch();
            Vec3d eye = mc.player.getEyePos();
            Vec3d dir = Vec3d.fromPolar(pitch, yaw);
            Vec3d end = eye.add(dir.multiply(mc.player.getBlockInteractionRange()));
            BlockHitResult hit = mc.world.raycast(new net.minecraft.world.RaycastContext(eye, end,
                    net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                remember(hit.getBlockPos());
                remember(hit.getBlockPos().offset(hit.getSide()));
            }
        }
    }

    private boolean holdsFilledBucket(Hand hand) {
        var stack = mc.player.getStackInHand(hand);
        return stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.LAVA_BUCKET);
    }

    private void remember(BlockPos pos) {
        mine.put(pos.toImmutable(), System.currentTimeMillis());
    }

    private BlockHitResult support(BlockPos target) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction dir : order) {
            BlockPos neighbor = target.offset(dir);
            Direction face = dir.getOpposite(); // the neighbour's face that touches the target
            if (!mc.world.getBlockState(neighbor).isSideSolidFullSquare(mc.world, neighbor, face)) continue;
            Vec3d c = Vec3d.ofCenter(neighbor).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            return new BlockHitResult(c, face, neighbor, false);
        }
        return null;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7fda,0xe69d,0x4bfe,0xc444,0x8534,0x27b0,0xd788}, new char[]{0x7f96,0x27d9,0xd7f9,0xe6e8,0x855d,0x4b9a,0xc437}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb5e,0x7399,0xbafd,0x55dc,0xfa84}, new char[]{0xfad3,0x55bd,0xeb2a,0x73fc,0xba8f}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ef4,0x8463,0x9821,0xcca8}, new char[]{0x842f,0x9840,0x3e82,0xccc9}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7f87,0xf33f,0xc39b,0x5be5,0xd12e}, new char[]{0xc3c9,0xf35e,0x5b8b,0xd149,0x7fe2}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x25f7,0xb0c9,0x713c,0xa3d9,0x3995,0x187a,0x76d9,0x3089,0x56ac,0x8bff,0x2066,0xa6ae,0xfd51}, new char[]{0x30cd,0x2003,0xfd3d,0x715d,0x8b86,0xb0e9,0x39bd,0x56d8,0xa3b0,0x76ba,0x259c,0x1809,0xa687}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb858,0xd5bf,0x7bf3,0xaf8a,0xb5c4,0x6f71,0xe582,0xd67a,0x034a,0x51fc,0xe618,0x82a4,0x815c,0xfc41,0xfb2f}, new char[]{0xafc9,0x7b9c,0x813e,0xb82f,0xb5a1,0xd618,0xe638,0xd5f9,0xfc20,0xfb43,0xe5ee,0x519e,0x032b,0x6f12,0x82cf}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x47fc,0xc4ab,0xf26b,0xeb9d,0x8cc6,0x7ef7,0xd77c,0x6911,0xd1b1}, new char[]{0x6942,0xf21c,0xd71d,0x478c,0xd191,0x7eb5,0x8ca7,0xc4c8,0xebf6}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c0e,0x0a98,0x5a2c,0x0528,0x8064,0xb782,0xb427,0x3774,0x82f1,0xc66a,0x48e9,0x7a10,0xec16,0x8d44}, new char[]{0xc639,0xec7d,0x371d,0x6c7e,0xb7a2,0x82be,0x5a5b,0x8d2a,0xb407,0x7a52,0x4885,0x800b,0x054b,0x0af3}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf1e1,0xc6e1,0x3b70,0x36c0,0xe0f1,0xc5bd,0x6d84,0xf0d5,0xe77d,0xfa64,0x5af6,0x82a5,0x43ba,0xc3e9,0xc135,0xed3a,0x7f2c}, new char[]{0xc3a0,0x36a7,0x43d4,0xc15a,0xe70f,0xe094,0xf1c1,0xfa29,0x7f55,0x6da4,0xc5f1,0x82cc,0x3b01,0xc694,0xed53,0x5a92,0xf0a6}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9c1a,0x318c,0x5207,0x3c23,0xc9fe,0x6124,0xd415,0x2715,0x2f14,0x7144,0x4258,0x6f62,0x0baf,0xca41,0x3a98,0x9181,0xc2fc}, new char[]{0x6176,0xc299,0x526a,0xc99b,0x2778,0x0bcd,0xd470,0x6f10,0xca61,0x91cc,0x3af1,0x9c74,0x423d,0x3c03,0x716c,0x31ff,0x2f3d}).toString();
    }
}
