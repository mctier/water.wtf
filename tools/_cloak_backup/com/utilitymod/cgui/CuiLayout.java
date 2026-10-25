package com.utilitymod.cgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.utilitymod.UtilityMod;
import com.utilitymod.gui.GuiConfig;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Window session store: title-keyed position/width/collapsed per screen,
 * encoded with the config codec (gzip + mask, same file family as the
 * module config). Applied when a screen rebuilds, saved when the screen
 * leaves (CuiScreen.removed). Broken store = empty session, never a crash.
 */
public final class CuiLayout {
    private static final Gson GSON = new GsonBuilder().create();
    private static JsonObject state;
    private static boolean dirty;

    private CuiLayout() {}

    public static void apply(String screenKey, Window win) {
        JsonArray v = stored(screenKey, titleOf(win));
        if (v == null || v.size() < 4) return;
        try {
            win.x = v.get(0).getAsFloat();
            win.y = v.get(1).getAsFloat();
            win.w = Math.max(140, v.get(2).getAsFloat());
            if (v.get(3).getAsInt() != 0 && !win.isCollapsed()) win.toggleCollapsed();
        } catch (Exception bad) {
            // bad row - skip, keep constructor defaults
        }
    }

    public static void remember(String screenKey, Window win) {
        JsonObject screens = root();
        JsonObject screenObj;
        if (screens.has(screenKey) && screens.get(screenKey).isJsonObject()) {
            screenObj = screens.get(screenKey).getAsJsonObject();
        } else {
            screenObj = new JsonObject();
            screens.add(screenKey, screenObj);
        }
        JsonArray row = new JsonArray();
        row.add(win.x);
        row.add(win.y);
        row.add(Math.max(140, win.w));
        row.add(win.isCollapsed() ? 1 : 0);
        screenObj.add(titleOf(win), row);
        dirty = true;
    }

    public static void save() {
        if (!dirty || state == null) return;
        try {
            Files.createDirectories(file().getParent());
            Files.write(file(), GuiConfig.encodeLayout(GSON.toJson(state)));
            dirty = false;
        } catch (Exception bad) {
            UtilityMod.LOGGER.warn("[cgui] layout save failed: {}", bad.toString());
        }
    }

    private static JsonArray stored(String screenKey, String winTitle) {
        JsonObject root = root();
        if (!root.has(screenKey) || !root.get(screenKey).isJsonObject()) return null;
        JsonObject screenObj = root.get(screenKey).getAsJsonObject();
        if (!screenObj.has(winTitle) || !screenObj.get(winTitle).isJsonArray()) return null;
        return screenObj.getAsJsonArray(winTitle);
    }

    private static JsonObject root() {
        if (state == null) state = loadState();
        return state;
    }

    private static JsonObject loadState() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                String json = GuiConfig.decodeLayout(Files.readAllBytes(f));
                JsonElement parsed = JsonParser.parseString(json);
                if (parsed.isJsonObject() && parsed.getAsJsonObject().has("screens")
                        && parsed.getAsJsonObject().get("screens").isJsonObject()) {
                    return parsed.getAsJsonObject().get("screens").getAsJsonObject();
                }
            }
        } catch (Exception bad) {
            UtilityMod.LOGGER.warn("[cgui] layout load failed: {}", bad.toString());
        }
        return new JsonObject();
    }

    private static String titleOf(Window win) {
        return win.getTitle();
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config").resolve("utilitymod").resolve("cgui_layout.dat");
    }
}
