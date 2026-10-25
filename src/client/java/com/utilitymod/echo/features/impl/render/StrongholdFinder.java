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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
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
 * Finds strongholds in loaded chunks. An End-portal frame is proof on its own; otherwise a chunk
 * packed with stone bricks plus stronghold trimmings (iron bars, bookshelves, a silverfish spawner)
 * counts. Overworld only; deep chunks are scanned a few per tick.
 */
public class StrongholdFinder extends Feature {
    private static final Set<Block> BRICKS = Set.of(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.INFESTED_STONE_BRICKS,
            Blocks.INFESTED_MOSSY_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS);

    private final IntSetting range = new IntSetting(LT.L1, 10, 2, 24);
    private final BoolSetting notify = new BoolSetting(LT.L2, true);
    private final BoolSetting autoWaypoint = new BoolSetting(LT.L3, false);
    private final BoolSetting showMarker = new BoolSetting(LT.L4, true);

    private record Hold(BlockPos center, boolean confirmed, int bricks) {}

    private final Map<Long, Hold> hits = new HashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private final Set<Long> announced = new HashSet<>();
    private List<Hold> holds = List.of();
    private int ticks;

    public StrongholdFinder() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a18,0x76b0,0xf040,0xb168,0x226a,0xd129,0x0b34,0xeba7,0x82c2,0x1c9e,0x9627,0x11b0,0xa8bf,0x37d0,0xfce9,0x655f,0xbc06}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a}).toString(), LT.L5, Category.FINDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        hits.clear();
        scannedAt.clear();
        announced.clear();
        holds = List.of();
    }

    @Override
    public CharSequence concat() {
        return String.valueOf(holds.size());
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.world.getRegistryKey() != World.OVERWORLD) return;
        ChunkPos c = mc.player.getChunkPos();
        int r = range.getValue();
        long now = System.currentTimeMillis();
        hits.keySet().removeIf(k -> Math.abs(ChunkPos.getPackedX(k) - c.x) > r || Math.abs(ChunkPos.getPackedZ(k) - c.z) > r);
        scannedAt.keySet().retainAll(hits.keySet());

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
                    Hold h = scan(chunk);
                    if (h != null) hits.put(key, h); else hits.remove(key);
                    scannedAt.put(key, now);
                    if (--budget <= 0) break outer;
                }
            }
        }
        if (++ticks % 20 == 0) holds = new ArrayList<>(hits.values());

        for (Hold h : hits.values()) {
            long ck = ChunkPos.toLong(h.center().getX() >> 4, h.center().getZ() >> 4);
            boolean near = false;
            for (long a : announced)
                if (Math.abs(ChunkPos.getPackedX(a) - (h.center().getX() >> 4)) <= 4
                        && Math.abs(ChunkPos.getPackedZ(a) - (h.center().getZ() >> 4)) <= 4) { near = true; break; }
            if (near) continue;
            announced.add(ck);
            if (notify.getValue()) Notifications.push(LT.L6,
                    (h.confirmed() ? LT.L7 : LT.L8) + h.center().toShortString(), Notifications.Type.INFO);
            if (autoWaypoint.getValue()) Waypoints.add(LT.L9, h.center().getX(), h.center().getY(), h.center().getZ());
        }
    }

    private Hold scan(WorldChunk chunk) {
        ChunkSection[] secs = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        int bricks = 0, bars = 0, shelves = 0, frames = 0, spawners = 0;
        BlockPos frame = null, any = null;
        long sy = 0;
        int n = 0;
        for (int i = 0; i < secs.length; i++) {
            ChunkSection s = secs[i];
            if (s == null || s.isEmpty()) continue;
            int baseY = (bottom + i) << 4;
            if (baseY > 80) continue; // strongholds are underground
            boolean maybe = s.hasAny(st -> BRICKS.contains(st.getBlock()) || st.isOf(Blocks.END_PORTAL_FRAME)
                    || st.isOf(Blocks.IRON_BARS) || st.isOf(Blocks.BOOKSHELF) || st.isOf(Blocks.SPAWNER));
            if (!maybe) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                Block b = s.getBlockState(x, y, z).getBlock();
                BlockPos p = null;
                if (BRICKS.contains(b)) { bricks++; p = new BlockPos(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z); }
                else if (b == Blocks.END_PORTAL_FRAME) { frames++; frame = new BlockPos(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z); }
                else if (b == Blocks.IRON_BARS) bars++;
                else if (b == Blocks.BOOKSHELF) shelves++;
                else if (b == Blocks.SPAWNER) spawners++;
                if (p != null) { sy += p.getY(); n++; any = p; }
            }
        }
        boolean confirmed = frames > 0;
        boolean possible = bricks >= 80 || (bricks >= 24 && (bars > 0 || shelves > 0 || spawners > 0));
        if (!confirmed && !possible) return null;
        BlockPos center = frame != null ? frame
                : new BlockPos(chunk.getPos().getStartX() + 8, n > 0 ? (int) (sy / n) : mc.player.getBlockY(), chunk.getPos().getStartZ() + 8);
        if (any != null && frame == null) center = any;
        return new Hold(center, confirmed, bricks);
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (!showMarker.getValue() || frame == null || mc.player == null || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        for (Hold h : holds) {
            Vector4f clip = frame.toClip(h.center().getX() + 0.5, h.center().getY() + 2, h.center().getZ() + 0.5);
            if (clip.w() <= 0.05f) continue;
            float[] g = Render3D.toGui(clip);
            int col = h.confirmed() ? 0xFF8CC2FF : 0xFF6A8CA8;
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(g[0], g[1]);
            m.rotate((float) Math.toRadians(45));
            Draw.rect(ctx, -5, -5, 10, 10, 2, Colors.withAlpha(0xFF000000, 0.45f));
            Draw.rect(ctx, -4, -4, 8, 8, 1, col);
            m.popMatrix();
            double d = Math.sqrt(mc.player.squaredDistanceTo(h.center().getX() + 0.5, h.center().getY(), h.center().getZ() + 0.5));
            String label = (h.confirmed() ? LT.L10 : LT.L11) + Math.round(d) + LT.L12;
            float tw = Text.width(label, 7f) + 10;
            Draw.rect(ctx, Math.round(g[0] - tw / 2), Math.round(g[1] - 19), Math.round(tw), 11, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, g[0], g[1] - 13.5f, 7f, col);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x54a4,0x6cd3,0x6802,0xb692,0xf413,0x52cd,0xcd64,0x9866,0x6905,0xce10,0xb00f,0x001a,0x2e20,0xb03d}, new char[]{0x9834,0xcd05,0x0074,0xf474,0x54c1,0x6822,0x6cfb,0x6966,0x2e48,0xce65,0xb6fc,0xb064,0xb04e,0x52e4}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xedde,0x0dc7,0xbf7d,0xeeec,0x14d8,0x1f94}, new char[]{0x0d89,0x14b7,0xee98,0xedb7,0xbf1b,0x1fed}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf637,0x39ee,0xa98a,0xe41a,0x9e18,0xf84b,0x1f22,0x2c90,0xdf30,0x4327,0x6c59,0x1d97,0x4b62}, new char[]{0xa9cb,0x9e6d,0xe46e,0xdf5f,0x1f02,0xf81c,0x2cf1,0x435e,0x4b12,0x6c36,0xf65e,0x3980,0x1de3}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0dfb,0x74d7,0xf108,0x8d62,0x7da3,0x5ddb,0xc45a,0xd78f,0x8f26,0xe689,0x79fb}, new char[]{0xe6da,0x5db3,0x7dcc,0x74a0,0xf128,0x8d2f,0x799a,0x8f54,0xc431,0xd7ea,0x0d89}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x465e,0xcdcf,0xbf0f,0xa0f2,0xbac5,0xe3f9,0xead7,0xb164,0x7e3c,0x633e,0x8503,0x6835,0x005f,0xbbf2,0x36f7,0x2864,0x99aa,0xdb46,0x4b03,0x8b9f,0x6d8e,0xf044,0x3a0c,0x4532,0x7a2b,0x9984,0x5a05,0x6028,0x60fe,0x01af,0x57e8,0xd23d,0x8df4,0x3986,0x4db6,0x449c,0x386f,0x8556,0x2b5c,0xf0e8,0x7dd3,0xd1dc,0x7fa6,0x0a5b,0xec95,0xd151,0x9fda,0x3bf8,0x01ff,0xf7f6,0x9294,0x688a,0x3b42,0x831f,0xc727,0x0070,0xb267,0x5735,0x1d77,0xa1c6,0x1fe8,0xab5e,0xb84c,0x53a1}, new char[]{0x8510,0xb10d,0x455c,0x8d90,0x836c,0x3b62,0x4b70,0x002b,0x7a59,0x2b33,0x1d19,0x99cd,0x68e2,0xdb29,0xd1b0,0x4dd2,0x7da0,0x57c8,0x681d,0x1f9b,0x3b8c,0xd13e,0x575b,0xbf6a,0xc707,0x9fb8,0xbb80,0x6097,0xb204,0x7fcd,0x7e4f,0xf068,0x8523,0xd24d,0x0190,0xbab7,0x8beb,0xcdae,0x01c3,0x99a4,0x3a6a,0xb83e,0x0a3a,0xe394,0x53c4,0x605b,0x284d,0xab7e,0x92fd,0x3699,0xa0d2,0x5a69,0xf799,0xf089,0x44f8,0x6deb,0x39e2,0x467e,0xeab4,0x0018,0x634b,0x3801,0xa1ad,0xece6}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x37e8,0x4aaa,0x4eae,0x1e85,0x9707,0x143e,0xd345,0x51e7,0x2158,0x06b1,0x7da5,0xc786,0xed2d,0xf30a,0x6ebb,0xf248,0x56eb}, new char[]{0x6ee8,0xc7f2,0x06c3,0x1451,0xed43,0x378f,0x4ec6,0x5188,0x2134,0x9763,0x1ea5,0x7de3,0xf363,0x5685,0xf22c,0xd320,0x4ad8}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11cd,0xb075,0x7d9f,0xe82d,0x319d,0x4554,0xfc30,0x99bf,0xe820,0x8bb2,0xafb9,0x2cc5,0xdaab,0x7a6a}, new char[]{0xe873,0x7deb,0x31ef,0xe842,0xfc5e,0x99d8,0x2cad,0xb01a,0x11a1,0x8bd6,0x4574,0xdaca,0xafcd,0x7a4a}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5d06,0x191f,0xd94a,0x15fb,0x9b20,0x139d,0x84dd,0x4591,0xd54d,0x3c0c,0xeb0e,0x455d,0xe501,0x143e,0x36b6,0x27b6,0x40f0,0x4bcd,0x1bf1,0x3ad0,0x942d,0xe9f6,0xf4d8}, new char[]{0x45c1,0xe56e,0x9b53,0x196c,0xd524,0xeb6c,0x27da,0x3ab5,0xf4f8,0x4bbe,0xd93e,0x3c7e,0x13f2,0x84b3,0x944a,0x36de,0x4532,0x1452,0x5d62,0xe9d6,0x159a,0x1b85,0x40d0}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0fde,0xe65b,0xd3fc,0xa2dd,0x083d,0xf58b,0xc7e7,0xe0a0,0x963d,0xc98a}, new char[]{0xc7b4,0xc9fe,0x084f,0xe634,0xf5e5,0x965a,0x0fb6,0xa2b2,0xd390,0xe0c4}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c08,0xf0f8,0xca2b,0x29d8,0x6212,0x02cd,0xaf72,0xc68a,0xc44c,0x578c,0x6303,0x5f29}, new char[]{0x6c5b,0x29ac,0x6260,0x5f46,0xc6e4,0xc42b,0xca43,0x636c,0x57e0,0xaf16,0xf0d8,0x02ed}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaad4,0xf10c,0x4c70,0x4609,0x5e41,0xc332,0x25b2,0xe34d,0xcede,0x7bba,0xbc7f,0xfa81,0x4a99}, new char[]{0x5e12,0xceaa,0xe33f,0x25dd,0x4667,0xbc18,0x7bd2,0xf163,0xfaed,0xaab0,0xc30d,0x4c50,0x4ab9}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0cb8}, new char[]{0x0cd5,0x6291,0xb0bd,0x9c62}).plainString();
    }
}
