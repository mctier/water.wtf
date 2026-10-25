package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.hud.HudElement;
import com.utilitymod.echo.hud.HudManager;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Potion effects as a movable HUD list with draining timer rings (replaces the vanilla icons). */
public class EffectsHud extends Feature {
    private final BoolSetting hideVanilla = new BoolSetting(LT.L1, true);
    /** First-seen duration per effect, so the ring drains from full. */
    private final Map<String, Integer> maxDuration = new HashMap<>();
    private static final String[] ROMAN = {"", "", LT.L2, LT.L3, LT.L4, LT.L5, LT.L6, LT.L7, LT.L8, LT.L9, LT.L10};

    public EffectsHud() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11d4,0xa8ad,0xbc0c,0x227e,0x1cba,0x828d,0x0b2f,0xd13f,0xb17b,0x9616,0x37dd}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString(), LT.L11, Category.RENDER));
        com.utilitymod.inject.Hooks.hideVanillaEffects = () -> isEnabled() && hideVanilla.getValue();
        HudManager.register(new Element());
    }

    private List<StatusEffectInstance> effects() {
        return mc.player == null ? List.of() : new ArrayList<>(mc.player.getStatusEffects());
    }

    private final class Element extends HudElement {
        Element() { super(LT.L12, LT.L13, 0.995f, 0.25f); }

        @Override public boolean active() { return isEnabled() && !effects().isEmpty(); }
        @Override public float width() { return 110; }
        @Override public float height() { return Math.max(1, effects().size()) * 20 + 4; }
        @Override public float radius() { return 6; }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            float y = 2;
            for (StatusEffectInstance e : effects()) {
                String key = e.getEffectType().getIdAsString();
                int dur = e.getDuration();
                int max = Math.max(dur, maxDuration.getOrDefault(key, dur));
                maxDuration.put(key, max);
                float left = e.isInfinite() ? 1f : dur / (float) Math.max(1, max);
                int color = e.getEffectType().value().getColor() | 0xFF000000;
                ring(ctx, 11, y + 9, 9, left, color);
                ctx.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.client.gui.hud.InGameHud.getEffectTexture(e.getEffectType()), 5, Math.round(y + 3), 12, 12);
                String name = e.getEffectType().value().getName().getString()
                        + (e.getAmplifier() + 1 < ROMAN.length ? ROMAN[e.getAmplifier() + 1] : " " + (e.getAmplifier() + 1));
                Text.drawCentered(ctx, Text.fit(name, 80, 7f), 24, y + 6, 7f, Theme.TEXT);
                String time = e.isInfinite() ? "∞" : String.format(LT.L14, dur / 1200, (dur / 20) % 60);
                Text.drawCentered(ctx, time, 24, y + 14, 6.3f, Theme.MUTED);
                y += 20;
            }
            if (!maxDuration.isEmpty() && effects().isEmpty()) maxDuration.clear();
        }

        /** Dotted ring that drains clockwise from the top. */
        private void ring(DrawContext ctx, float cx, float cy, float r, float left, int color) {
            int segs = 28;
            for (int i = 0; i < segs; i++) {
                float t = i / (float) segs;
                double a = -Math.PI / 2 + t * Math.PI * 2;
                float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
                boolean lit = t < left;
                Draw.rect(ctx, Math.round(px - 1), Math.round(py - 1), 2, 2, 1,
                        lit ? color : Colors.withAlpha(Theme.TRACK, 0.9f));
            }

        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0299,0x198b,0x206c,0x7c17,0xb5b1,0x69e0,0xa05f,0x3ad9,0x5b1d,0x93e1,0xd09d,0x3fc9,0x9283,0xef01,0xc67d,0xb4d6,0xa7e2,0x30b5}, new char[]{0x7c5f,0x02f0,0x3abd,0xef64,0x19ab,0xa009,0xd0fc,0xc613,0xb5d8,0x698c,0xa78e,0x9380,0x92a3,0xb49f,0x200f,0x3fa6,0x5b73,0x30c6}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda53,0xc86f,0x2495}, new char[]{0x24b5,0xc826,0xda1a,0x9b36}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x748e,0xd817,0xe16d,0xbb01}, new char[]{0xe14d,0xbb48,0xd85e,0x74c7}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf8c2,0x2d20,0x88b5}, new char[]{0x2d00,0xf88b,0x88e3,0xa2ba}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xada9,0x9585}, new char[]{0xad89,0x95d3,0xaf76,0xfd1d}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5fb5,0x44dc,0x4058}, new char[]{0x5f95,0x400e,0x4495,0x592d}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaed6,0xf9db,0xa592,0x9ba9}, new char[]{0xa5b2,0xf98d,0xae9f,0x9be0}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f7a,0xb330,0x3349,0x943f,0x2012}, new char[]{0x2032,0x9469,0xb379,0x3f33,0x3300}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3245,0x2975,0x85b4}, new char[]{0x8594,0x320c,0x292d,0x0c9b}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9832,0x70c4}, new char[]{0x9812,0x709c,0xad83,0x4f36}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x88bc,0x796b,0x5e31,0xa026,0x482b,0xb587,0x4ce1,0x4b03,0x41e5,0x4147,0x06b6,0x05d9,0xe780,0x2285,0xad15,0x6fc2,0x40f4,0xf322,0xa1f5,0xe9c6,0x58c5,0xb74a,0x409a,0x5612,0xabfd,0xc1bb,0x3f67,0x2da6,0xbc6c,0xda1b,0x9ee2,0xa21c,0xbb52,0xa097,0x645f,0xff1b,0x07bc,0xf63b,0x6555,0xa6f6,0x55dc,0xd327,0x289b,0x44df,0xdf36,0xba61,0x7091,0x58d8,0x87b4,0xfe9b,0x1ff7,0x4c82,0xde6f,0x7b5f,0x386a,0x45e0,0xbfb9,0x1df5,0x0f12,0x404b}, new char[]{0x4cb1,0xa699,0xd353,0x4ceb,0x5e5e,0xda75,0x45c0,0x1d90,0xbfdf,0x484d,0x7b3a,0x6536,0x88c8,0x4b70,0xa006,0x2dd1,0xa275,0xdf42,0xab95,0xff3b,0x1f83,0x58ac,0xc1d6,0x06d3,0xf350,0x40d4,0x22f7,0x412e,0xe7ee,0xb72d,0xbc1f,0x0f32,0x41cd,0xba11,0xa199,0x55bd,0x87d7,0x790e,0xe9e6,0x3803,0xa0e3,0x28bb,0xb5ee,0x567c,0x40ba,0xbb26,0x05b1,0x3f02,0x6fe2,0xde27,0xfece,0x589c,0xf61b,0x44ba,0x9e86,0x07d5,0x70e5,0x6430,0x4039,0xad3c}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xad3c,0xcb7c,0x6c3d,0xdf33,0xdd76,0x3a9c,0x4a2d}, new char[]{0x6c58,0xdd10,0xad5a,0x4a48,0xcb1f,0xdf47,0x3aef}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x512a,0x5863,0x3b4a,0x92ef,0x1849,0x387d,0x40be}, new char[]{0x516f,0x5805,0x9289,0x3b2f,0x182a,0x3809,0x40cd}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc9af,0xf686,0x14dd,0x74d0,0xe092,0x684f,0x9d4a}, new char[]{0x686a,0x14b9,0x9d70,0xe0b7,0xc99f,0xf6b4,0x74b4}).plainString();
    }
}
