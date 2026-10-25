package com.utilitymod.echo.hud;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.gui.CardGuiStyle;
import com.utilitymod.gui.ModuleBinds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * water.lol HUD set: gradient watermark, fps / bps pills, client tag, keybinds list, target HUD, gradient
 * module list, effects with ring timers, inventory grid and radar - all coloured from the theme gradient
 * (accent -> background, set on the Themes page). While these are registered the old overlapping elements
 * (watermark, module list, fps/ping, target hud, effects hud) are hidden by {@link HudManager}.
 */
public final class WaterHud {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final int PANEL = 0xC8161A22, WHITE = 0xFFF2F4F8, MUTED = 0xFF9AA1B1;
    private static boolean registered;

    private WaterHud() {}

    public static void init() {
        if (registered) return;
        registered = true;
        // retire the old elements these replace (watermark, module list, fps/ping, target hud, effects hud)
        HudManager.elements().removeIf(e -> {
            String n = e.getClass().getName();
            return n.endsWith(LT.L1) || n.endsWith(LT.L2) || n.endsWith(LT.L3)
                    || n.endsWith(LT.L4) || n.endsWith(LT.L5);
        });
        HudManager.register(new Watermark());
        HudManager.register(new Pill(LT.L6, LT.L7, .02f, .17f, LT.L8, () -> Integer.toString(mc.getCurrentFps())));
        HudManager.register(new Pill(LT.L9, LT.L10, .02f, .24f, LT.L11, WaterHud::bps));
        HudManager.register(new Promo());
        HudManager.register(new Keybinds());
        HudManager.register(new Target());
        HudManager.register(new ModuleList());
        HudManager.register(new Effects());
        HudManager.register(new Inventory());
        HudManager.register(new Radar());
    }

    static int a() { return CardGuiStyle.accent; }
    static int b() { return CardGuiStyle.background; }
    /** Control Panel > Interface > Text Color Shift: the gradient slowly flows across the HUD. */
    public static volatile boolean colorShift = true;

    static int grad(float t) {
        t = MathHelper.clamp(t, 0, 1);
        if (colorShift) {
            float k = (t * .5f + (System.currentTimeMillis() % 4000L) / 4000f) % 1f;
            t = k < .5f ? k * 2 : 2 - k * 2;   // ping-pong so it never jumps
        }
        return Colors.lerp(a(), b(), t);
    }

    static void panel(DrawContext g, float w, float h, float r) {
        Draw.rect(g, 0, 0, w, Math.round(h), Math.round(r), PANEL);
    }

    /** Horizontal gradient text across [x, x+width). */
    static void gradText(DrawContext g, String s, float x, float cy, float size) {
        float w = Math.max(1, Text.width(s, size));
        Text.drawCentered(g, s, x, cy, size, (Text.ColorAt) px -> grad((px - x) / w));
    }

    static void gradBar(DrawContext g, float x, float y, float w, float h) {
        int strips = Math.max(4, Math.round(w / 2));
        for (int i = 0; i < strips; i++) {
            int x0 = Math.round(x + w * i / strips), x1 = Math.round(x + w * (i + 1) / strips);
            g.fill(x0, Math.round(y), x1, Math.round(y + h), grad(i / (float) (strips - 1)));
        }
    }

    // ---- bps (horizontal blocks per second) ---------------------------------------------------------

    private static Vec3d lastPos;
    private static long lastAt;
    private static double speed;

    static String bps() {
        if (mc.player == null) return LT.L12;
        long now = System.nanoTime();
        Vec3d p = mc.player.getEntityPos();
        if (lastPos != null && now - lastAt > 50_000_000L) {
            double dt = (now - lastAt) / 1e9;
            double d = Math.hypot(p.x - lastPos.x, p.z - lastPos.z);
            speed = speed * .6 + (d / dt) * .4;
            lastPos = p;
            lastAt = now;
        } else if (lastPos == null) { lastPos = p; lastAt = now; }
        return String.format(LT.L13, speed);
    }

    // ---- elements -------------------------------------------------------------------------------------

