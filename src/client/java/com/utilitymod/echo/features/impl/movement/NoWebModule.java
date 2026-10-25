package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.utils.player.MoveUtils;
import com.utilitymod.echo.utils.player.PlayerIntersectionUtil;
import com.utilitymod.echo.utils.strings.Concat;

public class NoWebModule extends Feature {
    public NoWebModule() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8a4,0xb16d,0xd134,0x2238,0x37db,0xbc3e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f}).toString()),
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x82fe,0x76ba,0x0b7c,0xeb85,0x962c,0xa8a4,0x37db,0x1c9e,0xfcf0,0xbc3e,0xb16d,0xd134,0x6559,0x2238,0x11e7}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4}).toString()),
                Category.MOVEMENT
        ));
    }

    private final ModeSetting modeSetting = new ModeSetting(Concat.of(LT.L1), Concat.of(LT.L2), Concat.of(LT.L2));
    private final FloatSetting speedSetting = new FloatSetting(Concat.of(LT.L3), 0.35f, 0.35f, 0.65f, 0.1f);

    @EventSubscribe
    private void onTick(EventTick e) {
        if (isNull() || !isEnabled() || !modeSetting.is(Concat.of(LT.L2))) {
            return;
        }

        if (!PlayerIntersectionUtil.isPlayerInWeb()) {
            return;
        }

        final double horizontalSpeed = speedSetting.getValue();
        final double yaw = MoveUtils.getDirection();
        final double x = -Math.sin(yaw) * horizontalSpeed;
        final double z = Math.cos(yaw) * horizontalSpeed;
        final double y = mc.options.jumpKey.isPressed() ? 0.65D : (mc.options.sneakKey.isPressed() ? -0.65D : 0.0D);

        mc.player.setVelocity(
                mc.player.getVelocity().x + x,
                y,
                mc.player.getVelocity().z + z
        );
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9f6c,0x02cc,0xead6,0x335d}, new char[]{0x0281,0xeab9,0x9f08,0x3338}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x91b5,0xf96b,0x6d29,0x7c02}, new char[]{0xf92c,0x7c70,0x6d40,0x91d8}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3fbe,0xbf86,0x811c,0xf3a8,0xe555}, new char[]{0x814f,0xbff6,0x3fdb,0xf3cd,0xe531}).plainString();
    }
}
