package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.ServerRotations;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.combat.ExplosionUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Shared helpers for the LegacyPort crystal-pvp ports (slots, anchors, delays, placement). */
final class Cpvp {
    static final MinecraftClient mc = MinecraftClient.getInstance();

    private Cpvp() {}

    // ---- own anchors (placed by you, by hand or by any of these modules) ------------------------

    static final java.util.Set<BlockPos> OWN = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final Object TRACKER = new Object() {
        @com.utilitymod.echo.event.EventSubscribe
        @SuppressWarnings("unused")
        private void onSend(com.utilitymod.echo.event.impl.EventPacketSend e) {
            if (e.getPacket() instanceof net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket p && mc.player != null) {
                var hand = p.getHand() == net.minecraft.util.Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
                if (hand.isOf(Items.RESPAWN_ANCHOR)) {
                    BlockPos clicked = p.getBlockHitResult().getBlockPos();
                    BlockPos placed = mc.world != null && mc.world.getBlockState(clicked).isReplaceable() ? clicked : clicked.offset(p.getBlockHitResult().getSide());
                    OWN.add(placed.toImmutable());
                    if (OWN.size() > 256) OWN.clear();
                }
            }
        }
    };
    private static boolean tracking;

    /** Start recording anchors you place (idempotent). */
    static void track() {
        if (tracking) return;
        tracking = true;
        com.utilitymod.echo.event.EventManager.register(TRACKER);
    }

    static boolean own(BlockPos p) { return OWN.contains(p); }

    /** Rising-edge detector for KeybindSetting codes. */
    static final class Edge {
        private boolean was;
        boolean pressed(int key) {
            boolean down = mc.currentScreen == null && com.utilitymod.echo.handlers.InputHandler.isBindDown(key);
            boolean r = down && !was;
            was = down;
            return r;
        }
        boolean held(int key) { return mc.currentScreen == null && com.utilitymod.echo.handlers.InputHandler.isBindDown(key); }
    }

    /** The block the crosshair is on, or null. */
    static BlockHitResult crosshairBlock() {
        return mc.crosshairTarget instanceof BlockHitResult b && b.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK ? b : null;
    }

    /** Use the held item on exactly what the crosshair hits (vanilla-identical click). */
    static boolean useCrosshair() {
        BlockHitResult b = crosshairBlock();
        return b != null && BlockUtils.interactWithBlock(b, true).isAccepted();
    }

    // ---- slots -------------------------------------------------------------------------

