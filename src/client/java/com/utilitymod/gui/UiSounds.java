package com.utilitymod.gui;

import com.utilitymod.UtilityMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * wing sounds from the bundled WAVs.
 *
 * <p>A late-injected jar never reaches Minecraft's sound manager (sounds.json
 * is read at resource reload), so clips are decoded through the classloader
 * and played with javax.sound. Volume follows the game's master volume and
 * the "UI sounds" setting. A small clip pool per sound allows overlap.
 */
public final class UiSounds {
    public enum Sound {
        OPEN(LT.L1, 0.35f),
        CLOSE(LT.L1, 0.22f),
        CLICK(LT.L2, 0.55f),
        TOGGLE_ON(LT.L3, 0.5f),
        TOGGLE_OFF(LT.L2, 0.45f),
        TAB(LT.L4, 0.35f),
        HOVER(LT.L5, 0.12f),
        DROPDOWN(LT.L6, 0.3f),
        POPUP(LT.L7, 0.3f),
        SUCCESS(LT.L8, 0.3f);

        final String file;
        final float volume;

        Sound(String file, float volume) {
            this.file = file;
            this.volume = volume;
        }
    }

    private static final int POOL = 3;
    private static final long HOVER_GAP_MS = 45;
    private static final Map<Sound, Clip[]> CLIPS = new EnumMap<>(Sound.class);
    private static final Map<Sound, Integer> NEXT = new EnumMap<>(Sound.class);
    private static boolean loaded;
    private static boolean broken;
    private static long lastHover;

    public static boolean enabled = true;

    private UiSounds() {}

    public static void play(Sound sound) {
        if (!enabled || broken) return;
        if (sound == Sound.HOVER) {
            long now = System.currentTimeMillis();
            if (now - lastHover < HOVER_GAP_MS) return;
            lastHover = now;
        }
        load();
        Clip[] pool = CLIPS.get(sound);
        if (pool == null) return;
        int i = NEXT.getOrDefault(sound, 0);
        NEXT.put(sound, (i + 1) % pool.length);
        Clip clip = pool[i];
        try {
            clip.stop();
            clip.setFramePosition(0);
            gain(clip, sound.volume * master());
            clip.start();
        } catch (Throwable ignored) {
            // A failed clip must never break input handling.
        }
    }

    private static float master() {
        try {
            return MinecraftClient.getInstance().options.getSoundVolume(SoundCategory.MASTER);
        } catch (Throwable t) {
            return 1f;
        }
    }

