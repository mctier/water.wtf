package com.utilitymod.echo.handlers.impl;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.handlers.Handler;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;

import java.util.ArrayDeque;
import java.util.Deque;

public final class SwapStateManager extends Handler {

    private enum RestorePhase {
        EARLY,
        POST
    }

    private static final Deque<SwapState> stack = new ArrayDeque<>();

    public static boolean swapTo(Feature owner, int targetSlot, boolean inputSimulation) {
        return swapTo(owner, targetSlot, inputSimulation, 0, true, null);
    }

    public static boolean swapTo(Feature owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks) {
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, true, null);
    }

    public static boolean swapTo(Feature owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd) {
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, restoreOnEnd, null);
    }

    public static boolean swapTo(Feature owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, Runnable onEnd) {
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, true, onEnd);
    }

    public static boolean swapTo(Feature owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd, Runnable onEnd) {
        if (owner == null || mc.player == null) return false;
        if (targetSlot < 0 || targetSlot > 8) return false;

        SwapState top = stack.peek();
        if (top != null && top.owner == owner) {
            top.targetSlot = targetSlot;
            top.inputSimulation = inputSimulation;
            top.restoreOnEnd = restoreOnEnd;
            top.restoreTick = restoreDelayTicks < 0 ? Long.MAX_VALUE : mc.player.age + restoreDelayTicks;
            top.restorePhase = restoreDelayTicks <= 0 ? RestorePhase.POST : RestorePhase.EARLY;
            top.onEnd = onEnd;
            InventoryUtils.setInvSlot(targetSlot, inputSimulation);
            return true;
        }

        if (isActive(owner)) return false;

        int previousSlot = getSelectedSlot();
        if (previousSlot == targetSlot) return false;

        SwapState rootState = stack.peekLast();
        int rootSlot = rootState != null ? rootState.rootSlot : previousSlot;
        boolean rootInputSimulation = rootState != null ? rootState.rootInputSimulation : inputSimulation;

        long restoreTick = restoreDelayTicks < 0 ? Long.MAX_VALUE : mc.player.age + restoreDelayTicks;
        RestorePhase restorePhase = restoreDelayTicks <= 0 ? RestorePhase.POST : RestorePhase.EARLY;

        stack.push(new SwapState(owner, rootSlot, rootInputSimulation, targetSlot, inputSimulation, restoreOnEnd, restoreTick, restorePhase, onEnd));
        InventoryUtils.setInvSlot(targetSlot, inputSimulation);
        return true;
    }

    public static boolean swapToIfNeeded(Feature owner, int targetSlot, boolean inputSimulation) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, 0, true, null);
    }

    public static boolean swapToIfNeeded(Feature owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, restoreDelayTicks, true, null);
    }

    public static boolean swapToIfNeeded(Feature owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, restoreDelayTicks, restoreOnEnd, null);
    }

    public static boolean swapToIfNeeded(Feature owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, Runnable onEnd) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, restoreDelayTicks, true, onEnd);
    }

    public static boolean swapToIfNeeded(Feature owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd, Runnable onEnd) {
        if (owner == null || mc.player == null) return false;
        if (targetSlot < 0 || targetSlot > 8) return false;

        int selectedSlot = getSelectedSlot();
        if (selectedSlot == targetSlot && !isActive(owner)) {
            return true;
        }

        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, restoreOnEnd, onEnd);
    }

    public static boolean isActive(Feature owner) {
        if (owner == null) return false;
        for (SwapState state : stack) {
            if (state.owner == owner) return true;
        }
        return false;
    }

    public static boolean isActive(Class<? extends Feature> ownerClass) {
        if (ownerClass == null) return false;
        for (SwapState state : stack) {
            if (ownerClass.isInstance(state.owner)) return true;
        }
        return false;
    }

    public static boolean isOwnerActive(Feature owner) {
        return isActive(owner);
    }

    public static boolean isOwnerActive(Class<? extends Feature> ownerClass) {
        return isActive(ownerClass);
    }

    public static boolean hasActiveSwaps() {
        return !stack.isEmpty();
    }

    public static boolean updateSlot(Feature owner, int targetSlot) {
        if (owner == null) return false;
        SwapState state = stack.peek();
        if (state == null || state.owner != owner) return false;
        state.targetSlot = targetSlot;
        return true;
    }

    public static int getActiveTargetSlot(Feature owner) {
        if (owner == null) return -1;
        SwapState state = stack.peek();
        if (state == null || state.owner != owner) return -1;
        return state.targetSlot;
    }

    public static int getSelectedSlot() {
        if (mc.player == null) return -1;
        SwapState state = stack.peek();
        return state != null ? state.targetSlot : mc.player.getInventory().getSelectedSlot();
    }

    public static void cancel(Feature owner) {
        cancel(owner, true);
    }

    public static void cancel(Feature owner, boolean restore) {
        if (owner == null || stack.isEmpty()) return;
        if (!isActive(owner)) return;

        if (restore) {
            restoreRootAndClear(stack.peekLast());
            return;
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.pop();
            endState(state, false, true);
            if (state.owner == owner) {
                break;
            }
        }
    }

    public static void clear() {
        stack.clear();
    }

    @EventSubscribe(priority = EventSubscribe.Priority.LOWEST)
    private void onHandleInputEarly(EventHandleInput.Early event) {
        processDueRestores(RestorePhase.EARLY);
    }

    @EventSubscribe(priority = EventSubscribe.Priority.LOWEST)
    private void onHandleInputPost(EventHandleInput.Post event) {
        processDueRestores(RestorePhase.POST);
    }

    @EventSubscribe
    private void onPacketReceive(EventPacketReceive event) {
        if (event.getPacket() instanceof PlayerRespawnS2CPacket || event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            clear();
        }
    }

    private static void processDueRestores(RestorePhase phase) {
        if (mc.player == null) {
            clear();
            return;
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.peek();
            if (!state.isDue(mc.player.age, phase)) break;

            stack.pop();
            if (state.restoreOnEnd) {
                restoreRootAndClear(state);
                runEnd(state);
                return;
            }

            endState(state, false, false);
        }
    }

    private static void restoreRootAndClear(SwapState fallbackRoot) {
        SwapState root = stack.peekLast();
        if (root == null) root = fallbackRoot;

        if (root != null && mc.player != null) {
            InventoryUtils.setInvSlot(root.rootSlot, root.rootInputSimulation);
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.pop();
            runEnd(state);
        }
    }

    private static void endState(SwapState state, boolean restore, boolean forceRestore) {
        if (state == null) return;

        if (restore && mc.player != null && (forceRestore || state.restoreOnEnd)) {
            InventoryUtils.setInvSlot(state.rootSlot, state.rootInputSimulation);
        }

        runEnd(state);
    }

    private static void runEnd(SwapState state) {
        if (state.onEnd == null) return;
        try {
            state.onEnd.run();
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }

    private static final class SwapState {
        private final Feature owner;
        private final int rootSlot;
        private final boolean rootInputSimulation;
        private int targetSlot;
        private boolean inputSimulation;
        private boolean restoreOnEnd;
        private long restoreTick;
        private RestorePhase restorePhase;
        private Runnable onEnd;

        private SwapState(Feature owner, int rootSlot, boolean rootInputSimulation, int targetSlot, boolean inputSimulation, boolean restoreOnEnd, long restoreTick, RestorePhase restorePhase, Runnable onEnd) {
            this.owner = owner;
            this.rootSlot = rootSlot;
            this.rootInputSimulation = rootInputSimulation;
            this.targetSlot = targetSlot;
            this.inputSimulation = inputSimulation;
            this.restoreOnEnd = restoreOnEnd;
            this.restoreTick = restoreTick;
            this.restorePhase = restorePhase;
            this.onEnd = onEnd;
        }

        private boolean isDue(long currentTick, RestorePhase phase) {
            return restoreTick != Long.MAX_VALUE
                    && currentTick >= restoreTick
                    && phase.ordinal() >= restorePhase.ordinal();
        }
    }
}