    static final class Watermark extends HudElement {
        Watermark() { super(LT.L14, LT.L15, .01f, .01f); }
        @Override public boolean wantsBackground() { return false; }
        @Override public float width() { return Text.width(LT.L16, 28f) + 4; }
        @Override public float height() { return 30; }
        @Override public void render(DrawContext g, boolean preview) {
            gradText(g, LT.L16, 1, 15, 28f);
            gradText(g, LT.L16, 1.6f, 15, 28f);   // faux bold
        }
    }

    static final class Pill extends HudElement {
        private final String label;
        private final java.util.function.Supplier<String> value;
        Pill(String id, String name, float x, float y, String label, java.util.function.Supplier<String> value) {
            super(id, name, x, y);
            this.label = label;
            this.value = value;
        }
        @Override public boolean wantsBackground() { return false; }
        @Override public float radius() { return 7; }
        @Override public float width() { return Text.width(label, 8f) + Text.width(value.get(), 8f) + 18; }
        @Override public float height() { return 15; }
        @Override public void render(DrawContext g, boolean preview) {
            panel(g, width(), height(), 7);
            gradText(g, label, 7, 7.5f, 8f);
            Text.drawCentered(g, value.get(), 11 + Text.width(label, 8f), 7.5f, 8f, WHITE);
        }
    }

    static final class Promo extends HudElement {
        Promo() { super(LT.L17, LT.L18, .03f, .34f); }
        @Override public boolean wantsBackground() { return false; }
        @Override public float width() { return Text.width(LT.L19, 8f) + 14; }
        @Override public float height() { return 15; }
        @Override public void render(DrawContext g, boolean preview) {
            panel(g, width(), height(), 6);
            gradText(g, LT.L19, 7, 7.5f, 8f);
        }
    }

    static final class Keybinds extends HudElement {
        Keybinds() { super(LT.L20, LT.L21, .22f, .15f); }
        @Override public boolean wantsBackground() { return false; }

        private List<String[]> rows(boolean preview) {
            List<String[]> out = new ArrayList<>();
            var all = ModuleBinds.save();
            for (var e : all.entrySet()) {
                Module m = ModuleManager.get(e.getKey());
                if (m == null) continue;
                String key = ModuleBinds.name(e.getValue().getAsInt());
                out.add(new String[]{m.getName(), key.isEmpty() ? LT.L22 : key, m.isEnabled() ? LT.L23 : LT.L24});
            }
            if (out.isEmpty() && preview) {
                out.add(new String[]{LT.L25, LT.L26, LT.L23});
                out.add(new String[]{LT.L27, LT.L26, LT.L24});
            }
            return out;
        }
        @Override public boolean active() { return !rows(false).isEmpty(); }
        @Override public float width() {
            float w = 90;
            for (String[] r : rows(true)) w = Math.max(w, Text.width(r[0], 8f) + Text.width(r[1], 8f) + 30);
            return w;
        }
        @Override public float height() { return 6 + rows(true).size() * 15; }
        @Override public void render(DrawContext g, boolean preview) {
            List<String[]> rs = rows(preview);
            float w = width();
            panel(g, w, height(), 6);
            float y = 3;
            for (String[] r : rs) {
                boolean on = r[2].equals(LT.L23);
                Draw.rect(g, 3, y, w - 6, 13, 4, on ? 0x26FFFFFF : 0x12FFFFFF);
                Text.drawCentered(g, r[0], 8, y + 6.5f, 8f, on ? WHITE : MUTED);
                Text.drawCentered(g, r[1], w - 8 - Text.width(r[1], 8f), y + 6.5f, 8f, on ? a() : Colors.withAlpha(a(), .5f));
                y += 15;
            }
        }
    }

