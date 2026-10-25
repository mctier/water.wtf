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
 * Finds bastion remnants in loaded Nether chunks. Gilded blackstone or a lodestone is proof on its
 * own; otherwise a chunk packed with polished-blackstone brick variants (plus chains or chiseled
 * trim) counts. Nether only; chunks are scanned a few per tick with a cache.
 */
public class BastionFinder extends Feature {
    private static final Set<Block> BRICKS = Set.of(Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE);

    private final IntSetting range = new IntSetting(LT.L1, 10, 2, 24);
    private final BoolSetting notify = new BoolSetting(LT.L2, true);
    private final BoolSetting autoWaypoint = new BoolSetting(LT.L3, false);
    private final BoolSetting showMarker = new BoolSetting(LT.L4, true);

    private record Bastion(BlockPos center, boolean confirmed, int bricks) {}

    private final Map<Long, Bastion> hits = new HashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private final Set<Long> announced = new HashSet<>();
    private List<Bastion> bastions = List.of();
    private int ticks;

    public BastionFinder() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd138,0x0b32,0xb160,0x37d7,0x962a,0x226b,0xeb85,0xfcf5,0x828d,0xbc1d,0xa8aa,0x1cb4,0x11fe,0x6553}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}).toString(), LT.L5, Category.FINDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        hits.clear();
        scannedAt.clear();
        announced.clear();
        bastions = List.of();
    }

    @Override
    public CharSequence concat() {
        return String.valueOf(bastions.size());
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.world.getRegistryKey() != World.NETHER) return;
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
                    Bastion b = scan(chunk);
                    if (b != null) hits.put(key, b); else hits.remove(key);
                    scannedAt.put(key, now);
                    if (--budget <= 0) break outer;
                }
            }
        }
        if (++ticks % 20 == 0) bastions = new ArrayList<>(hits.values());

        for (Bastion b : hits.values()) {
            long ck = ChunkPos.toLong(b.center().getX() >> 4, b.center().getZ() >> 4);
            boolean near = false;
            for (long a : announced)
                if (Math.abs(ChunkPos.getPackedX(a) - (b.center().getX() >> 4)) <= 4
                        && Math.abs(ChunkPos.getPackedZ(a) - (b.center().getZ() >> 4)) <= 4) { near = true; break; }
            if (near) continue;
            announced.add(ck);
            if (notify.getValue()) Notifications.push(LT.L6,
                    (b.confirmed() ? LT.L7 : LT.L8) + b.center().toShortString(), Notifications.Type.INFO);
            if (autoWaypoint.getValue()) Waypoints.add(LT.L9, b.center().getX(), b.center().getY(), b.center().getZ());
        }
    }

    private Bastion scan(WorldChunk chunk) {
        ChunkSection[] secs = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        int bricks = 0, gilded = 0, chiseled = 0;
        BlockPos proof = null, any = null;
        long sy = 0;
        int n = 0;
        for (int i = 0; i < secs.length; i++) {
            ChunkSection s = secs[i];
            if (s == null || s.isEmpty()) continue;
            int baseY = (bottom + i) << 4;
            boolean maybe = s.hasAny(st -> BRICKS.contains(st.getBlock()) || st.isOf(Blocks.GILDED_BLACKSTONE)
                    || st.isOf(Blocks.LODESTONE) || st.isOf(Blocks.CHISELED_POLISHED_BLACKSTONE));
            if (!maybe) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                Block b = s.getBlockState(x, y, z).getBlock();
                BlockPos p = null;
                if (b == Blocks.GILDED_BLACKSTONE || b == Blocks.LODESTONE) {
                    gilded++;
                    proof = new BlockPos(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z);
                } else if (BRICKS.contains(b)) {
                    bricks++;
                    p = new BlockPos(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z);
                    if (b == Blocks.CHISELED_POLISHED_BLACKSTONE) chiseled++;
                }
                if (p != null) { sy += p.getY(); n++; any = p; }
            }
        }
        boolean confirmed = gilded > 0;
        boolean possible = bricks >= 100 || (bricks >= 40 && chiseled > 0);
        if (!confirmed && !possible) return null;
        BlockPos center = proof != null ? proof
                : new BlockPos(chunk.getPos().getStartX() + 8, n > 0 ? (int) (sy / n) : mc.player.getBlockY(), chunk.getPos().getStartZ() + 8);
        if (any != null && proof == null) center = any;
        return new Bastion(center, confirmed, bricks);
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (!showMarker.getValue() || frame == null || mc.player == null || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        for (Bastion b : bastions) {
            Vector4f clip = frame.toClip(b.center().getX() + 0.5, b.center().getY() + 2, b.center().getZ() + 0.5);
            if (clip.w() <= 0.05f) continue;
            float[] g = Render3D.toGui(clip);
            int col = b.confirmed() ? 0xFFFFC55C : 0xFFA8894A;
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(g[0], g[1]);
            m.rotate((float) Math.toRadians(45));
            Draw.rect(ctx, -5, -5, 10, 10, 2, Colors.withAlpha(0xFF000000, 0.45f));
            Draw.rect(ctx, -4, -4, 8, 8, 1, col);
            m.popMatrix();
            double d = Math.sqrt(mc.player.squaredDistanceTo(b.center().getX() + 0.5, b.center().getY(), b.center().getZ() + 0.5));
            String label = (b.confirmed() ? LT.L10 : LT.L11) + Math.round(d) + LT.L12;
            float tw = Text.width(label, 7f) + 10;
            Draw.rect(ctx, Math.round(g[0] - tw / 2), Math.round(g[1] - 19), Math.round(tw), 11, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, g[0], g[1] - 13.5f, 7f, col);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8309,0xddf4,0x0036,0x6cd0,0x1232,0x0a76,0xd560,0x2902,0xd7cc,0xbd1f,0xefc6,0x02b4,0x8342,0xb477}, new char[]{0x02e6,0x8368,0xefa8,0x1255,0x0053,0x0a56,0x6cf8,0xbd7c,0x832a,0xdd81,0x296c,0xd50b,0xd7bf,0xb45e}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x422c,0x99eb,0x2d70,0x83ed,0x342c,0x0a22}, new char[]{0x99a5,0x2d1f,0x4258,0x0a4b,0x838b,0x3455}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3603,0x5f1f,0xf9fc,0x1df3,0xad74,0x2e1a,0x0bd7,0xb30d,0x7eda,0x5657,0xb497,0x2798,0xbb63}, new char[]{0xf9bd,0xb4e2,0xb379,0x2e75,0x1dd3,0x3654,0x27f9,0x562e,0xbb13,0x0bb8,0x7eb3,0xad1a,0x5f6b}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb14a,0xa11a,0xf50d,0x0e5c,0x0d62,0x375f,0x29a2,0xa248,0x3c7b,0x5fc4,0x9da6}, new char[]{0x9df5,0x29ca,0x3c14,0xb13d,0x5fe4,0xf540,0x373e,0xa168,0x0d09,0x0e39,0xa23a}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e09,0xbfbe,0x8732,0xa9b1,0x5d0a,0x6dd5,0x3ef1,0x35d1,0xb898,0x726a,0xc90f,0x8273,0x3bd8,0x2e5d,0x6519,0xd013,0x9bd8,0x0706,0xb39a,0x4852,0x41c7,0x3c25,0xaffe,0x8369,0xf131,0xb8c7,0x61c2,0x7908,0x184c,0x6272,0x4cf1,0xfb07,0xe20f,0x9b23,0x5ac8,0x58aa,0xc3f5,0x5f05,0xba6d,0x87c4,0xca88,0x457e,0x4ebf,0x392d,0xc1db,0x9344,0xb611,0xc5aa,0x5ec9,0x1f42,0x805f,0xb8de,0x74d5,0xb6e6,0x8cf8,0x8401,0x6dc1,0x579f,0x330e,0x0401,0x6ea0,0x114f,0x1f05,0xc35c,0x7c6b,0x0710,0x8860}, new char[]{0x8774,0x7c02,0xa9df,0xb3fe,0x0763,0x7928,0x451c,0x3e90,0x57ec,0x4c85,0x5d63,0xd07c,0xc961,0x8cd8,0x6da7,0x336b,0x61af,0x6daf,0x1f64,0xaf90,0xf145,0xe27c,0x8e29,0x87ec,0x5f62,0x2e34,0x7206,0x6216,0xc390,0x3bbc,0xb8b8,0x1f20,0x076a,0x4833,0x0462,0x5ea2,0xb695,0x58de,0x4ed0,0xb8b0,0x9321,0x842d,0x116f,0x821f,0x35be,0x9bbc,0xfb62,0x3c56,0x831d,0x41a8,0x3943,0xc339,0x9b0a,0xba4d,0x8036,0x74bb,0xb8e7,0xbfca,0x1824,0x657c,0xc58a,0x6eee,0xb674,0xcafc,0x8808,0xc1be,0x5aba}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f17,0x5506,0xb371,0x24ee,0xc3e0,0x7e87,0x1f04,0x5fa6,0x99c5,0x3ca2,0x6901,0x8314,0x0f37,0x76bd}, new char[]{0x5544,0x248f,0x8367,0x1f70,0x0f5e,0x5fc9,0xc38e,0x6921,0x3ce4,0x99ac,0x76d3,0x0f73,0xb314,0x7ef5}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x235f,0x1344,0x65a6,0xebcf,0x2baa,0x6323,0x2812,0xa49a,0x3b3a,0x8139,0x004e}, new char[]{0x65e4,0x233e,0x2bd9,0xa4ee,0x8150,0xeba0,0x634d,0x006e,0x3b5b,0x1330,0x2832}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa816,0x73a0,0x7f60,0xdcba,0x193f,0x82e7,0xc41e,0x21a8,0xbcec,0x7e95,0xe94a,0x2316,0x14a4,0x0287,0x11aa,0x31a2,0x59cc,0xad89,0x2ff2,0xe7cd}, new char[]{0x7f30,0x31cd,0x59bf,0x73d3,0x2f9b,0x195d,0xe7a1,0x8282,0xbccc,0x14c6,0x11cb,0xdcc9,0xadfd,0x21c1,0xa879,0xe924,0x7eb5,0x02e6,0xc46a,0x2336}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbeb1,0xd8dd,0xa4d7,0x45a0,0xda51,0x5bc7,0x9af2}, new char[]{0x9ab0,0xda30,0x45d3,0xbec5,0xa4be,0x5ba8,0xd8b3}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa0d0,0xb4b6,0x15bc,0xd415,0x004c,0x0e5b,0xead1,0xa223,0x4bac}, new char[]{0x4bee,0x002d,0x15cf,0xeaa5,0xd47c,0xa0bf,0xa24d,0x0e7b,0xb496}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x955e,0x2cfc,0x149f,0x1cf8,0x4cf7,0x3521,0xe90c,0x5e5d,0x2563,0xf84e}, new char[]{0x14dd,0xf82f,0x5e2e,0x952a,0x250a,0x354e,0xe962,0x2cc3,0x1cd8,0x4cd7}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x081c}, new char[]{0x0871,0xfeb6,0x9325,0xb56c}).plainString();
    }
}
