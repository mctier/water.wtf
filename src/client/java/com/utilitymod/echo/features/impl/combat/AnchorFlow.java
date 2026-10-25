package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventStartUseItem;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.event.impl.MouseUpdateEvent;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.impl.combat.anchorflow.AnchorAimSearch;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.handlers.impl.SwapStateManager;
import com.utilitymod.echo.api.MinecraftCompat;
import com.utilitymod.echo.render3.api.FramebufferTarget;
import com.utilitymod.echo.screens.frame.glass.GlassUIConstants;
import com.utilitymod.echo.utils.DrawUtils;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.combat.ExplosionUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.rotation.RotationConvergenceTracker;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import com.utilitymod.echo.utils.EncryptedString;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks an anchor placement near the closest target, places it, and runs the
 * charge/explode sequence. Optional safety-block step drops a glowstone between
 * us and the anchor before charging so we eat less of our own explosion.
 *
 * <p>The interesting bits live in helper classes:
 * <ul>
 *   <li>{@link AnchorAimSearch} -- finds an aim point on the anchor that
 *       raycasts cleanly (works even when an adjacent block occludes the
 *       eye-side face).</li>
 *   <li>{@link RotationConvergenceTracker} -- per-tick rotation delta so we
 *       only fire interactions on a tick where Grim's {@code deltaX > 2}
 *       gate is closed.</li>
 * </ul>
 */
public class AnchorFlow extends Feature {

    private static final CharSequence MODE_HOLD = Concat.of(LT.L1);
    private static final CharSequence MODE_CLICK = Concat.of(LT.L2);
    private static final int VANILLA_PLACE_COOLDOWN_TICKS = 4;
    private static final int MAX_ANCHOR_CHARGES = 4;
    private static final float ROTATION_SETTLE_THRESHOLD = 1.7f;

    private enum AnchorStep { PLACE_SAFETY, CHARGE, EXPLODE }