    static final class Target extends HudElement {
        Target() { super(LT.L28, LT.L29, .38f, .08f); }
        @Override public boolean wantsBackground() { return false; }
        private LivingEntity target(boolean preview) {
            LivingEntity t = com.utilitymod.echo.utils.combat.TargetUtils.getLastAttackedTarget(100);
            if (t != null && t.isAlive()) return t;
            if (mc.targetedEntity instanceof PlayerEntity p) return p;
            return preview ? mc.player : null;
        }
        @Override public boolean active() { return target(false) != null; }
        @Override public float width() { return 150; }
        @Override public float height() { return 46; }
        @Override public void render(DrawContext g, boolean preview) {
            LivingEntity t = target(preview);
            if (t == null) return;
            // rounder card: antialiased 14px corners
            com.utilitymod.gui.Rounded.fill(g, 0, 0, 150, 46, 14f, PANEL);
            if (t instanceof AbstractClientPlayerEntity p) {
                net.minecraft.client.gui.PlayerSkinDrawer.draw(g, p.getSkin(), 5, 5, 36);
                // round the head's corners: a panel-coloured ring over its square edges
                com.utilitymod.gui.Rounded.outline(g, 3, 3, 40, 40, 11f, 3f, PANEL);
            } else com.utilitymod.gui.Rounded.fill(g, 5, 5, 36, 36, 9f, 0x33FFFFFF);
            Text.drawCentered(g, Text.fit(t.getName().getString(), 96, 8.5f), 48, 11, 8.5f, WHITE);
            float hp = t.getHealth() + t.getAbsorptionAmount(), max = Math.max(1, t.getMaxHealth());
            float frac = MathHelper.clamp(hp / max, 0, 1);
            // pill HP bar with round ends
            com.utilitymod.gui.Rounded.fill(g, 48, 19, 96, 7, 3.5f, 0x33FFFFFF);
            int fill = Math.max(7, Math.round(96 * frac));
            if (frac > 0) {
                gradBar(g, 51.5f, 19, fill - 7, 7);
                com.utilitymod.gui.Rounded.fill(g, 48, 19, 7, 7, 3.5f, grad(0));
                com.utilitymod.gui.Rounded.fill(g, 48 + fill - 7, 19, 7, 7, 3.5f, grad(fill / 96f));
            }
            Text.drawCentered(g, String.format(LT.L30, hp), 48, 34, 7.5f, MUTED);
            String dist = Math.round(mc.player.distanceTo(t)) + LT.L31;
            Text.drawCentered(g, dist, 144 - Text.width(dist, 7.5f), 34, 7.5f, MUTED);
        }
    }

    static final class ModuleList extends HudElement {
        ModuleList() { super(LT.L32, LT.L33, .99f, .08f); }
        @Override public boolean wantsBackground() { return false; }
        private List<String> names() {
            List<String> out = new ArrayList<>();
            for (Module m : ModuleManager.all()) if (m.isEnabled() && m.getCategory() != null) out.add(m.getName());
            out.sort(Comparator.comparingDouble((String n) -> -Text.width(n, 8.5f)));
            return out;
        }
        @Override public float width() {
            float w = 40;
            for (String n : names()) w = Math.max(w, Text.width(n, 8.5f) + 8);
            return w;
        }
        @Override public float height() { return Math.max(12, names().size() * 11); }
        @Override public void render(DrawContext g, boolean preview) {
            List<String> ns = names();
            float w = width();
            for (int i = 0; i < ns.size(); i++) {
                String n = ns.get(i);
                float tw = Text.width(n, 8.5f);
                float t = ns.size() <= 1 ? 0 : i / (float) (ns.size() - 1);
                Text.drawCentered(g, n, w - 5 - tw, i * 11 + 5.5f, 8.5f, grad(t));
                Draw.rect(g, w - 2, i * 11, 2, 11, 0, grad(t));
            }
        }
    }

