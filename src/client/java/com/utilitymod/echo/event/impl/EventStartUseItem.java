package com.utilitymod.echo.event.impl;


import com.utilitymod.echo.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Event fired when a player right-clicks with an item
 */
public abstract class
EventStartUseItem extends Event {
    private final PlayerEntity player;
    private final Hand hand;
    private final ItemStack stack;
    private final HitResult hitResult;

    public EventStartUseItem(Event.Stage stage, PlayerEntity player, Hand hand, ItemStack stack, HitResult hitResult) {
        super(stage);
        this.player = player;
        this.hand = hand;
        this.stack = stack;
        this.hitResult = hitResult;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public Hand getHand() {
        return hand;
    }

    public ItemStack getStack() {
        return stack;
    }

    public HitResult getHitResult() {
        return hitResult;
    }

    public boolean isTargetingBlock() {
        return hitResult instanceof BlockHitResult;
    }

    public boolean isTargetingEntity() {
        return hitResult instanceof EntityHitResult;
    }

    public BlockHitResult getBlockHitResult() {
        return isTargetingBlock() ? (BlockHitResult) hitResult : null;
    }

    public EntityHitResult getEntityHitResult() {
        return isTargetingEntity() ? (EntityHitResult) hitResult : null;
    }

    /**
     * Called before the item use is processed
     */
    public static class Pre extends EventStartUseItem {
        public Pre(PlayerEntity player, Hand hand, ItemStack stack, HitResult hitResult) {
            super(Event.Stage.PRE, player, hand, stack, hitResult);
        }
    }

    /**
     * Called after the item use is processed
     */
    public static class Post extends EventStartUseItem {
        public Post(PlayerEntity player, Hand hand, ItemStack stack, HitResult hitResult) {
            super(Event.Stage.POST, player, hand, stack, hitResult);
        }
    }
}
