package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;

public class NoLungeCooldown extends Feature {

    public NoLungeCooldown() {
        super(new FeatureInfo(
            Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a04,0x76bb,0xf052,0xb161,0x2238,0xd134,0x0b3b,0xeb8e,0x82c8,0x1cd2,0x9600,0x11ff,0xa8a4,0x37cb,0xfce3,0x655a,0xbc25}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a}).toString()),
            Concat.of(LT.L1),
            Category.MOVEMENT
        ));
    }

    private final IntSetting multiplier = new IntSetting(Concat.of(LT.L2), 1, 1, 10);

    @EventSubscribe
    public void onAttack(EventStartAttack attack) {
        if (isNull()) return;
        if (InventoryUtils.isHoldingSpearItem()) {
            attack.cancel();
            sendStabPackets();
        }
    }

    private void sendStabPackets() {
        int count = Math.max(1, Math.min(multiplier.getValue(), 10));
        // Grim PacketOrderB ("Did not swing for attack"): every attack needs a main-hand
        // ANIMATION since the previous attack, and one again before the tick closes.
        // Raw consecutive STABs with the swing cancelled = pre-attack flag per stab.
        // Wire shape below: swing, stab, swing, stab, ... swing.
        for (int i = 0; i < count; i++) {
            if (i > 0) mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(
                new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STAB, BlockPos.ORIGIN, Direction.DOWN)
            );
        }
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x566a,0x40a1,0x72bd,0xff97,0x0c10,0x5501,0xd2d4,0x7e5c,0xa00f,0x1420,0xe60c,0x1e7e,0x4ca9,0x23d4,0x6703,0xe982,0x1471,0xacac,0x2130,0xedad,0xfedf,0x5892,0xdede,0x4214,0xa0d3,0x2d00,0x7f40,0x2b69,0x5376}, new char[]{0xe9cf,0x23bb,0xdeba,0xedc4,0x1e18,0x72d4,0x7f25,0x58e1,0x0c30,0x5619,0xa07f,0xacc9,0xfebe,0xffe5,0x4c89,0x676f,0x40d4,0xa0bd,0x5311,0x1445,0x1451,0x7e2a,0x2155,0xe660,0xd2bb,0x2d63,0x2b00,0x4260,0x5578}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfdc8,0x3a63,0xfd56,0x07ff,0x2b73,0x4ab2,0x5be1,0xb4cb,0xfaac,0xb227}, new char[]{0xfd1b,0xfdbd,0x2b1f,0xb4bf,0x4adb,0x3a13,0x0793,0x5b88,0xb242,0xfade}).plainString();
    }
}
