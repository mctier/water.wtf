package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.render.GlassRenderer;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * Target HUD: the entity under the crosshair (or the last one you hit, for a
 * short time) with face, name, health, distance and gear.
 *
 * <p>All motion is frame-rate independent (exponential smoothing on real
 * elapsed time): the panel pops in with scale + slide, health eases toward
 * its value with a lighter "damage ghost" bar trailing behind, the face
 * flashes red and shakes when the target is hurt, and switching targets
 * eases the bars instead of snapping.
 */
public class TargetHud extends Feature {
    private final FloatSetting range = new FloatSetting(LT.L1, 8f, 2f, 20f, 0.5f);
    private final IntSetting keepMs = new IntSetting(LT.L2, 2500, 0, 10000);
    private final FloatSetting speed = new FloatSetting(LT.L3, 1f, 0.3f, 3f, 0.1f);
    private final BoolSetting showGear = new BoolSetting(LT.L4, true);
    private final BoolSetting onlyPlayers = new BoolSetting(LT.L5, false);
    private final BoolSetting glassSetting = new BoolSetting(LT.L6, true);
    private final ModeSetting position = new ModeSetting(LT.L7, LT.L8, LT.L8, LT.L9, LT.L10);
    private final FloatSetting floatScale = new FloatSetting(LT.L11, 0.6f, 0.2f, 1.5f, 0.05f,
            o -> !position.is(LT.L8));
    private final FloatSetting floatHeight = new FloatSetting(LT.L12, 0.6f, 0f, 2f, 0.05f,
            o -> !position.is(LT.L8));
    private final BoolSetting turnWithTarget = new BoolSetting(LT.L13, true, o -> position.is(LT.L10));
    private final BoolSetting fadeBehindWalls = new BoolSetting(LT.L14, true, o -> position.is(LT.L10));

    /** Smoothed on-screen anchor and 3D state (avoid jitter when floating). */
    private float anchorX = Float.NaN, anchorY, turn = 1f, sight = 1f;
    private boolean occluded;
    private int raycastFrame;

    private static final int W = 150;
    private static final int H = 52;

    private LivingEntity lastHit;
    private long lastHitAt;
    private LivingEntity shown;
    private long lastFrame = System.nanoTime();

    /** 0..1 open state, smoothed health, trailing damage ghost, hurt flash. */
    private float open;
    private float health = -1f;
    private float ghost = -1f;
    private float hurtFlash;
    private int lastHurtTime;