    public AnchorFlow() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e2,0xa8be,0xbc06,0x226c,0x1c9a,0x82ce,0x0b32,0xd13b,0xb14e,0x962c,0x379e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString()),
                Concat.of(LT.L3),
                Category.COMBAT
        ));
    }

    // -- Settings --

    private final ModeSetting mode = new ModeSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227c,0xa8a4,0xd137,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2271,0xd139,0x37d5,0xa8a7,0xbc0a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0xa8a4,0xd132,0xbc0d}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2271,0xd139,0x37d5,0xa8a7,0xbc0a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be}));
    private final KeybindSetting activateKey = new KeybindSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb98,0x0b28,0x1cd2,0x9608,0x226c,0xd13b,0x11f5,0x82c8,0xa8a8,0x37c8,0xb16e,0xbc00}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}), -1
    ).describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe452,0x6a95,0x820e,0xa43f,0xb6d1,0xc159,0xb509,0xd659,0x5f7d,0x66e2,0xe1c4,0xcd08,0xef50,0x4fad,0x508e,0x4058,0x76a7,0x655e,0xa984,0x6d61,0x6262,0x9622,0x7ed9,0x0b2f,0x4a13,0x05ce,0xcdea,0x1c9f,0xd2a1,0xc97f,0x006a,0xbee0,0xac46,0x0cd7,0xd133,0xc698,0xdad8,0x3378,0x5468,0x8ebd,0x1103,0x3065,0x976b,0xaddc,0x9f92,0x5014,0x4304,0x58ad,0xdbe6,0x34f9,0xe0ba,0xf19e,0xa8ad,0x44d3,0xa1b4,0xf9a5,0xb210,0x8660,0x9b54,0xa56f,0x2e30,0xf415,0xfc3e,0x1580,0xbd1a,0x7fa4,0x93b7,0x90e4,0x11e4,0x1960,0x87b9,0x7269,0x12a1,0x582a,0xe892,0x9e0a,0x2394,0xe73b,0xd92b,0x828d,0x272c,0xde6c,0x0153,0x1dae,0x662b,0x77c2,0x085a,0x2238,0xc5d4,0x1857,0x8a31,0x4cfe,0x3886,0x5cc5,0xf88c,0x0def,0xf005,0xbc1d,0x3b73,0xc2e6,0x37d6,0x2601,0xb166,0x4b28,0xeb82,0x7b90,0x3f0f,0xd3dc,0x3ff8,0xfce2,0x04a8,0x8ae5,0x737b,0xec7f,0x7a0c,0x9bec,0x2be8,0x2aaa,0xb9c2,0x4787,0xf52d}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406,0x2e57,0xa9a4,0x23f5,0xbd3a,0x348b,0x8ed8,0x0829,0x827e,0x1dcf,0x971c,0x116d,0xe8b2,0x6203,0xfc50,0x77a1,0xf1f6,0x4b47,0xc294,0x5ce5,0xd62a,0x507b,0x2bc8,0xa519,0x3f6e,0xb6bf,0x300c,0x8a5d,0x05a2,0x9ff3,0x1940,0x9091,0x6ae6,0xe437,0x7f84,0xf9d5,0x731a,0xcd6b,0x44b8,0xde09,0x585e,0xd3af,0xadfc,0x274d,0xbe92,0x38e3,0xb230,0x0d81,0x87d6,0x0127,0x9b74,0x12c5,0xec0a,0x665b,0xe1a8,0x7bf9,0xf54e,0x4c9f,0xc6ec,0x403d,0xdb82}));
    private final BoolSetting repeat = new BoolSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8ae,0xb17b,0xd128,0x2268,0x37df,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}), true, value -> mode.is(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2274,0xa8a4,0xd132,0xbc0d}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}))).describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf005,0x0026,0xd2be,0x76bb,0xef4a,0x8aa0,0xa1ae,0x7ec8,0x58e4,0xac03,0xf89d,0xc148,0x9e43,0xe0ff,0x3356,0x8660,0xe734,0xd93c,0xc5de,0x6516,0x1806,0x0cc6,0x4781,0xa8a4,0xeb98,0xfcf3,0x7a45,0x9bfe,0x4a18,0x2609,0xcdeb,0x9628,0x3b63,0x5f78,0xbc0d,0xb17b,0x6d70,0xb547,0x11f5,0x379e,0x66eb,0xd132,0x93b1,0xb98a,0xa46c,0x2274,0x2a8b,0x04a2,0x3fe9,0x82c8,0x1cd2,0x0b34,0x7269,0x50cf,0x1589,0xf409}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d}));
    private final BoolSetting autoAim = new BoolSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226c,0xd13b,0xb14e,0x0b35,0xbc06,0x379e,0xa8be,0x82c0}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}), true);
    private final BoolSetting sAim = new BoolSetting(
            dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4D,0x40,0x1C,0xDE,0xAC,0x79,0x74,0xDE,0xCF,0x9C}), false, value -> autoAim.getValue());
    private final IntSetting aimSpeed = new IntSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b39,0x1c96,0x82c8,0x37ed,0xbc49,0xa8a2,0xb17f,0x2275,0xd13b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}), 120, 0, 600, value -> autoAim.getValue());
    private final BoolSetting renderPosition = new BoolSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82fd,0x76ba,0x0b7c,0xeb95,0x9630,0xa8ae,0x37db,0x1c9d,0xfce8,0xbc0d,0xb17d,0xd128,0x655f,0x2276,0x11f9}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4}), true);
    private final ColorSetting fillColor = new ColorSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a2,0x9631,0x82c1,0x379e,0x0b33,0x1c9d,0x2274,0xb14c,0xd13c,0xbc05}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}),
            GlassUIConstants.ACCENT_FOR_RENDERING.getRGB(),
            value -> renderPosition.getValue(),
            true);
    private final ColorSetting outlineColor = new ColorSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226c,0x962c,0x11fc,0xeb8e,0x828d,0x0b39,0xa8be,0xbc05,0x1cb1,0xd135,0x37d7,0x6544,0xb161}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}),
            Color.WHITE.getRGB(),
            value -> renderPosition.getValue(),
            true);
    private final BoolSetting canPlaceLegit = new BoolSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82ce,0x76a0,0x0b3d,0xeb84,0x9663,0xa8aa,0x37ee,0x1c97,0xfcee,0xbc49,0xb163,0xd139,0x6551,0x2276,0x11dc}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4}), true
    ).describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f7d,0x11e7,0x58f9,0xa8a4,0x93a8,0xd979,0xeb89,0x2a9e,0x226b,0x6d74,0xf005,0xbc00,0x82c3,0xcdfb,0x76b1,0x7ece,0xf8d8,0x6553,0x4788,0xb166,0xb98d,0xc5d4,0x9e06,0x04a8,0xd12a,0x1807,0x3fbd,0xc155,0xa422,0xe739,0x50cd,0xfcf5,0x7225,0x1c81,0x37ca,0x4a13,0x335d,0x8aec,0x9663,0x0b33}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d}));
    private final BoolSetting safetyGate = new BoolSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e2,0xa8aa,0xbc0c,0x227e,0x1c9a,0x82ce,0x0b32,0xd129,0xb14e,0x962c,0x379e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}), false
    ).describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x866b,0xfcfe,0x76f4,0x626a,0xe8d5,0x1811,0x6d70,0xef51,0x7a4d,0x3fbd,0x335c,0xeb84,0x5426,0x002e,0x1da7,0xc97b,0x430f,0xfc3e,0xa9c1,0xa1ae,0x2e35,0xb12f,0x6542,0xcdea,0xd12a,0x111f,0x8ebd,0xbc0a,0x4fa1,0xb99b,0x2279,0x9bbb,0x9622,0x11f6,0x0cd5,0x0809,0x4798,0x0b3d,0x828d,0x8af5,0xf413,0x58e8,0xd932,0x2609,0x821d,0xd2bc,0xbd55,0x66eb,0x9e06,0xdac3,0x508e,0xc159,0x159c,0x77c6,0x1c81,0xc5d4,0x93a1,0x2393,0x7e8b,0x722c,0x34f9,0x5f6b,0x3b63,0x2a9e,0xe72e,0xb547,0x4a06,0xa438,0xe0ff,0xf047,0xac03,0x37db,0xf89a,0x977d,0x04a3,0xa8a7}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406,0x2e57,0xa9a4,0x23f5,0xbd3a,0x348b,0x8ed8,0x0829,0x827e,0x1dcf,0x971c,0x116d,0xe8b2,0x6203,0xfc50,0x77a1}));
    private final IntSetting safetyTimeout = new IntSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd129,0x0b7c,0xb176,0x37ca,0x962e,0x227e,0xeb8e,0xfcf3,0x82f9,0xbc0c,0xa8aa,0x1c9b,0x11f5,0x6543}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}), 200, 0, 2000, new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226b,0xa8a6,0xd15a}, new char[]{0xd17a,0xa8cb,0x2218}),
            value -> safetyGate.getValue()
    ).describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ae5,0xfce6,0x1dae,0x2a9a,0x5f28,0xac03,0x5463,0xbd4e,0xeb91,0x9bf5,0xd92d,0x66ef,0x3b7f,0x7ec8,0x76b7,0x93a6,0xf04c,0xfc37,0x626d,0x0025,0xbc0c,0xa8a2,0x6d79,0xc148,0x1857,0xe0b1,0xa46c,0x8216,0xd64b,0x4789,0x655a,0x7781,0xf197,0x4b29,0xb983,0x8669,0x226e,0x5c92,0x50c6,0x110a,0xf45d,0xc961,0x7269,0xe735,0x7a48,0xef19,0xe8db,0xd2a0,0xa9c0,0x084a,0x158f,0x8ef8,0x0b2c,0x23d5,0xf893,0x4a04,0x2e32,0xdad2,0x34e4,0x9e5f,0x4fb2,0xc2ed,0x976e,0x962d,0xb17a,0x1c9d,0xd13d,0x11b0,0xc5dc,0x828d,0x0c94,0xcdff,0x3355,0x2604,0x58e8,0x379e,0xb514,0x04a5,0xa1a9,0x3fee,0x430f,0x5002}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406,0x2e57,0xa9a4,0x23f5,0xbd3a,0x348b,0x8ed8,0x0829,0x827e,0x1dcf,0x971c,0x116d,0xe8b2,0x6203,0xfc50,0x77a1,0xf1f6,0x4b47,0xc294,0x5ce5,0xd62a,0x507b}));
    private final FloatSetting minDamage = new FloatSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a2,0x9626,0x82cc,0x37fa,0x0b31,0x1c95,0x2276,0xb16e,0xd137,0xbc49}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}), 6.0f, 0.0f, 20.0f, 0.5f,
            value -> safetyGate.getValue()
    ).describedBy(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ae8,0xfcf3,0x1dab,0x2adf,0x5f67,0xac4d,0x546e,0xbd57,0xeb87,0x9bbb,0xd93a,0x66ae,0x3b36,0x7ec4,0x76ad,0x93aa,0xf005,0xfc35,0x6264,0x0026,0xbc19,0xa8a0,0x6d76,0xc149,0x1816,0xe0b3,0xa425,0x825e,0xd645,0x4780,0x6553,0x7781,0xf182,0x4b28,0xb9c2,0x8664,0x2271,0x5c9c,0x50c5,0x1100,0xf41c,0xc967,0x7269,0xe732,0x7a49,0xef4d,0xe8d3,0xd2b6,0xa9d7,0x0841,0x1589,0x8ebb,0x0b34,0x23d5,0xf88a,0x4a08,0x2e3e,0xdac5,0x34fe,0x9e06,0x4fb1,0xc2b4,0x977d,0x9630,0xb17b,0x1cd2,0xd129,0x11f1,0xc5d7,0x82c8,0x0cd1,0xcde9,0x3347,0x2657,0x58e1,0x379e,0xb503,0x04b4,0xa1b5,0x3ffb,0x4340,0x500e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406,0x2e57,0xa9a4,0x23f5,0xbd3a,0x348b,0x8ed8,0x0829,0x827e,0x1dcf,0x971c,0x116d,0xe8b2,0x6203,0xfc50,0x77a1,0xf1f6,0x4b47,0xc294,0x5ce5,0xd62a,0x507b}));
    private final RangeSetting charges = new RangeSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b2f,0xd139,0x37d9,0xb16a,0xbc1b,0x2279,0xa8a3}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}), 1, 1, 1, MAX_ANCHOR_CHARGES, 1, new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2270,0xd15a,0xb168,0x0b39,0xbc08,0x37cc,0xa8a8,0x82de}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}));
    private final RangeSetting chargeDelay = new RangeSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb98,0x0b7c,0x1c97,0x962f,0x2279,0xd139,0x11f1,0x82e9,0xa8a3,0x37d9,0xb16a,0xbc1b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}), 1, 1, 0, 20, 1, new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0xb17c,0xd15a,0x2271,0x37d5,0xbc0a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}));
    private final IntSetting burstRow = new IntSetting(
            new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb95,0x0b39,0x1ca1,0x962f,0x2268,0xd13f,0x11ff,0x828d,0xa8b3,0x37d1,0xb16b,0xbc05}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}), 1, 1, 9);
    private final RangeSetting delayExplode = new RangeSetting(
            dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x5B,0x51,0x00,0xD7,0xAD,0x69,0x31,0xBF,0xE2,0x94,0x54,0x22,0xF3}), 1, 1, 0, 20, 1, new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0xb17c,0xd15a,0x2271,0x37d5,0xbc0a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}));

    // -- State --

    private boolean clickQueued;
    private boolean keyWasDown;
    private boolean wasAiming;
    private boolean restoringSwap;
    private boolean holdCycleCompleted;
    private int placeCooldown;
    private AnchorStep anchorStep;
    private BlockPos activeAnchorPos;
    private BlockPos pendingUseKeyAnchorPos;
    private int remainingCharges;
    private int nextAnchorActionTick;

    /** Aim point picked once per sequence; revalidated each call so a freshly
     *  placed safety block can force a re-pick onto an unobstructed face. */
    private Vec3d cachedAnchorAim;

    /** Wall-clock at PLACE_SAFETY entry; used by {@link #safetyTimeout}. */
    private long safetyStepStartMillis;

    private final RotationConvergenceTracker rotation = new RotationConvergenceTracker();

    // -- Lifecycle --

    @Override
    public void onEnable() {
        super.onEnable();
        clickQueued = false;
        keyWasDown = false;
        wasAiming = false;
        restoringSwap = false;
        holdCycleCompleted = false;
        placeCooldown = 0;
        rotation.reset();
        resetAnchorSequence();
    }

    @Override
    public void onDisable() {
        clickQueued = false;
        keyWasDown = false;
        wasAiming = false;
        restoringSwap = false;
        holdCycleCompleted = false;
        placeCooldown = 0;
        resetAnchorSequence();
        SwapStateManager.cancel(this, true);
        if (RotationUtils.isControlledBy(this)) {
            RotationUtils.stopTracking();
        }
        super.onDisable();
    }

    // -- Event handlers --

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onStartUseItem(EventStartUseItem.Pre event) {
        if (mc.crosshairTarget != null && com.utilitymod.echo.utils.combat.TargetUtils.friendNear(mc.crosshairTarget.getPos(), 6.5)) return;
        if (!shouldHandleSameUseKey()) return;
        if (event.getPlayer() != mc.player) return;
        if (event.getHand() != Hand.MAIN_HAND) return;
        if (MinecraftCompat.getScreen() != null || activateKey.isListening()) return;
        if (mode.is(MODE_HOLD) && !InputHandler.isBindDown(activateKey.getKey()) && !isAnchorSequenceActive()) return;

        boolean startFromAnchor = !isAnchorSequenceActive() && event.getStack().isOf(Items.RESPAWN_ANCHOR);
        if (startFromAnchor) {
            pendingUseKeyAnchorPos = anchorPosOf(event.getHitResult());
        }

        if (!startFromAnchor && !isAnchorSequenceActive() && !SwapStateManager.isOwnerActive(this) && !restoringSwap) {
            return;
        }

        if (!mode.is(MODE_HOLD) && startFromAnchor) {
            clickQueued = true;
            if (!isAnchorSequenceActive()) {
                restoringSwap = false;
                placeCooldown = 0;
            }
        }

        event.cancel();
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onRender3D(EventRender3D event) {
        if (isNull() || !renderPosition.getValue()) return;

        boolean renderFill = fillColor.getAlpha() > 0;
        boolean renderOutline = outlineColor.getAlpha() > 0;
        if (!renderFill && !renderOutline) return;

        BlockUtils.PlacementHit bestHit = findBestPlacementHit(event.getTickDelta());
        if (bestHit == null) return;

        BlockPos bestPos = bestHit.placementPos();
        FramebufferTarget draw = event.getDraw3D().getMinecraftTarget();
        if (renderFill) {
            draw.box(bestPos.getX(), bestPos.getY(), bestPos.getZ(), 1.0, 1.0, 1.0, fillColor.getARGB());
        }
        if (renderOutline) {
            DrawUtils.drawBoxOutline(draw, new Box(bestPos), outlineColor.getARGB());
        }
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onTick(EventTick event) {
        if (mc.crosshairTarget != null && com.utilitymod.echo.utils.combat.TargetUtils.friendNear(mc.crosshairTarget.getPos(), 6.5)) return;
        rotation.update();

        if (placeCooldown > 0) placeCooldown--;
        if (!SwapStateManager.isOwnerActive(this)) restoringSwap = false;

        if (isNull() || MinecraftCompat.getScreen() != null || activateKey.isListening()) {
            clickQueued = false;
            resetAnchorSequence();
            handleInactive();
            keyWasDown = false;
            return;
        }

        boolean sameUseKey = shouldHandleSameUseKey();
        boolean keyDown = InputHandler.isBindDown(activateKey.getKey());
        boolean holdMode = mode.is(MODE_HOLD);
        if (holdMode && !keyDown) holdCycleCompleted = false;

        if (!holdMode && keyDown && !keyWasDown && (!sameUseKey || isHoldingRespawnAnchor())) {
            clickQueued = true;
            if (!isAnchorSequenceActive()) {
                restoringSwap = false;
                placeCooldown = 0;
            }
        }

        boolean active = holdMode ? keyDown : clickQueued || isAnchorSequenceActive();
        if (!active) {
            resetAnchorSequence();
            handleInactive();
            keyWasDown = keyDown;
            return;
        }

        if (holdMode && !repeat.getValue() && holdCycleCompleted && !isAnchorSequenceActive()) {
            handleInactive();
            keyWasDown = keyDown;
            return;
        }

        if (sameUseKey && !isAnchorSequenceActive() && !isHoldingRespawnAnchor()) {
            if (!holdMode) clickQueued = false;
            if (!SwapStateManager.isOwnerActive(this)) handleInactive();
            keyWasDown = keyDown;
            return;
        }

        if (isAnchorSequenceActive()) {
            continueAnchorSequence();
            keyWasDown = keyDown;
            return;
        }

        BlockPos existingAnchor = consumePendingUseKeyAnchorPos();
        if (existingAnchor == null) {
            BlockHitResult hit = currentBlockHit();
            if (hit != null && isRespawnAnchor(hit.getBlockPos())) existingAnchor = hit.getBlockPos();
        }

        if (existingAnchor != null) {
            if (!isAnchorWithinInteractionRange(existingAnchor)) {
                if (!holdMode) clickQueued = false;
                handleInactive();
                keyWasDown = keyDown;
                return;
            }
            beginAnchorSequence(existingAnchor, rollChargeCount());
            keyWasDown = keyDown;
            return;
        }

        keyWasDown = keyDown;
        runFreshAnchorPlacement(holdMode, sameUseKey, keyDown);
    }

    /** Handles the "no active sequence yet, no anchor under cursor" branch -- find a spot, swap, place. */
    private void runFreshAnchorPlacement(boolean holdMode, boolean useHeldAnchorSlot, boolean keyDown) {
        // Same-use-key flow: the user drives placement by looking at a block.
        // Auto-aim is reserved for charging/exploding, so we force the manual
        // path here even when Auto Aim is enabled.
        boolean useAutoAim = autoAim.getValue() && !useHeldAnchorSlot;
        BlockUtils.PlacementHit autoAimHit = null;
        BlockHitResult manualHit = null;
        boolean useAutoAimPlacement = false;

        float partialTick = mc.getRenderTickCounter().getTickProgress(false);

        if (useAutoAim) {
            autoAimHit = findBestPlacementHit(partialTick);
            if (autoAimHit != null) {
                useAutoAimPlacement = true;
            } else {
                manualHit = currentBlockHit();
            }
            if (manualHit == null && !useAutoAimPlacement) {
                if (!holdMode) clickQueued = false;
                handleInactive();
                return;
            }
        } else {
            manualHit = currentBlockHit();
            if (manualHit == null) {
                if (!holdMode) clickQueued = false;
                handleInactive();
                return;
            }
        }

        int anchorSlot = useHeldAnchorSlot
                ? mc.player.getInventory().getSelectedSlot()
                : InventoryUtils.findItemWithPredicateInHotbar(stack -> stack.isOf(Items.RESPAWN_ANCHOR));
        if (anchorSlot == -1) {
            clickQueued = false;
            handleInactive();
            return;
        }

        restoringSwap = false;
        if (!useHeldAnchorSlot && !SwapStateManager.swapToIfNeeded(this, anchorSlot, false, -1, false)) return;
        if (placeCooldown > 0) return;
        if (!mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) return;

        BlockHitResult hitResult;
        BlockPos placedAnchorPos;
        if (useAutoAimPlacement) {
            if (autoAimHit == null || !isReadyToPlace(autoAimHit, partialTick)) return;
            hitResult = autoAimHit.hitResult();
            placedAnchorPos = autoAimHit.placementPos();
        } else {
            hitResult = manualHit;
            placedAnchorPos = placedAnchorPosOf(hitResult);
            if (!isManualAnchorPlacementValid(placedAnchorPos)) {
                if (!holdMode) clickQueued = false;
                handleInactive();
                return;
            }
        }

        ActionResult result = BlockUtils.interactWithBlock(hitResult, true);
        placeCooldown = VANILLA_PLACE_COOLDOWN_TICKS;
        if (result.isAccepted()) rotation.markInteraction();

        if (result.isAccepted() && placedAnchorPos != null) {
            beginAnchorSequence(placedAnchorPos, rollChargeCount());
        } else if (!holdMode) {
            finishAnchorSequence();
        }

        if (holdMode && !result.isAccepted()) {
            placeCooldown = Math.max(placeCooldown, 1);
        }
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onMouseUpdate(MouseUpdateEvent event) {
        if (!autoAim.getValue() || isNull() || MinecraftCompat.getScreen() != null || !isActionActive()
                || (shouldHandleSameUseKey() && !isAnchorSequenceActive())) {
            disengageRotations();
            return;
        }

        Vec3d aimPoint = findActiveAimPoint();
        if (aimPoint == null) {
            disengageRotations();
            return;
        }

        boolean rotated = RotationUtils.aim(this)
                .priority(EventSubscribe.Priority.HIGH)
                .silent(sAim.getValue())
                .speed(aimSpeed.getValue())
                .aimType(RotationUtils.AimType.REGULAR)
                .to(aimPoint);

        if (!rotated) {
            disengageRotations();
            return;
        }
        wasAiming = true;
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (!autoAim.getValue() || !wasAiming || !sAim.getValue()) return;
        if (!RotationUtils.isControlledBy(this) || !RotationUtils.hasSilentRotation()) return;

        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    // -- Sequence state machine --

    private void beginAnchorSequence(BlockPos anchorPos, int chargeCount) {
        activeAnchorPos = anchorPos;
        remainingCharges = Math.max(0, chargeCount - getAnchorCharge(anchorPos));
        anchorStep = safetyGate.getValue() ? AnchorStep.PLACE_SAFETY : AnchorStep.CHARGE;
        nextAnchorActionTick = mc.player.age + (anchorStep == AnchorStep.CHARGE ? rollTickDelay(chargeDelay) : 0);
        restoringSwap = false;
        cachedAnchorAim = AnchorAimSearch.findClearAimPoint(anchorPos, partialTick());
        safetyStepStartMillis = System.currentTimeMillis();
    }

    private void continueAnchorSequence() {
        if (!isRespawnAnchor(activeAnchorPos) || !isAnchorWithinInteractionRange(activeAnchorPos)) {
            finishAnchorSequence();
            return;
        }
        if (mc.player.age < nextAnchorActionTick) return;

        switch (anchorStep) {
            case PLACE_SAFETY -> placeSafetyBlock();
            case CHARGE -> chargeActiveAnchor();
            case EXPLODE -> explodeActiveAnchor();
        }
    }

    private void finishAnchorSequence() {
        resetAnchorSequence();
        if (mode.is(MODE_HOLD) && InputHandler.isBindDown(activateKey.getKey())) {
            if (!repeat.getValue()) {
                holdCycleCompleted = true;
                scheduleSwapBack(1);
                disengageRotations();
            } else if (shouldHandleSameUseKey()) {
                scheduleSwapBack(1);
            }
            return;
        }
        holdCycleCompleted = false;
        clickQueued = false;
        scheduleSwapBack(1);
    }

    private void resetAnchorSequence() {
        anchorStep = null;
        activeAnchorPos = null;
        pendingUseKeyAnchorPos = null;
        remainingCharges = 0;
        nextAnchorActionTick = 0;
        cachedAnchorAim = null;
    }

    private boolean isAnchorSequenceActive() {
        return anchorStep != null && activeAnchorPos != null;
    }

    private void advanceToChargeStep() {
        anchorStep = AnchorStep.CHARGE;
        nextAnchorActionTick = mc.player.age + rollTickDelay(chargeDelay);
    }

    private void beginExplodeStepOrFinish() {
        if (getAnchorCharge(activeAnchorPos) <= 0) {
            finishAnchorSequence();
            return;
        }
        anchorStep = AnchorStep.EXPLODE;
        nextAnchorActionTick = mc.player.age + rollTickDelay(delayExplode);
    }

    // -- Step: PLACE_SAFETY --

    private void placeSafetyBlock() {
        // Build the safety candidate list ONCE per tick instead of three times.
        List<BlockPos> ranked = rankedSafetyCandidates();

        if (!shouldPlaceSafety(ranked)) {
            advanceToChargeStep();
            return;
        }

        int timeoutMs = safetyTimeout.getValue();
        if (timeoutMs > 0 && System.currentTimeMillis() - safetyStepStartMillis >= timeoutMs) {
            advanceToChargeStep();
            return;
        }

        boolean useAutoAim = autoAim.getValue();
        BlockHitResult hitResult;
        float partialTick = partialTick();

        if (useAutoAim) {
            BlockUtils.PlacementHit placementHit = pickSafetyPlacement(ranked);
            if (placementHit == null) return;
            if (!isReadyToPlace(placementHit, partialTick)) return;
            hitResult = placementHit.hitResult();
        } else {
            BlockHitResult playerHit = currentBlockHit();
            if (playerHit == null) return;
            BlockPos lookedAt = playerHit.getBlockPos();
            if (lookedAt.equals(activeAnchorPos)) return;

            BlockPos placement = lookedAt.offset(playerHit.getSide());
            if (placement.equals(activeAnchorPos)) return;
            if (!BlockUtils.isValidPlacement(placement)) return;
            if (BlockUtils.collidesWithPlayer(placement)) return;
            if (BlockUtils.collidesWithBlockingEntity(placement)) return;
            hitResult = playerHit;
        }

        int safetySlot = findSafetyBlockSlot();
        if (safetySlot == -1) {
            advanceToChargeStep();
            return;
        }

        if (!SwapStateManager.swapToIfNeeded(this, safetySlot, false, -1, false)) return;
        if (placeCooldown > 0) return;
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;

        ActionResult result = BlockUtils.interactWithBlock(hitResult, true);
        placeCooldown = VANILLA_PLACE_COOLDOWN_TICKS;
        if (result.isAccepted()) {
            rotation.markInteraction();
            advanceToChargeStep();
        }
    }

    private boolean shouldPlaceSafety(List<BlockPos> ranked) {
        if (!safetyGate.getValue()) return false;
        if (mc.player == null || mc.world == null || activeAnchorPos == null) return false;

        // Below the damage threshold the explosion isn't worth a glowstone.
        float threshold = minDamage.getValue();
        if (threshold > 0.0f && ExplosionUtils.getAnchorDamageTo(mc.player, activeAnchorPos) < threshold) {
            return false;
        }

        // If the closest-to-anchor neighbor is already a solid block, we're already covered.
        BlockPos ideal = ranked.isEmpty() ? null : ranked.get(0);
        return ideal == null || !hasExistingSafetyBlock(ideal);
    }

    /**
     * 8 horizontal neighbors of the player's feet, restricted to the half-plane
     * toward the anchor (so the safety can never end up "to the right" or
     * behind us), sorted by distance to the anchor center -- the spot that
     * sits most directly between us and the anchor comes first.
     */
    private List<BlockPos> rankedSafetyCandidates() {
        List<BlockPos> ordered = new ArrayList<>(8);
        if (mc.player == null || activeAnchorPos == null) return ordered;

        float partialTick = partialTick();
        BlockPos feet = BlockPos.ofFloored(mc.player.getLerpedPos(partialTick));
        Vec3d feetCenter = Vec3d.ofCenter(feet);
        Vec3d anchorCenter = Vec3d.ofCenter(activeAnchorPos);
        Vec3d toAnchor = anchorCenter.subtract(feetCenter);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos pos = feet.add(dx, 0, dz);
                Vec3d toCand = Vec3d.ofCenter(pos).subtract(feetCenter);
                if (toCand.x * toAnchor.x + toCand.z * toAnchor.z <= 0) continue; // behind / perpendicular
                ordered.add(pos);
            }
        }
        ordered.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(anchorCenter)));
        return ordered;
    }

    private BlockUtils.PlacementHit pickSafetyPlacement(List<BlockPos> ranked) {
        for (BlockPos pos : ranked) {
            BlockUtils.PlacementHit hit = trySafetyPlacementAt(pos);
            if (hit != null) return hit;
        }
        return null;
    }

    private BlockUtils.PlacementHit trySafetyPlacementAt(BlockPos pos) {
        if (pos == null || mc.world == null || mc.player == null) return null;
        if (pos.equals(activeAnchorPos)) return null;
        if (!BlockUtils.isValidPlacement(pos)) return null;
        if (!BlockUtils.hasSupportingFace(pos)) return null;
        if (BlockUtils.collidesWithPlayer(pos)) return null;
        if (BlockUtils.collidesWithBlockingEntity(pos)) return null;

        BlockUtils.PlacementHit hit = BlockUtils.findPlacementHit(pos, canPlaceLegit.getValue());
        if (hit == null) return null;

        Vec3d eye = mc.player.getCameraPosVec(partialTick());
        double range = mc.player.getBlockInteractionRange();
        if (eye.squaredDistanceTo(hit.hitResult().getPos()) > range * range) return null;
        return hit;
    }

    private boolean hasExistingSafetyBlock(BlockPos pos) {
        if (mc.world == null || pos == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && !state.isReplaceable();
    }

    private int findSafetyBlockSlot() {
        int slot = InventoryUtils.findItemWithPredicateInHotbar(stack -> stack.isOf(Items.GLOWSTONE));
        if (slot != -1) return slot;
        return InventoryUtils.findItemWithPredicateInHotbar(stack ->
                stack.getItem() instanceof BlockItem && !stack.isOf(Items.RESPAWN_ANCHOR));
    }

    // -- Step: CHARGE --

    private void chargeActiveAnchor() {
        int currentCharge = getAnchorCharge(activeAnchorPos);
        if (remainingCharges <= 0 || currentCharge >= MAX_ANCHOR_CHARGES) {
            beginExplodeStepOrFinish();
            return;
        }

        int glowstoneSlot = InventoryUtils.findItemWithPredicateInHotbar(stack -> stack.isOf(Items.GLOWSTONE));
        if (glowstoneSlot == -1) {
            remainingCharges = 0;
            beginExplodeStepOrFinish();
            return;
        }

        if (!SwapStateManager.swapToIfNeeded(this, glowstoneSlot, false, -1, false)) return;
        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) return;

        BlockHitResult hitResult = getReadyAnchorHit(activeAnchorPos);
        if (hitResult == null) return;

        ActionResult result = BlockUtils.interactWithBlock(hitResult, true);
        if (!result.isAccepted()) return;

        rotation.markInteraction();
        remainingCharges--;
        if (remainingCharges <= 0) {
            anchorStep = AnchorStep.EXPLODE;
            nextAnchorActionTick = mc.player.age + rollTickDelay(delayExplode);
            return;
        }
        nextAnchorActionTick = mc.player.age + rollTickDelay(chargeDelay);
    }

    // -- Step: EXPLODE --

    private void explodeActiveAnchor() {
        int currentCharge = getAnchorCharge(activeAnchorPos);
        if (currentCharge <= 0) {
            finishAnchorSequence();
            return;
        }

        int targetSlot = burstRow.getValue() - 1;
        if (!SwapStateManager.swapToIfNeeded(this, targetSlot, false, -1, false)) return;

        // Glowstone in the explode slot would just charge again; bail unless the anchor is already maxed.
        ItemStack explodeStack = mc.player.getInventory().getStack(targetSlot);
        if (explodeStack.isOf(Items.GLOWSTONE) && currentCharge < MAX_ANCHOR_CHARGES) {
            finishAnchorSequence();
            return;
        }

        BlockHitResult hitResult = getReadyAnchorHit(activeAnchorPos);
        if (hitResult == null) return;

        ActionResult result = BlockUtils.interactWithBlock(hitResult, true);
        if (!result.isAccepted()) return;

        rotation.markInteraction();
        placeCooldown = VANILLA_PLACE_COOLDOWN_TICKS;
        finishAnchorSequence();
    }

    // -- Aim picking --

    private Vec3d findActiveAimPoint() {
        if (isAnchorSequenceActive()) {
            if (anchorStep == AnchorStep.PLACE_SAFETY) {
                List<BlockPos> ranked = rankedSafetyCandidates();
                if (shouldPlaceSafety(ranked)) {
                    BlockUtils.PlacementHit safetyHit = pickSafetyPlacement(ranked);
                    if (safetyHit != null) return safetyHit.hitResult().getPos();
                }
            }
            return getAnchorAimPoint(activeAnchorPos);
        }

        BlockHitResult anchorHit = currentBlockHit();
        if (anchorHit != null && isRespawnAnchor(anchorHit.getBlockPos())) {
            return anchorHit.getPos();
        }

        float partialTick = partialTick();
        BlockUtils.PlacementHit placementHit = findBestPlacementHit(partialTick);
        if (placementHit != null) return placementHit.hitResult().getPos();

        BlockHitResult manualHit = currentBlockHit();
        return manualHit != null ? manualHit.getPos() : null;
    }

    private Vec3d getAnchorAimPoint(BlockPos anchorPos) {
        if (anchorPos == null) return null;
        if (mc.player == null) return Vec3d.ofCenter(anchorPos);

        float partialTick = partialTick();
        // Reuse the cached aim if it still raycasts cleanly. A block placed
        // since the sequence started (e.g. our safety glowstone) can occlude
        // the original corner -- in that case re-pick.
        if (cachedAnchorAim != null && anchorPos.equals(activeAnchorPos)) {
            Vec3d eye = mc.player.getCameraPosVec(partialTick);
            if (AnchorAimSearch.canHitAnchor(eye, cachedAnchorAim, anchorPos)) return cachedAnchorAim;
            cachedAnchorAim = AnchorAimSearch.findClearAimPoint(anchorPos, partialTick);
            return cachedAnchorAim != null ? cachedAnchorAim : Vec3d.ofCenter(anchorPos);
        }
        Vec3d fresh = AnchorAimSearch.findClearAimPoint(anchorPos, partialTick);
        return fresh != null ? fresh : Vec3d.ofCenter(anchorPos);
    }

    private boolean isReadyToPlace(BlockUtils.PlacementHit placementHit, float partialTick) {
        float yaw = effectiveYaw();
        float pitch = effectivePitch();

        // Grim's DuplicateRotPlace compares pitch delta against the previous
        // place packet. Falling changes the needed pitch every tick, so allow
        // those non-duplicate deltas instead of waiting until we land.
        if (!rotation.isDuplicateRotPlaceSafe(ROTATION_SETTLE_THRESHOLD)) return false;

        if (!canPlaceLegit.getValue()) {
            return RotationUtils.rayIntersectsBlock(yaw, pitch, placementHit.hitResult().getBlockPos(), partialTick);
        }

        BlockHitResult raycast = RotationUtils.raycastFromRotation(yaw, pitch, partialTick);
        if (raycast == null || raycast.getType() != HitResult.Type.BLOCK) return false;
        if (!raycast.getBlockPos().equals(placementHit.hitResult().getBlockPos())) return false;
        return raycast.getSide() == placementHit.hitResult().getSide();
    }

    private BlockHitResult getReadyAnchorHit(BlockPos anchorPos) {
        if (!isAnchorWithinInteractionRange(anchorPos)) return null;

        // Grim's DuplicateRotPlace compares pitch delta against the previous
        // place packet. Falling changes the needed pitch every tick, so allow
        // those non-duplicate deltas instead of waiting until we land.
        if (!rotation.isDuplicateRotPlaceSafe(ROTATION_SETTLE_THRESHOLD)) return null;

        float yaw = effectiveYaw();
        float pitch = effectivePitch();
        float partialTick = partialTick();

        if (!canPlaceLegit.getValue()) {
            if (!RotationUtils.rayIntersectsBlock(yaw, pitch, anchorPos, partialTick)) return null;
            return new BlockHitResult(getAnchorAimPoint(anchorPos), Direction.UP, anchorPos, false);
        }

        BlockHitResult currentHit = currentBlockHit();
        if (currentHit != null && currentHit.getBlockPos().equals(anchorPos)) return currentHit;

        BlockHitResult raycast = RotationUtils.raycastFromRotation(yaw, pitch, partialTick);
        if (raycast == null || raycast.getType() != HitResult.Type.BLOCK) return null;
        if (!raycast.getBlockPos().equals(anchorPos)) return null;
        return raycast;
    }

    // -- Best-placement search (anchor placement candidate) --

    private BlockUtils.PlacementHit findBestPlacementHit(float partialTick) {
        Vec3d playerEye = mc.player.getCameraPosVec(partialTick);
        LivingEntity target = TargetUtils.findClosestResolvedTarget(playerEye, partialTick);
        if (target == null) return null;

        double blockRange = mc.player.getBlockInteractionRange();
        double blockRangeSq = blockRange * blockRange;

        Vec3d targetPos = target.getLerpedPos(partialTick);
        Vec3d offset = targetPos.subtract(target.getEntityPos());
        Box targetBox = target.getBoundingBox().offset(offset);
        Vec3d targetFeet = new Vec3d(targetPos.x, targetBox.minY, targetPos.z);

        int floorX = MathHelper.floor(targetFeet.x);
        int floorY = MathHelper.floor(targetFeet.y);
        int floorZ = MathHelper.floor(targetFeet.z);

        BlockUtils.PlacementHit bestHit = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = new BlockPos(floorX + dx, floorY + dy, floorZ + dz);

                    if (!BlockUtils.isValidPlacement(pos)) continue;
                    if (!BlockUtils.hasSupportingFace(pos)) continue;

                    BlockUtils.PlacementHit hit = BlockUtils.findPlacementHit(pos, canPlaceLegit.getValue());
                    if (hit == null) continue;
                    if (BlockUtils.collidesWithPlayer(pos)) continue;
                    if (BlockUtils.hasBlockingEntity(new Box(pos))) continue;
                    if (playerEye.squaredDistanceTo(hit.hitResult().getPos()) > blockRangeSq) continue;

                    Vec3d blockCenter = Vec3d.ofCenter(pos);
                    float exposure = ExplosionUtils.getSeenPercent(blockCenter, targetBox, target);
                    double feetDistSq = targetFeet.squaredDistanceTo(blockCenter);
                    // Prefer high-exposure hits; break ties by proximity to feet.
                    double score = (1.0 - exposure) * 100.0 + feetDistSq * 0.01;

                    if (score < bestScore) {
                        bestScore = score;
                        bestHit = hit;
                    }
                }
            }
        }
        return bestHit;
    }

    // -- Misc helpers --

    private float partialTick() {
        return mc.getRenderTickCounter().getTickProgress(false);
    }

    private float effectiveYaw() {
        return RotationHandler.isHasSilentRotation() ? RotationHandler.getServerYaw() : mc.player.getYaw();
    }

    private float effectivePitch() {
        return RotationHandler.isHasSilentRotation() ? RotationHandler.getServerPitch() : mc.player.getPitch();
    }

    private boolean isActionActive() {
        if (mode.is(MODE_HOLD)) {
            if (!InputHandler.isBindDown(activateKey.getKey())) return false;
            return repeat.getValue() || !holdCycleCompleted || isAnchorSequenceActive();
        }
        return clickQueued || isAnchorSequenceActive();
    }

    private boolean shouldHandleSameUseKey() {
        if (mc == null || mc.options == null || mc.options.useKey == null) return false;
        return activateKey.getKey() != -1 && activateKey.getKey() == bindCodeOf(mc.options.useKey);
    }

    private int bindCodeOf(KeyBinding keyMapping) {
        if (keyMapping == null) return -1;
        InputUtil.Key key = com.utilitymod.echo.api.Accessors.getKey(keyMapping);
        if (key == null) return -1;
        if (key.getCategory() == InputUtil.Type.MOUSE) return 0x80000000 | key.getCode();
        return key.getCode();
    }

    private boolean isHoldingRespawnAnchor() {
        return mc.player != null && mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR);
    }

    private BlockHitResult currentBlockHit() {
        if (!(mc.crosshairTarget instanceof BlockHitResult blockHit)) return null;
        if (blockHit.getType() != HitResult.Type.BLOCK) return null;
        return blockHit;
    }

    private BlockPos consumePendingUseKeyAnchorPos() {
        BlockPos pos = pendingUseKeyAnchorPos;
        pendingUseKeyAnchorPos = null;
        if (pos == null) return null;
        return isRespawnAnchor(pos) ? pos : null;
    }

    private BlockPos anchorPosOf(HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult blockHit)) return null;
        if (blockHit.getType() != HitResult.Type.BLOCK) return null;
        return isRespawnAnchor(blockHit.getBlockPos()) ? blockHit.getBlockPos() : null;
    }

    private BlockPos placedAnchorPosOf(BlockHitResult hitResult) {
        return hitResult == null ? null : hitResult.getBlockPos().offset(hitResult.getSide());
    }

    private boolean isManualAnchorPlacementValid(BlockPos pos) {
        if (pos == null) return false;
        if (!BlockUtils.isValidPlacement(pos)) return false;
        if (BlockUtils.collidesWithPlayer(pos)) return false;
        return !BlockUtils.collidesWithBlockingEntity(pos);
    }

    private boolean isRespawnAnchor(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        return BlockUtils.isBlockAtPosition(pos, Blocks.RESPAWN_ANCHOR);
    }

    private int getAnchorCharge(BlockPos pos) {
        if (!isRespawnAnchor(pos)) return 0;
        return mc.world.getBlockState(pos).get(RespawnAnchorBlock.CHARGES);
    }

    private boolean isAnchorWithinInteractionRange(BlockPos anchorPos) {
        return mc.player != null && anchorPos != null && mc.player.canInteractWithBlockAt(anchorPos, 1.0);
    }

    private void handleInactive() {
        resetAnchorSequence();
        if (SwapStateManager.isOwnerActive(this) && !restoringSwap) scheduleSwapBack(1);
        disengageRotations();
    }

    private void scheduleSwapBack(int delayTicks) {
        if (!SwapStateManager.isOwnerActive(this)) return;
        int activeSlot = SwapStateManager.getActiveTargetSlot(this);
        if (activeSlot == -1) {
            SwapStateManager.cancel(this, true);
            restoringSwap = false;
            return;
        }
        SwapStateManager.swapToIfNeeded(this, activeSlot, false, delayTicks, true);
        restoringSwap = true;
    }

    private void disengageRotations() {
        if (!RotationUtils.isControlledBy(this)) {
            wasAiming = false;
            return;
        }
        if (RotationHandler.isHasSilentRotation() || sAim.getValue()) {
            if (RotationUtils.cleanup(this, true, aimSpeed.getValue(), aimSpeed.getValue(), RotationUtils.AimType.REGULAR, false)) return;
        } else {
            RotationUtils.stopTracking();
        }
        wasAiming = false;
    }

    // Random rolls -- delegate to RangeSetting.getRandom() and clamp to int.
    private int rollChargeCount() {
        return MathHelper.clamp(Math.round(charges.getRandom()), 1, MAX_ANCHOR_CHARGES);
    }

    private int rollTickDelay(RangeSetting setting) {
        return Math.max(0, Math.round(setting.getRandom()));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb029,0x0afc,0x76d5,0xda4e}, new char[]{0xda06,0x76ba,0xb045,0x0a98}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x89c3,0xea8f,0x291a,0x39c1,0xa79f}, new char[]{0xeacc,0xa7f3,0x89aa,0x39a2,0x2971}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfa46,0xb3c8,0xe2b3,0x3b66,0x5ac3,0x123c,0xf664,0x725f,0x9ac8,0xe1c1,0x91df,0x741d,0x5ed1,0x5a27,0xbd05,0x80cd,0x2265,0xfe49,0x608f,0x4066,0xe168,0x14f4,0x6325,0x2fe3,0xbf21,0xe0d7,0x547e,0x4544,0xfa78,0xa060,0x1a23,0xbc98,0x6d93,0xf70d,0x1290,0x1ccd,0xd18e,0xe534,0x3598,0xf4ef,0x224f,0x54d8,0x55a4,0x16f5,0x842e,0xab78,0x5aa6,0x98e1,0x1ecb,0xaa64,0x8d9d,0x1078,0x14c5,0x2ad1,0x4ad2}, new char[]{0x1e9b,0x2223,0x1019,0x14a6,0xab1d,0x845d,0x4046,0xbd64,0x54b6,0x55c7,0xe2db,0x5ebe,0xd1fc,0x540d,0xfe69,0x3b07,0xf49b,0x2245,0x5ad2,0x60e7,0x2ab4,0x743d,0xe107,0x6de3,0x1a57,0x80a4,0x988c,0x4ab3,0x2f8f,0x6305,0xb3b8,0x12ff,0x1686,0x7236,0x4530,0xa009,0x5a48,0x9aa6,0x35b8,0xbf4f,0xe0b2,0xf76c,0x5ab1,0xbcb8,0xaa1d,0x149b,0xe1b4,0x1cbf,0x121c,0xf610,0x91be,0xfa34,0xfa1f,0xe551,0x8de9}).plainString();
    }
}
