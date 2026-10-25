package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.api.Accessors;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

/**
 * MouseTweaks-style inventory helpers: scroll over a slot to shift-move its stack, or hold left
 * click and drag across slots (with an empty cursor) to mass shift-move them to the other inventory.
 */
public class MouseTweaks extends Feature {
    private final BoolSetting wheelTransfer = new BoolSetting(LT.L1, true);
    private final BoolSetting dragTransfer = new BoolSetting(LT.L2, true);

    private int lastDragSlot = -1;

    public MouseTweaks() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb92,0x0b08,0x1c97,0x9622,0x226d,0xd137,0x11fb,0x82da,0xa8a4,0x37db,0xb12f,0xbc1a}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x04f1,0x6d66,0x7a58,0x6557,0x1818,0xd92a,0xe0b1,0x226a,0xe732,0xb163,0x4782,0x37d2,0x0cc7,0xc5c9,0xd129,0x93e4,0xb98d,0xef5a,0x3b73,0x5f67,0x3347,0xac03,0x15c8,0xcdb3,0xa1b5,0xbc06,0x66e1,0x0b7c,0xf044,0xeb93,0x82c2,0x3ff1,0xfce0,0xdac3,0x4a09,0x7ec2,0x2adf,0xa43f,0xf41c,0x76f4,0x8af3,0x723d,0x11f4,0xc97d,0xc153,0xa8a8,0x1c80,0xb50a,0x2619,0x4fab,0x8625,0x9bf5,0xf89e,0x0023,0x9663,0x5475,0x50dd,0x4305,0x9e52,0xd2b7,0x58fb}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406}).toString(), Category.UTILITY));
        // Attach a scroll listener to every container screen once; the handler gates on the module.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen<?> hs)) return;
            ScreenMouseEvents.afterMouseScroll(screen).register((scr, mx, my, hAmt, vAmt, consumed) -> {
                if (isEnabled() && wheelTransfer.getValue() && vAmt != 0
                        && hs.getScreenHandler().getCursorStack().isEmpty()) quickMove(hs, focusedSlot(hs));
                return false;
            });
        });
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (!dragTransfer.getValue() || mc.player == null || !(mc.currentScreen instanceof HandledScreen<?> hs)) {
            lastDragSlot = -1;
            return;
        }
        boolean leftDown = mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!leftDown || !hs.getScreenHandler().getCursorStack().isEmpty()) { lastDragSlot = -1; return; }
        Slot slot = focusedSlot(hs);
        if (slot == null) return;
        if (slot.id == lastDragSlot) return;      // one move per newly-hovered slot
        lastDragSlot = slot.id;
        quickMove(hs, slot);
    }

    private void quickMove(HandledScreen<?> hs, Slot slot) {
        if (slot == null || !slot.hasStack() || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(hs.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    private Slot focusedSlot(HandledScreen<?> hs) {
        try {
            Object s = Accessors.field(HandledScreen.class, LT.L3, LT.L4).get(hs);
            return s instanceof Slot slot ? slot : null;
        } catch (Exception e) {
            return null;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ee1,0x1c7c,0xe7ea,0x663b,0x91bf,0xcd53,0x89b1,0xe383,0x9ea3,0xeed2,0x290f,0x1032,0xc9af,0x403c}, new char[]{0xc9f8,0xcd3b,0x665e,0x1c19,0x89dd,0xe3a3,0x4068,0x2e93,0xeeb3,0xe784,0x9ed0,0x91d9,0x296a,0x1040}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3808,0x740a,0x368b,0x6d2b,0x5489,0xe363,0x6aea,0xac86,0x5875,0x4cd2,0xd870,0xe2f4,0x4c0e}, new char[]{0xd834,0xe311,0xe295,0x4cb5,0x6aca,0x5821,0xacf4,0x3869,0x7464,0x36f8,0x4c68,0x54ec,0x6d59}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x593a,0x53b6,0x0248,0x2b8b,0x0479,0xe972,0xaed3,0x8b94,0x93b5,0x3da1}, new char[]{0x93d3,0x2be2,0x53d3,0xaebf,0x595e,0x0217,0xe940,0x044e,0x3d99,0x8ba3}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde2c,0x0b12,0x4d8a,0x4467,0xc21b,0x870c,0xf3d2,0x5932,0x4bb7,0x8fe4,0x3c18}, new char[]{0x8f82,0x595d,0xf3b1,0x8779,0xde5f,0x0b77,0x4403,0x3c4b,0x4de6,0x4bd8,0xc26f}).plainString();
    }
}