    static final class Effects extends HudElement {
        Effects() { super(LT.L34, LT.L35, .01f, .47f); }
        @Override public boolean wantsBackground() { return false; }
        private List<StatusEffectInstance> list() {
            return mc.player == null ? List.of() : new ArrayList<>(mc.player.getStatusEffects());
        }
        @Override public boolean active() { return !list().isEmpty(); }
        @Override public float width() { return 120; }
        @Override public float height() { return Math.max(1, list().size()) * 22; }
        @Override public void render(DrawContext g, boolean preview) {
            List<StatusEffectInstance> l = list();
            if (l.isEmpty() && preview) { panel(g, 120, 20, 6); Text.drawCentered(g, LT.L36, 26, 10, 8f, WHITE); ring(g, 11, 10, 7, .6f); return; }
            float y = 0;
            for (StatusEffectInstance e : l) {
                g.getMatrices().pushMatrix();
                g.getMatrices().translate(0, y);
                panel(g, 120, 20, 6);
                float frac = e.isInfinite() ? 1 : MathHelper.clamp(e.getDuration() / 1200f, 0, 1);
                ring(g, 11, 10, 7, frac);
                String name = e.getEffectType().value().getName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
                Text.drawCentered(g, Text.fit(name, 70, 8f), 24, 10, 8f, WHITE);
                String time = e.isInfinite() ? "∞" : (e.getDuration() / 20 / 60) + LT.L37 + String.format(LT.L38, e.getDuration() / 20 % 60);
                Text.drawCentered(g, time, 114 - Text.width(time, 7f), 10, 7f, a());
                g.getMatrices().popMatrix();
                y += 22;
            }
        }
        private static String roman(int n) { return switch (n) { case 2 -> LT.L39; case 3 -> LT.L40; case 4 -> LT.L41; case 5 -> LT.L42; default -> Integer.toString(n); }; }
    }

