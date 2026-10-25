package com.utilitymod.gui;

import com.utilitymod.echo.features.settings.impl.RegistryPickerSetting;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.render.GlassRenderer;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Searchable icon grid for a block / item picker setting: type to filter, click to toggle,
 * "Selected" shows only the chosen ones, Esc goes back. Selected entries glow in the accent colour.
 */
public final class RegistryPickerScreen extends Screen {
    private static final int CELL = 22, COLS = 12, PAD = 10;

    private final Screen parent;
    private final RegistryPickerSetting<?> setting;
    private final StringBuilder search = new StringBuilder();
    private boolean onlySelected;
    private float scroll, scrollGoal;
    private List<Entry> entries = List.of();
    private String lastQuery = null;
    private boolean lastOnly;

    private record Entry(Identifier id, String name, ItemStack icon) {}
    private record Hit(float x, float y, float w, float h, Runnable action) {}
    private final List<Hit> hits = new ArrayList<>();

    public RegistryPickerScreen(Screen parent, RegistryPickerSetting<?> setting) {
        super(net.minecraft.text.Text.literal(setting.getName()));
        this.parent = parent;
        this.setting = setting;
    }

    @Override public boolean shouldPause() { return false; }

    private float scale() {
        double f = client != null ? client.getWindow().getScaleFactor() : 2;
        return (float) Math.max(0.5, Math.min(1.0, 2.0 / f));
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        String q = search.toString().trim().toLowerCase(Locale.ROOT);
        if (q.equals(lastQuery) && onlySelected == lastOnly) return;
        lastQuery = q;
        lastOnly = onlySelected;
        RegistryPickerSetting<Object> s = (RegistryPickerSetting<Object>) setting;
        List<Entry> out = new ArrayList<>();
        for (Object o : s.getRegistry()) {
            Identifier id = s.getRegistry().getId(o);
            if (id == null) continue;
            ItemStack icon = o instanceof Block b ? new ItemStack(b.asItem()) : o instanceof Item i ? new ItemStack(i) : ItemStack.EMPTY;
            if (icon.isEmpty() || icon.isOf(Items.AIR)) continue; // no icon to show (e.g. fire, water)
            String name = s.getNameProvider().apply(o);
            if (onlySelected && !s.isSelected(id)) continue;
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !id.getPath().contains(q)) continue;
            out.add(new Entry(id, name, icon));
        }
        entries = out;
        scrollGoal = 0;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        rebuild();
        hits.clear();
        float sc = scale();
        float vw = width / sc, vh = height / sc;
        float w = COLS * CELL + PAD * 2, h = Math.min(vh - 30, 330);
        float x = (vw - w) / 2, y = (vh - h) / 2;
        int mx = Math.round(mouseX / sc), my = Math.round(mouseY / sc);

        boolean glass = HudManager.liquidGlass && GlassRenderer.available();
        if (glass) {
            GlassRenderer.queue(new GlassRenderer.Panel(x * sc, y * sc, w * sc, h * sc, 10 * sc, 1f));
            GlassRenderer.flush(ctx);
        }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(sc, sc);
        Draw.rect(ctx, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 10,
                Colors.withAlpha(glass ? 0xFF101216 : Theme.SIDEBAR, glass ? 0.45f : 0.96f));

