package com.utilitymod.inject;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.features.impl.movement.Freecam;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Static targets called from patched Minecraft bytecode ({@link Patches}) and
 * from mixins when loaded from the mods folder, so both paths share logic.
 *
 * <p>Signatures must match the descriptors {@link Patcher} derives: reference
 * types are {@code Object}, primitives stay primitive. Modules subscribe
 * through the listener lists / override fields here instead of mixins.
 * Hooks never throw into game code.
 */
public final class Hooks {
    private Hooks() {}

    /**
     * Global kill switch flipped by ExitGate: every hook entry point that
     * has fired after this returns straight vanilla behaviour and the module
     * runtime no longer sees events. All listener lists are emptied.
     */
    public static volatile boolean PARALYZED = false;

    /** Wipes every listener list + override and flips {@link #PARALYZED}. Idempotent. */
    public static void detonate() {
        try {
            CLIENT_TICK.clear();
            ATTACK.clear();
            ATTACK_POST.clear();
            HUD.clear();
            MOVE_PRE.clear();
            MOVE_POST.clear();
            swingOverride = null;
            hitboxOverride = null;
            outlineOverride = null;
            outlineColor = null;
            hideVanillaCrosshair = null;
            hideVanillaHotbar = null;
            hideVanillaEffects = null;
            skinOverride = null;
            fovMultiplier = null;
            timerSpeed = 1.0f;
            spoofX = spoofY = spoofZ = Double.NaN;
            spoofYaw = spoofPitch = Float.NaN;
            spoofGround = null;
            PARALYZED = true;
        } catch (Throwable ignored) {
        }
    }

    // ---- listener registries ------------------------------------------------

    /** Fired at MinecraftClient.tick HEAD with the client instance. */
    public static final List<Consumer<Object>> CLIENT_TICK = new CopyOnWriteArrayList<>();
    /** Attack listeners: (player, target) -> true cancels the attack. */
    public static final List<AttackListener> ATTACK = new CopyOnWriteArrayList<>();
    /** Fired after an attack completes: (player, target). */
    public static final List<AttackListener> ATTACK_POST = new CopyOnWriteArrayList<>();
    /** HUD render, after vanilla: (DrawContext, RenderTickCounter). */
    public static final List<HudListener> HUD = new CopyOnWriteArrayList<>();
    /** Pre/post movement packet send (ClientPlayerEntity). */
    public static final List<Consumer<Object>> MOVE_PRE = new CopyOnWriteArrayList<>();
    public static final List<Consumer<Object>> MOVE_POST = new CopyOnWriteArrayList<>();

    /** Swing duration override: (living entity, vanilla ticks) -> ticks. Null = vanilla. */
    public static volatile SwingOverride swingOverride;
    /** Hitbox override for crosshair/projectile picking: entity -> Box. Null = vanilla. */
    public static volatile Function<Object, Object> hitboxOverride;

    /** Movement packet spoof; set during MOVE_PRE, cleared after send. NaN / null = vanilla. */
    public static double spoofX = Double.NaN, spoofY = Double.NaN, spoofZ = Double.NaN;
    public static float spoofYaw = Float.NaN, spoofPitch = Float.NaN;
    public static Boolean spoofGround;

    @FunctionalInterface public interface AttackListener { boolean on(Object player, Object target); }
    @FunctionalInterface public interface HudListener { void on(Object drawContext, Object tickCounter); }
    @FunctionalInterface public interface SwingOverride { int ticks(Object entity, int vanilla); }

    // ---- hook targets (descriptors fixed by Patches) ------------------------

