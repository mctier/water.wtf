package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.ServerRotations;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * LegacyPort Auto Drain: scoops liquid sources with an empty bucket (or covers them with a cobweb when
 * no bucket is left). Legit drains the source under your crosshair; Silent Aim finds nearby sources and
 * aims at them server-side. the web types are sources touching a cobweb (the usual web+water/lava trap).
 */
public class AutoDrain extends Feature {
    private final ModeSetting mode = new ModeSetting(new EncryptedString(new char[]{0x227c, 0xa8a4, 0xd137, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}), new EncryptedString(new char[]{0x227f, 0xd136, 0x37ca, 0xa8ae, 0xbc00}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}), new EncryptedString(new char[]{0x227f, 0xd136, 0x37ca, 0xa8ae, 0xbc00}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}), new EncryptedString(new char[]{0xa8a2, 0x962e, 0x82ec, 0x37d0, 0x0b7c, 0x1c9b, 0x2274, 0xb17b, 0xd129, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}));
    private final MultiModeSetting liquid = new MultiModeSetting(new EncryptedString(new char[]{0xa8a2, 0xb16b, 0xd136, 0x2269, 0x37d7, 0xbc1c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}), new EncryptedString(new char[]{0x226c, 0xd12d, 0x37cc, 0xa8aa, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}), new EncryptedString(new char[]{0x226e, 0xa8aa, 0xd136, 0xbc08}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}), new EncryptedString(new char[]{0xeb83, 0x0b13, 0x1cd2, 0x9614, 0x226c, 0xd12d, 0x11f5, 0x82c3, 0xa8aa, 0x37cc, 0xb12f, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), new EncryptedString(new char[]{0x11f2, 0xa8aa, 0xbc08, 0x226e, 0x1ca5, 0x828d, 0x0b32, 0xd136, 0xb140, 0x9626, 0x379e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}));
    private final RangeSetting delay = new RangeSetting(new EncryptedString(new char[]{0x2274, 0xd13e, 0x37c7, 0xa8ae, 0xbc08}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}), 50f, 50f, 0f, 600f, 1f);
    private final ModeSetting swap = new ModeSetting(new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}), new EncryptedString(new char[]{0x2276, 0xa8a4, 0xd134, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}), new EncryptedString(new char[]{0x2276, 0xa8a4, 0xd134, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}), new EncryptedString(new char[]{0xa8a4, 0xb163, 0xd134, 0x226a, 0x37df, 0xbc04}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}));
    private final BoolSetting cobweb = new BoolSetting(new EncryptedString(new char[]{0x6575, 0xa8aa, 0xfce8, 0x76b6, 0xf052, 0xebc1, 0x9637, 0x37dc, 0x82c6, 0xb16e, 0xbc05, 0x4a0f, 0x1cd2, 0xc5d9, 0x2274, 0xd13c, 0x11ff, 0x0b3f}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb}), true);
    private final BoolSetting interact = new BoolSetting(new EncryptedString(new char[]{0x226c, 0xd133, 0xb16e, 0x0b3f, 0xbc0c, 0x37cc, 0xa8a5, 0x82d9}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}), true);
    private final BoolSetting swapBack = new BoolSetting(new EncryptedString(new char[]{0x0b3d, 0x1c99, 0x82ce, 0x379e, 0xbc19, 0xa8bc, 0xb14d, 0x2279, 0xd129}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2}), true);
    private final FloatSetting fov = new FloatSetting(new EncryptedString(new char[]{0xd129, 0x0b7c, 0xb17b, 0x37d0, 0x962e, 0x2274, 0xeba7, 0xfcd1, 0x82ec, 0xbc0c, 0xa8a2, 0x1c9b, 0x11b0, 0x6579}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87}), 180f, 0f, 180f, 1f);

    private long readyAt;
    private int home = -1;

    /** Liquid sources you placed with a bucket (pos -> time); never drained. */
    private static final java.util.Map<BlockPos, Long> OWN_LIQUID = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long OWN_KEEP_MS = 5 * 60_000L;

    /** Record where your own water/lava bucket lands (bucket use raycasts with your look, like vanilla). */
    @EventSubscribe
    @SuppressWarnings("unused")
    private void onSend(com.utilitymod.echo.event.impl.EventPacketSend e) {
        if (mc.player == null || mc.world == null) return;
        if (!(e.getPacket() instanceof net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket p)) return;
        var stack = p.getHand() == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
        if (!stack.isOf(Items.WATER_BUCKET) && !stack.isOf(Items.LAVA_BUCKET)) return;
        Vec3d eye = mc.player.getEyePos();
        HitResult r = mc.world.raycast(new RaycastContext(eye, eye.add(mc.player.getRotationVector().multiply(mc.player.getBlockInteractionRange())),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (!(r instanceof BlockHitResult b) || r.getType() != HitResult.Type.BLOCK) return;
        BlockPos hit = b.getBlockPos();
        BlockPos placed = mc.world.getBlockState(hit).isReplaceable() ? hit : hit.offset(b.getSide());
        long now = System.currentTimeMillis();
        OWN_LIQUID.values().removeIf(t -> now - t > OWN_KEEP_MS);
        OWN_LIQUID.put(placed.toImmutable(), now);
        OWN_LIQUID.put(hit.toImmutable(), now);   // waterlogging the clicked block
    }

    public AutoDrain() {
        super(new FeatureInfo(new EncryptedString(new char[]{0xa8be, 0x962d, 0x82cc, 0x379e, 0x0b2e, 0x1c9b, 0x226c, 0xb14b, 0xd13b, 0xbc06}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), new EncryptedString(new char[]{0x3b36, 0xf89a, 0x93ac, 0xc5da, 0x4a1d, 0x3ffc, 0x11f5, 0x76ad, 0x962d, 0x5f7c, 0xd93c, 0x82dd, 0x3352, 0x261f, 0xa420, 0xd2fe, 0x1581, 0x50dc, 0x1803, 0x227b, 0xb994, 0x9e4f, 0xd12a, 0xcdfd, 0xf005, 0x8aa0, 0x4784, 0xbc02, 0x37cd, 0xef4d, 0xb12f, 0x9bbb, 0x6544, 0xc159, 0xa8a2, 0xac03, 0x1cd2, 0x0cd1, 0x8667, 0x7e8b, 0xeb80, 0x0039, 0xe73b, 0x04a6, 0x66a0, 0x723c, 0x6d35, 0xb510, 0x2ad0, 0x58f9, 0xa1b3, 0x0b29, 0xfce5}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff, 0xa44c, 0x3f9d, 0xb9e2, 0x3333, 0x8a80, 0x04d1, 0x9e26, 0x1877, 0x93c4, 0x6d15, 0xe75a, 0x7eab, 0xf8f8, 0x7249, 0xcd9e, 0x47ef, 0xc13c, 0x588d, 0xd2d2, 0xac23, 0x2670, 0xa1c1, 0x3b16, 0xb567, 0x0cb4, 0x8605, 0x004a, 0x9b9b, 0x15e8, 0xef39, 0x668e}), Category.UTILITY));
        for (CharSequence l : new CharSequence[]{new EncryptedString(new char[]{0x226c, 0xd12d, 0x37cc, 0xa8aa, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}), new EncryptedString(new char[]{0x226e, 0xa8aa, 0xd136, 0xbc08}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}), new EncryptedString(new char[]{0xeb83, 0x0b13, 0x1cd2, 0x9614, 0x226c, 0xd12d, 0x11f5, 0x82c3, 0xa8aa, 0x37cc, 0xb12f, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), new EncryptedString(new char[]{0x11f2, 0xa8aa, 0xbc08, 0x226e, 0x1ca5, 0x828d, 0x0b32, 0xd136, 0xb140, 0x9626, 0x379e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190})}) liquid.setOption(l, true);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        goHome();
    }

    private void goHome() {
        if (home >= 0 && swapBack.getValue() && mc.player != null) mc.player.getInventory().setSelectedSlot(home);
        home = -1;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        if (isNull() || mc.currentScreen != null) return;
        boolean silent = mode.is(new EncryptedString(new char[]{0xa8a2, 0x962e, 0x82ec, 0x37d0, 0x0b7c, 0x1c9b, 0x2274, 0xb17b, 0xd129, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}));
        BlockPos src = silent ? nearestSource() : crosshairSource();
        if (src == null) { if (System.currentTimeMillis() >= readyAt) goHome(); return; }
        if (System.currentTimeMillis() < readyAt) {
            if (silent) ServerRotations.lookAt(this, Vec3d.ofCenter(src), .55f, 9);
            return;
        }

        int bucket = Cpvp.hotbar(Items.BUCKET);
        boolean web = bucket < 0;
        int slot = web ? (cobweb.getValue() ? Cpvp.hotbar(Items.COBWEB) : -1) : bucket;
        if (slot < 0) return;
        if (swap.is(new EncryptedString(new char[]{0x2276, 0xa8a4, 0xd134, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69})) && Cpvp.selected() != slot) return;
        if (home < 0) home = Cpvp.selected();
        Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
        if (!interact.getValue()) return;

        if (web) {
            var hit = com.utilitymod.echo.utils.blocks.BlockUtils.findPlacementHit(src, false);
            if (hit == null) return;
            if (!Cpvp.aimed(this, silent, fov.getValue(), hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
            if (Cpvp.place(src)) readyAt = Cpvp.next(delay);
            return;
        }
        if (silent) {
            if (ServerRotations.yawOffset(Vec3d.ofCenter(src)) > fov.getValue()) return;
            ServerRotations.lookAt(this, Vec3d.ofCenter(src), .55f, 9);
            if (!sentLooksAtFluid(src)) return;
            useWithSentLook();
        } else {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }
        readyAt = Cpvp.next(delay);
    }

    /** Bucket use raycasts with the player's rotation (and the packet carries it): use the look the server has. */
    private void useWithSentLook() {
        var p = mc.player;
        float yaw = p.getYaw(), pitch = p.getPitch();
        Vec3d look = ServerRotations.sentLook();
        float sYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float sPitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, look.y))));
        try {
            p.setYaw(sYaw);
            p.setPitch(sPitch);
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        } finally {
            p.setYaw(yaw);
            p.setPitch(pitch);
        }
    }

    private boolean sentLooksAtFluid(BlockPos src) {
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getBlockInteractionRange();
        HitResult r = mc.world.raycast(new RaycastContext(eye, eye.add(ServerRotations.sentLook().multiply(reach)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.SOURCE_ONLY, mc.player));
        return r instanceof BlockHitResult b && r.getType() == HitResult.Type.BLOCK && b.getBlockPos().equals(src);
    }

    private BlockPos crosshairSource() {
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getBlockInteractionRange();
        HitResult r = mc.world.raycast(new RaycastContext(eye, eye.add(mc.player.getRotationVector().multiply(reach)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.SOURCE_ONLY, mc.player));
        if (!(r instanceof BlockHitResult b) || r.getType() != HitResult.Type.BLOCK) return null;
        return wanted(b.getBlockPos()) ? b.getBlockPos() : null;
    }

    private BlockPos nearestSource() {
        BlockPos me = mc.player.getBlockPos();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(me.add(-4, -3, -4), me.add(4, 3, 4))) {
            if (!wanted(p) || !Cpvp.inReach(p)) continue;
            if (ServerRotations.yawOffset(Vec3d.ofCenter(p)) > fov.getValue()) continue;
            double d = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(p));
            if (d < bd) { bd = d; best = p.toImmutable(); }
        }
        return best;
    }

    private boolean wanted(BlockPos p) {
        if (OWN_LIQUID.containsKey(p)) return false;   // liquid you placed yourself stays
        FluidState f = mc.world.getFluidState(p);
        if (f.isEmpty() || !f.isStill()) return false;
        boolean water = f.isIn(FluidTags.WATER), lava = f.isIn(FluidTags.LAVA);
        boolean web = false;
        for (Direction d : Direction.values()) if (mc.world.getBlockState(p.offset(d)).isOf(Blocks.COBWEB)) { web = true; break; }
        if (web) return (water && liquid.isEnabled(new EncryptedString(new char[]{0xeb83, 0x0b13, 0x1cd2, 0x9614, 0x226c, 0xd12d, 0x11f5, 0x82c3, 0xa8aa, 0x37cc, 0xb12f, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}))) || (lava && liquid.isEnabled(new EncryptedString(new char[]{0x11f2, 0xa8aa, 0xbc08, 0x226e, 0x1ca5, 0x828d, 0x0b32, 0xd136, 0xb140, 0x9626, 0x379e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190})));
        return (water && liquid.isEnabled(new EncryptedString(new char[]{0x226c, 0xd12d, 0x37cc, 0xa8aa, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be}))) || (lava && liquid.isEnabled(new EncryptedString(new char[]{0x226e, 0xa8aa, 0xd136, 0xbc08}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69})));
    }
}
