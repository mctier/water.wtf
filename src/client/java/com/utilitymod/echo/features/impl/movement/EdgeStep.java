package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.event.impl.EventStartUseItem;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import dev.potato.spud.api.ProtectedText;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/** Automatic edge sneaking; hold Use with a main-hand block for placement. */
public final class EdgeStep extends Feature {
    private final BoolSetting edgeSneak = new BoolSetting(LT.L1, true);
    private final BoolSetting autoPlace = new BoolSetting(LT.L2, true);
    private final IntSetting delayPlace = new IntSetting(
            ProtectedText.fromInts(new int[]{0x4E,0x45,0x11,0xD8,0xA7,0x2D,0x10,0xFA,0xCA,0x90,0x41}),
            2, 1, 6, LT.L3);
    private final PlacementGate placementGate = new PlacementGate();
    private Object lastWorld;
    private Object lastPlayer;

    public EdgeStep() {
        super(new FeatureInfo(
                // encrypted new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11f5,0xa8aa,0xbc1d,0x226b,0x1c96,0x82c4,0x0b2e,0xd13c,0xb14d,0x9624,0x379e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString()
                ProtectedText.fromInts(new int[]{0x58,0x48,0x03,0xCF,0xE2,0x4F,0x26,0xF6,0xC2,0x96,0x5D}),
                LT.L4,
                Category.MOVEMENT));
    }

    private boolean movementActive() {
        return !isNull() && mc.currentScreen == null && mc.interactionManager != null
                && mc.player.isAlive() && !mc.player.isSpectator() && mc.player.isOnGround()
                && !mc.player.getAbilities().flying && !mc.player.hasVehicle()
                && !mc.player.isTouchingWater() && !mc.player.isInLava() && !mc.player.isClimbing();
    }

    private boolean active() {
        return movementActive() && !mc.player.isUsingItem() && mc.options.useKey.isPressed()
                && !mc.options.attackKey.isPressed()
                && !mc.player.getMainHandStack().isEmpty()
                && mc.player.getMainHandStack().getItem() instanceof BlockItem;
    }

    private BlockHitResult placementTarget() {
        if (!active() || !(mc.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || hit.getSide().getAxis() == Direction.Axis.Y
                || hit.getBlockPos().getY() >= mc.player.getY()) return null;
        var next = hit.getBlockPos().offset(hit.getSide());
        double reach = mc.player.getBlockInteractionRange();
        if (!mc.world.getWorldBorder().contains(next)
                || !mc.world.getBlockState(next).isReplaceable()
                || mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > reach * reach) return null;
        // Do not auto-open storage while aiming along its side.
        if (mc.world.getBlockEntity(hit.getBlockPos()) != null) return null;
        return hit;
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (mc.world != lastWorld || mc.player != lastPlayer) {
            placementGate.reset();
            lastWorld = mc.world;
            lastPlayer = mc.player;
        }
        if (!autoPlace.getValue() || !active()) {
            placementGate.reset();
            return;
        }
        BlockHitResult hit = placementTarget();
        if (hit == null || !placementGate.tryAcquire(mc.world.getTime(), delayPlace.getValue())) return;
        var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    @EventSubscribe
    private void onUse(EventStartUseItem.Pre event) {
        // The vanilla held-use loop must not add extra attempts between our ticks.
        if (autoPlace.getValue() && placementTarget() != null) event.cancel();
    }

    @EventSubscribe(priority = EventSubscribe.Priority.LOWEST)
    private void onMovement(EventMovementInput event) {
        // Edge protection is independent of held items, Use, and Auto Place.
        if (!edgeSneak.getValue() || !movementActive() || event.jump) return;
        double forward = EventMovementInput.getMovementMultiplier(event.forward, event.back);
        double sideways = EventMovementInput.getMovementMultiplier(event.left, event.right);
        double length = Math.hypot(forward, sideways);
        double dx = 0, dz = 0;
        if (length > 0) {
            double yaw = Math.toRadians(mc.player.getYaw());
            dx = (sideways * Math.cos(yaw) - forward * Math.sin(yaw)) / length * 0.2;
            dz = (forward * Math.cos(yaw) + sideways * Math.sin(yaw)) / length * 0.2;
        }
        Box feet = mc.player.getBoundingBox();
        Box support = new Box(feet.minX + 0.05, feet.minY - 0.15, feet.minZ + 0.05,
                feet.maxX - 0.05, feet.minY, feet.maxZ - 0.05).offset(dx, 0, dz);
        if (!mc.world.getBlockCollisions(mc.player, support).iterator().hasNext()) {
            event.sneak = true;
            event.sprint = false;
        }
        // Never clear manual sneak or mutate the persistent key state.
    }

    @Override
    public void onEnable() {
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }

    private void reset() {
        placementGate.reset();
        lastWorld = null;
        lastPlayer = null;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5be1,0xc011,0x825a,0x7983,0x9d16,0x3251,0x721e,0x23e3,0xdcc6,0xa15c}, new char[]{0x79c6,0x5b85,0x823d,0x3234,0x723e,0xc042,0xa132,0x9d73,0xdca7,0x2388}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x97f0,0xbd46,0x44a3,0x5f98,0x95bc,0xb889,0xf48d,0x485f,0x0ff0,0x5d18}, new char[]{0xbd07,0x482a,0x5d6c,0xf4e2,0xb8a9,0x44f3,0x0f9c,0x5ff9,0x95df,0x9795}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3dda,0x0ad6,0xf308,0x1534,0x71e1,0x7237}, new char[]{0x0af6,0x3dae,0x7188,0xf36b,0x155f,0x7244}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb045,0xdf6f,0x0871,0x05ec,0xaff2,0x84c0,0x4bc2,0xf368,0xa2a4,0x2044,0x489b,0x3bf7,0xf724,0x8818,0x019d,0x7570,0x14f2,0x3a20,0xa1f2,0x346b,0xc2e1,0xdf52,0xc4e3,0xf1ba,0x8c02,0x737e,0xc39a,0xea0d,0x3679,0xae4b,0xe4e0,0xe484,0x7ed9,0x727d,0xce1d,0x078d,0x235f,0xb53e,0xd4e4,0x81aa,0x2489,0x4259,0xab1e,0x47f6,0x0645,0xece8,0x7856,0xf23c,0x724a,0x4187,0x3390,0xbd5f,0x075c,0xb870,0xc2be,0x9616,0x31ca,0x363c,0xc0b7,0x4cf0,0xceb1}, new char[]{0x7531,0x886d,0x360d,0x062a,0xf251,0xbd3e,0x3648,0x3402,0x31a9,0x4c91,0x41eb,0x967a,0xec91,0x726a,0x33e3,0xea63,0xc2db,0x4238,0xa2cf,0xc3ba,0x01fc,0xab6a,0xe4a4,0xced4,0x4ba6,0x058b,0xd481,0x232c,0x47cd,0xf704,0x783e,0xf1d5,0x07e1,0x24ed,0x735e,0xb56b,0xe493,0xdf0a,0x3a00,0x0728,0xae24,0x8c22,0xc291,0x081d,0xdf33,0x81c9,0xaf97,0x7ef9,0x48f9,0xb037,0xce74,0x84a4,0xb817,0x7218,0xa1d2,0x3b95,0xc48f,0x149d,0xf30b,0xc0dc,0x2037}).toString();
    }
}