    private static void gain(Clip clip, float linear) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl c = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = linear <= 0.0001f ? c.getMinimum() : (float) (20 * Math.log10(linear));
        c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        int ok = 0;
        for (Sound s : Sound.values()) {
            Clip[] pool = new Clip[s == Sound.HOVER || s == Sound.CLICK ? POOL : 2];
            try {
                for (int i = 0; i < pool.length; i++) {
                    try (InputStream raw = UiSounds.class.getResourceAsStream(LT.L9 + s.file)) {
                        if (raw == null) throw new IllegalStateException(LT.L10 + s.file);
                        AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
                        Clip clip = AudioSystem.getClip();
                        clip.open(in);
                        pool[i] = clip;
                    }
                }
                CLIPS.put(s, pool);
                ok++;
            } catch (Throwable t) {
                UtilityMod.LOGGER.warn(LT.L11, s.file, t.toString());
            }
        }
        if (ok == 0) broken = true;
        UtilityMod.LOGGER.info(LT.L12, ok, Sound.values().length);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4007,0x413f,0x091d,0xe49d,0xc54a,0x2fb8,0xd6ce,0x8cec,0xb490}, new char[]{0xc53d,0x2fd7,0x8c83,0xb4e3,0x406f,0xd6e0,0xe4ea,0x415e,0x096b}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f8b,0x67c3,0xdadd,0x290d,0x4780,0xb287,0x2a73,0x1c5f}, new char[]{0x2a03,0xb2e8,0x47f0,0x67f0,0x1c71,0x6ffc,0x296c,0xdaab}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7996,0xcae9,0xc7e3,0x3bcf,0xdc9b,0x4a9c,0x1ade}, new char[]{0x79e6,0x4af3,0xdceb,0xc7cd,0x1aa9,0x3bae,0xca9f}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd98e,0x20ba,0x2d0a,0xcc90,0x199b,0x5469,0x3034,0x2880}, new char[]{0xd9fe,0x20d5,0x19eb,0x545b,0x2d24,0xcce7,0x3055,0x28f6}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x09f5,0x4882,0xe1b7,0xe7e0,0xf28f,0x0e84,0x22cc,0x8d1e,0xb0c9}, new char[]{0x8d76,0xb0a6,0xe1c1,0x0ee1,0x22be,0xe7ce,0xf2f8,0x48e3,0x0983}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x86bc,0x5c45,0x36da,0xde4e,0x067d,0x0d24,0x0450,0x3c40,0x2ac8,0xb19c,0x0aa9,0x7cf8}, new char[]{0x0d40,0x060f,0x5c2a,0x2ab8,0x36be,0x86d3,0x0ade,0x043e,0x7cd6,0x3c37,0xde2f,0xb1ea}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x247e,0xd84f,0xede7,0xf61b,0xcafd,0x8460,0xd7eb,0x6104,0x37a3}, new char[]{0x37d3,0x2411,0xf66b,0xed92,0xd79b,0x612a,0x8417,0xd82e,0xca8b}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2754,0xff70,0xcab2,0x2d53,0x7a9c,0x1c6a,0xdf31,0x6d19,0x639e,0x1839,0xccbd}, new char[]{0xdf42,0x2d26,0xccde,0x185a,0xff15,0x6d6a,0x7aef,0x63b0,0x1c1d,0x2735,0xcac4}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e02,0xf803,0x97d4,0x74b8,0xe49f,0x7718,0x6a64,0x6fc0,0x531a,0x2e61,0x716a,0x8988,0x2569,0xd146,0xfd64,0x0249,0x8878,0xf475,0xa10a,0x1bda,0xb026,0xd7f7,0x54b8,0x4698,0x8b29,0x3f7b}, new char[]{0x3f54,0xd127,0x023a,0xd784,0x4e67,0xf877,0x6fb3,0xfd4b,0xb053,0xf401,0x7771,0x54d4,0x97bd,0x2e15,0x74c1,0xa167,0x7105,0x1bbe,0x46b7,0x880b,0xe4f0,0x8b5c,0x89e6,0x537e,0x6a17,0x2546}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf76b,0x331e,0x8e8b,0x4b58,0xe372,0x6034,0xde40,0xff52}, new char[]{0xf706,0x8ee2,0x4b2b,0x6047,0xff3b,0x3370,0xe315,0xde60}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3e8f,0x734e,0x86f9,0x3986,0x20e4,0x3b24,0x180d,0xc181,0x6f1c,0xb191,0x7c21,0xf1b9,0xf6b9,0x3688,0xc5da,0xe1a1,0xbf03,0x0918,0x04f1,0x9d89,0x1202,0xb955,0xcf51,0xdad3,0xcd36,0x0440,0xdd20,0x7d5c,0xf743,0xfddf,0x6799,0xd949,0x909d,0xa43f,0x49ee,0x8e94,0x71d0,0xa658,0x33fb,0x806a}, new char[]{0x041b,0x67cc,0xdd54,0x208d,0xb1fd,0xdaba,0xcf25,0x39ff,0x33b6,0xc1ee,0x3eeb,0x04ac,0x804a,0x49bb,0xf6f0,0x7c01,0xc5a9,0x0977,0x71a5,0x6f72,0xf1dd,0x86d9,0xb92e,0xcd4b,0x8eb4,0x9dfc,0xd927,0x90fc,0xfda9,0x7d3d,0xe1c8,0xbf6f,0x732f,0x186f,0xf72f,0xa63d,0xa405,0x1222,0x3b5f,0x36f5}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9102,0x9f6d,0x9850,0xf75c,0x5a66,0x9dcc,0x0a3c,0xe6cc,0x7702,0xcb46,0x75bf,0x2542,0x58cb,0x5279,0x159d,0xa16f,0xb188,0x39c9,0xf7b3,0xcc62,0x767c,0x7353,0x1fec,0xb47a,0x6141,0xb454,0x27f3,0x305f,0x7378,0x0ba3,0x8b92,0x6a97,0x4324,0x7683,0x8d74,0x0943}, new char[]{0x9159,0x399c,0x8d00,0x092a,0x5215,0x776b,0x0bd7,0xcb3f,0x5886,0xf733,0xb41e,0x75e2,0x7358,0x6ac2,0x250b,0xb474,0x76f0,0xa100,0x1f99,0x0a52,0x9da8,0x4357,0x7373,0x983c,0x9f02,0xe6ad,0xf7d7,0x7619,0xcc06,0x15a7,0x307f,0x8be9,0x613c,0xb1a7,0x5a1d,0x278e}).plainString();
    }
}
