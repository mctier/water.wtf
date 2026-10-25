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
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SculkShriekerBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds ancient cities in loaded chunks. Reinforced deepslate only generates in the city's portal
 * frame, so one chunk with it is proof; otherwise a cluster of deep sculk shriekers next to
 * deepslate-tile / soul-lantern building blocks counts. Deep chunks are scanned a few per tick.
 */
public class AncientCityFinder extends Feature {
    private final IntSetting range = new IntSetting(LT.L1, 12, 2, 32);
    private final BoolSetting notify = new BoolSetting(LT.L2, true);
    private final BoolSetting autoWaypoint = new BoolSetting(LT.L3, false);
    private final BoolSetting showMarker = new BoolSetting(LT.L4, true);

    private static final Set<Block> CITY_BLOCKS = Set.of(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES,
            Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL, Blocks.SOUL_LANTERN,
            Blocks.CHISELED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE);

    /** Per chunk: reinforced deepslate position (or null), shrieker count, city-block evidence. */
    private record ChunkInfo(BlockPos frame, int shriekers, boolean cityBlocks, BlockPos any) {}

    private record City(BlockPos center, boolean confirmed, int shriekers) {}

    private final Map<Long, ChunkInfo> info = new HashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private final Set<Long> announced = new HashSet<>();
    private List<City> cities = List.of();
    private int cluster;

