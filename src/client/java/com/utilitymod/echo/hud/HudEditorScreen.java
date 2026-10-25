package com.utilitymod.echo.hud;

import com.utilitymod.gui.GuiConfig;
import com.utilitymod.gui.UiSounds;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import com.utilitymod.echo.render3.api.FramebufferTarget;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

/**
 * HUD editor: drag elements (snaps to screen edges, the centre lines and other
 * elements), scroll over one to resize it, right-click to hide/show it, and
 * "Reset" to restore defaults. Esc closes and saves.
 */
public final class HudEditorScreen extends Screen {
    private static final float SNAP = 4f;

    private HudElement dragging;
    private float grabX, grabY;
    private float guideX = -1, guideY = -1;
    private final net.minecraft.client.gui.screen.Screen parent;

    public HudEditorScreen(net.minecraft.client.gui.screen.Screen parent) {
        super(net.minecraft.text.Text.literal(LT.L1));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private float[] resetButton() {
        return new float[] {width / 2f - 36, height - 26, 72, 18};
    }

    private HudElement at(double mx, double my) {
        var list = HudManager.elements();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudElement e = list.get(i);
            float[] r = HudManager.rect(e, width, height);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return e;
        }
        return null;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x55000000);
        // centre guides
        FramebufferTarget.rect(ctx, width / 2f - 0.5f, 0, 1, height, 0x22FFFFFF);
        FramebufferTarget.rect(ctx, 0, height / 2f - 0.5f, width, 1, 0x22FFFFFF);
        if (guideX >= 0) FramebufferTarget.rect(ctx, guideX - 0.5f, 0, 1, height, Colors.withAlpha(Theme.ACCENT, 0.8f));
        if (guideY >= 0) FramebufferTarget.rect(ctx, 0, guideY - 0.5f, width, 1, Colors.withAlpha(Theme.ACCENT, 0.8f));

        HudManager.draw(ctx, true);

        HudElement hover = dragging != null ? dragging : at(mouseX, mouseY);
        for (HudElement e : HudManager.elements()) {
            float[] r = HudManager.rect(e, width, height);
            int c = !e.visible ? 0x66FF5555 : e == hover ? Colors.withAlpha(Theme.ACCENT, 0.95f) : 0x66FFFFFF;
            outline(ctx, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, c);
            if (!e.visible) {
                FramebufferTarget.rect(ctx, r[0], r[1], r[2], r[3], 0x30FF5555);
            }
            if (e == hover) {
                String label = e.name + (e.visible ? "" : LT.L2) + String.format(LT.L3, e.scale * 100);
                float ly = r[1] - 11 < 2 ? r[1] + r[3] + 3 : r[1] - 11;
                Text.draw(ctx, label, r[0], ly + 7, 7f, 0xFFFFFFFF);
            }
        }

        drawPanel(ctx, mouseX, mouseY);

