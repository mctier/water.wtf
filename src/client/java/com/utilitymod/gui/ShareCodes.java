package com.utilitymod.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Offline config share codes: the config JSON (decoded from the binary {@code
 * utilitymod.dat} payload), minus private data (friends, waypoints),
 * deflate-compressed and base64url-encoded behind a "UM1-" prefix. Paste
 * anywhere; import reads the clipboard, backs up the current config as a
 * profile, then applies the code (re-encoded back to .dat on disk).
 */
public final class ShareCodes {
    private static final String PREFIX = LT.L1;
    /** Never leave this machine. */
    private static final String[] PRIVATE = {LT.L2, LT.L3};

    private ShareCodes() {}

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(LT.L4).resolve(LT.L5);
    }

    /** Builds the share code for the current config, or null on failure. */
    public static String export() {
        GuiConfig.save();
        return encode(file());
    }

    /** Share code for a saved profile (without loading it). */
    public static String exportProfile(String name) {
        return encode(file().getParent().resolve(LT.L6).resolve(name + LT.L7));
    }

    /** Copies a profile's code; returns its length (0 = failed). */
    public static int copyProfile(String name) {
        String code = exportProfile(name);
        if (code == null) return 0;
        MinecraftClient.getInstance().keyboard.setClipboard(code);
        return code.length();
    }

    /** Reads a .dat config file and returns its decoded JSON text, or null. */
    private static String readJson(Path source) {
        try {
            return GuiConfig.decodeDat(Files.readAllBytes(source));
        } catch (Exception e) {
            return null;
        }
    }

    private static String encode(Path source) {
        try {
            String jsonText = readJson(source);
            if (jsonText == null) return null;
            JsonObject o = JsonParser.parseString(jsonText).getAsJsonObject();
            for (String k : PRIVATE) o.remove(k);
            byte[] raw = o.toString().getBytes(StandardCharsets.UTF_8);
            Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
            d.setInput(raw);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!d.finished()) out.write(buf, 0, d.deflate(buf));
            d.end();
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /** Copies the code to the clipboard; returns its length (0 = failed). */
    public static int copyToClipboard() {
        String code = export();
        if (code == null) return 0;
        MinecraftClient.getInstance().keyboard.setClipboard(code);
        return code.length();
    }

    /** Imports a code. Returns null on success, or an error message. */
    public static String importCode(String code) {
        if (code == null) return LT.L8;
        code = code.trim().replaceAll(LT.L9, "");
        if (!code.startsWith(PREFIX)) return LT.L10 + PREFIX + LT.L11;
        try {
            byte[] packed = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
            Inflater in = new Inflater();
            in.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int guard = 0;
            while (!in.finished()) {
                int n = in.inflate(buf);
                if (n == 0 && (in.needsInput() || in.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > 4 * 1024 * 1024 || ++guard > 100000) return LT.L12;
            }
            in.end();
            JsonObject incoming = JsonParser.parseString(out.toString(StandardCharsets.UTF_8)).getAsJsonObject();

            // back up first, keep our own private data
            GuiConfig.saveProfile(LT.L13);
            String currentJson = readJson(file());
            if (currentJson != null) {
                JsonObject current = JsonParser.parseString(currentJson).getAsJsonObject();
                for (String k : PRIVATE) {
                    incoming.remove(k);
                    if (current.has(k)) incoming.add(k, current.get(k));
                }
            } else {
                for (String k : PRIVATE) incoming.remove(k);
            }
            // Re-encode to the binary .dat format before writing to disk.
            Files.write(file(), GuiConfig.encodeDat(incoming.toString()));
            GuiConfig.load();
            return null;
        } catch (IllegalArgumentException e) {
            return LT.L14;
        } catch (Exception e) {
            return LT.L15 + e.getClass().getSimpleName();
        }
    }

    public static String importFromClipboard() {
        return importCode(MinecraftClient.getInstance().keyboard.getClipboard());
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x18d5,0xf0ba,0x5bac,0xd4c4}, new char[]{0xd491,0xf0f7,0x18e4,0x5b81}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x76a9,0xa8d6,0x4949,0x4bbe,0x07ce,0x06e7,0x7c9f}, new char[]{0x07a8,0xa8a4,0x7cf6,0x492c,0x4bd0,0x0683,0x76da}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf4a3,0x69d6,0x8dc4,0xc127,0x58de,0x98c8,0x51c0,0xd67b,0x944f}, new char[]{0x51b7,0xd61a,0x69af,0x98b8,0x8dab,0x58b7,0xc149,0xf4d7,0x943c}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x745c,0x450d,0x046c,0x93a2,0x5047,0x2a68}, new char[]{0x93c1,0x2a07,0x4563,0x040a,0x502e,0x743b}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0aed,0xe58c,0xa44f,0x5cc4,0x7470,0xed8e,0x633e,0x6067,0x9f67,0x36b4,0xe442,0x1396,0xdcdb,0xec6d}, new char[]{0xedfb,0xdcaf,0x9f0e,0x36d8,0x13ff,0x5cb0,0x7409,0x600a,0xa420,0x635a,0xe46c,0xec09,0x0a8c,0xe5f8}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0461,0x5c6e,0xc588,0xa73f,0x6c1b,0xdedc,0x2057,0x6d07,0x3e59,0xb325,0x890d,0x9294,0xe28c,0xb2a3,0x5ec2,0xdf4a,0xbb22,0x77d9,0x20ee}, new char[]{0x3e2c,0x6c6f,0x0408,0x92f8,0x5eab,0xc5fc,0xdea5,0xdf27,0x2081,0xb341,0xa712,0x6d77,0x2025,0xe2e3,0x77bf,0x5c07,0x8961,0xb2c6,0xbb51}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x217a,0x0b6b,0x69b1,0xace3}, new char[]{0xaccd,0x0b0f,0x69d0,0x210e}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5372,0x3055,0x3835,0x8c04,0xada1,0x5366,0xefdd,0x4c38,0x7391,0xe14d,0xff14,0x5d5b,0x8fc7,0x5bba,0x3619,0x01eb,0xe1b8,0x3ccd}, new char[]{0x3876,0xefb1,0xe124,0x019b,0x367b,0xe1d7,0x5d3a,0x3027,0x73f5,0x8fe7,0x531b,0x3cbe,0x5b9a,0x8c61,0x530b,0xadd1,0x4c4c,0xff6d}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6b16,0xeb6f,0x6696}, new char[]{0x66ca,0x6b65,0xeb44,0x45b9}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9e19,0x0772,0x6217,0x4843,0x8f08,0xe589,0x3423,0x0c59,0xd156,0xb814,0x8268,0xfa4e,0x7b7c,0xa957,0x21ce,0x123f,0x04ef,0x7c7a,0xfe18,0xc787,0x7926,0x3699,0x2dcb,0xb2d8,0xb5c3,0xf1ea,0xb84f,0xea42,0xa05a,0x0d46,0x39aa,0x6d2a,0x643d,0x33a6,0x0de6,0xd97b,0xa642,0xc508,0xc110,0xed7b,0x6797}, new char[]{0x8226,0xb2b7,0xc57c,0x9e39,0x6276,0x3403,0x4836,0xb5b7,0xc179,0x0d2a,0x67fe,0xa02e,0x6444,0x071f,0xa62d,0x7b18,0xc7a7,0x8f6b,0xd914,0x0c3d,0x048a,0xb86f,0xea6a,0xd125,0xb87c,0xfe77,0x2dbe,0x33ca,0xe5ed,0x121f,0x7c09,0xf19e,0x21af,0xed09,0x36ed,0xfa6e,0x6d5d,0x0d8f,0xa923,0x39c2,0x7906}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4d24}, new char[]{0x4d0d,0xd559,0xd2ee,0xe1df}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa98b,0x6364,0xfb7c,0xc4b7,0x9beb,0x0ecf,0x3491,0x25c1,0xec7a,0x46bd,0x4a8f,0xbb39,0x4049,0xa8ca,0x05de,0x3675,0xe4cc}, new char[]{0xa9c8,0xbb56,0xfb18,0xc4d2,0xec5a,0xa8a3,0x0ebc,0x05fe,0x34e5,0xe4a3,0x4ae0,0x3655,0x9b87,0x46dc,0x403b,0x6303,0x25a4}).toString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x904a,0xc37b,0xd098,0x9b4c,0xedcf,0x6d56,0x3a0f,0xdedb,0xd91e,0x4d3f,0xa1d1,0x5bb9,0xecfe,0x55e6,0x11c2,0x021c,0xa131,0xa838,0x4b6c,0x60d6}, new char[]{0xedad,0x6d37,0xa152,0x3a64,0x5593,0x9b3c,0x4d12,0x4b0e,0xd97b,0xa85e,0xc314,0x9038,0x5bdc,0xd0b5,0xdeb2,0x60bb,0xec8e,0x0273,0x11b0,0xa1a5}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x58f0,0xef00,0xf28f,0x5b80,0x5701,0x2c64,0x9143,0xc36a,0x7f74,0x91e3,0x08ce,0x148b,0x19f0,0x4e73,0x8a9e,0xbacf,0x9010,0xa68a,0xcee6,0xa7df,0x14e7,0x301f,0x646e,0x12f8,0xc6fd,0x7024,0x5f00,0x61be,0x9e01,0xa6ef,0x6e7b,0x3c61,0x3523,0x47da,0x5760,0x9d5b,0x19e3,0x31e6}, new char[]{0xc6be,0x3c0e,0xbaab,0x61db,0xa7ff,0x5be9,0x47a9,0xcec6,0xa6ee,0x6e1a,0x589d,0x5701,0x1984,0x640b,0x3547,0x7004,0x8ab6,0x5f63,0x7f1b,0x1980,0x2c1d,0xa6cf,0x9197,0x4e1b,0x9e64,0xf2af,0x128f,0x9078,0x1488,0x912f,0xef65,0x9d7b,0x3192,0x08a6,0x5768,0xc304,0x14ec,0x3036}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9d6e,0xbdab,0xd3b8,0xfd88,0xd3e9,0xdb9f,0x4ef2,0x55dc,0x8b7c,0x8021,0xf878,0xdb9b,0x5851,0xa599,0x46ce,0xfd80,0xa00e,0xd0ac,0xcfd3,0x9254,0xa186}, new char[]{0x9d2d,0x923b,0xa07b,0x583d,0xf81c,0xa5b9,0x8b12,0x4e9d,0xfdf4,0xd08c,0xbdd9,0x8044,0xfde9,0xcfb7,0xa1a6,0x55bf,0xd386,0xdbfb,0x46ab,0xdba1,0xd398}).toString();
    }
}
