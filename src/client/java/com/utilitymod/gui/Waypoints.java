package com.utilitymod.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saved waypoints + the base-finder history (chunks the Sus/Prime finders flagged). */
public final class Waypoints {
    public record Waypoint(String name, int x, int y, int z, String dimension, int color) {}

    public record Finding(String source, int chunkX, int chunkZ, long time) {}

    public static final List<Waypoint> LIST = new ArrayList<>();
    private static final Map<Long, Finding> HISTORY = new LinkedHashMap<>();
    private static final int HISTORY_CAP = 200;
    private static final int[] COLORS = {0xFFF472B6, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFA78BFA, 0xFFF87171};

    private Waypoints() {}

    public static String dimension() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    public static void add(String name, int x, int y, int z) {
        LIST.add(new Waypoint(name, x, y, z, dimension(), COLORS[LIST.size() % COLORS.length]));
        GuiConfig.save();
    }

    public static void addHere() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        add(LT.L1 + (LIST.size() + 1), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
    }

    public static void remove(Waypoint w) {
        LIST.remove(w);
        GuiConfig.save();
    }

    // ---- finder history (fed by ChunkOverlay) -------------------------------------------------

    public static synchronized void found(String source, long chunkKey) {
        if (HISTORY.containsKey(chunkKey)) return;
        int cx = (int) chunkKey, cz = (int) (chunkKey >> 32);
        HISTORY.put(chunkKey, new Finding(source, cx, cz, System.currentTimeMillis()));
        if (HISTORY.size() > HISTORY_CAP) {
            Iterator<Long> it = HISTORY.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    public static synchronized List<Finding> history() {
        List<Finding> out = new ArrayList<>(HISTORY.values());
        java.util.Collections.reverse(out); // newest first
        return out;
    }

    public static synchronized void clearHistory() {
        HISTORY.clear();
    }

    // ---- persistence ----------------------------------------------------------------------------

    public static JsonArray save() {
        JsonArray a = new JsonArray();
        for (Waypoint w : LIST) {
            JsonObject o = new JsonObject();
            o.addProperty(LT.L2, w.name());
            o.addProperty(LT.L3, w.x());
            o.addProperty(LT.L4, w.y());
            o.addProperty(LT.L5, w.z());
            o.addProperty(LT.L6, w.dimension());
            o.addProperty(LT.L7, w.color());
            a.add(o);
        }
        return a;
    }

    public static void load(JsonArray a) {
        LIST.clear();
        if (a == null) return;
        for (var e : a) {
            try {
                JsonObject o = e.getAsJsonObject();
                LIST.add(new Waypoint(o.get(LT.L2).getAsString(), o.get(LT.L3).getAsInt(), o.get(LT.L4).getAsInt(),
                        o.get(LT.L5).getAsInt(), o.get(LT.L6).getAsString(), o.get(LT.L7).getAsInt()));
            } catch (Exception ignored) {
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08c9,0x0a5f,0xb54f,0xebc5,0x11f1,0x8b28,0x4312,0x02ae,0x6929}, new char[]{0x697e,0x1190,0x436b,0xb53f,0xebaa,0x8b41,0x08a7,0x0a2b,0x028e}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2d7f,0x7bb6,0xb7b0,0xb217}, new char[]{0xb7de,0x7bd7,0xb27a,0x2d1a}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b96}, new char[]{0x1bee,0x089e,0xba5a,0xe471}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x23d7}, new char[]{0x23ae,0xe5a3,0xa485,0x4cd6}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x61ae}, new char[]{0x61d4,0xf6d6,0xb1b0,0x6ebe}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9cbc,0xaee4,0x5c64}, new char[]{0xae80,0x9cd5,0x5c09,0xd69c}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3644,0xb803,0x51c4,0x3366,0xba9c}, new char[]{0xb860,0xbaf3,0x330a,0x362b,0x51b6}).plainString();
    }
}
