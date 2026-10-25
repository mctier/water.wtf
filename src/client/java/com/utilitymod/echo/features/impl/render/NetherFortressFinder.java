package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.gui.Waypoints;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Heuristic detection in already-loaded Nether chunks, never a seed/structure lookup. */
public final class NetherFortressFinder extends Feature {
    private static final int SCANS_PER_TICK = 2;
    private static final int POSITIONS_PER_TICK = 32;
    private static final int RESCAN_TICKS = 200;
    private static final String LABEL = LT.L1;

    private final IntSetting range = new IntSetting(new EncryptedString(new char[]{0xd128, 0x0b74, 0xb12f, 0x37db, 0x9636, 0x2276, 0xeb8a, 0xfcae, 0x82ce, 0xbc0e, 0xa8aa, 0x1c9a, 0x11fe, 0x6545}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87}), 10, 2, 24);
    private final BoolSetting notify = new BoolSetting(new EncryptedString(new char[]{0x226c, 0x962a, 0x11ff, 0xeb8f, 0x82cc, 0x0b3f, 0xa8a4, 0xbc00, 0x1c86, 0xd134, 0x37d8, 0x6545, 0xb166}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536}), true);
    private final BoolSetting autoWaypoint = new BoolSetting(new EncryptedString(new char[]{0x226c, 0x962c, 0x11f9, 0xeb8f, 0x82d4, 0x0b3d, 0xa8be, 0xbc06, 0x1c82, 0xd13b, 0x379e, 0x6542, 0xb158}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536}), false);
    private final BoolSetting showMarker = new BoolSetting(new EncryptedString(new char[]{0x11e2, 0xa8a3, 0xbc1e, 0x2277, 0x1c99, 0x82df, 0x0b3d, 0xd129, 0xb142, 0x9626, 0x379e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}), true);

    private record Fortress(BlockPos center) {}
    private final Map<Long, Fortress> hits = new HashMap<>();
    // Negative results are cached too; otherwise near empty chunks starve the outer rings.
    private final Map<Long, Long> nextScan = new HashMap<>();
    private final LinkedHashSet<Long> announced = new LinkedHashSet<>();
    private List<FortressScanPolicy.Offset> offsets = List.of();
    private List<Fortress> markers = List.of();
    private ClientWorld trackedWorld;
    private int searchRadius = -1;
    private int cursor;
    private long tick;

    public NetherFortressFinder() {
        super(new FeatureInfo(new EncryptedString(new char[]{0x4a2c, 0xfcf4, 0xbc01, 0x37db, 0x0b7c, 0xd93d, 0x82eb, 0x76a7, 0xeb93, 0x2a8d, 0x50cb, 0x11e4, 0xf005, 0xa8ae, 0xb17d, 0x226c, 0x6553, 0x9631, 0xd134, 0x1c9d, 0x5f66, 0xc5d2}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff}),
                LT.L2, Category.FINDER));
    }

    private void clearSearch() {
        hits.clear(); nextScan.clear(); announced.clear();
        markers = List.of(); offsets = List.of();
        trackedWorld = null; searchRadius = -1; cursor = 0; tick = 0;
    }

    @Override public void onEnable() { clearSearch(); super.onEnable(); }
    @Override public void onDisable() { super.onDisable(); clearSearch(); }
    @Override public CharSequence concat() { return Integer.toString(markers.size()); }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.world.getRegistryKey() != World.NETHER) {
            if (trackedWorld != null) clearSearch();
            return;
        }
        if (trackedWorld != mc.world) { clearSearch(); trackedWorld = mc.world; }
        tick++;
        int radius = Math.clamp(range.getValue(), 2, 24);
        if (radius != searchRadius) {
            offsets = FortressScanPolicy.offsets(radius);
            searchRadius = radius;
            cursor = 0;
        }
        ChunkPos player = mc.player.getChunkPos();
        // Keep both hits and negative-result cache bounded to the configured search square.
        nextScan.keySet().removeIf(key -> !inRange(key, player, radius));
        hits.keySet().removeIf(key -> !inRange(key, player, radius)
                || mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)) == null);

        int scanned = 0;
        for (int checked = 0; checked < Math.min(POSITIONS_PER_TICK, offsets.size()) && scanned < SCANS_PER_TICK; checked++) {
            var offset = offsets.get(cursor);
            cursor = (cursor + 1) % offsets.size();
            int x = player.x + offset.x(), z = player.z + offset.z();
            long key = ChunkPos.toLong(x, z);
            if (nextScan.getOrDefault(key, 0L) > tick) continue;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
            if (chunk == null) continue; // Never request/generate chunks.
            Fortress found = scan(chunk);
            nextScan.put(key, tick + RESCAN_TICKS);
            if (found == null) hits.remove(key); else hits.put(key, found);
            scanned++;
        }

        List<Fortress> grouped = new ArrayList<>();
        for (var entry : hits.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            Fortress found = entry.getValue();
            if (grouped.stream().anyMatch(other -> nearby(other.center(), found.center()))) continue;
            grouped.add(found);
            long key = ChunkPos.toLong(found.center().getX() >> 4, found.center().getZ() >> 4);
            boolean alreadyAnnounced = announced.stream().anyMatch(old -> FortressScanPolicy.inRange(
                    ChunkPos.getPackedX(old), ChunkPos.getPackedZ(old), ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key), 4));
            if (alreadyAnnounced) continue;
            announced.add(key);
            if (announced.size() > 512) announced.remove(announced.iterator().next());
            if (notify.getValue()) Notifications.push(String.valueOf(new EncryptedString(new char[]{0x4a2c, 0xfcf4, 0xbc01, 0x37db, 0x0b7c, 0xd93d, 0x82eb, 0x76a7, 0xeb93, 0x2a8d, 0x50cb, 0x11e4, 0xf005, 0xa8ae, 0xb17d, 0x226c, 0x6553, 0x9631, 0xd134, 0x1c9d, 0x5f66, 0xc5d2}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff})),
                    LABEL + LT.L3 + found.center().toShortString(), Notifications.Type.INFO);
            if (autoWaypoint.getValue() && Waypoints.LIST.stream().noneMatch(w ->
                    w.name().equals(LABEL) && w.dimension().equals(Waypoints.dimension())
                            && nearby(new BlockPos(w.x(), w.y(), w.z()), found.center()))) {
                Waypoints.add(LABEL, found.center().getX(), found.center().getY(), found.center().getZ());
            }
        }
        markers = List.copyOf(grouped);
    }

    private static boolean inRange(long key, ChunkPos player, int radius) {
        return FortressScanPolicy.inRange(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key), player.x, player.z, radius);
    }

    private static boolean nearby(BlockPos a, BlockPos b) {
        return FortressScanPolicy.inRange(a.getX() >> 4, a.getZ() >> 4, b.getX() >> 4, b.getZ() >> 4, 4);
    }

    private Fortress scan(WorldChunk chunk) {
        int bricks = 0, fences = 0, stairs = 0, samples = 0;
        long sumX = 0, sumY = 0, sumZ = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int index = 0; index < sections.length; index++) {
            ChunkSection section = sections[index];
            if (section == null || section.isEmpty() || !section.hasAny(state ->
                    state.isOf(Blocks.NETHER_BRICKS) || state.isOf(Blocks.NETHER_BRICK_FENCE)
                            || state.isOf(Blocks.NETHER_BRICK_STAIRS))) continue;
            int baseY = (chunk.getBottomSectionCoord() + index) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                Block block = section.getBlockState(x, y, z).getBlock();
                if (block == Blocks.NETHER_BRICKS) bricks++;
                else if (block == Blocks.NETHER_BRICK_FENCE) fences++;
                else if (block == Blocks.NETHER_BRICK_STAIRS) stairs++;
                else continue;
                samples++; sumX += chunk.getPos().getStartX() + x;
                sumY += baseY + y; sumZ += chunk.getPos().getStartZ() + z;
            }
        }
        if (!FortressScanPolicy.possible(bricks, fences, stairs)) return null;
        return new Fortress(new BlockPos((int)Math.floorDiv(sumX, samples), (int)Math.floorDiv(sumY, samples), (int)Math.floorDiv(sumZ, samples)));
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        if (!showMarker.getValue() || isNull() || mc.world != trackedWorld
                || mc.world.getRegistryKey() != World.NETHER || mc.options.hudHidden) return;
        Render3D frame = EchoEvents.lastFrame();
        if (frame == null) return;
        var ctx = event.getContext();
        for (Fortress fortress : markers) {
            BlockPos pos = fortress.center();
            var clip = frame.toClip(pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5);
            if (clip.w() <= 0.05f || clip.z() < -clip.w() || clip.z() > clip.w()) continue;
            float[] gui = Render3D.toGui(clip);
            if (!Float.isFinite(gui[0]) || !Float.isFinite(gui[1]) || gui[0] < 0 || gui[1] < 0
                    || gui[0] > mc.getWindow().getScaledWidth() || gui[1] > mc.getWindow().getScaledHeight()) continue;
            long distance = Math.round(Math.sqrt(mc.player.squaredDistanceTo(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5)));
            String label = LT.L4 + distance + LT.L5;
            float width = Text.width(label, 7f) + 10;
            Draw.rect(ctx, Math.round(gui[0]-width/2), Math.round(gui[1]-19), Math.round(width), 11, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, gui[0], gui[1]-13.5f, 7f, 0xFFFF8870);
            Draw.rect(ctx, Math.round(gui[0]-3), Math.round(gui[1]-3), 6, 6, 2, 0xFFFF8870);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9455,0x000b,0x1abd,0xe459,0xc1e4,0x45cc,0x86e3,0xbbcf,0xd8c4,0x5a4f,0x3cbd,0x7afb,0xc806,0x4d80,0xfd29,0x5618,0x7d4d,0x13f0,0x2668,0x09fd,0x73a5,0xd204,0xb53f,0xcff1}, new char[]{0xcfa1,0xd8ab,0x261b,0x098e,0xc18d,0x8681,0x0067,0xb55a,0x3c9d,0x4582,0x7d28,0x4df4,0x1398,0xbbaa,0x9427,0xc826,0xd242,0xe436,0x5a3d,0x7a8f,0x73d7,0x567d,0x1ace,0xfd5a}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda36,0xe5c7,0x4677,0x7a00,0x22c0,0xf78d,0xf259,0x535f,0x89ce,0x3a3d,0xd47c,0xfcfa,0xfceb,0x15ac,0xac93,0xbbba,0x1d16,0x2520,0xb6ee,0x8978,0x527d,0x7047,0xefc5,0xe247,0xa0e8,0x16a1,0xd338,0x0b18,0xae91,0x403c,0x0123,0x4fe7,0xb0ea,0x32b9,0xbc67,0x193d,0x443f,0x96ae,0xfe53,0x7b4d,0xd54b,0x9a2c,0x8b12,0x463a,0xa9d5,0x06a5,0x9513,0x601b,0x6be3,0xc17d,0x39f0,0xb7ad,0x15b0,0x8269,0xa33e,0xab2d,0x5f07,0xeb25,0x3346,0xfd28,0xeb98,0x8c20,0x2ea7,0x014d,0x9826,0x65b2,0x1491,0x6d10,0x4165}, new char[]{0xfe1e,0x6d71,0x15de,0xaefa,0xefb6,0x525d,0xace3,0xa9ba,0x8b61,0x7b3e,0x96c7,0x891a,0xfd44,0x3323,0x2500,0x32df,0xd524,0x4605,0x4111,0xe235,0xfc8e,0x7034,0x194e,0x0b7d,0x14e2,0xd318,0xbbdc,0x06d7,0xb085,0xfc97,0xd45c,0xc133,0x533a,0x16d5,0x1d7e,0x22a5,0x89bc,0xa31e,0x012f,0x8c52,0xf7e4,0x445c,0xb685,0x404f,0x7a20,0x3999,0xab43,0xda16,0x957f,0x2ec8,0x6b82,0xe5a3,0x5f62,0xb7c9,0x8249,0x4fa9,0x9843,0xbc13,0xebf0,0xa08d,0x3a4f,0xf279,0x0140,0x9a44,0x65c7,0x15de,0x6070,0xeb56,0x4614}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe7c7,0x5dd6,0xb7dd,0x04cf}, new char[]{0xe7e7,0x5db7,0xb7a9,0x04ef}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1cc9,0x71ec,0xd5a8,0x65b4,0x67b6,0x2cb3,0xdd8f,0xa70a,0x4989,0x5033,0x3183,0x2062,0x41f5,0x0850,0x68e1,0xdec8,0x95e2,0xd5b5}, new char[]{0x65e4,0xa765,0xddfc,0x9591,0x1ca0,0x2000,0x7180,0x67d3,0xdee8,0x31c5,0x688e,0xd5c7,0x2cc7,0x0822,0x4190,0x5040,0x49fa,0xd588}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x870e}, new char[]{0x8763,0x907c,0xa70b,0x8139}).plainString();
    }
}
