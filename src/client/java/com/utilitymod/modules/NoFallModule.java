package com.utilitymod.modules;

import com.utilitymod.core.Module;
import com.utilitymod.gui.Category;
import com.utilitymod.network.PacketQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Vanilla NoFall - only uses PlayerMoveC2SPacket.OnGroundOnly at tick start.
 * No custom packets. Tested on integrated server + test servers via vanilla packet.
 */
public class NoFallModule extends Module {
    public NoFallModule() { super(LT.L1, LT.L2, Category.MOVEMENT); }

    @Override public void onTickStart(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.fallDistance > 3.0f && !client.player.isOnGround()) {
            // Enqueue - will flush at same tick start, not between ticks
            PacketQueue.enqueue(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3072,0xd686,0x6b60,0xa4de,0xe067,0x3907}, new char[]{0x3949,0xd6e9,0xe021,0x3013,0x6b0c,0xa4b2}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9914,0xee11,0xefb7,0x3a67,0x8f2e,0x7af2,0xddca,0x4235,0xbc5c,0xe39c,0xa11a,0x8cc0,0x2040,0xc49b,0x68d3,0xb181,0xfe69,0xa779,0x1c93,0x853a,0x5f15,0x051b,0x7f56,0x546c,0x5c16,0xfeca,0x0b7c,0x8315,0x6422,0x078e,0x7ed1,0xdcfd,0x94c0,0x75e2,0x7b6b,0x28e4}, new char[]{0x7e87,0x540d,0x5f7b,0xbc35,0x0577,0x94ac,0x3a06,0x28c4,0xefd8,0x7f38,0xb1c6,0x0b0e,0x1cfc,0x5c63,0xfe07,0x6446,0xa13a,0x8cb3,0xddba,0x07e1,0xe3f3,0x8f48,0x2060,0x8374,0x4241,0x68f3,0x854e,0x997d,0xee72,0xdc96,0x7b4b,0x7a81,0xa70d,0xc4fa,0x7590,0xfebe}).toString();
    }
}
