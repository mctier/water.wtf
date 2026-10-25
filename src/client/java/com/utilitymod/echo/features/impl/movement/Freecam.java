package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.event.impl.MouseUpdateEvent;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Echo-style Freecam (reference architecture): the camera position/rotation lives in this
 * feature, integrates once per rendered frame from the movement keys, and is applied at
 * render time through {@code Hooks.cameraUpdateReturn} (Camera.update RETURN).
 *
 * <p>The player entity is never touched: no teleport on exit, no flying body, no velocity
 * or gravity state to restore. Enabling/disabling is invisible on the wire.
 */
public class Freecam extends Feature {
    private final FloatSetting speed = new FloatSetting(LT.L1, 1.0f, 0.1f, 5.0f, 0.1f);
    private final FloatSetting speedVert = new FloatSetting(
        dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x48,0x4C,0x02,0xCF,0xAB,0x6E,0x35,0xF3,0x86,0xA2,0x48,0x26,0xEF,0xB1}),
        1.0f, 0.1f, 5.0f, 0.1f);

    private static volatile boolean active;

    // Camera state is static: Hooks.cameraUpdateReturn calls the static applyCamera.
    private static volatile Vec3d position;
    private static volatile float yaw, pitch;
    private Perspective savedPerspective;

    private static void placeCamera(net.minecraft.client.render.Camera camera) throws Exception {
        // setPos/setRotation are protected on Camera in 1.21.11 - reach them by reflection.
        com.utilitymod.echo.api.Accessors.method(net.minecraft.client.render.Camera.class, 3,
            LT.L2, LT.L3)
            .invoke(camera, position.x, position.y, position.z);
        com.utilitymod.echo.api.Accessors.method(net.minecraft.client.render.Camera.class, 2,
            LT.L4, LT.L5)
            .invoke(camera, Freecam.yaw, Freecam.pitch);
    }

    public Freecam() {
        super(new FeatureInfo(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b31,0xd13c,0x37dd,0xb16e,0xbc0c,0x227d,0xa8b9}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}).toString(),
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb167,0x76b5,0xd962,0x58ec,0x723c,0xeb84,0x8671,0x1812,0xd13e,0x8af2,0xac47,0x6516,0xe77a,0xb994,0x2619,0xa8ae,0x4a0f,0x479c,0x508e,0x6d66,0x3fef,0xf048,0x1cd2,0xb547,0x0b39,0x226c,0xcdbe,0x9e55,0x1584,0x11f8,0xfce4,0x3b71,0x2a8c,0xf897,0xc5c9,0x9637,0x0023,0x3356,0xa1af,0x7ed2,0xd2bc,0xc148,0x82de,0x0cc7,0x04f1,0x5f69,0xa429,0x93a1,0xbc08,0x37dd,0x9bf7}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8}).toString(),
            Category.MOVEMENT));
    }

    public static boolean isActive() { return active; }

    /** Render-stage camera placement, called from Hooks.cameraUpdateReturn after vanilla Camera.update. */
    public static void applyCamera(net.minecraft.client.render.Camera camera) {
        if (!active || position == null) return;
        try {
            placeCamera(camera);
        } catch (Throwable ignored) {
            // never break the render pipeline; the camera just stays vanilla until fixed
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player == null) { setEnabled(false); return; }
        position = mc.player.getEyePos();
        savedPerspective = mc.options.getPerspective();
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        active = true;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        active = false;
        position = null;
        if (mc.options.getPerspective() != savedPerspective) {
            try { mc.options.setPerspective(savedPerspective); } catch (Exception ignored) {}
        }
    }

    private void rotate(float yawDelta, float pitchDelta) {
        this.yaw += yawDelta * 0.15f;
        this.pitch = MathHelper.clamp(this.pitch + pitchDelta * 0.15f, -90f, 90f);
    }

    @EventSubscribe
    private void onRender3D(EventRender3D event) {
        if (!active || position == null) return;
        float sp = speed.getValue() * event.getDeltaTicks();
        // Same math as RotationHandler.getRotationVector: (-sinYaw*cosPitch, -sinPitch, cosYaw*cosPitch).
        double yawRad = java.lang.Math.toRadians(yaw);
        double pitchRad = java.lang.Math.toRadians(pitch);
        Vec3d look = new Vec3d(
            -java.lang.Math.sin(yawRad) * java.lang.Math.cos(pitchRad),
            -java.lang.Math.sin(pitchRad),
            java.lang.Math.cos(yawRad) * java.lang.Math.cos(pitchRad));
        // look x (0,1,0) — the camera-right vector on the horizontal plane.
        Vec3d rightFlat = new Vec3d(-look.z, 0, look.x);
        Vec3d right = rightFlat.lengthSquared() > 1.0e-8 ? rightFlat.normalize() : Vec3d.ZERO;
        if (mc.options.forwardKey.isPressed()) position = position.add(look.multiply(sp));
        if (mc.options.backKey.isPressed()) position = position.subtract(look.multiply(sp));
        if (mc.options.leftKey.isPressed()) position = position.subtract(right.multiply(sp));
        if (mc.options.rightKey.isPressed()) position = position.add(right.multiply(sp));
        if (mc.options.jumpKey.isPressed()) position = position.add(0, speedVert.getValue() * event.getDeltaTicks(), 0);
        if (mc.options.sneakKey.isPressed()) position = position.subtract(0, speedVert.getValue() * event.getDeltaTicks(), 0);
    }

    @EventSubscribe(priority = EventSubscribe.Priority.HIGH)
    private void onMouse(MouseUpdateEvent event) {
        if (!active) return;
        rotate((float) event.deltaX, (float) event.deltaY);
        event.cancel();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (!active) return;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    @EventSubscribe
    private void onStartAttack(EventStartAttack event) {
        if (active) event.cancel();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc4fb,0xf46c,0xa58f,0x0a36,0x5814}, new char[]{0xa5dc,0xf41c,0x0a53,0xc49e,0x5870}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6008,0xb25a,0x21db,0xca13,0x29b4,0x50f8,0xaf37,0xcf40,0x1984,0x0d20,0x6232,0x58a5}, new char[]{0xcf2d,0x6257,0x21af,0x58cd,0x5097,0x0d44,0x29eb,0xca22,0x19bd,0x603b,0xaf05,0xb26d}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfceb,0xb621,0x5419,0xbf46,0x7f30,0xb1a1}, new char[]{0xb652,0xbf23,0x7f44,0xfcbb,0xb1ce,0x546a}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x32c9,0x51ac,0xa4c1,0x73fc,0x3047,0x490c,0xcb06,0x5b44,0x6ad2,0xe286,0xa2c1,0xf3e2}, new char[]{0x6abf,0xa4a4,0xf396,0x302f,0xe2e9,0x51c8,0x4953,0x73cd,0xa2f8,0x32fa,0xcb34,0x5b71}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf04d,0x4657,0xbd83,0x2e3c,0xdc09,0x1049,0xa09b,0x5ce0,0x5ea9,0x3e5d,0x3582}, new char[]{0x4624,0x35e7,0x2e48,0xdc5b,0x5ec6,0x103d,0xa0fa,0x5c94,0xf024,0x3e32,0xbded}).plainString();
    }
}
