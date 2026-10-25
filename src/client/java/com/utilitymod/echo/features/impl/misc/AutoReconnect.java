package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.impl.render.Notifications;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Rejoins the last server after a disconnect, with a delay and an attempt limit. */
public class AutoReconnect extends Feature {
    private final IntSetting delay = new IntSetting(LT.L1, 5, 1, 60);
    private final IntSetting attempts = new IntSetting(LT.L2, 5, 1, 50);

    private ServerInfo lastServer;
    private long disconnectedAt;
    private int tries;

    public AutoReconnect() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd13b,0x0b39,0xb15d,0x379e,0x962d,0x226c,0xeb84,0xfcf3,0x82ce,0xbc06,0xa8be,0x1c9d,0x11fe,0x6555}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a4a,0xa1af,0x58e4,0x655a,0x261f,0x8af4,0x37d7,0x2a9a,0x47cf,0x828d,0xb983,0x1857,0x9bff,0xf89f,0x722c,0xac40,0x0b2f,0x1c86,0x50d8,0x76a7,0x962b,0x5f6d,0xebc1,0x04b4,0xa8ae,0xa43e,0x93bd,0x3355,0xfce6,0x6d7a,0xb161,0xd128,0xbc06,0xf051,0xe72f,0x9e54,0xd92b,0x3fbd,0xd2a1,0xb502,0x8671,0x3b78,0x0cd7,0x002f,0xc158,0x7e8b,0xcdea,0xc5c8,0x11f5,0x2272}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b}).toString(), Category.UTILITY));
    }

    /** Runs every client tick, including on the disconnected screen (tick hook is not world-bound). */
    @EventSubscribe
    private void onTick(EventTick event) {
        if (mc.getCurrentServerEntry() != null && mc.player != null) {
            lastServer = mc.getCurrentServerEntry();
            disconnectedAt = 0;
            tries = 0;
            return;
        }
        if (!(mc.currentScreen instanceof DisconnectedScreen) || lastServer == null) {
            disconnectedAt = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (disconnectedAt == 0) {
            disconnectedAt = now;
            if (tries < attempts.getValue()) {
                Notifications.push(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd13b,0x0b39,0xb15d,0x379e,0x962d,0x226c,0xeb84,0xfcf3,0x82ce,0xbc06,0xa8be,0x1c9d,0x11fe,0x6555}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}).toString(), LT.L3 + delay.getValue() + LT.L4, Notifications.Type.INFO);
            }
            return;
        }
        if (tries >= attempts.getValue() || now - disconnectedAt < delay.getValue() * 1000L) return;
        tries++;
        disconnectedAt = 0;
        ServerInfo info = lastServer;
        ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc, ServerAddress.parse(info.address), info, false, null);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa4eb,0x6cbe,0x5a90,0x30df,0x23ae,0xe3b0,0x369e,0x9fac,0x0160}, new char[]{0x23ea,0x30ba,0x9fc0,0xa48a,0x6cc7,0x0140,0x36b6,0xe3c3,0x5ab9}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1468,0xaea9,0x165a,0xc094,0x2158,0x6b7a,0xd7d5,0xda79,0x0f9a,0xd046,0x8a4f,0x57df}, new char[]{0x1617,0xc0f5,0xd7ad,0x0fba,0x6b3b,0xda0d,0x57ab,0x213d,0x8a22,0xd036,0xaedd,0x141b}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7572,0x74ea,0x9d73,0x5ca5,0x03de,0x2572,0xb7f8,0x63a7,0xa120,0xba64,0xf840,0x6fce,0x4cd7}, new char[]{0x6f9c,0x63c2,0x9d19,0x03b1,0x5ccc,0xa14e,0x751b,0xba0a,0x4cb0,0xb7d8,0x251b,0x7484,0xf860}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x43a8}, new char[]{0x43db,0x9129,0xc866,0xc950}).plainString();
    }
}
