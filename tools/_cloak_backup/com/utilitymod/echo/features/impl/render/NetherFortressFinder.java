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
    private static final String LABEL = "Possible Nether Fortress";

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
                "Marks possible fortresses from Nether bricks in loaded Nether chunks.", Category.FINDER));
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
                    LABEL + " at " + found.center().toShortString(), Notifications.Type.INFO);
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
            String label = "Possible Fortress " + distance + "m";
            float width = Text.width(label, 7f) + 10;
            Draw.rect(ctx, Math.round(gui[0]-width/2), Math.round(gui[1]-19), Math.round(width), 11, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, gui[0], gui[1]-13.5f, 7f, 0xFFFF8870);
            Draw.rect(ctx, Math.round(gui[0]-3), Math.round(gui[1]-3), 6, 6, 2, 0xFFFF8870);
        }
    }
}