    public AncientCityFinder() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf04b,0xc5de,0xb161,0xbc00,0x828d,0x37db,0x4a0e,0x0b28,0x76bd,0x227b,0x11e4,0x5f7a,0x1cb1,0x962a,0xeb98,0xa8a5,0xd13b,0x6516,0xfcc1}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08}).toString(), LT.L5, Category.FINDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        info.clear();
        scannedAt.clear();
        announced.clear();
        cities = List.of();
    }

    @Override
    public CharSequence concat() {
        return String.valueOf(cities.size());
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        if (!mc.world.getDimension().hasSkyLight()) return; // overworld only (cities are below y=0)
        ChunkPos c = mc.player.getChunkPos();
        int r = range.getValue();
        long now = System.currentTimeMillis();
        info.keySet().removeIf(k -> Math.abs(ChunkPos.getPackedX(k) - c.x) > r || Math.abs(ChunkPos.getPackedZ(k) - c.z) > r);
        scannedAt.keySet().retainAll(info.keySet());

        int budget = 3;
        outer:
        for (int ring = 0; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    long key = ChunkPos.toLong(c.x + dx, c.z + dz);
                    Long at = scannedAt.get(key);
                    if (at != null && now - at < 10_000) continue;
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(c.x + dx, c.z + dz);
                    if (chunk == null) continue;
                    info.put(key, scan(chunk));
                    scannedAt.put(key, now);
                    if (--budget <= 0) break outer;
                }
            }
        }
        if (++cluster % 20 == 0) rebuild();
    }

    private ChunkInfo scan(WorldChunk chunk) {
        int shriekers = 0;
        BlockPos any = null;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof SculkShriekerBlockEntity && be.getPos().getY() < 0) {
                shriekers++;
                any = be.getPos();
            }
        }
        BlockPos frame = null;
        boolean cityBlocks = false;
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        for (int i = 0; i < sections.length; i++) {
            int baseY = (bottom + i) << 4;
            if (baseY >= 0) break; // cities generate around y=-51
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty()) continue;
            if (!cityBlocks && s.hasAny(st -> CITY_BLOCKS.contains(st.getBlock()))) cityBlocks = true;
            if (frame == null && s.hasAny(st -> st.isOf(Blocks.REINFORCED_DEEPSLATE))) {
                search:
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (s.getBlockState(x, y, z).isOf(Blocks.REINFORCED_DEEPSLATE)) {
                                frame = new BlockPos(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z);
                                break search;
                            }
                        }
                    }
                }
            }
        }
        return new ChunkInfo(frame, shriekers, cityBlocks, frame != null ? frame : any);
    }

    /** Groups evidence chunks within 6 chunks of each other into cities. */
    private void rebuild() {
        List<long[]> groups = new ArrayList<>(); // [sumX, sumZ, count, shriekers, confirmed, fx, fy, fz, anyY]
        for (var e : info.entrySet()) {
            ChunkInfo ci = e.getValue();
            boolean evidence = ci.frame() != null || (ci.shriekers() > 0 && ci.cityBlocks());
            if (!evidence) continue;
            int cx = ChunkPos.getPackedX(e.getKey()), cz = ChunkPos.getPackedZ(e.getKey());
            long[] g = null;
            for (long[] o : groups) {
                if (Math.abs(o[0] / o[2] - cx) <= 6 && Math.abs(o[1] / o[2] - cz) <= 6) { g = o; break; }
            }
            if (g == null) groups.add(g = new long[9]);
            g[0] += cx; g[1] += cz; g[2]++; g[3] += ci.shriekers();
            if (ci.frame() != null && g[4] == 0) {
                g[4] = 1; g[5] = ci.frame().getX(); g[6] = ci.frame().getY(); g[7] = ci.frame().getZ();
            }
            if (ci.any() != null) g[8] = ci.any().getY();
        }
        List<City> out = new ArrayList<>();
        for (long[] g : groups) {
            boolean confirmed = g[4] == 1;
            if (!confirmed && g[3] < 3) continue; // a lone shrieker is just the deep dark
            BlockPos center = confirmed ? new BlockPos((int) g[5], (int) g[6], (int) g[7])
                    : new BlockPos((int) (g[0] / g[2] * 16 + 8), (int) g[8], (int) (g[1] / g[2] * 16 + 8));
            out.add(new City(center, confirmed, (int) g[3]));
        }
        cities = out;
        for (City city : out) {
            boolean seen = false;
            for (long a : announced) {
                if (Math.abs(ChunkPos.getPackedX(a) - (city.center().getX() >> 4)) <= 8
                        && Math.abs(ChunkPos.getPackedZ(a) - (city.center().getZ() >> 4)) <= 8) { seen = true; break; }
            }
            if (seen) continue;
            announced.add(ChunkPos.toLong(city.center().getX() >> 4, city.center().getZ() >> 4));
            String where = city.center().getX() + LT.L6 + city.center().getY() + LT.L6 + city.center().getZ();
            if (notify.getValue()) Notifications.push(LT.L7,
                    (city.confirmed() ? LT.L8 : LT.L9) + where, Notifications.Type.INFO);
            if (autoWaypoint.getValue()) Waypoints.add(LT.L10, city.center().getX(), city.center().getY(), city.center().getZ());
        }
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (!showMarker.getValue() || frame == null || mc.player == null || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        for (City c : cities) {
            Vector4f clip = frame.toClip(c.center().getX() + 0.5, c.center().getY() + 4, c.center().getZ() + 0.5);
            if (clip.w() <= 0.05f) continue;
            float[] p = Render3D.toGui(clip);
            int col = c.confirmed() ? 0xFF35D0C8 : 0xFF6A8CA8;
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(p[0], p[1]);
            m.rotate((float) Math.toRadians(45));
            Draw.rect(ctx, -5, -5, 10, 10, 2, Colors.withAlpha(0xFF000000, 0.45f));
            Draw.rect(ctx, -4, -4, 8, 8, 1, col);
            m.popMatrix();
            double d = Math.sqrt(mc.player.squaredDistanceTo(c.center().getX() + 0.5, c.center().getY(), c.center().getZ() + 0.5));
            String label = (c.confirmed() ? LT.L11 : LT.L12) + Math.round(d) + LT.L13;
            String sub = c.shriekers() + LT.L14 + c.center().getY();
            float tw = Math.max(Text.width(label, 7f), Text.width(sub, 6f)) + 10;
            Draw.rect(ctx, Math.round(p[0] - tw / 2), Math.round(p[1] - 29), Math.round(tw), 19, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, p[0], p[1] - 23.5f, 7f, col);
            Text.drawCenteredX(ctx, sub, p[0], p[1] - 14.5f, 6f, 0xFFCCCCCC);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7d08,0x8375,0xaa44,0xad6e,0x1423,0x6efe,0x40bc,0xcbba,0xd7a1,0x5da1,0xc27e,0xbc3c,0x4f4e,0xa6e9}, new char[]{0x7d5a,0x8314,0x5dcf,0xc219,0x1446,0x6ede,0xa6c1,0xd7c2,0xbc54,0x40c9,0x4f20,0xad05,0xaa37,0xcb93}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2efb,0xaae5,0xf852,0xe92f,0x134b,0xcbcc}, new char[]{0x1305,0xf83d,0xe95b,0xaa8c,0xcbaa,0x2e82}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd418,0x851c,0xb542,0xd60b,0x6abb,0x74f2,0x5b69,0x1897,0x1ed1,0x2e24,0x9ba6,0xac28,0x3d61}, new char[]{0x1e90,0x2e51,0xb536,0xd664,0x3d41,0x9bf1,0xac49,0x8565,0x18e7,0x5b06,0x749b,0xd476,0x6acf}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xca67,0x7993,0xf2bc,0x93d9,0xc81e,0x9592,0x9143,0xd3ed,0xcbc5,0x0ee2,0x85ba}, new char[]{0xd3be,0xf2d4,0xcbaa,0x95e5,0x859a,0xca2a,0x9122,0xc86c,0x0e89,0x93bc,0x79e1}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x56e9,0xf1d2,0x350c,0xa97f,0x66c3,0x1c06,0xc9a4,0x60c5,0x8a00,0xa54f,0x5b93,0x0352,0x4f38,0xc4e1,0x8c02,0x141f,0x187a,0xae32,0x03f0,0x61f7,0x2148,0x9647,0xce8d,0x2cb1,0x79c8,0x1444,0x0fca,0xd0b3,0x01d5,0xa44e,0x3204,0x73ee,0x4d56,0x4dc9,0x7045,0x9415,0xcf89,0xb3bf,0x4d7d,0x8a9e,0x0668,0x7f0b,0x434d,0x00db,0xb1be,0x4dba,0x7523,0x1c41,0xe7ba,0x0568,0x842e,0x06b5,0xff19,0x685e,0xc6f8,0x8b56,0x7243,0xad5a,0x33ca,0x9d91,0xa11a,0xbe38,0xc1a3,0x13e3,0x82f3,0x5070,0x11fb,0x8ddc,0x261e,0x1ce8,0x8d95}, new char[]{0xd0f5,0x1c6f,0x66ad,0x9df5,0x7036,0x00fb,0x5011,0xf1bc,0x8c61,0x06dc,0x0337,0xc1cd,0x061c,0x687e,0x432e,0xc488,0xb1ca,0x8af7,0x212d,0x051b,0x82d3,0x4d92,0x4dbb,0x7f6e,0x142d,0x60ab,0x2cd7,0x7381,0x8dae,0x9624,0xa52a,0x7227,0x56c9,0x1c25,0xc9c1,0x0faf,0x1393,0x4f4b,0xb3d3,0x9474,0x01a1,0x8a65,0x4d51,0x5bb3,0x8b25,0x33a2,0xa168,0x1476,0x6192,0xad31,0xe7df,0x8de7,0x7550,0xff30,0x263e,0xcfe0,0x1c86,0x79e8,0xbe54,0x3563,0xceec,0xa91b,0x0395,0x181e,0xc6d8,0x844d,0x326c,0x118e,0xa420,0xae59,0x4d25}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc3d7,0x9e26}, new char[]{0xc3fb,0x9e06,0x68c2,0x4cb6}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b17,0x1129,0xabf0,0xc928,0x98d4,0xd47e,0x5434,0x25a6,0xcb1e,0xbaf2,0x7b54,0x8ee3,0xb76e,0x5761,0xdbb0,0xbaef,0x2633,0xd885,0xab73}, new char[]{0x9895,0x1b79,0x5457,0xd8ec,0xab16,0xba9c,0x2647,0x2586,0xabb3,0x7b3d,0x8e97,0xd407,0x1109,0xb728,0xba86,0xcb70,0x5705,0xdbd5,0xc95a}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x589a,0xff66,0xf513,0x7e12,0x381f,0xfcf9,0x1b87,0xbe4d,0x5cc6,0x9ad9,0xcba0,0xfd26,0x9be8,0xb9ef,0xacd8,0x87f0}, new char[]{0x87b1,0x1be9,0x5ca5,0xfd4f,0xbe28,0xcbce,0xff12,0xb9cf,0x7e71,0x58f3,0x386b,0xf56a,0x9af9,0xfc98,0x9b9c,0xacf8}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x639a,0x5af7,0x573e,0x3daa,0x7b3b,0xc202,0x7131,0x8313,0x8c7f,0x4b4c,0x2b9b,0x7e04,0xd3dd,0xcd72,0x4c8d,0x0cca,0x502c,0xc69b,0xdc88,0xb97f,0xc4ab,0xa9ed,0x10e2,0xd7eb,0x2838}, new char[]{0xd7bb,0x3dc5,0x63e9,0x574d,0xb916,0x7153,0xdce4,0x1087,0x5ad7,0x2bfa,0x2856,0x8c1c,0xa984,0x4b29,0x7e6a,0xcd06,0x500c,0x0ca9,0xc6f2,0xc4df,0x4cf4,0x7b1b,0xd3bc,0x8367,0xc222}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1e9f,0xc327,0x9dd8,0x059e,0x3238,0x8573,0x622b,0x3748,0x7e1e,0xcee9,0x50a2,0x1919}, new char[]{0x1ede,0x7e70,0x9dbb,0xc34e,0x197c,0x3256,0xce9d,0x620b,0x8530,0x05f7,0x373c,0x50db}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8b9a,0x43a6,0xcf72,0xe091,0xbe0b,0xb0b8,0x833d,0x0881,0x05ae,0xa134,0x5ee9,0x42b9,0xf979,0xb651}, new char[]{0x05ef,0x8353,0xa157,0xb0d1,0x08e4,0x42d7,0x8bee,0xcf52,0xf93a,0x5e80,0xe0e5,0xb628,0xbe2b,0x4386}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x353a,0x2f56,0x9c3c,0xb657,0x0d59,0xc0a7,0x93db,0xd78a,0x6b8a,0xe5d2,0x9ab4,0x8653,0x8a03,0xcdb8,0x3d10}, new char[]{0x3d51,0x0d37,0x3559,0x93b2,0x8636,0xe5bc,0x9ac0,0xd7aa,0x2f15,0xcdd1,0xc0d3,0xb62e,0x6bb5,0x8a23,0x9c1c}).toString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf9ff}, new char[]{0xf992,0x0c8a,0x8316,0x5d06}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ad9,0x3d96,0x95ba,0x6292,0xfa97,0x3a87,0x2abc,0xc134,0x3ed3,0x5da1,0xd167,0x30cf,0x2c4d}, new char[]{0xfab7,0xc147,0x62fa,0x3de4,0x30a6,0x5dc4,0x3eb8,0x95df,0x2ace,0x3af4,0xd147,0x2c6d,0x2aa0}).plainString();
    }
}
