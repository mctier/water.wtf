package com.utilitymod.echo.features.impl.combat;

import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.InputSimulationSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ItemPickerSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.utils.EncryptedString;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.combat.ExplosionUtils;
import com.utilitymod.echo.utils.combat.TargetUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.math.TimerUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Crystal family: Shard Rig - aurora law with the sheet's settings cloned on.
 * The belt is world-driven and stateless across ticks: every pass re-reads the
 * crosshair - hit any crystal at the crosshair or sharing the clicked block's box,
 * place obsidian on a non-base, place a crystal on the base. Separate ms gates for
 * attack/place/switch. No progress counter, so the belt cannot stall - the world
 * is the state machine.
 */
public class CrystalBurst extends Feature {

    // -- Label carriers (generated codec-faithful) --
    private static final EncryptedString K_NAME = new EncryptedString(new char[]{0x0b0e, 0x1c95, 0x82c4, 0x37da, 0xbc1b, 0xa8a3, 0xb12f, 0x2279, 0xd129}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2});
    private static final EncryptedString K_DESCRIPTION = new EncryptedString(new char[]{0xc5c8, 0x1857, 0x3347, 0x11f1, 0x1c81, 0x6545, 0x3fee, 0x0b2e, 0xd936, 0xfca7, 0xa42d, 0x76b5, 0x4a4a, 0x723c, 0xb9c2, 0x2adf, 0xf8d8, 0x50c0, 0x9637, 0x379e, 0x5f67, 0xbc1a, 0xf056, 0xcdee, 0x93b7, 0x226c, 0x8ae8, 0xe735, 0x04b4, 0x9e5f, 0x7edc, 0xeb8d, 0x6d7d, 0xa8a2, 0x82d4, 0xd132, 0xb16c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025, 0x4a6a, 0xc5bb, 0x5f08, 0xd959, 0x50ae, 0x2aff, 0xa44c, 0x3f9d, 0xb9e2, 0x3333, 0x8a80, 0x04d1, 0x9e26, 0x1877, 0x93c4, 0x6d15, 0xe75a, 0x7eab, 0xf8f8, 0x7249, 0xcd9e});
    private static final EncryptedString K_DELAY = new EncryptedString(new char[]{0x2274, 0xd13e, 0x37c7, 0xa8ae, 0xbc08}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be});
    private static final EncryptedString K_PLACE = new EncryptedString(new char[]{0x11e9, 0xa8a7, 0xbc0a, 0x2279, 0x1c9e, 0x82c8, 0x0b18, 0xd12a, 0xb12f, 0x9622, 0x37db}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190});
    private static final EncryptedString K_SWITCH = new EncryptedString(new char[]{0xeb98, 0x0b7c, 0x1c97, 0x962f, 0x2271, 0xd129, 0x11f1, 0x82e9, 0xa8bc, 0x37dd, 0xb167, 0xbc1d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1});
    private static final EncryptedString K_MS = new EncryptedString(new char[]{0xa8b8, 0xd117}, new char[]{0xd17a, 0xa8cb});
    private static final EncryptedString K_MODE = new EncryptedString(new char[]{0x227c, 0xa8a4, 0xd137, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
    private static final EncryptedString K_SINGLE = new EncryptedString(new char[]{0xa8a2, 0x9633, 0x82f9, 0x37d2, 0x0b7c, 0x1c93, 0x2276, 0xb16a, 0xd129, 0xbc0e}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
    private static final EncryptedString K_DOUBLE = new EncryptedString(new char[]{0xa8a4, 0x9633, 0x82f9, 0x37d2, 0x0b7c, 0x1c93, 0x226d, 0xb16a, 0xd13e, 0xbc0b}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
    private static final EncryptedString K_NONE = new EncryptedString(new char[]{0x2276, 0xa8a4, 0xd134, 0xbc0c}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
    private static final EncryptedString K_SILENT = new EncryptedString(new char[]{0x1c9d, 0x9637, 0xbc0c, 0x11f1, 0x76ba, 0x0b7c, 0xeb95, 0x37d0, 0xf056, 0xd129, 0x82ff, 0x2274, 0xfce8, 0xa8a2, 0xb17b, 0x655f}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025});
    private static final EncryptedString K_PERFECT = new EncryptedString(new char[]{0xd12a, 0x0b28, 0xb16c, 0x37db, 0x962a, 0x226a, 0xeb88, 0xfce0, 0x828d, 0xbc0f, 0xa8ae, 0x1ca6, 0x11fd, 0x6558}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87});
    private static final EncryptedString K_BUTTER = new EncryptedString(new char[]{0x0b3a, 0x1c8b, 0x82c1, 0x37db, 0xbc1d, 0xa8be, 0xb17d, 0x226c, 0xd138}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2});
    private static final EncryptedString K_SIM = new EncryptedString(new char[]{0x1c9f, 0x9636, 0xbc1c, 0x11fc, 0x76bb, 0x0b0f, 0xeb80, 0x37ca, 0xf04b, 0xd133, 0x82c4, 0x2268, 0xfcee, 0xa8a5, 0xb12f, 0x6542}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1, 0x6536, 0xfc87, 0x76d4, 0xf025});
    private static final EncryptedString K_SUICIDE = new EncryptedString(new char[]{0xeb84, 0x0b29, 0x1c91, 0x962a, 0x226c, 0xd13b, 0x11f4, 0x82c4, 0xa8a5, 0x379e, 0xb15c, 0xbc00}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1});
    private static final EncryptedString K_GHOST = new EncryptedString(new char[]{0xa8a5, 0x9637, 0x82c2, 0x379e, 0x0b34, 0x1c81, 0x226c, 0xb148, 0xd13b, 0xbc00}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643});
    private static final EncryptedString K_OBI = new EncryptedString(new char[]{0x226b, 0xd135, 0xb166, 0x0b3d, 0xbc00, 0x37da, 0xa8a9, 0x82c3}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad});
    private static final EncryptedString K_BED = new EncryptedString(new char[]{0x0b37, 0xd138, 0x37d1, 0xb16c, 0xbc1b, 0x227c, 0xa8ae}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c});
    private static final EncryptedString K_TARGETS = new EncryptedString(new char[]{0x0b2f, 0xd12e, 0x37db, 0xb17b, 0xbc0e, 0x226a, 0xa8aa}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c});
    private static final EncryptedString K_CORPSE = new EncryptedString(new char[]{0xeb8d, 0x0b32, 0x1c99, 0x962a, 0x2277, 0xd129, 0x11fc, 0x828d, 0xa8bf, 0x379e, 0xb160, 0xbc19}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190, 0xebe1});
    private static final EncryptedString K_BIND = new EncryptedString(new char[]{0x2276, 0xa8a2, 0xd138, 0xbc0d}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69});
    private static final EncryptedString K_DIAG = new EncryptedString(new char[]{0x2279, 0xd13e, 0xb160, 0x0b2f, 0xbc0e, 0x37d0, 0xa8a2, 0x82c8}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad});

    // -- Sheet settings --
    private final IntSetting placeDelay = new IntSetting(K_PLACE, 50, 0, 500, K_MS);
    private final IntSetting swapDelay = new IntSetting(K_SWITCH, 0, 0, 1000, K_MS);
    private final InputSimulationSetting inputSimulation = new InputSimulationSetting(K_SIM, false);
    private final BoolSetting antiSuicide = new BoolSetting(K_SUICIDE, true);
    private final BoolSetting antiGhost = new BoolSetting(K_GHOST, true);
    private final BoolSetting butterfly = new BoolSetting(K_BUTTER, false);
    private final BoolSetting obiAllowed = new BoolSetting(K_OBI, true);
    private final BoolSetting bedAllowed = new BoolSetting(K_BED, true);
    private final BoolSetting stopOnKill = new BoolSetting(K_CORPSE, false);
    private final ItemPickerSetting targets = new ItemPickerSetting(K_TARGETS);
    private final KeybindSetting activateKey = new KeybindSetting(K_BIND, 0x80000000 | 1);
    private final IntSetting delay = new IntSetting(K_DELAY, 30, 0, 200, K_MS);
    private final ModeSetting mode = new ModeSetting(K_MODE, K_SINGLE, K_SINGLE, K_DOUBLE, K_NONE);
    private final BoolSetting silentRotations = new BoolSetting(K_SILENT, false);
    private final BoolSetting perfectTiming = new BoolSetting(K_PERFECT, false);
    private final BoolSetting diagnose = new BoolSetting(K_DIAG, false);

    /** Fresh-spawn crystals younger than this read as ghost-spawn noise, never worth a hit. */
    private static final long GHOST_FRESH_MS = 150;

    private final TimerUtils stepTimer = new TimerUtils();
    private final TimerUtils placeTimer = new TimerUtils();
    private final TimerUtils switchTimer = new TimerUtils();
    private final WeakHashMap<EndCrystalEntity, Long> firstSeenMs = new WeakHashMap<>();
    private int burstCount;
    private int aimTicks;
    private int diagTicks;
    private boolean armed;
    private int lastActionTick = Integer.MIN_VALUE;

    /** MultiActionsF law: two actions may never share a tick batch - adjacent
     *  ticks at 50 ms still ride, which keeps the belt fast without a shared flush. */
    private boolean spaced() {
        if (lastActionTick != Integer.MIN_VALUE && mc.player.age - lastActionTick < 1) return false;
        lastActionTick = mc.player.age;
        return true;
    }
    private BlockPos lastPocket;
    private float lastPlaceYaw = Float.NaN;
    private float lastPlacePitch = Float.NaN;
    private boolean nudgeSign;
    private EndCrystalEntity sweetCrystal;
    private Vec3d sweetPoint;

    /** DuplicateRotPlace guard: a place only counts when the head has moved since the last one. */
    private boolean rotFresh() {
        if (Float.isNaN(lastPlaceYaw)) return true;
        return Math.abs(mc.player.getYaw() - lastPlaceYaw) > 0.01f
                || Math.abs(mc.player.getPitch() - lastPlacePitch) > 0.01f;
    }

    private void markRotPlaced() {
        lastPlaceYaw = mc.player.getYaw();
        lastPlacePitch = mc.player.getPitch();
    }

    public CrystalBurst() {
        super(new FeatureInfo(K_NAME, K_DESCRIPTION, Category.COMBAT));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        burstCount = 0;
        aimTicks = 0;
        armed = false;
        lastActionTick = Integer.MIN_VALUE;
        lastPocket = null;
        sweetCrystal = null;
        sweetPoint = null;
        lastPlaceYaw = Float.NaN;
        lastPlacePitch = Float.NaN;
        stepTimer.reset();
    }

    @Override
    public void onDisable() {
        if (RotationUtils.isControlledBy(this)) RotationUtils.resetState();
        burstCount = 0;
        aimTicks = 0;
        armed = false;
        lastActionTick = Integer.MIN_VALUE;
        lastPocket = null;
        sweetCrystal = null;
        sweetPoint = null;
        lastPlaceYaw = Float.NaN;
        lastPlacePitch = Float.NaN;
        super.onDisable();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (!isEnabled() || isNull()) return;
        if (mc.currentScreen != null || !mc.isWindowFocused()) return;

        boolean noBind = activateKey.getKey() == -1;
        boolean held = noBind ? butterfly.getValue() : InputHandler.isBindDown(activateKey.getKey());
        if (!held) {
            // release resets the burst AND the pocket - no ghost swings away from the fight
            armed = false;
            lastActionTick = Integer.MIN_VALUE;
            burstCount = 0;
            aimTicks = 0;
            lastPocket = null;
            lastPlaceYaw = Float.NaN;
            lastPlacePitch = Float.NaN;
            return;
        }
        if (stopOnKill.getValue() && corpseNearby()) return;
        // aurora arm law: the picker arms the press ONCE - once you're in, the belt
        // runs every lane with whatever hand holds (obi is ammo, not a weapon), the
        // per-tick re-check would disarm mid-swap and freeze the build
        if (!armed) {
            if (!weaponAllowed()) return;
            armed = true;
        }
        if (diagnose.getValue() && ++diagTicks >= 40) {
            diagTicks = 0;
            diagReport();
        }

        PlayerEntity enemy = closestEnemy();

        // LANE 1 - attack (aurora: crosshair entity, or a crystal sharing
        // the clicked block's box, beats any placement logic)
        EndCrystalEntity target = beltTarget();
        int cap = mode.is(K_NONE) ? 0 : mode.is(K_SINGLE) ? 1 : 2;
        if (target != null && cap > 0 && burstCount < cap && !isGhost(target)) {
            // Grim hitbox law: the attack ray must actually pass through the crystal.
            // no silent rots = only a crystal under the crosshair legally swings
            if (silentRotations.getValue()) {
                if (!aimAtCrystal(target)) return;
            } else if (!(mc.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == target)) {
                return;
            }
            if (!spaced()) return;
            attackCrystal(target);
            burstCount++;
            stepTimer.reset();
            return;
        }
        // burst done for this press - Single/Double wait for a release
        if (target != null && cap > 0 && burstCount >= cap) return;

        // LANE 2 - placement on whatever the crosshair block is right now
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        boolean baseAllowed = (state.getBlock() == Blocks.OBSIDIAN && obiAllowed.getValue())
                || (state.getBlock() == Blocks.BEDROCK && bedAllowed.getValue());

        if (baseAllowed) {
            // enemy standing on the base? a block placed there collides - no crystal either
            if (BlockUtils.willPlacedBlockPlacementCollideWithEntity(hit.getBlockPos(), Blocks.OBSIDIAN.getDefaultState())) return;
            if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) {
                if (!switchTimer.hasReached(swapDelay.getValue())) return;
                int slot = InventoryUtils.findItemWithPredicateInHotbar(stack -> stack.getItem() == Items.END_CRYSTAL);
                if (slot == -1) return;
                // no-packet law: the hotbar key press flows through vanilla (the
                // client-only slot set would lie to the server on the next interact)
                InventoryUtils.setInvSlot(slot, true);
                switchTimer.reset();
                return;
            }
            // FoxAddition blocks law: placements breathe at least 60 ms, so the
            // interval check can't read 44/60 on the wire; a frozen head nudges
            // a hair instead of stalling (DuplicateRotPlace law)
            if (!placeTimer.hasReached(Math.max(placeDelay.getValue(), 60))) return;
            if (!rotFresh()) {
                nudgeSign = !nudgeSign;
                mc.player.setYaw(mc.player.getYaw() + (nudgeSign ? 0.02f : -0.02f));
            }
            BlockPos pocket = hit.getBlockPos().offset(hit.getSide());
            if (crystalNear(pocket) != null) {
                lastPocket = pocket;
                return;
            }
            // air-only pocket: isReplaceable() is true for water/lava too, and a
            // liquid pocket is AirLiquidPlace territory - never worth the flag
            BlockState pocketState = mc.world.getBlockState(pocket);
            if (!pocketState.isAir()) return;
            // aurora law: the belt has no suicide gate - anti-suicide lives on the
            // sheet's Shard Hack module, not here; stalling the crystal looks weak
            if (!spaced()) return;
            placeCrystal(hit);
            markRotPlaced();
            lastPocket = pocket;
            placeTimer.reset();
            // sheet law: place-only mode flips itself off after the drop
            if (mode.is(K_NONE)) toggle();
            return;
        }

        // non-base: obi lane (bedrock list belongs to the base check above)
        if (!obiAllowed.getValue()) return;
        if (mc.player.getMainHandStack().getItem() != Items.OBSIDIAN) {
            if (!switchTimer.hasReached(swapDelay.getValue())) return;
            int slot = InventoryUtils.findItemWithPredicateInHotbar(stack -> stack.getItem() == Items.OBSIDIAN);
            if (slot == -1) return;
            InventoryUtils.setInvSlot(slot, true);
            switchTimer.reset();
            return;
        }
        if (!placeTimer.hasReached(Math.max(placeDelay.getValue(), 60))) return;
        // DuplicateRotPlace law spans both lanes - a frozen head nudges a hair
        // instead of stalling; the obi has to go down same-tick, hands-off
        if (!rotFresh()) {
            nudgeSign = !nudgeSign;
            mc.player.setYaw(mc.player.getYaw() + (nudgeSign ? 0.02f : -0.02f));
        }
        BlockPos pocket = hit.getBlockPos().add(
                hit.getSide().getOffsetX(), hit.getSide().getOffsetY(), hit.getSide().getOffsetZ());
        if (!mc.world.getBlockState(pocket).isAir()) return;
        if (!BlockUtils.willPlacedBlockPlacementCollideWithEntity(pocket, Blocks.OBSIDIAN.getDefaultState())) return;
        if (!spaced()) return;
        placeObi(hit);
        markRotPlaced();
        placeTimer.reset();
    }

    /** Aurora aiming law: every hit aims a randomized point inside the hitbox
     *  (x, y, z spread across the sweet spots), then the wire holds two ticks
     *  before the attack leaves. */
    private boolean aimAtCrystal(EndCrystalEntity crystal) {
        if (crystal != sweetCrystal || sweetPoint == null) {
            sweetCrystal = crystal;
            sweetPoint = crystal.getEntityPos().add(
                    mc.world.random.nextDouble() * 0.5 - 0.25,
                    mc.world.random.nextDouble() * 0.3 + 0.3,
                    mc.world.random.nextDouble() * 0.5 - 0.25);
            aimTicks = 0;
        }
        boolean ok = RotationUtils.aim(this)
                .priority(EventSubscribe.Priority.HIGH)
                .silent(true)
                .speed(720f)
                .aimType(RotationUtils.AimType.BLATANT)
                .to(sweetPoint);
        if (!ok) return false;
        return ++aimTicks >= 2;
    }

    private void placeObi(BlockHitResult hit) {
        if (inputSimulation.getValue()) {
            InputHandler.simulateClick(mc.options.useKey, inputSimulation);
            return;
        }
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void placeCrystal(BlockHitResult hit) {
        if (inputSimulation.getValue()) {
            InputHandler.simulateClick(mc.options.useKey, inputSimulation);
            return;
        }
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (aimTicks > 0) {
            aimTicks = 0;
            if (RotationUtils.isControlledBy(this)) RotationUtils.resetState();
        }
        if (inputSimulation.getValue()) {
            InputHandler.simulateClick(mc.options.attackKey, inputSimulation);
            return;
        }
        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private EndCrystalEntity crystalNear(BlockPos pocket) {
        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(6.0), c -> c.isAlive() && !c.isRemoved())) {
            if (crystal.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(pocket)) < 1.0) return crystal;
        }
        return null;
    }

    /** Aurora target law: the belt attacks what the crosshair holds, not what it
     *  remembers; box-sharing crystals need age on the client first or the attack
     *  names an entityId the server never got (BadPacketsW). The pocket backs up
     *  placement state only. */
    private EndCrystalEntity beltTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof EndCrystalEntity cross) {
            return cross;
        }
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            List<EndCrystalEntity> shared = mc.world.getEntitiesByClass(EndCrystalEntity.class,
                    new Box(hit.getBlockPos()), c -> c.isAlive() && !c.isRemoved() && c.age >= 3);
            if (!shared.isEmpty()) return shared.get(0);
        }
        return null;
    }

    private PlayerEntity closestEnemy() {
        PlayerEntity best = null;
        double bestSq = 100.0;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator() || TargetUtils.isFriend(player)) continue;
            double sq = mc.player.squaredDistanceTo(player);
            if (sq < bestSq) {
                bestSq = sq;
                best = player;
            }
        }
        return best;
    }

    private boolean corpseNearby() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (mc.player.squaredDistanceTo(player) < 36 && player.isDead()) return true;
        }
        return false;
    }

    /** Anti Ghost: crystals younger than the window never leave the belt. */
    private boolean isGhost(EndCrystalEntity crystal) {
        if (!antiGhost.getValue()) return false;
        long now = System.currentTimeMillis();
        Long seen = firstSeenMs.get(crystal);
        if (seen == null) {
            firstSeenMs.put(crystal, now);
            return true;
        }
        return now - seen < GHOST_FRESH_MS;
    }

    /** Targets whitelist: hand item must be picked on the attack lane; empty reads as free. */
    private boolean weaponAllowed() {
        Set<Identifier> selected = targets.getSelectedIds();
        if (selected.isEmpty()) return true;
        return selected.contains(Registries.ITEM.getId(mc.player.getMainHandStack().getItem()));
    }

    /** Blast would floor us below half a heart - vanilla crystal power is radius 6. */
    private boolean suicideRisk(Vec3d blastCenter) {
        if (!antiSuicide.getValue()) return false;
        float self = ExplosionUtils.getExplosionDamageTo(mc.player, blastCenter, 6.0f);
        return self >= mc.player.getHealth() - 1.0f;
    }

    /** Diagnose: one chat line every 2 s naming the belt and its aim state. */
    private void diagReport() {
        PlayerEntity enemy = closestEnemy();
        String hand = mc.player.getMainHandStack().getItem() == Items.OBSIDIAN ? LT.L1
                : mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL ? LT.L2 : LT.L3;
        String head = mc.crosshairTarget == null ? LT.L4 : mc.crosshairTarget.getType().name().toLowerCase();
        String focus = "";
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            focus = LT.L5 + hit.getBlockPos().toShortString();
        } else if (mc.crosshairTarget instanceof EntityHitResult hit) {
            focus = LT.L5 + hit.getEntity().getName().getString();
        }
        String line = LT.L6 + hand + LT.L7 + head + focus
                + LT.L8 + (lastPocket == null ? LT.L4 : lastPocket.toShortString())
                + LT.L9 + (lastPocket != null && crystalNear(lastPocket) != null)
                + LT.L10 + burstCount
                + LT.L11 + (enemy == null ? LT.L4 : enemy.getName().getString()
                        + (perfectTiming.getValue() && enemy.hurtTime != 0 ? LT.L12 : ""))
                + LT.L13 + (InventoryUtils.findItemWithPredicateInHotbar(stack -> stack.getItem() == Items.OBSIDIAN) != -1)
                + LT.L14 + (InventoryUtils.findItemWithPredicateInHotbar(stack -> stack.getItem() == Items.END_CRYSTAL) != -1);
        mc.player.sendMessage(net.minecraft.text.Text.literal(line), false);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd680,0xafdb,0x80c5}, new char[]{0xd6ef,0xafb9,0x80ac,0x8766}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ea8,0xed47,0x9e57,0x7a8f,0x8d28,0x7598,0x5790}, new char[]{0x75fb,0x8d5a,0xed3e,0x7afc,0x2edc,0x9e36,0x57fc}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd276,0x6dec,0xc4f0,0xcd45,0xf478}, new char[]{0xf417,0x6d98,0xc498,0xcd20,0xd204}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2c78,0xc89f,0x522b,0xc68b}, new char[]{0xc8f1,0x2c17,0x5245,0xc6ee}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x931c}, new char[]{0x935c,0xe66b,0x7272,0x86f0}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x786f,0x55dd,0x3bc5,0x4064,0xab88,0x7fc9,0x4a62,0x7bbb,0x5a0d,0x9eb9,0x010c}, new char[]{0x7f92,0x4a23,0x5595,0x9efa,0xabd5,0x784f,0x7bd3,0x4005,0x3bab,0x0168,0x5a30}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x81fc,0x20f1,0x8531,0x3f6b,0x31bf,0xe5cb,0x403f}, new char[]{0x319f,0x819f,0x2083,0x3f04,0x404c,0x8542,0xe5f6}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x30b8,0x2427,0x45ef,0x2a6d,0xaf32,0x3e3e,0x2b18,0xc801}, new char[]{0x3e1e,0x2457,0xc86e,0x2a0e,0x30d3,0xaf57,0x459b,0x2b25}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x272a,0x131f,0xeb15,0xc5ba,0x964d,0x056a,0xba53}, new char[]{0xeb35,0xba30,0x1351,0x274f,0xc5db,0x0518,0x9670}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x708f,0x36a0,0x5362,0x8795,0xf2b2,0xe085,0x0ccd}, new char[]{0xf292,0x0caf,0x87e0,0x36d2,0x5311,0x70fb,0xe0b8}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xac9c,0xbb0c,0x767a,0xd3db,0x542a,0x8c70,0xb52f}, new char[]{0xd3fb,0x8c15,0x5444,0xacf9,0xb542,0x7603,0xbb31}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb5da,0x7d99,0xa11f,0x8dfc,0x45f6,0xf3b2}, new char[]{0x8dd4,0x459e,0xb5af,0x7deb,0xa16b,0xf39b}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c60,0x3786,0x2e73,0x9cce,0xe784,0x180e,0xaa92,0x6769}, new char[]{0x2e53,0x6c08,0xe7e5,0x187d,0x37c9,0x9cac,0xaafb,0x6754}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f70,0x5cab,0x41f5,0x1b2a,0xc70b,0x397a,0x98ed,0x290d,0xa7a5,0x4035,0x4f7e,0xbc14}, new char[]{0x4015,0x4f16,0xbc75,0x297e,0x5ce8,0x989f,0xc772,0x1b59,0x6f04,0xa7c4,0x3916,0x41c8}).toString();
    }
}
