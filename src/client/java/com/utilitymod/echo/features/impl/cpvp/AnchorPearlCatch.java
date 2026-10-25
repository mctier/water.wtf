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
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.ServerRotations;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.utils.simulation.PearlSimulation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * LegacyPort Anchor Pearl Catch: predicts where an enemy pearl lands, sets up a charged anchor (with an
 * optional safe cover) next to the landing spot while the pearl is still flying, then detonates it once the
 * enemy has landed and can take damage. All aiming is server-side when Rotate Silently is on.
 */
public class AnchorPearlCatch extends Feature {
    private final FloatSetting scanRange = new FloatSetting(new EncryptedString(new char[]{0xa8a8, 0x9626, 0x82c3, 0x379e, 0x0b3d, 0x1c95, 0x2279, 0xb15d, 0xd129, 0xbc07}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), 48f, 16f, 96f, 1f);
    private final FloatSetting minDamage = new FloatSetting(new EncryptedString(new char[]{0xa8a2, 0x9626, 0x82cc, 0x37fa, 0x0b31, 0x1c95, 0x2276, 0xb16e, 0xd137, 0xbc49}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), 15f, 0f, 40f, .5f);
    private final FloatSetting maxSelf = new FloatSetting(new EncryptedString(new char[]{0x82cb, 0x76b1, 0x0b30, 0xeb8c, 0x9607, 0xa8aa, 0x37ed, 0x1cd2, 0xfce0, 0xbc49, 0xb16a, 0xd137, 0x6557, 0x2260, 0x11f1}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4}), 10f, 0f, 36f, .5f);
    private final BoolSetting antiSuicide = new BoolSetting(new EncryptedString(new char[]{0xeb84, 0x0b29, 0x1c91, 0x962a, 0x226c, 0xd13b, 0x11f4, 0x82c4, 0xa8a5, 0x379e, 0xb15c, 0xbc00}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), true);
    private final BoolSetting blipHurt = new BoolSetting(new EncryptedString(new char[]{0x11fb, 0xa8aa, 0xbc08, 0x2275, 0x1c9b, 0x82f9, 0x0b7c, 0xd13e, 0xb16a, 0x9620, 0x37d9}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}), true);
    private final BoolSetting wrapCare = new BoolSetting(new EncryptedString(new char[]{0x11e2, 0xa8aa, 0xbc0c, 0x227e, 0x1c9a, 0x82ce, 0x0b32, 0xd129, 0xb14e, 0x962c, 0x379e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}), true);
    private final ModeSetting safeBlock = new ModeSetting(new EncryptedString(new char[]{0xa8aa, 0x9628, 0x82c2, 0x379e, 0x0b30, 0x1c91, 0x227e, 0xb14d, 0xd129, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), new EncryptedString(new char[]{0x0b33, 0x1c97, 0x82c3, 0x37cd, 0xbc1e, 0xa8a7, 0xb17b, 0x2277, 0xd13d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2}), new EncryptedString(new char[]{0x0b33, 0x1c97, 0x82c3, 0x37cd, 0xbc1e, 0xa8a7, 0xb17b, 0x2277, 0xd13d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2}), new EncryptedString(new char[]{0x226b, 0xd135, 0xb166, 0x0b3d, 0xbc00, 0x37da, 0xa8a9, 0x82c3}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}));
    private final BoolSetting rotate = new BoolSetting(new EncryptedString(new char[]{0x82fe, 0x76ad, 0x0b7c, 0xeb8f, 0x962f, 0xa8a4, 0x37ca, 0x1c9b, 0xfceb, 0xbc08, 0xb16a, 0xd128, 0x6542, 0x226c, 0x11f5}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4}), true);
    private final FloatSetting fov = new FloatSetting(new EncryptedString(new char[]{0xd129, 0x0b7c, 0xb17b, 0x37d0, 0x962e, 0x2274, 0xeba7, 0xfcd1, 0x82ec, 0xbc0c, 0xa8a2, 0x1c9b, 0x11b0, 0x6579}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87}), 180f, 0f, 180f, 1f);
    private final BoolSetting swapBack = new BoolSetting(new EncryptedString(new char[]{0x0b3d, 0x1c99, 0x82ce, 0x379e, 0xbc19, 0xa8bc, 0xb14d, 0x2279, 0xd129}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2}), true);
    private final RangeSetting placeDelay = new RangeSetting(new EncryptedString(new char[]{0x11e9, 0xa8a7, 0xbc0a, 0x2279, 0x1c9e, 0x82c8, 0x0b18, 0xd12a, 0xb12f, 0x9622, 0x37db}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}), 0f, 0f, 0f, 500f, 1f);
    private final RangeSetting chargeDelay = new RangeSetting(new EncryptedString(new char[]{0xeb98, 0x0b7c, 0x1c97, 0x962f, 0x2279, 0xd139, 0x11f1, 0x82e9, 0xa8a3, 0x37d9, 0xb16a, 0xbc1b}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), 20f, 20f, 0f, 500f, 1f);
    private final RangeSetting detonateDelay = new RangeSetting(new EncryptedString(new char[]{0xd13e, 0x0b28, 0xb16e, 0x37d0, 0x9607, 0x226c, 0xeb8d, 0xfcfe, 0x82c8, 0xbc06, 0xa8ae, 0x1cd2, 0x11f5, 0x6557}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87}), 40f, 40f, 0f, 500f, 1f);
    private final BoolSetting render = new BoolSetting(new EncryptedString(new char[]{0xa8ae, 0xb17d, 0xd128, 0x2276, 0x37db, 0xbc0d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}), true);
    private final ColorSetting anchorColor = new ColorSetting(new EncryptedString(new char[]{0xeb93, 0x0b7c, 0x1c9d, 0x962f, 0x227b, 0xd13b, 0x11ff, 0x82ee, 0xa8a5, 0x37d1, 0xb17d, 0xbc01}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), 255, 90, 60, 255);
    private final ColorSetting safeColor = new ColorSetting(new EncryptedString(new char[]{0xa8aa, 0x9631, 0x82c1, 0x379e, 0x0b33, 0x1c9d, 0x227e, 0xb14c, 0xd129, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), 255, 220, 90, 255);
    private final ColorSetting landingColor = new ColorSetting(new EncryptedString(new char[]{0x2276, 0x962c, 0x11fc, 0xeb8e, 0x828d, 0x0b3b, 0xa8aa, 0xbc0d, 0x1cb1, 0xd136, 0x37d7, 0x6544, 0xb161}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536}), 80, 170, 255, 255);

    private enum Step { PLACE, COVER, CHARGE, WAIT, DETONATE }

    private EnderPearlEntity pearl;
    private PlayerEntity victim;
    private Vec3d landing;
    private BlockPos anchor, cover;
    private Step step;
    private long readyAt, landedAt;
    private int home = -1;

    public AnchorPearlCatch() {
        super(new FeatureInfo(new EncryptedString(new char[]{0x6516, 0xa8a5, 0xfcc4, 0x76b5, 0xf051, 0xeb8d, 0x9622, 0x37d1, 0x82fd, 0xb17d, 0xbc01, 0x4a09, 0x1c97, 0xc5d3, 0x227b, 0xd13b, 0x11e2, 0x0b7c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb}), LT.L1, Category.COMBAT));
        Cpvp.track();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }

    private void reset() {
        if (home >= 0 && swapBack.getValue() && mc.player != null) mc.player.getInventory().setSelectedSlot(home);
        home = -1;
        pearl = null; victim = null; landing = null; anchor = null; cover = null; step = null; landedAt = 0;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        if (isNull() || mc.currentScreen != null) return;
        if (step == null && !acquire()) return;
        if (step == null) return;
        if (victim == null || !victim.isAlive() || victim.squaredDistanceTo(mc.player) > 400) { reset(); return; }
        if (pearl != null && !pearl.isRemoved() && step.ordinal() < Step.WAIT.ordinal()) landing = predict(pearl);

        if (System.currentTimeMillis() < readyAt) { holdAim(); return; }
        if (home < 0) home = Cpvp.selected();
        switch (step) {
            case PLACE -> {
                if (Cpvp.isAnchor(anchor)) { step = wrapCare.getValue() ? Step.COVER : Step.CHARGE; return; }
                int slot = Cpvp.hotbar(Items.RESPAWN_ANCHOR);
                if (slot < 0 || !Cpvp.canPlace(anchor)) { reset(); return; }
                Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
                var hit = com.utilitymod.echo.utils.blocks.BlockUtils.findPlacementHit(anchor, false);
                if (hit == null) { reset(); return; }
                if (!Cpvp.aimed(this, rotate.getValue(), fov.getValue(), hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
                if (Cpvp.place(anchor)) { readyAt = Cpvp.next(placeDelay); step = wrapCare.getValue() ? Step.COVER : Step.CHARGE; }
            }
            case COVER -> {
                if (cover == null) cover = Cpvp.coverPos(anchor);
                int slot = Cpvp.hotbar(Cpvp.coverItem(safeBlock.getValue()));
                if (cover == null || slot < 0) { step = Step.CHARGE; return; }
                var hit = com.utilitymod.echo.utils.blocks.BlockUtils.findPlacementHit(cover, false);
                if (hit == null) { step = Step.CHARGE; return; }
                Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
                if (!Cpvp.aimed(this, rotate.getValue(), fov.getValue(), hit.hitResult().getPos(), hit.hitResult().getBlockPos())) return;
                if (Cpvp.place(cover)) { readyAt = Cpvp.next(chargeDelay); step = Step.CHARGE; }
            }
            case CHARGE -> {
                if (Cpvp.charges(anchor) > 0) { step = Step.WAIT; return; }
                int slot = Cpvp.hotbar(Items.GLOWSTONE);
                if (slot < 0 || !Cpvp.isAnchor(anchor)) { reset(); return; }
                Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
                if (!Cpvp.aimed(this, rotate.getValue(), fov.getValue(), Cpvp.faceHit(anchor).getPos(), anchor)) return;
                if (Cpvp.use(anchor)) step = Step.WAIT;
            }
            case WAIT -> {
                boolean landed = (pearl == null || pearl.isRemoved()) && landing != null && victim.getEntityPos().distanceTo(landing) < 2.5;
                if (pearl != null && pearl.isRemoved() && !landed && System.currentTimeMillis() - readyAt > 1500) { reset(); return; }
                if (!landed) { holdAim(); return; }
                if (landedAt == 0) { landedAt = System.currentTimeMillis(); readyAt = Cpvp.next(detonateDelay); return; }
                step = Step.DETONATE;
            }
            case DETONATE -> {
                if (!Cpvp.isAnchor(anchor) || Cpvp.charges(anchor) <= 0) { reset(); return; }
                if (blipHurt.getValue() && victim.hurtTime > 0) { holdAim(); return; }
                float self = Cpvp.damage(mc.player, anchor);
                if (self > maxSelf.getValue() || (antiSuicide.getValue() && Cpvp.lethal(self))) { reset(); return; }
                int slot = Cpvp.hotbar(Items.TOTEM_OF_UNDYING);
                if (slot < 0) for (int i = 0; i < 9; i++) if (!mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) { slot = i; break; }
                if (slot < 0) return;
                Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
                if (!Cpvp.aimed(this, rotate.getValue(), fov.getValue(), Cpvp.faceHit(anchor).getPos(), anchor)) return;
                if (Cpvp.use(anchor)) reset();
            }
        }
    }

    private void holdAim() {
        if (!rotate.getValue() || anchor == null) return;
        ServerRotations.lookAt(this, Cpvp.isAnchor(anchor) ? Cpvp.faceHit(anchor).getPos() : Cpvp.placeAim(anchor), .55f, 10);
    }

    /** Lock onto the first enemy pearl in range whose landing we can cover with an anchor in reach. */
    private boolean acquire() {
        double r2 = scanRange.getValue() * scanRange.getValue();
        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof EnderPearlEntity p) || p.squaredDistanceTo(mc.player) > r2) continue;
            if (!(p.getOwner() instanceof PlayerEntity owner) || owner == mc.player
                    || !com.utilitymod.echo.utils.combat.TargetUtils.isTargetAllowed(owner)) continue;
            Vec3d land = predict(p);
            if (land == null) continue;
            BlockPos spot = spotFor(land);
            if (spot == null) continue;
            pearl = p; victim = owner; landing = land; anchor = spot; cover = null;
            step = Step.PLACE;
            readyAt = 0;
            return true;
        }
        return false;
    }

    private static Vec3d predict(EnderPearlEntity p) {
        PearlSimulation sim = new PearlSimulation(p.getEntityPos(), p.getVelocity(), p);
        var hit = sim.simulateUntilHit(200);
        return hit == null ? null : sim.getPosition();
    }

    private BlockPos spotFor(Vec3d land) {
        BlockPos base = BlockPos.ofFloored(land);
        BlockPos best = null;
        float bestScore = -1;
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos p = base.add(dx, dy, dz);
            if (p.equals(base) || p.equals(base.up())) continue;   // never inside the landing spot
            if (!Cpvp.inReach(p)) continue;
            boolean existing = Cpvp.isAnchor(p);
            if (!existing && !Cpvp.canPlace(p)) continue;
            float dmg = Cpvp.damageAt(land, p), self = Cpvp.damage(mc.player, p);
            if (dmg < minDamage.getValue() || self > maxSelf.getValue()) continue;
            if (antiSuicide.getValue() && Cpvp.lethal(self)) continue;
            float score = dmg - self * .5f;
            if (score > bestScore) { bestScore = score; best = p.toImmutable(); }
        }
        return best;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onRender(EventRender3D e) {
        if (!render.getValue() || isNull()) return;
        var out = e.getDraw3D().getMinecraftTarget();
        if (landing != null) out.box(landing.x - .3, landing.y, landing.z - .3, .6, 1.8, .6, Cpvp.argb(landingColor, 255));
        if (anchor != null) out.box(anchor.getX(), anchor.getY(), anchor.getZ(), 1, 1, 1, Cpvp.argb(anchorColor, 255));
        if (cover != null) out.box(cover.getX(), cover.getY(), cover.getZ(), 1, 1, 1, Cpvp.argb(safeColor, 255));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8957,0xfda0,0x63ce,0x15ac,0xc804,0x5c32,0x3cff,0xb3c4,0x35f9,0x92dd,0x2ff2,0x26ad,0x0f83,0x4346,0xd063,0x9b64,0x7fcf,0x28f4,0x7b97,0x1692,0x4139,0xa393,0xe002,0x9088,0xf95e,0x63be,0xe08b,0x8f0d,0xb89c,0x58dd,0x22b2,0x1492,0xffa1,0xbb28,0x1f50,0x0914,0x8f96,0x8d15,0x141e,0xd75e,0x76a5,0x809e,0x7cd9,0x9b47,0xc850,0x4916,0xece7,0xd82a,0x40b8,0x52a5,0xc529,0x6d46,0x2a37,0x24ec,0xbb99,0x85db,0x7fec,0xe445,0xa7bd,0x9fba,0x7708,0x43b5,0x82ba,0x50a3,0xaf5a,0x1334,0x666c,0x3ea7,0x4099}, new char[]{0x3cbc,0x92bc,0x4962,0x50c0,0xfdc8,0xc54c,0xd010,0x639e,0x9b22,0x26c3,0xa3f6,0xc83d,0xbbe0,0xaf7a,0xe435,0xbb4d,0xf93f,0x7fbd,0xa7d1,0x1347,0x8f2d,0x661b,0x2f9b,0x58a9,0x2484,0x6d66,0x0fe2,0x143e,0x7778,0xec95,0xb8f9,0x229f,0x4325,0xd736,0x5c53,0xe0f9,0x8d72,0x16f7,0x63aa,0x90a8,0x3ec6,0x82d4,0x0977,0x9b0c,0x9fd5,0x7f9e,0xe022,0x2a56,0x1f24,0x4395,0xd85e,0x15c4,0x40dd,0x893e,0xc876,0x80be,0x52c9,0x76c4,0x4157,0x85bf,0x7cb0,0xffcf,0xb3a3,0x8fb6,0x40ea,0x3589,0x14fd,0x2880,0x7bb9}).toString();
    }
}
