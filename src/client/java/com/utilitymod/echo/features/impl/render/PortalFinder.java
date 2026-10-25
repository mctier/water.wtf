package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.gui.Waypoints;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.util.Colors;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds nether portals in loaded chunks and marks them. */
public class PortalFinder extends Feature {
    private final IntSetting range = new IntSetting(LT.L1, 12, 2, 32);
    private final BoolSetting notify = new BoolSetting(LT.L2, true);
    private final BoolSetting autoWaypoint = new BoolSetting(LT.L3, false);
    private final BoolSetting showMarker = new BoolSetting(LT.L4, true);

    private List<BlockPos> portals = List.of();
    private final Set<Long> announced = new HashSet<>();
    private final Set<Long> scanned = new HashSet<>();
    private int cooldown;

    public PortalFinder() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226a,0x962d,0x11f4,0xeb84,0x82eb,0x0b7c,0xa8a4,0xbc1d,0x1c9b,0xd12a,0x37df,0x6544,0xb163}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc5da,0x1813,0x335c,0x11f5,0x1c86,0x6516,0x3fbd,0x0b32,0xd92a,0xfcf7,0xa422,0x76bb,0x4a1e,0x7222,0xb98e,0x2a96,0xf896,0x508e,0x962b,0x37cd,0x5f64,0xbc0d,0xf057,0xcded,0x93e4,0x2276,0x8ae1,0xe732,0x04b5,0x9e43,0x7ede,0xeb93,0x6d76,0xa8a2,0x82c8,0xd13c,0xb12f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e}).toString(), Category.FINDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        portals = List.of();
        announced.clear();
        scanned.clear();
    }

    @Override
    public CharSequence concat() {
        return String.valueOf(portals.size());
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || --cooldown > 0) return;
        cooldown = 40;
        ChunkPos c = mc.player.getChunkPos();
        int r = range.getValue();
        List<BlockPos> found = new ArrayList<>();
        for (int cx = c.x - r; cx <= c.x + r; cx++) {
            for (int cz = c.z - r; cz <= c.z + r; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                ChunkSection[] secs = chunk.getSectionArray();
                int bottom = chunk.getBottomSectionCoord();
                for (int i = 0; i < secs.length; i++) {
                    ChunkSection s = secs[i];
                    if (s == null || s.isEmpty() || !s.hasAny(st -> st.isOf(Blocks.NETHER_PORTAL))) continue;
                    int by = (bottom + i) << 4;
                    BlockPos any = null;
                    for (int y = 0; y < 16 && any == null; y++)
                        for (int z = 0; z < 16 && any == null; z++)
                            for (int x = 0; x < 16; x++)
                                if (s.getBlockState(x, y, z).isOf(Blocks.NETHER_PORTAL)) {
                                    any = new BlockPos((cx << 4) + x, by + y, (cz << 4) + z);
                                    break;
                                }
                    if (any != null) found.add(any);
                }
            }
        }
        portals = found;
        for (BlockPos p : found) {
            long key = ChunkPos.toLong(p.getX() >> 4, p.getZ() >> 4);
            if (!announced.add(key)) continue;
            if (notify.getValue()) Notifications.push(LT.L5, LT.L6 + p.toShortString(), Notifications.Type.INFO);
            if (autoWaypoint.getValue()) Waypoints.add(LT.L7, p.getX(), p.getY(), p.getZ());
        }
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (!showMarker.getValue() || frame == null || mc.player == null || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        for (BlockPos p : portals) {
            Vector4f clip = frame.toClip(p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5);
            if (clip.w() <= 0.05f) continue;
            float[] g = Render3D.toGui(clip);
            int col = 0xFFB060FF;
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(g[0], g[1]);
            m.rotate((float) Math.toRadians(45));
            Draw.rect(ctx, -4, -4, 8, 8, 2, Colors.withAlpha(0xFF000000, 0.45f));
            Draw.rect(ctx, -3, -3, 6, 6, 1, col);
            m.popMatrix();
            double d = Math.sqrt(mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
            String label = LT.L8 + Math.round(d) + LT.L9;
            float tw = Text.width(label, 7f) + 8;
            Draw.rect(ctx, Math.round(g[0] - tw / 2), Math.round(g[1] - 19), Math.round(tw), 11, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, g[0], g[1] - 13.5f, 7f, col);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x30f5,0xbce1,0x25e9,0x17d0,0xa3e1,0xa471,0x5c83,0xd88f,0x73ea,0x2824,0x0196,0x32ff,0x9b34,0x2544}, new char[]{0x2876,0x2588,0xbc8f,0x17b7,0xa384,0x32df,0x9b1c,0x5ce0,0x252c,0xd8fa,0x7384,0xa41a,0x3086,0x01bf}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb6f3,0x9be7,0x2b44,0xb675,0x464b,0xeccf}, new char[]{0x2b0a,0xeca0,0xb601,0x4622,0x9b81,0xb68a}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x33b4,0x4ccd,0x0d02,0x0f79,0xae46,0x2da2,0xf822,0x853d,0xa415,0x510e,0x09d7,0xdba1,0xef1f}, new char[]{0xef5e,0xf857,0x33c0,0x0d6d,0x851d,0x0f2e,0x2dc3,0xdbd8,0x517e,0x09b8,0x4ca4,0xae28,0xa461}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8fc3,0x7e2c,0x87d6,0xf65b,0xd79a,0x8afd,0x6c4c,0xa469,0x08b2,0x61c3,0x52ba}, new char[]{0x8785,0xf633,0xd7f5,0x8fb4,0x8add,0x6c01,0x7e4d,0x52c8,0x08d9,0x61a6,0xa41b}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf6aa,0x0268,0x03a5,0xcbfb,0xa408,0xab46,0xcd18,0xbefe,0x4265,0xc9d7,0xbbbb,0x5284,0xa389}, new char[]{0x03f5,0xa467,0x021a,0xbe8a,0xbbda,0x52e8,0xa3a9,0xf6ec,0xc9be,0xab28,0xcb9f,0x4200,0xcd6a}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6928,0x6960,0x9dbc,0x5b65,0x05a0,0x8bd3,0x2815,0x6272,0x2919,0x0f6b,0x5560,0xdeec,0xccef,0x7be9,0x2f03,0xd3fc,0x3ff3}, new char[]{0x0f25,0xcc8a,0x296d,0x6940,0x9dd9,0x7b9b,0xd3dc,0x05d0,0x2f6c,0x5b17,0x6206,0x8bb2,0x2879,0x5540,0x3f92,0xde98,0x6940}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xab33,0x9036,0x8e20,0x3da5,0x2ecb,0x9563}, new char[]{0xab63,0x8e4f,0x3dd7,0x2ebf,0x9057,0x950f}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4947,0xd158,0x832e,0x3671,0xd460,0x3034,0x6353,0x1086}, new char[]{0x4917,0x361e,0xd12a,0x6327,0xd401,0x10ea,0x3014,0x830e}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ae3}, new char[]{0x1a8e,0xa99b,0xbfc2,0xcccd}).plainString();
    }
}
