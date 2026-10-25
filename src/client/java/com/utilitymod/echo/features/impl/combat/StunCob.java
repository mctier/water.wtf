package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.event.impl.EventOnAttackEntity;
import com.utilitymod.echo.event.impl.EventPacketReceive;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.player.PlayerUtils;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Stun Cob: the moment a player's shield is disabled (entity status 30, or shield-break sound on
 * 1.21.5+ BlocksAttacksComponent shields), swap to a cobweb and place it in their feet space so
 * they are stuck while they cannot block.
 *
 * <h2>Grim notes</h2>
 * <ul>
 *   <li>Rotation goes through {@link RotationUtils#aim(Feature)} with silent routing — the
 *       vanilla flying packet carries the aimed yaw/pitch, so there is no raw
 *       {@code LookAndOnGround} send and Grim's {@code AimDuplicateLook} /
 *       {@code Post (block_place)} checks don't trip.</li>
 *   <li>The interact is held until {@link RotationUtils#hasSilentRotation()} is set and the
 *       server-side rotation matches the target within 0.5&deg;.</li>
 *   <li>Pauses while {@code Freecam} is active to avoid out-of-range desync.</li>
 * </ul>
 */
public class StunCob extends Feature {
    private static final byte SHIELD_DISABLED = 30;

    private final FloatSetting range = new FloatSetting(LT.L1, 4.5f, 2f, 6f, 0.1f);
    private final BoolSetting onlyMyHits = new BoolSetting(LT.L2, true);
    private final IntSetting delay = new IntSetting(LT.L3, 0, 0, 500);
    private final BoolSetting rotate = new BoolSetting(LT.L4, true);
    private final BoolSetting silent = new BoolSetting(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4D,0x40,0x1C,0xDE,0xAC,0x79,0x74,0xDE,0xCF,0x9C}), true, o -> rotate.getValue());
    private final BoolSetting swapBack = new BoolSetting(LT.L5, true);
    private final BoolSetting ignoreFriends = new BoolSetting(LT.L6, true);
    private final IntSetting maxAimTicks = new IntSetting(LT.L7, 2, 1, 20);
    private final BoolSetting debugSetting = new BoolSetting(LT.L8, false);
    private final BoolSetting smpOnly = new BoolSetting(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2248, 0xd129, 0xb161, 0x0b30, 0xbc49, 0x37d1, 0xa886, 0x82d4}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}), true);

    private Entity lastHit;
    private long lastHitAt;
    private PlayerEntity pending;
    private long pendingAt;

    private BlockHitResult pendingHit;
    private Vec3d pendingAim;
    private int aimTicks;

    public StunCob() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x226d,0xd129,0xb14c,0x0b33,0xbc07,0x379e,0xa8bf,0x82cf}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1857,0xa43c,0xb514,0xcded,0xd93c,0xb17c,0xc5d5,0xa8a7,0x82cc,0xf005,0x7269,0x9e55,0x002d,0xeb83,0xfce2,0x50dc,0xe735,0xc97f,0x37db,0x7ed8,0x93b3,0x0b7c,0x4305,0x5f6c,0x11ff,0xa1ab,0x3b63,0x9bf4,0x334a,0x04a3,0x8ae5,0xd2be,0x9620,0x76b6,0xdad5,0x0cc0,0x66ea,0x6d7d,0x4787,0xf41c,0xef19,0xd12a,0x4fa0,0xbc0a,0x1cd2,0xac47,0x8625,0xf89d,0xb983,0x2650,0x58e8,0xc155,0x2279,0x7a5f,0xe0b6,0x159c,0x2adf,0x6541,0x3ff1,0x4a1f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1}).toString(), Category.COMBAT));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RotationUtils.cleanup(this, silent.getValue());
        clearPending();
    }

    @EventSubscribe
    private void onAttack(EventOnAttackEntity.Post event) {
        lastHit = event.getTarget();
        lastHitAt = System.currentTimeMillis();
        if (lastHit instanceof PlayerEntity pl && pl.isBlocking()
                && mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem) {
            axeVictim = pl;
            axeAt = lastHitAt;
        }
    }

    /**
     * 1.21.5+ shields (BlocksAttacksComponent) no longer send entity status 30 when disabled:
     * the server sets the cooldown and plays the shield-break sound at the player. So the sound
     * packet is the trigger (status 30 kept for older servers / via-version proxies).
     */
    @EventSubscribe
    private void onPacket(EventPacketReceive event) {
        if (event.getPacket() instanceof PlaySoundS2CPacket p
                && p.getSound().matches(e -> e.equals(SoundEvents.ITEM_SHIELD_BREAK.value()))) {
            double x = p.getX(), y = p.getY(), z = p.getZ();
            mc.execute(() -> {
                if (isNull()) return;
                PlayerEntity best = null;
                double bestD = 2.5 * 2.5;
                for (PlayerEntity pl : mc.world.getPlayers()) {
                    if (pl == mc.player) continue;
                    double d = pl.squaredDistanceTo(x, y, z);
                    if (d < bestD) { bestD = d; best = pl; }
                }
                trigger(best, LT.L9);
            });
        } else if (event.getPacket() instanceof EntityStatusS2CPacket p && p.getStatus() == SHIELD_DISABLED) {
            mc.execute(() -> {
                if (!isNull() && p.getEntity(mc.world) instanceof PlayerEntity target) trigger(target, LT.L10);
            });
        }
    }

    private void trigger(PlayerEntity target, String source) {
        if (triggerGate()) return;
        if (target == null || target == mc.player) { debug(LT.L11); return; }
        if (ignoreFriends.getValue() && PlayerUtils.isFriend(target.getName().getString())) {
            debug(target.getName().getString() + LT.L12);
            return;
        }
        if (onlyMyHits.getValue() && (target != lastHit || System.currentTimeMillis() - lastHitAt > 1000)) {
            debug(target.getName().getString() + LT.L13);
            return;
        }
        if (pending == target) return;
        pending = target;
        pendingAt = System.currentTimeMillis();
        debug(LT.L14 + target.getName().getString() + LT.L15 + source + LT.L16);
    }

    /** Fallback: your axe hit a blocking player and they stopped blocking right after. */
    private PlayerEntity axeVictim;
    private long axeAt;

    private void debug(String msg) {
        if (debugSetting.getValue() && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8bf,0x9663,0x82cf,0x379e,0x0b33,0x1cc8,0x226d,0xb14c,0xd129,0xbc07}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).toString() + msg), false);
        }
    }

    /**
     * SMP scope law: StunCob only fires on the legendssmp host (the module's home arena);
     * anywhere else — including singleplayer — it stays inert unless the toggle is cleared.
     */
    private boolean triggerGate() {
        return smpOnly.getValue() && !onSmp();
    }

    private boolean onSmp() {
        net.minecraft.client.network.ServerInfo entry = mc.getCurrentServerEntry();
        return entry != null
                && entry.address != null
                && entry.address.toLowerCase(java.util.Locale.ROOT).contains(LT2.L1);
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        if (com.utilitymod.echo.features.impl.movement.Freecam.isActive()) {
            if (pendingHit != null) RotationUtils.cleanup(this, silent.getValue());
            clearPending();
            return;
        }

        if (axeVictim != null) {
            long since = System.currentTimeMillis() - axeAt;
            if (!axeVictim.isBlocking() && since > 30) { trigger(axeVictim, LT.L17); axeVictim = null; }
            else if (since > 400) axeVictim = null;
        }
        if (pending == null && pendingHit == null) return;

        if (pending != null && pendingHit == null) {
            if (System.currentTimeMillis() - pendingAt < delay.getValue()) return;
            PlayerEntity target = pending;
            pending = null;
            if (!target.isAlive() || target.isRemoved()) return;
            if (!armPlace(target)) return;
        }

        if (!rotate.getValue()) {
            performPending();
            return;
        }

        boolean okRotation = RotationUtils.aim(this)
                .priority(EventSubscribe.Priority.HIGH)
                .silent(silent.getValue())
                .speed(360f)
                .aimType(RotationUtils.AimType.BLATANT)
                .to(pendingAim);
        if (!okRotation) { performPending(); return; }

        aimTicks++;
        if (rotationReady() || aimTicks >= maxAimTicks.getValue()) performPending();
    }

    private boolean armPlace(PlayerEntity target) {
        BlockPos feet = BlockPos.ofFloored(target.getX(), target.getY() + 0.2, target.getZ());
        var state = mc.world.getBlockState(feet);
        if (state.isOf(Blocks.COBWEB)) { debug(LT.L18); return false; }
        if (!state.isReplaceable()) { debug(LT.L19); return false; }

        BlockHitResult hit = support(feet);
        if (hit == null) { debug(LT.L20); return false; }
        Vec3d eye = mc.player.getEyePos();
        if (eye.squaredDistanceTo(hit.getPos()) > range.getValue() * range.getValue()) { debug(LT.L21); return false; }

        pendingHit = hit;
        pendingAim = hit.getPos();
        aimTicks = 0;
        return true;
    }

    private boolean rotationReady() {
        if (!silent.getValue()) return true;
        if (!RotationUtils.hasSilentRotation()) return aimTicks >= 1;
        float[] want = RotationUtils.calculate(mc.player.getEyePos(), pendingAim);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(RotationHandler.getServerYaw() - want[0]));
        float pitchDiff = Math.abs(RotationHandler.getServerPitch() - want[1]);
        return yawDiff <= 0.5f && pitchDiff <= 0.5f;
    }

    private void performPending() {
        if (pendingHit == null) return;
        int web = InventoryUtils.findItemWithPredicateInHotbar(s -> s.isOf(Items.COBWEB));
        if (web == -1) { debug(LT.L22); clearPending(); return; }
        int previous = mc.player.getInventory().getSelectedSlot();
        if (web != previous) InventoryUtils.setInvSlot(web);
        try {
            var result = BlockUtils.interactWithBlock(pendingHit, true);
            debug(result.isAccepted() ? LT.L23 : LT.L24 + result + LT.L16);
        } finally {
            if (swapBack.getValue() && web != previous) InventoryUtils.setInvSlot(previous);
            clearPending();
        }
    }

    private void clearPending() {
        pendingHit = null;
        pendingAim = null;
        aimTicks = 0;
    }

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (!rotate.getValue() || !silent.getValue() || pendingHit == null) return;
        if (!RotationUtils.isControlledBy(this) || !RotationUtils.hasSilentRotation()) return;
        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    /** A solid face to click so the web lands in {@code feet}: the block below, else a side. */
    private BlockHitResult support(BlockPos feet) {
        BlockPos below = feet.down();
        if (mc.world.getBlockState(below).isSideSolidFullSquare(mc.world, below, Direction.UP)) {
            return new BlockHitResult(Vec3d.ofCenter(below).add(0, 0.5, 0), Direction.UP, below, false);
        }
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos side = feet.offset(dir);
            Direction face = dir.getOpposite();
            if (mc.world.getBlockState(side).isSideSolidFullSquare(mc.world, side, face)) {
                Vec3d c = Vec3d.ofCenter(side).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                return new BlockHitResult(c, face, side, false);
            }
        }
        return null;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf378,0xb112,0x770f,0xa3d6,0x0d87}, new char[]{0x775d,0xa3b7,0xf316,0x0de0,0xb177}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5b06,0x290c,0x165d,0xd7eb,0x05e7,0x3bf5,0xbd58,0x623f,0xb013,0x8288,0x54e8,0x70ba,0x1fa1}, new char[]{0x2943,0x1fcf,0x70d6,0xb06a,0x05c7,0x1610,0x82f1,0xbd78,0xd7b8,0x549c,0x3b80,0x6251,0x5b75}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6920,0x2b0a,0xe6cc,0x01f8,0xe8a8,0xe10c,0x7bcd,0x8667,0x5ff3,0x05ba}, new char[]{0x2b4e,0x7ba8,0xe6a0,0x6941,0xe175,0x01d8,0x864f,0xe8c5,0x05c9,0x5fda}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x043a,0x2e52,0x6141,0xd0c9,0x7860,0xa9ab}, new char[]{0x7832,0xd0a6,0x2e26,0xa9ca,0x044e,0x6124}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfa5f,0xf626,0x37a3,0xb1f0,0xd883,0xcc45,0xd935,0xaa27,0x14ad}, new char[]{0xaa74,0xb187,0xfa3e,0x14dd,0x3783,0xd977,0xd8e2,0xf645,0xcc2e}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcc13,0x57fd,0x4fa4,0x6d5f,0x8378,0xf5a8,0x47a3,0xcdcd,0x6524,0x07b6,0xb64f,0x1909,0x3701,0x323b}, new char[]{0x656d,0x831f,0xcc7d,0x5792,0xcdbf,0xb62a,0x3721,0x4fe2,0x47d1,0x07df,0x325e,0xf5c6,0x6d3b,0x197a}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x526c,0x8149,0xf769,0x21d1,0xdead,0xd175,0xf08c,0x8124,0x772c,0x8219,0x9b1e,0x2b4e,0x13ba}, new char[]{0x8169,0xd114,0x9b66,0x524c,0x13fb,0xf0e5,0x8274,0x2b6e,0xdef9,0x7745,0xf70a,0x8122,0x21a2}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf91b,0x6aa2,0xa774,0xb2ff,0x734d}, new char[]{0xa730,0xb29a,0xf979,0x6ad7,0x732a}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5579,0x276c,0xef27,0x5ed7,0x7df6,0x8b0c,0xddd7,0x6247,0x9fae,0x6aaa,0x8997,0xbb6f,0x17fe,0xcd90,0xa04a,0xd875,0xa7d9,0x21cd}, new char[]{0x9fdd,0x8b64,0xd81c,0xbb0a,0xa026,0x551d,0xef0a,0xcdf2,0xdda5,0x179b,0x89f6,0x2707,0x21ed,0x6ad9,0x5eb8,0x6232,0x7d98,0xa7bd}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2541,0xd8a8,0x323a,0x5705,0xa61d,0x16d7,0x457c,0xc77d,0x4822}, new char[]{0x4851,0x16a3,0x2520,0xc709,0x4509,0x5776,0xa63d,0x3209,0xd898}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11a7,0xa63b,0x562d,0x990c,0x65c1,0x3c75,0x6a92,0xd30e,0x283f,0x8d04,0xec2e,0xdded,0x704a,0x6a12,0x5459,0xfa25,0x7597,0x3a0b,0x115e,0x7cfe,0x4093,0x91ce,0x1b4f,0xbaf3,0xa55d,0xda9a,0x4db2,0x165e,0xd4a8,0x7cfa,0xb42e,0xf4c0,0xacc8,0x2332,0xfa0d,0xd11d,0xed16,0x3d0d}, new char[]{0xb45d,0x3d65,0xba9a,0x702f,0x91a2,0x6af6,0xddcd,0x2350,0x542b,0x3c1a,0xfa4e,0x75f2,0x3a2b,0x113c,0xfa78,0x7c8e,0xdaba,0x65af,0x2850,0x560d,0x997c,0xed7a,0xd36f,0x11de,0xec4b,0x7c8c,0x1b6f,0x1638,0x6a7d,0xa528,0x40fd,0xd179,0x4d92,0xf4b4,0x8d6c,0xd4cd,0xacba,0xa65e}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5141,0x984b,0x5ffb,0x67d5,0x1ac5,0xdc6e,0x0829,0x67b5,0xbf01,0x9369,0x74a1,0x136e}, new char[]{0x134e,0x0840,0x931a,0xdc4e,0x67d4,0x5fdb,0x67b3,0x1ab7,0x5128,0x982e,0xbf6f,0x74c5}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x83fb,0xadc1,0x05b7,0xef2d,0x610d,0x3954,0xa729,0x7fb4,0xede5,0x1fbf,0x4307,0x4e5a,0xef53,0xa20d,0xfb27,0xccfe,0x79d2,0x053c,0xf18a,0xc758,0x75ca,0xce98,0x7622,0xec8b,0x3d49,0x0fcf,0x24b3,0x7971,0xd3bb,0x369c,0x8db5,0xcb3e,0xca69,0x06ec,0x22e6,0x3333,0x79a9,0x332f,0xbada,0xc1f1,0x2f8a,0xc271,0x567b,0xa7ea,0xc6ca,0x3ab7,0x66fa}, new char[]{0xfb00,0x5608,0x83db,0x3927,0x79ba,0xd3d2,0x75af,0xca05,0xc6ae,0x051c,0x2284,0xa27f,0x3d26,0x791a,0xbabf,0xc25d,0x0fef,0x4e38,0x24c6,0x79dd,0xa709,0x4369,0xece4,0x05c3,0xedc5,0xc73a,0x334a,0xccde,0x7fcd,0x764d,0xadb4,0xef73,0x8d9d,0xced7,0x6694,0x0680,0xcb47,0x612d,0x36d1,0xef54,0xc1d1,0x1fec,0x3ac3,0x2fff,0xf1e4,0x335c,0xa7c3}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf3f6,0x1157,0xb95a,0xf5fd,0xa2cc,0x0c4b,0xc918,0xa3e3}, new char[]{0x1124,0xa397,0xf383,0xf593,0xc976,0xa2a9,0x0c2f,0xb97a}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeabb,0xf015}, new char[]{0xea9b,0xf03d,0xc3fc,0x584d}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3b50}, new char[]{0x3b79,0xd0fe,0xe979,0x6c01}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6c43,0xb2e8,0xb4bc,0xeb43,0x5c0d,0x930e,0x8603}, new char[]{0xeb22,0xb4c4,0x5c68,0x932e,0x6c2b,0xb281,0x8677}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfca9,0x1d82,0x57a8,0xbe85,0x25bb,0x2176,0x8b29,0x5a50,0xf453,0x2a62,0xb0fb,0xef3c,0xe8c9,0xc9c3,0x261d,0xb8f2}, new char[]{0xc9a2,0xb097,0x25c9,0xb897,0xbee4,0x2a06,0x1dfb,0xef1c,0x211f,0x5a3e,0xfc89,0x8b48,0x5788,0xe8be,0xf436,0x267f}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2bd1,0xa187,0x3be1,0x3fb8,0xee1a,0x4677,0x1732,0xc285,0x075b,0x8e9f,0xc730,0xc80d,0xcb87,0xe151,0x6c0b,0xff9b,0xec76,0xaac7,0xc678,0xf449,0xe5d4,0x0905,0xb010,0xdda2,0x2acf,0x6f01,0x979b,0x4bfe,0x88e7}, new char[]{0xa1f3,0x97f3,0x6c6e,0x8ef6,0x4b8c,0x0925,0xaaa1,0xcbe2,0xec13,0x8893,0xc2a5,0xddc0,0xfff7,0xe13e,0x1751,0xb07b,0xc82d,0x3b88,0xc60b,0xf469,0x4619,0xe5bb,0x072f,0xee3a,0xc755,0x2bbc,0x2abf,0x6f75,0x3fc1}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x08a7,0x108d,0x780b,0x6a4f,0x348b,0x93b8,0xe4f4,0x2718,0x537c,0xd58b,0x8b7d,0x2143,0x45e1,0x00f8,0x5b5b,0x732c,0xa073,0x5f83,0x62cb,0x39f7,0x77a0,0x9844,0xb8d3,0x6903,0x662b,0x0c20,0xd24e,0xf1f5,0x8156,0xe63b,0x2cbf,0x4cc2,0x28b5,0x94c1,0xc3f9,0xd873,0x6835,0x7069}, new char[]{0xd220,0x08c8,0xd807,0x28dd,0x212a,0xd5e5,0x94a6,0x34ab,0xe648,0x6a20,0x10e1,0x2771,0x5318,0x7780,0x62bf,0x2cd0,0xb8f3,0xc389,0x458d,0x7008,0x734f,0xe491,0x8176,0x3983,0x93d0,0x0c45,0x5b7b,0x9833,0x4ca7,0x5fe1,0x00d8,0xf194,0x8b1a,0xa012,0x7862,0x6645,0x6970,0x6841}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e35,0x1e9b,0x4d33,0xcaf3,0xdd2b,0xf763,0x048c,0xb5e5,0xa625,0x6824,0x81bf,0xdc35}, new char[]{0xca9c,0xdc40,0xf717,0xdd0b,0x8e5a,0x04ea,0x819f,0x4d41,0xa644,0xb58b,0x1efc,0x6841}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x86af,0xf0e4,0xb657,0x4532,0x0b97,0x20f0,0xaf5a,0x3213,0x41f1,0xf628,0x6ed1,0x8308,0x9992,0xf6cd,0xe2d4,0xeb21,0x913d,0xc2c6,0x403c,0xf1cd,0x95d2,0xf673,0xe7f7,0xaa00}, new char[]{0x95bc,0xf1a2,0x20d0,0x836b,0x6ebe,0xaf38,0x0be0,0xf616,0xe2b6,0x3233,0xc2af,0xf08a,0x868f,0x4188,0x4053,0xeb54,0x99e0,0xf608,0x9155,0xaa6f,0xb623,0xf6af,0xe796,0x4540}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1334,0x720f,0x885e,0xa268,0x4a1a,0xad0e}, new char[]{0x8829,0x726a,0xa20a,0x1356,0x4a7f,0xad6a}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4cb4,0xe3a7,0xab1a,0xf146,0xc2fc,0x06ad,0x332b,0x087f,0xb707,0x3109,0x07dc,0xffc2,0x33c3,0x79e3,0xc952,0x1083,0xb6a0,0xc646,0x624f}, new char[]{0xb6d0,0xb76b,0xf127,0xab79,0x10e6,0xc62b,0x06c8,0x798d,0x4cc0,0xffe2,0x3359,0x07b9,0x33a5,0xe3d2,0xc921,0x316c,0x622b,0xc2dc,0x0857}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7f5d,0x4dab,0x7e83,0x4146,0xf501,0x3b90,0x5867,0x12d0,0xd0dd,0x96ff}, new char[]{0x7f31,0x969a,0x5800,0xd0b8,0x7eed,0x4dcf,0x12a3,0x3be3,0xf56c,0x4136}).plainString();
    }
}
