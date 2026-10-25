package com.utilitymod.modules;

import com.utilitymod.core.Module;
import com.utilitymod.gui.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Echo Nametags port: compact, readable nearby-player status labels. */
public final class NametagsModule extends Module {
    public NametagsModule() { super(LT.L1, LT.L2, Category.VISUAL); }

    @Override public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        int x = 8;
        int y = 8;
        for (var player : client.world.getPlayers()) {
            if (player == client.player || player.isSpectator() || player.squaredDistanceTo(client.player) > 4096) continue;
            String label = player.getName().getString() + "  " + Math.ceil(player.getHealth()) + "❤";
            int width = client.textRenderer.getWidth(label);
            context.fill(x - 3, y - 2, x + width + 3, y + 9, 0xB012141D);
            context.drawText(client.textRenderer, label, x, y, 0xFFEAEAEA, true);
            y += 13;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5339,0x8193,0xc0e6,0xb871,0x8246,0x0271,0x2add,0x1290}, new char[]{0x81dd,0x0210,0xc08b,0x2ab8,0x8232,0x5358,0x12f7,0xb802}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd11c,0xa6c1,0x22d3,0xc60d,0x87c4,0x7e25,0xf313,0x6a8f,0xd062,0x02f8,0xb0b3,0x111f,0xf2c3,0x122f,0xf86f,0x06d8,0x428a,0xcbd1,0x7de1,0x1040,0x325f,0xec7c,0x2ef3,0x2229,0xf6d3,0x234a,0x0557,0x5a89,0x3fa5,0xc06c,0x9e6d,0xfc65,0x044b,0x99cf,0x757c,0xfdee}, new char[]{0xc03f,0xfd86,0x2325,0x043c,0x116c,0xcbf1,0x3fcb,0x124a,0xd003,0xf2b1,0xec1e,0x99b6,0x327f,0x22a3,0x42e6,0xf372,0x7e5c,0x7d84,0xc67f,0x1060,0x06b6,0x0299,0xf802,0xd179,0x750f,0x9e4d,0x5ae8,0xf6bd,0xa6a5,0xb093,0x2241,0x87a1,0x6aee,0x053b,0xfc11,0x2e9b}).toString();
    }
}
