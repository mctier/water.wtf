package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.hud.HudElement;
import com.utilitymod.echo.hud.HudManager;
import com.utilitymod.gui.Waypoints;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows saved waypoints (Waypoints tab of the dropdown GUI) as markers in the world and as a
 * movable HUD list with distances.
 */
public class WaypointsModule extends Feature {
    private final BoolSetting inWorld = new BoolSetting(LT.L1, true);
    private final BoolSetting showList = new BoolSetting(LT.L2, true);
    private final FloatSetting maxDistance = new FloatSetting(LT.L3, 5000f, 50f, 30000f, 50f);
    private final BoolSetting addHere = new BoolSetting(LT.L4, false);

    public WaypointsModule() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b32,0x1c81,0x82d9,0x37d1,0xbc19,0xa8aa,0xb166,0x2261,0xd12d}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}).toString(), LT.L5,
                Category.RENDER));
        HudManager.register(new ListElement());
    }

    private List<Waypoints.Waypoint> here() {
        List<Waypoints.Waypoint> out = new ArrayList<>();
        String dim = Waypoints.dimension();
        for (Waypoints.Waypoint w : Waypoints.LIST) if (w.dimension().equals(dim)) out.add(w);
        return out;
    }

    private double distance(Waypoints.Waypoint w) {
        return mc.player == null ? 0 : Math.sqrt(mc.player.squaredDistanceTo(w.x() + 0.5, w.y() + 0.5, w.z() + 0.5));
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        if (addHere.getValue()) {
            addHere.setValue(false);
            Waypoints.addHere();
        }
        Render3D frame = EchoEvents.lastFrame();
        if (!inWorld.getValue() || frame == null || mc.player == null || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        for (Waypoints.Waypoint w : here()) {
            double d = distance(w);
            if (d > maxDistance.getValue()) continue;
            Vector4f clip = frame.toClip(w.x() + 0.5, w.y() + 1.2, w.z() + 0.5);
            if (clip.w() <= 0.05f) continue;
            float[] p = Render3D.toGui(clip);
            // diamond marker
            int c = w.color();
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(p[0], p[1]);
            m.rotate((float) Math.toRadians(45));
            Draw.rect(ctx, -4, -4, 8, 8, 2, Colors.withAlpha(0xFF000000, 0.45f));
            Draw.rect(ctx, -3, -3, 6, 6, 1, c);
            m.popMatrix();
            String label = w.name() + "  " + formatDistance(d);
            float tw = Text.width(label, 7f) + 8;
            Draw.rect(ctx, Math.round(p[0] - tw / 2), Math.round(p[1] - 19), Math.round(tw), 11, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, p[0], p[1] - 13.5f, 7f, 0xFFFFFFFF);
        }
    }

    private static String formatDistance(double d) {
        return d >= 1000 ? String.format(Locale.ROOT, LT.L6, d / 1000) : Math.round(d) + LT.L7;
    }

    private final class ListElement extends HudElement {
        ListElement() { super(LT.L8, LT.L9, 0.995f, 0.5f); }

        @Override public boolean active() { return isEnabled() && showList.getValue() && !here().isEmpty(); }
        @Override public float width() {
            float w = 70;
            for (Waypoints.Waypoint p : here()) w = Math.max(w, Text.width(p.name() + "  " + formatDistance(distance(p)), 7f) + 18);
            return w;
        }
        @Override public float height() { return 14 + Math.max(1, Math.min(8, here().size())) * 11; }
        @Override public float radius() { return 6; }

        @Override
        public void render(DrawContext ctx, boolean preview) {
            com.utilitymod.echo.render.Icons.drawItem(ctx, new net.minecraft.item.ItemStack(net.minecraft.item.Items.COMPASS), 5, 2.5f, 9);
            Text.drawCentered(ctx, LT.L9, 17, 7, 7.5f, Theme.TEXT);
            List<Waypoints.Waypoint> list = here();
            list.sort((a, b) -> Double.compare(distance(a), distance(b)));
            float y = 18;
            int i = 0;
            for (Waypoints.Waypoint p : list) {
                if (++i > 8) break;
                Draw.rect(ctx, 6, Math.round(y - 2), 4, 4, 2, p.color());
                Text.drawCentered(ctx, p.name(), 14, y, 7f, Theme.TEXT);
                String d = formatDistance(distance(p));
                Text.drawCentered(ctx, d, width() - 6 - Text.width(d, 7f), y, 7f, Theme.MUTED);
                y += 11;
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf358,0x2171,0xd5f3,0x8da0,0xb0f3,0x95bd,0xeee2,0xfff1,0xddc5,0x9986,0xeba3,0x6791,0x28d4}, new char[]{0x2887,0xee8a,0x95d2,0xd584,0xdde5,0xffb8,0xebcd,0x67b1,0xb0a4,0xf337,0x2103,0x99ea,0x8dc4}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x22d0,0x4603,0x5b88,0xfa00,0xcc82,0x5dbf,0x4e7a,0xb246}, new char[]{0xccca,0x4e2f,0x5bcc,0xfa20,0x5df3,0xb22f,0x4670,0x22a4}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x866a,0xe792,0xdf6c,0x083e,0x9b41,0x1d02,0xd09f,0x9801,0x38f4,0x768a,0xa5ba,0xee06}, new char[]{0x0873,0x76eb,0x1d7a,0x9b61,0x862e,0x389d,0xee75,0xe7e6,0x9860,0xd0f1,0xdf0f,0xa5df}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf236,0x6de7,0x65eb,0x7133,0x00b2,0x9b25,0x6f22,0xd51c,0x22a4,0x868f,0x7474,0xd646,0x862b,0x3ca4,0x70c1,0xbc1b,0xdc9a}, new char[]{0x866a,0x6f46,0x3cc0,0x9b05,0x00e5,0xdcfb,0x6d9e,0x86ff,0x715c,0xd62f,0x70af,0xf242,0xbc3b,0x22ec,0x658e,0xd56e,0x7411}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6616,0x546d,0xa84c,0x5431,0x7ffb,0xdcfd,0x99b4,0xee47,0x293a,0x0977,0x751d,0xf07f,0x9ce2,0x8430,0x1755,0x0016,0xb026,0x2dea,0x7d1d,0xe254,0xf0c7,0x5cab,0xa463,0x42c5,0xed64,0xe94b,0x9ae2,0xc6ed,0x9c9d,0x6521,0xec43,0x8070,0xbc04,0xc609,0xb9f9,0xfd8d,0x167b,0x73e6,0xbc2c,0x3a6a,0x689b,0xa264,0xb7a2,0x5535,0x7bde,0x2e6d,0x2571,0x82e4,0xb3eb,0xc74b,0xb667,0xf494,0xc6e6,0x7632,0x4a19,0x71fd,0x674a,0x7b07,0x16a0,0xdea1,0xe5e7,0xf967,0x9814,0x820f,0x6539,0x1027,0xa39e,0x936e,0x90b5,0xb68b,0x2707,0x1e81,0xea78,0x08ec,0x6544,0x5f7b,0x4112}, new char[]{0xed33,0x4a78,0xf0be,0x7b77,0xf010,0x5cc2,0xe589,0x931a,0x2727,0xb386,0xc687,0x7f89,0x8264,0xec26,0xbc76,0x6739,0x0957,0x0885,0x9cf3,0xb782,0xa417,0xb04e,0xa3fb,0x6564,0x541a,0x7bb1,0xfdff,0x007a,0xb6ef,0xc629,0xa24f,0x6519,0x7d7c,0xbc0c,0x428d,0xea2d,0x6565,0x9cc2,0x667a,0xb60e,0xf914,0x7189,0x5411,0xc6c5,0x801d,0x9a83,0x1615,0x8451,0x99d3,0xe92e,0x3a4a,0x7392,0x16c8,0xe231,0xdc90,0x291a,0x987d,0xb997,0x5f5b,0x1721,0xdec9,0x7657,0x1ea1,0xf4c3,0x4173,0xa835,0xc73b,0x90da,0x2d83,0x828a,0xee33,0x5546,0x68bb,0x2e19,0x1046,0x2513,0x7534}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc33b,0xe30a,0x3e73,0xfd92,0x5758,0x0ad3}, new char[]{0x0af6,0xc315,0x3e42,0xe36c,0x5733,0xfdff}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb3bc}, new char[]{0xb3d1,0x56cc,0x7221,0x2944}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x030e,0x3c3a,0xb44e,0xf4d5,0x2398,0x6bd1,0x1dda,0x57b2,0x3f64}, new char[]{0x3f13,0xb42f,0x6ba8,0x57c2,0x3c55,0x1db3,0x23f6,0xf4a1,0x037d}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9e35,0xbe4f,0x0e42,0x0116,0x5e56,0x9252,0xaf03,0xf855,0xc99e}, new char[]{0x0e15,0xc9ff,0xaf7a,0xbe3f,0x0179,0x9e5c,0x5e38,0xf821,0x9221}).toString();
    }
}
