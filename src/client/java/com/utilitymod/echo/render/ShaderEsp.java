package com.utilitymod.echo.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.utilitymod.UtilityMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Shader ESP: runs one of the Icons Pack post shaders (outline / rainbow /
 * aura / chams, all on post.vert) over Minecraft's entity-outline
 * framebuffer, which holds a white silhouette of every forced-outline entity.
 *
 * <p>Vanilla's own outline composite is skipped while active (patch on
 * WorldRenderer.drawEntityOutlinesFramebuffer). In the HUD pass the shader
 * renders into an offscreen texture owned by a {@link NativeImageBackedTexture}
 * (so the GUI can draw it by Identifier), then that texture is blitted over
 * the screen. Raw GL is bracketed by a full save/restore of the state it
 * touches, through GlStateManager where Minecraft caches state.
 */
public final class ShaderEsp {
    private static boolean warnedFbo;

    public enum Style { OUTLINE, RAINBOW, AURA, CHAMS, GLOW, LIQUID_GLASS, CHROMA, PULSE, HOLOGRAM, STARFIELD }

    private static final Identifier OUT_ID = Identifier.of(UtilityMod.MOD_ID, LT.L1);
    private static final Map<Style, Integer> PROGRAMS = new EnumMap<>(Style.class);
    private static final long START = System.nanoTime();

    private static boolean failed;
    /** Last step reached / failure reason, shown on screen while Shader mode is on. */
    private static volatile String status = LT.L2;
    private static int vao, vbo, fbo;
    private static NativeImageBackedTexture out;
    private static int outW, outH;

    /** Installed by PlayerESP: shader mode on? (null = off). */
    public static volatile java.util.function.BooleanSupplier activeWhen;
    /** Installed by PlayerESP: copies its settings into the fields below. */
    public static volatile Runnable settings;
    public static volatile Style style = Style.OUTLINE;
    public static volatile int color = 0xFF8C0000;
    public static volatile float radius = 10f;
    public static volatile float fill = 0.15f;
    public static volatile int quality = 4;

    private ShaderEsp() {}

    /** WorldRenderer.drawEntityOutlinesFramebuffer HEAD: true = skip vanilla's composite. */
    public static boolean replaceVanillaComposite() {
        return active() && !failed;
    }

    private static boolean active() {
        java.util.function.BooleanSupplier a = activeWhen;
        return a != null && a.getAsBoolean();
    }

    /** HUD pass: render the effect and draw it over the screen. */
    public static void render(DrawContext ctx) {
        if (!active()) return;
        try {
            renderInner(ctx);
        } finally {
            // status is kept for the log only
        }
    }

    private static boolean createTried;

    /** The real entityOutlineFramebuffer field (the public getter is a frame-graph handle, null outside render). */
    private static Framebuffer outlineField(MinecraftClient mc) {
        try {
            return (Framebuffer) com.utilitymod.echo.api.Accessors.field(net.minecraft.client.render.WorldRenderer.class,
                    LT.L3, LT.L4).get(mc.worldRenderer);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void createOutlineFramebuffer(MinecraftClient mc) {
        if (createTried) return;
        createTried = true;
        try {
            com.utilitymod.echo.api.Accessors.method(net.minecraft.client.render.WorldRenderer.class, 0,
                    LT.L5, LT.L6).invoke(mc.worldRenderer);
            UtilityMod.LOGGER.info(LT.L7);
        } catch (Throwable t) {
            fail(LT.L8 + t);
        }
    }

    /** WorldRenderer.render: loadPostEffect(ENTITY_OUTLINE) redirect. null skips vanilla's edge/blur pass
     *  so the buffer keeps the filled white silhouettes the Icons Pack shaders expect. */
    public static Object outlinePostEffect(Object loader, Object id, Object set) {
        if (active() && !failed) return null;
        return ((net.minecraft.client.gl.ShaderLoader) loader).loadPostEffect(
                (net.minecraft.util.Identifier) id, (java.util.Set<net.minecraft.util.Identifier>) set);
    }

    private static boolean canDrawOutlines(MinecraftClient mc) {
        try {
            return (boolean) com.utilitymod.echo.api.Accessors.method(net.minecraft.client.render.WorldRenderer.class, 0,
                    LT.L9, LT.L10).invoke(mc.worldRenderer);
        } catch (Throwable t) {
            return true; // unknown: try anyway
        }
    }

    private static void fail(String why) {
        status = LT.L11 + why;
        if (!failed) UtilityMod.LOGGER.warn(LT.L12, why);
        failed = true;
    }

    private static void renderInner(DrawContext ctx) {
        if (failed) return;
        Runnable s = settings;
        if (s != null) s.run();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer == null) { status = LT.L13; return; }
        Framebuffer outline = outlineField(mc);
        if (outline == null) {
            // 1.21.11 only creates it on a resource reload (loadEntityOutlinePostProcessor);
            // without it canDrawEntityOutlines() is false and no silhouettes are drawn at all.
            createOutlineFramebuffer(mc);
            status = LT.L14;
            return;
        }
        Object att = outline.getColorAttachment();
        if (!(att instanceof GlTexture mask)) {
            fail(LT.L15 + (att == null ? LT.L16 : att.getClass().getName()) + LT.L17);
            return;
        }
        int w = outline.textureWidth, h = outline.textureHeight;
        if (w <= 0 || h <= 0) { status = LT.L18 + w + LT.L19 + h; return; }
        try {
            if (!ensureResources(w, h)) { fail(LT.L20); return; }
            int program = program(style);
            if (program == 0) { fail(style + LT.L21); return; }
            if (!(out.getGlTexture() instanceof GlTexture target)) {
                fail(LT.L22 + out.getGlTexture().getClass().getName());
                return;
            }
            int scene = 0;
            if (style == Style.LIQUID_GLASS
                    && mc.getFramebuffer().getColorAttachment() instanceof GlTexture sceneTex) {
                scene = sceneTex.getGlId();
            }
            draw(program, mask.getGlId(), scene, target.getGlId(), w, h);
            int err = GL11.glGetError();
            status = LT.L23 + style + " " + w + LT.L19 + h + LT.L24 + mask.getGlId() + LT.L25 + target.getGlId()
                    + (err != 0 ? LT.L26 + err : "");
        } catch (Throwable t) {
            fail(t.toString());
            return;
        }
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, OUT_ID, 0, 0, 0f, 0f, sw, sh, w, h, w, h);
    }

