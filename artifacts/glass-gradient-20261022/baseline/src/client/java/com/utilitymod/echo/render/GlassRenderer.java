package com.utilitymod.echo.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.utilitymod.UtilityMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Liquid-glass panels for HUD elements.
 *
 * <p>Clear / Tinted are Jacquesqwq/LiquidGlassShader V2 (MIT) ported to GLSL 330, fed the
 * way its LiquidGlass.java V2 path does: the main framebuffer is downscaled into a blur
 * target and run through {@code blurIterations} separable Gaussian passes of
 * {@code blurRadius}. Lens is an own edge-lens style (sharp backdrop, refraction band,
 * rim, glint). Queued panels are drawn into an offscreen glass layer which is then
 * drawn into the GUI; all raw GL is bracketed by a full state save/restore.
 */
public final class GlassRenderer {
    public enum Mode { LIQUID, BUBBLE, CLEAR, TINTED, LENS }

    /** One glass panel in GUI pixels. */
    public record Panel(float x, float y, float w, float h, float radius, float alpha) {}

    /** Parameters (pushed by the HUD module through {@link #settings}). */
    public static volatile Mode mode = Mode.LIQUID;
    public static volatile float powerFactor = 4f, liquidPower = 3f, motion = 1f, flowSpeed = 1f;
    public static volatile int blurIterations = 1;
    public static volatile float blurRadius = 2f;
    public static volatile float blurDownscale = 0.5f;
    public static volatile float noise = 0.03f;
    public static volatile float refractionPower = 0.75f;
    public static volatile float glowWeight = 0.3f, glowBias = 0f, glowEdge0 = 0.06f, glowEdge1 = 0f;
    public static volatile float tintR = 0.62f, tintG = 0.83f, tintB = 1f, tintStrength = 0.25f;
    public static volatile float chroma = 0.001f;
    public static volatile float darkness = 0f;
    public static volatile float lensRefractPx = 10f, lensChromaPx = 1.5f, lensRipple = 0.75f, lensHighlight = 1f;
    /** Copies the HUD module's settings into the fields above (null = defaults). */
    public static volatile Runnable settings;

    /**
     * GUI textures are drawn at the END of the frame, after every flush has run. With one shared layer
     * texture, each flush overwrote the previous one and every glass element showed the LAST flush's
     * panels. Each flush now renders into the next texture of a small ring (created on demand).
     */
    private static final int LAYERS = 8;
    private static final Identifier[] LAYER_IDS = new Identifier[LAYERS];
    private static final NativeImageBackedTexture[] LAYER_TEX = new NativeImageBackedTexture[LAYERS];
    private static int nextLayer;
    private static final List<Panel> queue = new ArrayList<>();
    private static final long START = System.nanoTime();
    private static boolean failed;
    private static int vao, vbo, fbo;
    private static int blurA, blurB, blurW, blurH;
    private static int copyProgram, gaussProgram, clearProgram, tintedProgram, lensProgram, liquidProgram, bubbleProgram;

    private static long lastBlurNanos, lastBlurKey;
    /** program -> uniform name -> location (glGetUniformLocation is a driver round trip). */
    private static final java.util.Map<Integer, java.util.Map<String, Integer>> LOCATIONS = new java.util.HashMap<>();
    private static int layerW, layerH;
    /** one-shot gate diag keys (plain literals, diag lane) */
    private static final java.util.Map<String, Boolean> WARNED = new java.util.HashMap<>();

    private GlassRenderer() {}

    public static boolean available() {
        return !failed;
    }

    public static void queue(Panel p) {
        if (!failed && p.w() > 1 && p.h() > 1 && p.alpha() > 0.01f) queue.add(p);
    }

    /**
     * Flushes the queued card-shell panels with a scoped, static tinted-glass material.
     * Call on the render thread before drawing labels and controls. The HUD's global
     * settings callback is intentionally suppressed for this flush and restored even
     * when shader setup, resource creation, or GUI submission fails. Queue only the
     * card shell before this call; unrelated HUD panels must be flushed separately.
     *
     * @param tint RGB/ARGB material color (alpha comes from each queued Panel)
     * @param blur Gaussian blur radius, clamped to 0..24; non-finite values use 10
     * @param reducedMotion reduces optical displacement; TINTED itself has no time animation
     */
    public static void flushCard(DrawContext ctx, int tint, float blur, boolean reducedMotion) {
        Runnable previousSettings = settings;
        Mode previousMode = mode;
        int previousIterations = blurIterations;
        float previousRadius = blurRadius, previousDownscale = blurDownscale;
        float previousNoise = noise, previousRefraction = refractionPower;
        float previousR = tintR, previousG = tintG, previousB = tintB;
        float previousStrength = tintStrength, previousChroma = chroma, previousDarkness = darkness;
        try {
            settings = null;
            mode = Mode.TINTED;
            blurRadius = Float.isFinite(blur) ? Math.max(0f, Math.min(24f, blur)) : 10f;
            blurIterations = blurRadius > 0.5f ? 2 : 0;
            blurDownscale = 0.5f;
            noise = 0.012f;
            refractionPower = reducedMotion ? 0.08f : 0.32f;
            tintR = ((tint >>> 16) & 255) / 255f;
            tintG = ((tint >>> 8) & 255) / 255f;
            tintB = (tint & 255) / 255f;
            tintStrength = 0.22f;
            chroma = reducedMotion ? 0f : 0.0008f;
            darkness = 0.02f;
            flush(ctx);
        } finally {
            mode = previousMode;
            blurIterations = previousIterations;
            blurRadius = previousRadius;
            blurDownscale = previousDownscale;
            noise = previousNoise;
            refractionPower = previousRefraction;
            tintR = previousR;
            tintG = previousG;
            tintB = previousB;
            tintStrength = previousStrength;
            chroma = previousChroma;
            darkness = previousDarkness;
            settings = previousSettings;
        }
    }