        float[] b = resetButton();
        boolean overReset = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
        Draw.rect(ctx, Math.round(b[0]), Math.round(b[1]), Math.round(b[2]), Math.round(b[3]), 5,
                overReset ? Theme.CONTROL_ACTIVE : Theme.CONTROL);
        Text.drawCenteredX(ctx, LT.L4, b[0] + b[2] / 2f, b[1] + b[3] / 2f, 7.5f, Theme.TEXT);
        Text.drawCenteredX(ctx, "Drag to move  •  Scroll to resize  •  Right-click to hide/show  •  Esc to save",
                width / 2f, 12, 7f, 0xFFDDDDDD);
    }

    // ---- "HUD Modules" toggle panel ------------------------------------------------------------

    private float[] panelRect() {
        int n = HudManager.elements().size();
        float w = 150, rowH = 18, h = 30 + n * rowH + 8;
        return new float[]{width / 2f - w / 2, 22, w, Math.min(h, height - 60), rowH};
    }

    private void drawPanel(DrawContext ctx, int mx, int my) {
        float[] p = panelRect();
        int accent = com.utilitymod.gui.CardGuiStyle.accent;
        Draw.rect(ctx, p[0], p[1], p[2], Math.round(p[3]), 8, 0xF2161A22);
        com.utilitymod.gui.Rounded.outline(ctx, Math.round(p[0]), Math.round(p[1]), Math.round(p[2]), Math.round(p[3]), 8, 1f, 0x18FFFFFF);
        Text.drawCenteredX(ctx, LT2.L1, p[0] + p[2] / 2, p[1] + 12, 8.5f, 0xFFFFFFFF);
        FramebufferTarget.rect(ctx, p[0] + 8, p[1] + 22, p[2] - 16, 1, 0x22FFFFFF);
        float y = p[1] + 28;
        for (HudElement e : HudManager.elements()) {
            if (y + p[4] > p[1] + p[3]) break;
            Text.drawCentered(ctx, e.name, p[0] + 10, y + p[4] / 2, 7.5f, e.visible ? 0xFFE8ECF3 : 0xFF8C93A3);
            float tx = p[0] + p[2] - 30, ty = y + p[4] / 2 - 5;
            Draw.rect(ctx, tx, ty, 20, 10, 5, e.visible ? accent : 0xFF2A303C);
            Draw.rect(ctx, e.visible ? tx + 11 : tx + 1, ty + 1, 8, 8, 4, 0xFFFFFFFF);
            y += p[4];
        }
    }

    /** Click on the panel toggles the row under the mouse; true if the panel took the click. */
    private boolean panelClick(double mx, double my) {
        float[] p = panelRect();
        if (mx < p[0] || mx >= p[0] + p[2] || my < p[1] || my >= p[1] + p[3]) return false;
        float y = p[1] + 28;
        for (HudElement e : HudManager.elements()) {
            if (my >= y && my < y + p[4]) {
                e.visible = !e.visible;
                UiSounds.play(e.visible ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
                break;
            }
            y += p[4];
        }
        return true;
    }

    private static void outline(DrawContext ctx, float x, float y, float w, float h, int c) {
        FramebufferTarget.rect(ctx, x, y, w, 1, c);
        FramebufferTarget.rect(ctx, x, y + h - 1, w, 1, c);
        FramebufferTarget.rect(ctx, x, y, 1, h, c);
        FramebufferTarget.rect(ctx, x + w - 1, y, 1, h, c);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mx = click.x(), my = click.y();
        if (click.button() == 0 && panelClick(mx, my)) return true;
        float[] b = resetButton();
        if (click.button() == 0 && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
            for (HudElement e : HudManager.elements()) e.reset();
            UiSounds.play(UiSounds.Sound.CLICK);
            return true;
        }
        HudElement e = at(mx, my);
        if (e == null) return super.mouseClicked(click, doubleClick);
        if (click.button() == 1) {
            e.visible = !e.visible;
            UiSounds.play(e.visible ? UiSounds.Sound.TOGGLE_ON : UiSounds.Sound.TOGGLE_OFF);
            return true;
        }
        if (click.button() == 0) {
            float[] r = HudManager.rect(e, width, height);
            dragging = e;
            grabX = (float) (mx - r[0]);
            grabY = (float) (my - r[1]);
            // bring to front
            HudManager.elements().remove(e);
            HudManager.elements().add(e);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(click, dx, dy);
        float[] r = HudManager.rect(dragging, width, height);
        float nx = (float) click.x() - grabX, ny = (float) click.y() - grabY;
        guideX = guideY = -1;
        // snap: screen edges, centre lines, other elements' edges
        java.util.List<float[]> xs = new java.util.ArrayList<>(), ys = new java.util.ArrayList<>();
        xs.add(new float[] {0, 0});
        xs.add(new float[] {width, width});
        xs.add(new float[] {width / 2f, width / 2f});
        ys.add(new float[] {0, 0});
        ys.add(new float[] {height, height});
        ys.add(new float[] {height / 2f, height / 2f});
        for (HudElement o : HudManager.elements()) {
            if (o == dragging || !o.visible) continue;
            float[] or = HudManager.rect(o, width, height);
            xs.add(new float[] {or[0], or[0]});
            xs.add(new float[] {or[0] + or[2], or[0] + or[2]});
            ys.add(new float[] {or[1], or[1]});
            ys.add(new float[] {or[1] + or[3], or[1] + or[3]});
        }
        float bestDx = SNAP + 1, bestDy = SNAP + 1;
        for (float[] g : xs) {
            for (float edge : new float[] {nx, nx + r[2] / 2f, nx + r[2]}) {
                float d = g[0] - edge;
                if (Math.abs(d) < Math.abs(bestDx) && Math.abs(d) <= SNAP) {
                    bestDx = d;
                    guideX = g[0];
                }
            }
        }
        for (float[] g : ys) {
            for (float edge : new float[] {ny, ny + r[3] / 2f, ny + r[3]}) {
                float d = g[0] - edge;
                if (Math.abs(d) < Math.abs(bestDy) && Math.abs(d) <= SNAP) {
                    bestDy = d;
                    guideY = g[0];
                }
            }
        }
        if (Math.abs(bestDx) <= SNAP) nx += bestDx;
        if (Math.abs(bestDy) <= SNAP) ny += bestDy;
        HudManager.place(dragging, nx, ny, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            guideX = guideY = -1;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        HudElement e = at(mx, my);
        if (e == null) return false;
        e.scale = Math.max(0.5f, Math.min(2.5f, e.scale + (v > 0 ? 0.1f : -0.1f)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
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
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5798,0xcf07,0xf164,0x2eb7,0x9afe,0x4c6c,0xc472,0x687f,0x0ba4,0xff32}, new char[]{0x4c24,0xf131,0xff76,0x57b8,0x2ef2,0x9a9a,0xc41b,0xcf73,0x0bcb,0x680d}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x894a,0xf88e,0x4747,0x90ae,0xad51,0x8594,0x50e2,0xd7da,0x4ee3}, new char[]{0xad71,0x476f,0x4e8b,0xd7b3,0xf8ea,0x5086,0x892f,0x90c0,0x85bd}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7059,0x4578,0xc6b3,0x8454,0x0120,0x9e35,0xddf7,0x4834}, new char[]{0x8474,0x9e15,0x4811,0x7077,0xc683,0xdd91,0x0105,0x455d}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb5f0,0xdbea,0xe030,0xe5ee,0x02d6}, new char[]{0xe5bc,0xdb8f,0xe043,0xb595,0x02a2}).toString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04b6,0xddb6,0xa1cf,0x0e3f,0xda9b,0x610f,0xf3bd,0x3765,0xc6d5,0xe340,0xd7d2}, new char[]{0xe308,0x3730,0xdadf,0xc6f5,0xd79f,0xf3d2,0x04d2,0x617a,0x0e53,0xa1aa,0xddc5}).toString();
    }
}
