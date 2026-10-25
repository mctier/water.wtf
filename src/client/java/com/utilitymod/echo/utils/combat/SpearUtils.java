package com.utilitymod.echo.utils.combat;

import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class SpearUtils {
    /**
     * Tracks the player's tickCount at the time of the last hand swing.
     * On the server, ServerPlayer.swing() calls resetAttackStrengthTicker() which
     * resets both attackStrengthTicker and itemSwapTicker to 0. The client does NOT
     * reset these tickers on swing (only on attack/item change), causing a desync
     * when the player uses an item or places a block. We track swings ourselves to
     * mirror the server's behavior.
     */
    private static int lastSwingPlayerTick = Integer.MIN_VALUE;

    private SpearUtils() {
    }

    public static void onPlayerSwing(PlayerEntity player) {
        if (player != null) {
            lastSwingPlayerTick = player.age;
        }
    }

    private static int getSwingTicker(PlayerEntity player) {
        if (player == null) return Integer.MAX_VALUE;
        return player.age - lastSwingPlayerTick;
    }

    public static boolean isSpearFullyCooled(PlayerEntity player, ItemStack spearStack, float partialTicks) {
        return getProjectedSpearAttackStrengthScale(player, spearStack, partialTicks) >= 1.0F;
    }

    public static boolean isSpearThrustFullyCooled(PlayerEntity player, ItemStack spearStack, float partialTicks) {
        int effectiveTicker = Math.min(getItemSwapTicker(player), getSwingTicker(player));
        return getProjectedSpearScale(player, spearStack, partialTicks, effectiveTicker) >= 1.0F;
    }

    public static float getProjectedSpearAttackStrengthScale(PlayerEntity player, ItemStack spearStack, float partialTicks) {
        return getProjectedSpearScale(player, spearStack, partialTicks, getAttackStrengthTicker(player));
    }

    public static float getProjectedThrustStrengthScale(PlayerEntity player, ItemStack spearStack, float partialTicks) {
        int effectiveTicker = Math.min(getItemSwapTicker(player), getSwingTicker(player));
        return getProjectedSpearScale(player, spearStack, partialTicks, effectiveTicker);
    }

    public static double getRemainingSpearAttackCooldownSeconds(PlayerEntity player, ItemStack spearStack, float partialTicks) {
        return getRemainingCooldownSeconds(player, spearStack, partialTicks, getAttackStrengthTicker(player));
    }

    public static double getRemainingThrustCooldownSeconds(PlayerEntity player, ItemStack spearStack, float partialTicks) {
        int effectiveTicker = Math.min(getItemSwapTicker(player), getSwingTicker(player));
        return getRemainingCooldownSeconds(player, spearStack, partialTicks, effectiveTicker);
    }

    private static int getAttackStrengthTicker(PlayerEntity player) {
        return player == null ? 0 : com.utilitymod.echo.api.Accessors.getAttackStrengthTicker(player);
    }

    private static int getItemSwapTicker(PlayerEntity player) {
        return player == null ? 0 : com.utilitymod.echo.api.Accessors.getItemSwapTicker(player);
    }

    private static float getProjectedSpearScale(PlayerEntity player, ItemStack spearStack, float partialTicks, int ticker) {
        float projectedDelay = getProjectedSpearAttackDelay(player, spearStack);
        if (projectedDelay <= 0.0F) {
            return 0.0F;
        }

        return MathHelper.clamp((ticker + partialTicks) / projectedDelay, 0.0F, 1.0F);
    }

    private static double getRemainingCooldownSeconds(PlayerEntity player, ItemStack spearStack, float partialTicks, int ticker) {
        float projectedDelay = getProjectedSpearAttackDelay(player, spearStack);
        if (projectedDelay <= 0.0F) {
            return 0.0D;
        }

        return Math.max(0.0D, (projectedDelay - (ticker + partialTicks)) / 20.0D);
    }

    private static float getProjectedSpearAttackDelay(PlayerEntity player, ItemStack spearStack) {
        if (player == null || spearStack == null || spearStack.isEmpty()) {
            return 0.0F;
        }

        EntityAttributeInstance attackSpeedInstance = player.getAttributeInstance(EntityAttributes.ATTACK_SPEED);
        if (attackSpeedInstance == null) {
            return player.getAttackCooldownProgressPerTick();
        }

        double[] add = {0.0D};
        double[] addMultBase = {0.0D};
        double[] addMultTotal = {1.0D};

        /**
         * EntityAttributeInstance.class
         *     private double calculateValue() {
         *         double d = this.getBaseValue();
         *
         *         for (EntityAttributeModifier attributeModifier : this.getModifiersOrEmpty(EntityAttributeModifier.Operation.ADD_VALUE)) {
         *             d += attributeModifier.value();
         *         }
         *
         *         double e = d;
         *
         *         for (EntityAttributeModifier attributeModifier2 : this.getModifiersOrEmpty(EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
         *             e += d * attributeModifier2.value();
         *         }
         *
         *         for (EntityAttributeModifier attributeModifier2 : this.getModifiersOrEmpty(EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
         *             e *= 1.0 + attributeModifier2.value();
         *         }
         *
         *         return this.attribute.value().clamp(e);
         *     }
         */
        for (EntityAttributeModifier modifier : attackSpeedInstance.getModifiers()) {
            if (modifier.idMatches(Item.BASE_ATTACK_SPEED_MODIFIER_ID)) {
                continue;
            }
            applyModifier(modifier, add, addMultBase, addMultTotal);
        }

        /**
         * ItemStack.class
         *     public void forEachModifier(EquipmentSlot equipmentSlot, BiConsumer<Holder<Attribute>, EntityAttributeModifier> biConsumer) {
         *         ItemAttributeModifiers itemAttributeModifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
         *         itemAttributeModifiers.forEach(equipmentSlot, biConsumer);
         *         EnchantmentHelper.applyAttributeModifiers(this, equipmentSlot, biConsumer);
         *     }
         */
        spearStack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (holder, modifier) -> {
            if (holder.equals(EntityAttributes.ATTACK_SPEED)) {
                applyModifier(modifier, add, addMultBase, addMultTotal);
            }
        });

        double base = attackSpeedInstance.getBaseValue();
        double withAdds = base + add[0];
        double withBaseMultiplier = withAdds + (withAdds * addMultBase[0]);
        double projectedSpeed = withBaseMultiplier * addMultTotal[0];
        projectedSpeed = EntityAttributes.ATTACK_SPEED.value().clamp(projectedSpeed);

        if (projectedSpeed <= 0.0D) {
            return 0.0F;
        }

        return (float) (20.0D / projectedSpeed);
    }

    private static void applyModifier(
        EntityAttributeModifier modifier,
        double[] add,
        double[] addMultBase,
        double[] addMultTotal
    ) {
        switch (modifier.operation()) {
            case ADD_VALUE -> add[0] += modifier.value();
            case ADD_MULTIPLIED_BASE -> addMultBase[0] += modifier.value();
            case ADD_MULTIPLIED_TOTAL -> addMultTotal[0] *= 1.0D + modifier.value();
        }
    }
}