    /** Renders all queued panels and draws the glass layer into the GUI; clears the queue. */
    public static void flush(DrawContext ctx) {
        if (queue.isEmpty() || failed) {
            queue.clear();
            return;
        }
        List<Panel> panels = List.copyOf(queue);
        queue.clear();
        Runnable s = settings;
        if (s != null) s.run();
        MinecraftClient mc = MinecraftClient.getInstance();
        var mainAttachment = mc.getFramebuffer().getColorAttachment();
        int mainId = mainAttachment instanceof GlTexture main ? main.getGlId() : glIdOf(mainAttachment);
        if (mainId <= 0) {
            // 1.21.x pipeline wrappers can hide the gl handle; diag once and bail
            warnGate(LT.L50);
            return;
        }
        int fw = mc.getFramebuffer().textureWidth, fh = mc.getFramebuffer().textureHeight;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        try {
            if (!ensure(fw, fh)) return;
            slot = nextLayer;
            nextLayer = (nextLayer + 1) % LAYERS;
            NativeImageBackedTexture layer = layer(slot, fw, fh);
            if (!(layer.getGlTexture() instanceof GlTexture out)) {
                warnGate(LT.L51);
                return;
            }
            render(mainId, out.getGlId(), fw, fh, sw, sh, panels);
        } catch (Throwable t) {
            failed = true;
            UtilityMod.LOGGER.warn(LT.L1, t.toString());
            return;
        }
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LAYER_IDS[slot], 0, 0, 0f, 0f, sw, sh, fw, fh, fw, fh);
    }

    private static int slot;

    /** Ring slot texture, (re)created at the framebuffer size when needed. */
    private static NativeImageBackedTexture layer(int i, int fw, int fh) {
        NativeImageBackedTexture t = LAYER_TEX[i];
        if (t == null || layerW != fw || layerH != fh) {
            if (layerW != fw || layerH != fh) {
                // size changed: drop every slot so they all get rebuilt at the new size
                for (int k = 0; k < LAYERS; k++) {
                    if (LAYER_TEX[k] != null) LAYER_TEX[k].close();
                    LAYER_TEX[k] = null;
                }
                layerW = fw;
                layerH = fh;
            }
            if (LAYER_IDS[i] == null) LAYER_IDS[i] = Identifier.of(UtilityMod.MOD_ID, LT.L2 + i);
            t = new NativeImageBackedTexture(LAYER_IDS[i]::toString, new NativeImage(fw, fh, true));
            MinecraftClient.getInstance().getTextureManager().registerTexture(LAYER_IDS[i], t);
            LAYER_TEX[i] = t;
        }
        return t;
    }

    // ---- resources -------------------------------------------------------------------

    private static int newTexture(int w, int h) {
        int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        int t = GL11.glGenTextures();
        GlStateManager._bindTexture(t);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(prev);
        return t;
    }

    private static boolean ensure(int fw, int fh) {
        if (vao == 0) {
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            fbo = GL30.glGenFramebuffers();
            int prevVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevVbo = GlStateManager._getInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GlStateManager._glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 24L * 4, GL15.GL_DYNAMIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8);
            GlStateManager._glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);

            copyProgram = link(LT.L3, LT.L4);
            gaussProgram = link(LT.L3, LT.L5);
            clearProgram = link(LT.L6, LT.L7);
            tintedProgram = link(LT.L6, LT.L8);
            lensProgram = link(LT.L6, LT.L9);
            liquidProgram = link(LT.L6, LT.L10);
            bubbleProgram = link(LT.L6, LT.L11); // optional: falls back to Liquid
            if (copyProgram == 0 || gaussProgram == 0 || clearProgram == 0 || tintedProgram == 0 || lensProgram == 0 || liquidProgram == 0) {
                failed = true;
                return false;
            }
        }
        float ds = Math.max(0.1f, Math.min(1f, blurDownscale));
        int bw = Math.max(1, Math.round(fw * ds)), bh = Math.max(1, Math.round(fh * ds));
        if (blurA == 0 || blurW != bw || blurH != bh) {
            if (blurA != 0) GL11.glDeleteTextures(new int[] {blurA, blurB});
            blurA = newTexture(bw, bh);
            lastBlurNanos = 0;
            blurB = newTexture(bw, bh);
            blurW = bw;
            blurH = bh;
        }
        return true;
    }

    // ---- frame -------------------------------------------------------------------------

    private static void target(int tex, int w, int h) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GlStateManager._viewport(0, 0, w, h);
    }

    private static void render(int mainTex, int outTex, int fw, int fh, int sw, int sh, List<Panel> panels) {
        int prevFbo = GlStateManager._getInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevProgram = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevVbo = GlStateManager._getInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevActive = GlStateManager._getInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        // 1.21.11 binds sampler objects; one on unit 0 overrides our filtering (black reads)
        int prevSampler = GlStateManager._getInteger(GL33.GL_SAMPLER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            GL33.glBindSampler(0, 0);
            GlStateManager._disableDepthTest();
            GlStateManager._disableScissorTest();
            GlStateManager._disableCull();
            GlStateManager._disableBlend();
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

            // 1) downscaled copy of the scene (+ blur), reused by later flushes in the same frame
            long now = System.nanoTime();
            int iterationsKey = mode == Mode.LENS ? 0 : Math.max(0, Math.min(5, blurIterations));
            long key = ((long) iterationsKey << 40) ^ ((long) Float.floatToIntBits(blurRadius) << 8) ^ mainTex;
            boolean reuse = false; // cross-flush reuse disabled: it left panels sampling a stale/empty blur target
            if (!reuse) {
            target(blurA, blurW, blurH);
            GlStateManager._glUseProgram(copyProgram);
            GlStateManager._bindTexture(mainTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            uniform1i(copyProgram, LT.L12, 0);
            quad(-1, -1, 1, 1, 0, 0, 1, 1);

            // 2) separable Gaussian, ping-pong A -> B -> A
            int iterations = mode == Mode.LENS ? 0 : Math.max(0, Math.min(5, blurIterations));
            if (iterations > 0 && blurRadius > 0.5f) {
                GlStateManager._glUseProgram(gaussProgram);
                uniform1i(gaussProgram, LT.L12, 0);
                uniform2f(gaussProgram, LT.L13, 1f / blurW, 1f / blurH);
                uniform1f(gaussProgram, LT.L14, Math.min(24f, blurRadius));
                for (int i = 0; i < iterations; i++) {
                    target(blurB, blurW, blurH);
                    GlStateManager._bindTexture(blurA);
                    uniform2f(gaussProgram, LT.L15, 1f, 0f);
                    quad(-1, -1, 1, 1, 0, 0, 1, 1);
                    target(blurA, blurW, blurH);
                    GlStateManager._bindTexture(blurB);
                    uniform2f(gaussProgram, LT.L15, 0f, 1f);
                    quad(-1, -1, 1, 1, 0, 0, 1, 1);
                }
            }
            lastBlurNanos = System.nanoTime();
            lastBlurKey = key;
            }

            // 3) panels into the glass layer
            target(outTex, fw, fh);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int program = switch (mode) {
                case CLEAR -> clearProgram;
                case TINTED -> tintedProgram;
                case LENS -> lensProgram;
                case LIQUID -> liquidProgram;
                case BUBBLE -> bubbleProgram != 0 ? bubbleProgram : liquidProgram;
            };
            GlStateManager._glUseProgram(program);
            GlStateManager._bindTexture(blurA);
            uniform1i(program, LT.L16, 0);
            uniform2f(program, LT.L17, sw, sh);
            uniform1f(program, LT.L18, 0f);
            uniform1f(program, LT.L19, noise);
            uniform1f(program, LT.L20, refractionPower);
            uniform1f(program, LT.L21, glowWeight);
            uniform1f(program, LT.L22, glowBias);
            uniform1f(program, LT.L23, glowEdge0);
            uniform1f(program, LT.L24, glowEdge1);
            uniform3f(program, LT.L25, tintR, tintG, tintB);
            uniform1f(program, LT.L26, tintStrength);
            uniform1f(program, LT.L27, chroma);
            uniform1f(program, LT.L28, darkness);
            uniform1f(program, LT.L29, lensRefractPx);
            uniform1f(program, LT.L30, lensChromaPx);
            uniform1f(program, LT.L31, lensRipple);
            uniform1f(program, LT.L32, lensHighlight);
            uniform1f(program, LT.L33, powerFactor);
            uniform1f(program, LT.L34, liquidPower);
            uniform1f(program, LT.L35, motion);
            uniform1f(program, LT.L36, flowSpeed);
            uniform1f(program, LT.L37, (System.nanoTime() - START) / 1e9f);
            for (Panel p : panels) {
                uniform2f(program, LT.L38, p.x() + p.w() / 2f, p.y() + p.h() / 2f);
                uniform2f(program, LT.L39, p.w(), p.h());
                uniform2f(program, LT.L40, p.w() / sw, p.h() / sh);
                uniform1f(program, LT.L41, p.radius());
                uniform1f(program, LT.L42, p.alpha());
                quad(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), 0, 0, 1, 1);
            }
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        } finally {
            GlStateManager._glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
            GlStateManager._glUseProgram(prevProgram);
            GL33.glBindSampler(0, prevSampler);
            GlStateManager._bindTexture(prevTex0);
            GlStateManager._activeTexture(prevActive);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GlStateManager._viewport(vp[0], vp[1], vp[2], vp[3]);
            if (blend) GlStateManager._enableBlend();
            else GlStateManager._disableBlend();
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (scissor) GlStateManager._enableScissorTest();
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private static void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        float[] d = {x0, y0, u0, v0, x1, y0, u1, v0, x1, y1, u1, v1, x0, y0, u0, v0, x1, y1, u1, v1, x0, y1, u0, v1};
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer b = stack.mallocFloat(d.length);
            b.put(d).flip();
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, b);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    // ---- shaders ---------------------------------------------------------------------------

    private static int link(String vert, String frag) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vert), fs = compile(GL20.GL_FRAGMENT_SHADER, frag);
        int p = 0;
        if (vs != 0 && fs != 0) {
            p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vs);
            GL20.glAttachShader(p, fs);
            GL20.glBindAttribLocation(p, 0, LT.L43);
            GL20.glBindAttribLocation(p, 1, LT.L44);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
                UtilityMod.LOGGER.warn(LT.L45, frag, GL20.glGetProgramInfoLog(p));
                GL20.glDeleteProgram(p);
                p = 0;
            }
        }
        if (vs != 0) GL20.glDeleteShader(vs);
        if (fs != 0) GL20.glDeleteShader(fs);
        return p;
    }

    private static int compile(int type, String file) {
        String src;
        try (InputStream in = GlassRenderer.class.getResourceAsStream(LT.L46 + file)) {
            if (in == null) throw new IllegalStateException(LT.L47 + file);
            src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            UtilityMod.LOGGER.warn(LT.L48, file, e.toString());
            return 0;
        }
        int sh = GL20.glCreateShader(type);
        GL20.glShaderSource(sh, src);
        GL20.glCompileShader(sh);
        if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
            UtilityMod.LOGGER.warn(LT.L49, file, GL20.glGetShaderInfoLog(sh));
            GL20.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }

    private static int loc(int program, String name) {
        return LOCATIONS.computeIfAbsent(program, k -> new java.util.HashMap<>())
                .computeIfAbsent(name, n -> GL20.glGetUniformLocation(program, n));
    }

    private static void uniform1i(int p, String n, int v) { int l = loc(p, n); if (l >= 0) GL20.glUniform1i(l, v); }
    private static void uniform1f(int p, String n, float v) { int l = loc(p, n); if (l >= 0) GL20.glUniform1f(l, v); }
    private static void uniform2f(int p, String n, float a, float b) { int l = loc(p, n); if (l >= 0) GL20.glUniform2f(l, a, b); }
    private static void uniform3f(int p, String n, float a, float b, float c) { int l = loc(p, n); if (l >= 0) GL20.glUniform3f(l, a, b, c); }

    /** Reflects the gl handle off a non-GlTexture attachment (1.21.x pipeline wrappers). */
    private static int glIdOf(Object attachment) {
        if (attachment == null) return -1;
        for (String m : new String[] {"glId", "getGlId"}) {
            try {
                Object v = attachment.getClass().getMethod(m).invoke(attachment);
                if (v instanceof Integer i) return i;
            } catch (Throwable ignored) {}
        }
        try {
            java.lang.reflect.Field f = attachment.getClass().getDeclaredField("glId");
            f.setAccessible(true);
            return f.getInt(attachment);
        } catch (Throwable ignored) {}
        return -1;
    }

    /** One-shot gate diag: the same key never logs twice, so frame-time gates stay quiet after the first. */
    private static void warnGate(String why) {
        if (WARNED.putIfAbsent(why, Boolean.TRUE) == null) UtilityMod.LOGGER.warn(LT.L1, why);
    }

    /** Card-panel fallback diag: separates the glass toggle from a renderer gate. */
    public static void reportCardGate(boolean styleGlass) {
        if (!styleGlass) warnGate(LT.L52);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xab50,0xbbd6,0x3aa5,0xaaf5,0x9f15,0x7735,0x8693,0xe002,0x8577,0x056d,0x7113,0x661a,0x8ab4,0x83a2,0xf79b,0x53e5,0xe70d,0xda63,0xfb66,0x3dca,0x0564,0x4461,0x6ee8,0x5021,0x2669,0x4a01,0xde9c,0x1b27,0x3a6e,0x65d7,0x5c0b,0xd0cb,0xab1e,0x306a,0x6720,0x1f23,0xe197,0x431a}, new char[]{0x6eb3,0x4434,0x666e,0x65be,0xe06e,0x775c,0xf7ef,0xe774,0x3027,0x3da5,0x1b43,0xab43,0x8557,0x86df,0x2600,0x051c,0x7166,0x9f7c,0xab34,0xe1b7,0xdefb,0xbbba,0xda02,0xfb15,0x4a72,0x0544,0x6744,0x5048,0x5c78,0x437b,0x1f41,0x5389,0x3ac0,0xd0af,0x8a8e,0xaad5,0x3a15,0x83df}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x20bc,0x402d,0xf78d,0xeed3,0xc306,0x67b1,0x235f,0x0f7a,0xecd8,0x0500,0x7e99,0x6fe4,0x9089,0xedc6,0x9293,0xf59b}, new char[]{0x20d4,0x6f91,0x233b,0x7eb6,0xeda1,0xc36a,0xf5fa,0x405e,0x92e0,0x90d6,0xecb4,0x0561,0x67c8,0xf7e8,0x0f08,0xee8c}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4512,0x6f3d,0x3f42,0x6e9b,0x3302,0xfcc7,0x3f8d,0x6461,0x9ad9,0xad62,0x457b,0x7474,0xa803,0x9738,0xb127,0xb5d7,0x0b0b}, new char[]{0xb14b,0x451c,0x6ec4,0x6402,0x741b,0xad12,0x0b72,0xb588,0x3ffc,0x6f48,0xa862,0x975c,0x453c,0x3f34,0xfca2,0x9aab,0x3376}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe750,0xe339,0x6004,0x76fe,0x43c1,0xe5d5,0xcefa,0x4280,0xcd1c,0xf495,0x3fe4,0x7098}, new char[]{0xce96,0xe35e,0x70c7,0x43a2,0xf4fa,0x42f0,0x7687,0xe5fb,0x3f82,0xcd6e,0xe731,0x6063}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ea3,0xf47a,0x5873,0x9c0b,0x863d,0xb025,0xd873,0xfb07,0xad04,0x9714,0xd734,0xa1e9,0x4681,0x12a1,0xf91d,0x31b0}, new char[]{0x9778,0x46e6,0xd82c,0xd753,0xb044,0x8ed6,0x12d2,0xf409,0xf974,0xad65,0xfb69,0xa1c7,0x5815,0x31c2,0x9c6a,0x865a}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf267,0x9e15,0x3ba5,0x2d72,0xcdba,0x038a,0xa035,0x305b,0xc0f6,0x1ebb,0x09e7,0x0e3e}, new char[]{0x098b,0xa052,0x2d2d,0x3bd4,0x1ece,0xc097,0x9e71,0x03a4,0x302d,0xcddf,0xf215,0x0e4a}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f71,0x8c91,0x5b6b,0xf92a,0xc17a,0x73c7,0x19c8,0x229b,0x3bd3,0xe94b,0xea04,0xb3ec,0x954d}, new char[]{0x19a4,0xb38b,0x3f2e,0xc119,0x9521,0xf94f,0x8cf0,0xea76,0xe965,0x73a1,0x3ba1,0x22fa,0x5b0c}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f95,0xeb8e,0xc1ae,0xb6cf,0x5fff,0x4954,0x7e40,0x5014,0x41f1,0x0c8a,0x4a13,0x0814,0xcba7,0x0b98}, new char[]{0x4a7f,0xcbc0,0x7e1f,0x0860,0xc1c7,0x0bf6,0xebfa,0x5071,0x4195,0x0ca4,0x5f99,0x0fe7,0x4935,0xb6a8}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e3e,0x3247,0xf789,0x4195,0x089d,0xd254,0x00eb,0x4053,0xbc08,0x5098,0x8cce,0x5570}, new char[]{0x41f9,0x4e59,0x08c2,0x8ca2,0xf7ec,0x551e,0xd227,0x407d,0x50fe,0x0099,0x3226,0xbc6f}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaf34,0x3064,0xcd38,0xffd4,0x266c,0xa02d,0x221f,0x488f,0x057f,0xf835,0x56d2,0x1486,0x3563,0x5c98}, new char[]{0x14ea,0xffb3,0x568d,0x2600,0x5cf1,0x3015,0xaf41,0x48e6,0xcd5c,0xf81b,0x0519,0xa05f,0x3502,0x2278}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x372f,0xae2d,0xe1dd,0x0ace,0x5ce5,0xedd8,0xa0d0,0x1408,0x5652,0xc948,0x6ed8,0x45f6,0x9585,0xe7be}, new char[]{0x459a,0x95e2,0xae72,0xe7dc,0x375a,0xa0b2,0x5c87,0xe1b1,0x0aab,0x1426,0x6ebe,0xc93a,0xedb9,0x5635}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe23e,0xde58,0x6d4e,0xd76e,0xbd39,0xf224,0x01d3,0xdf41,0xe6ca}, new char[]{0xdf35,0xf241,0xe6b2,0x01a7,0x6d3b,0xd71c,0xe25b,0xbd70,0xde36}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ec2,0xb81e,0x76d0,0x3ee6,0x900d,0x701f,0x458d,0xcb13,0x97ea}, new char[]{0x706b,0x76b5,0x9075,0x1ea7,0x45e1,0x3eb5,0x9783,0xcb69,0xb87b}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4cde,0xb7fa,0x9fac,0xe813,0xa9ba,0x2c13}, new char[]{0xb788,0xe872,0x4cba,0x9fc5,0x2c66,0xa9c9}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x01b2,0xed53,0xdce7,0x07fa,0xfdad,0xe41b,0x9f4e,0xb8dc,0x381c}, new char[]{0x01d6,0x9f27,0x0788,0xed36,0xb8bf,0x3868,0xfdc4,0xdc88,0xe475}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b57,0xebc5,0xe80d,0xe1eb,0x0bb4,0x74c1,0x1054,0x207e}, new char[]{0x0bc1,0xe1a9,0x0b3b,0x1021,0xe87f,0xeb91,0x201b,0x74b9}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdd83,0x8ae4,0x7581,0xaec5,0x67d1,0x0ae5,0xc3dc}, new char[]{0xddf6,0x75d2,0x0a86,0xaeb7,0x8a81,0x67b4,0xc3b2}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xef6c,0xed28,0x8b59,0x624d,0x5c9d,0x39f5,0xde77,0x24e0}, new char[]{0x5ce8,0xed6a,0x8b35,0x3980,0xef1e,0x6201,0xde18,0x2484}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f89,0x38a6,0x10d3,0x0ab2,0x1183,0x18fd}, new char[]{0x1ffc,0x38e8,0x10bc,0x1894,0x11f0,0x0ad7}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3e0e,0x2e32,0x933e,0x2997,0x6bac,0x22aa,0x08ed,0xaa61,0x401a,0xcd12,0xc310,0x4988,0x9741,0xda04,0x593e,0x4850}, new char[]{0x406f,0x22f8,0x0888,0x5958,0x49fa,0xaa00,0x935d,0x6bd8,0xda6d,0x3e61,0xc37e,0x2e62,0x483f,0x9736,0xcd77,0x29e5}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfe73,0x4dbd,0x06cd,0xac6f,0x9a41,0xbad2,0xe281,0x0a65,0xb239,0xd1c1,0xa096}, new char[]{0xb24c,0x4dfa,0x9a2d,0xbabd,0x06ba,0xd196,0xac0a,0xfe1a,0x0a02,0xe2e9,0xa0e2}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xae44,0xd503,0x9c8d,0x7ce8,0x862d,0xbca9,0x9de7,0xa82c,0xbd9f}, new char[]{0x9d92,0xbcee,0xa840,0x7c87,0xbde8,0xae06,0xd56a,0x864c,0x9cfe}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3891,0x86d5,0x7810,0xca71,0x2e42,0xfe47,0x067e,0x008c,0xfe66,0xbffd}, new char[]{0x00f9,0x7857,0x2e2e,0x0611,0x38e6,0x8690,0xfe02,0xbf9a,0xca14,0xfe77}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe98d,0xd079,0xa277,0x163b,0xc371,0xffdf,0x901b,0x0bba,0x3154,0x6aea}, new char[]{0xffaa,0x3113,0x9077,0xc31e,0xd00e,0x167e,0x0bde,0xe9ea,0x6a8f,0xa246}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5de6,0x521b,0x3861,0x375a,0x30d2,0x2d6c,0xad39,0xdc7a,0xa736,0x3e29}, new char[]{0x3e5c,0x2d38,0xa75f,0xdc14,0xad4d,0x3822,0x3735,0x5d8a,0x5274,0x30a0}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8a8b,0x7588,0x2cdb,0x77d6,0xe778,0x9b9e,0xa059,0xb0a2,0x48de,0x33a2,0x605a,0xe822,0xc7be}, new char[]{0x48ab,0x75dc,0x8ae2,0xe84c,0xc7ca,0x33f1,0xa02d,0x6028,0x9bfb,0x2cb5,0x77b1,0xb0d6,0xe710}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x696a,0x3cc1,0xef7a,0xc71d,0xa6f0,0xd650,0x477f,0xe6fa,0xfc2f,0xc9f9,0x038d,0x2a4c,0xf072,0x63e4,0x43c7}, new char[]{0x43b2,0x2a0f,0xfc47,0x470d,0x6905,0xa69d,0xc998,0xe6a9,0xef0e,0x3cb3,0x03e8,0x638a,0xf015,0xc769,0xd638}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1068,0xe896,0xae9f,0x2a9b,0x87b1,0xe0ab,0x3d2a,0xe1bc,0x660a}, new char[]{0x2aee,0xe0ef,0x1009,0xe1ce,0xaef4,0xe8f8,0x3d4f,0x87c2,0x6679}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe866,0x5a03,0x9ad7,0xeb91,0x7429,0x271b,0x4d70,0xfb8a,0x92d4,0x69d3}, new char[]{0x4d05,0x5a51,0xebf4,0x69b5,0x92a6,0xfbeb,0x9ab4,0x276f,0x7479,0xe81e}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6369,0xeb07,0x60aa,0x5249,0xc077,0xda57,0xdb7c,0xc593,0x4d98}, new char[]{0xdb09,0x60e9,0x4df0,0xc005,0xda38,0xeb6a,0xc5f2,0x6339,0x5231}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd11f,0xbfa0,0x0fad,0x07f0,0x447a,0x199a,0x74cc}, new char[]{0x19ef,0xd14d,0x4413,0x74bc,0xbfd0,0x079c,0x0fc8}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9e62,0x887b,0x2260,0x5757,0xe2c9,0x758a,0x78fb,0xd150,0xa573,0x37b9}, new char[]{0x75ff,0xe281,0x37d0,0xd137,0x7893,0x8817,0x2209,0x5730,0x9e0a,0xa507}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3cad,0xcbd1,0x745a,0x02cb,0x9c90,0x6f32,0xf86c,0xafac,0x2582,0xc0e7,0xe399,0x60e1}, new char[]{0x6094,0x3cfd,0x9cff,0xf81b,0x02ae,0x7428,0x25c4,0xe3f8,0xcbb2,0x6f46,0xc088,0xafde}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdb71,0x1a30,0x4d4f,0xaddc,0x57fd,0x2478,0x2a9d}, new char[]{0xada9,0x57bb,0x2428,0x2af2,0x4d38,0xdb14,0x1a42}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcc42,0x418e,0x8aa8,0x9b5d,0xd468,0xa9aa,0xd2a1}, new char[]{0xa9df,0xd2ec,0xcc2d,0xd41c,0x8ac1,0x9b32,0x41e0}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa84a,0x2c32,0x0882,0x5e2f,0x22a9,0xd91e,0x96ba,0xe814,0xa3d7,0x5865}, new char[]{0xd96b,0xa80c,0x5e43,0x22c6,0x96cd,0x2c61,0x5815,0xa3b2,0xe871,0x08e6}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba5d,0xcbc6,0xf98b,0x851e,0xc969}, new char[]{0x856b,0xc93d,0xf9e2,0xba30,0xcba3}).plainString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x419e,0xcb77,0x0bc6,0x1983}, new char[]{0xcb02,0x0b8b,0x19ea,0x41fa}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c51,0x352c,0x89d4,0x4588,0xe1ee,0xef6b,0x6930,0xa97e,0x3b76}, new char[]{0x6945,0x357d,0xe19b,0x8c30,0x45ec,0x3b25,0xa917,0x89ae,0xef0e}).plainString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe21d,0x78d3,0xc8a9,0x0e6e,0xc6a5,0x3058,0x47a2,0x9a5d}, new char[]{0x302d,0x0e26,0x9a3c,0xc6c9,0x47c4,0xc8e7,0xe279,0x78b0}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x30a9,0x1fa6,0xe594,0x0624,0xa0d6,0x0c6b,0x6dc4}, new char[]{0x0c1e,0x6d96,0xe5f5,0xa0b2,0x1fcf,0x0651,0x30da}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x47b1,0x70a9,0x0f42,0x8586,0x347f,0xfd46}, new char[]{0x85f3,0x343e,0x47dd,0x70d9,0x0f2a,0xfd27}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5632,0xec98,0xd3ce,0x0824,0xd02e}, new char[]{0xecf9,0x566d,0x0854,0xd3a1,0xd05d}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x06fa,0x5448,0x02da,0x9de7}, new char[]{0x9d86,0x06a5,0x02af,0x543e}).plainString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0de,0x9ac8,0x6be0,0xa606,0x317b,0x9634,0x0df8,0x56fb,0x7e23,0xb995,0xde48,0x2c57,0x43aa,0xc78f,0x0fef,0x1c1d,0xfce9,0xaa76,0xf9a9,0x4287,0x1a37,0x7b25,0x4cba,0x8f8d,0xf5fc,0x8b42,0x2d2e,0x9d39,0x6a6c,0xa373,0x7b8c,0x4213,0xa949,0xcc89,0x6927,0xa1ca,0xe3cc,0xac81,0xab58,0x8a6e,0x430d,0x6aa0,0x0029,0x53c0}, new char[]{0xfcb2,0x7b70,0x9640,0x5692,0xc7e3,0xab31,0x310f,0xa67f,0x42ca,0x4362,0x7e47,0x0074,0xcca9,0xe38b,0x1c71,0xa1ab,0xb9e6,0xa300,0x0dd8,0x7bff,0x4cd2,0x1a56,0xace5,0xf9cc,0x43d8,0x8b62,0x9d42,0xa934,0x53e0,0xaa1a,0x2c3e,0x8fe3,0x8a05,0x2d0e,0x6941,0x6ac1,0x9aa1,0x0f83,0x6b85,0x4277,0xde72,0x6a4c,0xc0a5,0xf581}).plainString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x526a,0x531f,0x11a8,0x1bbd,0x29ca,0x645d,0x56c3,0x8eb3,0x6a72,0x9bb0,0x7d55,0x5b43,0x24ca,0x3e85,0xdc0b,0x7848,0xd7b5,0xf850,0x5b3e,0xf377,0xc35c,0xab74,0xc314,0x9507,0x7e3c,0x547b,0x7873,0x49ec,0xdd68,0xfbd4,0x900f,0xc542,0x0ee6}, new char[]{0x5b6c,0xdc6a,0xf823,0x499f,0x3ee0,0xc536,0x9574,0xc373,0xdd1d,0xfba0,0x56aa,0xab18,0x781a,0x8ec7,0x11d1,0x5416,0x5b51,0xf313,0x0ec9,0x1bce,0xc37c,0x24ab,0x6439,0x520f,0xd7c7,0x7e4f,0x9020,0x6a15,0x7824,0x537e,0x29b9,0x7d26,0x9b9f}).plainString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1cfd,0xd245,0x403a,0x96b5,0x07e1,0x5ecd,0x9a8a,0x8eac}, new char[]{0x96d8,0x4053,0x0792,0x9af9,0xd22c,0x5ea3,0x8ecb,0x1cdd}).plainString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9182,0xc03f,0xb5b0,0x6c51,0xfbc7,0xcc3d,0x2415,0xdf8d,0x1e90,0x5600,0x2220,0x032e,0xa007,0x31fc,0x4d4e,0x9627,0x5f0c,0x87bc,0x6b01,0xab4f,0x877e,0x202c,0x351a,0x01ba,0x4f16,0x552f,0x578e,0x155b,0xf751,0xd5da,0x77bd,0xffb6,0xcd1b,0x6ac6,0x2d3e,0x2dc8,0x3669,0xc3ed,0x49e6,0xd65e,0xc122,0xe54e,0x21ab}, new char[]{0xc3b6,0x6c04,0x2461,0x5f65,0xd5b6,0x4d27,0xc156,0xc046,0x564d,0x224f,0x153f,0xffeb,0xcc1d,0x57c9,0x498a,0x77dc,0x91f1,0x870d,0xd67e,0x4f65,0x21c3,0x6aa7,0xdfe9,0xb5d5,0x318e,0xab6f,0x2db3,0xa07a,0x879c,0x01cf,0x3574,0x555d,0xe52b,0xfba6,0x2048,0x3608,0x6b63,0x964b,0x034b,0xcd21,0x2d1e,0x1eeb,0xf72c}).plainString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8873,0xf05d,0xb9cf,0xd6fe,0x2b1d,0x4264,0x4d66,0x44aa,0x3078,0x0e31,0x8067,0x3785,0x4ad5,0xfaf0,0xcd98,0xc81e,0xb0ca,0x8e9b,0x67c2,0x2bb8,0x3092,0xcb4e,0x56af,0xa7e9,0x2134,0xd435,0x2907,0x0ed1,0x63a0,0x6ae7,0x0d00,0xb6da,0x33dc,0xf0ce,0xe9bd,0xca62,0xcc62,0x5205,0xa895,0xb5a0,0xd928,0xa0e6,0xb310,0x68bd,0xda8a,0xe662,0x46b7}, new char[]{0xdad1,0x0d55,0x2973,0xb6b3,0xa08a,0xcb27,0xb0be,0xf0b7,0x30df,0x8ef4,0xd69a,0x37d8,0xcdb8,0xc859,0xd944,0xe603,0xa79a,0xf02e,0x4244,0x0e42,0x4abd,0xa8f4,0xcc06,0x46d2,0xb9bd,0x5225,0x0eaa,0x4d1b,0x33fc,0x2b7e,0xb5cf,0x800a,0x2bc8,0x3011,0x68d1,0xd450,0xfad0,0x56c9,0xb371,0xca0b,0x6a8b,0x63c5,0x67a6,0x210e,0x448a,0xe9c6,0x880e}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd92d,0x37db,0x2a9e,0x82cb,0xa42f,0x04a5,0xebc1,0x50da,0xc59b,0xb98f,0x1c94,0x4a18,0x11e2,0xa8b9,0x3356,0x6555,0x76b8,0x3ff5,0x0b29,0x8aee,0x5f69,0x9626,0xfce8,0xb16d,0xf04a,0x2279,0xbc04,0xd11c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1}).plainString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf051,0xc5c9,0xb12f,0xbc1a,0x82cc,0x37cd,0x4a1f,0x0b30,0x76ac,0x2279,0x11e2,0x5f6d,0x1c8b,0x9626,0xebc1,0xa8a7,0xd11d,0x6542,0xfce2}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08}).plainString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x50c9,0xd938,0x3ffb,0xd119,0xeb86,0xa423,0x226a,0x11b0,0xb984,0x2adf,0x379e,0x4a4a,0x76a7,0xbc0d,0x5f64,0xa8aa,0xc5dd,0x962f,0x0b3d,0x82c3,0xfce6,0x655a,0xf056,0x1c97,0xb17f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2}).plainString();
    }
}