    public TargetHud() {
        super(new FeatureInfo(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4A,0x48,0x02,0xDC,0xA7,0x79,0x74,0xD7,0xF3,0xB5}),
                LT.L15,
                Category.RENDER));
        com.utilitymod.echo.hud.HudManager.register(new Element());
    }

    /** The movable HUD element (background/glass by HudManager, contents here). */
    private final class Element extends com.utilitymod.echo.hud.HudElement {
        Element() { super(LT.L16, dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4A,0x48,0x02,0xDC,0xA7,0x79,0x74,0xD7,0xF3,0xB5}).rawValue(), 0.55f, 0.6f); }

        @Override public float width() { return W; }
        @Override public float height() { return H; }
        @Override public float radius() { return 8; }
        @Override public boolean active() { return isEnabled() && position.is(LT.L8) && shown != null && open > 0.005f; }
        @Override public float alpha() { return Math.min(1f, open * 1.4f); }
        @Override public boolean forceGlass() { return glassSetting.getValue(); }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            LivingEntity t = shown;
            if (t == null) {
                if (!preview || mc.player == null) return;
                t = mc.player; // sample data in the editor
                if (health < 0f) health = ghost = 1f;
            }
            draw(ctx, t, preview ? 1f : alpha());
        }
    }

    @EventSubscribe
    private void onAttack(EventOnAttackEntity.Post event) {
        if (event.getTarget() instanceof LivingEntity living) {
            lastHit = living;
            lastHitAt = System.currentTimeMillis();
        }
    }

    private LivingEntity pickTarget() {
        if (isNull()) return null;
        double maxSq = range.getValue() * range.getValue();
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                && valid(living, maxSq)) {
            return living;
        }
        if (lastHit != null && System.currentTimeMillis() - lastHitAt <= keepMs.getValue() && valid(lastHit, maxSq)) {
            return lastHit;
        }
        return null;
    }

    private boolean valid(LivingEntity e, double maxSq) {
        return e != mc.player && e.isAlive() && !e.isRemoved() && mc.player.squaredDistanceTo(e) <= maxSq
                && (!onlyPlayers.getValue() || e instanceof PlayerEntity);
    }

    /** Exponential approach that behaves the same at any frame rate. */
    private static float approach(float current, float target, float rate, float dt) {
        return target + (current - target) * (float) Math.exp(-rate * dt);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float k = speed.getValue();

        LivingEntity target = pickTarget();
        if (target != null) shown = target;
        open = approach(open, target != null ? 1f : 0f, 12f * k, dt);
        if (open < 0.005f && target == null) {
            open = 0f;
            shown = null;
            health = ghost = -1f;
            return;
        }
        if (shown == null) return;

        float max = Math.max(1f, shown.getMaxHealth() + shown.getAbsorptionAmount());
        float pct = Math.max(0f, Math.min(1f, (shown.getHealth() + shown.getAbsorptionAmount()) / max));
        if (health < 0f) health = ghost = pct;
        health = approach(health, pct, 14f * k, dt);
        // ghost waits, then slides down to the real value (classic damage trail)
        ghost = ghost < health ? health : approach(ghost, health, 3.5f * k, dt);

        if (shown.hurtTime > lastHurtTime) hurtFlash = 1f;
        lastHurtTime = shown.hurtTime;
        hurtFlash = approach(hurtFlash, 0f, 7f * k, dt);

        if (!position.is(LT.L8)) drawFloating(event.getContext(), dt);
    }

    /** 2D: constant-size card above the head. 3D: perspective size, turns with the target, fades behind walls. */
    private void drawFloating(DrawContext ctx, float dt) {
        Render3D frame = EchoEvents.lastFrame();
        LivingEntity t = shown;
        if (frame == null || t == null || mc.options.hudHidden) return;
        Vec3d base = t.getLerpedPos(frame.tickDelta);
        Vec3d anchor = base.add(0, t.getHeight() + floatHeight.getValue(), 0);
        Vector4f clip = frame.toClip(anchor.x, anchor.y, anchor.z);
        if (clip.w() <= 0.05f) {
            anchorX = Float.NaN;
            return;
        }
        float[] gui = Render3D.toGui(clip);
        if (Float.isNaN(anchorX) || Math.abs(gui[0] - anchorX) > 200 || Math.abs(gui[1] - anchorY) > 200) {
            anchorX = gui[0];
            anchorY = gui[1];
        } else {
            anchorX = approach(anchorX, gui[0], 40f, dt);
            anchorY = approach(anchorY, gui[1], 40f, dt);
        }

        float sx = floatScale.getValue(), sy = sx, fade = 1f;
        if (position.is(LT.L10)) {
            double dist = Math.max(0.5, frame.camera.distanceTo(anchor));
            float persp = (float) Math.max(0.15, Math.min(3.0, 4.0 / dist));
            sx *= persp;
            sy *= persp;
            float turnTarget = 1f;
            if (turnWithTarget.getValue()) {
                // facing vs. direction to the camera on the horizontal plane
                double yaw = Math.toRadians(t.getBodyYaw());
                double fxDir = -Math.sin(yaw), fzDir = Math.cos(yaw);
                double cx = frame.camera.x - base.x, cz = frame.camera.z - base.z;
                double len = Math.sqrt(cx * cx + cz * cz);
                if (len > 1e-3) turnTarget = (float) (0.3 + 0.7 * Math.abs((fxDir * cx + fzDir * cz) / len));
            }
            turn = approach(turn, turnTarget, 10f, dt);
            sx *= turn;
            if (!fadeBehindWalls.getValue()) {
                occluded = false;
            } else if (++raycastFrame % 3 == 0) {
                // smoothed below, so a check every 3rd frame is plenty
                BlockHitResult hit = mc.world.raycast(new RaycastContext(frame.camera, anchor,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                occluded = hit.getType() == HitResult.Type.BLOCK;
            }
            float sightTarget = occluded ? 0.3f : 1f;
            sight = approach(sight, sightTarget, 8f, dt);
            fade = sight;
        }

        float pop = 0.85f + 0.15f * easeOutBack(Math.min(1f, open));
        sx *= pop;
        sy *= pop;
        float alpha = Math.min(1f, open * 1.4f) * fade;
        float x = anchorX - W * sx / 2f, y = anchorY - H * sy;

        boolean glass = (HudManager.liquidGlass || glassSetting.getValue()) && GlassRenderer.available();
        if (glass) {
            GlassRenderer.queue(new GlassRenderer.Panel(x, y, W * sx, H * sy, 8 * Math.min(sx, sy), alpha));
            GlassRenderer.flush(ctx);
        }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sx, sy);
        if (!glass) Draw.rect(ctx, 0, 0, W, H, 8, Colors.withAlpha(Theme.SIDEBAR, HudManager.panelOpacity * alpha));
        draw(ctx, t, alpha);
        m.popMatrix();
    }

    private void draw(DrawContext ctx, LivingEntity t, float alpha) {
        var m = ctx.getMatrices();
        Draw.rect(ctx, 0, 0, 3, H, 2, Colors.withAlpha(Theme.ACCENT, alpha));

        // Face (shakes + red flash when hurt)
        int face = 32;
        float shake = hurtFlash * 1.6f;
        float fx = 9 + (float) Math.sin(System.nanoTime() / 2.5e7) * shake;
        int fy = 9;
        m.pushMatrix();
        m.translate(fx, fy);
        if (t instanceof net.minecraft.client.network.AbstractClientPlayerEntity p) {
            // skin straight from the entity (works for players missing from the tab list)
            PlayerSkinDrawer.draw(ctx, p.getSkin(), 0, 0, face);
        } else {
            Draw.rect(ctx, 0, 0, face, face, 5, Colors.withAlpha(Theme.CONTROL, alpha));
            drawEntityHead(ctx, t, face);
        }
        if (hurtFlash > 0.02f) Draw.rect(ctx, 0, 0, face, face, 4, Colors.withAlpha(0xFFFF3030, hurtFlash * 0.45f * alpha));
        m.popMatrix();

        // Name, HP, distance
        int tx = 9 + face + 8;
        int textW = W - tx - 8 - 18; // room for the armour bars on the right
        // win estimate: effective HP (health + absorption, scaled by armour) of you vs them
        String win = "";
        int winColor = Theme.MUTED;
        if (mc.player != null && t != mc.player) {
            float mine = effectiveHp(mc.player), theirs = effectiveHp(t);
            int pct = Math.round(100f * mine / Math.max(0.01f, mine + theirs));
            win = pct + LT.L17;
            winColor = pct >= 55 ? 0xFF4ADE80 : pct <= 45 ? 0xFFF87171 : 0xFFFBBF24;
            Text.drawCentered(ctx, win, W - 8 - Text.width(win, 7.5f), 13, 7.5f, Colors.withAlpha(winColor, alpha));
        }
        Text.drawCentered(ctx, Text.fit(t.getName().getString(), textW - Text.width(win, 7.5f), 8.5f), tx, 13, 8.5f,
                Colors.withAlpha(Theme.TEXT, alpha));
        float max = Math.max(1f, t.getMaxHealth() + t.getAbsorptionAmount());
        int popped = pops.getOrDefault(t.getId(), 0);
        String info = String.format(Locale.ROOT, LT.L18, health * max,
                Math.sqrt(mc.player.squaredDistanceTo(t))) + (popped > 0 ? "  " + popped + (popped == 1 ? LT.L19 : LT.L20) : "");
        com.utilitymod.echo.render.Icons.draw(ctx, LT.L21, tx, 21.5f, 7, Colors.withAlpha(healthColor(health), alpha));
        Text.drawCentered(ctx, info, tx + 9, 25, 6.8f, Colors.withAlpha(Theme.MUTED, alpha));

        // Health: track, ghost trail, live bar
        // armour durability: four slim bars on the right (head -> feet)
        net.minecraft.entity.EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = t.getEquippedStack(armor[i]);
            float ax = W - 8 - (armor.length - i) * 4.5f, ay = 22, ah = 22;
            Draw.rect(ctx, Math.round(ax), Math.round(ay), 3, Math.round(ah), 1, Colors.withAlpha(Theme.TRACK, alpha));
            if (piece.isEmpty() || !piece.isDamageable()) continue;
            float left = 1f - piece.getDamage() / (float) Math.max(1, piece.getMaxDamage());
            int col = left > 0.5f ? 0xFF4ADE80 : left > 0.25f ? 0xFFFBBF24 : 0xFFF87171;
            int fill = Math.max(1, Math.round(ah * left));
            Draw.rect(ctx, Math.round(ax), Math.round(ay + ah - fill), 3, fill, 1, Colors.withAlpha(col, alpha));
        }

        int barY = 33, barH = 5;
        Draw.rect(ctx, tx, barY, textW, barH, 2, Colors.withAlpha(Theme.TRACK, alpha));
        if (ghost > health + 0.002f) {
            Draw.rect(ctx, tx, barY, Math.max(barH, Math.round(textW * ghost)), barH, 2,
                    Colors.withAlpha(0xFFFFFFFF, alpha * 0.45f));
        }
        Draw.rect(ctx, tx, barY, Math.max(barH, Math.round(textW * health)), barH, 2,
                Colors.withAlpha(healthColor(health), alpha));

        // Gear (fades in after the panel)
        if (showGear.getValue() && alpha > 0.6f) {
            int gx = tx, gy = H - 13;
            EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
            for (EquipmentSlot slot : slots) {
                ItemStack stack = t.getEquippedStack(slot);
                if (stack.isEmpty()) continue;
                m.pushMatrix();
                m.translate(gx, gy);
                m.scale(0.62f, 0.62f);
                ctx.drawItem(stack, 0, 0);
                m.popMatrix();
                gx += 12;
            }
        }
    }

    /**
     * Mob model in the face slot. Entity GUI elements are placed in absolute GUI px (the pose
     * stack is ignored), so the slot's corners are run through the current matrix first.
     */
    private void drawEntityHead(DrawContext ctx, LivingEntity t, int face) {
        var pose = new org.joml.Matrix3x2f(ctx.getMatrices());
        org.joml.Vector2f a = pose.transformPosition(new org.joml.Vector2f(0, 0));
        org.joml.Vector2f b = pose.transformPosition(new org.joml.Vector2f(face, face));
        int x1 = Math.round(a.x), y1 = Math.round(a.y), x2 = Math.round(b.x), y2 = Math.round(b.y);
        float px = (b.x - a.x);
        // zoom on the head: upper ~40% of the model fills the slot
        int size = Math.max(4, Math.round(px * 1.6f / Math.max(0.5f, t.getHeight())));
        float cx = (x1 + x2) / 2f, cy = (y1 + y2) / 2f;
        ctx.enableScissor(x1, y1, x2, y2);
        net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(ctx, x1, y1 + Math.round(px * 0.55f), x2,
                y2 + Math.round(px * 0.55f), size, 0.0625f, cx, cy - px * 0.2f, t);
        ctx.disableScissor();
    }

    /** Health + absorption, scaled up by armour (armour points cut damage ~4% each, capped at 80%). */
    private static float effectiveHp(LivingEntity e) {
        float hp = e.getHealth() + e.getAbsorptionAmount();
        float reduction = Math.min(0.8f, e.getArmor() * 0.04f);
        return hp / (1f - reduction);
    }

    /** Totem pops per entity id (entity status 35), cleared on world change. */
    private final java.util.Map<Integer, Integer> pops = new java.util.concurrent.ConcurrentHashMap<>();
    private net.minecraft.client.world.ClientWorld popWorld;

    @EventSubscribe
    private void onPacket(com.utilitymod.echo.event.impl.EventPacketReceive event) {
        if (!(event.getPacket() instanceof net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket p) || p.getStatus() != 35) return;
        mc.execute(() -> {
            if (mc.world == null) return;
            if (popWorld != mc.world) {
                pops.clear();
                popWorld = mc.world;
            }
            net.minecraft.entity.Entity e = p.getEntity(mc.world);
            if (e != null) pops.merge(e.getId(), 1, Integer::sum);
        });
    }

    private static int healthColor(float pct) {
        int r = (int) (255 * Math.min(1f, 2f * (1f - pct)));
        int g = (int) (255 * Math.min(1f, 2f * pct));
        return 0xFF000000 | (r << 16) | (g << 8) | 0x40;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xce2d,0x80b6,0x1f42,0x4425,0x8cee}, new char[]{0x1f10,0x8c8f,0x80d8,0xce4a,0x4440}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x474b,0x1976,0x9ebb,0xbf3f,0x57fe,0xe20d,0x03b7,0x41c9,0x3b25,0x0b93,0x64eb,0x7f71,0x8c96,0x52a9,0xde4d,0x3b81}, new char[]{0x4700,0xe268,0x0bf6,0x3b55,0x57de,0xbf6b,0x7f10,0x3bf3,0x41ae,0x9ede,0x52dd,0x1956,0xde65,0x03da,0x6498,0x8cbf}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xef28,0xf439,0x44f3,0xdd50,0x86d3,0xdf12,0xd72e,0xe0b7,0x14b7,0x6f71,0x6d60,0x4744,0x8a1a,0xbc8f,0x7700}, new char[]{0x4705,0xdf7c,0xbce6,0x6d0d,0x4492,0xef5c,0x86ba,0x14d8,0xdd3e,0xd70e,0x7753,0xf449,0x6f14,0xe0d2,0x8a7e}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x46e7,0x357b,0xbe2b,0x3339,0xf86e,0xf2c5,0x9bc5,0xf38a,0x9764}, new char[]{0x336a,0x970c,0xf3e5,0x4690,0xf84e,0xbe6c,0xf2a0,0x9ba4,0x3509}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x32e1,0x5ca4,0xcf6c,0x4dad,0xd34c,0x7d70,0x0dfa,0x079c,0x95ac,0x67a1,0xa2e9,0xef16}, new char[]{0xcf3c,0x7d1c,0x4dcc,0xa290,0x3284,0x5cd6,0xd33f,0x07bc,0x67ee,0x95c2,0xef7a,0x0d83}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf035,0xfc9e,0x0d39,0x8910,0xdfd0,0x09e1,0x3322,0x3c37,0x00a0,0x68c4,0x06f3,0x29d8}, new char[]{0x00ec,0xf05c,0x3c46,0x0d4c,0xfcf7,0x8974,0x09c1,0x299f,0xdfbc,0x68a5,0x3351,0x0680}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x68b7,0xfb90,0xa356,0xc448,0xce05,0x76c8,0x2296,0x3401}, new char[]{0x68e7,0xa339,0x76bb,0x3468,0xc43c,0xfbf9,0x22f9,0xce6b}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6455,0x0a29,0x453d,0x081b,0x9313,0x6e0a}, new char[]{0x9340,0x0a4a,0x0869,0x6430,0x6e6f,0x4553}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe2c1,0xe7da}, new char[]{0xe2f3,0xe79e,0xa876,0x6a48}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf0b2,0xc0d5}, new char[]{0xf081,0xc091,0x9452,0x0a8c}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ed8,0x1282,0x8914,0x9400,0x5803,0x8a0e,0xfc18,0xbc3b,0xb3e3,0x725d,0xf5ea}, new char[]{0x7e9e,0x8a62,0xb38c,0xbc5a,0x9474,0x8934,0xfc4b,0x12e1,0x723c,0x586f,0xf58f}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba25,0x83b9,0x8feb,0x4a43,0x2118,0x31f6,0x3cde,0x6845,0x0f01,0xc0ba,0x3abb,0x0797}, new char[]{0x8fad,0x2174,0xba4a,0x83d8,0x3caa,0xc09a,0x31be,0x07f2,0x4a2a,0x0f66,0x682d,0x3acf}).toString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x17f2,0x6964,0x963a,0xad82,0xfdd1,0xd819,0xe60d,0xcfe2,0x56a9,0x6eda,0x6b34,0xb7bd,0x74e1,0xb0a8,0xc043,0xc296}, new char[]{0xcfb6,0xb7c8,0x7493,0xd877,0xfdf1,0xc014,0x6b5d,0xe679,0xc2fe,0xada2,0x56fd,0xb0c9,0x6ea8,0x965d,0x6901,0x1786}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xea58,0x818f,0x2941,0xfe75,0x20a1,0x049b,0x5cb6,0xffaf,0xeaff,0xe8c0,0x8d35,0x68c2,0xa157,0xaf6c,0x25f5,0x5d51,0xe493}, new char[]{0x5d17,0xea39,0x81eb,0x68a7,0x04bb,0x25b7,0x5cd3,0xaf04,0xa13e,0x292f,0xffcb,0xfe55,0xe897,0x20c0,0xe4ff,0x8d59,0xea8c}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x764a,0xf47b,0x44cc,0x8c4b,0x48dc,0x16d4,0xd45d,0xabe0,0x386e,0x8201,0x580f,0x5af7,0xc312,0xd0cf,0x6057,0x86a3,0x5c37,0x0105,0xa337,0x4f84,0x5a0e,0x6304,0xb29c,0x4422,0xfdc8,0xe5a3,0xf92e,0x1f70,0xb916,0x68d8,0xa95f,0x953d,0x6f15,0x6a9d,0x3e8e,0xae33,0x4747,0x2216,0x3b90,0xf85f,0xbfe8,0xb1aa,0xea36,0x9b47,0x5ad1,0xf27a,0x9a53,0xd8d9,0xad8c,0xa769,0xce1e,0x0179,0x9885,0xb0d2,0xa4ac,0xb3bb,0xdec7,0xe15b,0x1136,0x8ec2,0x03c6,0xc582,0xcea4,0xed05,0xd96d,0x63f6,0xc96e,0x0951,0x8ad1,0x3fe8,0x4393,0xc663}, new char[]{0xea65,0x8eaa,0x5c58,0x48ab,0xd8aa,0xa97f,0x8ab8,0x9a3d,0x762c,0xc37d,0x1f50,0x3f89,0xdea5,0xed6a,0x16a1,0x1142,0x582f,0xe5d7,0xb2f4,0xc90b,0x5a2e,0xb1cf,0xc5ec,0x6023,0x444b,0xf82b,0x6ae4,0x3bb0,0xa34e,0x5a98,0xbf9d,0xae13,0x98e4,0x954f,0x2273,0x6f35,0x8c2d,0x639f,0x4720,0xd0a7,0x4ff0,0xb97f,0xf940,0x0162,0xfde8,0xd945,0x03ab,0xb3d4,0xf40d,0x011c,0x0971,0x68b1,0xadf8,0xb0f2,0x44a5,0x86cd,0x43b3,0xce6a,0xf212,0xd438,0x3eae,0x634c,0x9b12,0xaba4,0xa749,0xa4c9,0x380a,0xe132,0xc617,0xcecb,0x5aa3,0x8228}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2664,0x134c,0x7399,0xa0be,0xf83c,0x28bc,0x2ae0,0x0961,0xa1d6}, new char[]{0xa0ca,0x132d,0x73eb,0xa1b1,0x2601,0x28c8,0x0909,0xf849,0x2a84}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa447}, new char[]{0xa462,0x6aae,0x694f,0xccc3}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd9f9,0xecca,0xa3e7,0x1b3c,0xf38a,0x526e,0x9a4f,0xe6bc,0x8d95,0xf048,0xb7f2,0x3899,0x7c7e,0x2a4a}, new char[]{0xf3af,0x9a61,0xe68d,0xecac,0xd9d9,0x1b74,0xb7a2,0x38b9,0x8db5,0xa3c2,0xf066,0x7c4f,0x2a2c,0x5203}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x281a,0xb6f3,0x880a,0x534d}, new char[]{0x882a,0x533d,0xb69c,0x286a}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x831c,0xb8ad,0x8b96,0x5f6c,0x9958}, new char[]{0x8bb6,0xb8dd,0x5f03,0x836c,0x992b}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1724,0x980d,0x7b6e,0x9897,0xe1da}, new char[]{0x174c,0x9868,0x98f6,0xe1a8,0x7b1a}).plainString();
    }
}
