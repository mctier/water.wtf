package com.utilitymod.echo.handlers.impl;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.SprintEvent;
import com.utilitymod.echo.handlers.Handler;
import lombok.Getter;
import net.minecraft.client.option.StickyKeyBinding;

public class SprintController extends Handler {
    
    @Getter
    private static boolean forceSprint = false;
    @Getter
    private static boolean forceStopSprint = false;

    public static void setForceSprint(boolean value) {
        forceSprint = value;
        if (value && mc.player != null) {
            if (mc.player.isSprinting()) return;
            mc.player.setSprinting(true);
            setSprintKeyState(true);
        }
    }

    public static void setForceStopSprint(boolean value) {
        forceStopSprint = value;
        if (value && mc.player != null) {
            if (!mc.player.isSprinting()) return;
            mc.player.setSprinting(false);
            setSprintKeyState(false);
        }
    }

    private static void setSprintKeyState(boolean desired) {
        if (mc.options == null) {
            return;
        }

        if (mc.options.sprintKey instanceof StickyKeyBinding && mc.options.getSprintToggled().getValue()) {
            if (mc.options.sprintKey.isPressed() != desired) {
                mc.options.sprintKey.setPressed(true);
            }
            return;
        }

        mc.options.sprintKey.setPressed(desired);
    }

    public static void reset() {
        forceSprint = false;
        forceStopSprint = false;
    }
    
    @EventSubscribe
    public void onSprint(SprintEvent event) {
        if (forceStopSprint) {
            event.setShouldSprint(false);
            return;
        }
        
        if (forceSprint) {
            event.setShouldSprint(true);
        }
    }
}
