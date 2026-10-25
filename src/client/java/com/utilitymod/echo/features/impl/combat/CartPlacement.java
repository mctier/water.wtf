package com.utilitymod.echo.features.impl.combat;

import com.utilitymod.echo.utils.blocks.BlockUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import static com.utilitymod.echo.utils.Imports.mc;

/** Separate rail placement (click support) from minecart use (click the rail). */
final class CartPlacement {
    private CartPlacement() {}
    static boolean railAt(BlockPos pos) {
        return pos != null && mc.world != null && mc.world.getBlockState(pos).isIn(BlockTags.RAILS);
    }
    static BlockPos target(BlockHitResult hit) {
        return railAt(hit.getBlockPos()) ? hit.getBlockPos() : hit.getBlockPos().offset(hit.getSide());
    }
    static boolean supported(BlockPos pos) {
        return railAt(pos) || (pos != null && mc.world.getBlockState(pos).isReplaceable()
                && mc.world.getBlockState(pos.down()).isSideSolidFullSquare(mc.world,pos.down(),Direction.UP));
    }
    static BlockHitResult hit(int slot, BlockPos pos) {
        if (slot < 0 || slot > 8 || pos == null || mc.player == null || mc.world == null) return null;
        if (mc.player.getInventory().getStack(slot).isOf(Items.TNT_MINECART)) {
            if (!railAt(pos)) return null;
            Vec3d aim = new Vec3d(pos.getX()+0.5,pos.getY()+0.05,pos.getZ()+0.5);
            BlockHitResult ray=mc.world.raycast(new RaycastContext(mc.player.getEyePos(),aim,
                    RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
            return ray.getType()==HitResult.Type.BLOCK && ray.getBlockPos().equals(pos) && inReach(ray) ? ray : null;
        }
        if (!mc.world.getBlockState(pos).isReplaceable()) return null;
        var placement=BlockUtils.findPlacementHit(pos,true);
        return placement!=null && inReach(placement.hitResult()) ? placement.hitResult() : null;
    }
    static boolean inReach(BlockHitResult hit) {
        double reach=mc.player.getBlockInteractionRange();
        return mc.player.getEyePos().squaredDistanceTo(hit.getPos())<=reach*reach;
    }
    static boolean use(int slot, BlockHitResult hit) {
        if (slot<0 || hit==null || mc.interactionManager==null) return false;
        InventoryUtils.setInvSlot(slot);
        return BlockUtils.interactWithBlock(hit,true).isAccepted();
    }
    static boolean keyDown(int key) {
        if (mc.getWindow()==null || key==-1) return false;
        long window=mc.getWindow().getHandle();
        if(key<0) return GLFW.glfwGetMouseButton(window,key&255)==GLFW.GLFW_PRESS;
        return key<=GLFW.GLFW_KEY_LAST && GLFW.glfwGetKey(window,key)==GLFW.GLFW_PRESS;
    }
}
