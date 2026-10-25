package com.utilitymod.echo.features.impl.cpvp;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * LegacyPort Auto Anchor: picks the best anchor spot around the nearest enemy (target damage vs self
 * damage), places it (unless Only Existing), optionally drops a safe cover between you and it, charges it
 * with glowstone and detonates it - each step after its random delay, aimed server-side when Rotate
 * Silently is on.
 */
public class Haldex extends Feature {
    private final FloatSetting targetRange = new FloatSetting(new EncryptedString(new char[]{0xeb84, 0x0b7c, 0x1c93, 0x962d, 0x226a, 0xd12e, 0x11f7, 0x82ff, 0xa8aa, 0x37db, 0xb17b, 0xbc0e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), 10f, 1f, 16f, .5f);
    private final FloatSetting minDamage = new FloatSetting(new EncryptedString(new char[]{0xa8a2, 0x9626, 0x82cc, 0x37fa, 0x0b31, 0x1c95, 0x2276, 0xb16e, 0xd137, 0xbc49}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), 6f, 0f, 36f, .5f);
    private final FloatSetting maxSelf = new FloatSetting(new EncryptedString(new char[]{0x82cb, 0x76b1, 0x0b30, 0xeb8c, 0x9607, 0xa8aa, 0x37ed, 0x1cd2, 0xfce0, 0xbc49, 0xb16a, 0xd137, 0x6557, 0x2260, 0x11f1}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4}), 10f, 0f, 36f, .5f);
    private final BoolSetting antiSuicide = new BoolSetting(new EncryptedString(new char[]{0xeb84, 0x0b29, 0x1c91, 0x962a, 0x226c, 0xd13b, 0x11f4, 0x82c4, 0xa8a5, 0x379e, 0xb15c, 0xbc00}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), true);
    private final BoolSetting wrapCare = new BoolSetting(new EncryptedString(new char[]{0x11e2, 0xa8aa, 0xbc0c, 0x227e, 0x1c9a, 0x82ce, 0x0b32, 0xd129, 0xb14e, 0x962c, 0x379e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}), true);
    private final ModeSetting safeBlock = new ModeSetting(new EncryptedString(new char[]{0xa8aa, 0x9628, 0x82c2, 0x379e, 0x0b30, 0x1c91, 0x227e, 0xb14d, 0xd129, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), new EncryptedString(new char[]{0x0b33, 0x1c97, 0x82c3, 0x37cd, 0xbc1e, 0xa8a7, 0xb17b, 0x2277, 0xd13d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2}), new EncryptedString(new char[]{0x0b33, 0x1c97, 0x82c3, 0x37cd, 0xbc1e, 0xa8a7, 0xb17b, 0x2277, 0xd13d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2}), new EncryptedString(new char[]{0x226b, 0xd135, 0xb166, 0x0b3d, 0xbc00, 0x37da, 0xa8a9, 0x82c3}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}));
    private final BoolSetting rotate = new BoolSetting(new EncryptedString(new char[]{0x82fe, 0x76ad, 0x0b7c, 0xeb8f, 0x962f, 0xa8a4, 0x37ca, 0x1c9b, 0xfceb, 0xbc08, 0xb16a, 0xd128, 0x6542, 0x226c, 0x11f5}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4}), true);
    private final BoolSetting swapBack = new BoolSetting(new EncryptedString(new char[]{0x0b3d, 0x1c99, 0x82ce, 0x379e, 0xbc19, 0xa8bc, 0xb14d, 0x2279, 0xd129}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2}), true);
    private final RangeSetting placeDelay = new RangeSetting(new EncryptedString(new char[]{0x11e9, 0xa8a7, 0xbc0a, 0x2279, 0x1c9e, 0x82c8, 0x0b18, 0xd12a, 0xb12f, 0x9622, 0x37db}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}), 0f, 0f, 0f, 500f, 1f);
    private final RangeSetting chargeDelay = new RangeSetting(new EncryptedString(new char[]{0xeb98, 0x0b7c, 0x1c97, 0x962f, 0x2279, 0xd139, 0x11f1, 0x82e9, 0xa8a3, 0x37d9, 0xb16a, 0xbc1b}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), 20f, 20f, 0f, 500f, 1f);
    private final RangeSetting detonateDelay = new RangeSetting(new EncryptedString(new char[]{0xd13e, 0x0b28, 0xb16e, 0x37d0, 0x9607, 0x226c, 0xeb8d, 0xfcfe, 0x82c8, 0xbc06, 0xa8ae, 0x1cd2, 0x11f5, 0x6557}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87}), 20f, 20f, 0f, 500f, 1f);
    private final BoolSetting onlyExisting = new BoolSetting(new EncryptedString(new char[]{0x2274, 0x9637, 0x11f9, 0xeb8f, 0x82c4, 0x0b24, 0xa8a5, 0xbc10, 0x1c81, 0xd135, 0x379e, 0x6551, 0xb14a}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536}), false);
    private final BoolSetting render = new BoolSetting(new EncryptedString(new char[]{0xa8ae, 0xb17d, 0xd128, 0x2276, 0x37db, 0xbc0d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f}), true);
    private final ColorSetting anchorColor = new ColorSetting(new EncryptedString(new char[]{0xeb93, 0x0b7c, 0x1c9d, 0x962f, 0x227b, 0xd13b, 0x11ff, 0x82ee, 0xa8a5, 0x37d1, 0xb17d, 0xbc01}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), 255, 90, 60, 255);
    private final ColorSetting safeColor = new ColorSetting(new EncryptedString(new char[]{0xa8aa, 0x9631, 0x82c1, 0x379e, 0x0b33, 0x1c9d, 0x227e, 0xb14c, 0xd129, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643}), 255, 220, 90, 255);
    private final ColorSetting targetColor = new ColorSetting(new EncryptedString(new char[]{0xeb93, 0x0b7c, 0x1c9d, 0x962f, 0x226a, 0xd12e, 0x11ff, 0x82ee, 0xa8aa, 0x37db, 0xb17b, 0xbc0e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1}), 255, 80, 80, 255);

    private enum Step { PLACE, COVER, CHARGE, DETONATE }

    private PlayerEntity target;
    private BlockPos anchor, cover;
    private Step step = Step.PLACE;
    private long readyAt;
    private int homeSlot = -1;

    public Haldex() {
        super(new FeatureInfo(new EncryptedString(new char[]{0x11e2, 0xa8be, 0xbc06, 0x226c, 0x1c9a, 0x82ce, 0x0b32, 0xd13b, 0xb14e, 0x962c, 0x379e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}), "Places, covers, charges and detonates respawn anchors on the nearest enemy.", Category.COMBAT));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }

    private void reset() {
        if (homeSlot >= 0 && swapBack.getValue() && mc.player != null) mc.player.getInventory().setSelectedSlot(homeSlot);
        homeSlot = -1;
        anchor = cover = null;
        target = null;
        step = Step.PLACE;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onInput(EventHandleInput.Early e) {
        if (isNull() || mc.currentScreen != null) return;
        target = Cpvp.nearestEnemy(targetRange.getValue());
        if (target == null) { if (anchor != null) reset(); return; }
        if (anchor == null || !stillGood(anchor)) { anchor = pick(); step = Step.PLACE; cover = null; }
        if (anchor == null) { if (homeSlot >= 0) reset(); return; }
        if (System.currentTimeMillis() < readyAt) { holdAim(); return; }
        if (homeSlot < 0) homeSlot = Cpvp.selected();

        switch (step) {
            case PLACE -> {
                if (Cpvp.isAnchor(anchor)) { step = wrapCare.getValue() ? Step.COVER : Step.CHARGE; return; }
                if (onlyExisting.getValue()) { anchor = null; return; }
                int slot = Cpvp.hotbar(Items.RESPAWN_ANCHOR);
                if (slot < 0 || !Cpvp.canPlace(anchor)) { anchor = null; return; }
                Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
                if (!Cpvp.aimed(this, rotate.getValue(), 180, Cpvp.placeAim(anchor), anchorSupport(anchor))) return;
                if (Cpvp.place(anchor)) { readyAt = Cpvp.next(placeDelay); step = wrapCare.getValue() ? Step.COVER : Step.CHARGE; }
            }
            case COVER -> {
                if (cover == null) cover = Cpvp.coverPos(anchor);
                int slot = Cpvp.hotbar(Cpvp.coverItem(safeBlock.getValue()));
                if (cover == null || slot < 0) { step = Step.CHARGE; return; }
                Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
                if (!Cpvp.aimed(this, rotate.getValue(), 180, Cpvp.placeAim(cover), anchorSupport(cover))) return;
                if (Cpvp.place(cover)) { readyAt = Cpvp.next(chargeDelay); step = Step.CHARGE; }
            }
            case CHARGE -> {
                if (Cpvp.charges(anchor) > 0) { step = Step.DETONATE; return; }
                int slot = Cpvp.hotbar(Items.GLOWSTONE);
                if (slot < 0) { anchor = null; return; }
                Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
                if (!Cpvp.aimed(this, rotate.getValue(), 180, Cpvp.faceHit(anchor).getPos(), anchor)) return;
                if (Cpvp.use(anchor)) { readyAt = Cpvp.next(detonateDelay); step = Step.DETONATE; }
            }
            case DETONATE -> {
                if (!Cpvp.isAnchor(anchor)) { anchor = null; step = Step.PLACE; return; }
                if (Cpvp.charges(anchor) <= 0) { step = Step.CHARGE; return; }
                float self = Cpvp.damage(mc.player, anchor);
                if (antiSuicide.getValue() && Cpvp.lethal(self)) return;
                int slot = detonateSlot();
                if (slot < 0) return;
                Cpvp.select(slot, new EncryptedString(new char[]{0x2279, 0xa8bc, 0xd129, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69}));
                if (!Cpvp.aimed(this, rotate.getValue(), 180, Cpvp.faceHit(anchor).getPos(), anchor)) return;
                if (Cpvp.use(anchor)) { readyAt = Cpvp.next(placeDelay); anchor = null; cover = null; step = Step.PLACE; }
            }
        }
    }

    /** Keep the server look on the current step while waiting out a delay. */
    private void holdAim() {
        if (!rotate.getValue() || anchor == null) return;
        Vec3d p = step == Step.PLACE ? Cpvp.placeAim(anchor) : step == Step.COVER && cover != null ? Cpvp.placeAim(cover) : Cpvp.faceHit(anchor).getPos();
        com.utilitymod.echo.handlers.ServerRotations.lookAt(this, p, .55f, 10);
    }

    /** The block a placement at {@code pos} clicks (what the server look must point at). */
    private static BlockPos anchorSupport(BlockPos pos) {
        var hit = com.utilitymod.echo.utils.blocks.BlockUtils.findPlacementHit(pos, false);
        return hit == null ? pos : hit.hitResult().getBlockPos();
    }

    /** Any hotbar slot that does not charge the anchor (prefer a totem, keeping you safe while it blows). */
    private int detonateSlot() {
        int totem = Cpvp.hotbar(Items.TOTEM_OF_UNDYING);
        if (totem >= 0) return totem;
        var inv = mc.player.getInventory();
        if (!inv.getStack(Cpvp.selected()).isOf(Items.GLOWSTONE)) return Cpvp.selected();
        for (int i = 0; i < 9; i++) if (!inv.getStack(i).isOf(Items.GLOWSTONE)) return i;
        return -1;
    }

    private boolean stillGood(BlockPos p) {
        if (!Cpvp.inReach(p)) return false;
        return Cpvp.isAnchor(p) || (!onlyExisting.getValue() && Cpvp.canPlace(p));
    }

    private BlockPos pick() {
        BlockPos base = target.getBlockPos();
        BlockPos best = null;
        float bestScore = -1;
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos p = base.add(dx, dy, dz);
            if (!Cpvp.inReach(p)) continue;
            boolean existing = Cpvp.isAnchor(p);
            if (!existing && (onlyExisting.getValue() || !Cpvp.canPlace(p))) continue;
            if (!existing && p.equals(mc.player.getBlockPos())) continue;
            float dmg = Cpvp.damage(target, p), self = Cpvp.damage(mc.player, p);
            if (dmg < minDamage.getValue() || self > maxSelf.getValue()) continue;
            if (antiSuicide.getValue() && Cpvp.lethal(self)) continue;
            float score = dmg - self * .5f + (existing ? 2f : 0f);
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return best;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onRender(EventRender3D e) {
        if (!render.getValue() || isNull()) return;
        var out = e.getDraw3D().getMinecraftTarget();
        if (anchor != null) out.box(anchor.getX(), anchor.getY(), anchor.getZ(), 1, 1, 1, Cpvp.argb(anchorColor, 255));
        if (cover != null) out.box(cover.getX(), cover.getY(), cover.getZ(), 1, 1, 1, Cpvp.argb(safeColor, 255));
        if (target != null) {
            var b = target.getBoundingBox();
            out.box(b.minX, b.minY, b.minZ, b.getLengthX(), b.getLengthY(), b.getLengthZ(), Cpvp.argb(targetColor, 255));
        }
    }
}