    /** Ring timer: dots around a circle, filled clockwise by {@code frac}, gradient coloured. */
    static void ring(DrawContext g, float cx, float cy, float r, float frac) {
        int n = 36;
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + i * Math.PI * 2 / n;
            boolean on = i / (float) n <= frac;
            float x = (float) (cx + Math.cos(ang) * r), y = (float) (cy + Math.sin(ang) * r);
            Draw.rect(g, x - 1, y - 1, 2, 2, 1, on ? grad(i / (float) n) : 0x33FFFFFF);
        }
    }

    static final class Inventory extends HudElement {
        Inventory() { super(LT.L43, LT.L44, .01f, .6f); }
        @Override public boolean wantsBackground() { return false; }
        @Override public float width() { return 9 * 18 + 6; }
        @Override public float height() { return 3 * 18 + 6; }
        @Override public void render(DrawContext g, boolean preview) {
            panel(g, width(), height(), 6);
            for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) {
                float x = 3 + c * 18, y = 3 + r * 18;
                Draw.rect(g, x + 1, y + 1, 16, 16, 3, 0x22FFFFFF);
                if (mc.player == null) continue;
                ItemStack st = mc.player.getInventory().getStack(9 + r * 9 + c);
                if (!st.isEmpty()) {
                    com.utilitymod.echo.render.Icons.drawItem(g, st, x + 1, y + 1, 16);
                    if (st.getCount() > 1) Text.drawCentered(g, Integer.toString(st.getCount()), x + 17 - Text.width(Integer.toString(st.getCount()), 6f), y + 14, 6f, WHITE);
                }
            }
        }
    }

    static final class Radar extends HudElement {
        private static final float SIZE = 76, RANGE = 32;
        Radar() { super(LT.L45, LT.L46, .75f, .6f); }
        @Override public boolean wantsBackground() { return false; }
        @Override public float width() { return SIZE; }
        @Override public float height() { return SIZE; }
        @Override public void render(DrawContext g, boolean preview) {
            panel(g, SIZE, SIZE, 7);
            g.fill(Math.round(SIZE / 2), 4, Math.round(SIZE / 2) + 1, Math.round(SIZE - 4), 0x22FFFFFF);
            g.fill(4, Math.round(SIZE / 2), Math.round(SIZE - 4), Math.round(SIZE / 2) + 1, 0x22FFFFFF);
            if (mc.player == null || mc.world == null) return;
            float yaw = mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            float cos = MathHelper.cos(yaw), sin = MathHelper.sin(yaw);
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                double dx = p.getX() - mc.player.getX(), dz = p.getZ() - mc.player.getZ();
                // rotate so "up" on the radar is where you look
                double rx = dx * cos + dz * sin, rz = dz * cos - dx * sin;
                float px = (float) (SIZE / 2 - rx / RANGE * (SIZE / 2 - 5));
                float pz = (float) (SIZE / 2 - rz / RANGE * (SIZE / 2 - 5));
                if (px < 3 || pz < 3 || px > SIZE - 3 || pz > SIZE - 3) continue;
                Draw.rect(g, px - 2, pz - 2, 4, 4, 2, a());
            }
            Draw.rect(g, SIZE / 2 - 2, SIZE / 2 - 2, 4, 4, 2, WHITE);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2987,0x38ca,0x8268,0x687b,0xf439,0x639a,0xb7f4,0xd715,0x3ce0,0x28a2,0x7a51,0xea0e,0xda2d,0xa08c,0x921e,0x981c,0x8c13,0xb059,0xf322,0xce36}, new char[]{0x9854,0x29f2,0xf346,0x3885,0xa0fa,0xea6b,0xd767,0x9272,0xb038,0x8211,0xda09,0x3cb7,0x63fb,0x28d6,0xf45c,0x7a23,0xce5b,0x8c72,0xb786,0x6810}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc56f,0x0b4c,0xc6f1,0x2409,0x648d,0xc476,0xac86,0xd007,0xa6e3,0xc75e,0xd3c3,0xd0ab,0x3809,0x1d4a,0x6808,0x1973,0xd5fc,0xb9a9,0x5a56,0x621d,0xe937}, new char[]{0xc716,0x0b39,0xa687,0xc520,0x1d3c,0xace3,0xb9db,0x6271,0xd3a2,0xd07e,0xd08f,0xe97a,0xc69e,0x386d,0xc403,0x64e1,0xd599,0x6844,0x2460,0x1900,0x5a22}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc77e,0x777b,0x82da,0x6699,0xb3fb,0x9202,0x804c,0x5897,0x1a20,0x3397,0x4aa0,0x0dd8,0x1862,0x760a,0x7611,0x9a2a}, new char[]{0x924a,0x33e2,0x8028,0xb3b4,0x767c,0x1a45,0x7709,0x9a46,0x82bb,0x0da1,0x58b3,0x7642,0x66ed,0x4ac1,0xc70a,0x1811}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x603b,0xe336,0x0ef4,0x8d7a,0xb4e2,0x0e65,0x72a3,0xbb9c,0xfd49,0x4f62,0x5bf2,0x1902,0x03d6,0x6376,0xb58f,0x6a65,0xe014}, new char[]{0x0e31,0x6317,0xe066,0xfd2e,0x72c6,0xb496,0x6073,0xe343,0x0e90,0xbbb8,0x5bb7,0x4f0e,0xb5ea,0x6a08,0x03b3,0x8d14,0x1976}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd873,0x6885,0x6b3a,0x01fe,0x99bb,0x6727,0xe100,0x91a0,0x1b85,0x35cf,0x5c6a,0x487b,0x6853,0x933e,0xf381,0xb9cc,0x4c88,0x853c}, new char[]{0x6816,0x0198,0x855a,0x5c0f,0x6744,0x934a,0xd800,0x4833,0xf3f4,0x35ab,0x68a1,0x1bc0,0xe16c,0xb9a9,0x4ce5,0x99de,0x6b54,0x91d4}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf56b,0x9135,0x88ff,0x4ea4,0x9ea0,0x5fbc,0x4ae4,0x86bc,0x88b9}, new char[]{0xf51c,0x9ec1,0x4ed0,0x9150,0x888d,0x88e6,0x5fda,0x4a94,0x86cf}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b51,0xc321,0xa6e0}, new char[]{0x9b17,0xa6b0,0xc372,0xcd99}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4ad2,0x79b8,0x794f}, new char[]{0x7929,0x79c8,0x4aa1,0x56a7}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04fc,0xcae7,0x411e,0xc66e,0xac9c,0x2440,0xf974,0x762f,0xb966}, new char[]{0x7658,0x2421,0x0488,0x417b,0xacee,0xf92b,0xca85,0xb916,0xc61d}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x487e,0x16b4,0x2665}, new char[]{0x16f6,0x482e,0x2636,0x684c}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0acd,0x04a0,0xa6f1}, new char[]{0xa693,0x0abd,0x04d3,0x0071}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4993,0x5ff1,0xd91f,0x126c}, new char[]{0x125c,0x5fdf,0xd92f,0x49a3}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e83,0x03e7,0xe3a9,0x1748}, new char[]{0xe38c,0x8ead,0x03d5,0x172e}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x02de,0x0085,0x04b2,0x3222,0xb414,0xa20e,0x8fe8,0x3b6a,0x1ec8,0x835f,0x8af8,0x60eb,0x4c92,0xebc1,0xe967}, new char[]{0x1ebf,0x8f89,0x02aa,0x00e0,0xe915,0xa251,0x04c5,0x8a99,0xb460,0x3247,0x832d,0x4cff,0x608a,0x3b18,0xebaa}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf1cf,0x5975,0x4ca1,0x6085,0xae34,0x31cb,0x39f8,0x940f,0x35b5}, new char[]{0x9458,0x35d4,0x4cd5,0xf1aa,0x31b9,0x60e8,0x5914,0x398a,0xae5f}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf8d2,0xa2af,0x7169,0xec9f,0x2b80,0xa8ea,0x837b,0x1114,0x2eee}, new char[]{0xa89d,0x2e8f,0x1160,0x2be5,0x8309,0xecb1,0xf8be,0x7106,0xa2c3}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf2c3,0x9357,0xe65e,0xed61,0xdb53,0xd512,0x63b1,0x5810,0xd34b,0x4a8a,0xc738}, new char[]{0xdb24,0x4aeb,0xed15,0xf2a6,0x5862,0x9308,0xc748,0xd339,0xd57d,0x63dc,0xe631}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe687,0x7fe4,0x27b6,0xd794,0xa699}, new char[]{0xa6c9,0x7f96,0x27d9,0xd7f9,0xe6e8}).toString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcca5,0xc3a0,0xc443,0x5be5,0x55cf,0x3ec1,0x7390,0xeb04,0x4bfb,0xf33b,0xd13d,0xbae0,0x852a,0xfab6,0x8443,0x9860}, new char[]{0x855d,0x4b9a,0xc437,0xfad3,0x55bd,0xeb2a,0x73fc,0xba8f,0x842f,0x9840,0x3e82,0xccc9,0xc3c9,0xf35e,0x5b8b,0xd149}).toString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x25f2,0xa3d2,0xb082,0xfd58,0x2077,0x186d,0x56a1,0x39d8,0x8bd9,0xa6f4,0x76d3,0x7f95,0x30ac,0x712f}, new char[]{0x7fe2,0x30cd,0x2003,0xfd3d,0x715d,0x8b86,0xb0e9,0x39bd,0x56d8,0xa3b0,0x76ba,0x259c,0x1809,0xa687}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc68,0xd676,0x7bf9,0xd58a,0xe5aa,0x8147,0xb84d,0xe65c,0xfb16,0xaf82,0xb5c8}, new char[]{0xafc9,0x7b9c,0x813e,0xb82f,0xb5a1,0xd618,0xe638,0xd5f9,0xfc20,0xfb43,0xe5ee}).toString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x51a1}, new char[]{0x519e,0x032b,0x6f12,0x82cf}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6973}, new char[]{0x6942,0xf21c,0xd71d,0x478c}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd1a1}, new char[]{0xd191,0x7eb5,0x8ca7,0xc4c8}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7a37,0x5a2e,0x82f2,0xc649,0x8d44,0xec18,0xeba5,0xb460,0xb782,0x6c0c,0x377c}, new char[]{0xebf6,0xc639,0xec7d,0x371d,0x6c7e,0xb7a2,0x82be,0x5a5b,0x8d2a,0xb407,0x7a52}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x48dd}, new char[]{0x4885,0x800b,0x054b,0x0af3}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc12d,0x7f21,0xfa5b,0xc3f8,0x43bb,0x36c5,0xf1a0,0xe72f,0xe0d7}, new char[]{0xc3a0,0x36a7,0x43d4,0xc15a,0xe70f,0xe094,0xf1c1,0xfa29,0x7f55}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6104,0x520f,0xc9ef,0xc590,0x3b64,0xf0c7,0xc6e6,0xc2fe,0x82b8,0x6dd3,0xed0c,0x5ae6}, new char[]{0x6da4,0xc5f1,0x82cc,0x3b01,0xc694,0xed53,0x5a92,0xf0a6,0x6176,0xc299,0x526a,0xc99b}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f77,0xd402,0x9c21,0x0bac,0x91b8,0x3ab9,0x272c,0x4279,0xca04}, new char[]{0x2778,0x0bcd,0xd470,0x6f10,0xca61,0x91cc,0x3af1,0x9c74,0x423d}).toString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3c26,0x2b83,0xbae1,0xb780,0x31ce,0x7142,0x2f5b}, new char[]{0x3c03,0x716c,0x31ff,0x2f3d,0x2ba3,0xb7c8,0xbab1}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb99b}, new char[]{0xb9f6,0x2ec8,0xe7b1,0x08d4}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa6b7,0xd046,0xc87c,0xa640,0x9d0e,0x4e96,0xb73e,0x4bdd,0xeedc,0x7c2f,0x3c8e,0x93ca,0x9940}, new char[]{0x3cf9,0xeebd,0x4ba9,0x93af,0x9932,0xd019,0xa6da,0xb751,0xc818,0x9d7b,0xa62c,0x4ef3,0x7c5c}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1a6b,0x76c8,0x0e2f,0x2c5f,0x5c1a,0x55b6,0x44a2,0xe833,0x3b6f,0x7467,0xa4c8}, new char[]{0xa485,0x7408,0x76ac,0xe846,0x1a07,0x44c7,0x2c7f,0x55fa,0x5c73,0x0e5c,0x3b1b}).toString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b95,0x9b95,0xa79c,0xe543,0xfd2d,0x72e7,0xa729,0xef1e,0xec22,0x5c5d,0xc616,0x9002,0xfd27}, new char[]{0x7290,0xe522,0x9be1,0x5c38,0x0be7,0xec7d,0xef7b,0xc670,0xfd4b,0x9067,0xa7ff,0xa75d,0xfd54}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c04,0xb22e,0x23fa,0x2c54,0x905b,0x4e59,0x48ca}, new char[]{0x4e1c,0xb248,0x2c32,0x48af,0x2399,0x902f,0x0c77}).toString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb212,0x833f,0x28d9,0x5866,0x9b79,0xcf64,0x66f3,0x888f}, new char[]{0xb241,0x834f,0x28bc,0xcf01,0x5802,0x66d3,0x9b30,0x88c6}).toString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5fdd}, new char[]{0x5fe7,0xbc40,0xf5e6,0x099b}).plainString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x824a,0xe5bb,0x23bc,0x3581}, new char[]{0x826f,0xe58b,0x35b3,0x23d8}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3a1,0x1958}, new char[]{0xd3e8,0x1911,0x63a5,0xfa21}).plainString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x170a,0x4976,0x3e55}, new char[]{0x1743,0x3e1c,0x493f,0x4eea}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6cf7,0xc563}, new char[]{0xc52a,0x6ca1,0xc76f,0xb484}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2620}, new char[]{0x2676,0x0987,0x23ea,0x30ba}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa4eb,0x36c4,0xd7c8,0x5ad0,0x6b4f,0xc083,0xe39c,0x0fd4,0x1679,0xda62,0x57d9,0x6cb3,0x2144,0x9fb7,0x0125}, new char[]{0x9fc0,0xa48a,0x6cc7,0x0140,0x36b6,0xe3c3,0x5ab9,0x1617,0xc0f5,0xd7ad,0x0fba,0x6b3b,0xda0d,0x57ab,0x213d}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6ff2,0xd058,0x8a6b,0x147e,0xaeab,0x63b6,0x5cb5,0x03c3,0x9d76}, new char[]{0x8a22,0xd036,0xaedd,0x141b,0x6f9c,0x63c2,0x9d19,0x03b1,0x5ccc}).toString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x757a,0x9148,0x74f6,0x4cd5,0xf801,0x43bf,0xc814,0x2544,0xb7aa,0xba7e,0xa139}, new char[]{0xa14e,0x751b,0xba0a,0x4cb0,0xb7d8,0x251b,0x7484,0xf860,0x43db,0x9129,0xc866}).plainString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa5b7,0x516a,0xffb9,0xc902,0x75aa}, new char[]{0xc950,0xffd8,0x510e,0xa5d6,0x75d8}).toString();
    }
}
