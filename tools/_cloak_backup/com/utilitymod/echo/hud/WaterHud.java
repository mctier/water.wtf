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
            return n.endsWith("HudOverlay$Watermark") || n.endsWith("HudOverlay$ModuleList") || n.endsWith("HudOverlay$Stats")
                    || n.endsWith("TargetHud$Element") || n.endsWith("EffectsHud$Element");
        });
        HudManager.register(new Watermark());
        HudManager.register(new Pill("water_fps", "FPS", .02f, .17f, "fps", () -> Integer.toString(mc.getCurrentFps())));
        HudManager.register(new Pill("water_bps", "BPS", .02f, .24f, "bps", WaterHud::bps));
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
        if (mc.player == null) return "0.00";
        long now = System.nanoTime();
        Vec3d p = mc.player.getEntityPos();
        if (lastPos != null && now - lastAt > 50_000_000L) {
            double dt = (now - lastAt) / 1e9;
            double d = Math.hypot(p.x - lastPos.x, p.z - lastPos.z);
            speed = speed * .6 + (d / dt) * .4;
            lastPos = p;
            lastAt = now;
        } else if (lastPos == null) { lastPos = p; lastAt = now; }
        return String.format("%.2f", speed);
    }

    // ---- elements -------------------------------------------------------------------------------------

    static final class Watermark extends HudElement {
        Watermark() { super("water_watermark", "Watermark", .01f, .01f); }
        @Override public boolean wantsBackground() { return false; }
        @Override public float width() { return Text.width("water.lol", 28f) + 4; }
        @Override public float height() { return 30; }
        @Override public void render(DrawContext g, boolean preview) {
            gradText(g, "water.lol", 1, 15, 28f);
            gradText(g, "water.lol", 1.6f, 15, 28f);   // faux bold
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
        Promo() { super("water_promo", "Promo", .03f, .34f); }
        @Override public boolean wantsBackground() { return false; }
        @Override public float width() { return Text.width("water.lol Client", 8f) + 14; }
        @Override public float height() { return 15; }
        @Override public void render(DrawContext g, boolean preview) {
            panel(g, width(), height(), 6);
            gradText(g, "water.lol Client", 7, 7.5f, 8f);
        }
    }

    static final class Keybinds extends HudElement {
        Keybinds() { super("water_keybinds", "KeybindsHUD", .22f, .15f); }
        @Override public boolean wantsBackground() { return false; }

        private List<String[]> rows(boolean preview) {
            List<String[]> out = new ArrayList<>();
            var all = ModuleBinds.save();
            for (var e : all.entrySet()) {
                Module m = ModuleManager.get(e.getKey());
                if (m == null) continue;
                String key = ModuleBinds.name(e.getValue().getAsInt());
                out.add(new String[]{m.getName(), key.isEmpty() ? "?" : key, m.isEnabled() ? "1" : "0"});
            }
            if (out.isEmpty() && preview) {
                out.add(new String[]{"Spear Lunge", "X", "1"});
                out.add(new String[]{"Xbow Cart", "X", "0"});
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
                boolean on = r[2].equals("1");
                Draw.rect(g, 3, y, w - 6, 13, 4, on ? 0x26FFFFFF : 0x12FFFFFF);
                Text.drawCentered(g, r[0], 8, y + 6.5f, 8f, on ? WHITE : MUTED);
                Text.drawCentered(g, r[1], w - 8 - Text.width(r[1], 8f), y + 6.5f, 8f, on ? a() : Colors.withAlpha(a(), .5f));
                y += 15;
            }
        }
    }

    static final class Target extends HudElement {
        Target() { super("water_target", "TargetHUD", .38f, .08f); }
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
            panel(g, 150, 46, 7);
            if (t instanceof AbstractClientPlayerEntity p) net.minecraft.client.gui.PlayerSkinDrawer.draw(g, p.getSkin(), 5, 5, 36);
            else Draw.rect(g, 5, 5, 36, 36, 4, 0x33FFFFFF);
            Text.drawCentered(g, Text.fit(t.getName().getString(), 96, 8.5f), 48, 11, 8.5f, WHITE);
            float hp = t.getHealth() + t.getAbsorptionAmount(), max = Math.max(1, t.getMaxHealth());
            float frac = MathHelper.clamp(hp / max, 0, 1);
            Draw.rect(g, 48, 20, 96, 5, 2, 0x33FFFFFF);
            g.enableScissor(48, 20, 48 + Math.max(1, Math.round(96 * frac)), 25);
            gradBar(g, 48, 20, 96, 5);
            g.disableScissor();
            Text.drawCentered(g, String.format("%.1f HP", hp), 48, 34, 7.5f, MUTED);
            String dist = Math.round(mc.player.distanceTo(t)) + "m";
            Text.drawCentered(g, dist, 144 - Text.width(dist, 7.5f), 34, 7.5f, MUTED);
        }
    }

    static final class ModuleList extends HudElement {
        ModuleList() { super("water_modules", "Module List", .99f, .08f); }
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
        Effects() { super("water_effects", "Effects", .01f, .47f); }
        @Override public boolean wantsBackground() { return false; }
        private List<StatusEffectInstance> list() {
            return mc.player == null ? List.of() : new ArrayList<>(mc.player.getStatusEffects());
        }
        @Override public boolean active() { return !list().isEmpty(); }
        @Override public float width() { return 120; }
        @Override public float height() { return Math.max(1, list().size()) * 22; }
        @Override public void render(DrawContext g, boolean preview) {
            List<StatusEffectInstance> l = list();
            if (l.isEmpty() && preview) { panel(g, 120, 20, 6); Text.drawCentered(g, "Speed II", 26, 10, 8f, WHITE); ring(g, 11, 10, 7, .6f); return; }
            float y = 0;
            for (StatusEffectInstance e : l) {
                g.getMatrices().pushMatrix();
                g.getMatrices().translate(0, y);
                panel(g, 120, 20, 6);
                float frac = e.isInfinite() ? 1 : MathHelper.clamp(e.getDuration() / 1200f, 0, 1);
                ring(g, 11, 10, 7, frac);
                String name = e.getEffectType().value().getName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
                Text.drawCentered(g, Text.fit(name, 70, 8f), 24, 10, 8f, WHITE);
                String time = e.isInfinite() ? "∞" : (e.getDuration() / 20 / 60) + ":" + String.format("%02d", e.getDuration() / 20 % 60);
                Text.drawCentered(g, time, 114 - Text.width(time, 7f), 10, 7f, a());
                g.getMatrices().popMatrix();
                y += 22;
            }
        }
        private static String roman(int n) { return switch (n) { case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> Integer.toString(n); }; }
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
        Inventory() { super("water_inventory", "Inventory", .01f, .6f); }
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
        Radar() { super("water_radar", "Radar", .75f, .6f); }
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
}
