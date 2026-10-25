package com.utilitymod.echo.features.impl.movement;

import net.minecraft.client.util.InputUtil;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.event.impl.EventPacketSend;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.handlers.impl.SprintController;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

public class GuiWalk extends Feature {

    private final Queue<Packet<?>> queuedPackets = new ConcurrentLinkedDeque<>();
    private boolean flushing = false;
    private boolean waitingToFlush = false;
    private int ticksStopped = 0;

    private KeyBinding[] movementKeys;

    private final ModeSetting mode = new ModeSetting(Concat.of(LT.L1), Concat.of(LT.L2),
            Concat.of(LT.L2),
            Concat.of(LT.L3)
    );

    @Override
    public void onDisable() {
        super.onDisable();
        flushPackets();
        waitingToFlush = false;
        ticksStopped = 0;
        SprintController.setForceStopSprint(false);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        queuedPackets.clear();
        waitingToFlush = false;
        ticksStopped = 0;
    }

    public GuiWalk() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2251,0xd13d,0xb16e,0x0b30,0xbc49,0x37e9,0xa89e,0x82c6}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad}).toString()),
                Concat.of(LT.L4),
                Category.MOVEMENT
        ));
    }

    private KeyBinding[] getMovementKeys() {
        if (movementKeys == null) {
            movementKeys = new KeyBinding[]{
                    mc.options.forwardKey,
                    mc.options.rightKey,
                    mc.options.leftKey,
                    mc.options.jumpKey,
                    mc.options.backKey,
            };
        }
        return movementKeys;
    }

    @EventSubscribe
    private void onUpdate(EventRender3D event) {
        if (isNull()) return;
        for (KeyBinding key : getMovementKeys()) {
            key.setPressed(isKeyPressed(key));
        }
    }

    public static boolean isKeyPressed(KeyBinding key) {
        return InputUtil.isKeyPressed(mc.getWindow(), key.getDefaultKey().getCode());
    }

    @EventSubscribe
    private void onPacketSend(EventPacketSend event) {
        if (isNull()) return;
        if (flushing) return;
        if (mode.is(LT.L2)) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClickSlotC2SPacket || packet instanceof CloseHandledScreenC2SPacket ||
        packet instanceof ChatMessageC2SPacket || packet instanceof CommandExecutionC2SPacket) {
            queuedPackets.add(packet);
            event.cancel();

            waitingToFlush = true;
            ticksStopped = 0;
            SprintController.setForceStopSprint(true);
            return;
        }

        if (waitingToFlush && packet instanceof PlayerMoveC2SPacket) {
            ticksStopped++;
            // We have to wait 1 tick to ensure the movement event knows that it has stopped since changing a field takes until the next
            // event/tick for an event to recognize it
            if (ticksStopped >= 1) {
                flushPackets();
                waitingToFlush = false;
                // We set this to false which makes it so that the next tick will free the movement of the player
                ticksStopped = 0;
                SprintController.setForceStopSprint(false);
            }
        }
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;

        if (!queuedPackets.isEmpty() && !waitingToFlush && !(com.utilitymod.echo.api.MinecraftCompat.getScreen() instanceof HandledScreen)) {
            waitingToFlush = true;
            ticksStopped = 0;
            SprintController.setForceStopSprint(true);
        }
    }

    private void flushPackets() {
        ClientPlayNetworkHandler connection = mc.getNetworkHandler();
        if (connection == null) {
            queuedPackets.clear();
            return;
        }

        flushing = true;
        while (!queuedPackets.isEmpty()) {
            Packet<?> packet = queuedPackets.poll();
            if (packet != null) {
                connection.sendPacket(packet);
            }
        }
        flushing = false;
    }

    @EventSubscribe
    private void onMovement(EventMovementInput event) {
        if (isNull()) return;

        if (waitingToFlush) {
            event.forward = false;
            event.back = false;
            event.left = false;
            event.right = false;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x00dd,0x8e82,0xcd67,0x77d5}, new char[]{0x7798,0x00b2,0xcd03,0x8ee7}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0c2e,0x6989,0x80ce,0x8852,0x5027,0x5c38,0x1f1f}, new char[]{0x0c78,0x5046,0x1f71,0x883b,0x5c54,0x80a2,0x69e8}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfec1,0x3f3b,0x981c,0x1ff2}, new char[]{0x985b,0x3f49,0x1f9b,0xfeac}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x35a1,0xfc7b,0xc2f3,0x9b55,0x41fc,0x452e,0x3850,0x5d3f,0xd771,0x5a4a,0x08f9,0x4b1b,0x1ebd,0x2233,0x01ec,0x3797,0x9cf5,0x311b,0xafc7,0x79e2,0x5c0b,0x1118,0x39b2,0xba67,0x2d2f,0xac9c,0x28f9,0x53a4}, new char[]{0x79ae,0x3835,0xba13,0x9b26,0x41dc,0xafbe,0x4541,0x088c,0xc2d3,0x35d6,0x1edc,0x9c99,0x5d54,0xacbc,0x39db,0xfc15,0x116b,0x2890,0x5c6f,0x317e,0x01cc,0xd710,0x2213,0x5a27,0x37f2,0x2d41,0x4b6e,0x538a}).plainString();
    }
}
