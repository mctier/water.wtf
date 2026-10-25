package com.utilitymod.modules;

import com.utilitymod.core.Module;
import com.utilitymod.gui.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;

/**
 * SSP AutoTool - no packets needed, client-side only. Tick-start logic.
 */
public class AutoToolModule extends Module {
    public AutoToolModule() { super(LT.L1, LT.L2, Category.COMBAT); }

    @Override public void onTickStart(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!client.options.attackKey.isPressed()) return;
        // Logic runs at tick start - selects hotbar slot before block break packet is sent vanilla-side
        // Vanilla packet PlayerActionC2SPacket will be sent by game with correct slot
        if (!(client.crosshairTarget instanceof BlockHitResult hit)) return;
        var state = client.world.getBlockState(hit.getBlockPos());
        if (state.isAir()) return;
        int bestSlot = client.player.getInventory().getSelectedSlot();
        float bestSpeed = 1f;
        for (int slot = 0; slot < 9; slot++) {
            var stack = client.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        client.player.getInventory().setSelectedSlot(bestSlot);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbcf2,0xe415,0xc60b,0xd2ff,0xcc69,0xeed9,0x47ed,0x8790}, new char[]{0xd2be,0xc67e,0x4799,0xcc06,0xee8d,0x87ff,0xbc9d,0xe479}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0146,0xa904,0xb7f8,0xbf24,0x176d,0x15d2,0xfe85,0xc2e0,0xb4b2,0xa2b2,0xa6f7,0x54d6,0x60bc,0x9522,0x4092,0x6833,0xb33b,0x3e00,0x8156,0xd238,0x8ad4,0x8686,0x5cb7,0xc90a,0x431c,0x867e,0x3ad9,0x4ac5,0x03ec,0x4287,0xc617,0xe4b7,0x5ba5,0xc788,0xcef6,0x8627,0xb47a,0x5898,0xfdb5,0x5e10,0x43e5}, new char[]{0x43a4,0x3aac,0xc663,0x0383,0x5e3d,0xb4c1,0x1708,0x4370,0x4aa0,0xa967,0xc97e,0x5b85,0xbf46,0xa692,0x8654,0x5cc3,0x8176,0xb34f,0xd257,0xfeea,0x3e6c,0x42a7,0x86e9,0xfddb,0x6813,0xa2d0,0x9550,0xb79d,0x861f,0xc28b,0x54f6,0xcede,0x1581,0x8a87,0xc7d8,0x40bf,0xb409,0x0127,0x58fe,0xe4d2,0x6095}).toString();
    }
}
