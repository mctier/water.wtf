package com.utilitymod.echo.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.utilitymod.UtilityMod;
import com.utilitymod.echo.Render3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Block-silhouette shader mode: per colour group, draws the blocks as solid silhouettes (through walls)
 * into a mask texture, runs a Shader ESP style over it (outline, glow, rainbow, ...) and draws the
 * result over the screen. Full GL state save/restore like the other passes.
 */
public final class BlockShaderRenderer {
    public static final int MAX_GROUPS = 6;

    private static boolean failed;
    private static int program, vao, vbo, fbo, mask, maskW, maskH;
    private static final NativeImageBackedTexture[] OUT = new NativeImageBackedTexture[MAX_GROUPS];
    private static final Identifier[] OUT_ID = new Identifier[MAX_GROUPS];
    private static int outW, outH;

    private BlockShaderRenderer() {}

    /** groups: colour -> block positions. */
    public static void render(DrawContext ctx, Render3D frame, Map<Integer, List<BlockPos>> groups,
                              ShaderEsp.Style style, float radius, float fill) {
        if (failed || frame == null || groups.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getFramebuffer().textureWidth, h = mc.getFramebuffer().textureHeight;
        try {
            if (!ensure(w, h)) return;
            Matrix4f mvp = new Matrix4f(frame.projection).mul(frame.view);
            int i = 0;
            for (var e : groups.entrySet()) {
                if (i >= MAX_GROUPS) break;
                if (!(OUT[i].getGlTexture() instanceof GlTexture out)) continue;
                drawMask(mvp, frame, e.getValue(), w, h);
                if (!ShaderEsp.processMask(mask, out.getGlId(), w, h, style, e.getKey() | 0xFF000000, radius, fill)) {
                    failed = true;
                    UtilityMod.LOGGER.warn(LT.L1, style);
                    return;
                }
                if (i == 0 && System.currentTimeMillis() - lastDiag > 5000) {
                    lastDiag = System.currentTimeMillis();
                    UtilityMod.LOGGER.info(LT.L2,
                            e.getValue().size(), countLit(mask, w, h), countLit(out.getGlId(), w, h), w, h);
                }
                int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, OUT_ID[i], 0, 0, 0f, 0f, sw, sh, w, h, w, h);
                i++;
            }
        } catch (Throwable t) {
            failed = true;
            UtilityMod.LOGGER.warn(LT.L3, t.toString());
        }
    }

    private static long lastDiag;

