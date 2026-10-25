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
    private final RangeSetting reaction = new RangeSetting("Reaction Delay", 80f, 160f, 0f, 1000f, 1f);
    private final FloatSetting swapDelay = new FloatSetting("Swap Delay", 50f, 0f, 300f, 1f);
    private final BoolSetting swapBack = new BoolSetting("Swap Back", true);
    private final FloatSetting swapBackDelay = new FloatSetting("Swap Back Delay", 50f, 0f, 300f, 1f);
    private final RangeSetting attackDelay = new RangeSetting("Attack Delay", 50f, 50f, 0f, 1000f, 1f);
    private final FloatSetting targetFov = new FloatSetting("Target FOV", 180f, 30f, 360f, 1f);

    private TntMinecartEntity target;
    private long hitAllowedAt, swappedAt, nextAttackAt, swapBackAt;
    private int home = -1;

    public CartAura() {
        super(new FeatureInfo("Cart Aura", "Breaks nearby TNT minecarts with your sword.", Category.COMBAT));
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
}
