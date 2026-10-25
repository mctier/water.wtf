package com.utilitymod.gui;

import com.utilitymod.anubis.AnubisBridge;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.features.impl.render.Notifications;
import com.utilitymod.echo.hud.HudEditorScreen;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.render.GlassRenderer;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Onboarding: a short welcome card after injection, then a first-time guide. In-game the guide is
 * one slim pill with the current step; inside the dropdown GUI it becomes a spotlight on the thing
 * to click (see DropdownGui#drawCoachMark). Steps tick themselves off as you do them; finishing or
 * skipping is remembered in the config.
 */
public final class Welcome {
    /** Saved: the guide was finished or skipped. */
    public static volatile boolean guideDone;

    public enum Step {
        OPEN(LT.L1, LT.L2),
        TOGGLE(LT.L3, LT.L4),
        BIND(LT.L5, LT.L6),
        HUD(LT.L7, LT.L8),
        THEME(LT.L9, LT.L10);

        public final String title, hint;

        Step(String title, String hint) {
            this.title = title;
            this.hint = hint;
        }
    }

    private static final Step[] STEPS = Step.values();
    private static final boolean[] done = new boolean[STEPS.length];

    private static long shownAt;
    private static boolean welcomed;
    private static float slide, progressShown;
    private static int startAccent = -1;
    private static long lastFrame = System.nanoTime();
    private static long allDoneAt;

    private Welcome() {}

    // ---- state -----------------------------------------------------------------------------------

    /** Current step (first unfinished), or null when the guide is finished / skipped. */
    public static Step current() {
        if (guideDone) return null;
        for (int i = 0; i < STEPS.length; i++) if (!done[i]) return STEPS[i];
        return null;
    }

    public static int completed() {
        int n = 0;
        for (boolean d : done) if (d) n++;
        return n;
    }

    public static int total() { return STEPS.length; }

    public static void moduleToggled() { mark(Step.TOGGLE); }

    public static void keyBound() { mark(Step.BIND); }

    /** Polled from the HUD pass and the GUI. */
    public static void poll() {
        if (guideDone) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (startAccent == -1) startAccent = Theme.accentRgb();
        if (FrameManager.isOpen()) mark(Step.OPEN);
        if (mc.currentScreen instanceof HudEditorScreen) mark(Step.HUD);
        if (Theme.accentRgb() != startAccent) mark(Step.THEME);
    }

    private static void mark(Step s) {
        if (guideDone || done[s.ordinal()]) return;
        done[s.ordinal()] = true;
        UiSounds.play(UiSounds.Sound.TOGGLE_ON);
        for (boolean d : done) if (!d) return;
        allDoneAt = System.currentTimeMillis();
        guideDone = true;
        GuiConfig.save();
        Notifications.push(LT.L11, LT.L12, Notifications.Type.ENABLED);
    }

    public static void restart() {
        guideDone = false;
        allDoneAt = 0;
        java.util.Arrays.fill(done, false);
        startAccent = Theme.accentRgb();
        welcomed = true;
        GuiConfig.save();
    }

    public static void skip() {
        guideDone = true;
        GuiConfig.save();
    }

    private static float approach(float cur, float target, float rate, float dt) {
        return target + (cur - target) * (float) Math.exp(-rate * dt);
    }

    // ---- in-game card (HUD pass) -----------------------------------------------------------------

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        if (mc.player == null || mc.options.hudHidden) return;
        long now = System.currentTimeMillis();
        if (shownAt == 0) shownAt = now;
        poll();
        if (mc.currentScreen != null) return; // the GUI shows its own spotlight

        long age = now - shownAt;
        boolean greeting = !welcomed && age < 5000;
        if (!greeting) welcomed = true;
        Step step = current();
        boolean finishing = allDoneAt != 0 && now - allDoneAt < 1800;
        boolean visible = greeting || step != null || finishing;
        slide = approach(slide, visible ? 1f : 0f, visible ? 10f : 8f, dt);
        if (slide < 0.01f) return;

        int sw = ctx.getScaledWindowWidth();
        float a = 1f - (1f - slide) * (1f - slide);
        int accent = Colors.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.2f);

        String left, right;
        if (greeting) {
            left = LT.L13 + mc.player.getName().getString();
            right = (ModuleManager.all().size() + AnubisBridge.all().size()) + LT.L14;
        } else if (step != null) {
            left = completed() + LT.L15 + total() + "  " + step.title;
            right = step.hint;
        } else {
            left = LT.L16;
            right = LT.L17;
        }
        float lw = Text.width(left, 7.5f), rw = Text.width(right, 6.8f);
        float w = lw + rw + 34, h = 18;
        float x = (sw - w) / 2f, y = 8 - (1f - a) * 24;

        boolean glass = HudManager.liquidGlass && GlassRenderer.available();
        if (glass) {
            GlassRenderer.queue(new GlassRenderer.Panel(x, y, w, h, h / 2f, a));
            GlassRenderer.flush(ctx);
        }
        Draw.rect(ctx, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Math.round(h / 2),
                Colors.withAlpha(glass ? 0xFF101216 : Theme.SIDEBAR, (glass ? 0.45f : 0.9f) * a));
        // accent dot, title, divider, hint
        Draw.rect(ctx, Math.round(x + 9), Math.round(y + h / 2 - 2), 4, 4, 2, Colors.withAlpha(accent, a));
        Text.drawCentered(ctx, left, x + 18, y + h / 2, 7.5f, Colors.withAlpha(0xFFFFFFFF, a));
        Draw.rect(ctx, Math.round(x + 24 + lw), Math.round(y + 5), 1, Math.round(h - 10), 0, Colors.withAlpha(0xFFFFFFFF, 0.15f * a));
        Text.drawCentered(ctx, right, x + 29 + lw, y + h / 2, 6.8f, Colors.withAlpha(0xFFB8BCC6, a));

        // thin progress line under the pill (timer while greeting)
        float target = greeting ? 1f - Math.min(1f, age / 5000f) : completed() / (float) total();
        progressShown = approach(progressShown, target, 10f, dt);
        float pw = w - h;
        Draw.rect(ctx, Math.round(x + h / 2), Math.round(y + h - 2), Math.max(1, Math.round(pw * progressShown)), 1, 0,
                Colors.withAlpha(accent, 0.8f * a));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcb4c,0x17e2,0x98d2,0x08b3,0x95c4,0x752f,0x0ef7,0x0d43,0xb96e,0x2e18,0x2e23,0x55b5,0x19e2}, new char[]{0x08fc,0x2e68,0x98b7,0x0e99,0x95e4,0xcb38,0x2e4b,0x1787,0x19c2,0x0d2e,0x55d0,0x7541,0xb91b}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x71cf,0x5226,0xb7d9,0x186d,0xa4b3,0xf280,0xa917,0xa220,0xcaec,0x9046,0x03df,0x7888,0xd672,0x4cac,0x88ba,0x0de7,0x7afe}, new char[]{0x7aae,0x71bd,0x1808,0x78fb,0xa964,0xd652,0x4cfe,0x03b6,0xb7be,0xa4db,0x88ce,0xf2a0,0xa273,0x902e,0x0d8e,0x5240,0xca98}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5d23,0x8e88,0xb263,0xa4ae,0x6acf,0xdf9b,0x8f0e,0xf458,0x9bb9,0x05e7,0x2720,0x3e54,0x9128,0x48c6,0xecb4,0xe4c7}, new char[]{0x917c,0x48b3,0xb211,0xdff5,0xec94,0x6aa0,0xa4c0,0x2700,0x5d42,0x9b99,0x8ee5,0x0588,0xf43c,0x8f7b,0xe4ab,0x3e31}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x737d,0x7372,0xf09a,0x4d11,0x25f5,0x7a56,0x1918,0xa8ca,0xe3c2,0xd681,0xa41c,0x9a31,0xab55,0xd021,0x5b0b,0xe3c1,0x6a23,0x8ccb,0x9f4c,0xeabf,0x4431}, new char[]{0x5b47,0x7318,0x7314,0x9a45,0xe3ec,0x6a40,0x9f20,0xe3ab,0xab36,0xd04a,0x7a76,0xa8ab,0x4d7f,0xa465,0x1938,0x445c,0xead0,0xf0fe,0x8cbe,0x2599,0xd6e4}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6002,0x87db,0x8584,0xc384,0x34c8,0xec98,0x5b31,0xa553,0x0ac2,0xa152,0xb5d4,0x17c2,0x9f8d,0xe07d,0x41d2,0x664b,0x6a00,0x9f1d,0x12fc,0xa149,0xe7a5,0xbefe}, new char[]{0x8799,0xe014,0xecf6,0x85e0,0x34e8,0x6063,0xe785,0x41bf,0x6a6f,0xa537,0xbe8b,0x6627,0x9fe8,0xb5f4,0xa126,0x5b5e,0x9f3d,0xa128,0x0ae2,0x1297,0xc3e1,0x17bb}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc7cb,0x33b5,0x2005,0xfa0a,0xe8d3,0xb240,0xe932,0x31e5,0x5c65,0x38fc,0xec30,0x2401,0xed5a,0xd6b7,0x02a1,0x9479,0x99a5,0x886f,0x9230,0x6f31,0x7583,0x6179,0x5ade,0x8c9f,0x94aa,0x06b0,0x9eef,0x400b,0x2d98,0x1ebd,0xf726,0x542c,0x7534,0x4476,0x9513,0x7bfb,0x83e5,0x0a14,0xafee}, new char[]{0xe89e,0xf74f,0x406f,0x02c5,0xfa66,0x5c00,0x83c8,0xed39,0x8cf3,0x2df1,0xaf8d,0xc7a0,0x540c,0x94cb,0x3395,0x2068,0x5ab1,0x9577,0x3889,0x9415,0xec55,0x7518,0x75a3,0x9244,0x99cd,0x1ed8,0x246f,0x31c5,0xe942,0x9e9d,0x880a,0xd6c4,0x4405,0x6159,0xb221,0x6f11,0x7b90,0x0a71,0x06c9}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa89c,0x08b5,0x19eb,0xb5df,0x262c,0x01cd,0x00ea,0xcfd7,0xa9b3,0x6020,0xfef5,0xb083,0x5c91,0x571c,0x896d,0x83f5}, new char[]{0x08f4,0x8387,0xcfa5,0x6041,0x5cff,0xa9d4,0xb0e6,0xfed5,0x0093,0x8902,0xa8e9,0x265e,0x19cb,0xb597,0x0198,0x5758}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95eb,0xc0a6,0xe6e5,0x0cd3,0x2b8d,0x1e95,0xbb20,0x0c27,0x0127,0x025f,0x5f09,0x4a11,0xca04,0x6fe4,0xf05d,0x9b46}, new char[]{0xf012,0x6f94,0x4a74,0x0231,0x2bad,0x9b32,0x014f,0xca61,0xe6c5,0x1edd,0xc0f3,0x5f4d,0x95cb,0x0c53,0x0cb2,0xbb42}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc81d,0x43bf,0x0333,0x905c,0x9718,0x6bdb,0x714a,0x51ba,0x0ca9,0x6e60,0x57c7,0x01a0}, new char[]{0x711a,0x9771,0x0350,0x01cb,0x907c,0x43de,0x519a,0x0cdd,0xc875,0x57a2,0x6e0d,0x6bbe}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4f2e,0xe750,0xafaf,0x65c4,0x2ad4,0x8948,0x617f,0x01c0,0xb177,0xd962,0x1217,0x1df4,0x7f86,0xa720,0x88e7,0x73b0,0xf1c1,0x563e,0x2c66,0x5b38,0x5d5e,0x0c8f,0xaaa5,0x57e9,0x88e5,0x0e8b,0x7760,0x9891,0xe75a,0xbd79,0xb053,0xe22a,0x2396}, new char[]{0x890b,0x23fe,0x770f,0xf1ae,0xb104,0xe735,0x615f,0x2ab5,0xd90c,0x98b1,0x5b59,0x5d3d,0x0ee8,0x1272,0xe734,0x01b4,0xe20a,0x2c0f,0xafc1,0x7fa6,0x0cfb,0x4f46,0xb036,0xbd59,0x566a,0x1d9c,0x578c,0x65a9,0xa745,0x88c5,0x73c4,0xaac4,0x8885}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe836,0xd154,0x34c2,0x3dde,0x44d9,0x5a0f,0x2a80,0xc97d,0x13ed,0x28db,0xfbc4,0x589c,0xe30e,0xdf02,0x8010}, new char[]{0xe357,0x3db1,0xdf77,0x28fc,0x5a7d,0x34a7,0xe816,0xc91c,0x58f0,0x807c,0x44f9,0xd127,0x1388,0x2af4,0xfbe5}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x93cd,0x811f,0x05cb,0xfb1e,0x26fe,0x3a26,0xa05e,0x9398,0xd99d,0x1909,0x7c00,0x3295,0x60aa,0x5d21,0x9007,0x5dae,0x162f,0x9d29,0xfb43,0x96ef,0x9398,0xb814,0xb74a,0x59ad,0x1660}, new char[]{0x7c47,0x816a,0xfb77,0x05af,0x269b,0x32b5,0x5d42,0x3a49,0xfb2e,0x59dd,0x9d45,0x93fd,0x969b,0xb72f,0x5d8e,0xa073,0x1640,0x1647,0x60cb,0xd9eb,0x9062,0x1929,0xb872,0x93ed,0x93a3}).toString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8557,0xbba9,0x4b19,0x578c,0xb890,0x02a0,0xaea1,0x65c3,0xf9d8}, new char[]{0xaef6,0x65a6,0x4b75,0xbbca,0x02cf,0xb8fd,0xf9bd,0x57a0,0x8577}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc694,0x1243,0x7cd1,0x24c5,0x60e5,0xaaef,0xf02a,0x749b,0x3985,0xa320,0x1724,0xa62f,0x19ad,0xa61e,0xdb07,0xb88b,0x9401,0xf795,0xcda0,0xc2a6,0x0bfc,0x4f84,0xa6a7,0x8e77}, new char[]{0x60c5,0xa6ca,0x7cbe,0x4fe0,0x74ee,0xa672,0xb8ee,0x0b8f,0xa300,0xf00a,0x191a,0x9421,0xaacf,0x1776,0xcdc9,0x39e2,0xc2ce,0xf7e1,0xa60f,0x8e24,0xc6fc,0x122a,0x24a3,0xdb73}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c72}, new char[]{0x6c5d,0x289c,0xd82e,0xce85}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb068,0xe106,0x9a6c,0x26b6,0x442e,0x2f78,0x57c5}, new char[]{0x26f7,0x9a00,0xb004,0x57e5,0xe175,0x444b,0x2f0c}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc4ef,0xb67c,0x0305,0xc822,0xbfad,0xc847,0x1210,0x99d4,0x8fd6,0x7c74,0xba08,0x413e,0x6216,0xc4e8,0x3c59,0x0686}, new char[]{0xc802,0xba66,0x127a,0x7c1b,0xc85b,0x3c79,0x8fa3,0x6262,0x036c,0xc484,0x06ef,0xc49b,0xbfd4,0xb611,0x99bb,0x415a}).toString();
    }
}
