package com.utilitymod.cgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.features.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON GUI loader: config/utilitymod/cgui/home.json builds the whole screen.
 * Hot-reloaded on every open - edit the file, press CTRL+SHIFT+G again.
 * Malformed anything = one chat line, no crash, falls back to the built-in
 * example window.
 *
 * Grammar:
 * {
 *   "title": "home",
 *   "windows": [
 *     {
 *       "title": "Belt", "x": 40, "y": 60, "width": 210,
 *       "widgets": [
 *         {"type": "label", "text": "hello"},
 *         {"type": "module", "name": "Shard Rig"},
 *         {"type": "setting", "module": "Shard Rig", "setting": "Place Delay"},
 *         {"type": "button", "text": "flip", "module": "Shard Rig"},
 *         {"type": "image", "namespace": "utilitymod", "path": "textures/gui/logo.png", "height": 64}
 *       ]
 *     }
 *   ]
 * }
 */
public final class CuiLoader {
    private CuiLoader() {}

    public static CuiScreen load() {
        Path file = file();
        if (!Files.exists(file)) return null;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            if (!parsed.isJsonObject()) {
                chat("[cgui] home.json is not an object - using the built-in window");
                return null;
            }
            return fromJson(parsed.getAsJsonObject());
        } catch (Exception bad) {
            chat("[cgui] home.json failed to load: " + bad.getClass().getSimpleName());
            return null;
        }
    }

    public static CuiScreen fromJson(JsonObject root) {
        String title = string(root, "title", "home");
        CuiScreen screen = new CuiScreen(title);
        if (root.has("windows") && root.get("windows").isJsonArray()) {
            JsonArray wins = root.getAsJsonArray("windows");
            for (JsonElement winEl : wins) {
                if (!winEl.isJsonObject()) continue;
                buildWindow(screen, winEl.getAsJsonObject());
            }
        }
        return screen;
    }

    private static void buildWindow(CuiScreen screen, JsonObject win) {
        String title = string(win, "title", "window");
        float x = num(win, "x", 40);
        float y = num(win, "y", 60);
        float width = num(win, "width", 210);
        Window w = screen.window(title, x, y, width);
        if (!win.has("widgets") || !win.get("widgets").isJsonArray()) return;
        JsonArray widgets = win.getAsJsonArray("widgets");
        for (JsonElement widgetEl : widgets) {
            if (widgetEl.isJsonObject()) buildWidget(w, widgetEl.getAsJsonObject());
        }
    }

    private static void buildWidget(Window w, JsonObject jw) {
        String type = string(jw, "type", "label");
        switch (type) {
            case "label" -> w.add(new Label(string(jw, "text", ""), CuiTheme.TEXT_SIZE, CuiTheme.TEXT, false));
            case "module" -> {
                Feature f = feature(string(jw, "name", ""));
                if (f == null) {
                    chat("[cgui] unknown module: " + string(jw, "name", ""));
                    return;
                }
                w.add(new Toggle("Enabled", f::isEnabled, f::setEnabled));
                for (var s : f.getSettings()) w.add(SettingAdapters.of(s));
            }
            case "setting" -> {
                Feature f = feature(string(jw, "module", ""));
                if (f == null) {
                    chat("[cgui] unknown module: " + string(jw, "module", ""));
                    return;
                }
                String want = string(jw, "setting", "");
                boolean found = false;
                for (var s : f.getSettings()) {
                    if (String.valueOf(s.getName()).equalsIgnoreCase(want)) {
                        w.add(SettingAdapters.of(s));
                        found = true;
                        break;
                    }
                }
                if (!found) chat("[cgui] unknown setting: " + want + " on " + string(jw, "module", ""));
            }
            case "button" -> {
                Feature f = feature(string(jw, "module", ""));
                if (f == null) {
                    chat("[cgui] button needs a module: " + string(jw, "text", ""));
                    return;
                }
                w.add(new Button(string(jw, "text", ""), f::toggle));
            }
            case "image" -> w.add(new Image(null,
                    string(jw, "namespace", "utilitymod"), string(jw, "path", "textures/gui/logo.png"),
                    num(jw, "height", 48)));
            default -> chat("[cgui] skipped widget type: " + type);
        }
    }

    private static Feature feature(String name) {
        if (name.isBlank()) return null;
        for (Module m : ModuleManager.all()) {
            if (m instanceof Feature f && String.valueOf(m.getName()).equalsIgnoreCase(name)) return f;
        }
        return null;
    }
    private static String string(JsonObject o, String key, String fallback) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) {
            String v = o.get(key).getAsString();
            if (!v.isBlank()) return v;
        }
        return fallback;
    }

    private static float num(JsonObject o, String key, float fallback) {
        try {
            return o.has(key) ? o.get(key).getAsFloat() : fallback;
        } catch (Exception bad) {
            return fallback;
        }
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config").resolve("utilitymod").resolve("cgui").resolve("home.json");
    }

    private static void chat(String line) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(line), false);
        }
    }
}
