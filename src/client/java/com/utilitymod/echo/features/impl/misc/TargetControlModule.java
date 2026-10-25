package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventManager;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.player.PlayerUtils;
import com.utilitymod.echo.utils.strings.Concat;
import com.utilitymod.echo.utils.world.WorldUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.hit.EntityHitResult;
import org.jspecify.annotations.Nullable;

public class TargetControlModule extends Feature {

    public TargetControlModule() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b2f,0xd12e,0x37db,0xb17b,0xbc0e,0x226a,0xa8aa}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c}).toString()),
                Concat.of(LT.L1),
                Category.INTERNALS
        ));

        botCheckList.setDependency(obj -> botCheck.getValue());
        botCheckList.setOption(Concat.of(LT.L2), true);
        botCheckList.setOption(Concat.of(LT.L3), true);
        botCheckList.setOption(Concat.of(LT.L4), true);
        botCheckList.setOption(Concat.of(LT.L5), true);
        botCheckList.setOption(Concat.of(LT.L6), false);
        lastAttacked.setDependency(obj -> keepTarget.getValue());
        keepTarget.onChanged(() -> {
            if (!keepTarget.getValue()) {
                TargetUtils.clearTrackedTargets();
            }
        });

        EventManager.register(this);
    }

    @Override
    public void toggle() {
        
    }

    @Override
    public void setEnabled(boolean enabled) {

    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public final MultiModeSetting entityTypes = new MultiModeSetting(
            Concat.of(LT.L7),
            Concat.of(LT.L8),
            Concat.of(LT.L9),
            Concat.of(LT.L10),
            Concat.of(LT.L11),
            Concat.of(LT.L12),
            Concat.of(LT.L13)
    );

    public final BoolSetting ignoreTeams = new BoolSetting(Concat.of(LT.L14), true);
    public final BoolSetting ignoreFriends = new BoolSetting(Concat.of(LT.L15), true);
    public final BoolSetting botCheck = new BoolSetting(dev.potato.spud.api.ProtectedText.fromPlain(LT.L16), true);
    public final MultiModeSetting botCheckList = new MultiModeSetting(
            dev.potato.spud.api.ProtectedText.fromPlain(LT.L17),
            Concat.of(LT.L2),
            Concat.of(LT.L3),
            Concat.of(LT.L4),
            Concat.of(LT.L5),
            Concat.of(LT.L6)
    );
    public final BoolSetting keepTarget = new BoolSetting(Concat.of(LT.L18), true);
    public final BoolSetting lastAttacked = new BoolSetting(Concat.of(LT.L19), false);
    public final FloatSetting keepTargetRange = new FloatSetting(Concat.of(LT.L20), 16.0f, 1.0f, 50.0f, 0.5f);
    public final KeybindSetting manualTargetSelect = new KeybindSetting(Concat.of(LT.L21), -1);

    @Nullable
    public static LivingEntity pendingShieldBreakTarget = null;

    private boolean manualTargetSelectWasDown;

    @SuppressWarnings("unused")
    @EventSubscribe
    public void onHandleInput(EventHandleInput.Early event) {
        if (isNull()) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;

        boolean keyDown = InputHandler.isBindDown(manualTargetSelect.getKey());
        if (!keyDown) {
            manualTargetSelectWasDown = false;
            return;
        }

        if (manualTargetSelectWasDown) return;
        manualTargetSelectWasDown = true;

        if (!keepTarget.getValue()) return;

        LivingEntity target = PlayerUtils.getManualSelectTarget();
        if (target == null) return;
        if (!TargetUtils.isTargetAllowed(target)) return;

        TargetUtils.setKeptTarget(target);
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    public void onDetectShieldBreak(EventOnAttackEntity.Pre event) {
        if (isNull()) return;

        if (!(mc.crosshairTarget instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof PlayerEntity target)) return;

        if (!target.isUsingItem()) return;
        if (!(target.getActiveItem().getItem() instanceof ShieldItem)) return;

        if (WorldUtils.isShieldFacingAway(target)) return;
        if (!TargetUtils.isTargetAllowed(target)) return;


        ItemStack weapon = mc.player.getMainHandStack();
        if (!(weapon.getItem() instanceof AxeItem)) return;


        pendingShieldBreakTarget = target;
    }



    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb93a,0xc579,0xcd47,0x9028,0xb96b,0x05f9,0x47e8,0xdb01,0xd3a9,0xa759,0x1477,0x31df,0x28aa,0xbf65,0xd4d0,0xd7c6,0xdbb8,0x2a37,0xa57e,0x1dcd,0xa4fc,0xff91,0x2c33,0x610f,0x2113,0xbd24,0xf941,0x69f8,0x828a,0x5072,0xc154,0x6402,0x43db,0x1137,0xc6f9,0x67fe,0x4b07,0xfd69,0xee86,0xddcb,0xdda2}, new char[]{0x5035,0xddce,0x217c,0x2a55,0xdbd9,0xc695,0xb91a,0x058d,0x28cb,0xeef4,0xd3ce,0xa51b,0xa72d,0xbf45,0xbd57,0xfd0c,0xd7b2,0x1db9,0x6797,0x31b1,0x43bc,0xb918,0xc174,0xcd21,0x646d,0x698a,0x2c13,0xd4b3,0x6160,0x4b6a,0xf923,0x1416,0x479c,0xffb1,0x9045,0x1158,0xc51d,0xdb74,0x82e6,0xa499,0xddb8}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5ee2,0x1316,0x67f4,0xde37}, new char[]{0x67ba,0x5e83,0x137b,0xde52}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6fb1,0x76da,0x5d76,0xf9ea,0x693c,0x366c,0xc07b,0xb424}, new char[]{0x5d3b,0x76b5,0x694a,0xc01e,0xb449,0x6fd4,0xf984,0x3618}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x385c,0xc4e3,0x3275,0xaecc,0xb244,0xd27c,0x3ef3,0x4e83}, new char[]{0xc4b7,0x4ee2,0x383e,0xb264,0xae80,0x3e9a,0xd20f,0x3201}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x25d0,0x7af1,0x4c12,0x1eda,0x7656}, new char[]{0x7aa2,0x4c71,0x25b1,0x763a,0x1ebf}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8597,0x5028,0x35fa,0xc15b,0xd57b}, new char[]{0xc119,0x5044,0xd512,0x85e4,0x3589}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa22d,0x0cfa,0x6d8e,0x2119,0xfce4,0x4463,0x8555,0x8fa9,0x61de,0x01cb,0xbd72,0xcf1b}, new char[]{0x018e,0x0c94,0x61aa,0x6de7,0xcf6f,0xfc9d,0x8f89,0xbd26,0x852c,0x4413,0xa248,0x216a}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd618,0x5518,0xcaa4,0x19b8,0x68a3,0x104b,0xf6c2}, new char[]{0x5548,0x68cf,0x19d9,0x1032,0xf6a7,0xd66a,0xcad7}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xee89,0xa0a0,0x7d5b,0xa1ea,0x460c,0x7a05,0x8585}, new char[]{0x85cd,0x7a6a,0xa199,0xeefd,0x4665,0xa0cc,0x7d3e}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c81,0x7507,0xafe6,0xaaf7,0xece5,0xe1e1,0xa17a}, new char[]{0xecb5,0xaa96,0x0cf2,0xe192,0x756e,0xa10c,0xaf83}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe4d0,0xfcea,0x5421,0x015c,0x074b,0x2868,0x907f}, new char[]{0x9031,0x0139,0x281d,0xe4a4,0xfc98,0x5440,0x0727}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb347,0x3d38,0x0353,0x78de,0xb319,0x7248,0x3759,0xceb7,0x96bf}, new char[]{0x96f6,0x78b0,0xb36f,0x3d51,0xb334,0x7221,0x0331,0x3735,0xced2}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcb49,0x3960,0xe90e,0x4b6e,0x5542}, new char[]{0x4b20,0xcb28,0x5529,0x3905,0xe96a}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc0f3,0x9f42,0x617e,0x1c1e,0x0d0b,0x17d7,0x9830,0xa1d7,0x00df,0xc224,0x7ec1,0x5a49}, new char[]{0x0d42,0x00b8,0x7eaf,0x9f2d,0x9842,0x17b2,0xc204,0x5a1d,0x611b,0x1c7f,0xc09e,0xa1a4}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3a57,0xba9e,0x9f8d,0xf9a1,0x89fc,0xa6a0,0xb725,0x275c,0x7294,0x50a4,0x31e6,0x0398,0xb4d4,0x3484}, new char[]{0xf9e8,0xbaf9,0x50ca,0x8993,0x272e,0xb740,0x31c6,0x03de,0x9fff,0x72fd,0xa6c5,0xb4ba,0x3a33,0x34f7}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4ae4,0x9918,0xf0ec,0x812b,0x2625,0x75f9,0x672c,0xae65,0x398b}, new char[]{0x8169,0xf083,0x996c,0x2605,0x4aa3,0x39fe,0xae04,0x675e,0x759d}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x700a,0x31fb,0x0725,0xff6d,0x22a9,0xe849,0x38fd,0xf24c,0xd78b,0x6f9c}, new char[]{0xf20e,0x3892,0x6fe8,0x2289,0x0766,0xff05,0xd7ee,0x3198,0x7061,0xe83a}).toString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x30da,0x24f9,0xc2dd,0x6dd0,0x5544,0x9be7,0xf792,0x8745,0xae33,0x395f,0xaf3e}, new char[]{0xc296,0x393a,0xf7f7,0x8735,0x24d9,0x5510,0x9b86,0x6da2,0x30bd,0xae56,0xaf4a}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5620,0xf61f,0x7e8a,0x5767,0x8386,0x82d1,0x37de,0x78f4,0xa5b3,0x8581,0x34ce,0x1921,0xc325}, new char[]{0xa5ff,0x37bf,0x5653,0x83f2,0x1901,0x8290,0x34ba,0x7880,0x85e0,0xf67c,0x570c,0x7eef,0xc341}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5ac6,0x17d5,0xa15e,0x7386,0x2a44,0xd681,0x77f6,0x4753,0x57f1,0x771d,0xeb2c,0xd8e3,0xed68,0x1edc,0x2cea,0xce1a,0xd3cb}, new char[]{0x5a8d,0x2c8f,0x7793,0xeb5c,0x73a6,0x4707,0x17b4,0xd6f3,0x5796,0xed0d,0x1ea8,0x773d,0xa10c,0xd882,0x2a2a,0xd3ac,0xce7f}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0468,0x1a95,0x00f1,0x8ea7,0x9071,0xd354,0x1619,0x6126,0xc4dd,0x5108,0xe7a7,0x6d1b,0x55ee,0x2f5b,0x0a50,0x7809,0xa7e2,0x23eb,0xd295,0x730e}, new char[]{0xa7af,0xc4bc,0x009f,0x787c,0x2f3a,0x8ecb,0xe787,0x23bf,0x6147,0xd326,0x516f,0x167c,0x6d6f,0x9051,0x1ac6,0x040d,0x0a3c,0x736b,0x558d,0xd2e1}).plainString();
    }
}
