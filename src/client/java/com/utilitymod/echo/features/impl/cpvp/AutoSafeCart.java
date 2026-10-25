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
    private final ModeSetting mode = new ModeSetting(LT.L1, LT.L2, LT.L2, LT.L3);
    private final ModeSetting protect = new ModeSetting(LT.L4, LT.L5, LT.L5, LT.L6, LT.L7);
    private final FloatSetting minDamage = new FloatSetting(LT.L8, 5f, 0f, 30f, .5f);
    private final FloatSetting maxDamage = new FloatSetting(LT.L9, 5f, 0f, 30f, .5f);
    private final MultiModeSetting blocks = new MultiModeSetting(LT.L10, LT.L11, LT.L12, LT.L13, LT.L14);
    private final IntSetting maxBlocks = new IntSetting(LT.L15, 2, 1, 4);
    private final RangeSetting delay = new RangeSetting(LT.L16, 80f, 80f, 0f, 500f, 1f);
    private final ModeSetting swap = new ModeSetting(LT.L17, LT.L18, LT.L19, LT.L18, LT.L2);
    private final FloatSetting range = new FloatSetting(LT.L20, 10f, 3f, 15f, .5f);
    private final FloatSetting fov = new FloatSetting(LT.L21, 180f, 0f, 180f, 1f);

    private final Map<Integer, Integer> placedFor = new HashMap<>();
    private final Map<Integer, Long> noticed = new HashMap<>();
    private final List<Vec3d> ownPlacements = new ArrayList<>();
    private long readyAt;

    public AutoSafeCart() {
        super(new FeatureInfo(LT.L22, LT.L23, Category.COMBAT));
        for (String b : new String[]{LT.L11, LT.L12, LT.L13, LT.L14}) blocks.setOption(b, true);
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
            if (protect.is(LT.L6) && !mine) continue;
            if (protect.is(LT.L7) && mine) continue;
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
        if (swap.is(LT.L19) && home != slot) return false;
        var hit = BlockUtils.findPlacementHit(spot, false);
        if (hit == null) return false;
        if (mode.is(LT.L3)) {
            var ch = Cpvp.crosshairBlock();
            if (ch == null || !ch.getBlockPos().offset(ch.getSide()).equals(spot)) return false;
        }
        Cpvp.select(slot, swap.getValue());
        boolean silent = mode.is(LT.L2);
        if (!Cpvp.aimed(this, silent, fov.getValue(), hit.hitResult().getPos(), hit.hitResult().getBlockPos())) {
            if (home != slot && swap.is(LT.L2)) Cpvp.restore(home, LT.L2);
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
            if (blocks.isEnabled(LT.L11) && s.isIn(ItemTags.LOGS)) return i;
            if (blocks.isEnabled(LT.L12) && s.isIn(ItemTags.PLANKS)) return i;
            if (blocks.isEnabled(LT.L13) && s.isOf(Items.COBBLESTONE)) return i;
            if (blocks.isEnabled(LT.L14) && s.isOf(Items.OBSIDIAN)) return i;
        }
        return -1;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ad2,0x6799,0x5fb4,0x67d7}, new char[]{0x67d4,0x5fdb,0x67b3,0x1ab7}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9847,0xbf03,0x74a0,0xfb6e,0x567c,0x517b}, new char[]{0x5128,0x982e,0xbf6f,0x74c5,0xfb00,0x5608}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3bb,0x79dd,0x8397,0x3942,0x75db}, new char[]{0x83db,0x3927,0x79ba,0xd3d2,0x75af}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0573,0xa21a,0xc6dc,0x3d45,0xca55,0x796e,0x22f0}, new char[]{0xca05,0xc6ae,0x051c,0x2284,0xa27f,0x3d26,0x791a}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc232,0x0f9b,0xbafd,0x4e50}, new char[]{0xbabf,0xc25d,0x0fef,0x4e38}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x79b3,0x332c,0xeda0,0x0590,0x2489,0xa765,0x4310,0xecc4,0xc756}, new char[]{0x24c6,0x79dd,0xa709,0x4369,0xece4,0x05c3,0xedc5,0xc73a,0x334a}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06ed,0x7621,0xcb3e,0x8dd8,0xceb9,0x7fa3,0xef53,0xadcd,0x66f1,0xcc91}, new char[]{0xccde,0x7fcd,0x764d,0xadb4,0xef73,0x8d9d,0xced7,0x6694,0x0680,0xcb47}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x36b8,0xc1f1,0x2f92,0x333b,0xef3a,0x3aa2,0x6160,0xf185,0xa7a6,0x1fa8}, new char[]{0x612d,0x36d1,0xef54,0xc1d1,0x1fec,0x3ac3,0x2fff,0xf1e4,0x335c,0xa7c3}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa2c8,0xeafc,0x1169,0xf058,0xa3f6,0x0c42,0xc932,0xb91b,0xf5b3,0xf3fb}, new char[]{0x1124,0xa397,0xf383,0xf593,0xc976,0xa2a9,0x0c2f,0xb97a,0xea9b,0xf03d}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe912,0x6c72,0x5821,0xd09d,0xc3be,0x3b16}, new char[]{0xc3fc,0x584d,0x3b79,0xd0fe,0xe979,0x6c01}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb6e,0x935d,0xb4ab,0x5c0f}, new char[]{0xeb22,0xb4c4,0x5c68,0x932e}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc9cc,0x8616,0x25ba,0xb2ed,0xb0fc,0x6c7b}, new char[]{0x6c2b,0xb281,0x8677,0xc9a2,0xb097,0x25c9}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe8db,0xef70,0x5a4d,0x1d99,0x8b27,0x57e6,0x2a64,0xfcfd,0x217a,0xb8d4,0xbe8b}, new char[]{0xb897,0xbee4,0x2a06,0x1dfb,0xef1c,0x211f,0x5a3e,0xfc89,0x8b48,0x5788,0xe8be}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x979a,0xf479,0x094b,0x8e9f,0xa180,0x4bed,0x6c0a,0x261d}, new char[]{0xf436,0x267f,0xa1f3,0x97f3,0x6c6e,0x8ef6,0x4b8c,0x0925}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc2e7,0x88b3,0x173a,0xcb83,0xaaec,0xb008,0xe15d,0xddac,0xec6b,0xff98}, new char[]{0xaaa1,0xcbe2,0xec13,0x8893,0xc2a5,0xddc0,0xfff7,0xe13e,0x1751,0xb07b}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf408,0x3bed,0xc869,0x4660,0xc667}, new char[]{0xc82d,0x3b88,0xc60b,0xf469,0x4619}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc725,0xee5b,0xe5e8,0x0758}, new char[]{0xe5bb,0x072f,0xee3a,0xc755}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f07,0x3fac,0x2ad0,0x2bf2,0xd241,0x08a4}, new char[]{0x2bbc,0x2abf,0x6f75,0x3fc1,0xd220,0x08c8}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2144,0xd849,0xd580,0x28b2}, new char[]{0xd807,0x28dd,0x212a,0xd5e5}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe626,0x94f4,0x34ca,0x1084,0x6a47}, new char[]{0x94a6,0x34ab,0xe648,0x6a20,0x10e1}).toString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5357,0x77d6,0x2737}, new char[]{0x2771,0x5318,0x7780,0x62bf}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2c91,0x5b1a,0x731c,0x8110,0x0c06,0x45e2,0x7028,0xe4f0,0x9841,0x4cd3,0x39e6,0x93f0,0xb886,0xc3fd}, new char[]{0x2cd0,0xb8f3,0xc389,0x458d,0x7008,0x734f,0xe491,0x8176,0x3983,0x93d0,0x0c45,0x5b7b,0x9833,0x4ca7}).toString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x109f,0x8845,0x4d38,0xaa07,0x00b4,0xb704,0xcafa,0x3240,0x4522,0xad1a,0x1eb2,0x4a0a,0xf779,0x7811,0xf628,0xf6d6,0x403c,0x8b79,0xf0e8,0xe7b6,0x86ea,0x9121,0xa664,0xaf5b,0xdc60,0x41ee,0xb5df,0xb6f0,0xf149,0xa079,0xc28f,0x0b81,0xa27d,0x20b9,0x6edb,0x06bd,0x81fd,0x6815,0xe2c2,0x8305,0x5fa3,0x0498,0xc644,0x79a3,0x959c,0x691f,0x7205,0x9985,0xf1fb,0xf664,0xdd6e,0x6827,0x6665,0xb646,0xeb26,0xab59,0xf1cf,0x1376,0x8e3b}, new char[]{0x5fe1,0x00d8,0xf194,0x8b1a,0xa012,0x7862,0x6645,0x6970,0x6841,0xca9c,0xdc40,0xf717,0xdd0b,0x8e5a,0x04ea,0x819f,0x4d41,0xa644,0xb58b,0x1efc,0x6841,0x95bc,0xf1a2,0x20d0,0x836b,0x6ebe,0xaf38,0x0be0,0xf616,0xe2b6,0x3233,0xc2af,0xf08a,0x868f,0x4188,0x4053,0xeb54,0x99e0,0xf608,0x9155,0xaa6f,0xb623,0xf6af,0xe796,0x4540,0x8829,0x726a,0xa20a,0x1356,0x4a7f,0xad6a,0xb6d0,0xb76b,0xf127,0xab79,0x10e6,0xc62b,0x06c8,0x798d}).toString();
    }
}