    /**
     * Runs a style over any silhouette mask (white = inside) into {@code outTex}. Used by Block ESP's
     * shader mode. Returns false if the style's program is unavailable.
     */
    public static boolean processMask(int maskTex, int outTex, int w, int h, Style st, int col, float rad, float fil) {
        if (failed || !ensureVao()) return false;
        int program = program(st);
        if (program == 0) return false;
        float pr = radius, pf = fill;
        radius = rad;
        fill = fil;
        try {
            draw(program, maskTex, 0, outTex, w, h, st, col);
        } finally {
            radius = pr;
            fill = pf;
        }
        return true;
    }

    private static boolean ensureVao() {
        return vao != 0 || ensureResources(1, 1);
    }

    private static boolean ensureResources(int w, int h) {
        if (vao == 0) {
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            fbo = GL30.glGenFramebuffers();
            int prevVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevVbo = GlStateManager._getInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GlStateManager._glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            // Fullscreen quad (2 triangles): pos.xyz + uv. Y is flipped so the output texture's
            // row 0 is the screen top, matching how the GUI samples textures.
            float[] quad = {
                    -1f, 1f, 0f, 0f, 0f,   1f, 1f, 0f, 1f, 0f,   1f, -1f, 0f, 1f, 1f,
                    -1f, 1f, 0f, 0f, 0f,   1f, -1f, 0f, 1f, 1f,  -1f, -1f, 0f, 0f, 1f};
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buf = stack.mallocFloat(quad.length);
                buf.put(quad).flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
            }
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 20, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 20, 12);
            GlStateManager._glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
        }
        if (out == null || outW != w || outH != h) {
            if (out != null) out.close();
            out = new NativeImageBackedTexture(OUT_ID::toString, new NativeImage(w, h, true));
            MinecraftClient.getInstance().getTextureManager().registerTexture(OUT_ID, out);
            outW = w;
            outH = h;
        }
        return true;
    }

    private static void draw(int program, int maskTex, int sceneTex, int outTex, int w, int h) {
        draw(program, maskTex, sceneTex, outTex, w, h, style, color);
    }

    private static void draw(int program, int maskTex, int sceneTex, int outTex, int w, int h, Style st, int col) {
        // ---- save ----
        int prevFbo = GlStateManager._getInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevProgram = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GlStateManager._getInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int prevTex1 = -1;
        // Blaze3D leaves sampler objects bound; they override texture filtering (mask read as empty)
        int prevSampler0 = GlStateManager._getInteger(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING);
        org.lwjgl.opengl.GL33.glBindSampler(0, 0);
        org.lwjgl.opengl.GL33.glBindSampler(1, 0);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, outTex, 0);
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE && !warnedFbo) {
                warnedFbo = true;
                UtilityMod.LOGGER.warn(LT.L27, Integer.toHexString(status));
            }
            GlStateManager._viewport(0, 0, w, h);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL20.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            if (sceneTex != 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                prevTex1 = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTex);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
            }
            uniforms(program, w, h, st, col);
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        } finally {
            // ---- restore ----
            org.lwjgl.opengl.GL33.glBindSampler(0, prevSampler0);
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProgram);
            if (prevTex1 >= 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex0);
            GL13.glActiveTexture(prevActive);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GlStateManager._viewport(vp[0], vp[1], vp[2], vp[3]);
            if (blend) GL11.glEnable(GL11.GL_BLEND);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private static void uniforms(int p, int w, int h, Style st, int col) {
        float a = ((col >>> 24) & 255) / 255f, r = ((col >> 16) & 255) / 255f;
        float g = ((col >> 8) & 255) / 255f, b = (col & 255) / 255f;
        set1i(p, LT.L28, 0);
        set2f(p, LT.L29, 1f / w, 1f / h);
        set4f(p, LT.L30, r, g, b, a);
        int q = Math.max(1, Math.min(12, quality)); // outline.frag uses arrays of 16 (3 + quality)
        switch (st) {
            case OUTLINE -> {
                set1i(p, LT.L31, q);
                set1f(p, LT.L32, radius);
                set1f(p, LT.L33, fill);
            }
            case RAINBOW -> {
                set1f(p, LT.L34, (System.nanoTime() - START) / 1e9f);
                set1f(p, LT.L35, 1f);
                set1f(p, LT.L36, 0.8f);
                set1f(p, LT.L37, 1f);
                set1f(p, LT.L38, radius);
            }
            case AURA -> {
                set1i(p, LT.L31, q);
                set1f(p, LT.L32, radius);
                set1f(p, LT.L33, fill);
                set1f(p, LT.L39, 1.5f);
                set1f(p, LT.L40, 0.6f);
                set1f(p, LT.L41, 0.5f);
            }
            case GLOW -> {
                set1i(p, LT.L31, q);
                set1f(p, LT.L32, radius);
                set1f(p, LT.L33, fill);
            }
            case LIQUID_GLASS -> {
                set1i(p, LT.L42, 1);
                set1f(p, LT.L32, radius);
                set1f(p, LT.L33, fill);
                set1f(p, LT.L34, (System.nanoTime() - START) / 1e9f);
            }
            case CHROMA, PULSE, HOLOGRAM, STARFIELD -> {
                set1f(p, LT.L32, radius);
                set1f(p, LT.L33, fill);
                set1f(p, LT.L34, (System.nanoTime() - START) / 1e9f);
            }
            case CHAMS -> {
                set1f(p, LT.L43, Math.max(fill, 0.35f));
                set1f(p, LT.L44, Math.max(1f, radius * 0.3f));
            }
        }
    }

    private static void set1i(int p, String n, int v) { int l = GL20.glGetUniformLocation(p, n); if (l >= 0) GL20.glUniform1i(l, v); }
    private static void set1f(int p, String n, float v) { int l = GL20.glGetUniformLocation(p, n); if (l >= 0) GL20.glUniform1f(l, v); }
    private static void set2f(int p, String n, float x, float y) { int l = GL20.glGetUniformLocation(p, n); if (l >= 0) GL20.glUniform2f(l, x, y); }
    private static void set4f(int p, String n, float x, float y, float z, float w) { int l = GL20.glGetUniformLocation(p, n); if (l >= 0) GL20.glUniform4f(l, x, y, z, w); }

    private static int program(Style s) {
        Integer cached = PROGRAMS.get(s);
        if (cached != null) return cached;
        String frag = switch (s) {
            case OUTLINE -> LT.L45;
            case RAINBOW -> LT.L46;
            case AURA -> LT.L47;
            case CHAMS -> LT.L48;
            case GLOW -> LT.L49;
            case LIQUID_GLASS -> LT.L50;
            case CHROMA -> LT.L51;
            case PULSE -> LT.L52;
            case HOLOGRAM -> LT.L53;
            case STARFIELD -> LT.L54;
        };
        int vs = compile(GL20.GL_VERTEX_SHADER, LT.L55);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, frag);
        int p = 0;
        if (vs != 0 && fs != 0) {
            p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vs);
            GL20.glAttachShader(p, fs);
            GL20.glBindAttribLocation(p, 0, LT.L56);
            GL20.glBindAttribLocation(p, 1, LT.L57);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
                UtilityMod.LOGGER.warn(LT.L58, frag, GL20.glGetProgramInfoLog(p));
                GL20.glDeleteProgram(p);
                p = 0;
            }
        }
        if (vs != 0) GL20.glDeleteShader(vs);
        if (fs != 0) GL20.glDeleteShader(fs);
        PROGRAMS.put(s, p);
        return p;
    }

    private static int compile(int type, String file) {
        String src;
        try (InputStream in = ShaderEsp.class.getResourceAsStream(LT.L59 + file)) {
            if (in == null) throw new IllegalStateException(LT.L60 + file);
            src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            UtilityMod.LOGGER.warn(LT.L61, file, e.toString());
            return 0;
        }
        int sh = GL20.glCreateShader(type);
        GL20.glShaderSource(sh, src);
        GL20.glCompileShader(sh);
        if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
            UtilityMod.LOGGER.warn(LT.L62, file, GL20.glGetShaderInfoLog(sh));
            GL20.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0eb4,0xeb15,0x030e,0x00d4,0xb968,0x1490,0x5521,0xba3e,0xcee4,0xd5ec,0x8ba1,0x6d56,0x6c35,0xca8e}, new char[]{0x8bc4,0x6d25,0x14e0,0x6c1a,0xd59f,0x5549,0x00b5,0xcaea,0xba5b,0x0ec6,0xcebb,0x0361,0xb91d,0xeb61}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcf98,0xe683,0x26b1,0x02eb}, new char[]{0xe6ea,0xcffc,0x26dd,0x028e}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa3cf,0x41b6,0x4132,0x3a4e,0x23ea,0xfff9,0xf267,0x918c,0x6359,0x8021,0xa0fe}, new char[]{0xa3a9,0xa097,0x4157,0x2386,0x3a2a,0xffa6,0x91b9,0x8012,0x6369,0x418e,0xf257}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c2e,0x4679,0xd965,0xf158,0xe2c6,0x6846,0x8236,0x4e0a,0x968c,0xe763,0x2b5c,0xae3e,0x7245,0x1554,0x94b3,0x060d,0xcef6,0x3b8b,0xef02,0x1e25,0x2ac0,0xddf3,0x72aa,0x433a}, new char[]{0x8253,0x4e64,0x0679,0xe2af,0x1520,0x2ab9,0x3bc4,0x96f9,0xd911,0x2b30,0x722c,0x1e4b,0x94d6,0xe725,0x4348,0x72cb,0xae53,0xf13d,0x461b,0x6833,0xdd95,0xce90,0x4c4b,0xef70}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x108d,0x393d,0x5705,0x141a,0xecbc,0x604e,0x9fce,0x8455,0x0237,0xecd8,0x4185}, new char[]{0x025a,0x5760,0x41f1,0x10e5,0x3952,0x147e,0xece3,0x8466,0xecea,0x6077,0x9ff8}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xed59,0xcab2,0xd7bd,0xd134,0x5e3a,0x8f12,0xd897,0x1d5a,0xd327,0x7f12,0x43c5,0x81a0,0x2ae3,0x0c8b,0xccd9,0x0ce5,0xfb3b,0xd17d,0x7785,0x9a7b,0x813f,0x3ee4,0xd9d0,0xfcee,0x2088,0x88fe,0xe9d4,0x8dc5,0x6d83,0x6782}, new char[]{0xd8fb,0x8f7d,0xfc8f,0x6de7,0x9a3e,0xd349,0x1d2e,0x20e1,0x67f6,0xd14d,0xcc96,0xd7c8,0xcac6,0x7f7e,0x3e8d,0x77eb,0x43a0,0x2ab3,0xed36,0x814c,0x0cff,0xe984,0x8db7,0x8891,0x0c86,0x81c5,0xd9a3,0xd10e,0xfb54,0x5e48}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd121,0xb17b,0x37cc,0x37cb,0x1c9c,0x0b3d,0x37d3,0xa8ad,0x82d9,0xb16a,0x226a,0xd15a,0xd11e,0x226c,0xbc0a,0xbc00,0xa8b9,0x0b3e,0x0b28,0x1c94,0x1c97,0x0b30,0x82d8,0x37d2,0xd11f,0x9627,0x2238,0xb16a,0xa89e,0x82d4,0x1cbf,0xbc08,0x9626,0x82c4,0xa896,0xbc06,0xb166,0x962c,0x9625,0x2238}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6188,0x373d,0x8ce7,0x19ec,0x7ee4,0x185c,0x9ac8,0x1927,0x9f18,0x85e1,0x4a31,0x03fc,0xe350,0x4601,0x34ea,0xc7ad,0xa0f1,0xfc24,0xee7e,0xb86a,0x1e84,0xca64,0xf1b7,0xa99a,0x0f09,0x7477,0xbb1f,0xbb2f,0x0f78,0x9997,0xeec6,0x6cea,0x144f,0x9df9,0x019b,0x8af0,0x1b0b,0x9f73,0xab74,0x0e85,0x4c54,0xfc7c,0x27d5,0x0e43,0xb133}, new char[]{0xeea5,0x1983,0x9f06,0xbb73,0x9d9d,0x8cc7,0x4a5f,0x0f66,0x9f6c,0x1ea4,0x7e87,0xfc0e,0x0f1d,0x8580,0x0388,0xb156,0xfc04,0x8a95,0xca0a,0xa085,0x0eec,0x4675,0x99ee,0x9ae8,0x4c3b,0x01ee,0x349e,0xbb43,0x194e,0x61e6,0xee1b,0x0e63,0xf1d1,0xc7df,0xab15,0x3750,0x1839,0xa9f8,0x6c9f,0x1b6d,0x7411,0x142a,0xb818,0xe36a,0x27f5}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x12ef,0xeb57,0xcb0a,0xaff8,0x4151,0x0696,0xce1a,0x842b,0x79e0,0x45a9,0x76bb}, new char[]{0xeb3a,0x76de,0x45dd,0xcb62,0xce75,0x7984,0x8474,0xafcb,0x06a4,0x12d8,0x4161}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x70ba,0x30ff,0xd8db,0xf522,0x432e,0x46b4,0x4244,0xb940,0x4938,0x7048,0x1da6,0xf54d,0x0e7d,0x4c4e,0x36fc,0xf64e,0xe210,0xbe3a,0x8aa4,0x06b3,0x192f}, new char[]{0x4227,0x7029,0x1941,0xbe7e,0x435c,0x0e1c,0xe267,0xf567,0x1dc8,0x70ce,0x8acd,0x46c0,0x06ca,0xf601,0xf538,0x3688,0x4c22,0x3096,0xd8b5,0xb925,0x494b}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc80,0x97b9,0xec9e,0xf96b,0x064d,0x8c8c,0x7267,0x5b2c,0x4ddf}, new char[]{0xecd8,0x060c,0xf922,0xfccc,0x97fc,0x5b68,0x7247,0x4df2,0x8cac}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa896,0x2263,0x1c9c,0xbc00,0xb166,0x37cb,0xbc14,0x226c,0xbc06,0x0b28,0xa89e,0xd11e,0xa8eb,0x82c4,0x82d4,0x0b30,0x2238,0xd140,0x1cbf,0x962c,0x9626,0x37d2,0xd121,0xb17b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b30,0x0b2e,0xd114,0x1cd2,0x37d1,0xb16a,0x9631,0xbc0c,0xbc1e,0xa8a5,0xb17d,0x2238,0x82c9,0x37cc,0xa8a4,0x227c,0xd11f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd78b,0x117f,0xf450,0x5754,0x5adb,0xcb94,0x7c30,0x39db,0xf398,0xe68d,0x2975,0x80ec,0xefbc,0x8664,0xbd07,0x1be9,0x26ae,0xcfe4,0xd29c,0xdbfa,0xa6b3,0x02a4,0x8614,0xece2,0xeab7,0x3df6,0x8ab2,0xeff5,0x6f5c,0x2efe,0xa7ec,0x5d34,0x61ab,0x4a4d,0x8704,0x3fa8,0x2385,0x8d0d,0x22f0,0x8dd4,0xc825,0x7741,0x115d,0xf3bc,0xa6d1,0x3bfb,0xb24e,0xa205,0xa440,0xd6bb,0xf453,0x3f7e,0x9121,0xbced,0xf3fe,0x8616,0x9b40,0xca24}, new char[]{0x8675,0x2907,0x3b9e,0x23e4,0x1129,0x8601,0x7725,0x39fb,0xef90,0xbd69,0xa6c7,0xa429,0x26da,0xe6f4,0xf470,0x914e,0xefc9,0xca50,0xec8e,0x02cd,0x5d5a,0xa6b4,0xd2bc,0x3f18,0x9b32,0x1b88,0xf393,0x3d93,0xc847,0xcf91,0x4a2b,0x8762,0xdb9f,0xd6c9,0x80c0,0xea97,0x8d7a,0x8ad3,0xbc84,0xb23a,0xf43a,0x573a,0xd7ec,0x22d0,0x61cd,0x8dbb,0xa277,0x2ede,0x3fc6,0xa789,0xf3c4,0x5aaf,0x8634,0xcbf2,0x110d,0x7c51,0xf3f5,0x6f39}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c45,0xfac1,0xe326,0x75c4,0xeab3,0xc021,0x9b31,0x1e8d,0x035c,0x133f,0x7750,0x947a,0xa97d,0x9b23,0xfea6,0x40c2,0x35eb,0x5a07,0x7853,0x4863,0xa940,0x0094}, new char[]{0xeadc,0x1ef8,0x5a73,0x773c,0x75ad,0x00fa,0xc044,0x9b11,0xfec7,0x4817,0x9b57,0x135e,0x7c26,0x3583,0xfaac,0x0339,0xa92e,0x40b6,0xa95d,0x783a,0x9409,0xe306}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa677,0x2e81,0xad1b,0xf77e}, new char[]{0xa619,0xad6e,0x2eed,0xf712}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x12ac,0x3765,0xf292,0xda19,0x5384,0x27a8,0x99a8,0xd696,0x89f0,0x4d74,0xf907,0xdc9f,0x5406,0x22e1,0x497e}, new char[]{0xdcb3,0x5426,0xf969,0xd6f9,0x8984,0x128c,0x3722,0x228d,0x99fc,0x491b,0x53fc,0x27dc,0xda6c,0xf2e0,0x4d11}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4319,0x0695,0x5fa9,0xfde5,0x9e4b,0xd811,0x6e6c,0xead2,0xf2a8,0x169b,0x7c71,0x844f,0x4abb,0x948f,0xeb5b,0x25a1,0x2b70,0x96d3,0xc462,0xdee4,0x4071,0x72ae,0xfcda,0x956f,0x272c}, new char[]{0x25ce,0xde91,0x9e3f,0x9503,0xd878,0x8421,0x2749,0xfcfa,0x16fd,0x94fd,0x96b2,0x6e01,0xf2cd,0xfd87,0xeaa7,0x4017,0x437f,0x2b15,0x4ac9,0xeb7b,0x06e6,0x72c7,0xc418,0x5fcc,0x7c51}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0370}, new char[]{0x0308,0xfb20,0x1605,0x4699}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b35,0xd5e3,0x5dc6,0xd6ba,0xe6e5,0x04c8,0x44fe,0x05f7,0x9cf4,0xae9a,0x8df3,0xf7b6,0x23d5,0xd73f,0xcd59,0x6907,0x81c2,0x542c,0xb059,0xdf87,0x7823,0x425c,0xf608,0x65e4,0x4753,0x6c0a,0x0ae2,0x0cc2,0xf329,0x6b52,0xd5a1}, new char[]{0xd5c2,0xf346,0x0cb7,0x473f,0xd75b,0x6927,0x6c64,0x4491,0xd6ce,0xf628,0x423f,0x8d81,0xdfe2,0xd582,0x7857,0x6581,0x81e2,0xf7d9,0xcd2c,0xe691,0x0a92,0x9c81,0xaeee,0x05d7,0x1b41,0x5449,0x04b0,0x5db2,0xb02c,0x23a7,0x6b37}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5e14,0x0aa8,0xc101,0x4457,0x6d9f,0x8648,0x5482,0xd6b2,0xcf46,0x7a2a,0x740c,0x7b8b,0xc9da,0x4efe,0x65fb,0xf934,0x03a8,0x564f,0x3f70,0xf55c,0x59c2,0xa105,0xd2ad,0x6d76,0x1951,0x1c31,0xb9a6,0xd244,0xa288,0x5931,0xdf7a,0xb76b,0x4b09,0xc817,0x6632,0x72aa,0x1d08,0x1235}, new char[]{0x6dbf,0xdf09,0xa16d,0x4e9f,0x59a6,0xc872,0x4425,0xcf66,0x7468,0x6592,0x7a4e,0xb74b,0x72c4,0x3f1f,0x1925,0xf914,0x562c,0xc16e,0xd2c0,0x1c41,0x03c1,0x1259,0xb9c3,0x8667,0x0ac4,0x6d1f,0xa2e6,0xd22f,0x5911,0x1d20,0x7bf8,0x6657,0xd6d7,0x4b29,0x5e78,0xc9b5,0x54e5,0xf575}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x50c1,0xba15,0x5c03,0xff60,0x142b,0x1117,0xd48b,0xc3eb,0xfe0b,0x9798,0x1ee4,0xb68b,0x10a0,0xec88,0xd6e9,0xb6f0,0x4aa5,0x0612}, new char[]{0xb6e4,0x10d5,0x4ad1,0xb680,0x1162,0xd4ff,0x97b8,0xecfc,0x5c66,0x50b9,0xfe7f,0x0667,0xd69b,0x144e,0xba35,0xff09,0x1e97,0xc3cb}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf796,0x4768,0x1ef9,0x02df,0x3933,0xb945,0x25be,0xac6b}, new char[]{0x02bb,0x1e8b,0xac0a,0x3944,0xb92c,0x25d0,0xf7f1,0x4748}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7bb7,0x1a44,0x0ef9,0x9ec5,0x1da0,0x404b}, new char[]{0x1a64,0x7bda,0x0e98,0x4038,0x9eae,0x1d9d}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0f8,0x3563,0xce39,0x8e2a,0x8be1}, new char[]{0x8bc1,0x350c,0xc08d,0x8e5e,0xce04}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf823,0x2304,0xb171,0x5380,0x8546,0xbadc,0x7e13,0x1ad2,0x9141}, new char[]{0x7e33,0xf844,0x1abe,0x9104,0x8534,0xbaae,0x236b,0xb103,0x53bd}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x79ad,0x39fd,0x1574,0x6c12,0xbe8e,0x06bd,0x5119,0x2512,0x22d2,0x40f3,0x7770,0x8934,0x8a18,0x254e,0x7c57,0xb370,0x65e5,0x82fa,0xa00f,0x7470,0xf8ff,0x1e6d,0x6d5a,0x3887,0x9fe6,0x2fce,0xbe3d,0x450e,0xfd5e,0x4f2e,0xa662,0x2e5c,0x2b11,0x0eb3,0x3086,0xa71e,0x8365,0xa80d,0x7f0d,0x0769,0xffc7,0x1b1b,0x3086,0xd3d4,0x3b22,0xcbd4,0x951c,0x24e5,0xfb61,0xf118,0xc628,0x3368}, new char[]{0x833e,0x9fb3,0x6d2e,0x895d,0x8a74,0xf171,0x457a,0x1e14,0x30cb,0x409c,0x0ed7,0x2513,0x7f2d,0x1b5e,0xa74d,0x06ed,0xbe1d,0xa60d,0xbefb,0x30f2,0xa07f,0xffb2,0x2566,0x953c,0xf899,0x1506,0x38e6,0x4f43,0x7c32,0x070b,0x79d8,0xd3b2,0xfd38,0xa868,0x516b,0xb350,0x7419,0x2e32,0x6586,0x8295,0x22bf,0x2fbe,0x6c7e,0xcbb1,0xc65c,0x7715,0x3352,0x3b02,0xfb51,0x249d,0x2b6a,0x3980}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x683d,0xdd02,0x55ff,0x983d,0x821a,0x7f79,0xd01f,0x3e5a,0x6ced,0xe87c}, new char[]{0x9849,0x6858,0x8262,0xd04c,0xdd63,0x6c80,0xe80c,0x7f15,0x3e3f,0x558d}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e62,0xd330,0x9672,0xf57e,0xfd7d,0x2999,0x44ba,0x7a2f}, new char[]{0xf511,0x44d4,0xd355,0x29cd,0x8e07,0xfd05,0x7a4a,0x961e}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x514d,0x9c13,0xabdc,0x7ed9,0xa8c1}, new char[]{0x7eba,0x9c7c,0xa8ad,0xabb3,0x513f}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb9fa,0x87bd,0x8283,0xb5e2,0x8f33,0xd611,0xc224}, new char[]{0xb98b,0x82f6,0x87dc,0xc248,0xd678,0xb596,0x8f4a}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa382,0x0942,0x99ef,0x0133,0x8239,0xe01c}, new char[]{0x824b,0xe07d,0x0926,0xa3eb,0x0146,0x999c}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6269,0xc85f,0x74d7,0x0952}, new char[]{0x0934,0x6200,0x74bb,0xc833}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73b6,0x776e,0xc887,0x1008}, new char[]{0x73c2,0x1061,0xc8ea,0x770b}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2b25,0xa1f3,0x9178,0xccb8,0x45ec,0xf218,0x6dfe,0xd25b,0xfb99,0xa0ea,0xcb01,0x85f0}, new char[]{0x8582,0xf279,0xccd1,0xfbf7,0x911a,0xcb6e,0x2b52,0x6dad,0xd22b,0xa08f,0x4589,0xa197}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7013,0x290c,0x2cf9,0xf278,0xf48b,0x615e,0xfb69,0x7ef1,0xf7b6,0xe395}, new char[]{0xf7c5,0x7e90,0xe3e1,0x7066,0xf20a,0xf4ea,0x2c8d,0x6137,0x2963,0xfb07}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06f9,0x34ae,0x9b30,0xdd36,0x62d4,0x072a,0x7c99,0x606d,0xa653,0xc33f}, new char[]{0x0748,0x601f,0xdd5f,0xa634,0x0691,0xc34b,0x9b5e,0x62b1,0x34dd,0x7cea}).plainString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd48a,0xb53e,0xe50c,0x2532}, new char[]{0x2541,0xe565,0xd4f0,0xb55b}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x978e,0x7df0,0x92c8,0x7527,0x9c8b,0x552f,0x5906,0xe3fe,0xbcdb}, new char[]{0x596a,0x754e,0x7d9e,0x554a,0x929f,0xbcb2,0x97ea,0xe38a,0x9ce3}).plainString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3e8,0x8a84,0x61cf,0x0cdc,0x8eab,0x06c4,0x4577,0x3765,0xa5f7,0x9aa8,0xe2bf}, new char[]{0x451f,0x8eca,0x9ac4,0x0cb3,0x8acb,0x61bf,0x3704,0xe2dc,0xa59e,0x06b0,0xd391}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc3ad,0xd0a3,0x132b,0xc5f3,0x6d73,0x6de7,0x1675,0xca95,0x622a}, new char[]{0xc590,0xc3c2,0x6258,0xd0c6,0x1637,0x1344,0xcafa,0x6d94,0x6d07}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xce05,0x811a,0x6683,0xe126,0x95ba,0x10d6,0xfeb6,0x9e9b,0x6b40,0xa27f,0xc1b6,0xd465}, new char[]{0x6b33,0xa21c,0xe143,0xce6b,0x66e6,0x9ec8,0xc1d7,0x10bb,0xfec6,0x95d6,0xd400,0x8168}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb6c6,0x0037,0x84b5,0x6977,0xa2d8,0xcc29,0xfd51}, new char[]{0xb6a9,0x0047,0x84d4,0xfd32,0x691e,0xa2ac,0xcc50}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x670a,0x91b4,0x57d4,0x583a,0xb557,0xb302,0x89e3,0x3805}, new char[]{0xb370,0x898a,0x3868,0x675d,0xb53e,0x91d0,0x584e,0x57bc}).plainString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3e32,0xe8ac,0x1437,0x2ace,0xa5f8,0x3d6f,0x1b7c,0xd8be,0xf676,0x2c01,0x85b7,0x9308}, new char[]{0x3e5d,0x3d1a,0x2aba,0x9364,0x2c68,0xd8d0,0xa59d,0xf658,0x1b1a,0x85c5,0xe8cd,0x1450}).plainString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5544,0x5e2d,0x35ac,0xdd41,0x9fcb,0x61e2,0x1893,0x6102,0x9537,0xf014,0xa9f8,0xd386,0xe7b1,0x4dbf,0x9a37,0x5430,0xcb68,0x8fd9,0xab93,0x3d15}, new char[]{0x6190,0x9a56,0x5459,0x9559,0xabf1,0xdd2e,0x5e5a,0x551b,0xcb07,0xd3f3,0xe7c5,0x8fb5,0xf07d,0x9fa5,0x18f6,0x3d3b,0x4dd9,0x35de,0x6163,0xa99f}).plainString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73ea,0xbd28,0xac56,0xf2e5,0xf267,0x84c1,0xd2c3,0x8430,0x4078,0x6888,0xc577,0x639f,0xa38c,0xcbdf,0x59d1,0x23e1,0x83cd}, new char[]{0x59b0,0xa3f9,0x2393,0x738b,0x4027,0x84ae,0xcbaa,0x63eb,0xbd44,0xf20e,0xc519,0xac33,0x83e3,0x68ee,0xd2b1,0x8451,0xf282}).plainString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2fe2,0x5ea3,0x1296,0x0d8b,0x211f,0x27b8,0xf42a,0xee9d,0x87a9,0x17c0,0xd1b3,0xa422,0x3615,0x2aa6,0x6e33,0xf70d,0x77ac,0xcca8}, new char[]{0xf76e,0xccc0,0x217e,0xf447,0x77df,0x6e6c,0xa44d,0x3660,0x2f96,0x2aca,0x27d1,0xd1dd,0x5ec6,0xeeb3,0x17a6,0x0df9,0x12f7,0x87ce}).plainString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa5a0,0xf7e9,0x8425,0x0e14,0xf224,0x02ea,0x4a84,0xc935,0x2075,0x4fd5,0xbdff,0x2d38,0xd1f0}, new char[]{0x028d,0x4ae8,0xd19f,0xf253,0x0e4b,0x2010,0x4fa6,0x8455,0x2d16,0xc953,0xa5d2,0xbd9e,0xf78e}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x605e,0xa90b,0xb6b6,0xf998,0x342c,0xb4c8,0x2b74,0xf52e,0xaa22,0xedd7,0x7b27,0x7d3a,0x42a6,0x4870,0x09e4,0xbdd3,0x30f9}, new char[]{0x6032,0x098d,0xa97a,0xb6c3,0xaa4b,0x309d,0x7b78,0x4817,0xedbb,0x344d,0xf9eb,0x7d49,0x2b5a,0xf548,0xbda1,0x42c7,0xb4af}).plainString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x375b,0xdf45,0xf392,0x1da6,0x56de,0xe02b,0xbcf3,0xc577,0xcd51,0x260b,0x645a}, new char[]{0xcd32,0x6432,0x1dd4,0x3734,0xf3ff,0x56bf,0xc559,0x266d,0xe059,0xdf24,0xbc94}).plainString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b20,0x1ba6,0x2f65,0x8999,0x706f,0x374d,0x21f6,0x6f9f,0xc56f,0x632e}, new char[]{0x89e9,0xc51a,0x4b4c,0x2f16,0x700a,0x21d8,0x372b,0x1bd4,0x6ffe,0x6349}).plainString();
        static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf56b,0x4be8,0xfdcf,0x5bf9,0xc471,0x1fe8,0xfb16,0xcf4d,0x144d,0xba79,0x097f,0x4557,0xf42f}, new char[]{0xf503,0xba16,0xfb7a,0x1422,0x4b8f,0x090d,0x1f89,0xc41c,0x5bd7,0xfda9,0xf45d,0x4536,0xcf2a}).plainString();
        static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3360,0xc933,0x510f,0x7b8c,0x54e3,0x4722,0x7987,0x2e8c,0xfadf,0x4f9c,0x8edf,0x9163,0xfb43,0x3d9b}, new char[]{0xfb30,0x3314,0x8ebe,0xfaad,0x5169,0x474b,0x2ee9,0x910f,0x4ff8,0x7ba2,0x79e1,0x5491,0xc952,0x3dfc}).plainString();
        static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xff21,0x9443,0xd323,0xe94a,0x2d44,0x1ddf,0x6913,0x8ecf,0xfb9e}, new char[]{0xe93a,0x697c,0x1dac,0xff55,0xfbb0,0x9435,0xd346,0x8ebd,0x2d30}).plainString();
        static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb796,0x2328,0xc9d6,0x5e6e,0x4ea4}, new char[]{0x2349,0x4efb,0xb7e6,0x5e01,0xc9a5}).plainString();
        static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf0fb,0xdbbf,0xc836,0xa18f}, new char[]{0xdbde,0xf0a4,0xa1fa,0xc840}).plainString();
        static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4296,0x3d3b,0xf678,0xa43b,0xc19a,0xc9b5,0x1da4,0x9852,0x1908,0x126c,0xce27,0x1c5d,0xeff1,0xfcc6,0x8ee9,0xb98e,0x37bc,0xbe84,0xcad3,0x47c0,0xabb2,0xa1a9,0x84af,0x9e3f,0x0a6f,0xcf3d,0x1e7f,0x3915,0x0199,0x1c88,0x0e8d,0x7005,0xeda3,0xaef2,0xe3ef,0xd350,0x2950,0x5f66,0xec93,0x1d11,0xf386,0x5a43}, new char[]{0x290b,0xaea7,0x1c29,0x1961,0xd33c,0x397c,0x37c8,0xe396,0xf3cb,0x1e10,0xbee0,0x479d,0x1d84,0x5f23,0xb9dd,0xc1ca,0x5a63,0x9e4c,0x983a,0xfca7,0x3d5f,0xedc6,0x42e4,0x8ec9,0xef8a,0x1211,0xcf1d,0xa1c5,0x0ee4,0xecfd,0xf613,0xa41b,0xcab5,0x84ce,0xc9dc,0x1ce4,0x7060,0x0a0b,0xab88,0x1d31,0xce5c,0x01e4}).plainString();
        static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfad8,0x4fae,0x0353,0x775b,0xe2d4,0xde9d,0xc7e6,0xa8e9,0xed85,0x996f,0xbce5,0xab93,0x2695,0x89c4,0xc57d,0x8bdc,0x9ad5,0xcd20,0x2c60,0xf46c,0xc7a0,0x80f6,0xeecd,0x3c73,0xf39f,0x2e03,0xeac5,0x18cb,0xc56a,0xaef1,0xc4a1}, new char[]{0xedaa,0xdefc,0x18b8,0xc4d2,0x773e,0xeeb9,0x2c13,0xbcca,0xf3ea,0x991b,0x26fc,0xeaa9,0xc78f,0x8ba8,0xc504,0xae9c,0x2e6c,0xabf7,0xc545,0x0320,0xf404,0xc7c1,0xfabc,0x4fcb,0x8084,0x9aa6,0x89eb,0xcd45,0xa89a,0x3c03,0xe2fb}).plainString();
        static final String L60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8a84,0x5ea1,0xfbc9,0x6728,0x3111,0x880b,0xed8b,0x4451}, new char[]{0xede6,0x8862,0x5ed2,0x3162,0x8aed,0x443f,0x674f,0xfbe9}).plainString();
        static final String L61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a6f,0x86e5,0xce2b,0x8571,0xfd86,0x476c,0x540d,0x10a7,0x99b7,0x1b46,0xc740,0x2e00,0x66d0,0xd932,0x3168,0x011b,0x8a89,0xceb5,0xba8d,0x1902,0x2b23,0xd24e,0xa517,0x3a92,0x2ce8,0x1df1,0x369b,0x115c,0x4bcb,0xaf8e,0xc716,0xed84,0xfeaf,0x1758,0xd392,0x1574,0xa933,0x442d,0x3e29,0x41ad,0x0df9}, new char[]{0xafd5,0x1da4,0xedf0,0x1135,0xbae1,0x10ce,0x172c,0x197b,0xcef8,0xfec0,0xfde2,0x4731,0x2a4f,0x2cad,0x3e7a,0xc710,0x440d,0x2b50,0x2e68,0x8ae8,0x017f,0x5468,0xd940,0x3ab2,0x4bb0,0x41d0,0x66f0,0xce5e,0x3106,0x36e9,0xa956,0xa576,0xd22a,0x1b27,0xc774,0x1518,0x8680,0x854b,0x0dd9,0x99cc,0xd3ef}).plainString();
        static final String L62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7438,0x2d88,0x0e48,0x5be9,0x200e,0xa73b,0xb8b1,0xc53e,0xc2cd,0xffc1,0x6b19,0x3499,0x4b0c,0x9b91,0xc7fe,0x8d18,0x9a61,0x3522,0x0b8c,0x0f24,0x7d07,0xb63e,0x8f58,0xebbb,0x1815,0xf0b3,0xb730,0x0f08,0x0916,0x3b2e,0x903a,0x8c69,0x687b,0xf2e7,0xf91c,0xdf25,0x397d,0x951a,0x3b77,0xeb7a,0xcc9a,0xa579,0x832f,0x3600,0x85f9}, new char[]{0xc7a5,0xa76e,0x8c1d,0xebd2,0x3b42,0xdf4c,0xc2b9,0xeb03,0x0f45,0x6814,0x6b7d,0x4b51,0x3b57,0x8f1d,0x0bdf,0x9a31,0xf093,0xb8c2,0x8d70,0xccfb,0x2dec,0x0e2d,0x3550,0x85d9,0x0f5f,0xb74d,0x953a,0x1876,0x7d68,0xf971,0xf297,0x9053,0xffad,0x834a,0x0936,0x9bf7,0x34f8,0x7451,0x5b85,0xc55b,0xb65a,0xa543,0x395d,0x367b,0x2073}).plainString();
    }
}
