package com.utilitymod.echo.features.impl.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.utilitymod.UtilityMod;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Frame-accumulation motion blur: each frame the previous (already blurred) frame is blended over
 * the fresh world image, then the result is kept as the new history. Optional saturation boost.
 * Runs at the start of the HUD pass (world drawn, GUI not yet), with a full GL state save/restore.
 */
public class MotionBlur extends Feature {
    private final FloatSetting strength = new FloatSetting(LT.L1, 0.55f, 0.05f, 0.92f, 0.01f);
    private final FloatSetting saturation = new FloatSetting(LT.L2, 1.15f, 0f, 2f, 0.05f);

    private static MotionBlur instance;
    private static boolean failed;
    private static int program, fbo, vao, vbo, history, histW, histH;
    private static boolean historyValid;

    public MotionBlur() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e2,0xa8a4,0xbc00,0x226c,0x1c9e,0x82ef,0x0b7c,0xd137,0xb161,0x9636,0x37d1}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04bd,0x6d62,0x1c80,0x0b7c,0x2a8b,0x3313,0x7269,0x3ff2,0xe733,0xeb84,0x8ae2,0xd934,0xc5df,0xa1a8,0x2277,0xd2a0,0x7edf,0xac42,0x93e4,0xcded,0x76b8,0x37ca,0xb167,0xd129,0x2604,0xa8a6,0x4a04,0x11fd,0xb98c,0x478e,0xc148,0x82cb,0xa425,0x58f8,0x9e53,0x3b79,0xbc06,0x5f28,0x651b,0xfce5,0x9622,0x1805,0x50c1,0xf890,0xf040,0xb509}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567}).toString(), Category.RENDER));
        instance = this;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        historyValid = false;
    }

    /** Called from the HUD pass. */
    public static void apply() {
        MotionBlur mb = instance;
        if (mb == null || !mb.isEnabled() || failed) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !(mc.getFramebuffer().getColorAttachment() instanceof GlTexture main)) return;
        int w = mc.getFramebuffer().textureWidth, h = mc.getFramebuffer().textureHeight;
        try {
            if (!ensure(w, h)) return;
            run(main.getGlId(), w, h, mb.strength.getValue(), mb.saturation.getValue());
        } catch (Throwable t) {
            failed = true;
            UtilityMod.LOGGER.warn(LT.L3, t.toString());
        }
    }

    private static boolean ensure(int w, int h) {
        if (program == 0) {
            program = link();
            if (program == 0) {
                failed = true;
                return false;
            }
            fbo = GL30.glGenFramebuffers();
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            int pv = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING), pb = GlStateManager._getInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GlStateManager._glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            float[] quad = {-1, -1, 0, 0, 1, -1, 1, 0, 1, 1, 1, 1, -1, -1, 0, 0, 1, 1, 1, 1, -1, 1, 0, 1};
            try (MemoryStack s = MemoryStack.stackPush()) {
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, s.floats(quad), GL15.GL_STATIC_DRAW);
            }
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8);
            GlStateManager._glBindVertexArray(pv);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pb);
        }
        if (history == 0 || histW != w || histH != h) {
            if (history != 0) GL11.glDeleteTextures(history);
            int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            history = GL11.glGenTextures();
            GlStateManager._bindTexture(history);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(prev);
            histW = w;
            histH = h;
            historyValid = false;
        }
        return true;
    }

    private static void run(int mainTex, int w, int h, float strength, float sat) {
        int prevFbo = GlStateManager._getInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevProgram = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GlStateManager._getInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int prevTex = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevSampler = GlStateManager._getInteger(GL33.GL_SAMPLER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            GL33.glBindSampler(0, 0);
            GlStateManager._disableDepthTest();
            GlStateManager._disableScissorTest();
            GlStateManager._disableCull();
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glUseProgram(program);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GlStateManager._viewport(0, 0, w, h);
            int texLoc = GL20.glGetUniformLocation(program, LT.L4);
            int aLoc = GL20.glGetUniformLocation(program, LT.L5);
            int sLoc = GL20.glGetUniformLocation(program, LT.L6);
            GL20.glUniform1i(texLoc, 0);

            // 1) history over the fresh frame (main colour texture is the render target)
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mainTex, 0);
            if (historyValid) {
                GlStateManager._enableBlend();
                GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                GlStateManager._bindTexture(history);
                GL20.glUniform1f(aLoc, strength);
                GL20.glUniform1f(sLoc, 1f);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            }
            // 2) keep the blended frame as the new history (with saturation applied to the stored copy)
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, history, 0);
            GlStateManager._disableBlend();
            GlStateManager._bindTexture(mainTex);
            GL20.glUniform1f(aLoc, 1f);
            GL20.glUniform1f(sLoc, 1f);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            historyValid = true;
            // 3) saturation on the visible frame
            if (Math.abs(sat - 1f) > 0.01f) {
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mainTex, 0);
                GlStateManager._bindTexture(history);
                GL20.glUniform1f(sLoc, sat);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            }
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        } finally {
            GL33.glBindSampler(0, prevSampler);
            GlStateManager._glBindVertexArray(prevVao);
            GlStateManager._glUseProgram(prevProgram);
            GlStateManager._bindTexture(prevTex);
            GlStateManager._activeTexture(prevActive);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GlStateManager._viewport(vp[0], vp[1], vp[2], vp[3]);
            if (blend) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (scissor) GlStateManager._enableScissorTest();
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private static int link() {
        int vs = compile(GL20.GL_VERTEX_SHADER, LT.L7), fs = compile(GL20.GL_FRAGMENT_SHADER, LT.L8);
        if (vs == 0 || fs == 0) return 0;
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glBindAttribLocation(p, 0, LT.L9);
        GL20.glBindAttribLocation(p, 1, LT.L10);
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            UtilityMod.LOGGER.warn(LT.L11, GL20.glGetProgramInfoLog(p));
            return 0;
        }
        return p;
    }

    private static int compile(int type, String file) {
        try (InputStream in = MotionBlur.class.getResourceAsStream(LT.L12 + file)) {
            if (in == null) return 0;
            int sh = GL20.glCreateShader(type);
            GL20.glShaderSource(sh, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            GL20.glCompileShader(sh);
            if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
                UtilityMod.LOGGER.warn(LT.L13, file, GL20.glGetShaderInfoLog(sh));
                return 0;
            }
            return sh;
        } catch (Exception e) {
            return 0;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1933,0x0ea3,0xa8fb,0xadac,0x05c4,0x3f0e,0x65ad,0x4e87}, new char[]{0x1960,0x3f7a,0x05b6,0xa89e,0x0ecd,0xadcb,0x4ef3,0x65c5}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa010,0xa185,0x6a01,0x3d7b,0x30d9,0xeff6,0xe769,0xa261,0xdb62,0x73ab}, new char[]{0x73f8,0xe708,0xa064,0x30ac,0xef84,0x6a60,0xa215,0x3d12,0xa1ea,0xdb0c}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd23c,0x24e4,0x7181,0x4077,0x35dc,0xc4c7,0x26bb,0x58b9,0x11b7,0x78e4,0x1f75,0x19be,0x1b74,0x7025,0xcf36,0xe476,0x84b7,0x8f56,0xc023,0x1a91,0x6fae,0x26f2,0x8ca5,0x99b2,0xedbc,0x60b4,0x3808,0xc6a9,0x2a67,0xcbd3,0x3f9d,0x970e,0xc307,0xbe1c,0x427d,0x8477,0x7021}, new char[]{0x26e0,0x19eb,0x2490,0xc6c0,0x1b18,0x6fc7,0x3fe9,0x705c,0x8ce8,0x1afe,0x2696,0x842a,0x7001,0x60f9,0x35b3,0x71f5,0x2a0e,0xc4a8,0x788a,0xcbf3,0xc365,0xd250,0x977b,0x11c5,0x8f76,0xbe78,0xc04a,0xcf45,0x99d3,0x421f,0xe41a,0x84d2,0x1f11,0x3832,0xed9c,0x58c2,0x400a}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc5b,0x88c8,0x4166,0x0b0d,0xcaf1,0x2db9,0xe56f,0x8b6c,0xeabf}, new char[]{0xeacb,0xfc3e,0x411e,0xe51b,0x2dcc,0x8b1e,0xca94,0x0b44,0x88a6}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2edf,0x5364,0x85cb,0x902c,0x29fb,0x3e78}, new char[]{0x298e,0x5325,0x3e14,0x2eaf,0x9044,0x85aa}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c80,0xfe6d,0x3c37,0xe6d6,0xe610,0x1990,0xf7e0,0x10ad,0x29f5,0xa5d8,0x8151}, new char[]{0x8cf5,0xf7b3,0xe6b7,0xa5ac,0x3c42,0xe662,0x19f1,0x2981,0x8138,0xfe02,0x10c3}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9364,0x57d9,0xc46f,0x1063,0xff38,0x5097,0xf801,0x8bc9,0x9233,0x1b4f,0xdf39,0xfe2b,0x1901,0x4620,0x76cc,0xc3ea,0xe1d4}, new char[]{0xf86d,0x57be,0x195e,0x76af,0xdf56,0xc39a,0xff41,0x1b10,0x9315,0x9246,0x4641,0xfe4f,0xc441,0x50e1,0xe1b1,0x8bbb,0x1017}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc8a2,0x0a24,0xfb5a,0x70a8,0x17aa,0x6e79,0xc3a5,0x055c,0x449b,0x5fdc,0x870a,0xf39b,0xdd6c}, new char[]{0x70c5,0x17c8,0xc3fa,0x5fbe,0xfb36,0x6e1c,0x0a4a,0x876e,0xdd42,0xc8c4,0x052e,0x44fa,0xf3fc}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2cbb,0x4d11,0x3e0d,0x524d,0x5327}, new char[]{0x3e6c,0x2ce4,0x4d61,0x5348,0x523e}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x41cf,0x577b,0x10bd,0xde95}, new char[]{0x41ae,0x5724,0x10c8,0xdee3}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe33f,0x1655,0x72b4,0x51b1,0x0117,0xef06,0x5ead,0x1aff,0xaeba,0xf0c3,0xa3b9,0x6765,0xea8e,0x7423,0x8e1c,0xaceb,0x0e1a,0x3428,0x22b5,0xe625,0xeced,0x9383,0xd0e6,0x22a2,0x70fa,0x154d,0xcefd,0x392e,0x611d,0x746f,0x4a55,0x93d7,0xe061,0xc7ab,0xbfe2,0x78d0,0x6c4e,0x0317,0x25e3,0xcf36}, new char[]{0xc7f0,0x70af,0x741b,0x0e73,0x72d8,0xaed3,0x6169,0x22cc,0xe668,0x3941,0x7447,0xe362,0x6c6e,0x51dc,0xef69,0x93f7,0x8e75,0x258c,0xce93,0xeccd,0x4a37,0x5ec1,0x78a5,0xa3cb,0x0137,0xeae2,0xbf8b,0xcf58,0x1a94,0x2282,0x344e,0x1634,0x1524,0x037b,0x93b2,0xf0a7,0x675f,0xd0c6,0xac90,0xe01c}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb4e9,0xae98,0xe389,0xe66b,0x3652,0xdb40,0x2cf5,0xb168,0xb4ed,0xfdd2,0x6951,0xc2b9,0xe8ee,0xe524,0xed12,0xaf1f,0xc57d,0xca74,0x4533,0x269a,0xa1eb,0x2216,0xaa2c,0x451f,0xf4bf,0x45bc,0x42cd,0x7ece,0xf4e3,0xadc4,0x3e4e,0x31bb,0xe5e0}, new char[]{0x26b5,0x7eaf,0x42be,0xe618,0x457a,0x31cf,0x4540,0xb4c6,0x2c80,0xb499,0x363b,0xe58c,0xed7b,0xc509,0xe3f0,0xada9,0xf48c,0xaf7b,0xca5b,0xfda1,0x227e,0xb109,0xe88a,0xdb25,0xa199,0xaa5f,0xc296,0x6936,0xaef4,0x3e2f,0xe557,0x45cf,0xf490}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x62b8,0x4129,0xd659,0x7a48,0xecb3,0x640f,0x0304,0xd825,0x03b0,0xa826,0xae45,0x3a81,0xcbf0,0x4e2e,0x0d8e,0xea2b,0xd502,0x578f,0x7da5,0xb0a8,0x7a9c,0xc62d,0xb50f,0x6bac,0xe122,0xb848,0x0bab,0x5218,0xc8a4,0x9668,0x13a8,0x19f7,0x9d8a,0x3008,0xf7e2,0xad1b,0x019d,0x6415}, new char[]{0x01c6,0x963d,0xd576,0xad72,0xae29,0x647c,0xecc7,0x198e,0xb805,0x7dca,0xd841,0x4e73,0x4109,0x0de3,0xc8cb,0xcb84,0x036d,0xe14d,0xa848,0x0b8b,0x7a2a,0x57e3,0xd62c,0xb0da,0xc60d,0xea58,0x7af4,0x13c9,0xb56b,0x306d,0x647d,0x6b8c,0x5263,0xf79f,0x6282,0x9daa,0x3afa,0x03cd}).plainString();
    }
}
