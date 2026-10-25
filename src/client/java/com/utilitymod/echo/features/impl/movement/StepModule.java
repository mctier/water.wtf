package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.entity.attribute.EntityAttributes;

public class StepModule extends Feature {
    public StepModule() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227d,0xa8bf,0xd129,0xbc19}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69}).toString()),
                Concat.of(LT.L1),
                Category.MOVEMENT,
                false
        ));
    }

    private final FloatSetting stepHeightSetting = new FloatSetting(Concat.of(LT.L2), 1.0f, 1.0f, 3.0f, 0.1f);
    private final BoolSetting disableOnSneak = new BoolSetting(Concat.of(LT.L3), false);

    private void setStepHeight(float stepHeight) {
        mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(stepHeight);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        setStepHeight(0.6f);
    }


    // Source: CCBLUEX
    /**
     * A common jump looks like this:
     * PlayerMoveC2SPacket 207.30000001192093 62.0 149.86302076816213 0.0 0.0 true
     * PlayerMoveC2SPacket 207.30000001192093 62.41999998688698 149.86517219525172 0.0 0.0 false
     * PlayerMoveC2SPacket 207.30000001192093 62.7531999805212 149.8656024806536 0.0 0.0 false
     * PlayerMoveC2SPacket 207.28040473521648 63.00133597911215 149.86603276605547 0.0 0.0 false
     * PlayerMoveC2SPacket 207.23709917391574 63.166109260938214 149.8665921370619 0.0 0.0 false
     * PlayerMoveC2SPacket 207.17221725301053 63.24918707874468 149.86715150806833 0.0 0.0 false
     * PlayerMoveC2SPacket 207.0877008442994 63.25220334025373 149.86771087907476 0.0 0.0 false
     * PlayerMoveC2SPacket 206.98531705116994 63.17675927506424 149.8682702500812 0.0 0.0 false
     * PlayerMoveC2SPacket 206.86667393775122 63.024424088213685 149.86882962108763 0.0 0.0 false
     * PlayerMoveC2SPacket 206.73323484244284 63.0 149.86938899209406 0.0 0.0 true
     */

    @EventSubscribe
    public void onTick(EventTick event) {
        if (isNull()) return;

        if (mc.player.isSneaking() && disableOnSneak.getValue()) return;
        setStepHeight(stepHeightSetting.getValue());
    }




    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf307,0x7259,0x1fbd,0x121e,0x127a,0xd549,0xb50a,0x4eb9,0x0100,0x8ddb,0xc504,0xbed6,0xd892,0x349d,0xa2b4,0xfc82,0x05a5,0x9cdf,0x31fd,0xbbd6,0xa228,0xe8eb,0xe833,0xa686,0x4271,0x597c,0x6ce8,0xaf5f,0xfefe,0x94f3,0x1de4,0xe729,0xa8c0,0xac95,0x2d3d,0x9997,0xf1ec}, new char[]{0xa2f7,0x1d8c,0xa249,0x949d,0x2d5a,0xe74c,0xace6,0x7279,0x4205,0xc56c,0x99f2,0xf327,0xb579,0xbba2,0xaf3a,0xa6f6,0xa8e0,0x8db3,0x3198,0xd8fb,0x6c8f,0xbebe,0x0174,0xf1cc,0xfced,0xe855,0x1f9d,0x34e9,0xd521,0xfe9b,0x0585,0xe89b,0x1272,0x121b,0x9ca6,0x5919,0x4ecb}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x77f9,0x1262,0x6990,0x072b,0x8e36,0x66fc}, new char[]{0x69d8,0x8e53,0x6695,0x779e,0x120a,0x075f}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb977,0x27d8,0xda96,0x8327,0x31bb,0x5065,0x6495,0xbb36,0x701a,0xde48,0x193b,0xa780,0xb8e1,0x132a,0x6809,0x2203}, new char[]{0x8363,0xb91e,0xde3b,0xbb57,0x27ba,0x5009,0xdaf3,0x191b,0x7075,0x6867,0x130a,0xa7f3,0xb88f,0x31de,0x64f4,0x2268}).plainString();
    }
}