    /** Diagnostic: how many pixels of a texture have alpha (sampled on a coarse grid). */
    private static int countLit(int tex, int w, int h) {
        int prevFbo = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int rf = GL30.glGenFramebuffers();
        java.nio.ByteBuffer px = MemoryUtil.memAlloc(w * 4);
        int lit = 0;
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, rf);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
            for (int y = 0; y < h; y += Math.max(1, h / 32)) {
                GL11.glReadPixels(0, y, w, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
                for (int x = 0; x < w; x += 4) if ((px.get(x * 4 + 3) & 255) > 8) lit++;
            }
        } finally {
            MemoryUtil.memFree(px);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevFbo);
            GL30.glDeleteFramebuffers(rf);
        }
        return lit;
    }

    private static boolean ensure(int w, int h) {
        if (program == 0) {
            program = link();
            if (program == 0) {
                failed = true;
                return false;
            }
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            fbo = GL30.glGenFramebuffers();
            int pv = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING), pb = GlStateManager._getInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GlStateManager._glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
            GlStateManager._glBindVertexArray(pv);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pb);
        }
        if (mask == 0 || maskW != w || maskH != h) {
            if (mask != 0) GL11.glDeleteTextures(mask);
            int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            mask = GL11.glGenTextures();
            GlStateManager._bindTexture(mask);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            // one level only: texture stays complete under any sampler state
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GlStateManager._bindTexture(prev);
            maskW = w;
            maskH = h;
        }
        if (OUT[0] == null || outW != w || outH != h) {
            var tm = MinecraftClient.getInstance().getTextureManager();
            for (int i = 0; i < MAX_GROUPS; i++) {
                if (OUT[i] != null) OUT[i].close();
                OUT_ID[i] = Identifier.of(UtilityMod.MOD_ID, LT.L4 + i);
                OUT[i] = new NativeImageBackedTexture(OUT_ID[i]::toString, new NativeImage(w, h, true));
                tm.registerTexture(OUT_ID[i], OUT[i]);
            }
            outW = w;
            outH = h;
        }
        return true;
    }

    /** Solid cubes (camera-relative, 36 vertices each) into the mask texture. */
    private static void drawMask(Matrix4f mvp, Render3D frame, List<BlockPos> blocks, int w, int h) {
        int n = blocks.size();
        FloatBuffer buf = MemoryUtil.memAllocFloat(n * 36 * 3);
        try {
            double cx = frame.camera.x, cy = frame.camera.y, cz = frame.camera.z;
            for (BlockPos p : blocks) {
                float x0 = (float) (p.getX() - cx), y0 = (float) (p.getY() - cy), z0 = (float) (p.getZ() - cz);
                cube(buf, x0, y0, z0, x0 + 1, y0 + 1, z0 + 1);
            }
            buf.flip();

            int prevFbo = GlStateManager._getInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int prevProgram = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevVbo = GlStateManager._getInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            try {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mask, 0);
                GlStateManager._viewport(0, 0, w, h);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glColorMask(true, true, true, true);
                GL11.glClearColor(0f, 0f, 0f, 0f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GlStateManager._glUseProgram(program);
                float[] m = new float[16];
                mvp.get(m);
                GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, LT.L5), false, m);
                GlStateManager._glBindVertexArray(vao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STREAM_DRAW);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, n * 36);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            } finally {
                GlStateManager._glBindVertexArray(prevVao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
                GlStateManager._glUseProgram(prevProgram);
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
                GlStateManager._viewport(vp[0], vp[1], vp[2], vp[3]);
                if (blend) GL11.glEnable(GL11.GL_BLEND);
                if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
            }
        } finally {
            MemoryUtil.memFree(buf); // allocated with memAllocFloat above: same allocator
        }
    }

    private static void cube(FloatBuffer b, float x0, float y0, float z0, float x1, float y1, float z1) {
        float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        int[] idx = {0, 1, 2, 0, 2, 3, 4, 6, 5, 4, 7, 6, 0, 4, 5, 0, 5, 1, 3, 2, 6, 3, 6, 7, 0, 3, 7, 0, 7, 4, 1, 5, 6, 1, 6, 2};
        for (int i : idx) b.put(c[i][0]).put(c[i][1]).put(c[i][2]);
    }

    private static int link() {
        int vs = compile(GL20.GL_VERTEX_SHADER, LT.L6), fs = compile(GL20.GL_FRAGMENT_SHADER, LT.L7);
        if (vs == 0 || fs == 0) return 0;
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glBindAttribLocation(p, 0, LT.L8);
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            UtilityMod.LOGGER.warn(LT.L9, GL20.glGetProgramInfoLog(p));
            return 0;
        }
        return p;
    }

    private static int compile(int type, String file) {
        try (InputStream in = BlockShaderRenderer.class.getResourceAsStream(LT.L10 + file)) {
            if (in == null) return 0;
            int sh = GL20.glCreateShader(type);
            GL20.glShaderSource(sh, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            GL20.glCompileShader(sh);
            if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
                UtilityMod.LOGGER.warn(LT.L11, file, GL20.glGetShaderInfoLog(sh));
                return 0;
            }
            return sh;
        } catch (Exception e) {
            return 0;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd8a0,0xb27d,0xda9b,0x44e9,0x332c,0x233f,0x3110,0x888f,0x823e,0xc86b,0x5794,0x43d5,0x2f15,0xd427,0xe110,0xb04a,0x9885,0x0243,0x4d4f,0xcb44,0x478f,0x9c49,0x9664,0xc470,0xc8df,0xa96f,0xdbfa,0xcfac,0x5500,0x84fe,0x628a,0x37aa,0xf040,0x96b0,0xd1ab,0x64bf,0x960a,0xf236,0xc7db,0xaa5f,0xe4bb,0x2210,0x93b1,0x8f42,0xe6c4,0xf663,0x7f2e}, new char[]{0x47d4,0xcff9,0xc404,0xe6ad,0xf25a,0x2f7c,0xe4cf,0x023a,0xf00d,0xb025,0x448d,0x84a3,0x8f62,0xaa3d,0x57f8,0xe17f,0xf600,0x88e4,0xcb69,0x93c2,0xdaf3,0xc7ba,0x2274,0x4d2a,0x824c,0xc851,0xc8ff,0x7f5d,0x9610,0x98fc,0x2353,0xd8c5,0xb25d,0x37d1,0xdb87,0x62aa,0x3359,0x96de,0x9c28,0x64c9,0xa90e,0xd44e,0x556c,0xd1ca,0x43b7,0x9666,0x3175}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd62d,0x4272,0x0cf5,0xf5ee,0x8f74,0xf404,0xa668,0xdcff,0x8091,0x21fc,0xffbc,0x4a32,0x00c1,0x9497,0xed2a,0x7dd4,0x8bf6,0xdce4,0x5cda,0x8bcb,0x160f,0xc0ab,0xb39d,0x51e6,0x779b,0xe0f9,0xfebb,0xd75b,0x7fa6,0x5375,0x61f3,0x16bd,0x044d,0x6293,0xa8bc,0x2328,0x1de0,0xc9a4,0x324d,0x4f41,0x3b60,0x8274,0x704e,0x6e84,0x2e73,0x0c39,0x90d2,0xc2d4,0x6a7d,0xc6a0,0x3af7,0x04b0,0x92e7,0xf939,0x56f7,0x8603,0x1247,0xabf8,0x1f57,0x75dc,0x6e59,0xc0db,0x1369,0x194d,0x3398,0xbaed,0xd388,0x06bd,0x72bf,0x1376,0xe12a}, new char[]{0x009a,0x62c6,0x131d,0xc0c2,0xb3f1,0x94fe,0xf59a,0x1d99,0xd716,0x5189,0x4216,0xa8e1,0x7f86,0x6ee6,0xa604,0x33f7,0xc9c7,0x77f0,0xc0f6,0x167c,0xe142,0x219d,0xfedf,0x3a92,0xd3fa,0x1267,0x7db0,0x2e1a,0x1f36,0x8664,0x6e63,0x8beb,0x8216,0xab94,0x5698,0x6a1e,0x2343,0x8f07,0x80ac,0x3b1b,0x929a,0xed0a,0xc2b9,0x042c,0x06ce,0x131d,0x4f11,0x16c5,0x048d,0xe082,0xc6dd,0x8bd6,0x72d0,0x3238,0x6187,0xf454,0xffc4,0x75e1,0x1936,0xd650,0x90f2,0x5ca9,0xba84,0x0c43,0xdc9a,0x0cc8,0x530e,0x4a4f,0xf941,0xdc9f,0x7033}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7f7e,0xe7b0,0x0996,0x65f9,0xdf34,0xadce,0xc858,0x48d2,0x2bce,0x5c54,0x9b3f,0x52dc,0xa954,0x8d1e,0xf94a,0x0a9d,0x4f22,0xcce9,0xf590,0xf9f4,0xfcab,0xa5a6,0x398a,0x95a4,0x3c02,0x4094,0xd338,0x0c17,0x57ef,0x2834,0x834c,0xca4a,0x54a7,0xc8ac,0x7534,0x3255,0x791b,0xd37a}, new char[]{0x09cd,0x54f2,0xdf40,0x8d77,0x0af1,0x2ba7,0xf980,0x0c6e,0x5c19,0x7974,0x4f46,0xd365,0xc878,0x7f1c,0x48be,0xd315,0x95c7,0xca21,0xf967,0x39f9,0xcc81,0x6598,0xadaa,0xe7d5,0x9b4d,0x836c,0x3231,0x3c6b,0xa5d5,0x52bd,0x40f6,0xf5fc,0x578a,0x7550,0xfc91,0xc88c,0xa92f,0x2849}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ba8,0xfe5c,0xca8b,0xaad5,0xaee6,0x4f3e,0x22e8,0x650c,0xf965,0x4282,0xacc6,0xabbf,0xc803,0x2246,0x44ee,0xba61,0x27da}, new char[]{0x228d,0xae95,0xabcf,0xace9,0xf907,0x6560,0x27b5,0xcae8,0x4f55,0xba3e,0xaaa6,0x222e,0xc862,0x42e6,0x1bcd,0x449c,0xfe03}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6570,0x93e5,0x4c1d,0x3994}, new char[]{0x9390,0x4c50,0x6526,0x39c4}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd6e2,0x7635,0x3ab6,0x1efa,0x868e,0x85b4,0x5420,0x5665,0x662d}, new char[]{0x7658,0x5604,0x85c7,0x544b,0xd6cc,0x665b,0x86eb,0x1e88,0x3ac2}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c93,0x486a,0x4650,0x5dca,0xe8f8,0x55f8,0xdc3a,0xdc23,0x19e4}, new char[]{0xdc57,0x480b,0xe88b,0x8cf8,0x467e,0x5dac,0x1996,0x5599,0xdc44}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x81bd,0xecd4,0xb61c,0x94fa,0xe55d}, new char[]{0xecb5,0x81e2,0x948a,0xe532,0xb66f}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc953,0xb509,0x1c14,0xdd54,0xd02a,0xa98b,0xd028,0x051a,0x44a5,0x3b03,0xe793,0x5d98,0x33a0,0x62f9,0xc35c,0xa0ad,0xd526,0x36e1,0x9f28,0xd66f,0x132d,0x4d92,0x097f,0xd5a2,0xea1e,0x07a3,0xbf7c,0x1cb8,0x69f2,0xa0db,0x12bc,0x5ced,0xed4e,0x7388,0x88a6,0x203b,0x1387,0x2467,0xa449}, new char[]{0x0924,0x1c41,0xa9ff,0xd606,0x699e,0xea77,0x204f,0x5c94,0x242a,0xed21,0x3685,0x44f8,0x3380,0xdd36,0x5df4,0x0575,0xc930,0x07c8,0xd58f,0xd045,0xa0cc,0xb57a,0x4df9,0xd506,0x88ca,0x6290,0x9f46,0xa0b0,0xd00a,0x1cde,0x13e6,0xbf15,0xe7ff,0x73ed,0x1349,0xa473,0x129c,0xc327,0x3b7e}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6a5d,0x4b1f,0x116c,0xc418,0x17fe,0xe467,0xa504,0xd35b,0x0140,0x3a90,0x8470,0xad2b,0x8feb,0x67e0,0xcff9,0xbbee,0xba27,0x5abd,0x4fef,0x449e,0xf3b6,0x099c,0x605b,0x99ea,0xe0c7,0x9b92,0xd496,0x067c,0x0734,0x7206,0x343d,0x29be,0xa804}, new char[]{0x6074,0xcf98,0xd328,0xe414,0xc47d,0xe0b3,0x4f9c,0x1143,0x8405,0x8f9f,0xba4e,0x678c,0x726f,0x3449,0x9beb,0x012d,0x0613,0xa860,0xa52b,0x0747,0x5ad5,0x998b,0x44fa,0x29db,0xd4e4,0x3ae3,0xad04,0xbb89,0xf3da,0x179f,0x4b6c,0x09ef,0x6a72}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe6ca,0xceaa,0x943a,0xa4bd,0x1939,0xaa87,0x1c57,0x1ddd,0x9588,0x0700,0xa064,0x7b3f,0xa651,0xebf8,0x91ac,0xe78b,0x03bf,0x3a79,0x2f0a,0x5b0e,0x52b7,0xd061,0x41da,0x3678,0x6c44,0x5833,0x27f6,0x569a,0x2863,0x2d21,0x866c,0xf25b,0x7d79,0x5d28,0x8860,0x5f31,0x3d36}, new char[]{0x1962,0x03ea,0xa4c9,0xf232,0x2f66,0x9453,0x3d42,0xeb81,0xcee7,0x280c,0x8608,0xa039,0x1c77,0x361a,0x2d4d,0x91c3,0x7b5c,0x880b,0x27db,0x5f5c,0x41bb,0x5840,0x52dc,0x5d08,0xe6b9,0x7d11,0x6c25,0x0764,0x95ed,0xd013,0xa671,0x3a02,0x5b73,0xaabd,0x56ba,0x1da6,0xe7f6}).plainString();
    }
}
