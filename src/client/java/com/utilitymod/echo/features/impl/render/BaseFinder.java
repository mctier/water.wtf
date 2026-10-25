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
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BellBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
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
 * Finds player bases from the blocks players leave behind: ender chests, enchanting tables,
 * anvils, beacons, shulkers, brewing stands, piles of chests, beds, and villagers away from any
 * village bell (trading halls). Chunks are scored, neighbours grouped, strong groups marked.
 */
public class BaseFinder extends Feature {
    private final IntSetting range = new IntSetting(LT.L1, 12, 2, 32);
    private final IntSetting sensitivity = new IntSetting(LT.L2, 8, 3, 30);
    private final BoolSetting villagers = new BoolSetting(LT.L3, true);
    private final BoolSetting notify = new BoolSetting(LT.L4, true);
    private final BoolSetting autoWaypoint = new BoolSetting(LT.L5, false);
    private final BoolSetting showMarker = new BoolSetting(LT.L6, true);

    /** What one chunk contains. */
    private static final class Tally {
        int enderChests, enchantTables, anvils, beacons, shulkers, brewing, chests, beds, bells;
        BlockPos at;

        int score() {
            return enderChests * 3 + enchantTables * 3 + anvils * 3 + beacons * 5 + shulkers * 2 + brewing * 2
                    + Math.min(chests, 20) / 3 + beds / 2;
        }
    }

    private record Base(BlockPos center, int score, String summary) {}

    private final Map<Long, Tally> tallies = new HashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private final Set<Long> announced = new HashSet<>();
    private List<Base> bases = List.of();
    private int ticks;

