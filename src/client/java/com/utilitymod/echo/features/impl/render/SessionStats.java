package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.hud.HudElement;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.echo.utils.combat.KillTracker;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** Kills, deaths, K/D, distance walked and playtime for this session, in a small HUD card. */
public class SessionStats extends Feature {
    private final BoolSetting playersOnly = new BoolSetting(LT.L1, true);
    private final BoolSetting resetOnLeave = new BoolSetting(LT.L2, false);

    private int kills, deaths;
    private double walked;
    private long startedAt;
    private double lastX = Double.NaN, lastZ;
    private boolean wasDead;

    public SessionStats() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226b,0x9637,0x11f1,0xeb95,0x828d,0x0b32,0xa8ae,0xbc1a,0x1ca1,0xd129,0x37d7,0x6545,0xb160}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString(), LT.L3, Category.RENDER));
        KillTracker.listen(e -> {
            if (!playersOnly.getValue() || e instanceof net.minecraft.entity.player.PlayerEntity) kills++;
        });
        HudManager.register(new Element());
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (mc.player == null) {
            if (resetOnLeave.getValue()) {
                kills = deaths = 0;
                walked = 0;
                startedAt = 0;
            }
            lastX = Double.NaN;
            return;
        }
        if (startedAt == 0) startedAt = System.currentTimeMillis();
        boolean dead = mc.player.isDead();
        if (dead && !wasDead) deaths++;
        wasDead = dead;
        if (!Double.isNaN(lastX)) {
            double d = Math.hypot(mc.player.getX() - lastX, mc.player.getZ() - lastZ);
            if (d < 10) walked += d; // ignore teleports
        }
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
    }

    private final class Element extends HudElement {
        Element() { super(LT.L4, LT.L5, 0.005f, 0.4f); }

        @Override public boolean active() { return isEnabled() && mc.player != null; }
        @Override public float width() { return 104; }
        @Override public float height() { return 56; }
        @Override public float radius() { return 7; }

        private void row(DrawContext ctx, net.minecraft.item.Item icon, String k, String v, float y, int color) {
            com.utilitymod.echo.render.Icons.drawItem(ctx, new net.minecraft.item.ItemStack(icon), 7, y - 4, 8);
            Text.drawCentered(ctx, k, 18, y, 6.8f, Theme.MUTED);
            Text.drawCentered(ctx, v, width() - 8 - Text.width(v, 7f), y, 7f, color);
        }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            com.utilitymod.echo.render.Icons.drawItem(ctx, new net.minecraft.item.ItemStack(net.minecraft.item.Items.CLOCK), 6, 3.5f, 9);
            Text.drawCentered(ctx, LT.L6, 18, 8, 7.5f, Theme.TEXT);
            long secs = startedAt == 0 ? 0 : (System.currentTimeMillis() - startedAt) / 1000;
            String time = String.format(Locale.ROOT, LT.L7, secs / 3600, secs / 60 % 60, secs % 60);
            Text.drawCentered(ctx, time, width() - 8 - Text.width(time, 6.5f), 8, 6.5f, Colors.withAlpha(Theme.ACCENT, 0.9f));
            String kd = deaths == 0 ? String.valueOf(kills) : String.format(Locale.ROOT, LT.L8, kills / (float) deaths);
            row(ctx, net.minecraft.item.Items.DIAMOND_SWORD, LT.L9, String.valueOf(kills), 20, 0xFF4ADE80);
            row(ctx, net.minecraft.item.Items.SKELETON_SKULL, LT.L10, String.valueOf(deaths), 29, 0xFFF87171);
            row(ctx, net.minecraft.item.Items.GOLDEN_SWORD, LT.L11, kd, 38, Theme.TEXT);
            String dist = walked >= 1000 ? String.format(Locale.ROOT, LT.L12, walked / 1000) : Math.round(walked) + LT.L13;
            row(ctx, net.minecraft.item.Items.LEATHER_BOOTS, LT.L14, dist, 47, Theme.TEXT);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3fff,0xbe9b,0x4d16,0x9a64,0x9e6b,0xa68a,0xef07,0xc1d0,0xfecf,0x61cf,0xef07,0x0c96}, new char[]{0x9a34,0xef6b,0x61ae,0xfeb6,0x4d73,0x9e19,0xc1a3,0xef27,0x0cd9,0xa6e4,0xbef7,0x3f86}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0393,0x460c,0x6fdd,0xd852,0xec2e,0xc21c,0x5c6c,0xd4bc,0x63ac,0x3171,0x37a7,0x6869,0xb9e1,0x1660}, new char[]{0x03c1,0x6fb8,0x37d4,0xc279,0x5c18,0xd872,0x6826,0x160e,0xd49c,0xb9ad,0x63c9,0x3110,0xec58,0x4669}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb45f,0x8c92,0x9888,0x9c1d,0xb08c,0x115c,0x5f24,0xfacb,0x1c2c,0x0afa,0x0108,0xa02b,0x6f06,0x02fc,0x55fc,0x11da,0x64fc,0x4347,0x4a14,0x5717,0x399c,0x2682,0x3cb6,0xaa22,0xe830,0xb5f0,0xf20c,0xa933,0xc73c,0xa8b0,0xef3e,0x262e,0x0b2e,0xc081,0x9ef7,0x2ca6,0xe6f5,0xe8b3,0x1257,0x8969,0xaddf,0xc0ad,0xafd0,0xdde3,0x50c1,0x9ada,0xe911,0x7cb8,0x7618,0xee15,0x58c6,0xe396,0x02a7,0x3800,0x1ad7,0x8783,0xd206,0xcc5a,0xf31a,0x579e,0x27ca,0xc670,0x4b0b,0x105e,0x8838,0x9d0f,0xf430,0x40be,0x3a03,0x33a0}, new char[]{0x8cd9,0xf459,0xc0ed,0xc750,0x9e84,0x3c9a,0xa890,0x33c4,0x1ab2,0xe851,0x11ae,0xb437,0x87f0,0x028b,0x5737,0x7cf3,0x382f,0x2ce2,0x8945,0x9d2f,0xa04f,0xadb6,0x57ed,0xcc2e,0x40df,0x1c42,0x6f65,0x27af,0x9c3d,0x0a9b,0xf262,0x98ec,0x0b0e,0xaa52,0x1130,0x50a0,0x26fb,0x39e8,0xe69c,0x6491,0xee70,0x8818,0x4a3c,0xb0fc,0xfaa7,0xf37b,0xafb3,0x4b6e,0x5f04,0x5595,0xb584,0xe3b6,0x123e,0xa95d,0xe931,0x58b2,0x9ab2,0x3a66,0x260e,0xe8fb,0xddb6,0xc634,0xef1e,0x767d,0x103a,0x0295,0x4333,0x0167,0xc0df,0xd22f}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xedb7,0x81f5,0xd5cc,0xb1b3,0x5239,0x846f,0xeb13,0x65d1,0xcdac,0xd4ef,0x3820,0x9545}, new char[]{0xd49c,0xb1d6,0xedc4,0x9536,0x819c,0x8400,0xeb7d,0xd5bf,0xcdd8,0x3841,0x65a5,0x524a}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1768,0x480e,0xddc4,0xabc8,0x68d8,0x3b75,0x8dea,0x857d,0x24a0,0xca80,0x8af8,0xf5fd,0xbefd}, new char[]{0xab9b,0x3b10,0xcaf3,0xddb7,0x8514,0x4861,0x68b6,0x1748,0x8aab,0x24d4,0xbe9c,0x8d9e,0xf58e}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1335,0x1ad8,0xfd8b,0xc56b,0x0c09,0x615e,0x2b2c}, new char[]{0xfdd8,0x2b49,0x0c7a,0x612d,0x135c,0x1ab7,0xc505}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f57,0x6688,0x09c5,0x6a92,0x47de,0x86d8,0x372f,0xde17,0xb447,0xa83d,0xd353,0xe51a}, new char[]{0x09e0,0x66ec,0x3715,0x47fb,0xde27,0x0f65,0xd337,0x6aa8,0xe53f,0xa80d,0xb475,0x86bc}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x67fd,0x2112,0x772f,0x4837}, new char[]{0x4812,0x67d3,0x771d,0x2174}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf767,0xb195,0xb047,0x2e6f,0xf527}, new char[]{0xb00c,0xf54e,0x2e03,0xb1f9,0xf714}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xffc4,0xfa79,0x5aaa,0x9a33,0x9782,0x1873}, new char[]{0x1837,0x97e7,0x5acb,0xffb0,0xfa11,0x9a40}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7cc9,0x7b7e,0xc87c}, new char[]{0x7b35,0xc853,0x7c8d,0x8bb0}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1eff,0xcd96,0x5018,0x2733,0x5945,0x4a79,0x49c8}, new char[]{0x5960,0x271d,0x4a4b,0x49ae,0x5038,0xcdfd,0x1e92}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x93c7,0x5a12}, new char[]{0x5a32,0x93aa,0xb8fe,0x423a}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x195b,0x4d18,0x51d1,0x67f6,0xad11,0xf948}, new char[]{0x67a1,0xf929,0x1937,0x4d73,0x51b4,0xad75}).toString();
    }
}