    public static boolean cancelHurtTilt() {
        if (PARALYZED) return false;
        try {
            Module m = ModuleManager.get(LT.L1);
            return m != null && m.isEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void clientTickHead(Object client) {
        if (PARALYZED) return;
        for (Consumer<Object> l : CLIENT_TICK) safe(() -> l.accept(client));
        safe(com.utilitymod.echo.EchoEvents::tick);
    }

    public static boolean attackHead(Object gameMode, Object player, Object target) {
        if (PARALYZED) return false;
        boolean cancel = false;
        for (AttackListener l : ATTACK) {
            try {
                cancel |= l.on(player, target);
            } catch (Throwable t) {
                log(t);
            }
        }
        return cancel | bool(() -> com.utilitymod.echo.EchoEvents.attackPre(player, target));
    }

    public static void attackTail(Object gameMode, Object player, Object target) {
        if (PARALYZED) return;
        for (AttackListener l : ATTACK_POST) safe(() -> l.on(player, target));
        safe(() -> com.utilitymod.echo.EchoEvents.attackPost(player, target));
    }

    /** LivingEntityRenderer.updateRenderState RETURN: show the server-side look on your own model (visual only). */
    public static void livingRenderState(Object self, Object entity, Object state, float tickDelta) {
        if (PARALYZED) return;
        safe(() -> {
            if (entity != net.minecraft.client.MinecraftClient.getInstance().player) return;
            var st = (net.minecraft.client.render.entity.state.LivingEntityRenderState) state;
            if (st.flipUpsideDown) return;
            float[] r = com.utilitymod.echo.handlers.ServerRotations.renderRotation(tickDelta);
            if (r == null) return;
            st.relativeHeadYaw = net.minecraft.util.math.MathHelper.wrapDegrees(r[0] - st.bodyYaw);
            st.pitch = r[1];
        });
    }

    public static void hudRenderTail(Object hud, Object drawContext, Object tickCounter) {
        if (PARALYZED) return;
        for (HudListener l : HUD) safe(() -> l.on(drawContext, tickCounter));
    }

    /** ClientPlayerEntity.sendMovementPackets HEAD (cancellable, echo EventMove.Pre). */
    public static boolean sendPositionHead(Object player) {
        if (PARALYZED) return false;
        for (Consumer<Object> l : MOVE_PRE) safe(() -> l.accept(player));
        return bool(() -> com.utilitymod.echo.EchoEvents.sendPositionHead(player));
    }

    public static void sendPositionReturn(Object player) {
        if (PARALYZED) return;
        for (Consumer<Object> l : MOVE_POST) safe(() -> l.accept(player));
        safe(() -> com.utilitymod.echo.EchoEvents.sendPositionReturn(player));
        spoofX = spoofY = spoofZ = Double.NaN;
        spoofYaw = spoofPitch = Float.NaN;
        spoofGround = null;
    }

    public static double spoofX(double v) { return Double.isNaN(spoofX) ? v : spoofX; }
    public static double spoofY(double v) { return Double.isNaN(spoofY) ? v : spoofY; }
    public static double spoofZ(double v) { return Double.isNaN(spoofZ) ? v : spoofZ; }
    public static float spoofYaw(float v) { return Float.isNaN(spoofYaw) ? v : spoofYaw; }
    public static float spoofPitch(float v) { return Float.isNaN(spoofPitch) ? v : spoofPitch; }
    public static boolean spoofOnGround(boolean v) { Boolean g = spoofGround; return g == null ? v : g; }

    public static int swingDuration(int vanilla, Object entity) {
        if (PARALYZED) return vanilla;
        try {
            vanilla = com.utilitymod.echo.EchoMixins.swingDuration(vanilla);
        } catch (Throwable t) {
            log(t);
        }
        SwingOverride o = swingOverride;
        if (o == null) return vanilla;
        try {
            return o.ticks(entity, vanilla);
        } catch (Throwable t) {
            log(t);
            return vanilla;
        }
    }

    /** Redirect of Entity.getBoundingBox() inside ProjectileUtil.getEntityHitResult. */
    public static Object entityHitbox(Object entity) {
        if (PARALYZED) return ((net.minecraft.entity.Entity) entity).getBoundingBox();
        Object box = null;
        try {
            Object echoBox = com.utilitymod.echo.EchoMixins.hitbox((net.minecraft.entity.Entity) entity);
            if (echoBox != null) box = echoBox;
        } catch (Throwable t) {
            log(t);
        }
        if (box == null) {
            Function<Object, Object> o = hitboxOverride;
            if (o != null) {
                try {
                    Object b = o.apply(entity);
                    if (b != null) box = b;
                } catch (Throwable t) {
                    log(t);
                }
            }
        }
        if (box == null) return ((net.minecraft.entity.Entity) entity).getBoundingBox();
        // Grim-clean: an expanded box must never make an out-of-vanilla-reach target attackable.
        // If the vanilla box itself is beyond attack reach, ignore the expansion entirely - the
        // client then never even selects that entity, so no out-of-reach attack packet is sent.
        net.minecraft.client.MinecraftClient mz = net.minecraft.client.MinecraftClient.getInstance();
        if (mz == null || mz.player == null) return box;
        net.minecraft.entity.Entity e = (net.minecraft.entity.Entity) entity;
        net.minecraft.util.math.Box vanilla = e.getBoundingBox();
        net.minecraft.util.math.Vec3d eye = mz.player.getEyePos();
        double reach = mz.player.getEntityInteractionRange() + 0.001;
        double cx = Math.min(Math.max(eye.x, vanilla.minX), vanilla.maxX);
        double cy = Math.min(Math.max(eye.y, vanilla.minY), vanilla.maxY);
        double cz = Math.min(Math.max(eye.z, vanilla.minZ), vanilla.maxZ);
        if (eye.squaredDistanceTo(cx, cy, cz) > reach * reach) return vanilla;
        return box;
    }

    /** MinecraftClient.handleInputEvents HEAD (echo EventHandleInput.Early/Pre). true = skip input handling. */
    public static boolean handleInputHead(Object client) {
        if (PARALYZED) return false;
        return bool(com.utilitymod.echo.EchoEvents::handleInputHead);
    }

    /** MinecraftClient.handleInputEvents RETURN (echo EventHandleInput.Post). */
    public static void handleInputReturn(Object client) {
        if (PARALYZED) return;
        safe(com.utilitymod.echo.EchoEvents::handleInputReturn);
    }

    /** MinecraftClient.doAttack()Z HEAD (echo EventStartAttack): non-null = return that (FALSE). */
    public static Object startAttackHead(Object client) {
        if (PARALYZED) return null;
        return bool(com.utilitymod.echo.EchoEvents::startAttack) ? Boolean.FALSE : null;
    }

    /** MinecraftClient.doItemUse HEAD/RETURN (echo EventStartUseItem). */
    public static boolean startUseItemHead(Object client) {
        if (PARALYZED) return false;
        return bool(com.utilitymod.echo.EchoEvents::startUseItemHead);
    }

    public static void startUseItemReturn(Object client) {
        if (PARALYZED) return;
        safe(com.utilitymod.echo.EchoEvents::startUseItemReturn);
    }

    /** KeyboardInput.tick HEAD/RETURN (echo EventMovementInput). */
    public static void keyboardTickHead(Object input) {
        if (PARALYZED) return;
        safe(com.utilitymod.echo.EchoEvents::keyboardTickHead);
    }

    public static void keyboardTickReturn(Object input) {
        if (PARALYZED) return;
        safe(com.utilitymod.echo.EchoEvents::keyboardTickReturn);
    }

    /** ClientPlayerEntity.swingHand(Hand) HEAD (echo EventSwingHand). */
    public static boolean swingHead(Object player, Object hand) {
        if (PARALYZED) return false;
        return bool(() -> com.utilitymod.echo.EchoEvents.swing(player, hand));
    }

    /** echo EventSlowdown value hooks (tickMovement / applyMovementSpeedFactors). */
    public static boolean slowDueToItem(boolean v) {
        if (PARALYZED) return v;
        try {
            return com.utilitymod.echo.EchoEvents.slowDueToItem(v);
        } catch (Throwable t) {
            log(t);
            return v;
        }
    }

    public static float itemUseSpeed(float v) {
        if (PARALYZED) return v;
        try {
            return com.utilitymod.echo.EchoEvents.itemUseSpeed(v);
        } catch (Throwable t) {
            log(t);
            return v;
        }
    }

    public static boolean movingSlowly(boolean v) {
        if (PARALYZED) return v;
        try {
            return com.utilitymod.echo.EchoEvents.movingSlowly(v);
        } catch (Throwable t) {
            log(t);
            return v;
        }
    }

    /** Redirect of Entity.hasVehicle() (echo NoSlow passenger spoof). */
    public static boolean passenger(Object player) {
        if (PARALYZED) return ((net.minecraft.entity.Entity) player).hasVehicle();
        try {
            return com.utilitymod.echo.EchoEvents.passenger(player);
        } catch (Throwable t) {
            log(t);
            return ((net.minecraft.entity.Entity) player).hasVehicle();
        }
    }

    /** KeyBinding.isPressed RETURN (echo SprintEvent on the sprint key). */
    public static boolean keyPressed(boolean v, Object binding) {
        if (PARALYZED) return v;
        try {
            return com.utilitymod.echo.EchoEvents.keyPressed(v, binding);
        } catch (Throwable t) {
            log(t);
            return v;
        }
    }

    /** Mouse.updateMouse(double) HEAD (echo MouseUpdateEvent; cancel skips turning). */
    public static boolean mouseUpdateHead(Object mouse, double timeDelta) {
        if (PARALYZED) return false;
        return bool(() -> com.utilitymod.echo.EchoEvents.mouseUpdate(mouse, timeDelta));
    }

    /** static ClientConnection.handlePacket(Packet, PacketListener) HEAD (echo EventPacketReceive). */
    public static boolean packetReceiveHead(Object packet, Object listener) {
        if (PARALYZED) return false;
        return bool(() -> com.utilitymod.echo.EchoEvents.packetReceive(packet));
    }

    /** ClientConnection.send(Packet, ChannelFutureListener, boolean) HEAD (echo EventPacketSend). */
    public static boolean packetSendHead(Object connection, Object packet, Object listener, boolean flush) {
        if (PARALYZED) return false;
        return bool(() -> com.utilitymod.echo.EchoEvents.packetSend(packet));
    }

    /** ClientWorld.handleBlockUpdate HEAD / RETURN (Anubis BlockUpdateEvent). */
    public static void blockUpdateHead(Object world, Object pos, Object state, int flags) {
        if (PARALYZED) return;
        safe(() -> com.utilitymod.anubis.AnubisBridge.blockUpdateHead(world, pos, state));
    }

    public static void blockUpdateReturn(Object world, Object pos, Object state, int flags) {
        if (PARALYZED) return;
        safe(() -> com.utilitymod.anubis.AnubisBridge.blockUpdateReturn(world, pos, state));
    }

    /** ClientPlayNetworkHandler.sendChatMessage HEAD: true = handled locally (# commands). */
    public static boolean chatSendHead(Object handler, Object message) {
        if (PARALYZED) return false;
        return bool(() -> com.utilitymod.echo.features.impl.misc.AutoBlockMiner.onChat((String) message));
    }

    /** SkyRendering.renderCelestialBodies RETURN: Ambience sky (drawn before terrain). */
    public static void skyDrawn() {
        if (PARALYZED) return;
        safe(com.utilitymod.echo.features.impl.render.SkyArt::skyPass);
    }

    /** WorldRenderer.render(...) HEAD (echo EventRender3D): capture camera + matrices. */
    /** Camera.update RETURN: Freecam places its own camera after vanilla positions the player's. */
    public static void cameraUpdateReturn(Object self) {
        if (PARALYZED) return;
        try {
            Freecam.applyCamera((net.minecraft.client.render.Camera) self);
        } catch (Throwable t) {
            log(t);
        }
    }

    public static void worldRenderHead(Object self, Object allocator, Object tickCounter, boolean blockOutline,
                                       Object camera, Object positionMatrix, Object basicProjection,
                                       Object projectionMatrix, Object fogBuffer, Object fogColor, boolean sky) {
        if (PARALYZED) return;
        safe(() -> com.utilitymod.echo.EchoEvents.worldRender(camera, tickCounter, positionMatrix, projectionMatrix));
    }

    /** Force the entity outline (glow) shader: entity -> true. Null = vanilla. */
    public static volatile java.util.function.Predicate<Object> outlineOverride;
    /** Outline colour override: entity -> RGB, or -1 for vanilla team colour. */
    public static volatile java.util.function.ToIntFunction<Object> outlineColor;

    /** MinecraftClient.hasOutline(Entity) RETURN (echo shader ESP). */
    public static boolean entityOutline(boolean vanilla, Object client, Object entity) {
        if (PARALYZED) return vanilla;
        java.util.function.Predicate<Object> o = outlineOverride;
        if (vanilla || o == null) return vanilla;
        try {
            return o.test(entity);
        } catch (Throwable t) {
            log(t);
            return vanilla;
        }
    }

    /** Entity.getTeamColorValue() RETURN: outline colour for forced outlines. */
    public static int teamColor(int vanilla, Object entity) {
        if (PARALYZED) return vanilla;
        java.util.function.ToIntFunction<Object> o = outlineColor;
        if (o == null) return vanilla;
        try {
            int c = o.applyAsInt(entity);
            return c < 0 ? vanilla : c;
        } catch (Throwable t) {
            log(t);
            return vanilla;
        }
    }

    /** Game speed multiplier (echo Imports.setTimer: Timer / Speed). 1 = vanilla. */
    public static volatile float timerSpeed = 1.0f;

    /** RenderTickCounter.Dynamic: value stored into deltaTicks (echo MixinRenderTickCounterDynamic). */
    public static float timerDelta(float deltaTicks) {
        if (PARALYZED) return deltaTicks;
        float t = timerSpeed;
        return t == 1.0f || !(t > 0f) ? deltaTicks : deltaTicks * t;
    }

    // ---- echo entity/input mixins (EchoMixins) ----------------------------------

    /** Input.hasForwardMovement HEAD (Sprint omni-sprint): non-null overrides. */
    public static Object forwardImpulseHead(Object input) {
        if (PARALYZED) return null;
        try {
            return com.utilitymod.echo.EchoMixins.forwardImpulse(input);
        } catch (Throwable t) {
            log(t);
            return null;
        }
    }

    /** Entity.getRotationVec(float) HEAD: silent rotation view vector for the local player. */
    public static Object viewVectorHead(Object entity, float tickDelta) {
        if (PARALYZED) return null;
        try {
            return com.utilitymod.echo.EchoMixins.silentLook(entity);
        } catch (Throwable t) {
            log(t);
            return null;
        }
    }

    /** Entity.getHeadLookAngle HEAD (silent rotation). */
    public static Object headLookHead(Object entity) {
        if (PARALYZED) return null;
        try {
            return com.utilitymod.echo.EchoMixins.silentLook(entity);
        } catch (Throwable t) {
            log(t);
            return null;
        }
    }

    /** Entity.updateVelocity: getYaw redirect (vanilla movement). */
    public static float moveYaw(Object entity) {
        if (PARALYZED) return ((net.minecraft.entity.Entity) entity).getYaw();
        try {
            return com.utilitymod.echo.EchoMixins.moveYaw(entity);
        } catch (Throwable t) {
            log(t);
            return ((net.minecraft.entity.Entity) entity).getYaw();
        }
    }

    /** PlayerEntity.travel: getRotationVector redirect (vanilla swimming). */
    public static Object swimLook(Object player) {
        if (PARALYZED) return ((net.minecraft.entity.Entity) player).getRotationVector();
        try {
            return com.utilitymod.echo.EchoMixins.swimLook(player);
        } catch (Throwable t) {
            log(t);
            return ((net.minecraft.entity.Entity) player).getRotationVector();
        }
    }

    /** PlayerEntity.knockbackTarget: getYaw redirect (silent rotation attack yaw). */
    public static float attackYaw(Object player) {
        if (PARALYZED) return ((net.minecraft.entity.Entity) player).getYaw();
        try {
            return com.utilitymod.echo.EchoMixins.attackYaw(player);
        } catch (Throwable t) {
            log(t);
            return ((net.minecraft.entity.Entity) player).getYaw();
        }
    }

    /** PlayerEntity.knockbackTarget: setSprinting redirect (KeepSprint). */
    public static void attackSetSprinting(Object player, boolean sprinting) {
        if (PARALYZED) { ((net.minecraft.entity.Entity) player).setSprinting(sprinting); return; }
        try {
            com.utilitymod.echo.EchoMixins.attackSetSprinting(player, sprinting);
        } catch (Throwable t) {
            log(t);
            ((net.minecraft.entity.Entity) player).setSprinting(sprinting);
        }
    }

    /** LivingEntity.getRotationVector redirect in firework tick / elytra flight (vanilla movement). */
    public static Object livingLook(Object living) {
        if (PARALYZED) return ((net.minecraft.entity.Entity) living).getRotationVector();
        try {
            return com.utilitymod.echo.EchoMixins.livingLook(living);
        } catch (Throwable t) {
            log(t);
            return ((net.minecraft.entity.Entity) living).getRotationVector();
        }
    }

    /** LivingEntity.updateFallFlyingMovement: getPitch redirect (vanilla movement). */
    public static float elytraPitch(Object living) {
        if (PARALYZED) return ((net.minecraft.entity.Entity) living).getPitch();
        try {
            return com.utilitymod.echo.EchoMixins.elytraPitch(living);
        } catch (Throwable t) {
            log(t);
            return ((net.minecraft.entity.Entity) living).getPitch();
        }
    }

    /** LivingEntity.jump: addVelocityInternal redirect (vanilla sprint-jump). */
    public static void jumpBoost(Object living, Object vec) {
        if (PARALYZED) { ((net.minecraft.entity.LivingEntity) living).addVelocityInternal((net.minecraft.util.math.Vec3d) vec); return; }
        try {
            com.utilitymod.echo.EchoMixins.jumpBoost(living, vec);
        } catch (Throwable t) {
            log(t);
            ((net.minecraft.entity.LivingEntity) living).addVelocityInternal((net.minecraft.util.math.Vec3d) vec);
        }
    }

    /** WorldRenderer.drawEntityOutlinesFramebuffer HEAD: true = Shader ESP replaces vanilla's composite. */
    public static boolean outlineCompositeHead(Object worldRenderer) {
        if (PARALYZED) return false;
        return bool(com.utilitymod.echo.render.ShaderEsp::replaceVanillaComposite);
    }

    /** WorldRenderer.render: ShaderLoader.loadPostEffect redirect (only the ENTITY_OUTLINE call exists there). */
    public static Object outlinePostEffect(Object loader, Object id, Object set) {
        if (PARALYZED) return ((net.minecraft.client.gl.ShaderLoader) loader).loadPostEffect((net.minecraft.util.Identifier) id, (java.util.Set<net.minecraft.util.Identifier>) set);
        try {
            return com.utilitymod.echo.render.ShaderEsp.outlinePostEffect(loader, id, set);
        } catch (Throwable t) {
            log(t);
            return ((net.minecraft.client.gl.ShaderLoader) loader).loadPostEffect(
                    (net.minecraft.util.Identifier) id, (java.util.Set<net.minecraft.util.Identifier>) set);
        }
    }

    /** Custom crosshair module: true hides the vanilla crosshair. */
    public static volatile java.util.function.BooleanSupplier hideVanillaCrosshair;
    /** Zoom module: FOV multiplier (1 = vanilla). */
    public static volatile java.util.function.DoubleSupplier fovMultiplier;

    /** InGameHud.renderCrosshair HEAD (cancellable). */
    /** Set by Custom Cape: (vanilla SkinTextures, player) -> SkinTextures to use. */
    public static volatile java.util.function.BinaryOperator<Object> skinOverride;

    public static Object skinTextures(Object vanilla, Object player) {
        if (PARALYZED) return vanilla;
        java.util.function.BinaryOperator<Object> o = skinOverride;
        if (o == null) return vanilla;
        try {
            Object r = o.apply(vanilla, player);
            return r != null ? r : vanilla;
        } catch (Throwable t) {
            return vanilla;
        }
    }

    /** Set by the Glass Hotbar / Effects HUD modules: true = skip the vanilla element. */
    public static volatile java.util.function.BooleanSupplier hideVanillaHotbar, hideVanillaEffects;

    public static boolean hotbarHead(Object hud, Object context, Object counter) {
        if (PARALYZED) return false;
        java.util.function.BooleanSupplier s = hideVanillaHotbar;
        return s != null && bool(s::getAsBoolean);
    }

    public static boolean effectsHead(Object hud, Object context, Object counter) {
        if (PARALYZED) return false;
        java.util.function.BooleanSupplier s = hideVanillaEffects;
        return s != null && bool(s::getAsBoolean);
    }

    public static boolean crosshairHead(Object hud, Object context, Object counter) {
        if (PARALYZED) return false;
        java.util.function.BooleanSupplier s = hideVanillaCrosshair;
        return s != null && bool(s::getAsBoolean);
    }

    /** GameRenderer.getFov RETURN value. */
    public static float fovValue(float fov) {
        if (PARALYZED) return fov;
        java.util.function.DoubleSupplier s = fovMultiplier;
        if (s == null) return fov;
        try {
            double m = s.getAsDouble();
            return m > 0 && m != 1.0 ? (float) (fov * m) : fov;
        } catch (Throwable t) {
            log(t);
            return fov;
        }
    }

    // ---- helpers ---------------------------------------------------------------

    private static boolean bool(java.util.function.BooleanSupplier r) {
        try {
            return r.getAsBoolean();
        } catch (Throwable t) {
            log(t);
            return false;
        }
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            log(t);
        }
    }

    private static long lastLog;

    private static void log(Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastLog < 5000) return;
        lastLog = now;
        System.err.println(LT.L2 + t);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b95,0xb2c4,0x6981,0xe1e5,0x4860,0x6d00,0xff4f,0xacbb,0x62d5}, new char[]{0x482e,0xacd4,0x629d,0x69f4,0xb2b6,0x6d74,0xff0c,0x9bf4,0xe188}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5600,0xfd05,0x4340,0x0606,0x128a,0x4f6d,0x4f5a,0x2072,0x2407,0xe0a9,0x4739,0xd3ff,0x0308,0xf79d,0x39fc,0xb428,0xa9a2,0x88cf,0x16d8,0xa080,0xd8ef,0xc904,0x1877,0x116b,0x9a3e,0xe66b,0xd035,0x60fa,0xecc0,0x131b,0x2667,0x28e9,0x4cc5,0xb685,0xe136}, new char[]{0xd8b4,0xc951,0xd041,0x5669,0x9a52,0x201b,0xe0dd,0x1362,0x60b7,0x4f02,0x88ab,0x4f07,0x4719,0xb440,0xe604,0x1818,0x0363,0x4360,0x16b4,0x12e3,0x2474,0xfd71,0xd39a,0xb6eb,0x110e,0xf7ef,0x0626,0xe150,0x2606,0xeca9,0xa9ce,0x3999,0xa0e4,0x28d3,0x4ce5}).plainString();
    }
}