    public BaseFinder() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e2,0xa8aa,0xbc0c,0x226b,0x1c96,0x82c3,0x0b35,0xd138,0xb149,0x9626,0x379e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString(), LT.L7, Category.FINDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        tallies.clear();
        scannedAt.clear();
        announced.clear();
        bases = List.of();
    }

    @Override
    public CharSequence concat() {
        return String.valueOf(bases.size());
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        ChunkPos c = mc.player.getChunkPos();
        int r = range.getValue();
        long now = System.currentTimeMillis();
        tallies.keySet().removeIf(k -> Math.abs(ChunkPos.getPackedX(k) - c.x) > r || Math.abs(ChunkPos.getPackedZ(k) - c.z) > r);
        scannedAt.keySet().retainAll(tallies.keySet());
        int budget = 3;
        outer:
        for (int ring = 0; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    long key = ChunkPos.toLong(c.x + dx, c.z + dz);
                    Long at = scannedAt.get(key);
                    if (at != null && now - at < 8000) continue;
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(c.x + dx, c.z + dz);
                    if (chunk == null) continue;
                    tallies.put(key, scan(chunk));
                    scannedAt.put(key, now);
                    if (--budget <= 0) break outer;
                }
            }
        }
        if (++ticks % 20 == 0) rebuild();
    }

    private Tally scan(WorldChunk chunk) {
        Tally t = new Tally();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            boolean hit = true;
            if (be instanceof EnderChestBlockEntity) t.enderChests++;
            else if (be instanceof EnchantingTableBlockEntity) t.enchantTables++;
            else if (be instanceof BeaconBlockEntity) t.beacons++;
            else if (be instanceof ShulkerBoxBlockEntity) t.shulkers++;
            else if (be instanceof BrewingStandBlockEntity) t.brewing++;
            else if (be instanceof ChestBlockEntity) t.chests++;
            else if (be instanceof BedBlockEntity) t.beds++;
            else if (be instanceof BellBlockEntity) { t.bells++; hit = false; }
            else hit = false;
            if (hit && (t.at == null || be instanceof EnderChestBlockEntity || be instanceof EnchantingTableBlockEntity)) t.at = be.getPos();
        }
        // anvils are plain blocks: palette check, then find one
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty()) continue;
            if (!s.hasAny(st -> st.isOf(Blocks.ANVIL) || st.isOf(Blocks.CHIPPED_ANVIL) || st.isOf(Blocks.DAMAGED_ANVIL))) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        var st = s.getBlockState(x, y, z);
                        if (st.isOf(Blocks.ANVIL) || st.isOf(Blocks.CHIPPED_ANVIL) || st.isOf(Blocks.DAMAGED_ANVIL)) {
                            t.anvils++;
                            if (t.at == null) t.at = new BlockPos(chunk.getPos().getStartX() + x, ((bottom + i) << 4) + y, chunk.getPos().getStartZ() + z);
                        }
                    }
                }
            }
        }
        return t;
    }

    private void rebuild() {
        // villagers per chunk (a trading hall counts; villagers near a bell are a village, not a base)
        Map<Long, Integer> vill = new HashMap<>();
        if (villagers.getValue()) {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof VillagerEntity) vill.merge(ChunkPos.toLong(e.getBlockX() >> 4, e.getBlockZ() >> 4), 1, Integer::sum);
            }
        }
        Set<Long> keys = new HashSet<>(tallies.keySet());
        keys.addAll(vill.keySet());
        // group chunks with any evidence, within 3 chunks of each other
        List<long[]> groups = new ArrayList<>(); // sumX, sumZ, n, score, ec, et, an, bc, sh, villagers, bells, ax, ay, az
        for (long k : keys) {
            Tally t = tallies.get(k);
            int v = vill.getOrDefault(k, 0);
            int sc = t == null ? 0 : t.score();
            if (sc == 0 && v == 0) continue;
            int cx = ChunkPos.getPackedX(k), cz = ChunkPos.getPackedZ(k);
            long[] g = null;
            for (long[] o : groups) {
                if (Math.abs(o[0] / o[2] - cx) <= 3 && Math.abs(o[1] / o[2] - cz) <= 3) { g = o; break; }
            }
            if (g == null) {
                groups.add(g = new long[14]);
                g[11] = Long.MIN_VALUE;
            }
            g[0] += cx; g[1] += cz; g[2]++; g[3] += sc; g[9] += v;
            if (t != null) {
                g[4] += t.enderChests; g[5] += t.enchantTables; g[6] += t.anvils; g[7] += t.beacons; g[8] += t.shulkers; g[10] += t.bells;
                if (t.at != null && g[11] == Long.MIN_VALUE) { g[11] = t.at.getX(); g[12] = t.at.getY(); g[13] = t.at.getZ(); }
            }
        }
        List<Base> out = new ArrayList<>();
        for (long[] g : groups) {
            int score = (int) g[3];
            if (g[10] == 0) score += (int) g[9] * 2; // villagers with no bell around = trading hall
            if (score < sensitivity.getValue()) continue;
            BlockPos center = g[11] != Long.MIN_VALUE ? new BlockPos((int) g[11], (int) g[12], (int) g[13])
                    : new BlockPos((int) (g[0] / g[2] * 16 + 8), mc.player.getBlockY(), (int) (g[1] / g[2] * 16 + 8));
            StringBuilder sb = new StringBuilder();
            part(sb, g[4], LT.L8);
            part(sb, g[5], LT.L9);
            part(sb, g[6], LT.L10);
            part(sb, g[7], LT.L11);
            part(sb, g[8], LT.L12);
            if (g[10] == 0) part(sb, g[9], LT.L13);
            out.add(new Base(center, score, sb.toString()));
        }
        bases = out;
        for (Base b : out) {
            boolean seen = false;
            for (long a : announced) {
                if (Math.abs(ChunkPos.getPackedX(a) - (b.center().getX() >> 4)) <= 4
                        && Math.abs(ChunkPos.getPackedZ(a) - (b.center().getZ() >> 4)) <= 4) { seen = true; break; }
            }
            if (seen) continue;
            announced.add(ChunkPos.toLong(b.center().getX() >> 4, b.center().getZ() >> 4));
            String where = b.center().getX() + LT.L14 + b.center().getY() + LT.L14 + b.center().getZ();
            if (notify.getValue()) Notifications.push(LT.L15, LT.L16 + where + (b.summary().isEmpty() ? "" : LT.L17 + b.summary()), Notifications.Type.INFO);
            if (autoWaypoint.getValue()) Waypoints.add(LT.L18, b.center().getX(), b.center().getY(), b.center().getZ());
        }
    }

    private static void part(StringBuilder sb, long n, String what) {
        if (n <= 0) return;
        if (sb.length() > 0) sb.append(LT.L14);
        sb.append(n).append(' ').append(what).append(n > 1 ? LT.L19 : "");
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (!showMarker.getValue() || frame == null || mc.player == null || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        for (Base b : bases) {
            Vector4f clip = frame.toClip(b.center().getX() + 0.5, b.center().getY() + 2.5, b.center().getZ() + 0.5);
            if (clip.w() <= 0.05f) continue;
            float[] p = Render3D.toGui(clip);
            int col = 0xFFFF8A4C;
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(p[0], p[1]);
            m.rotate((float) Math.toRadians(45));
            Draw.rect(ctx, -5, -5, 10, 10, 2, Colors.withAlpha(0xFF000000, 0.45f));
            Draw.rect(ctx, -4, -4, 8, 8, 1, col);
            m.popMatrix();
            double d = Math.sqrt(mc.player.squaredDistanceTo(b.center().getX() + 0.5, b.center().getY(), b.center().getZ() + 0.5));
            String label = LT.L20 + Math.round(d) + LT.L21 + b.score() + LT.L22;
            String sub = b.summary();
            float tw = Math.max(Text.width(label, 7f), Text.width(sub, 6f)) + 10;
            int h = sub.isEmpty() ? 11 : 19;
            Draw.rect(ctx, Math.round(p[0] - tw / 2), Math.round(p[1] - 10 - h), Math.round(tw), h, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, p[0], p[1] - 4.5f - h, 7f, col);
            if (!sub.isEmpty()) Text.drawCenteredX(ctx, sub, p[0], p[1] - 4.5f - h + 9, 6f, 0xFFCCCCCC);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaab8,0xab39,0x834d,0x3a2f,0xf8ff,0x118b,0xf3f3,0x0ef6,0x652a,0x4210,0xdc8a,0x1b48,0xbbf4,0x496c}, new char[]{0x1b1a,0xaad9,0xbb9a,0x3a48,0xdcef,0xf8df,0x4944,0x832e,0xab51,0x11fe,0x0e98,0x427b,0xf380,0x6503}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbd84,0xcd4e,0x7f99,0x6e25,0x27a1,0xe7d8,0x0bc7,0xfdc2,0x999c}, new char[]{0x27ec,0xbded,0xe7b6,0xcd6e,0x6e76,0x0ba4,0x99f3,0xfdb0,0x7ffc}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf47e,0x3785,0x08ac,0xde2d,0x5699,0xb9e2,0x9377,0x8e77,0xe8a1,0x52bf,0x5dcd,0x7652,0x3495,0xbfbf,0xb8c8}, new char[]{0x52fc,0x9318,0x34e0,0xb98c,0x37f1,0x7672,0xf428,0xde44,0xe8cd,0x8e1b,0xb8a9,0x08cb,0x56fc,0xbfcd,0x5dbe}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xafdb,0x07b3,0x0c09,0x62e7,0x4ae8,0xa082}, new char[]{0x4aa6,0xa0ed,0xafaf,0x0c60,0x07d5,0x629e}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9d7d,0x745d,0x6c14,0x5062,0x749c,0x6b7b,0x69b6,0x21ca,0x57ca,0xf3fa,0xc0a0,0xd411,0x09ff}, new char[]{0x741c,0x74e9,0xc0d4,0x6c7b,0xd431,0x9d2a,0x69d7,0x57b3,0xf38a,0x0990,0x6b12,0x500c,0x21be}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x13e5,0xacf6,0x633c,0xff1d,0x16b0,0xbd26,0x2284,0xf656,0xb92e,0x39e3,0xe79a}, new char[]{0x13b6,0xbd4e,0x16df,0xb959,0xff3d,0xf61b,0xe7fb,0x3991,0x22ef,0x6359,0xac84}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3719,0xc478,0x4fbb,0xc783,0x2a17,0xb834,0x537d,0x45d8,0xdb93,0x6982,0xe10c,0x0f02,0xeb66,0x0f94,0x9821,0xa7b3,0x87f7,0x835a,0xb52e,0x0fb2,0xe4cc,0xaa8b,0x513a,0x2461,0x4849,0x078a,0xa3f5,0x9f3f,0x5fe4,0x468a,0xb7b3,0xc06e,0xf5ac,0x3ec3,0x0292,0x3af9,0x890c,0xef0b,0x3ee2,0xedc2,0x3a0b,0x6bdf,0xfd46,0x609b,0xac98,0x7859,0xa781,0xa45a,0x4d5c,0xe906,0xe303,0xedce,0x4fb7,0x9db5,0xa964,0x41c4,0x437f,0xf990,0xdcdf,0x7277,0xdf17,0x7a12,0xaa77,0xf288,0x0dba,0x2907,0x43cc,0xe680,0x75ea,0xb710,0x7141}, new char[]{0xaa31,0x721e,0xeb08,0x41a0,0xc7f0,0xe4ec,0x530d,0xdcb3,0x6bbe,0x9dcc,0xb851,0xb762,0xfd66,0x4fd5,0xb54f,0x02e1,0xf9f5,0x0f71,0xb793,0x7871,0xa43f,0xc000,0xedaa,0x9844,0x897e,0x4f9b,0xa907,0xdbfb,0x3ea6,0xef78,0x832e,0x5149,0x3ece,0xa793,0x2404,0x46e4,0x3a68,0x45b0,0x0fd3,0xc416,0x9f4b,0x4869,0x07fe,0x5f85,0xf2ea,0xacf4,0x431a,0x3a8a,0x7a3e,0xdf37,0xe362,0xf5c2,0xaafd,0x43a5,0x3775,0xe975,0x0d96,0x69a2,0x2a61,0x60f2,0x7586,0x712d,0xe16d,0x2960,0xa390,0xedb0,0x8784,0x0fba,0xe6ae,0xa7af,0x4d75}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd7b5,0x83cb,0xe35f,0xec4d,0x33dc,0x1960,0x4515,0x8778,0xe7ef,0x3cf5,0x20a3}, new char[]{0xd7d0,0x8716,0x4571,0xec28,0x20d1,0x3cd5,0xe33c,0x1908,0x33b9,0x83b8,0xe79b}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x31fb,0xde7a,0x780e,0xcf54,0x334d,0x9435,0xd9b5,0xc487,0xd200,0x556b,0x042c,0xecc7,0x864a}, new char[]{0x3328,0x3195,0xcf37,0xde12,0xc4e6,0x8624,0x9441,0xd220,0xd9c1,0xeca6,0x044e,0x7862,0x550e}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd66f,0xa700,0x9c60,0x8f84,0x9ce9}, new char[]{0x8fe5,0xa76e,0x9c16,0xd606,0x9c85}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe80e,0x0b92,0x651a,0x6107,0xc329,0x4b5a}, new char[]{0x0bf0,0x4b3f,0x6166,0xe86d,0x6575,0xc347}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x48ce,0x3e8c,0x298f,0x9c6c,0x56a4,0x0c16,0x65fa}, new char[]{0x6589,0x48a6,0x0c63,0x56c8,0x9c07,0x3ee9,0x29fd}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d2c,0xd63a,0x1d42,0x6a8f,0x0395,0x53da,0xdb9f,0x0b4b}, new char[]{0xd64c,0x0b22,0x1d2e,0x3d40,0x03f4,0x53bd,0x6aea,0xdbed}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7284,0x7a2b}, new char[]{0x7a07,0x72a4,0x2cfa,0x1614}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2754,0x8a53,0x8551,0xbab9,0x968e,0xce77,0x675d,0xc92b,0x62c2,0x000a,0x47cc}, new char[]{0xc969,0x47ad,0xbaca,0xce12,0x96ae,0x671b,0x8a3a,0x853f,0x62a6,0x006f,0x2726}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa18c,0x18b6,0x34b4,0x9ed6,0x40fb,0xb893,0x321e,0xb806}, new char[]{0x34f6,0xa1ed,0x9ea5,0xb863,0x323e,0x18d7,0x408f,0xb8b3}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x168c,0xa970,0xb64e}, new char[]{0x16ac,0xb663,0xa950,0x53da}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x77e6,0xbbaf,0x0f28,0x4f23}, new char[]{0x4f61,0xbbce,0x0f5b,0x7783}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcf65}, new char[]{0xcf16,0xb9b4,0xbb84,0xc547}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e97,0x70f3,0xfd2c,0xa89c,0x580a,0x2386}, new char[]{0x4ed5,0x23e7,0xfd5f,0x7096,0xa8bc,0x582a}).toString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7dd1,0x8b74,0x9926,0x5470,0xcc7a,0xc32f,0x019b,0xf937,0x349c,0x56fa}, new char[]{0xf95a,0x01bb,0xc30f,0x7df9,0x34ef,0xcc19,0x9949,0x8b06,0x569f,0x5450}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4316}, new char[]{0x433f,0xdbb2,0xfb9d,0xd3b7}).plainString();
    }
}
