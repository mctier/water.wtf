package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventPacketSend;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.combat.ExplosionUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenPrestige Auto Safe Cart: when a TNT minecart close to you would hit hard, places a block between
 * you and the cart (up to Max Blocks, stopping once the damage left is acceptable). Silent covers carts
 * itself with server-side aim; Legit only covers when your crosshair is on the cover spot.
 */
public class AutoSafeCart extends Feature {
    private final ModeSetting mode = new ModeSetting("Mode", "Silent", "Silent", "Legit");
    private final ModeSetting protect = new ModeSetting("Protect", "Both", "Both", "Only Self", "Only Enemy");
    private final FloatSetting minDamage = new FloatSetting("Min Damage", 5f, 0f, 30f, .5f);
    private final FloatSetting maxDamage = new FloatSetting("Max Damage", 5f, 0f, 30f, .5f);
    private final MultiModeSetting blocks = new MultiModeSetting("Blocks", "Logs", "Planks", "Cobblestone", "Obsidian");
    private final IntSetting maxBlocks = new IntSetting("Max Blocks", 2, 1, 4);
    private final RangeSetting delay = new RangeSetting("Delay", 80f, 80f, 0f, 500f, 1f);
    private final ModeSetting swap = new ModeSetting("Swap", "Normal", "None", "Normal", "Silent");
    private final FloatSetting range = new FloatSetting("Range", 10f, 3f, 15f, .5f);
    private final FloatSetting fov = new FloatSetting("FOV", 180f, 0f, 180f, 1f);

    private final Map<Integer, Integer> placedFor = new HashMap<>();
    private final Map<Integer, Long> noticed = new HashMap<>();
    private final List<Vec3d> ownPlacements = new ArrayList<>();
    private long readyAt;

    public AutoSafeCart() {
        super(new FeatureInfo("Auto Safe Cart", "Blocks off nearby TNT minecarts before they blow up on you.", Category.COMBAT));
        for (String b : new String[]{"Logs", "Planks", "Cobblestone", "Obsidian"}) blocks.setOption(b, true);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        placedFor.clear();
        noticed.clear();
    }

    /** Remember where you put TNT carts down, so Protect can tell yours from theirs. */
    @EventSubscribe
    @SuppressWarnings("unused")
    private void onSend(EventPacketSend e) {
        if (mc.player == null || !(e.getPacket() instanceof PlayerInteractBlockC2SPacket p)) return;
        ItemStack held = p.getHand() == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
        if (!held.isOf(Items.TNT_MINECART)) return;
        ownPlacements.add(Vec3d.ofCenter(p.getBlockHitResult().getBlockPos()));
        if (ownPlacements.size() > 32) ownPlacements.remove(0);
    }

    private boolean mine(Entity cart) {
        for (Vec3d v : ownPlacements) if (v.squaredDistanceTo(cart.getEntityPos()) < 4) return true;
        return false;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        if (isNull() || mc.currentScreen != null) return;
        placedFor.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
        noticed.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
        if (System.currentTimeMillis() < readyAt) return;

        double r2 = range.getValue() * range.getValue();
        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof TntMinecartEntity cart) || cart.squaredDistanceTo(mc.player) > r2) continue;
            boolean mine = mine(cart);
            if (protect.is("Only Self") && !mine) continue;
            if (protect.is("Only Enemy") && mine) continue;
            if (placedFor.getOrDefault(cart.getId(), 0) >= maxBlocks.getValue()) continue;
            float dmg = ExplosionUtils.getExplosionDamageTo(mc.player, cart.getEntityPos(), 4f);
            if (dmg < minDamage.getValue()) continue;
            long seen = noticed.computeIfAbsent(cart.getId(), k -> Cpvp.next(delay));
            if (System.currentTimeMillis() < seen) continue;
            if (cover(cart)) return;
        }
    }

    private boolean cover(TntMinecartEntity cart) {
        BlockPos spot = coverSpot(cart);
        int slot = blockSlot();
        if (spot == null || slot < 0) return false;
        int home = Cpvp.selected();
        if (swap.is("None") && home != slot) return false;
        var hit = BlockUtils.findPlacementHit(spot, false);
        if (hit == null) return false;
        if (mode.is("Legit")) {
            var ch = Cpvp.crosshairBlock();
            if (ch == null || !ch.getBlockPos().offset(ch.getSide()).equals(spot)) return false;
        }
        Cpvp.select(slot, swap.getValue());
        boolean silent = mode.is("Silent");
        if (!Cpvp.aimed(this, silent, fov.getValue(), hit.hitResult().getPos(), hit.hitResult().getBlockPos())) {
            if (home != slot && swap.is("Silent")) Cpvp.restore(home, "Silent");
            return true;
        }
        boolean ok = silent ? Cpvp.place(spot) : Cpvp.useCrosshair();
        if (home != slot) Cpvp.restore(home, swap.getValue());
        if (ok) {
            placedFor.merge(cart.getId(), 1, Integer::sum);
            if (ExplosionUtils.getExplosionDamageTo(mc.player, cart.getEntityPos(), 4f) <= maxDamage.getValue())
                placedFor.put(cart.getId(), maxBlocks.getValue());
        }
        readyAt = Cpvp.next(delay);
        return true;
    }

    /** First free block on the straight line from the cart toward your eyes. */
    private BlockPos coverSpot(TntMinecartEntity cart) {
        Vec3d from = cart.getEntityPos().add(0, .5, 0), to = mc.player.getEyePos();
        Vec3d dir = to.subtract(from);
        double len = dir.length();
        dir = dir.normalize();
        BlockPos cartPos = cart.getBlockPos();
        for (double d = 0.8; d < Math.min(len - 0.8, 4); d += 0.25) {
            BlockPos p = BlockPos.ofFloored(from.add(dir.multiply(d)));
            if (p.equals(cartPos) || p.equals(mc.player.getBlockPos()) || p.equals(mc.player.getBlockPos().up())) continue;
            if (!Cpvp.inReach(p)) continue;
            if (Cpvp.canPlace(p)) return p;
        }
        // fall back to the side of the cart that faces you
        Direction face = Direction.getFacing(dir.x, 0, dir.z);
        BlockPos side = cartPos.offset(face);
        return Cpvp.canPlace(side) && Cpvp.inReach(side) ? side : null;
    }

    private int blockSlot() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (blocks.isEnabled("Logs") && s.isIn(ItemTags.LOGS)) return i;
            if (blocks.isEnabled("Planks") && s.isIn(ItemTags.PLANKS)) return i;
            if (blocks.isEnabled("Cobblestone") && s.isOf(Items.COBBLESTONE)) return i;
            if (blocks.isEnabled("Obsidian") && s.isOf(Items.OBSIDIAN)) return i;
        }
        return -1;
    }
}
