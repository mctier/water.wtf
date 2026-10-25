package com.utilitymod.anubis.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.gui.screen.dialog.DialogScreen;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.dialog.type.Dialog;

/**
 * Replaces Anubis' mixin accessors (late injection has no mixins). Members are found by type,
 * so this works with both yarn (dev) and intermediary (runtime) names.
 */
public final class Access {
    private static Field dialogField, pendingField;
    private static Method fovMethod;
    private static boolean fovMissing;

    private Access() {}

    private static Field fieldOfType(Class<?> owner, Class<?> type) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        throw new IllegalStateException(LT.L1 + type.getSimpleName() + LT.L2 + owner.getSimpleName());
    }

    public static Dialog dialog(DialogScreen<?> screen) {
        try {
            if (dialogField == null) dialogField = fieldOfType(DialogScreen.class, Dialog.class);
            return (Dialog) dialogField.get(screen);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static PendingUpdateManager pendingUpdates(ClientWorld world) {
        try {
            if (pendingField == null) pendingField = fieldOfType(ClientWorld.class, PendingUpdateManager.class);
            return (PendingUpdateManager) pendingField.get(world);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** GameRenderer.getFov(Camera, float, boolean); falls back to the FOV option. */
    public static float fov(GameRenderer renderer, Camera camera, float tickDelta, boolean changing) {
        if (!fovMissing && fovMethod == null) {
            for (Method m : GameRenderer.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getReturnType() == float.class && p.length == 3 && p[0] == Camera.class
                        && p[1] == float.class && p[2] == boolean.class) {
                    m.setAccessible(true);
                    fovMethod = m;
                    break;
                }
            }
            fovMissing = fovMethod == null;
        }
        if (fovMethod != null) {
            try {
                return (float) fovMethod.invoke(renderer, camera, tickDelta, changing);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return net.minecraft.client.MinecraftClient.getInstance().options.getFov().getValue();
    }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x35c8,0x6b82,0x01e9}, new char[]{0x0187,0x6bed,0x35e8,0xea17}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8999,0x6a1e,0xf965,0x7b32,0xda9d,0x12a7,0x3036,0x1458,0x0a04,0x93d9}, new char[]{0x7b12,0x143e,0x93b0,0x12c2,0x0a68,0x89fd,0x3016,0xdaf4,0xf90b,0x6a3e}).plainString();
    }
}
