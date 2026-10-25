package com.utilitymod.echo.features.impl.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.utilitymod.UtilityMod;
import com.utilitymod.echo.EchoEvents;
import com.utilitymod.echo.Render3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sky Art: your own images in the sky (config/utilitymod-sky/*.png|jpg) plus a dramatic sky grade.
 * A full-screen pass after the world renders touches only pixels where the depth buffer shows open
 * sky, so terrain, players and clouds stay in front of the art.
 */
public class SkyArt extends Feature {
    private final BoolSetting skyOn = new BoolSetting(LT.L1, true);
    private final ModeSetting skyMode = new ModeSetting(LT.L2, LT.L3, o -> skyOn.getValue(),
            LT.L4, LT.L3, LT.L5, LT.L6, LT.L7);
    private final ModeSetting colors = new ModeSetting(LT.L8, LT.L9, o -> skyOn.getValue(), LT.L9, LT.L10);
    private final FloatSetting intensity = new FloatSetting(LT.L11, 0.85f, 0.1f, 1.5f, 0.01f, o -> skyOn.getValue());
    private final FloatSetting speed = new FloatSetting(LT.L12, 0.25f, 0f, 2f, 0.01f, o -> skyOn.getValue());
    private final FloatSetting softness = new FloatSetting(LT.L13, 0.65f, 0f, 1f, 0.01f, o -> skyOn.getValue());
    private final FloatSetting starAmt = new FloatSetting(LT.L14, 1f, 0f, 1.5f, 0.05f, o -> skyOn.getValue());
    private final ModeSetting quality = new ModeSetting(LT.L15, LT.L16, o -> skyOn.getValue(), LT.L17, LT.L18, LT.L16, LT.L19);
    private final BoolSetting artOn = new BoolSetting(LT.L20, false);
    private final IntSetting image = new IntSetting(LT.L21, 1, 1, 50, o -> artOn.getValue());
    private final BoolSetting openFolder = new BoolSetting(LT.L22, false, o -> artOn.getValue());
    private final FloatSetting yaw = new FloatSetting(LT.L23, 180f, 0f, 360f, 1f, o -> artOn.getValue());
    private final FloatSetting pitch = new FloatSetting(LT.L24, 25f, -10f, 85f, 1f, o -> artOn.getValue());
    private final FloatSetting size = new FloatSetting(LT.L25, 45f, 5f, 120f, 1f, o -> artOn.getValue());
    private final FloatSetting opacity = new FloatSetting(LT.L26, 0.95f, 0.05f, 1f, 0.05f, o -> artOn.getValue());
    private final FloatSetting glow = new FloatSetting(LT.L27, 0.6f, 0f, 2f, 0.05f, o -> artOn.getValue());
    private final ModeSetting fit = new ModeSetting(LT.L28, LT.L29, o -> artOn.getValue(), LT.L29, LT.L30);
    private final BoolSetting followMe = new BoolSetting(LT.L31, false, o -> artOn.getValue());
    private static float skyTime;
    private static long lastNs;

    private static SkyArt instance;
    private static boolean failed;
    private static int program, depthProgram, fbo, vao, vbo, sceneCopy, depthCopy, copyW, copyH;
    private static boolean depthFresh;
    private static final Identifier IMG_ID = Identifier.of(UtilityMod.MOD_ID, LT.L32);
    private static NativeImageBackedTexture imgTex;
    private static String loadedFile;
    private static int imgW = 1, imgH = 1;
    private static final long START = System.nanoTime();

    public SkyArt() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227a,0xd13b,0xb161,0x0b3f,0xbc00,0x37db,0xa8a6,0x82c8}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}).toString(), LT.L33, Category.RENDER));
        instance = this;
    }

    private static Path folder() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(LT.L34).resolve(LT.L35);
    }

    private static List<Path> cachedList = List.of();
    private static long listedAt;

    /** Folder listing, refreshed at most every 2s (this runs every frame). */
    private static List<Path> images() {
        long now = System.currentTimeMillis();
        if (now - listedAt < 2000) return cachedList;
        listedAt = now;
        return cachedList = listImages();
    }

    private static List<Path> listImages() {
        List<Path> out = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            try (var s = Files.list(folder())) {
                s.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(LT.L36) || n.endsWith(LT.L37) || n.endsWith(LT.L38);
                }).sorted().forEach(out::add);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Override
    public CharSequence concat() {
        if (!artOn.getValue()) return skyOn.getValue() ? skyMode.getValue().toString() : "";
        List<Path> list = images();
        if (list.isEmpty()) return LT.L39;
        return list.get(Math.min(list.size(), image.getValue()) - 1).getFileName().toString();
    }

    /**
     * Called at the end of the world pass, before the hand is drawn: the hand pass clears the
     * depth buffer, so by HUD time every pixel would look like sky. Keep our own copy.
     */
    public static void captureDepth() {
        SkyArt s = instance;
        depthFresh = false;
        if (s == null || !s.isEnabled() || failed || (!s.skyOn.getValue() && !s.artOn.getValue())) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        var fb = mc.getFramebuffer();
        if (!(fb.getDepthAttachment() instanceof GlTexture depth)) return;
        int w = fb.textureWidth, h = fb.textureHeight;
        try {
            if (!ensure(w, h) || depthProgram == 0) return;
            int prevFbo = GlStateManager._getInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int prevProgram = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevActive = GlStateManager._getInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int prevTex = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevSampler = GlStateManager._getInteger(GL33.GL_SAMPLER_BINDING);
            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depthOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            try {
                GL33.glBindSampler(0, 0);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glColorMask(true, true, true, true);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, depthCopy, 0);
                GL11.glViewport(0, 0, w, h);
                GL20.glUseProgram(depthProgram);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depth.getGlId());
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
                int l = GL20.glGetUniformLocation(depthProgram, LT.L40);
                if (l >= 0) GL20.glUniform1i(l, 0);
                GL30.glBindVertexArray(vao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
                depthFresh = true;
            } finally {
                GL33.glBindSampler(0, prevSampler);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
                GL13.glActiveTexture(prevActive);
                GL30.glBindVertexArray(prevVao);
                GL20.glUseProgram(prevProgram);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
                GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
                if (blend) GL11.glEnable(GL11.GL_BLEND);
                if (depthOn) GL11.glEnable(GL11.GL_DEPTH_TEST);
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
            }
        } catch (Throwable t) {
            failed = true;
            UtilityMod.LOGGER.warn(LT.L41, t.toString());
        }
    }

    /** Called from the HUD pass (after the world, before the GUI). */
    public static void apply() {
        SkyArt s = instance;
        if (s == null || !s.isEnabled() || failed) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!s.skyOn.getValue() && !s.artOn.getValue()) return;
        if (s.openFolder.getValue()) {
            s.openFolder.setValue(false);
            try {
                Files.createDirectories(folder());
                net.minecraft.util.Util.getOperatingSystem().open(folder());
            } catch (Exception ignored) {
            }
        }
    }

    /** Runs inside the vanilla sky pass (after sun/moon/stars, before terrain). */
    public static void skyPass() {
        SkyArt s = instance;
        if (s == null || !s.isEnabled() || failed || (!s.skyOn.getValue() && !s.artOn.getValue())) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Render3D frame = EchoEvents.lastFrame();
        if (mc.world == null || frame == null) return;
        var fb = mc.getFramebuffer();
        if (!(fb.getColorAttachment() instanceof GlTexture color)) return;
        try {
            s.loadImage();
            int w = fb.textureWidth, h = fb.textureHeight;
            if (!ensure(w, h)) return;
            s.run(color.getGlId(), 0, w, h, frame);
        } catch (Throwable t) {
            failed = true;
            UtilityMod.LOGGER.warn(LT.L42, t.toString());
        }
    }

    /** Loads the selected file into a texture (PNG via NativeImage, JPG via ImageIO). */
    private void loadImage() {
        List<Path> list = artOn.getValue() ? images() : List.of();
        if (list.isEmpty()) {
            loadedFile = null;
            return;
        }
        Path file = list.get(Math.min(list.size(), image.getValue()) - 1);
        String key = file.toString() + LT.L43 + file.toFile().lastModified();
        if (key.equals(loadedFile)) return;
        loadedFile = key;
        NativeImage img = null;
        try (InputStream in = Files.newInputStream(file)) {
            if (file.toString().toLowerCase(Locale.ROOT).endsWith(LT.L36)) {
                img = NativeImage.read(in);
            } else {
                BufferedImage b = javax.imageio.ImageIO.read(in);
                if (b != null) {
                    img = new NativeImage(b.getWidth(), b.getHeight(), true);
                    for (int y = 0; y < b.getHeight(); y++) {
                        for (int x = 0; x < b.getWidth(); x++) img.setColorArgb(x, y, b.getRGB(x, y));
                    }
                }
            }
        } catch (Exception e) {
            UtilityMod.LOGGER.warn(LT.L44, file.getFileName(), e.toString());
        }
        if (img == null) {
            loadedFile = null;
            return;
        }
        if (imgTex != null) imgTex.close();
        imgW = img.getWidth();
        imgH = img.getHeight();
        imgTex = new NativeImageBackedTexture(IMG_ID::toString, img);
        MinecraftClient.getInstance().getTextureManager().registerTexture(IMG_ID, imgTex);
    }

    private float[] palette() {
        String m = skyMode.getValue().toString();
        if (colors.is(LT.L9)) {
            int c = dev.koifih.client.ui.Theme.ACCENT;
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            float[] hsb = java.awt.Color.RGBtoHSB((int) (r * 255), (int) (g * 255), (int) (b * 255), null);
            int c2 = java.awt.Color.HSBtoRGB(hsb[0] + 0.12f, Math.min(1f, hsb[1] * 1.1f + 0.15f), Math.min(1f, hsb[2]));
            return new float[] {r, g, b, ((c2 >> 16) & 255) / 255f, ((c2 >> 8) & 255) / 255f, (c2 & 255) / 255f};
        }
        return switch (m) {
            case "Aurora" -> new float[] {0.15f, 0.95f, 0.55f, 0.55f, 0.25f, 0.85f};
            case "Nebula" -> new float[] {0.55f, 0.15f, 0.75f, 0.10f, 0.45f, 0.95f};
            case "Sunset Haze" -> new float[] {1.00f, 0.55f, 0.25f, 0.95f, 0.35f, 0.45f};
            case "Storm" -> new float[] {0.35f, 0.40f, 0.55f, 0.20f, 0.22f, 0.30f};
            default -> new float[] {0.95f, 0.90f, 0.80f, 0.40f, 0.60f, 0.95f};
        };
    }

    private void run(int colorTex, int depthTex, int w, int h, Render3D frame) {
        int prevFbo = GlStateManager._getInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevProgram = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GlStateManager._getInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] prevTex = new int[3], prevSampler = new int[3];
        for (int i = 0; i < 3; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            prevTex[i] = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            prevSampler[i] = GlStateManager._getInteger(GL33.GL_SAMPLER_BINDING);
            GL33.glBindSampler(i, 0);
        }
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depthOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._disableScissorTest();
            GlStateManager._disableCull();
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GlStateManager._viewport(0, 0, w, h);

            // 1) copy the frame (can't read and write the same texture)
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, sceneCopy, 0);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, sceneCopy, 0);
            int readFbo = GL30.glGenFramebuffers();
            try {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTex, 0);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
                GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                GL30.glDeleteFramebuffers(readFbo);
            }

            // 2) sky pass into the main colour texture
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTex, 0);
            GlStateManager._glUseProgram(program);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(sceneCopy);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(depthTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GlStateManager._activeTexture(GL13.GL_TEXTURE2);
            boolean hasImage = loadedFile != null && imgTex != null && imgTex.getGlTexture() instanceof GlTexture;
            if (hasImage) {
                GlStateManager._bindTexture(((GlTexture) imgTex.getGlTexture()).getGlId());
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            }
            u1i(LT.L45, 0);
            u1i(LT.L40, 1);
            u1i(LT.L46, 2);
            Matrix4f inv = new Matrix4f(frame.projection).mul(frame.view).invert();
            float[] m = new float[16];
            inv.get(m);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, LT.L47), false, m);
            // image direction: fixed in the world, or in front of where you're looking
            float yawDeg = followMe.getValue() && MinecraftClient.getInstance().player != null
                    ? MinecraftClient.getInstance().player.getYaw() : yaw.getValue();
            double yr = Math.toRadians(yawDeg), pr = Math.toRadians(pitch.getValue());
            Vector3f c = new Vector3f((float) (-Math.sin(yr) * Math.cos(pr)), (float) Math.sin(pr), (float) (Math.cos(yr) * Math.cos(pr))).normalize();
            Vector3f right = new Vector3f(c).cross(0, 1, 0);
            if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
            right.normalize();
            Vector3f up = new Vector3f(right).cross(c).normalize();
            u3(LT.L48, c.x, c.y, c.z);
            u3(LT.L49, right.x, right.y, right.z);
            u3(LT.L50, up.x, up.y, up.z);
            u1f(LT.L51, (float) Math.tan(Math.toRadians(size.getValue() / 2f)));
            u1f(LT.L52, imgW / (float) Math.max(1, imgH));
            u1f(LT.L53, opacity.getValue());
            u1f(LT.L54, hasImage ? 1f : 0f);
            float[] pal = palette();
            long ns = System.nanoTime();
            if (lastNs != 0) skyTime += Math.min(0.1f, (ns - lastNs) / 1e9f) * speed.getValue() * 4f;
            lastNs = ns;
            int mode = switch (skyMode.getValue().toString()) {
                case "Daylight" -> 0; case "Nebula" -> 2; case "Sunset Haze" -> 3; case "Storm" -> 4; default -> 1;
            };
            GL20.glUniform1i(GL20.glGetUniformLocation(program, LT.L55), mode);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, LT.L56),
                    switch (quality.getValue().toString()) { case "Low" -> 3; case "Medium" -> 4; case "Ultra" -> 7; default -> 5; });
            u3(LT.L57, pal[0], pal[1], pal[2]);
            u3(LT.L58, pal[3], pal[4], pal[5]);
            u1f(LT.L59, skyOn.getValue() ? intensity.getValue() : 0f);
            u1f(LT.L60, softness.getValue());
            u1f(LT.L61, starAmt.getValue());
            u1f(LT.L62, skyOn.getValue() ? 1f : 0f);
            u1f(LT.L63, glow.getValue());
            u1f(LT.L64, fit.is(LT.L29) ? 1f : 0f);
            u1f(LT.L65, skyTime);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        } finally {
            for (int i = 2; i >= 0; i--) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                GlStateManager._bindTexture(prevTex[i]);
                GL33.glBindSampler(i, prevSampler[i]);
            }
            GlStateManager._activeTexture(prevActive);
            GlStateManager._glBindVertexArray(prevVao);
            GlStateManager._glUseProgram(prevProgram);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GlStateManager._viewport(vp[0], vp[1], vp[2], vp[3]);
            if (blend) GlStateManager._enableBlend();
            if (depthOn) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (scissor) GlStateManager._enableScissorTest();
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private static void u1i(String n, int v) { int l = GL20.glGetUniformLocation(program, n); if (l >= 0) GL20.glUniform1i(l, v); }
    private static void u1f(String n, float v) { int l = GL20.glGetUniformLocation(program, n); if (l >= 0) GL20.glUniform1f(l, v); }
    private static void u3(String n, float a, float b, float c) { int l = GL20.glGetUniformLocation(program, n); if (l >= 0) GL20.glUniform3f(l, a, b, c); }

    private static boolean ensure(int w, int h) {
        if (program == 0) {
            program = link();
            depthProgram = link(LT.L66);
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
            try (MemoryStack st = MemoryStack.stackPush()) {
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, st.floats(quad), GL15.GL_STATIC_DRAW);
            }
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8);
            GlStateManager._glBindVertexArray(pv);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pb);
        }
        if (sceneCopy == 0 || copyW != w || copyH != h) {
            if (sceneCopy != 0) GL11.glDeleteTextures(sceneCopy);
            int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            sceneCopy = GL11.glGenTextures();
            GlStateManager._bindTexture(sceneCopy);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GlStateManager._bindTexture(prev);
            if (depthCopy != 0) GL11.glDeleteTextures(depthCopy);
            depthCopy = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthCopy);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, w, h, 0, GL11.GL_RED, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            copyW = w;
            copyH = h;
        }
        return true;
    }

    private static int link() {
        return link(LT.L67);
    }

    private static int link(String frag) {
        int vs = compile(GL20.GL_VERTEX_SHADER, LT.L68), fs = compile(GL20.GL_FRAGMENT_SHADER, frag);
        if (vs == 0 || fs == 0) return 0;
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glBindAttribLocation(p, 0, LT.L69);
        GL20.glBindAttribLocation(p, 1, LT.L70);
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            UtilityMod.LOGGER.warn(LT.L71, GL20.glGetProgramInfoLog(p));
            return 0;
        }
        return p;
    }

    private static int compile(int type, String file) {
        try (InputStream in = SkyArt.class.getResourceAsStream(LT.L72 + file)) {
            if (in == null) return 0;
            int sh = GL20.glCreateShader(type);
            GL20.glShaderSource(sh, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            GL20.glCompileShader(sh);
            if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
                UtilityMod.LOGGER.warn(LT.L73, file, GL20.glGetShaderInfoLog(sh));
                return 0;
            }
            return sh;
        } catch (Exception e) {
            return 0;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x393c,0x516f,0x6e63}, new char[]{0x6e30,0x5104,0x3945,0x5497}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x46fa,0x5478,0x0bea,0x99da,0xfed8,0xa8a6,0xc8f7,0xcf4d}, new char[]{0x46a9,0x99b1,0x0b93,0x5458,0xfe95,0xcf22,0xc893,0xa8c3}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6179,0x0c92,0x8948,0xa59c,0xd8b7,0xf8b9}, new char[]{0xf8f8,0xa5e9,0xd8c5,0x6116,0x0ce0,0x8929}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd884,0xbac5,0x09da,0x71a9,0x62a1,0x675b,0x1e0c,0xe5b3}, new char[]{0x099e,0x71c8,0x1e75,0x6737,0xbaac,0xe5d4,0x62c9,0xd8f0}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe90b,0x9c6a,0xdd95,0x3060,0x8122,0x2800}, new char[]{0x284e,0x9c0f,0x3002,0x8157,0xddf9,0xe96a}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa41c,0x4809,0x215e,0x838f,0x55e6,0xc7de,0xcebf,0x98f6,0x372b,0x44c9,0xf0c2}, new char[]{0x485a,0xc7ab,0x9898,0x44ba,0x213b,0x5592,0xce9f,0x3763,0xa47d,0x83f5,0xf0a7}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe112,0xf9eb,0xa1f4,0xcacf,0x6f85}, new char[]{0xa1a7,0xe166,0x6fea,0xf999,0xcaa2}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6501,0x7d68,0xff8c,0x4d20,0x9e99,0xdcd5}, new char[]{0x6542,0x7d07,0x9ef5,0x4d4f,0xdca7,0xffff}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82ec,0xebff,0x4484,0xd356,0x723d}, new char[]{0x44d0,0x8284,0xeb9a,0x7250,0xd333}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcedf,0x726e,0xf674,0x328e,0x4fc9,0xdac1,0x5f08}, new char[]{0x7220,0xdaa0,0xceab,0x4fbc,0xf606,0x5f69,0x32e2}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa150,0xd567,0xf3ea,0xe81b,0xa58f,0x1c3b,0xb0a5,0xf2d0,0xc0a7,0x759c,0x8ee0,0x3600,0x5294}, new char[]{0x75cf,0xc0cc,0xf393,0xb085,0x8ea9,0xd509,0x52e0,0xa5ea,0xf2be,0xa123,0x3669,0x1c4f,0xe862}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf7a0,0xa3fa,0xdd7e,0xe7c1,0x6574,0x6bf0,0x3d78,0xec0a,0x4436}, new char[]{0x3d2b,0x6b9b,0x650d,0xec2a,0xe792,0x4446,0xf7c5,0xdd1b,0xa39e}).toString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe843,0x6973,0x511f,0x47d4,0xc433,0xf9c0,0xe461,0xa59b,0x3601,0x7dac,0x30ac,0x0318}, new char[]{0x3652,0x6918,0x0361,0x513f,0x4787,0xe82c,0xc455,0x7dd8,0xe40f,0xf9a5,0xa5e8,0x30df}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc216,0x7244,0x9870,0xd2ce,0x0e1c}, new char[]{0x7217,0x0e68,0xd2af,0x9802,0xc265}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd49b,0x1949,0xae47,0x2664,0x73ab,0x771b,0x6521,0x4af1,0xf721,0x79c9,0xc6b6}, new char[]{0x7748,0xae2c,0xd4e2,0xf701,0xc6e7,0x2611,0x6540,0x4a9d,0x73c2,0x79bd,0x1930}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe4f9,0xc493,0x0101,0xddb5}, new char[]{0xe4b1,0xc4fa,0xddd2,0x0169}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb447,0xc57b,0x8ec0}, new char[]{0xb40b,0x8eaf,0xc50c,0x1ed4}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb810,0x774f,0xe9ea,0x4b57,0x9fcd,0x5759}, new char[]{0x7702,0xb875,0x9fa9,0x5730,0xe99f,0x4b3a}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfd57,0x6b9f,0xe6f7,0x4173,0x49d2}, new char[]{0x4126,0xfd3b,0x49a6,0x6bed,0xe696}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5711,0x814f,0x6600,0xdbfd,0x004f,0x5880,0x0b19,0xb8cc,0xb842,0x90e0,0xd525,0x7bb0,0x7870}, new char[]{0xb89f,0xb829,0x7809,0x006f,0x58c1,0x0b6b,0x7bc4,0x6620,0x8106,0x577c,0x9081,0xdb9a,0xd540}).toString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8764,0x02fc,0xfdc2,0xa8e1,0x4346,0x2685,0xca37}, new char[]{0xfd8b,0x432b,0x26e4,0xca50,0xa884,0x02dc,0x8747}).toString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3c74,0x6f80,0xb811,0xcc59,0x306f,0xdae5,0x16a8,0xa87a,0x3158,0x32d1,0x15ff}, new char[]{0x3c3b,0x32a1,0x6fe5,0xda8b,0x304f,0xcc1f,0xa815,0x1593,0xb875,0x16cd,0x312a}).toString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x76fd,0xfbc6,0xcf2c,0x1ab6,0x724d,0xac9c,0x3b94,0xe196,0xf135}, new char[]{0xe1d2,0x7694,0x1ac4,0xfba3,0xcf4f,0x3be0,0x7224,0xacf3,0xf15b}).toString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa131,0xdf0e,0x72d9,0xa4fa,0xd7ec,0xabd6}, new char[]{0xa179,0xabb3,0xd785,0xa49d,0x72b1,0xdf7a}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a6c,0xcaf8,0xbee5,0x4452}, new char[]{0x5a3f,0xca91,0x4428,0xbe80}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e19,0x1a26,0x63c3,0x183f,0x27f2,0x33b9,0xc722}, new char[]{0x1a69,0x63b3,0x2793,0x33da,0x1856,0x8e6d,0xc75b}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa582,0xf8e7,0x5ec0,0xfe2a}, new char[]{0x5e87,0xa5ee,0xfe45,0xf890}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x21d0,0x8a4e,0x1382,0x0cc6,0x0c54,0x21ad,0x8c06,0xd1d1,0xcaaa}, new char[]{0x0c1d,0x8a23,0x8c67,0x21ca,0xcacf,0x13a2,0xd197,0x0caf,0x21a4}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfcd3,0xdc8d,0xbf84,0x7413,0x3782,0x43c4,0x4d18,0x6766}, new char[]{0x7440,0xdce6,0xfcaa,0x4d38,0x4380,0xbfeb,0x670b,0x37e7}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8bf3,0x5e2a,0x052b,0xb747,0x5d15}, new char[]{0x8ba3,0x054a,0x5d61,0x5e49,0xb72f}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf2ad,0xce52,0x44e8,0xa4ea,0x9e8c,0xd010,0xedfe,0x05d6,0x9f11,0x5076,0x4a6f,0x1458,0x77b4,0xa36e}, new char[]{0xd056,0xa485,0x4a03,0x9ee0,0xce3d,0x9f66,0xf28d,0x1415,0x77cd,0x05f6,0xeda8,0x4481,0x5013,0xa319}).toString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2d49,0x894e,0xee05,0xab24,0xb857,0xe676,0xe908}, new char[]{0xb824,0x8925,0xe60f,0x2d66,0xe969,0xee77,0xab50}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb3be,0xf91f,0x6f11,0xb1f8,0xc836,0xd71e,0xfbe4,0xcca1,0xfa41,0x9ea3,0x6842,0x89c0,0xcbf8,0x9818,0x0799,0x4243,0x4f5f,0x3159,0xc71f,0x85cd,0x279e,0x1a9b,0xb1f7,0xb04f,0xaf02,0xd1a1,0xa0ad,0x4648,0x23f2,0x92a1,0x877a,0xb5a6,0x7d54,0xd320,0x01f2,0xcc5e,0xee55,0xbbf3,0x68d1,0xc87e,0xbe06,0x592f,0x2251,0xd88b,0x5715,0xee09,0x03b5,0x9f01,0xbbbc,0xd391,0x5b32,0xd5ac,0x0fc3,0x39ad,0x05b1,0x670a,0x905f,0xedcb,0x18d7,0x411e,0x9310,0xda9d,0xce51,0xbe10,0x146a,0x995a,0x8bad,0x7dfc,0x37d5,0x7efa,0x760c,0xe00e,0x2c09,0xa054,0x1353,0x8f40,0xcfda,0xd2b9,0x6bd3,0xdc34,0xa4ae,0xdd06,0xfe39,0xf7d5,0x9c45,0x7e3a,0xef36,0xb547,0x2672,0x3ee4,0xafcd,0xfb5a,0x6bac,0x1a0f,0xf563,0x94dd,0x0bef,0x2300,0x3b33,0x91fd,0xa25d,0xb174,0xdca1,0x0a43,0xddf7}, new char[]{0x3794,0xbe7e,0x1403,0xfa2c,0xb115,0x39d9,0x7d99,0x07fd,0x413e,0x6bdf,0xb193,0x92d8,0xf543,0x426b,0xfb85,0x7d21,0xbbce,0xfb35,0xcfa8,0x27ff,0x5739,0xa08d,0xb021,0x8f25,0xce33,0xbb86,0x0faf,0xdcc0,0x8b81,0x3b13,0xee36,0x6f7d,0xd34f,0xf7a0,0xa030,0x0b9c,0xcc72,0x0591,0x9c36,0x677f,0xef58,0x2601,0x85a8,0xb5d2,0xdddb,0xee29,0xc80d,0xb3ca,0xdc5b,0x7e88,0x1af6,0x9973,0x01d2,0xf934,0x0395,0x9026,0x94b2,0x1a7a,0x8708,0xd8ab,0x4627,0x2377,0xd5c2,0x91dd,0xcb91,0xc85b,0xbe67,0xc778,0x594a,0x6ba0,0x18f7,0x9379,0x682c,0xdabd,0x2386,0xd3f9,0xd2dc,0x68f1,0xfe4a,0xa4c5,0xd1d8,0xa27d,0xcc89,0x766f,0x133c,0x2c67,0xdd60,0xb19e,0x89a7,0x3ecb,0xe07b,0xafb9,0x7e53,0x4f33,0xd777,0x986c,0x5b4b,0x3134,0xeda4,0xb523,0x9e8e,0x0a30,0x223a,0xaf7b,0x9f28}).toString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x37f2,0xe7fe,0x2d92,0xd560,0x53a9,0x9d25}, new char[]{0xe79d,0x9d4a,0x379c,0xd506,0x53c0,0x2df5}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x20b0,0x26c6,0x33a1,0x198f,0x3626,0x79bc,0x4c55,0x3668,0x2483,0xaf7b,0x40f4,0xd4b5,0x9b8c,0x90ba}, new char[]{0x361d,0x9bf8,0x24ea,0x33cd,0x20d9,0x3652,0x79c5,0x26ab,0x4c3a,0x90de,0x19a2,0x4087,0xaf10,0xd4cc}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2bb8,0xbc07,0x03f9,0xe3cc}, new char[]{0x2b96,0xe3bc,0x0397,0xbc60}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0ab3,0x84b7,0x2e3f,0x4d46}, new char[]{0x2e11,0x4d2c,0x0ac3,0x84d0}).plainString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f27,0x2e1a,0x0efe,0x6d0d,0x12da}, new char[]{0x6d23,0x0e94,0x2f57,0x12bf,0x2e7d}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x81d6,0x0ec5,0x2c5d,0x2ddb,0xcc22,0x9ab5,0x929e,0x020a,0x5165}, new char[]{0xcc4c,0x0eaa,0x92be,0x2c34,0x9ad8,0x026b,0x5102,0x81b3,0x2da8}).plainString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc3aa,0xfa2a,0xb4c7,0x5b97,0x9db6,0x00ec}, new char[]{0x0099,0xb483,0x9dd3,0x5be7,0xfa5e,0xc3c2}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7959,0x035d,0x1ba1,0x6bb0,0x13f7,0x2035,0x212f,0xc189,0x35b8,0x97c9,0x02da,0xf352,0x9e47,0x4325,0x740f,0x48e6,0xcd2a,0x0ff7,0x31d4,0x6fb0,0x41da,0x53cc,0xe6f2,0xf34f,0x2f5c,0xcfa4,0x89be,0xd5f7,0xd7ad,0xa716,0x73d9,0x0a04,0x0627,0x6e13,0xf82a,0x8b45,0x7bca,0x2a76,0x5511,0x4bc9,0x70c7,0xba94,0xf2fc}, new char[]{0x4181,0x7092,0xd7d9,0x7466,0x97a5,0x9e2e,0xf85e,0xe68b,0xf2b1,0x7936,0xf336,0x13aa,0x7bea,0xcd6b,0x2f31,0x4884,0x5578,0x2a13,0x6fde,0x6e70,0xd592,0x899e,0x73bd,0x0f92,0x4bb9,0x53b8,0x064f,0x2015,0x8b26,0x1bce,0x35c8,0xf336,0xcf84,0xa770,0x02bb,0x0a6d,0x31b8,0x0338,0xbaf0,0x2115,0xc1a9,0x435e,0x6bcd}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c8f,0xb166,0xa89e,0x226c,0xd11e,0xd11b,0xb135,0xbc0c,0x962c,0x2274,0x82c9,0x9630,0x2279,0xbc00,0x37d2,0xa8a9,0xd121,0x37ca,0x37da,0x82d4,0x0b28,0x1c9b,0xb152,0xa8e4,0x0b7c,0x1cbf,0xbc1b,0x0b7c,0x82d6}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd91c}, new char[]{0xd926,0xfdd5,0x15ed,0xb4bb}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8e4,0x0b27,0xa8eb,0xa8af,0x82d0,0x82ce,0xd116,0xbc1b,0x0b7c,0x37d1,0x9636,0xbc07,0x0b7c,0x1c97,0xd11e,0x37d2,0x2279,0xbc14,0xd121,0x3784,0x9622,0xb152,0xa89e,0xb12f,0xb17b,0x82df,0x1c9d,0x82d4,0x37ca,0xb166,0x2263,0x2238,0xbc00,0x1cbf,0x962c,0xd11e,0x226c,0x0b28}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227b,0xb16a,0xbc0c,0xd10f,0xa898,0x37d0}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8b34,0xdc0d,0xad9b,0x35f9,0xc3bb,0x45e2}, new char[]{0xc3ce,0xdc44,0x458f,0xadfa,0x359e,0x8b51}).plainString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x74a5,0x094d,0x88fe,0xf68b,0xe122,0xed7d,0xedca,0xd4ff,0x6fba,0x696c,0x21b9,0x3f86}, new char[]{0x3ff3,0x74ec,0xe14c,0x691a,0x091b,0x8897,0x21dc,0xed0a,0xed9a,0xd48d,0xf6e4,0x6fd0}).plainString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f9d,0x2ee0,0xcca4,0x0b5b,0x51e9,0x580e,0x5012}, new char[]{0x0b2e,0x584d,0x5077,0xccca,0x519d,0x2ff8,0x2e92}).plainString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2323,0x22fa,0x49d6,0x59b2,0x21e4,0x7e7a}, new char[]{0x2356,0x59e0,0x218d,0x7e1d,0x49be,0x228e}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5d55,0xee54,0x7329}, new char[]{0xee21,0x5d00,0x7359,0xebec}).plainString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd771,0x0dbf,0x95de,0x9e00,0x2673,0xb2dd,0x1987,0xd27e}, new char[]{0xb2a8,0x958a,0x2612,0x9e6e,0x19cf,0xd21f,0x0dd3,0xd717}).plainString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa4fc,0x5ea8,0x730f,0x29be,0x5c18,0x6091,0xc899}, new char[]{0xa489,0x29ff,0x737c,0x5c68,0x60f4,0xc8fa,0x5edc}).plainString();
        static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x03ce,0x104c,0x36d4,0x9ebe,0xfc19,0x36e4,0xdaa0,0xfdc4}, new char[]{0xfdb1,0x369b,0x103c,0x03af,0x9edd,0xdac9,0xfc6d,0x369d}).plainString();
        static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x00cc,0x98b6,0x49cd,0x5646,0x7f96,0x95ef,0xb6f1,0xebcb,0x95be}, new char[]{0x7fe3,0x95a7,0x00ad,0x95cd,0xb6b8,0x562b,0x98d7,0x49aa,0xebae}).plainString();
        static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd011,0x1d10,0x0c4a,0x1a2e,0x4097}, new char[]{0x1d65,0x1a63,0xd07e,0x0c2e,0x40f2}).plainString();
        static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7244,0x2de2,0x47f2,0xa9f8,0x4949,0x2a2a,0xd431,0x92c9}, new char[]{0x2d97,0x2a65,0x492a,0x4786,0x7225,0xd447,0x92ac,0xa98b}).plainString();
        static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x26f5,0xff48,0xf3f5,0x81d4,0xadc2}, new char[]{0xf380,0xad81,0x81bb,0xff24,0x26b4}).plainString();
        static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8c66,0xc183,0xea83,0xf4f2,0x7ba3}, new char[]{0xeaf6,0xc1c0,0xf49d,0x8c0a,0x7be1}).plainString();
        static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x96f1,0xbe60,0x03c9,0x18ae,0x5699,0x3a00,0xca53,0xa739,0xdc9c,0x1900}, new char[]{0xa74c,0x18e7,0xbe0e,0x03bd,0xca36,0x56f7,0x9682,0x1969,0x3a74,0xdce5}).plainString();
        static final String L60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x828f,0xee9f,0x4b12,0x5352,0xcbe0,0x000a,0xce22,0xdb7d,0x8723}, new char[]{0xcb95,0xdb2e,0xce4d,0x82e9,0xeeeb,0x874d,0x4b77,0x5321,0x0079}).plainString();
        static final String L61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe7ed,0x9262,0x3fd1,0x4c55,0xd0d5,0xbef4}, new char[]{0x4c20,0xe7be,0x9216,0xbe95,0xd0a7,0x3fa2}).plainString();
        static final String L62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2d8c,0xbf28,0xa5dc,0x4cc3}, new char[]{0x4cb6,0x2ddf,0xbf43,0xa5a5}).plainString();
        static final String L63 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2707,0x715c,0x9e61,0xedb1,0xca50}, new char[]{0xca25,0x2740,0xeddd,0x7133,0x9e16}).plainString();
        static final String L64 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1398,0x82eb,0x01c1,0x0f2b,0xfa74}, new char[]{0x01b4,0xfa30,0x0f44,0x13f5,0x828e}).plainString();
        static final String L65 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbca3,0x773d,0xee65,0x90f1,0x853f}, new char[]{0xbcd6,0xee31,0x8556,0x7750,0x9094}).plainString();
        static final String L66 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x92e3,0x9469,0xbd4f,0xcf48,0xb3ce,0xb210,0x4a37,0x750b,0x8111,0x70fc,0x096d,0x8960,0x318c,0xdb3b}, new char[]{0xb3bd,0xcf23,0x929a,0x31d3,0x8904,0xdb5e,0x8161,0x757f,0x9401,0xb23e,0x090b,0x708e,0xbd2e,0x4a50}).plainString();
        static final String L67 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x56d0,0x98b7,0x9ab2,0xfcb6,0xd989,0x2ea4,0x2607,0xc627,0xda48,0xca21,0x3b94,0xe974}, new char[]{0xc654,0xd9e2,0x56a9,0xe92b,0xca40,0x98c5,0xda3c,0x2629,0xfcd0,0x3be6,0x9ad3,0x2ec3}).plainString();
        static final String L68 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf601,0x5445,0xc7c5,0xb4b3,0xa0b3,0x3309,0xf5f4,0x0c99,0x1d0e,0xe4b1,0x4c41,0x78ff,0x826d,0x042c,0xfbc3,0x2cbf,0xcb78}, new char[]{0x8201,0x5422,0xe4ee,0x4c22,0xb4dc,0xc7b5,0x7886,0x3356,0xfbb2,0x0459,0xa0d2,0xcb1c,0xf5da,0xf677,0x0cfc,0x2ccd,0x1d7a}).plainString();
        static final String L69 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2772,0x73b4,0x92e3,0x5081,0xdc1f}, new char[]{0x9282,0xdc40,0x2702,0x50ee,0x73c7}).plainString();
        static final String L70 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x81a0,0xad0c,0xac99,0xf677}, new char[]{0xacf8,0xad53,0xf602,0x81d6}).plainString();
        static final String L71 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1c9b,0x1cbf,0x2279,0xa8eb,0x37d2,0x962d,0xbc1b,0x0b39,0xd101,0xb152,0x1cc8,0xb166,0x9663,0xa8e4,0xd111,0x37ca,0xa89e,0x0b7c,0xa8b6,0x962c,0x227e,0x82d4,0x82c1,0xbc08,0xd11e,0x226c,0x0b28,0xbc00,0xd121,0xb163,0x37d7,0x82c9}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L72 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8aa,0x2271,0x1c87,0xa8aa,0x37db,0xbc1a,0xb17b,0x1cdd,0x0b33,0xd155,0x2237,0x8282,0xb162,0xd109,0xb17c,0xbc1d,0x37cc,0x9622,0xd113,0x1c9e,0x37c7,0x227c,0x9637,0x0b73,0xa8a7,0x0b2f,0x82ca,0xbc0c,0x82c9,0xd112,0x226b,0xa8b8,0x9630}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L73 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc1b,0xb166,0x0b7c,0x1c9a,0x37d2,0xbc49,0xb152,0xa89e,0xb172,0x82d4,0x9622,0xa8e4,0x0b28,0x1c89,0x37ca,0xd11e,0x37c5,0x226a,0x963e,0xa8ae,0x1cbf,0xd121,0x226c,0xd11e,0x0b66,0x2279,0x828d,0xbc00,0x962c,0x82de}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
    }
}
