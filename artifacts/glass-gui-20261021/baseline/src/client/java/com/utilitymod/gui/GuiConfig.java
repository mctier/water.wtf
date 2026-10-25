package com.utilitymod.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.utilitymod.UtilityMod;
import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import dev.koifih.client.input.Keybinds;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Tooltips;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.ui.Units;
import dev.koifih.client.util.Lang;
import net.minecraft.client.MinecraftClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persists client settings and module states to
 * {@code .minecraft/config/utilitymod.dat} (gzipped + XOR-masked JSON, so the
 * file bytes don't ship a readable copy of module/setting names to a disk
 * scanner). Loaded once at init, saved whenever the wing closes. Legacy
 * {@code utilitymod.json} is auto-migrated on first run and then deleted.
 */
public final class GuiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Mask matches ProtectedText / HeapCloak so the whole mod uses one stream:
    // mask[i] = (0x1E ^ (i * 0x37)) & 0xFF
    private static final int MASK_A = 0x1E;
    private static final int MASK_STEP = 0x37;

    // 4-byte magic so load() can tell "my own .dat" from a random file.
    private static final byte[] MAGIC = {(byte) 0x50, (byte) 0x4F, (byte) 0x54, (byte) 0x01}; // "POT\x01"

    private static volatile boolean dirty;

    private GuiConfig() {}

    /** A setting changed; saved with the next GUI close (or call {@link #save()}). */
    public static void markDirty() {
        dirty = true;
    }

    // ---- profiles: named copies of the whole config ---------------------------------------------

    private static Path profileDir() {
        return file().getParent().resolve(LT.L1);
    }

    /** Opens the profiles folder in the OS file manager (creates it first). */
    public static void openProfileFolder() {
        try {
            Files.createDirectories(profileDir());
            net.minecraft.util.Util.getOperatingSystem().open(profileDir());
        } catch (Exception ignored) {
        }
    }

    public static java.util.List<String> profiles() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (var s = Files.list(profileDir())) {
            s.filter(p -> p.toString().endsWith(LT.L2))
                    .forEach(p -> out.add(p.getFileName().toString().replaceFirst(LT.L3, "")));
        } catch (Exception ignored) {
        }
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static String safe(String name) {
        return name.replaceAll(LT.L4, "").trim();
    }

    public static boolean saveProfile(String name) {
        String n = safe(name);
        if (n.isEmpty()) return false;
        try {
            save();
            Files.createDirectories(profileDir());
            Files.copy(file(), profileDir().resolve(n + LT.L2), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean loadProfile(String name) {
        try {
            Path p = profileDir().resolve(safe(name) + LT.L2);
            if (!Files.exists(p)) return false;
            Files.copy(p, file(), StandardCopyOption.REPLACE_EXISTING);
            load();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void deleteProfile(String name) {
        try {
            Files.deleteIfExists(profileDir().resolve(safe(name) + LT.L2));
        } catch (Exception ignored) {
        }
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(LT.L5).resolve(LT.L6);
    }

    /** Legacy pre-v1.0.4 JSON config path. If present, read-once and migrate. */
    private static Path legacyFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(LT.L5).resolve(LT.L7);
    }

    // ---- byte-level codec: json <-> dat --------------------------------------------------------

    /** Package-private hook: encode JSON text to the .dat byte format. */
    static byte[] encodeDat(String json) throws Exception {
        return encode(json);
    }

    /** Package-private hook: decode .dat bytes back to the JSON text they wrap. */
    static String decodeDat(byte[] bytes) throws Exception {
        return decode(bytes);
    }

    /** Public codec for the cgui layout store - one mask/gzip implementation only. */
    public static byte[] encodeLayout(String json) throws Exception {
        return encode(json);
    }

    public static String decodeLayout(byte[] bytes) throws Exception {
        return decode(bytes);
    }

    private static byte[] encode(String json) throws Exception {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream gz = new ByteArrayOutputStream(raw.length / 3);
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(raw);
        }
        byte[] compressed = gz.toByteArray();

        byte[] result = new byte[MAGIC.length + compressed.length];
        System.arraycopy(MAGIC, 0, result, 0, MAGIC.length);
        for (int i = 0; i < compressed.length; i++) {
            result[MAGIC.length + i] = (byte) (compressed[i] ^ ((MASK_A ^ (i * MASK_STEP)) & 0xFF));
        }
        return result;
    }

    private static String decode(byte[] bytes) throws Exception {
        if (bytes.length < MAGIC.length) throw new IllegalArgumentException(LT.L8);
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) throw new IllegalArgumentException(LT.L9);
        }
        int payloadLen = bytes.length - MAGIC.length;
        byte[] compressed = new byte[payloadLen];
        for (int i = 0; i < payloadLen; i++) {
            compressed[i] = (byte) (bytes[MAGIC.length + i] ^ ((MASK_A ^ (i * MASK_STEP)) & 0xFF));
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- top-level load / save -----------------------------------------------------------------

    public static void load() {
        com.utilitymod.echo.features.impl.render.Notifications.muted = true;
        try {
            loadInner();
        } finally {
            com.utilitymod.echo.features.impl.render.Notifications.muted = false;
        }
    }

    private static void loadInner() {
        try {
            Path f = file();
            String json = null;

            if (Files.exists(f)) {
                try {
                    json = decode(Files.readAllBytes(f));
                } catch (Exception bad) {
                    UtilityMod.LOGGER.warn(LT.L10, bad.toString());
                }
            }

            if (json == null) {
                Path legacy = legacyFile();
                if (Files.exists(legacy)) {
                    json = Files.readString(legacy);
                    UtilityMod.LOGGER.info(LT.L11);
                }
            }

            if (json == null) return;

            JsonObject o = GSON.fromJson(json, JsonObject.class);
            if (o == null) return;
            if (o.has(LT.L12)) Theme.setMode(Theme.Mode.valueOf(o.get(LT.L12).getAsString()));
            if (o.has(LT.L13)) Theme.setAccent(o.get(LT.L13).getAsInt());
            if (o.has(LT.L14)) Lang.set(Lang.Language.valueOf(o.get(LT.L14).getAsString()));
            if (o.has(LT.L15)) UiScale.set(UiScale.valueOf(o.get(LT.L15).getAsString()));
            if (o.has(LT.L16)) Units.set(Units.valueOf(o.get(LT.L16).getAsString()));
            if (o.has(LT.L17)) Tooltips.set(Tooltips.valueOf(o.get(LT.L17).getAsString()));
            if (o.has(LT.L18)) Keybinds.setOpenKeyCode(o.get(LT.L18).getAsInt());
            if (o.has(LT.L19)) UiSounds.enabled = o.get(LT.L19).getAsBoolean();
            if (o.has(LT.L20) && o.get(LT.L20).isJsonArray()) {
                for (var e : o.getAsJsonArray(LT.L20)) com.utilitymod.echo.Echo.friendManager.add(e.getAsString());
            }
            if (o.has(LT.L21) && o.get(LT.L21).isJsonObject()) ModuleBinds.load(o.getAsJsonObject(LT.L21));
            if (o.has(LT.L22) && o.get(LT.L22).isJsonArray()) Waypoints.load(o.getAsJsonArray(LT.L22));
            if (o.has(LT.L23)) ToggleSounds.enabled = o.get(LT.L23).getAsBoolean();
            if (o.has(LT.L24)) ToggleSounds.soupPack = o.get(LT.L24).getAsBoolean();
            if (o.has(LT.L25)) Welcome.guideDone = o.get(LT.L25).getAsBoolean();
            if (o.has(LT.L26)) FrameManager.animatedBg = o.get(LT.L26).getAsBoolean();
            if (o.has(LT.L27) && o.get(LT.L27).isJsonArray()) CustomThemes.load(o.getAsJsonArray(LT.L27));
            if (o.has(LT.L28)) {
                try { FrameManager.style = FrameManager.Style.valueOf(o.get(LT.L28).getAsString()); } catch (Exception ignored) {}
            }
            if (o.has(LT.L29)) {
                JsonObject all = o.getAsJsonObject(LT.L29);
                for (Module m : ModuleManager.all()) {
                    if (!(m instanceof com.utilitymod.echo.features.Feature em) || !all.has(m.getName())) continue;
                    JsonObject vals = all.getAsJsonObject(m.getName());
                    for (com.utilitymod.echo.features.settings.Setting st : em.getSettings()) {
                        if (!vals.has(st.getName())) continue;
                        try {
                            com.utilitymod.echo.features.settings.SettingIO.load(st, vals.get(st.getName()).getAsString());
                        } catch (Throwable bad) {
                            UtilityMod.LOGGER.warn(LT.L30, m.getName(), st.getName());
                        }
                    }
                }
            }
            if (o.has(LT.L31)) com.utilitymod.echo.hud.HudManager.load(o.getAsJsonObject(LT.L31));
            if (o.has(LT.L32) && o.get(LT.L32).isJsonObject()) com.utilitymod.anubis.AnubisBridge.load(o.getAsJsonObject(LT.L32));
            if (o.has(LT.L33)) {
                JsonObject mods = o.getAsJsonObject(LT.L33);
                for (Module m : ModuleManager.all()) {
                    if (mods.has(m.getName())) m.setEnabled(mods.get(m.getName()).getAsBoolean());
                }
            }

            // Migration complete: persist as .dat and remove the legacy JSON.
            Path legacy = legacyFile();
            if (Files.exists(legacy)) {
                try {
                    save();
                    Files.deleteIfExists(legacy);
                } catch (Exception ignored) {}
            }
        } catch (Throwable t) {
            UtilityMod.LOGGER.warn(LT.L34, t.toString());
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty(LT.L12, Theme.mode().name());
            o.addProperty(LT.L13, Theme.accentRgb());
            o.addProperty(LT.L14, Lang.current().name());
            o.addProperty(LT.L15, UiScale.current().name());
            o.addProperty(LT.L16, Units.current().name());
            o.addProperty(LT.L17, Tooltips.current().name());
            o.addProperty(LT.L18, Keybinds.openKeyCode());
            o.addProperty(LT.L19, UiSounds.enabled);
            o.addProperty(LT.L28, FrameManager.style.name());
            o.add(LT.L21, ModuleBinds.save());
            o.add(LT.L22, Waypoints.save());
            o.addProperty(LT.L23, ToggleSounds.enabled);
            o.addProperty(LT.L24, ToggleSounds.soupPack);
            o.addProperty(LT.L25, Welcome.guideDone);
            o.addProperty(LT.L26, FrameManager.animatedBg);
            o.add(LT.L27, CustomThemes.save());
            com.google.gson.JsonArray friends = new com.google.gson.JsonArray();
            for (String f : com.utilitymod.echo.Echo.friendManager.all()) friends.add(f);
            o.add(LT.L20, friends);
            JsonObject mods = new JsonObject();
            for (Module m : ModuleManager.all()) mods.addProperty(m.getName(), m.isEnabled());
            o.add(LT.L33, mods);
            JsonObject all = new JsonObject();
            for (Module m : ModuleManager.all()) {
                if (!(m instanceof com.utilitymod.echo.features.Feature em) || em.getSettings().isEmpty()) continue;
                JsonObject vals = new JsonObject();
                for (com.utilitymod.echo.features.settings.Setting st : em.getSettings()) {
                    String v = com.utilitymod.echo.features.settings.SettingIO.save(st);
                    if (v != null) vals.addProperty(st.getName(), v);
                }
                all.add(m.getName(), vals);
            }
            o.add(LT.L29, all);
            o.add(LT.L31, com.utilitymod.echo.hud.HudManager.save());
            o.add(LT.L32, com.utilitymod.anubis.AnubisBridge.save());
            dirty = false;
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.write(f, encode(GSON.toJson(o)));
        } catch (Throwable t) {
            UtilityMod.LOGGER.warn(LT.L35, t.toString());
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x10c8,0x9977,0x4c1c,0xf9e3,0xe80e,0x3509,0xd9d2,0x28a7,0x355d,0x64b5,0x155d,0x5101,0xdb2a,0x62a6,0xceaa,0x520a,0x6618,0xfc54,0x3330}, new char[]{0xd9a7,0x62d2,0x6671,0x991b,0x28ce,0x3344,0x5273,0x1530,0xe861,0xf987,0x512c,0xfc24,0x64c7,0x3566,0x4c7a,0xdb43,0xcec6,0x3538,0x10bb}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x79a7,0x8073,0xa794,0x3615}, new char[]{0x363b,0xa7f0,0x79c6,0x8007}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b9e,0x08c1,0xaf09,0x010a,0x118b,0xbabb}, new char[]{0x089d,0xba95,0xaf6d,0x6bff,0x11ff,0x012e}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2038,0x3a37,0xb329,0x4f8c,0x6271,0x28b8,0x7802,0x2146,0x96fd,0xfff9,0xf8be,0xd0c3,0x39ce,0x0346,0x2d46}, new char[]{0x3995,0x2d18,0x28f9,0x4fa1,0xd099,0x6210,0xf893,0xb353,0x3a07,0x036b,0xffc0,0x2018,0x2119,0x782f,0x96a0}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0a22,0x87a3,0x1aaa,0x741f,0x5ad9,0x71d6}, new char[]{0x747c,0x1ac5,0x0a4c,0x87c5,0x5ab0,0x71b1}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3118,0x3649,0x45f7,0x6e32,0xb0ae,0x3ccc,0x9156,0x15a0,0xa94e,0xf085,0x21da,0x9b72,0x8d98,0x6235}, new char[]{0x9b07,0x6241,0xa927,0x3174,0x21b3,0x8dec,0x6e4b,0xb0c3,0x4598,0x362d,0x9178,0x15c4,0x3cad,0xf0f1}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfaa0,0x5854,0x468f,0xa72d,0xa42d,0x15bb,0xff23,0x0831,0x2c6b,0x5ca8,0x6bc1,0x4d6f,0xf8be,0x7e1d,0x1482}, new char[]{0x0844,0xa759,0xf8d7,0x46e3,0xa444,0x4d1b,0x15c2,0x6bac,0x14ed,0x5830,0xfa8e,0x7e77,0x2c18,0x5cc7,0xff4d}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3fda,0x444d,0x64ca,0x7de8,0x2861,0x696e,0xe1ff,0x265a,0x3e84}, new char[]{0xe18b,0x2635,0x3fb5,0x7dc8,0x3ef7,0x4425,0x280e,0x64b8,0x691a}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7e91,0xf9c8,0xd0fc,0xbc82,0x15f5,0xe6c5,0xb873,0x5de0,0xf448}, new char[]{0xb811,0x1594,0xf9ac,0xf468,0x5d8d,0xd09d,0x7ef6,0xe6ac,0xbce1}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a3a,0xbe3f,0x4b91,0x28a5,0xfb59,0x383b,0xf1ad,0x1c41,0xa934,0x0697,0xa975,0xe6bd,0xeb37,0x1e77,0xb881,0x4768,0x77eb,0xd34a,0x5d77,0x6dec,0x777e,0x3d3a,0xb501,0xb954,0x7cfa,0x348f,0x307e,0x1f88,0xaa3b,0x5280,0x8fbb,0x04cb,0x105d,0xbd41,0xc44a,0x2680}, new char[]{0x3d61,0xf1f8,0x779f,0xb93d,0xe6d1,0x8fd2,0x471c,0x1e0e,0x52cd,0x26ef,0x6d88,0x1c1c,0x1fa8,0x4bd2,0xb56e,0x34e1,0x5d11,0x06fe,0xbd26,0x5a1a,0xeb19,0xd32e,0xb8e0,0xaa4f,0x7cda,0xfb3a,0x3854,0xc438,0xbe4d,0x28d0,0x04bb,0xa901,0xa90e,0x305e,0x7705,0x1020}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x47a4,0x72f7,0x2f81,0x1a95,0xf430,0x8292,0xff83,0xa63e,0xdd52,0x5a7a,0x5058,0x116b,0xaf29,0xc564,0xff0a,0xdf88,0xea13,0xf120,0x49b3,0x3121,0xd5a5,0x2f12,0x1262,0x46cd,0xe42d,0x850f,0x8f2c,0xd81f,0x5fb1,0x3e35,0xd4a5,0xbb0e,0xb5f0,0xcf24,0xb8ce,0x13cb,0xcbf9,0xe4c3,0x8cbc,0x1318,0x4664,0xd685,0x42ad,0x25c7,0xd991,0x6fe4,0x5d8a,0x256c,0x1b36}, new char[]{0x2fda,0x1237,0x25b3,0x2505,0xd5c9,0xdd3b,0xf154,0x1361,0x1386,0x3e5a,0xd6e1,0x42f0,0xdfa8,0x316c,0x82fb,0x8cdb,0xe45f,0xd4c4,0x111f,0xffea,0xb8a0,0xb597,0xaf09,0x1b5a,0xa65b,0xe4a4,0x2f73,0xd9f2,0x6f9d,0xcbd9,0xbb44,0x8f7f,0x47eb,0x5a34,0x4993,0xcf47,0x460b,0xff64,0xf456,0x8566,0x46aa,0x5daa,0xd832,0x5066,0x72d7,0x1abb,0x5fd5,0xc505,0xea67}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x24d5,0x41b5,0xe04f,0x2e97,0x74b5}, new char[]{0x2ee3,0xe027,0x41d0,0x74d8,0x24b0}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa1e3,0x0aac,0x0694,0x6bcb,0xb1a7,0x6701}, new char[]{0xb1c6,0xa180,0x06f7,0x6764,0x6ba5,0x0ad8}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x89a6,0x8c29,0xae46,0x9daf,0x54f5,0xb636,0x3c68,0x790e}, new char[]{0x8c45,0x796f,0x89c8,0xae21,0x3c1d,0xb657,0x9dc8,0x5490}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5c03,0xc4e9,0x7eff,0xdb42}, new char[]{0x5c70,0xdb2b,0xc493,0x7e9a}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c22,0xd032,0x9bc3,0x71ef,0x41ea}, new char[]{0x719a,0x0c4c,0x4183,0x9bb7,0xd041}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b86,0xdd46,0x0c23,0x1487,0xfd58,0x4195,0xf377,0xe1e2}, new char[]{0xf303,0xe18d,0x1be9,0xfd34,0x41e1,0x0c4a,0xdd36,0x14f4}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x88c1,0x6d8b,0x8589,0x752e,0x8a63,0x7961,0x9ede}, new char[]{0x6de4,0x88b1,0x85ec,0x7540,0x8a28,0x9ebb,0x7918}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdf26,0x14d7,0x9092,0x9584,0x9482,0x4925}, new char[]{0x4956,0xdf49,0x95f1,0x90fc,0x14b3,0x94f1}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9815,0x482e,0xaf55,0xc036,0x845d,0x3811,0xc181}, new char[]{0xaf33,0x9867,0x3878,0x484b,0xc058,0xc1e5,0x842e}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf0f6,0x4a92,0xf154,0xd25a,0x6b44}, new char[]{0xd238,0xf09f,0x6b2a,0x4af6,0xf127}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x40a4,0x4d3f,0xb418,0x176a,0xc9ae,0x74a0,0x02f7,0xf7d2,0x03b3}, new char[]{0x4d48,0xf7b3,0xc9d7,0x03c3,0x74cf,0x40cd,0x1704,0x0283,0xb46b}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba01,0x5b49,0xfe6a,0x0ba3,0x821e,0xb91a,0x4a43,0x28ae,0xe220,0xdf23,0x0d2e,0xe5f2}, new char[]{0x0bd7,0xdf4c,0x8279,0x0d49,0x4a2f,0xfe0f,0xe5a1,0xba6e,0x5b3c,0x28c0,0xe244,0xb969}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc849,0x746a,0x6f3f,0xc5b7,0x3422,0xfc29,0x38b6,0x1faf,0x8a03,0x9496}, new char[]{0x3451,0x38d9,0x1fda,0xfc59,0x94c5,0xc5d8,0x741f,0x8a6d,0x6f5b,0xc83a}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd16d,0x5176,0x8e43,0xdce2,0xbc39,0x018e,0x16c9,0xa4a8,0xd948}, new char[]{0x16ae,0xa4dd,0xdc8b,0x8e27,0xbc5c,0x01ca,0x5119,0xd103,0xd92d}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x683d,0xfcf4,0xd7c1,0x26ca,0x61ee,0xd193,0x0dbe,0xfab2,0x2b02,0x5f03}, new char[]{0x0ddf,0x26a4,0x6187,0xd7ac,0xfad3,0xfc80,0x5f66,0x2b66,0xd1d1,0x685a}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x75ed,0x3edb,0x55a6,0x8cd1,0x8f19,0xd51f}, new char[]{0xd56b,0x8f71,0x55c3,0x3eb6,0x8cb4,0x759e}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdcf3,0x62c9,0x9cca,0x37c6,0x1090,0x07cb,0x39ee,0xbfa2}, new char[]{0xbfc5,0x07be,0x37af,0x10c3,0x9cbe,0xdc8a,0x3982,0x62ac}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6109,0x4ad7,0x2644,0x62de,0x5981,0xeae8,0x1b20,0x7fc1}, new char[]{0x7fb2,0x4ab2,0x2630,0x1b54,0x59e8,0xea86,0x616e,0x62ad}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa87e,0x91e6,0xa526,0xee0d,0xaaa3,0xe8c6,0x1dcb,0x156a,0x6db6,0x4232,0x0b9f,0xf9b4,0x9c15,0x0e4c,0xaf46,0x6fb5,0x9f44,0x3a17,0x84e4,0xc344,0x80ba,0xd680,0x2571,0x1455,0x66d8,0x30b0,0xd81f,0x3c48,0xc2c0,0x37b8,0xfb60,0xe7a2}, new char[]{0xa825,0x1d9e,0xf9c0,0xe7cb,0x8488,0x143c,0x6dc2,0x0e35,0xc28d,0x3a78,0x9182,0x252c,0xee2d,0x9c57,0xe8a7,0xc320,0x66f8,0x3c3e,0x37d9,0xfb0c,0x0bea,0x6fd0,0xa506,0x150c,0x30df,0x4240,0xd83f,0xd6fb,0x80c7,0xaf68,0xaad8,0x9f39}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3ff0,0x1bd5,0x44ec}, new char[]{0x1bbd,0x4499,0x3f94,0x601f}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbca5,0x65cd,0x7957,0x27dc,0xf60a,0xdded}, new char[]{0xdd8c,0x65a3,0x27a9,0xbcc7,0xf663,0x7924}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5ab4,0xe694,0x5385,0xc3ac,0xc24d,0x51d1,0xd5e8}, new char[]{0x51bc,0xc222,0xd58c,0x53f0,0xe6f8,0x5ad1,0xc3df}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x41dc,0x8517,0x7153,0x1990,0xd13f,0x476a,0x0645,0x62a5,0xe351,0xa5b9,0xa98e,0xeee8,0xb036,0x4a12,0x9573,0x498c,0xbcd6,0x7316,0x5727,0x8129,0x3b89,0x41b9,0x9054,0x71e1,0xa3b9,0xd1ee,0x5eb7,0x2c02,0x4d0d,0x81f5,0xa7ea,0x94e1,0x0413,0x288c,0x0f53,0x4b09,0x3a8c,0xe105}, new char[]{0x71ba,0x4a47,0x815d,0xa9e7,0x9038,0xb05f,0xbca2,0x4d74,0xe148,0x062a,0xee8c,0x710e,0x8537,0x419f,0x819a,0x4b7c,0x28e0,0x2c66,0x0f73,0xd151,0x7379,0x5753,0xa599,0x3bfb,0x62c0,0x3aed,0x9517,0x94c1,0xe332,0x5ed8,0xd180,0x19f6,0x41d0,0x470d,0x0429,0xa399,0x49f7,0xa797}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x795e,0x3445,0xa572,0xd32f,0x3bdc,0xd9f1,0xcdf1,0x6092,0x68a7,0x634c,0x8b9d,0x94a9,0x45e2,0x3280,0x9208,0x4796,0xe8bc,0x2990,0xf780,0xa0fc,0x00b6,0x4d4d,0x1941,0x59f6,0xe780,0x5088,0x21a3,0xe2a0,0xf715,0x60ad,0x18a5,0xf7c3,0x19b3,0x7073,0x2ad2,0x2a48,0x9fe6,0xeab2}, new char[]{0x68fc,0x60c7,0x1935,0x2abb,0xcd9d,0x701a,0x19c7,0x60d4,0xe2ed,0x9f89,0xe8d8,0x47cb,0xf7e3,0x32c3,0x2a27,0xd35a,0x18c9,0x793a,0x0096,0xe7ee,0xa093,0xd985,0x3465,0x4d3e,0xead3,0x8beb,0x21c6,0x636c,0x5995,0x94c6,0x9266,0xf7e6,0x50e1,0xf772,0x29aa,0x3bfc,0xa509,0x459f}).plainString();
    }
}
