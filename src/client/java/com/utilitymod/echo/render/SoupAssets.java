package com.utilitymod.echo.render;

import com.utilitymod.UtilityMod;
import com.utilitymod.echo.Render3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assets from SoupAPI-recode by Padej_ (GPL-3.0, see assets/utilitymod/soup/CREDITS.txt): textures are
 * read from the jar and registered directly (late-injected jars are invisible to the resource
 * manager); "glow" art made for additive blending (black background) is converted luminance -> alpha.
 * Sounds are .ogg, decoded with LWJGL's stb_vorbis (bundled with Minecraft) and played via javax Clip.
 */
public final class SoupAssets {
    public record Tex(Identifier id, int w, int h) {}

    private static final Map<String, Tex> TEXTURES = new HashMap<>();
    private static final Map<String, Clip[]> SOUNDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Integer> NEXT = new java.util.concurrent.ConcurrentHashMap<>();
    private static List<String> capes;

    private SoupAssets() {}

    // ---- textures ------------------------------------------------------------------------------

    /** {@code path} under assets/utilitymod/soup/ without ".png"; {@code glow} = luminance becomes alpha. */
    public static Tex texture(String path, boolean glow) {
        String key = path + (glow ? LT.L1 : "");
        if (TEXTURES.containsKey(key)) return TEXTURES.get(key);
        Tex tex = null;
        try (InputStream in = SoupAssets.class.getResourceAsStream(LT.L2 + path + LT.L3)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                if (glow) {
                    for (int y = 0; y < img.getHeight(); y++) {
                        for (int x = 0; x < img.getWidth(); x++) {
                            int c = img.getColorArgb(x, y);
                            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                            int a = Math.max(r, Math.max(g, b)) * ((c >>> 24) & 255) / 255;
                            if (a == 0) {
                                img.setColorArgb(x, y, 0);
                                continue;
                            }
                            img.setColorArgb(x, y, (a << 24) | (Math.min(255, r * 255 / Math.max(1, a)) << 16)
                                    | (Math.min(255, g * 255 / Math.max(1, a)) << 8) | Math.min(255, b * 255 / Math.max(1, a)));
                        }
                    }
                }
                Identifier id = Identifier.of(UtilityMod.MOD_ID, LT.L4 + key.replace('#', '_').toLowerCase());
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, img));
                tex = new Tex(id, img.getWidth(), img.getHeight());
            }
        } catch (Exception e) {
            UtilityMod.LOGGER.warn(LT.L5, path, e.toString());
        }
        TEXTURES.put(key, tex);
        return tex;
    }

    /** All capes as "category/name". */
    public static List<String> capes() {
        if (capes != null) return capes;
        List<String> out = new ArrayList<>();
        try (InputStream in = SoupAssets.class.getResourceAsStream(LT.L6)) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) if (!line.isBlank()) out.add(line.trim());
            }
        } catch (Exception ignored) {
        }
        capes = out;
        return out;
    }

    /** Capes in one category ("april", "custom", ...). */
    public static List<String> capes(String category) {
        List<String> out = new ArrayList<>();
        for (String c : capes()) if (c.startsWith(category + LT.L7)) out.add(c);
        return out;
    }

    // ---- drawing --------------------------------------------------------------------------------

    /** Texture centred at (x, y), {@code size} px, rotated, tinted. */
    public static void drawBillboard(DrawContext ctx, Tex t, float x, float y, float size, float degrees, int argb) {
        if (t == null) return;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.rotate((float) Math.toRadians(degrees));
        float s = size / Math.max(t.w(), t.h());
        m.scale(s, s);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, t.id(), -t.w() / 2, -t.h() / 2, 0f, 0f, t.w(), t.h(), t.w(), t.h(), argb);
        m.popMatrix();
    }

    /**
     * Texture lying on a world plane: centre {@code c}, half-axes {@code u} and {@code v} (world units),
     * projected with an affine fit around the centre (good for small quads like ground circles).
     */
    public static void drawWorldQuad(DrawContext ctx, Render3D frame, Tex t, Vec3d c, Vec3d u, Vec3d v, int argb) {
        if (t == null || frame == null) return;
        Vector4f cc = frame.toClip(c.x, c.y, c.z);
        Vector4f cu = frame.toClip(c.x + u.x, c.y + u.y, c.z + u.z);
        Vector4f cv = frame.toClip(c.x + v.x, c.y + v.y, c.z + v.z);
        if (cc.w() <= 0.05f || cu.w() <= 0.05f || cv.w() <= 0.05f) return;
        float[] p = Render3D.toGui(cc), pu = Render3D.toGui(cu), pv = Render3D.toGui(cv);
        float hw = t.w() / 2f, hh = t.h() / 2f;
        Matrix3x2f affine = new Matrix3x2f((pu[0] - p[0]) / hw, (pu[1] - p[1]) / hw, (pv[0] - p[0]) / hh, (pv[1] - p[1]) / hh, p[0], p[1]);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.mul(affine);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, t.id(), -t.w() / 2, -t.h() / 2, 0f, 0f, t.w(), t.h(), t.w(), t.h(), argb);
        m.popMatrix();
    }

    // ---- sounds ---------------------------------------------------------------------------------

    /**
     * All audio work (decode, clip open, play) runs on one daemon thread. The game thread only
     * enqueues, so a slow or busy audio device can never freeze the game.
     */
    private static final java.util.concurrent.ExecutorService AUDIO =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, LT.L8);
                t.setDaemon(true);
                return t;
            });
    private static final java.util.concurrent.atomic.AtomicInteger QUEUED = new java.util.concurrent.atomic.AtomicInteger();

    public static void preload(String... names) {
        AUDIO.execute(() -> {
            for (String n : names) SOUNDS.computeIfAbsent(n, SoupAssets::load);
        });
    }

    /** Plays sounds/{name}.ogg at {@code volume} (0..1). Never blocks the caller. */
    public static void play(String name, float volume) {
        if (QUEUED.get() > 8) return; // audio thread is stuck or busy: drop, don't pile up
        QUEUED.incrementAndGet();
        AUDIO.execute(() -> {
            try {
                Clip[] pool = SOUNDS.computeIfAbsent(name, SoupAssets::load);
                if (pool == null || pool.length == 0) return;
                int i = NEXT.merge(name, 1, Integer::sum) % pool.length;
                Clip c = pool[i];
                if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = (float) (20 * Math.log10(Math.max(0.0001, volume)));
                    g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
                }
                c.stop();
                c.setFramePosition(0);
                c.start();
            } catch (Throwable ignored) {
            } finally {
                QUEUED.decrementAndGet();
            }
        });
    }

    private static Clip[] load(String name) {
        try (InputStream in = SoupAssets.class.getResourceAsStream(LT.L9 + name + LT.L10)) {
            if (in == null) return new Clip[0];
            byte[] bytes = in.readAllBytes();
            // Decode with stb's streaming API into Java-owned buffers: no native memory is handed
            // back to us, so nothing has to be freed with "the right" allocator (decode_memory
            // returns stb-allocated memory; freeing it with the wrong allocator corrupted the heap).
            ByteBuffer ogg = org.lwjgl.BufferUtils.createByteBuffer(bytes.length);
            ogg.put(bytes).flip();
            byte[] pcm;
            int channels, rate;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                long handle = STBVorbis.stb_vorbis_open_memory(ogg, error, null);
                if (handle == 0L) return new Clip[0];
                try {
                    org.lwjgl.stb.STBVorbisInfo info = org.lwjgl.stb.STBVorbisInfo.malloc(stack);
                    STBVorbis.stb_vorbis_get_info(handle, info);
                    channels = info.channels();
                    rate = info.sample_rate();
                    int frames = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
                    if (channels <= 0 || frames <= 0) return new Clip[0];
                    ShortBuffer samples = org.lwjgl.BufferUtils.createShortBuffer(frames * channels);
                    int got = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, samples);
                    int count = Math.max(0, Math.min(got * channels, samples.capacity()));
                    ByteBuffer out = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < count; i++) out.putShort(samples.get(i));
                    pcm = out.array();
                } finally {
                    STBVorbis.stb_vorbis_close(handle);
                }
            }
            AudioFormat fmt = new AudioFormat(rate, 16, channels, true, false);
            Clip[] pool = new Clip[2];
            for (int i = 0; i < pool.length; i++) {
                pool[i] = AudioSystem.getClip();
                pool[i].open(fmt, pcm, 0, pcm.length);
            }
            return pool;
        } catch (Exception e) {
            UtilityMod.LOGGER.warn(LT.L11, name, e.toString());
            return new Clip[0];
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd562,0x2f7d,0x3f6b,0x0f01,0x5888}, new char[]{0x3f48,0xd505,0x0f6d,0x58e7,0x2f0a}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd7e5,0xc328,0x18e5,0xa94e,0x2195,0x2c39,0x3988,0xd657,0x77d4,0xf5f4,0x3bb4,0xd16f,0x4111,0x6c0a,0x4cfe,0xed0d,0x8854,0x545d,0x5d3e,0x2942,0x82db,0x4727,0x9849,0x4080}, new char[]{0x5d11,0xed6c,0xd624,0x77a7,0x982c,0x6c7e,0x40f3,0xf5db,0x1890,0x2c4d,0x5434,0x21f9,0xd106,0xc35c,0x82a2,0x8839,0x3bdb,0x4175,0x4708,0xa93d,0x39e7,0xd790,0x2932,0x4cd1}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8bbb,0x3e46,0xfd54,0xb5d0}, new char[]{0xfd7a,0x8bcb,0xb5be,0x3e21}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5180,0xc203,0xe4b3,0x0341,0x3703}, new char[]{0x0332,0xc26c,0x51f5,0x3773,0xe49c}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9732,0x2bae,0xbbf4,0xf303,0x8dc8,0xee37,0x62b8,0xccae,0xaeb4,0xc028,0x4619,0xb113,0xcb90,0x3e65,0xa95a,0x9547,0x8755,0x7285,0x9d9d,0x3575,0x6397,0x545c,0x5779,0x9ace,0xd56e,0x1208,0xa506,0x890d,0x70a3,0xf9a9,0x75ed,0x7d11,0x3288,0xa7ea,0xba02,0xe069,0x4d86,0x00d4,0x9a53}, new char[]{0xf9f2,0xa553,0xa79e,0xbb9d,0x00b8,0x2bc7,0xc05c,0x8974,0xba4f,0xb17c,0x126c,0x976f,0x9a73,0xf370,0x9aa1,0x466c,0x759d,0x3e45,0xee43,0xd50b,0x8db0,0x5428,0xa92f,0x32fa,0x571c,0xcbb0,0x350e,0x8728,0xcc8e,0xaed2,0x9526,0x72ec,0x7d7d,0xe00c,0x63f3,0x4dbc,0x9dbd,0x70d8,0x62c5}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd8ef,0xf0a4,0xd2de,0xdbd6,0x4436,0x6e7d,0x8dc5,0x8ad0,0xa0f4,0x69ac,0x8626,0xbd97,0x02e2,0x87ab,0xbf28,0x8d09,0x04b5,0xaa5c,0xe7a0,0x09ed,0x6389,0x88dd,0x4807,0xacbb,0x1ead,0xee8d,0xf190,0x0969,0xf293,0x4d5e,0x7ecb,0xc15f,0xb71a}, new char[]{0xd8c0,0x6e1c,0x63fa,0x0291,0xc13a,0x8652,0x099e,0xb735,0x69d9,0xaa28,0xbdfe,0xa098,0xacd2,0x8aa4,0x87d2,0xd2b3,0x4868,0xf1f4,0xdbf9,0x8db6,0xbf47,0x4d2b,0x7ebb,0x4419,0x88be,0x8d68,0x0919,0x04d0,0x1ede,0xf08a,0xeef9,0xf2eb,0xe7d4}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x228e}, new char[]{0x22a1,0x480b,0x2cb6,0x75d3}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd232,0xb6a3,0xf2d7,0x4e7c,0xed1f,0x89bd,0x800e,0x8992,0x9b1d,0xedaf,0x0639,0x5172,0x2204,0x8bff,0x1999,0xd05a}, new char[]{0x2271,0x89e6,0xf2be,0xd036,0xedc6,0x064d,0x89c4,0x511f,0x19f6,0x8b9b,0x9b30,0xed7e,0x807b,0xb6c7,0xd25b,0x4e13}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08b7,0xaa96,0xab9c,0x7d18,0x4539,0x32db,0x53d0,0xe21c,0xaaf1,0xc3e6,0xbb48,0x2885,0xf1f6,0x530d,0xcaff,0x11ab,0xa5bc,0xd3bc,0x1152,0xaa9b,0x4bf0,0x1ebc,0x896a,0xb2f1,0x0665,0xd2c4,0x0697,0xb1fd,0x4580,0x1972,0xad85}, new char[]{0xb1d2,0xaaf7,0x45f3,0x8919,0xabf9,0x32af,0x537e,0xe233,0x06e2,0xd3c8,0xaaf2,0x08db,0x113b,0x1906,0x28fc,0x0608,0xbb27,0x53b4,0xcad0,0xa5cf,0xc389,0x4b85,0x4549,0xf1d9,0xd2b7,0x1ed3,0x11de,0xadeb,0xaa95,0xb282,0x7d37}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd06d,0x8454,0x4f16,0xf760}, new char[]{0xf74e,0xd002,0x8433,0x4f71}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xff6f,0xed35,0x7827,0x5334,0xe2b1,0x5e83,0x98a1,0xfd5a,0x4524,0xc515,0x670f,0xcd5a,0xdfea,0x9957,0x6631,0x6536,0x5ac1,0xf6b5,0xfa06,0x5965,0x76be,0xe0e8,0xba4b,0x9757,0x621f,0x4ff9,0x9663,0x2305,0xb38c,0x4d79,0x4c80,0x8fbe,0x05d9,0xa8d2,0x90f5,0x256f,0xffca}, new char[]{0x5ed8,0x5930,0xc561,0x2506,0x665d,0x5aa8,0xe2c5,0x05a0,0xf6f8,0x4d16,0xcd3e,0x6752,0xe0c8,0xed46,0xdf85,0x9616,0xa8a2,0x2325,0xfd29,0xb3e3,0x9922,0x7849,0x5350,0xff4f,0x4cfb,0x98dc,0xffea,0x6550,0xfa67,0x973e,0x9099,0xba2e,0x76da,0x451e,0x623f,0x4f82,0x8fc3}).plainString();
    }
}
