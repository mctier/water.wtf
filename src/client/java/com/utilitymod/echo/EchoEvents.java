package com.utilitymod.echo;

import com.utilitymod.echo.api.Accessors;
import com.utilitymod.echo.event.EventManager;
import com.utilitymod.echo.event.impl.EventHandleInput;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.event.impl.EventPacketSend;
import com.utilitymod.echo.event.impl.EventRender2DGui;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventSlowdown;
import com.utilitymod.echo.event.impl.EventStartAttack;
import com.utilitymod.echo.event.impl.EventStartUseItem;
import com.utilitymod.echo.event.impl.EventSwingHand;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.event.impl.MouseUpdateEvent;
import com.utilitymod.echo.event.impl.SprintEvent;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.handlers.impl.HurtTickHandler;
import com.utilitymod.echo.handlers.impl.SprintController;
import com.utilitymod.echo.handlers.impl.SwapStateManager;
import com.utilitymod.echo.screens.frame.glass.GlassUIConstants;
import com.utilitymod.echo.utils.rotation.PitchUtil;
import com.utilitymod.inject.Hooks;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.StickyKeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.joml.Matrix4f;

/**
 * echo's mixin bodies, called from {@link Hooks} (patched bytecode). Each
 * method mirrors the echo mixin named in its comment and posts the same echo
 * event on the {@link EventManager} bus.
 */
public final class EchoEvents {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean initialized;
    private static Render3D lastFrame;

    // MixinKeyboardInput state
    private static boolean origForward, origBack, origLeft, origRight, origJump, origSneak, origSprint;
    private static boolean keyboardActive;

    private EchoEvents() {}

    public static Render3D lastFrame() {
        return lastFrame;
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        // echo HandlerManager: always-on handlers.
        EventManager.register(new HurtTickHandler());
        EventManager.register(new SprintController());
        EventManager.register(new SwapStateManager());

        // echo EventRender2DGui: HUD pass; projected Draw3D targets draw first.
        HudRenderCallback.EVENT.register((context, counter) -> {
            if (Hooks.PARALYZED) return; // destroyed: no client draws
            GlassUIConstants.syncTheme();
            com.utilitymod.echo.render.ShaderEsp.render(context);
            Echo.draw3d.getCustomTarget().drawProjected(context, lastFrame);
            Echo.draw3d.getMinecraftTarget().drawProjected(context, lastFrame);
            Echo.draw2D.begin(context);
            try {
                new EventRender2DGui(context, counter.getTickProgress(false), Echo.draw2D).call();
            } finally {
                Echo.draw2D.end();
            }
            com.utilitymod.gui.SelfTest.hud++;
            dev.koifih.client.ui.Theme.update(); // HUD / glass / toasts follow theme changes without a GUI open
            try { com.utilitymod.echo.features.impl.render.SkyArt.apply(); } catch (Throwable ignored) {}
            try { com.utilitymod.echo.features.impl.render.MotionBlur.apply(); } catch (Throwable ignored) {}
            try { com.utilitymod.echo.render.CustomParticles.render(context); } catch (Throwable ignored) {}
            com.utilitymod.anubis.AnubisBridge.hudOverlay(context);
            com.utilitymod.echo.hud.HudManager.render(context);
            try { com.utilitymod.gui.Welcome.render(context); } catch (Throwable ignored) {}
        });
    }

    // ---- MixinMinecraft --------------------------------------------------------------

    /** tick HEAD -> EventTick. */
    public static void tick() {
        com.utilitymod.gui.SelfTest.onTick();
        new EventTick().call();
        try { com.utilitymod.echo.handlers.ServerRotations.tickWindow(); } catch (Throwable ignored) {}
        try { com.utilitymod.anubis.AnubisBridge.tick(); } catch (Throwable ignored) {}
    }

    /** before handleKeybinds: Early (+ StartAttack.Pre when an attack click is queued), then Pre (cancellable). */
    public static boolean handleInputHead() {
        com.utilitymod.gui.SelfTest.input++;
        new EventHandleInput.Early().call();
        if (mc.player != null && mc.options != null && mc.options.attackKey != null
                && Accessors.getClickCount(mc.options.attackKey) > 0) {
            Entity target = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY
                    ? ((EntityHitResult) mc.crosshairTarget).getEntity() : null;
            new EventStartAttack.Pre(mc.player, target).call();
        }
        return new EventHandleInput.Pre().call();
    }

