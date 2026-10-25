package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.impl.movement.combohelpers.ComboMode;
import com.utilitymod.echo.features.impl.movement.combohelpers.WTap;
import com.utilitymod.echo.features.impl.movement.combohelpers.STap;
import com.utilitymod.echo.features.impl.movement.combohelpers.Strafe;
import com.utilitymod.echo.features.impl.movement.combohelpers.Round;
import com.utilitymod.echo.features.impl.movement.combohelpers.Jump;
import com.utilitymod.echo.features.impl.movement.combohelpers.Smart;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public class ComboHelper extends Feature {

    private static final CharSequence W_TAP_MODE = dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x49,0x04,0x24,0xDA,0xB2});
    private static final CharSequence S_TAP_MODE = Concat.of(LT.L1);
    private static final CharSequence STRAFE_MODE = Concat.of(LT.L2);
    private static final CharSequence ROUND_MODE = Concat.of(LT.L3);
    private static final CharSequence JUMP_MODE = Concat.of(LT.L4);
    private static final CharSequence SMART_MODE = Concat.of(LT.L5);

    private static final ModeSetting modeSetting = new ModeSetting(
        Concat.of(LT.L6),
        W_TAP_MODE,
        W_TAP_MODE,
        S_TAP_MODE,
        STRAFE_MODE,
        ROUND_MODE,
        JUMP_MODE,
        SMART_MODE
    );

    /** Distance helper: only assist when the target is within this many blocks (1..3). */
    public static final FloatSetting distanceSetting =
        new FloatSetting(Concat.of(LT.L7), 3f, 1f, 3f, 0.1f);

    @SuppressWarnings("unused") private static final IntSetting wTapDurationSetting = WTap.durationSetting;
    @SuppressWarnings("unused") private static final IntSetting wTapReactionSetting = WTap.reactionSetting;
    @SuppressWarnings("unused") private static final BoolSetting wTapAllowInAirSetting = WTap.allowInAir;

    @SuppressWarnings("unused") private static final IntSetting sTapDurationSetting = STap.durationSetting;
    @SuppressWarnings("unused") private static final IntSetting sTapReactionSetting = STap.reactionSetting;
    @SuppressWarnings("unused") private static final BoolSetting sTapAllowInAirSetting = STap.allowInAir;

    @SuppressWarnings("unused") private static final IntSetting strafeDurationSetting = Strafe.durationSetting;
    @SuppressWarnings("unused") private static final IntSetting strafeReactionSetting = Strafe.reactionSetting;
    @SuppressWarnings("unused") private static final BoolSetting strafeAllowInAirSetting = Strafe.allowInAir;

    @SuppressWarnings("unused") private static final IntSetting roundDurationSetting = Round.durationSetting;
    @SuppressWarnings("unused") private static final IntSetting roundReactionSetting = Round.reactionSetting;
    @SuppressWarnings("unused") private static final BoolSetting roundAllowInAirSetting = Round.allowInAir;

    @SuppressWarnings("unused") private static final IntSetting jumpDurationSetting = Jump.durationSetting;
    @SuppressWarnings("unused") private static final IntSetting jumpReactionSetting = Jump.reactionSetting;
    @SuppressWarnings("unused") private static final BoolSetting jumpAllowInAirSetting = Jump.allowInAir;

    @SuppressWarnings("unused") private static final IntSetting smartDurationSetting = Smart.durationSetting;
    @SuppressWarnings("unused") private static final IntSetting smartReactionSetting = Smart.reactionSetting;
    @SuppressWarnings("unused") private static final BoolSetting smartAllowInAirSetting = Smart.allowInAir;

    static {
        WTap.durationSetting.setDependency(o -> modeSetting.is(W_TAP_MODE));
        WTap.reactionSetting.setDependency(o -> modeSetting.is(W_TAP_MODE));
        WTap.allowInAir.setDependency(o -> modeSetting.is(W_TAP_MODE));

        STap.durationSetting.setDependency(o -> modeSetting.is(S_TAP_MODE));
        STap.reactionSetting.setDependency(o -> modeSetting.is(S_TAP_MODE));
        STap.allowInAir.setDependency(o -> modeSetting.is(S_TAP_MODE));

        Strafe.durationSetting.setDependency(o -> modeSetting.is(STRAFE_MODE));
        Strafe.reactionSetting.setDependency(o -> modeSetting.is(STRAFE_MODE));
        Strafe.allowInAir.setDependency(o -> modeSetting.is(STRAFE_MODE));

        Round.durationSetting.setDependency(o -> modeSetting.is(ROUND_MODE));
        Round.reactionSetting.setDependency(o -> modeSetting.is(ROUND_MODE));
        Round.allowInAir.setDependency(o -> modeSetting.is(ROUND_MODE));

        Jump.durationSetting.setDependency(o -> modeSetting.is(JUMP_MODE));
        Jump.reactionSetting.setDependency(o -> modeSetting.is(JUMP_MODE));
        Jump.allowInAir.setDependency(o -> modeSetting.is(JUMP_MODE));

        Smart.durationSetting.setDependency(o -> modeSetting.is(SMART_MODE));
        Smart.reactionSetting.setDependency(o -> modeSetting.is(SMART_MODE));
        Smart.allowInAir.setDependency(o -> modeSetting.is(SMART_MODE));
    }

    private final Map<CharSequence, ComboMode> modes = new LinkedHashMap<>();

    public ComboHelper() {
        super(new FeatureInfo(
            Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb93,0x0b14,0x1c9e,0x9633,0x2275,0xd139,0x11f5,0x82c8,0xa8a4,0x37d1,0xb12f,0xbc0b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}).toString()),
            Concat.of(LT.L8),
            Category.MOVEMENT
        ));
        modes.put(W_TAP_MODE, new WTap());
        modes.put(S_TAP_MODE, new STap());
        modes.put(STRAFE_MODE, new Strafe());
        modes.put(ROUND_MODE, new Round());
        modes.put(JUMP_MODE, new Jump());
        modes.put(SMART_MODE, new Smart());
    }

    private ComboMode getCurrentMode() {
        return modes.get(modeSetting.getValue());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        modes.values().forEach(ComboMode::onEnable);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        modes.values().forEach(ComboMode::onDisable);
    }

    @EventSubscribe
    public void onAttack(EventStartAttack e) {
        ComboMode mode = getCurrentMode();
        if (mode != null) {
            mode.onAttack(e);
        }
    }

    @EventSubscribe
    public void onMovement(EventMovementInput e) {
        ComboMode mode = getCurrentMode();
        if (mode != null) {
            mode.onMovement(e);
        }
    }

    public boolean isEntityAllowed(LivingEntity entity) {
        return TargetUtils.isTargetAllowed(entity);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b7a,0xb0fe,0xde94,0x3ed0,0x25f3}, new char[]{0xb0ad,0xdeb9,0x25a7,0x4b1b,0x3ea0}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf251,0xf802,0x4fc6,0x562c,0xe2e4,0xc865}, new char[]{0xf851,0xc811,0x4fb4,0xe285,0xf237,0x5649}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf6f6,0x8a5d,0x4be5,0xe3c0,0x6256}, new char[]{0xf6a4,0x6239,0x8a28,0xe3ae,0x4b81}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf69b,0x3359,0x6f0c,0x0726}, new char[]{0x3313,0xf6ee,0x6f61,0x0756}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfca8,0xdbc3,0xf5f0,0xda11,0xcdee}, new char[]{0xf5a3,0xdbae,0xda70,0xcd9c,0xfcdc}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1f2a,0x3025,0x3434,0x58ea}, new char[]{0x3479,0x5885,0x1f4e,0x3040}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8de8,0x2cb8,0x3dcb,0xc098,0x2a53,0x20fa,0x1e66,0x2b0f}, new char[]{0x20be,0x2a3a,0x2ccb,0x1e12,0xc0f9,0x8d86,0x3da8,0x2b6a}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x274e,0x56a5,0x1523,0x04a4,0x4ff7,0x9592,0xa9d4,0xb083,0x438f,0x8f28,0x1d8f,0x28ff,0x002e,0xfb20,0xbe59,0x83ee,0x3a10,0x9097,0x774a,0xb77c,0xa295,0x1db2,0x4e38,0x731e,0xa9fe,0xd53f}, new char[]{0x90d6,0xa98d,0xd54c,0xb0ea,0x736d,0x43fb,0xa9a7,0x83ce,0x0059,0xbe30,0x04d0,0x8f40,0x95b2,0x7729,0x4f98,0x154e,0x272c,0x4e57,0x28df,0xa2f4,0xb708,0x1dfb,0x3a71,0xfb43,0x1dd9,0x56d6}).plainString();
    }
}
