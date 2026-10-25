package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.Block;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.shape.VoxelShape;


// Aura monster
public class FastStairsModule extends Feature {

    private static final double JUMP_OFFSET = 1.01;

    public FastStairsModule() {
        super(new FeatureInfo(
                Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11e3,0xa8aa,0xbc1d,0x226b,0x1c9b,0x82cc,0x0b28,0xd13c,0xb15c,0x9631,0x379e}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190}).toString()),
                Concat.of(LT.L1),
                Category.MOVEMENT,
                false)
        );
    }

    @EventSubscribe
    private void onMovement(EventMovementInput e) {
        if (isNull() || !isEnabled()) return;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        if (mc.player.isDead()) return;
        if (!mc.player.isOnGround()) return;
        if (mc.player.isTouchingWater()) return;
        if (mc.player.isInLava()) return;
        if (!mc.player.isSprinting()) return;
        if (!e.forward) return;
        
        // Calculate forward direction from player yaw
        float yawRad = mc.player.getYaw() * ((float) Math.PI / 180.0F);
        double forwardX = -MathHelper.sin(yawRad);
        double forwardZ = MathHelper.cos(yawRad);
        
        ShapeContext collisionContext = ShapeContext.of(mc.player);
        
        // Check clearance above player first (blocks at Y+1 and Y+2 must be clear for jump)
        BlockPos playerPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getBoundingBox().maxY, mc.player.getZ());
        BlockState aboveBlock1 = mc.world.getBlockState(playerPos.up());
        if (!aboveBlock1.getCollisionShape(mc.world, playerPos.up(), collisionContext).isEmpty()) {
            return;
        }
        
        BlockState aboveBlock2 = mc.world.getBlockState(playerPos.up(2));
        if (!aboveBlock2.getCollisionShape(mc.world, playerPos.up(2), collisionContext).isEmpty()) {
            return;
        }
        
        // This checks the block 1 block ahead at current y/floor/feet level
        double checkX = mc.player.getX() + forwardX;
        double checkZ = mc.player.getZ() + forwardZ;
        BlockPos checkPos = BlockPos.ofFloored(checkX, mc.player.getBlockY(), checkZ);
        
        BlockState blockState = mc.world.getBlockState(checkPos);
        Block block = blockState.getBlock();
        
        if (block instanceof StairsBlock) {
            VoxelShape stairsShape = blockState.getCollisionShape(mc.world, checkPos, collisionContext);
            if (!stairsShape.isEmpty()) {
                double maxY = stairsShape.getMax(Direction.Axis.Y);
                if (maxY >= 0.5) {
                    double blockCenterX = checkPos.getX() + 0.5;
                    double blockCenterZ = checkPos.getZ() + 0.5;
                    double playerX = mc.player.getX();
                    double playerZ = mc.player.getZ();
                    
                    // Horizontal distance
                    double dx = blockCenterX - playerX;
                    double dz = blockCenterZ - playerZ;
                    double distance = Math.sqrt(dx * dx + dz * dz);
          
                    if (distance <= JUMP_OFFSET && mc.player.isOnGround()) {
                        e.jump = true;
                    }
                }
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x07a7,0x937e,0xcd72,0xfdaa,0x03fb,0x98b1,0xee7f,0x3fe8,0xa77c,0x6e2f,0x3f7c,0x187b,0x5b21,0x6360,0x4079,0xeeac,0xbebe,0xd120,0x33c1,0x2b06,0xe705,0x89ac,0xc042,0xc3cd,0xb5bb,0x2990,0xf1e6,0xa5fa,0xc104,0x5778,0x1bc4,0x712b,0x138b,0x5108,0x3a2a,0x70d9,0x07eb,0xe317,0x583e,0x319f,0x260a}, new char[]{0x3f2c,0x2b63,0x930c,0xc024,0x078e,0x89cf,0xb5cf,0x3f84,0x70a0,0x581e,0x33ab,0x400c,0xc169,0x267a,0x29b0,0x038c,0x31f7,0x13e2,0x1ba8,0x571d,0x185b,0xf191,0x714a,0xa596,0x630b,0xcd1b,0xd14e,0xa71b,0xee5f,0x6e5a,0xfdda,0xc3ed,0x07c8,0xbed0,0x3a0a,0xe776,0x5b55,0xeecd,0x5161,0x98c3,0xe364}).plainString();
    }
}
