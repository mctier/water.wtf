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
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BellBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds villages in loaded chunks from what the client can see: bells (every village has one at
 * its meeting point), beds, lecterns, villagers and iron golems. Evidence within 64 blocks is
 * grouped; a group strong enough is a village, marked in the world and announced once.
 */
public class VillageFinder extends Feature {
    private final IntSetting range = new IntSetting(LT.L1, 12, 2, 32);
    private final BoolSetting notify = new BoolSetting(LT.L2, true);
    private final BoolSetting autoWaypoint = new BoolSetting(LT.L3, false);
    private final BoolSetting showMarker = new BoolSetting(LT.L4, true);

    private record Village(BlockPos center, boolean bell, int beds, int villagers, int score) {}

    private List<Village> villages = List.of();
    private final Set<Long> announced = new HashSet<>();
    private int cooldown;

    public VillageFinder() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd12c,0x0b39,0xb168,0x37df,0x962a,0x2274,0xeb85,0xfcf5,0x828d,0xbc05,0xa8a2,0x1cb4,0x11fe,0x6553}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04a3,0x6d7b,0x7a47,0x6553,0x1857,0xd979,0xe0ad,0x2276,0xe77a,0xb12f,0x478a,0x37cd,0x0c94,0xc5cf,0xd13c,0x93ad,0xb983,0xef54,0x3b7d,0x5f60,0x3354,0xac4b,0x15c8,0xcdfa,0xa1af,0xbc0d,0x66ef,0x0b2a,0xf052,0xeb86,0x82c4,0x3ff1,0xfcf4,0xdad4,0x4a03,0x7ec7,0x2a96,0xa420,0xf40e,0x76f4,0x8ae5,0x7228,0x11f1,0xc967,0xc158,0xa8a2,0x1c9e,0xb514,0x2605,0x4fe2,0x8664,0x9bff,0xf897,0x0024,0x962f,0x546b,0x50d8,0x4308,0x9e55,0xd2b1,0x58ad}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406}).toString(), Category.FINDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        villages = List.of();
        announced.clear();
    }

    @Override
    public CharSequence concat() {
        return String.valueOf(villages.size());
    }

    private record Clue(double x, double y, double z, int weight, int kind) {} // kind 0 bell 1 bed 2 lectern 3 villager 4 golem

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || --cooldown > 0) return;
        cooldown = 40; // every 2s
        List<Clue> clues = new ArrayList<>();
        ChunkPos c = mc.player.getChunkPos();
        int r = range.getValue();
        for (int cx = c.x - r; cx <= c.x + r; cx++) {
            for (int cz = c.z - r; cz <= c.z + r; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if (be instanceof BellBlockEntity) clues.add(new Clue(p.getX(), p.getY(), p.getZ(), 6, 0));
                    else if (be instanceof BedBlockEntity) clues.add(new Clue(p.getX(), p.getY(), p.getZ(), 1, 1));
                    else if (be instanceof LecternBlockEntity) clues.add(new Clue(p.getX(), p.getY(), p.getZ(), 1, 2));
                }
            }
        }
        {
            double maxD = (r + 1) * 16.0;
            for (Entity e : mc.world.getEntities()) {
                if (Math.abs(e.getX() - mc.player.getX()) > maxD || Math.abs(e.getZ() - mc.player.getZ()) > maxD) continue;
                if (e instanceof VillagerEntity) clues.add(new Clue(e.getX(), e.getY(), e.getZ(), 2, 3));
                else if (e instanceof IronGolemEntity) clues.add(new Clue(e.getX(), e.getY(), e.getZ(), 3, 4));
            }
        }

        // greedy clustering: bells seed clusters first, then everything else joins the nearest within 64
        clues.sort((a, b) -> Integer.compare(a.kind(), b.kind()));
        List<List<Clue>> groups = new ArrayList<>();
        List<double[]> centers = new ArrayList<>();
        for (Clue k : clues) {
            int best = -1;
            double bestD = 64 * 64;
            for (int i = 0; i < centers.size(); i++) {
                double[] ce = centers.get(i);
                double dx = ce[0] - k.x(), dz = ce[1] - k.z(), d = dx * dx + dz * dz;
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best < 0) {
                groups.add(new ArrayList<>(List.of(k)));
                centers.add(new double[] {k.x(), k.z()});
            } else {
                groups.get(best).add(k);
            }
        }
        List<Village> out = new ArrayList<>();
        for (List<Clue> g : groups) {
            int score = 0, beds = 0, vill = 0;
            Clue bell = null;
            double sx = 0, sy = 0, sz = 0;
            for (Clue k : g) {
                score += k.weight();
                if (k.kind() == 0 && bell == null) bell = k;
                if (k.kind() == 1) beds++;
                if (k.kind() == 3) vill++;
                sx += k.x(); sy += k.y(); sz += k.z();
            }
            // no villagers = no village (ruins, abandoned/zombie villages, player builds with a bell)
            if (vill < 1) continue;
            if (score < 8 || (bell == null && beds / 2 + vill < 4)) continue;
            BlockPos center = bell != null ? BlockPos.ofFloored(bell.x(), bell.y(), bell.z())
                    : BlockPos.ofFloored(sx / g.size(), sy / g.size(), sz / g.size());
            out.add(new Village(center, bell != null, beds / 2, vill, score)); // beds have 2 halves
        }
        villages = out;

        for (Village v : out) {
            long key = ChunkPos.toLong(v.center().getX() >> 4, v.center().getZ() >> 4);
            boolean seen = false;
            for (long a : announced) {
                if (Math.abs(ChunkPos.getPackedX(a) - (v.center().getX() >> 4)) <= 4
                        && Math.abs(ChunkPos.getPackedZ(a) - (v.center().getZ() >> 4)) <= 4) { seen = true; break; }
            }
            if (seen) continue;
            announced.add(key);
            String where = v.center().getX() + LT.L5 + v.center().getZ();
            if (notify.getValue()) Notifications.push(LT.L6, LT.L7 + where + LT.L8 + Math.round(dist(v)) + LT.L9, Notifications.Type.INFO);
            if (autoWaypoint.getValue()) Waypoints.add(LT.L10, v.center().getX(), v.center().getY(), v.center().getZ());
        }
    }

    private double dist(Village v) {
        return Math.sqrt(mc.player.squaredDistanceTo(v.center().getX() + 0.5, v.center().getY(), v.center().getZ() + 0.5));
    }

    @EventSubscribe
    private void onRender(EventRender2DGui event) {
        Render3D frame = EchoEvents.lastFrame();
        if (!showMarker.getValue() || frame == null || mc.player == null || mc.options.hudHidden) return;
        DrawContext ctx = event.getContext();
        for (Village v : villages) {
            Vector4f clip = frame.toClip(v.center().getX() + 0.5, v.center().getY() + 3, v.center().getZ() + 0.5);
            if (clip.w() <= 0.05f) continue;
            float[] p = Render3D.toGui(clip);
            int col = v.bell() ? 0xFF7DD87D : 0xFFE0C060;
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(p[0], p[1]);
            m.rotate((float) Math.toRadians(45));
            Draw.rect(ctx, -5, -5, 10, 10, 2, Colors.withAlpha(0xFF000000, 0.45f));
            Draw.rect(ctx, -4, -4, 8, 8, 1, col);
            m.popMatrix();
            String label = LT.L11 + Math.round(dist(v)) + LT.L12;
            String sub = (v.villagers() > 0 ? v.villagers() + LT.L13 : "") + (v.beds() > 0 ? v.beds() + LT.L14 : "");
            float tw = Math.max(Text.width(label, 7f), Text.width(sub, 6f)) + 10;
            int h = sub.isEmpty() ? 11 : 19;
            Draw.rect(ctx, Math.round(p[0] - tw / 2), Math.round(p[1] - 10 - h), Math.round(tw), h, 4, 0xB0101216);
            Text.drawCenteredX(ctx, label, p[0], p[1] - 4.5f - h, 7f, col);
            if (!sub.isEmpty()) Text.drawCenteredX(ctx, sub, p[0], p[1] - 4.5f - h + 9, 6f, 0xFFCCCCCC);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a1e,0xcf1d,0x3973,0xb208,0xeceb,0x5dc7,0x4c93,0x9528,0xc19f,0xb734,0x0a79,0x177d,0xc692,0x7e24}, new char[]{0x7e76,0x4cf2,0x0a17,0xc1f8,0xb751,0x3953,0x9500,0x6a7d,0x1715,0x5db2,0xb266,0xc6f9,0xcf6e,0xecc2}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2388,0xeb7f,0x5b76,0x6bec,0x8415,0xf1fe}, new char[]{0xf1b0,0x5b19,0x23fc,0x847c,0xeb19,0x6b95}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7e0b,0xb5a9,0xcc5a,0xcfa7,0xe9ec,0x6c84,0xd781,0x37a0,0xd889,0xfb1b,0xb1ae,0x21e8,0x22a3}, new char[]{0xd8c8,0x22d6,0x7e7f,0xfb74,0xb18e,0xcff0,0xd7e0,0x6cfd,0x2198,0x37cf,0xe985,0xcc34,0xb5dd}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe1f4,0x5847,0x4f78,0x663f,0xeaf3,0xa247,0x28ed,0x6519,0xf17d,0x9ec4,0x33fd}, new char[]{0xa214,0xe19c,0x6650,0x5830,0x4f58,0x6554,0x339c,0x9eb6,0xea98,0xf118,0x289f}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcc60,0xb667}, new char[]{0xb64b,0xcc40,0xa2ee,0xc7da}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x39b9,0x196a,0xa0a8,0xf055,0x976c,0xf098,0x904e,0xb5ab,0xecc9,0x8cae,0xc52e,0xad34,0xd9f2,0x29c5}, new char[]{0xf003,0xad5d,0x8cc2,0x9022,0xf0f9,0xb5cc,0x9709,0x194a,0xec8f,0x39d0,0xd99c,0x29a1,0xc54b,0xa0da}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe4ba,0xcd15,0xd960,0x1929,0x043a,0x9bdb,0xe841,0x3104,0xd050,0x0025,0x2f3e}, new char[]{0xd936,0xe828,0xd03c,0xcd79,0x2f5f,0xe4dd,0x3161,0x1909,0x0044,0x9baf,0x041a}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6e89,0x1b64}, new char[]{0x1b44,0x6ea1,0xdf4a,0xba15}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x51bd,0xd1d4}, new char[]{0xd1b9,0x5194,0x0867,0xf0cf}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6127,0x5e87,0xe819,0xeb34,0x6e10,0x8131,0x9c5d}, new char[]{0x6171,0xeb5d,0x5eeb,0xe875,0x6e71,0x9c3a,0x8154}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x095d,0x072f,0xf3b4,0xe06e,0x57ec,0x755d,0xf8e0,0xfc6c,0x2f6e}, new char[]{0xe038,0xf3dd,0x0931,0x2f02,0xf881,0x578b,0x7538,0xfc4c,0x070f}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7dbd}, new char[]{0x7dd0,0xcc29,0xa8b3,0x7d51}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa48d,0x6333,0x2a22,0xda8d,0xb5d3,0xed55,0x59ee,0x575f,0x782f,0x1186,0x7089,0xd730}, new char[]{0xa4ad,0x5998,0x2a4b,0x7843,0x5733,0xd751,0x6354,0xed30,0x11f4,0xb5a0,0xdaad,0x70a9}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd9d6,0xa0c1,0x578b,0x3eab,0xba39}, new char[]{0xa0e1,0xd9b4,0x3ece,0x57ef,0xba4a}).plainString();
    }
}
