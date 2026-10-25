package com.utilitymod.echo.api;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replacements for echo's mixin accessors/invokers. A late-injected jar gets
 * no mixins, so private members are reached by reflection, trying the
 * runtime (intermediary) name first and the Yarn name second (dev runs).
 */
public final class Accessors {
    private static final ConcurrentHashMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHODS = new ConcurrentHashMap<>();

    private Accessors() {}

    public static Field field(Class<?> owner, String... names) {
        return FIELDS.computeIfAbsent(owner.getName() + LT.L1 + names[0], k -> {
            for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
                for (String n : names) {
                    try {
                        Field f = c.getDeclaredField(n);
                        f.setAccessible(true);
                        return f;
                    } catch (NoSuchFieldException ignored) {
                        // try next
                    }
                }
            }
            throw new IllegalStateException(LT.L2 + owner.getName() + LT.L3 + names[0]);
        });
    }

    public static Method method(Class<?> owner, int params, String... names) {
        return METHODS.computeIfAbsent(owner.getName() + LT.L1 + names[0] + LT.L4 + params, k -> {
            for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() != params) continue;
                    for (String n : names) {
                        if (m.getName().equals(n)) {
                            m.setAccessible(true);
                            return m;
                        }
                    }
                }
            }
            throw new IllegalStateException(LT.L5 + owner.getName() + LT.L3 + names[0]);
        });
    }

    // ---- MinecraftAccessor ----

    /** MinecraftClient.doAttack() (Mojang startAttack). */
    public static boolean invokeStartAttack() {
        try {
            return (boolean) method(MinecraftClient.class, 0, LT.L6, LT.L7).invoke(MinecraftClient.getInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Clear MinecraftClient.attackCooldown so a programmatic doAttack() is not gated by it. */
    public static void resetAttackCooldown() { setAttackCooldown(0); }

    /** Set MinecraftClient.attackCooldown. After an attack, restoring it (e.g. to 10) keeps vanilla's
     *  post-hit guard so looking at a block does not immediately start mining it. */
    public static void setAttackCooldown(int ticks) {
        try {
            field(MinecraftClient.class, LT.L8, LT.L9).setInt(MinecraftClient.getInstance(), ticks);
        } catch (Exception ignored) {
            // field name drift: Send Input still works, it just keeps vanilla's between-attack gate
        }
    }

    /** MinecraftClient.doItemUse() (Mojang startUseItem). */
    public static void invokeStartUseItem() {
        try {
            method(MinecraftClient.class, 0, LT.L10, LT.L11).invoke(MinecraftClient.getInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- KeyMappingAccessor ----

    public static InputUtil.Key getKey(KeyBinding binding) {
        return KeyBindingHelper.getBoundKeyOf(binding);
    }

    public static int getClickCount(KeyBinding binding) {
        try {
            return field(KeyBinding.class, LT.L12, LT.L13).getInt(binding);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    public static void setClickCount(KeyBinding binding, int count) {
        try {
            field(KeyBinding.class, LT.L12, LT.L13).setInt(binding, count);
        } catch (IllegalAccessException ignored) {
            // keep vanilla count
        }
    }

    // ---- ClientInputAccessor ----

    public static void setMovementVector(net.minecraft.client.input.Input input, net.minecraft.util.math.Vec2f vector) {
        try {
            field(net.minecraft.client.input.Input.class, LT.L14, LT.L15).set(input, vector);
        } catch (IllegalAccessException ignored) {
            // keep vanilla vector
        }
    }

    // ---- FireworkRocketAccessor / EntityAccessor / AbstractArrowAccessor ----

    public static int fireworkLife(Object firework) {
        try {
            return field(net.minecraft.entity.projectile.FireworkRocketEntity.class, LT.L16, LT.L17).getInt(firework);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    public static int fireworkLifetime(Object firework) {
        try {
            return field(net.minecraft.entity.projectile.FireworkRocketEntity.class, LT.L18, LT.L19).getInt(firework);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    public static void setSharedFlag(Object entity, int flag, boolean value) {
        try {
            method(net.minecraft.entity.Entity.class, 2, LT.L20, LT.L21).invoke(entity, flag, value);
        } catch (ReflectiveOperationException ignored) {
            // simulation keeps the default flags
        }
    }

    public static boolean arrowInGround(Object arrow) {
        try {
            return (boolean) method(net.minecraft.entity.projectile.PersistentProjectileEntity.class, 0,
                    LT.L22, LT.L23).invoke(arrow);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    // ---- LivingEntityAccessor ----

    public static void setJumpInstant(LivingEntity entity, int ticks) {
        try {
            field(LivingEntity.class, LT.L24, LT.L25).setInt(entity, ticks);
        } catch (IllegalAccessException ignored) {
            // keep vanilla delay
        }
    }

    public static int getAttackStrengthTicker(LivingEntity entity) {
        try {
            return field(LivingEntity.class, LT.L26, LT.L27).getInt(entity);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    /** Force the melee attack cooldown to fully charged, so doAttack()'s minimum-charge gate passes. */
    public static void setAttackStrengthTicker(LivingEntity entity, int ticks) {
        try {
            field(LivingEntity.class, LT.L26, LT.L27).setInt(entity, ticks);
        } catch (Exception ignored) {
            // leave vanilla charge; Direct input mode still bypasses the gate
        }
    }

    public static int getItemSwapTicker(LivingEntity entity) {
        try {
            return field(LivingEntity.class, LT.L28, LT.L29).getInt(entity);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6638}, new char[]{0x661b,0x2f9b,0x58a9,0x2484}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe09f,0x82f4,0x90cc,0xd742,0x22f1,0x8d1d,0x6d00,0x1682,0x434a,0xecf1,0x5c73,0x3efc,0x63c4,0x7714,0x145b,0x0f8b,0xb8d9}, new char[]{0x6d66,0x0fe2,0x143e,0x7778,0xec95,0xb8f9,0x229f,0x4325,0xd736,0x5c53,0xe0f9,0x8d72,0x16f7,0x63aa,0x90a8,0x3ec6,0x82d4}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0959}, new char[]{0x0977,0x9b0c,0x9fd5,0x7f9e}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe00d}, new char[]{0xe022,0x2a56,0x1f24,0x4395}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c90,0x40a9,0x28a0,0xffa9,0x85cb,0x4138,0x76aa,0x52e9,0xc819,0xb3cc,0x4084,0x80da,0x8956,0x15a1,0x35ed,0xd833,0x14c7,0x8fc3}, new char[]{0xd85e,0x15c4,0x40dd,0x893e,0xc876,0x80be,0x52c9,0x76c4,0x4157,0x85bf,0x7cb0,0xffcf,0xb3a3,0x8fb6,0x40ea,0x3589,0x14fd,0x2880}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3768,0x745d,0x1a59,0x31d9,0x6e09,0x8bd6,0x2775,0x1edb,0xc5b5,0x7bd4,0xabbd}, new char[]{0x7bb9,0x7438,0x31ad,0x3700,0xc5da,0x1ebf,0x272a,0x8be7,0x1a6c,0x6e3a,0xab8b}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x61bc,0xfa83,0xbf84,0x613d,0x89e0,0x046d,0xd168,0x05c5}, new char[]{0xfae7,0x6152,0xbfc5,0x05b1,0x8994,0x61dd,0x040e,0xd103}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4965,0x8d6f,0x009c,0x4562,0x7f14,0x8242,0x9104,0xe00f,0x53f7,0xbbd8}, new char[]{0x8224,0xbbb1,0x8d0a,0xe063,0x9160,0x453d,0x00ad,0x7f23,0x53c0,0x4954}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe367,0x69a1,0xa673,0xf533,0x96e4,0x294e,0x7ab8,0xe13e,0x461c,0x7370,0x6d6b,0xefd6,0x3bec,0x7b89}, new char[]{0x467d,0xa607,0xe14a,0x7ad9,0x7bea,0x3b87,0x69e2,0x6d04,0xe308,0xf55f,0x7314,0x2921,0x9693,0xefb8}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0a17,0xf8e9,0x9182,0xee7d,0x87aa,0x4808,0x9941,0x2be6,0xbfe1,0x36db,0x11af}, new char[]{0x992c,0xf88c,0xbf95,0x11c7,0x91ed,0x87ce,0x2bb9,0x4839,0x0a22,0x36e3,0xee4e}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9804,0x2e56,0xb94e,0x0166,0xe4a4,0x61aa,0x0eb7,0x787d,0xcd12}, new char[]{0xcd76,0x0109,0x2e1f,0x9870,0x7818,0xb923,0xe4f1,0x61d9,0x0ed2}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x64af,0x753e,0x3068,0xb1fe,0xf358,0xe8d0,0xdd04,0xa55c,0x8780,0x679a}, new char[]{0x67fc,0x7557,0xa539,0x64c3,0x87e4,0xf307,0xdd35,0xb1c8,0xe8e6,0x3059}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8503,0x6530,0xc6cc,0x1285,0x88b2,0xb5b1,0xa0b6,0xd820,0xf9e9,0x2489,0x074c,0x4d32}, new char[]{0x6544,0xb5d8,0x856e,0xc6a9,0x88c1,0xa0e6,0x24fb,0xf98c,0xd853,0x073f,0x12e0,0x4d56}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf32c,0xd871,0xca1b,0x2719,0xc43d,0x8f14,0x07b1,0x71e9,0x5361,0xe742,0xdcd9}, new char[]{0x277f,0xd818,0x718c,0xdcb5,0x8f70,0x07ee,0xca2e,0xe777,0xc405,0x5357,0xf314}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd72f,0x1453,0x775d,0x4e3f,0x02b2,0x3fcf,0x3d1f,0x8b0c,0x69fa,0xac94,0x7857,0xf767,0x4bc1,0x62ea}, new char[]{0x783a,0x8b63,0x1425,0x7738,0x3d72,0x3faa,0x6994,0x4bb5,0xf731,0x4e5a,0xacf7,0xd75b,0x02dd,0x6298}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0614,0x4816,0xb5c0,0x5cc1,0xaca0,0x65fe,0x93ea,0x114e,0xe68e,0x8127}, new char[]{0x6598,0xe6e7,0x112b,0xb5ac,0x0670,0xacff,0x4821,0x8111,0x93db,0x5cf2}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x242a,0x6bfc,0x43b8,0xf00c}, new char[]{0x6b90,0xf065,0x244c,0x43dd}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcd34,0x1121,0x5c74,0x166f,0x9fc8,0xc713,0xd828,0x6d32,0x7521,0x759c}, new char[]{0x1147,0x7548,0x9fad,0xc77f,0xcd50,0x75c3,0x5c43,0x1659,0xd819,0x6d00}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d9d,0xb702,0xae6f,0x141a,0x3a5a,0x3855,0xccbd,0xe3f0}, new char[]{0x1df1,0xe399,0x3833,0x3a3f,0xcce9,0xb76b,0xae02,0x147f}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaf22,0x88c4,0x5ac7,0xab4e,0x4c5e,0x9f2d,0xbf13,0x417f,0x36cb,0x3ad0,0xe8c9}, new char[]{0x4112,0xaf47,0xe8bd,0x88ac,0x36a4,0x4c3a,0x5a98,0x9f18,0xab79,0xbf21,0x3ae9}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x13c0,0x9b99,0x6bbf,0xe28a,0xfe60,0x82b8,0x4254}, new char[]{0x6bcc,0xe2ef,0x13b4,0x4212,0x82d4,0xfe01,0x9bfe}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc5a5,0xf0b1,0x3d10,0x30c1,0x041a,0xa9b9,0x5b36,0x6975,0xf260,0x9b76,0xe07d,0xd0d4}, new char[]{0x5b5b,0x047f,0x30b5,0xf208,0xa9d6,0x9b12,0xd08b,0xc593,0x6940,0x3d20,0xf084,0xe044}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3eaa,0x1771,0x2b13,0xee6f,0x58b7,0x5a7c,0xa443,0xce64,0x3fad,0x3f2c}, new char[]{0xee06,0x1702,0x58fe,0x3f42,0x5a3b,0xa431,0x2b7c,0xce11,0x3fc3,0x3ece}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x309d,0x7873,0x85d1,0x3cfd,0x5e94,0x77b7,0x8fbf,0xca53,0xf1f4,0xab76}, new char[]{0x3c9b,0x5efd,0xab13,0xca3f,0x30f9,0x8fe0,0x7845,0x85e3,0x7785,0xf1cc}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb820,0x019a,0xd729,0xa90b,0x458e,0xb6bf,0xa00d,0x69a3,0x8598,0x263b,0x0634,0x8fc7,0xdfc1,0xb3af,0xcd00}, new char[]{0x2651,0x0641,0xdfac,0xb6cf,0xd740,0x85f6,0x01fd,0xb863,0xb3c0,0xa964,0xa061,0x69c7,0x8fa8,0xcd77,0x45e0}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x44ae,0x725f,0xaeca,0xc397,0x8a51,0xf0a5,0xaa5b,0xfd46,0x165f,0xfc43}, new char[]{0xaa3d,0x7236,0x8a34,0x44c2,0xfd22,0xae95,0xf093,0x166d,0xc3a0,0xfc70}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x95b5,0x9a9f,0xbb0a,0x76e3,0x0b93,0x69a0,0x61fb,0x0c5d,0x795c,0x0c41,0x63ed,0x25d2,0xd4cc,0x58ba,0xe7fa,0x53d0,0x9f43,0x7c24,0x971e,0x7cbf}, new char[]{0xe78e,0x9777,0x25b1,0x7937,0x7690,0x7c77,0x0c28,0x0bfd,0x7cdc,0x58df,0xd480,0x95d4,0x69d3,0x618f,0xbb4b,0x6399,0x0c29,0x9f22,0x53b3,0x9af4}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdc0e,0x1516,0x7a23,0xe597,0x479d,0x2ca5,0xc3d9,0x8d90,0xc1b2,0x4175,0xda15}, new char[]{0xda73,0x47f4,0xe5f2,0xc3b5,0x8df4,0xc1ed,0x4143,0x7a10,0xdc3c,0x2c9c,0x1527}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x65c1,0x7f38,0xb596,0xb218,0x1748,0x0401,0x7f70,0xe674,0xf10b,0x9088,0x3212,0x47dd,0xfc3b,0x7da2,0xd931,0x6f0f,0x00ee,0x3356,0x42bc,0x5f1d,0x510f,0x447f,0x38a8}, new char[]{0x517b,0xf162,0xfc58,0xb273,0x5f6e,0x3305,0x7dcb,0x65af,0x7f5b,0x6f6a,0x1700,0x7f11,0xd95f,0x008a,0x90cd,0xe605,0x0474,0x42d5,0x38d8,0x47ad,0x327b,0xb5f8,0x4418}).plainString();
    }
}
