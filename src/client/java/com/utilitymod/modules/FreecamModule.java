package com.utilitymod.modules;

import com.utilitymod.core.Module;
import com.utilitymod.gui.Category;
import com.utilitymod.network.PacketQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

/**
 * SSP Freecam - vanilla packets only, tick-start.
 * Sends PlayerMove to keep server pos while camera moves.
 */
public class FreecamModule extends Module {
    private Vec3d savedPos;
    private float savedYaw, savedPitch;

    public FreecamModule() { super(LT.L1, LT.L2, Category.MOVEMENT); }

    @Override protected void onEnable() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null) {
            savedPos = new Vec3d(c.player.getX(), c.player.getY(), c.player.getZ());
            savedYaw = c.player.getYaw();
            savedPitch = c.player.getPitch();
            c.player.setNoGravity(true);
        }
    }
    @Override protected void onDisable() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null && savedPos != null) {
            c.player.setPos(savedPos.x, savedPos.y, savedPos.z);
            c.player.setYaw(savedYaw);
            c.player.setPitch(savedPitch);
            c.player.setNoGravity(false);
        }
    }

    @Override public void onTickStart(MinecraftClient client) {
        if (client.player == null || savedPos == null) return;
        // Keep server believing we're at savedPos - send at tick start only
        PacketQueue.enqueue(new PlayerMoveC2SPacket.PositionAndOnGround(savedPos.x, savedPos.y, savedPos.z, client.player.isOnGround(), false));
        // Client-side movement handled by noClip + input
        client.player.setNoGravity(true);
        client.player.noClip = true;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x31cc,0xa56c,0xbf81,0x368a,0xdb88,0x8251,0x8ade}, new char[]{0xdbce,0x36f8,0x31a9,0xbfe4,0x8abd,0x8230,0xa501}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5d16,0x343a,0xec3f,0x2c50,0x0142,0xdf7c,0x6faf,0xf22d,0xa226,0x9e1f,0x67b5,0x786a,0x7e89,0x8968,0xfbeb,0x5148,0x8ea3,0xac92,0xb794,0x62a0,0x7ace,0x0b35,0x9e36,0xbd7d,0x31bf,0x9825,0xb45f,0x605a,0xc3c2,0xf28b,0x92ae,0x7bfe,0x75c0,0xe97d,0x6dbe,0xa3a8,0x8709,0x2d89,0x0123,0x3ce2,0xcb03,0x8c8e,0x39eb,0x773e,0x5d49,0x02e5,0x44b6,0x1078}, new char[]{0x3469,0xacc1,0x1028,0x39cb,0xa240,0x9857,0x0146,0x92cb,0x5d75,0xb43e,0x2de4,0x7ea9,0x7847,0x5d69,0x7748,0xa3c9,0xf243,0x9e76,0x8ce2,0xe911,0xc3a3,0x6280,0x44db,0x7aa1,0x31c9,0x6ddb,0xbd5d,0x67c5,0x603b,0x2c33,0xf2e0,0x9e53,0x7b8a,0xdf0f,0xcb23,0x6fce,0x3c96,0xfbcb,0x8ed7,0x8901,0x0121,0x8762,0x02c5,0x0b46,0xb7e0,0x75a1,0xec4d,0x513c}).toString();
    }
}
