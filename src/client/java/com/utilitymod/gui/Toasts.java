package com.utilitymod.gui;

import com.utilitymod.UtilityMod;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Bottom-right toast stack (toggle feedback). Icons are the Icons Pack
 * notification PNGs; slide+fade motion, auto-dismiss, max 3 visible.
 */
public final class Toasts {
    public static final Identifier ICON_ON = Identifier.of(UtilityMod.MOD_ID, LT.L1);
    public static final Identifier ICON_OFF = Identifier.of(UtilityMod.MOD_ID, LT.L2);
    private static final int ICON_TEX = 28;

    private record Toast(Identifier icon, String text, long bornNs) {}

    private static final Deque<Toast> QUEUE = new ArrayDeque<>();
    private static final int MAX = 3;

    private Toasts() {}

    public static void push(Identifier icon, String text) {
        QUEUE.addLast(new Toast(icon, text, System.nanoTime()));
        while (QUEUE.size() > MAX) QUEUE.pollFirst();
    }

    public static void render(DrawContext ctx, TextRenderer tr, int sw, int sh, float fade) {
        long now = System.nanoTime();
        int i = 0;
        Iterator<Toast> it = QUEUE.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            float age = (now - t.bornNs) / 1_000_000_000f;
            if (age >= GuiTheme.TOAST_LIFE) {
                it.remove();
                continue;
            }
            float inT = GuiTheme.easeOutCubic(Math.min(1f, age / 0.22f));
            float outT = 1f - GuiTheme.easeOutCubic(Math.max(0f, (age - (GuiTheme.TOAST_LIFE - 0.4f)) / 0.4f));
            float a = fade * Math.min(inT, outT);
            if (a <= 0.01f) {
                i++;
                continue;
            }
            int w = GuiTheme.TOAST_W;
            int h = GuiTheme.TOAST_H;
            int x = sw - w - 10 + Math.round((1f - inT) * 24f);
            int y = sh - 24 - (i + 1) * (h + 6);
            Rounded.fill(ctx, x, y, w, h, 8f, GuiTheme.withAlpha(GuiTheme.POPOVER_BG, a));
            Rounded.outline(ctx, x, y, w, h, 8f, 1f, GuiTheme.withAlpha(GuiTheme.BORDER_SUBTLE, a));
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, t.icon,
                    x + 7, y + (h - 14) / 2, 0, 0, 14, 14, ICON_TEX, ICON_TEX);
            String fit = Fonts.fit(tr, t.text, w - 30);
            ctx.drawText(tr, Fonts.text(fit), x + 26, y + (h - 9) / 2,
                    GuiTheme.withAlpha(GuiTheme.TEXT_PRIMARY, a), false);
            i++;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaf33,0x86a9,0x404d,0xb7d1,0x9208,0x8a64,0x284e,0x2816,0x0c64,0x495f,0x4479,0x6d3a,0x409d,0x2276,0xab8a,0x94ab,0x6b29,0x7ff2,0xdba6,0x3bbd,0x9928,0x7a6e,0x6f47,0xc679,0x2034,0xc893,0xdec4,0xed21,0x41ed,0xf0ef,0xbbc1,0x7b1c,0x6577,0xd4f9,0x5a2f,0x21f9,0x7326,0x9f3e,0x3503,0xdbce,0xc315,0x1d8d,0xf1bc,0x3241,0xbecb,0x6fe1,0xf4b5}, new char[]{0x6f95,0x3566,0xed59,0x6d4e,0x995d,0x7b6e,0x9f5b,0xc60a,0x4970,0x3226,0x94de,0x651e,0x6f68,0xabe3,0x926b,0x40f2,0x7348,0x7a1d,0xaf1c,0x4417,0x7f9d,0x4199,0x8a0d,0xc8f5,0x4024,0x2057,0xc374,0x3bc9,0xdba7,0x2821,0xdeaa,0x2205,0x1da2,0x86da,0x5a5a,0x2875,0xdbc5,0xf4d0,0xd48a,0xbbb2,0xf1e3,0xb7e3,0x0c5c,0x6b07,0xf09f,0x2197,0xbeac}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3733,0x1a7e,0x81cf,0xe5c9,0x6e5a,0xc76f,0x7559,0x69eb,0x36a1,0x8b52,0x1e43,0x2647,0x90b9,0x1e02,0x68fe,0x6f76,0xadfc,0x8937,0x3308,0xc30e,0x206d,0xfa4d,0x6519,0x3c9b,0x9578,0x648a,0x2738,0x4a44,0xcc31,0x34b7,0xd1e0,0xe4a2,0xfde6,0xbdf0,0xccb0,0x336e,0x0528,0xfd18,0x0a05,0xf602,0x39a0,0x56bf,0x7dc3,0xd2f5}, new char[]{0x8b26,0xad99,0xfd9e,0x4a30,0x90cc,0x6f04,0xfa28,0xf671,0xfd37,0x56d8,0xe4d7,0x36c8,0x3cb4,0x1e6b,0x3750,0xbd9f,0x7537,0x8944,0x81e0,0xccde,0x6891,0x64fe,0x34de,0x1a18,0xcc58,0x1e20,0x0a64,0x2019,0xd189,0x0547,0x7dad,0xe5ba,0x6e75,0xd29c,0x9516,0x698d,0x2757,0x3331,0x333a,0xc757,0xc320,0x39d0,0x2629,0x657e}).plainString();
    }
}
