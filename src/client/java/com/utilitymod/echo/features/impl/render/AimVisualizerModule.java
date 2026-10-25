package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.handlers.RotationHandler;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;


// Originally I thought of using Gizmo package
// However I wanted to do a more "low-level" implementation
public class AimVisualizerModule extends Feature {

    private final FloatSetting cubeSize = new FloatSetting(Concat.of(LT.L1), 0.15f, 0.05f, 0.5f, 0.01f);
    private final ColorSetting serverColor = new ColorSetting(Concat.of(LT.L2), 255, 0, 0, 200);
    private final ColorSetting clientColor = new ColorSetting(Concat.of(LT.L3), 0, 0, 255, 200);

    public AimVisualizerModule() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd13b,0x0b2f,0xb166,0x37e8,0x962f,0x2275,0xeb9b,0xfcf5,0x82d8,0xbc49,0xa8a2,0x1c93,0x11f9,0x6553}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87}).toString()),
                Concat.of(LT.L4),
                Category.RENDER,
                false));
    }

    private Vec3d calculateTarget(Vec3d eyePos, float pitch, float yaw, double range, double hitboxMargin) {
        Vec3d lookDir = RotationHandler.getRotationVector(pitch, yaw);
        Vec3d endPos = eyePos.add(lookDir.multiply(range));

        // Raycast for block
        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                eyePos, endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player));

        // Raycast for entity
        Box entitySearchBox = mc.player.getBoundingBox().stretch(lookDir.multiply(range)).expand(hitboxMargin);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                mc.player, eyePos, endPos, entitySearchBox,
                entity -> !entity.isSpectator() && entity.canHit(),
                (range * range));

        return determineTargetPosition(eyePos, blockHit, entityHit, endPos);
    }

    @EventSubscribe
    private void onRender3d(EventRender3D event) {
        if (isNull()) return;
        if (mc.player == null || mc.world == null) return;

        float tickDelta = event.getTickDelta();
        Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);

        ItemStack mainHandItem = mc.player.getMainHandStack();
        AttackRangeComponent attackRangeComponent = mainHandItem.get(DataComponentTypes.ATTACK_RANGE);
        double attackRange = getAttackRange(attackRangeComponent);
        double hitboxMargin = getHitboxMargin(attackRangeComponent);

        boolean showSilentTarget = RotationUtils.hasSilentRotation();
        Vec3d serverTargetPos = null;
        if (showSilentTarget) {
            float[] serverRotations = getServerRotations();
            float serverYaw = serverRotations[0];
            float serverPitch = serverRotations[1];
            serverTargetPos = calculateTarget(eyePos, serverPitch, serverYaw, attackRange, hitboxMargin);
        }

        float clientYaw = mc.player.getLerpedYaw(tickDelta);
        float clientPitch = mc.player.getLerpedPitch(tickDelta);
        Vec3d clientTargetPos = calculateTarget(eyePos, clientPitch, clientYaw, attackRange, hitboxMargin);

        // Render serverside cube (red)
        int serverColorInt = ColorHelper.fromFloats(
                serverColor.getAlphaNormalized(),
                serverColor.getRedNormalized(),
                serverColor.getGreenNormalized(),
                serverColor.getBlueNormalized());

        // Render client-side cube (blue)
        int clientColorInt = ColorHelper.fromFloats(
                clientColor.getAlphaNormalized(),
                clientColor.getRedNormalized(),
                clientColor.getGreenNormalized(),
                clientColor.getBlueNormalized());

        float size = cubeSize.getValue();

        // z fighting shenanigans
        double clientSize = size - 0.001;
        var draw = event.getDraw3D().getMinecraftTarget();
        if (showSilentTarget) {
            draw.box(
                    serverTargetPos.x - size * 0.5,
                    serverTargetPos.y - size * 0.5,
                    serverTargetPos.z - size * 0.5,
                    size, size, size,
                    serverColorInt
            );
        }
        draw.box(
                clientTargetPos.x - clientSize * 0.5,
                clientTargetPos.y - clientSize * 0.5,
                clientTargetPos.z - clientSize * 0.5,
                clientSize,
                clientSize,
                clientSize,
                clientColorInt
        );
    }

    private double getAttackRange(AttackRangeComponent attackRangeComponent) {
        if (attackRangeComponent == null) {
            return 3.0;
        }

        return attackRangeComponent.getEffectiveMaxRange(mc.player);
    }

    private double getHitboxMargin(AttackRangeComponent attackRangeComponent) {
        if (attackRangeComponent == null) {
            return 0.0;
        }

        return attackRangeComponent.hitboxMargin();
    }

    private float[] getServerRotations() {
        if (RotationUtils.hasSilentRotation()) {
            return RotationUtils.getLastRotations();
        }

        return new float[]{RotationHandler.getServerYaw(), RotationHandler.getServerPitch()};
    }

    private Vec3d determineTargetPosition(Vec3d eyePos, BlockHitResult blockHit, EntityHitResult entityHit, Vec3d fallbackPos) {
        double blockDist = Double.MAX_VALUE;
        double entityDist = Double.MAX_VALUE;

        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            blockDist = eyePos.squaredDistanceTo(blockHit.getPos());
        }

        if (entityHit != null) {
            entityDist = eyePos.squaredDistanceTo(entityHit.getPos());
        }

        // Return closest hit, or fallback if no hits
        if (entityHit != null && entityDist < blockDist) {
            // Entity hit, use the actual hit location on the entity surface
            return entityHit.getPos();
        } else if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getPos();
        }

        return fallbackPos;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x25be,0x29d5,0x7df5,0x5fbe}, new char[]{0x5fed,0x7d9c,0x25c4,0x29b0}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9744,0x3c98,0x2289,0x089e,0xce5e,0xa148,0x7f6d,0xf80e,0xa15c,0xc937,0x2f1d,0xf3fd}, new char[]{0x22da,0xce3b,0xf38f,0x2f6b,0x3cfd,0xc945,0xf82e,0xa10b,0x7f02,0xa130,0x08f1,0x9736}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe983,0xf94c,0xfae3,0x06d4,0x831c,0x2683,0x548b,0xdb2e,0x3f5d,0x2c5e,0x911c,0xc90c}, new char[]{0xdb6d,0x3f31,0xf925,0x54ee,0x8372,0x9168,0xc92c,0x0697,0xe9ec,0xfa8f,0x2c31,0x26f1}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x645d,0x149c,0x0844,0x9d51,0x9734,0x88a0,0xac96,0x664b,0x858f,0x329c,0x3eaa,0x3e54,0x4ab1,0xe7a3,0xc6c9,0x4e96,0x037b,0xb649,0x1d5c,0xed6a,0x959d,0x6220,0x04cb,0xf07c,0x687d,0x5470,0x9426,0x5b7f,0xcaa5,0x90a0,0x593c,0x832c,0xff96,0x31ec,0x189c,0x3c6b,0x544e,0x7ae5,0x546e,0xab66,0x5150,0xc39a,0x4a09,0xcae4}, new char[]{0x640e,0x14f4,0x6812,0x5419,0x6638,0x9714,0x88d7,0x5b17,0x7a80,0xc3e8,0x18f9,0x9d71,0x835f,0x3e31,0xcad7,0xab10,0x4a6c,0x85fd,0x545d,0x0308,0x4ad8,0x04af,0x4ef3,0xffb6,0xf00e,0xca8b,0x90d4,0x6241,0x3198,0xb620,0x3c04,0x95f3,0x1d2f,0x3e8a,0xacf7,0x543c,0x5959,0x9406,0x5131,0xc6a0,0x32f1,0x082d,0xed04,0xe7c4}).plainString();
    }
}