    static int hotbar(Item item) {
        if (mc.player == null) return -1;
        var inv = mc.player.getInventory();
        if (inv.getStack(inv.getSelectedSlot()).isOf(item)) return inv.getSelectedSlot();
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isOf(item)) return i;
        return -1;
    }

    static boolean holding(Item item) {
        return mc.player != null && mc.player.getMainHandStack().isOf(item);
    }

    static int selected() { return mc.player.getInventory().getSelectedSlot(); }

    /** Swap modes: new EncryptedString(new char[]{0x2276, 0xa8a4, 0xd134, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}) (use what is held), new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69})/new EncryptedString(new char[]{0xa8a4, 0xb163, 0xd134, 0x226a, 0x37df, 0xbc04}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}) (client hotbar switch), new EncryptedString(new char[]{0xa8a2, 0xb17b, 0xd129, 0x2274, 0x37d0, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}) (server-only). */
    static boolean select(int slot, CharSequence mode) {
        if (slot < 0 || mc.player == null) return false;
        String m = mode.toString();
        if (m.equals(new EncryptedString(new char[]{0x2276, 0xa8a4, 0xd134, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}))) return selected() == slot;
        if (m.equals(new EncryptedString(new char[]{0xa8a2, 0xb17b, 0xd129, 0x2274, 0x37d0, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}))) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            return true;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    /** Undo a silent swap (re-announce the real slot) or switch the hotbar back. */
    static void restore(int slot, CharSequence mode) {
        if (slot < 0 || mc.player == null) return;
        if (mode.toString().equals(new EncryptedString(new char[]{0xa8a2, 0xb17b, 0xd129, 0x2274, 0x37d0, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}))) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selected()));
        else mc.player.getInventory().setSelectedSlot(slot);
    }

    /** Move a stack from player-inventory slot index (9..35 main, 0..8 hotbar) into hotbar slot / offhand(40). */
    static void swapInto(int invIndex, int button) {
        if (mc.player == null || mc.interactionManager == null) return;
        int handlerSlot = invIndex < 9 ? invIndex + 36 : invIndex;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, handlerSlot, button, SlotActionType.SWAP, mc.player);
    }

    static int findInventory(Item item, boolean includeHotbar) {
        var inv = mc.player.getInventory();
        for (int i = includeHotbar ? 0 : 9; i < 36; i++) if (inv.getStack(i).isOf(item)) return i;
        return -1;
    }

    // ---- delays ------------------------------------------------------------------------

    static long next(RangeSetting r) {
        return System.currentTimeMillis() + Math.round(Math.max(0, r.getRandom()));
    }

    // ---- anchors -----------------------------------------------------------------------

    static boolean isAnchor(BlockPos p) { return mc.world.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR); }

    static int charges(BlockPos p) {
        var s = mc.world.getBlockState(p);
        return s.isOf(Blocks.RESPAWN_ANCHOR) ? s.get(RespawnAnchorBlock.CHARGES) : -1;
    }

    static float damage(LivingEntity who, BlockPos anchor) { return ExplosionUtils.getAnchorDamageTo(who, anchor); }

    /** Pre-armor anchor damage to a player whose feet would be at {@code feet} (power 5, vanilla formula). */
    static float damageAt(Vec3d feet, BlockPos anchor) {
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(feet.x - .3, feet.y, feet.z - .3, feet.x + .3, feet.y + 1.8, feet.z + .3);
        Vec3d c = Vec3d.ofCenter(anchor);
        double q = feet.distanceTo(c) / 10.0;
        if (q > 1) return 0;
        float seen = ExplosionUtils.getSeenPercent(c, box, mc.player);
        double impact = (1 - q) * seen;
        return (float) ((impact * impact + impact) / 2 * 7 * 10 + 1);
    }

    /** Absorption-aware lethality check. */
    static boolean lethal(float dmg) {
        return dmg >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 0.5f;
    }

    static PlayerEntity nearestEnemy(double range) {
        PlayerEntity best = null;
        double bd = range * range;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator() || !TargetUtils.isTargetAllowed(p)) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ---- interaction ---------------------------------------------------------------------

    /** Hit result on the face of {@code pos} that faces the eye (used for charging/exploding a block). */
    static BlockHitResult faceHit(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d c = Vec3d.ofCenter(pos);
        Direction best = Direction.UP;
        double bd = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(d)).isAir() && !mc.world.getBlockState(pos.offset(d)).isReplaceable()) continue;
            Vec3d f = c.add(d.getOffsetX() * .5, d.getOffsetY() * .5, d.getOffsetZ() * .5);
            double dist = eye.squaredDistanceTo(f);
            if (dist < bd) { bd = dist; best = d; }
        }
        Vec3d point = c.add(best.getOffsetX() * .5, best.getOffsetY() * .5, best.getOffsetZ() * .5);
        return new BlockHitResult(point, best, pos, false);
    }

    /** Use the held item on {@code pos} (charge / explode an anchor). */
    static boolean use(BlockPos pos) {
        return BlockUtils.interactWithBlock(faceHit(pos), true).isAccepted();
    }

    /** Place the held block at {@code pos} against a solid neighbour. */
    static boolean place(BlockPos pos) {
        var hit = BlockUtils.findPlacementHit(pos, false);
        if (hit == null) return false;
        return BlockUtils.interactWithBlock(hit.hitResult(), true).isAccepted();
    }

    static Vec3d placeAim(BlockPos pos) {
        var hit = BlockUtils.findPlacementHit(pos, false);
        return hit == null ? Vec3d.ofCenter(pos) : hit.hitResult().getPos();
    }

    static boolean canPlace(BlockPos pos) {
        var s = mc.world.getBlockState(pos);
        return (s.isAir() || s.isReplaceable()) && BlockUtils.findPlacementHit(pos, false) != null
                && !BlockUtils.collidesWithBlockingEntity(pos);
    }

    /**
     * Aim then act. Silent: request the server look at {@code point} and return true only once the look the
     * server already received points at {@code block} (so the interact that follows is legal). Not silent:
     * act straight away with the camera look.
     */
    static boolean aimed(Object owner, boolean silent, float fov, Vec3d point, BlockPos block) {
        if (!silent) return true;
        if (ServerRotations.yawOffset(point) > fov) return false;
        ServerRotations.lookAt(owner, point, 0.55f, 10);
        return ServerRotations.sentHitsBlock(block);
    }

    /**
     * Use the held item (bucket, crossbow, bow release...) with the rotation the server last received:
     * item use raycasts/aims with the player's rotation and the packet carries it, so it must match.
     */
    static boolean useItemWithSentLook() {
        var p = mc.player;
        float yaw = p.getYaw(), pitch = p.getPitch();
        Vec3d look = ServerRotations.sentLook();
        try {
            p.setYaw((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
            p.setPitch((float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, look.y)))));
            return mc.interactionManager.interactItem(p, net.minecraft.util.Hand.MAIN_HAND).isAccepted();
        } finally {
            p.setYaw(yaw);
            p.setPitch(pitch);
        }
    }

    /** True when the look the server already has is within {@code tol} degrees of the direction to {@code point}. */
    static boolean sentNear(Vec3d point, float tol) {
        Vec3d want = point.subtract(mc.player.getEyePos()).normalize();
        double dot = Math.max(-1, Math.min(1, want.dotProduct(ServerRotations.sentLook())));
        return Math.toDegrees(Math.acos(dot)) <= tol;
    }

    static boolean inReach(BlockPos p) {
        double r = mc.player.getBlockInteractionRange();
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(p)) <= r * r;
    }

    /** Block between our eye and the anchor, adjacent to the anchor: where a blast cover goes. */
    static BlockPos coverPos(BlockPos anchor) {
        Vec3d eye = mc.player.getEyePos();
        Direction best = null;
        double bd = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            BlockPos c = anchor.offset(d);
            if (!canPlace(c)) continue;
            double dist = eye.squaredDistanceTo(Vec3d.ofCenter(c));
            if (dist < bd) { bd = dist; best = d; }
        }
        return best == null ? null : anchor.offset(best);
    }

    static Item coverItem(CharSequence mode) {
        return mode.toString().equals(new EncryptedString(new char[]{0x226b, 0xd135, 0xb166, 0x0b3d, 0xbc00, 0x37da, 0xa8a9, 0x82c3}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad})) ? Items.OBSIDIAN : Items.GLOWSTONE;
    }

    static int argb(com.utilitymod.echo.features.settings.impl.ColorSetting c, int alpha) {
        return (alpha << 24) | (c.getARGB() & 0xFFFFFF);
    }
}