    /** handleKeybinds RETURN -> Post. */
    public static void handleInputReturn() {
        new EventHandleInput.Post().call();
    }

    /** startAttack HEAD: cancelled -> return false. */
    public static boolean startAttack() {
        Entity target = null;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            target = ((EntityHitResult) mc.crosshairTarget).getEntity();
        }
        return new EventStartAttack(mc.player, target).call();
    }

    /** startUseItem HEAD (cancellable) / RETURN. */
    public static boolean startUseItemHead() {
        if (mc.player == null) return false;
        return new EventStartUseItem.Pre(mc.player, Hand.MAIN_HAND, mc.player.getMainHandStack(), mc.crosshairTarget).call();
    }

    public static void startUseItemReturn() {
        if (mc.player == null) return;
        new EventStartUseItem.Post(mc.player, Hand.MAIN_HAND, mc.player.getMainHandStack(), mc.crosshairTarget).call();
    }

    // ---- MixinMultiPlayerGameMode attack HEAD/TAIL ----------------------------------------

    public static boolean attackPre(Object player, Object target) {
        return new EventOnAttackEntity.Pre((PlayerEntity) player, (Entity) target).call();
    }

    public static void attackPost(Object player, Object target) {
        new EventOnAttackEntity.Post((PlayerEntity) player, (Entity) target).call();
        if (player == mc.player) com.utilitymod.echo.utils.combat.KillTracker.onAttack((Entity) target);
    }

    // ---- MixinKeyboardInput tick HEAD/RETURN ------------------------------------------

    private static void setKeyState(KeyBinding key, boolean desired, boolean toggleEnabled) {
        if (key instanceof StickyKeyBinding && toggleEnabled) {
            if (key.isPressed() != desired) key.setPressed(true);
            return;
        }
        key.setPressed(desired);
    }

    public static void keyboardTickHead() {
        GameOptions o = mc.options;
        if (o == null) return;
        origForward = o.forwardKey.isPressed();
        origBack = o.backKey.isPressed();
        origLeft = o.leftKey.isPressed();
        origRight = o.rightKey.isPressed();
        origJump = o.jumpKey.isPressed();
        origSneak = o.sneakKey.isPressed();
        origSprint = o.sprintKey.isPressed();
        keyboardActive = true;

        EventMovementInput e = new EventMovementInput(origForward, origBack, origLeft, origRight, origJump, origSneak, origSprint);
        e.call();
        // Final suppression after every movement listener: AutoWalk/GuiWalk cannot move the body.
        if (com.utilitymod.echo.features.impl.movement.Freecam.isActive()) {
            e.forward = e.back = e.left = e.right = e.jump = e.sneak = e.sprint = false;
        }
        e.syncFloatsFromBooleans();

        o.forwardKey.setPressed(e.forwardAmount > 0);
        o.backKey.setPressed(e.forwardAmount < 0);
        o.leftKey.setPressed(e.sidewaysAmount > 0);
        o.rightKey.setPressed(e.sidewaysAmount < 0);
        o.jumpKey.setPressed(e.jump);
        setKeyState(o.sneakKey, e.sneak, o.getSneakToggled().getValue());
        setKeyState(o.sprintKey, e.sprint, o.getSprintToggled().getValue());
    }

    public static void keyboardTickReturn() {
        GameOptions o = mc.options;
        if (o == null || !keyboardActive) return;
        keyboardActive = false;
        o.forwardKey.setPressed(origForward);
        o.backKey.setPressed(origBack);
        o.leftKey.setPressed(origLeft);
        o.rightKey.setPressed(origRight);
        o.jumpKey.setPressed(origJump);
        setKeyState(o.sneakKey, origSneak, o.getSneakToggled().getValue());
        setKeyState(o.sprintKey, origSprint, o.getSprintToggled().getValue());
    }

    // ---- MixinLocalPlayer ---------------------------------------------------------------

    /** swing HEAD (cancellable). */
    public static boolean swing(Object player, Object hand) {
        return new EventSwingHand((PlayerEntity) player, (Hand) hand).call();
    }

    /** aiStep: isSlowDueToUsingItem() value. */
    public static boolean slowDueToItem(boolean original) {
        EventSlowdown e = new EventSlowdown(1.0f, original, false, false);
        e.call();
        return !e.cancelled && e.isSlowDueToItem();
    }

    /** modifyInput: itemUseSpeedMultiplier() value. */
    public static float itemUseSpeed(float original) {
        EventSlowdown e = new EventSlowdown(original, false, false, false);
        e.call();
        return e.cancelled ? 1.0f : e.getSlowdownMultiplier();
    }

    /** modifyInput: isMovingSlowly() value. */
    public static boolean movingSlowly(boolean original) {
        EventSlowdown e = new EventSlowdown(1.0f, false, original, false);
        e.call();
        return !e.cancelled && e.isMovingSlowly();
    }

    /** isPassenger() redirect (aiStep ordinal 1, modifyInput). */
    public static boolean passenger(Object player) {
        if (((Entity) player).hasVehicle()) return true;
        EventSlowdown e = new EventSlowdown(1.0f, false, false, false);
        e.call();
        return e.isSpoofPassenger();
    }

    /** sendPosition HEAD: EventMove.Pre; values feed the MODIFY_RESULT spoof hooks. Cancel skips the send. */
    public static boolean sendPositionHead(Object p) {
        ClientPlayerEntity player = (ClientPlayerEntity) p;
        float enforcedPitch = PitchUtil.clampPitch(player.getPitch());
        if (!RotationHandler.isHasSilentRotation()) {
            RotationHandler.updateServerRotations(player.getYaw(), enforcedPitch);
        }
        EventMove e = new EventMove.Pre(player.getX(), player.getY(), player.getZ(), player.isOnGround(),
                player.getYaw(), enforcedPitch);
        e.call();
        if (e.cancelled) return true;
        Hooks.spoofX = e.getX();
        Hooks.spoofY = e.getY();
        Hooks.spoofZ = e.getZ();
        Hooks.spoofYaw = (float) e.getYaw();
        Hooks.spoofPitch = PitchUtil.clampPitch((float) e.getPitch());
        Hooks.spoofGround = e.isOnGround();
        return false;
    }

    public static void sendPositionReturn(Object p) {
        ClientPlayerEntity player = (ClientPlayerEntity) p;
        new EventMove.Post(player.getX(), player.getY(), player.getZ(), player.isOnGround(),
                player.getYaw(), player.getPitch()).call();
    }

    // ---- MixinKeymapping isDown (sprint key) ------------------------------------------

    public static boolean keyPressed(boolean vanilla, Object binding) {
        if (mc.options == null || mc.player == null || binding != mc.options.sprintKey) return vanilla;
        SprintEvent e = new SprintEvent(vanilla);
        e.call();
        return e.isOverridden() ? e.shouldSprint() : vanilla;
    }

    // ---- MixinMouseHandler turnPlayer HEAD -------------------------------------------------

    /**
     * MixinMouseHandler turnPlayer HEAD (echo MouseUpdateEvent). The event carries the deltas
     * vanilla is about to apply to the player's look, computed exactly like Mouse.updateMouse:
     * delta * (sens*0.6+0.2)^3 * 8 (or *1 for the spyglass branch). Freecam consumes them for its
     * own camera; cancelling skips vanilla's changeLookDirection so the body stays still.
     */
    public static boolean mouseUpdate(Object mouse, double timeDelta) {
        double dx = 0.0, dy = 0.0;
        try {
            if (mouse != null) {
                if (mouse instanceof net.minecraft.client.Mouse m) {
                    dx = Accessors.field(net.minecraft.client.Mouse.class, LT.L1, LT.L2).getDouble(m);
                    dy = Accessors.field(net.minecraft.client.Mouse.class, LT.L3, LT.L4).getDouble(m);
                }
            }
        } catch (Throwable ignored) {
            // field drift: fall back to zero deltas, Freecam then can't turn but never crashes
        }

        double i = dx, j = dy;
        try {
            if (mc.options != null && mc.player != null) {
                double d = mc.options.getMouseSensitivity().getValue() * 0.6F + 0.2F;
                double e = d * d * d;
                double f = e * 8.0;
                if (mc.options.smoothCameraEnabled) {
                    i = dx * f;
                    j = dy * f;
                } else if (mc.options.getPerspective().isFirstPerson() && mc.player.isUsingSpyglass()) {
                    i = dx * e;
                    j = dy * e;
                } else {
                    i = dx * f;
                    j = dy * f;
                }
                if (mc.options.getInvertMouseX().getValue()) i = -i;
                if (mc.options.getInvertMouseY().getValue()) j = -j;
            }
        } catch (Throwable ignored) {
            // keep raw deltas on any drift
        }

        MouseUpdateEvent e = new MouseUpdateEvent(i, j);
        e.call();
        return e.cancelled;
    }

    // ---- MixinConnection --------------------------------------------------------------------

    /** genericsFtw HEAD (netty or main thread, as in echo). true = drop the packet. */
    public static boolean packetReceive(Object packet) {
        if (!(packet instanceof Packet<?> p)) return false;
        com.utilitymod.gui.SelfTest.packetIn++;
        boolean drop = new EventPacketReceive(p).call();
        com.utilitymod.echo.utils.combat.KillTracker.onPacket(p);
        try { drop |= com.utilitymod.anubis.AnubisBridge.packetReceive(p); } catch (Throwable ignored) {}
        return drop;
    }

    /** send HEAD. true = drop the packet. */
    public static boolean packetSend(Object packet) {
        if (!(packet instanceof Packet<?> p)) return false;
        com.utilitymod.gui.SelfTest.packetOut++;
        if (heldSlotChanged(p)) return true;
        boolean drop = new EventPacketSend(p).call();
        try { drop |= com.utilitymod.anubis.AnubisBridge.packetSend(p); } catch (Throwable ignored) {}
        return drop;
    }

    // GrimAC BadPacketsA ("Sent duplicate slot id") / TotemGuard ProtocolB ("Invalid
    // slot change"): a vanilla client never announces the same held slot twice in a
    // row. Raw silent swaps and cpvp anchor cycles re-announce slot X at the same tick
    // cadence, so consecutive duplicate requests are dropped at the wire. The server
    // state is untouched - a duplicate announce carries no new information.
    private static volatile net.minecraft.client.world.ClientWorld seenWorld;
    private static volatile int lastAnnouncedHeldSlot = -1;

    private static boolean heldSlotChanged(Packet<?> p) {
        if (!(p instanceof UpdateSelectedSlotC2SPacket slotPacket)) return false;
        if (mc.world != seenWorld) {
            seenWorld = mc.world;
            lastAnnouncedHeldSlot = -1;
        }
        int slot = slotPacket.getSelectedSlot();
        if (slot == lastAnnouncedHeldSlot) return true;
        lastAnnouncedHeldSlot = slot;
        return false;
    }

    // ---- MixinGameRenderer renderLevel -> EventRender3D -----------------------------------

    public static void worldRender(Object camera, Object tickCounter, Object view, Object projection) {
        try {
            com.utilitymod.gui.SelfTest.worldRender++;
            Camera cam = (Camera) camera;
            float delta = ((RenderTickCounter) tickCounter).getTickProgress(false);
            Matrix4f v = new Matrix4f((Matrix4f) view);
            Matrix4f pr = new Matrix4f((Matrix4f) projection);
            lastFrame = new Render3D(cam.getCameraPos(), v, pr, delta);
            Echo.draw3d.updateCamera(v, pr);
            Echo.draw3d.beginFrame();
            new EventRender3D(delta, cam, pr, v, Echo.draw3d).call();
            com.utilitymod.echo.render.Ghosts.render(Echo.draw3d.getCustomTarget());
            Echo.draw3d.endFrame();
            try { com.utilitymod.anubis.AnubisBridge.render3D(delta); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            lastFrame = null;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f3d,0x6732,0x1b61,0x4f73,0xc745,0x98ae,0xbc19,0x291a,0x5cb7,0x407c}, new char[]{0x6754,0x4015,0x4f16,0xbc75,0x297e,0x5ce8,0x989f,0xc772,0x1b59,0x6f04}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3963,0x12d7,0x7f42,0x96f5,0x5872,0x4da3,0x3b82,0x41ba,0xf534,0x7e88,0xd0fc,0xa7a7}, new char[]{0xa7c4,0x3916,0x41c8,0x7f31,0x969a,0x5800,0xd0b8,0x7eed,0x4dcf,0x12a3,0x3be3,0xf56c}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4150,0x490e,0xc57d,0x50a4,0xd027,0x3cd5,0x9b15,0x92d9,0xfd97,0x63a6}, new char[]{0x4136,0x3cbc,0x92bc,0x4962,0x50c0,0xfdc8,0xc54c,0xd010,0x639e,0x9b22}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8f74,0x26a0,0xa7a5,0x7fd1,0xe447,0xf95a,0x1326,0xaf15,0xbb93,0xc84f,0xa383,0xbb09}, new char[]{0x26c3,0xa3f6,0xc83d,0xbbe0,0xaf7a,0xe435,0xbb4d,0xf93f,0x7fbd,0xa7d1,0x1347,0x8f2d}).plainString();
    }
}
