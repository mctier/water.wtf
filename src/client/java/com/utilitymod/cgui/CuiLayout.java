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
            UtilityMod.LOGGER.warn(LT.L1, bad.toString());
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
                if (parsed.isJsonObject() && parsed.getAsJsonObject().has(LT.L2)
                        && parsed.getAsJsonObject().get(LT.L2).isJsonObject()) {
                    return parsed.getAsJsonObject().get(LT.L2).getAsJsonObject();
                }
            }
        } catch (Exception bad) {
            UtilityMod.LOGGER.warn(LT.L3, bad.toString());
        }
        return new JsonObject();
    }

    private static String titleOf(Window win) {
        return win.getTitle();
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve(LT.L4).resolve(LT.L5).resolve(LT.L6);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3350,0x585c,0x4ca3,0x2339,0x905e,0xc194,0x35bf,0x2bed,0x9ffe,0xfdca,0xad78,0x8ee8,0x35c1,0x7ed9,0xbb69,0xd45c,0xb767,0x0942,0x83b5,0x11ab,0x5194,0x2dd1,0x01ff,0x5cef,0x4136,0xa2d9,0x6e58,0x2fa8,0xb175}, new char[]{0x2bb6,0x235a,0x83d2,0xb712,0x8e81,0x091f,0x7ef9,0x333c,0x5c8e,0x35b8,0x4ccc,0x4143,0x2fdc,0xc1b4,0x6e2b,0x9f9f,0xbb1f,0xa2bc,0x01df,0x35d9,0xfdab,0x51fd,0xad14,0x5839,0xd438,0x1191,0x2df1,0x9025,0xb108}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf2a8,0x9273,0x1e40,0xfa0b,0xd559,0xbfd9,0x0325}, new char[]{0xfa78,0xf2cb,0xd52b,0x0340,0x1e25,0x921d,0xbfaa}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe012,0x10a3,0xcff4,0x641e,0xc443,0xc4da,0x3b26,0x7c84,0x1e19,0xbf6f,0xe871,0x495b,0x1068,0x63d9,0x9740,0xb69e,0xb29c,0x455c,0x504c,0x40f9,0x668c,0xf247,0xe6c0,0xad8a,0xb1f6,0xfc47,0x0907,0xb3fd,0xef0c}, new char[]{0x10f8,0x502f,0x453b,0x3b53,0xcf9d,0xb3a0,0xb6be,0x1004,0xe6a1,0x4922,0xef63,0x63ac,0xadfe,0xfc67,0xb19a,0x972f,0xbf0e,0x409d,0x643e,0xc425,0xe073,0xf22e,0x096b,0x7ce1,0xb2f8,0x66b6,0x1e39,0xe80a,0xc4a7}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x27ab,0xa86b,0xd038,0xd137,0x0153,0xc060}, new char[]{0xa808,0xd158,0xc00e,0x0135,0xd051,0x27cc}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda77,0x45b1,0xfa66,0xa1f1,0xe0e5,0xe7bf,0xb562,0x20a5,0x4329,0x1678}, new char[]{0xe090,0xfa12,0x4340,0xe7d3,0xa198,0x160c,0xb51b,0xda1a,0x20ca,0x45d5}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x223e,0x8d67,0x9bf2,0x4e07,0x00be,0xa3a9,0xdf7d,0x5104,0x9c3e,0xc141,0x0c21,0x542c,0xfe90,0x8df2,0xc44a}, new char[]{0xc122,0x2259,0x4e72,0xdf14,0x5473,0x8d0b,0x00df,0xc433,0x516b,0x9c4b,0x0c55,0xa387,0xfef4,0x8d93,0x9b86}).plainString();
    }
}
