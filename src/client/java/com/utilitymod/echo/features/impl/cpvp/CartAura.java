package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.ServerRotations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

/**
 * OpenPrestige Cart Aura: breaks TNT minecarts in reach with your sword before they can go off.
 * Aims server-side at the cart, swaps to the sword, waits the reaction/swap delays, hits, swaps back.
 * (OpenPrestige's "Humanized Aim" jitter option is intentionally not ported.)
 */
public class CartAura extends Feature {
    private final RangeSetting reaction = new RangeSetting(LT.L1, 80f, 160f, 0f, 1000f, 1f);
    private final FloatSetting swapDelay = new FloatSetting(LT.L2, 50f, 0f, 300f, 1f);
    private final BoolSetting swapBack = new BoolSetting(LT.L3, true);
    private final FloatSetting swapBackDelay = new FloatSetting(LT.L4, 50f, 0f, 300f, 1f);
    private final RangeSetting attackDelay = new RangeSetting(LT.L5, 50f, 50f, 0f, 1000f, 1f);
    private final FloatSetting targetFov = new FloatSetting(LT.L6, 180f, 30f, 360f, 1f);

    private TntMinecartEntity target;
    private long hitAllowedAt, swappedAt, nextAttackAt, swapBackAt;
    private int home = -1;

    public CartAura() {
        super(new FeatureInfo(LT.L7, LT.L8, Category.COMBAT));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        back();
        target = null;
    }

    private void back() {
        if (home >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(home);
        home = -1;
        swapBackAt = 0;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        if (isNull() || mc.currentScreen != null) return;
        long now = System.currentTimeMillis();
        if (swapBackAt > 0 && now >= swapBackAt) back();

        if (target == null || !valid(target)) {
            target = acquire();
            if (target == null) return;
            hitAllowedAt = Cpvp.next(reaction);
        }
        var box = target.getBoundingBox();
        ServerRotations.lookAt(this, box.getCenter(), .6f, 15);   // aim tracks immediately, only the hit waits

        int sword = swordSlot();
        if (sword < 0) return;
        if (Cpvp.selected() != sword) {
            if (home < 0) home = Cpvp.selected();
            mc.player.getInventory().setSelectedSlot(sword);
            swappedAt = now;
            swapBackAt = 0;
            return;   // let the swap settle at least one tick
        }
        if (now < hitAllowedAt || now < nextAttackAt || now - swappedAt < swapDelay.getValue()) return;
        if (!ServerRotations.ownedBy(this) || !ServerRotations.sentHits(box)) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        nextAttackAt = Cpvp.next(attackDelay);
        if (swapBack.getValue() && home >= 0) swapBackAt = now + Math.round(swapBackDelay.getValue());
    }

    private boolean valid(Entity e) {
        if (e.isRemoved() || !e.isAlive()) return false;
        double reach = mc.player.getEntityInteractionRange();
        return mc.player.getEyePos().squaredDistanceTo(e.getBoundingBox().getCenter()) <= reach * reach;
    }

    private TntMinecartEntity acquire() {
        TntMinecartEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof TntMinecartEntity cart) || !valid(cart)) continue;
            if (targetFov.getValue() < 360 && ServerRotations.yawOffset(cart.getBoundingBox().getCenter()) > targetFov.getValue() / 2) continue;
            double d = mc.player.squaredDistanceTo(cart);
            if (d < bd) { bd = d; best = cart; }
        }
        return best;
    }

    private int swordSlot() {
        var inv = mc.player.getInventory();
        if (inv.getStack(Cpvp.selected()).isIn(ItemTags.SWORDS)) return Cpvp.selected();
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isIn(ItemTags.SWORDS)) return i;
        return -1;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc94e,0x3ecb,0x0832,0x07da,0xeede,0x4c92,0x3338,0xe3bb,0x33d1,0xff87,0xc298,0x620b,0x3102,0xf747}, new char[]{0x4cc0,0xffe2,0x3359,0x07b9,0x33a5,0xe3d2,0xc921,0x316c,0x622b,0xc2dc,0x0857,0xf72b,0xeebf,0x3eb2}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf0ed,0x5af6,0x1fb6,0x5469,0xeae8,0x37f8,0x5803,0xe4ef,0xa12b,0x5f6d}, new char[]{0x5f3e,0x5a81,0xe48e,0xf09d,0xa10b,0x5847,0x540c,0x1fda,0xea89,0x3781}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2405,0xf96a,0x9446,0x8621,0xd2ef,0xc321,0x9a91,0x6f6a,0x75f4}, new char[]{0xd2bc,0x6f1d,0xc340,0xf91a,0x2425,0x75b6,0x9427,0x8642,0x9afa}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2983,0x73b4,0xab94,0xcc4c,0x992d,0x0310,0xc5aa,0x663b,0xe672,0x9f0e,0x795f,0x13dc,0xbc51,0xc0fd,0x7ac4}, new char[]{0xabc7,0x13ab,0xc09c,0x73c4,0x0330,0x791d,0xc5cb,0x29e0,0xe619,0x9f2e,0x667f,0x9948,0x7aa8,0xcc2d,0xbc28}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb719,0xaae9,0x920a,0x9188,0xcac3,0x0851,0xfcd9,0xbe74,0xf709,0x3118,0xa6e1,0x86c6}, new char[]{0xb758,0xfcad,0xf77d,0xa680,0x0832,0xbe1f,0xaac9,0x91cc,0xcaa6,0x3174,0x926b,0x86bf}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb519,0x67af,0xa86d,0xfa23,0x5fc8,0x156d,0x9e6b,0x44c9,0x89e3,0xbb56}, new char[]{0xfa77,0xa80c,0x8991,0x44ae,0x67ca,0xb56d,0x154d,0x5f8e,0xbb19,0x9e3d}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb23f,0xa69a,0xcb2e,0xc984,0x14b8,0x7e2a,0x3547,0x993f,0xc548}, new char[]{0x14fb,0xc9e5,0xb24d,0x994b,0xa6ba,0x3506,0xcb5b,0x7e58,0xc529}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3c4d,0x4db2,0xd4b8,0xc6f0,0x4a8e,0xefa4,0xe983,0x85d9,0xef14,0x64e3,0x5025,0xda56,0x6559,0x2daa,0x9c72,0x3a4b,0x4c24,0x5d6a,0x338c,0x27db,0xea0d,0xdefb,0x6bc6,0x79ae,0xf3ce,0x1345,0xa2db,0x9845,0xed12,0xb391,0x96a3,0x08a7,0xe116,0x18b2,0xeecc,0xc0bb,0xb3d7,0xe4a7,0x3409,0x9046,0x1345,0xbb39,0x7f90,0x5e4c}, new char[]{0xe154,0xd4ca,0x7ff5,0xbb58,0xc0d0,0xc683,0x4aae,0x6537,0x85bc,0xda37,0x6bb4,0x1327,0xf3b7,0x9c52,0x5071,0x4c6a,0x5e18,0xa2fb,0x27b6,0xef7d,0x3467,0x2dcf,0x4dd1,0xed73,0x1337,0xefd0,0xea7e,0xeeec,0x33fb,0x648a,0x08d3,0x79c6,0x3a6b,0xde82,0xb3b8,0xe9f6,0x9034,0x9683,0xb3e2,0x3c3a,0x5d05,0x18c0,0xe4c3,0x986b}).toString();
    }
}