        // header: title + count
        Text.drawCentered(ctx, setting.getName(), x + PAD, y + 12, 9f, 0xFFFFFFFF);
        String count = setting.getSelectedCount() + LT.L1;
        Text.drawCentered(ctx, count, x + w - PAD - Text.width(count, 7f), y + 12, 7f, Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.2f));

        // search + filter + clear
        float sy = y + 24, sw = w - PAD * 2 - 112;
        Draw.rect(ctx, Math.round(x + PAD), Math.round(sy), Math.round(sw), 16, 8, 0xFF1A1D24);
        String q = search.toString();
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        Text.drawCentered(ctx, q.isEmpty() ? LT.L2 : q + (caret ? LT.L3 : ""), x + PAD + 8, sy + 8, 7f,
                q.isEmpty() ? 0xFF7A7E88 : 0xFFFFFFFF);
        button(ctx, onlySelected ? LT.L4 : LT.L5, x + PAD + sw + 4, sy, 52, onlySelected, mx, my, () -> onlySelected = !onlySelected);
        button(ctx, LT.L6, x + PAD + sw + 60, sy, 52, false, mx, my, () -> {
            setting.setSelectedIds(List.of());
            lastQuery = null;
        });

        // grid
        float gy = sy + 24, gh = y + h - gy - PAD;
        int rows = (entries.size() + COLS - 1) / COLS;
        float maxScroll = Math.max(0, rows * CELL - gh);
        scrollGoal = Math.max(0, Math.min(maxScroll, scrollGoal));
        scroll += (scrollGoal - scroll) * 0.35f;
        ctx.enableScissor(Math.round(x), Math.round(gy), Math.round(x + w), Math.round(gy + gh));
        Entry hovered = null;
        for (int i = 0; i < entries.size(); i++) {
            float cx = x + PAD + (i % COLS) * CELL, cy = gy + (i / COLS) * CELL - scroll;
            if (cy + CELL < gy || cy > gy + gh) continue;
            Entry e = entries.get(i);
            boolean sel = setting.isSelected(e.id());
            boolean hov = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL && my >= gy && my < gy + gh;
            if (hov) hovered = e;
            if (sel) Draw.rect(ctx, Math.round(cx + 1), Math.round(cy + 1), CELL - 2, CELL - 2, 5, Colors.withAlpha(Theme.ACCENT, 0.55f));
            else if (hov) Draw.rect(ctx, Math.round(cx + 1), Math.round(cy + 1), CELL - 2, CELL - 2, 5, 0x30FFFFFF);
            ctx.drawItem(e.icon(), Math.round(cx + 3), Math.round(cy + 3));
            if (hov) {
                Identifier id = e.id();
                hits.add(new Hit(cx, cy, CELL, CELL, () -> {
                    setting.setSelected(id, !setting.isSelected(id));
                    UiSounds.play(setting.isSelected(id) ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
                    if (onlySelected) lastQuery = null;
                }));
            }
        }
        ctx.disableScissor();
        if (entries.isEmpty()) Text.drawCenteredX(ctx, onlySelected ? LT.L7 : LT.L8, x + w / 2, gy + 30, 7.5f, 0xFF8A8E98);
        if (maxScroll > 0) {
            float barH = Math.max(16, gh * gh / (gh + maxScroll));
            float barY = gy + (gh - barH) * (scroll / maxScroll);
            Draw.rect(ctx, Math.round(x + w - 5), Math.round(barY), 2, Math.round(barH), 1, 0x60FFFFFF);
        }
        // hover name
        if (hovered != null) {
            String n = hovered.name();
            float tw = Text.width(n, 7f) + 10;
            float tx = Math.min(mx + 10, vw - tw - 4), ty = my + 12;
            Draw.rect(ctx, Math.round(tx), Math.round(ty), Math.round(tw), 13, 4, 0xF0101216);
            Text.drawCentered(ctx, n, tx + 5, ty + 6.5f, 7f, 0xFFFFFFFF);
        }
        Text.drawCenteredX(ctx, LT.L9, vw / 2, y + h + 9, 6.5f, 0xFFB8BCC6);
        m.popMatrix();
    }

    private void button(DrawContext ctx, String label, float x, float y, float w, boolean on, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 16;
        Draw.rect(ctx, Math.round(x), Math.round(y), Math.round(w), 16, 8,
                on ? Theme.ACCENT : hov ? 0xFF3A3D45 : 0xFF2A2C33);
        Text.drawCenteredX(ctx, label, x + w / 2, y + 8, 7f, on ? 0xFF15161A : 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, 16, () -> {
            action.run();
            UiSounds.play(UiSounds.Sound.CLICK);
        }));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        float sc = scale();
        double mx = click.x() / sc, my = click.y() / sc;
        for (Hit h : hits) {
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollGoal -= (float) v * CELL * 2;
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (search.length() < 32) search.append(input.asString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search.setLength(search.length() - 1);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        GuiConfig.save();
        client.setScreen(parent);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x442d,0xe682,0x9bf8,0xf2c2,0x742e,0x9a1e,0x1e8b,0xc966,0xd965}, new char[]{0xf2e2,0xc915,0x9b9d,0xe6ee,0x1eee,0xd906,0x745a,0x9a7b,0x4449}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf7d7,0x3955,0x40ff,0xb6c4,0x070c,0xc2e0,0x9adb,0x59d0,0x9148,0xab63,0x0180,0x697f,0x1149,0x9a86,0x4375,0xb81d,0x4496}, new char[]{0x44c2,0x1130,0x9138,0xb6a1,0x4355,0x3921,0x01ef,0x40df,0x9aa8,0x59b5,0x076d,0xab11,0xc283,0x6917,0xf7f9,0x9aa8,0xb833}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4501}, new char[]{0x457d,0x7be9,0x2ee8,0x12f3}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbf6b,0x6445,0x9f27,0xf3d6,0x1d27,0x5b72,0x3419,0xe44c}, new char[]{0xf385,0x1d42,0x6429,0x5b17,0x9f44,0xe438,0xbf0e,0x347d}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd74c,0xa0b4,0xf2e3}, new char[]{0xd70d,0xa0d8,0xf28f,0x1015}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x538e,0x8b2d,0xd047,0x6d90,0xdc3c}, new char[]{0xd004,0x6dfc,0xdc59,0x8b4c,0x53fc}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x116d,0x0f61,0x4cb9,0x546f,0x2549,0xcb12,0xde62,0x9404,0xe8a3,0xd541,0x1d05,0xec45,0xb449,0xd9c0,0x1178,0xe1c3,0x4633,0xa483,0x472a,0x2add}, new char[]{0xe18d,0x5400,0x475e,0x1105,0xde0b,0xe8cd,0x4cde,0xec65,0x9477,0x111d,0x2ab1,0x0f04,0xcb71,0xd9b4,0x1d60,0x252d,0xd561,0x464a,0xa4e6,0xb43d}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x45df,0xc7be,0x67eb,0x6894,0x3f7b,0x3ecc,0x36db,0x9088,0x11aa,0x80f8}, new char[]{0x4591,0x3f14,0x68b4,0xc7d3,0x11cb,0x679f,0x90eb,0x3ea4,0x36be,0x808b}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba46,0x41d3,0x9fcc,0x2e8d,0x16be,0x1b96,0xfa7b,0xb04b,0x14ee,0xac91,0x0616,0x4370,0xcde2,0x5af2,0xf4a6,0x4a3d,0xd41a,0x334f,0xb085,0x8dd4,0x9728,0x31eb,0x9f98,0x05a0,0x4e34,0x5252,0xe9bc,0x9672,0x4bca,0x9b0b,0x0d62,0x010d,0x0f31,0xaf44,0xf757,0x93cd,0xf0be,0xf009,0x95a6,0xc4e5,0x5fa2,0x1227,0xb6ee,0x3792,0xd06e,0xfbf9,0xdcf6,0x8fd2,0x8818,0xdb1f,0x29d6,0x822b,0x114f}, new char[]{0xb008,0xd002,0x9ff1,0x16dd,0x29bd,0xfa5b,0x5fd6,0xdc99,0x1bb6,0xb0f6,0x4e51,0x8874,0xacf4,0x05c3,0x5a86,0xd43a,0xb6ce,0x9bbc,0x8df4,0x116f,0x975c,0x93b4,0xf0ce,0x0673,0xc4c5,0x9fb8,0xf066,0x37b2,0xdb6c,0xf732,0xba27,0x1255,0x3188,0x961a,0x5272,0xcdc2,0xaff3,0x4bea,0x0d42,0x826e,0x333c,0x016e,0x41f3,0x2ef9,0xe9d3,0x0f11,0xf4c1,0x8fbd,0x14ce,0x4a5f,0x4311,0x95c5,0xfb92}).toString();
    }
}
