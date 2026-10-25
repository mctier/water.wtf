package com.utilitymod.echo.utils.inventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import com.utilitymod.echo.handlers.InputHandler;
import com.utilitymod.echo.utils.Imports;
import com.utilitymod.echo.utils.network.NetworkUtil;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;


public final class InventoryUtils implements Imports {

    public static void setInvSlot(int slot) {
        if (mc.player.getInventory().getSelectedSlot() == slot) return;
        mc.player.getInventory().setSelectedSlot(slot);
    }

    public static void setInvSlot(int slot, boolean inputSimulation) {
        if (slot < 0 || slot > 8) return;

        if (mc.player.getInventory().getSelectedSlot() == slot) return;

        if (inputSimulation) {
            if (mc.options != null && mc.options.hotbarKeys != null) {
                KeyBinding hotbarKey = mc.options.hotbarKeys[slot];
                if (hotbarKey != null) {
                    // Use the actual bound hotbar key (1-9 by default) so input
                    // simulation produces a virtual keyboard event for the slot.
                    InputHandler.simulateClick(hotbarKey, true);
                }
            }
        } else {
            setInvSlot(slot);
        }
    }

    public static void swapToOffhand(){
        NetworkUtil.send(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
    }

    public static boolean selectItemFromHotbar(Predicate<Item> item) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!item.test(itemStack.getItem())) continue;
            inv.setSelectedSlot(i);
            return true;
        }
        return false;
    }

    public static boolean selectItemFromHotbar(Item item) {
        return InventoryUtils.selectItemFromHotbar((Item i) -> i == item);
    }

    public static boolean hasItemInHotbar(Predicate<Item> item) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!item.test(itemStack.getItem())) continue;
            return true;
        }
        return false;
    }

    public static int findItemWithPredicateInHotbar(Predicate<ItemStack> predicate) {
        PlayerInventory inventory = mc.player.getInventory();
        for (int i = 0; i <= 8; i++) {
            if (predicate.test(inventory.getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    public static int countItem(Predicate<Item> item) {
        PlayerInventory inv = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!item.test(itemStack.getItem())) continue;
            count += itemStack.getCount();
        }
        return count;
    }

    public static int countItemExceptHotbar(Predicate<Item> item) {
        PlayerInventory inv = mc.player.getInventory();
        int count = 0;
        for (int i = 9; i < 36; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!item.test(itemStack.getItem())) continue;
            count += itemStack.getCount();
        }
        return count;
    }

    public static int getSwordSlot() {
        PlayerInventory playerInventory = mc.player.getInventory();
        for (int itemIndex = 0; itemIndex < 9; ++itemIndex) {
            Item item = playerInventory.getStack(itemIndex).getItem();
            if (item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD || item == Items.COPPER_SWORD) {
                return itemIndex;
            }
        }
        return -1;
    }

    public static boolean isSword(Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
    }

    public static boolean isFood(ItemStack item) {
        return item.getComponents().contains(DataComponentTypes.FOOD);
    }

    public static boolean selectSword() {
        int itemIndex = InventoryUtils.getSwordSlot();
        if (itemIndex != -1) {
            InventoryUtils.setInvSlot(itemIndex);
            return true;
        }
        return false;
    }

    public static int charmIndex() {
        assert (mc.player != null);
        PlayerInventory inv = mc.player.getInventory();
        for (int index = 9; index < 36; ++index) {
            if (inv.getStack(index).getItem() != Items.TOTEM_OF_UNDYING) continue;
            return index;
        }
        return -1;
    }

    public static boolean selectAxe() {
        int itemIndex = InventoryUtils.getAxeSlot();
        if (itemIndex != -1) {
            mc.player.getInventory().setSelectedSlot(itemIndex);
            return true;
        }
        return false;
    }

    public static int findRandomTotemSlot() {
        PlayerInventory inventory = mc.player.getInventory();
        Random random = new Random();
        ArrayList<Integer> totemIndexes = new ArrayList<Integer>();
        for (int i = 9; i < 36; ++i) {
            if (inventory.getStack(i).getItem() != Items.TOTEM_OF_UNDYING) continue;
            totemIndexes.add(i);
        }
        if (!totemIndexes.isEmpty()) {
            return totemIndexes.get(random.nextInt(totemIndexes.size()));
        }
        return -1;
    }

    public static int findRandomPot(String potion) {
        PlayerInventory inventory = mc.player.getInventory();
        Random random = new Random();
        int slotIndex = random.nextInt(27) + 9;
        for (int i = 0; i < 27; ++i) {
            int index = (slotIndex + i) % 36;
            ItemStack itemStack = inventory.getStack(index);
            if (!(itemStack.getItem() instanceof SplashPotionItem)) continue;
            if (!itemStack.get(DataComponentTypes.POTION_CONTENTS).getEffects().toString().contains(potion)) {
                return -1;
            }
            return index;
        }
        return -1;
    }

    public static List<Integer> getEmptyHotbarSlots() {
        PlayerInventory inventory = mc.player.getInventory();
        ArrayList<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < 9; ++i) {
            if (inventory.getStack(i).isEmpty()) {
                slots.add(i);
            }
        }
        return slots;
    }

    public static int getAxeSlot() {
        PlayerInventory playerInventory = mc.player.getInventory();
        for (int itemIndex = 0; itemIndex < 9; ++itemIndex) {
            if (!(playerInventory.getStack(itemIndex).getItem() instanceof AxeItem)) continue;
            return itemIndex;
        }
        return -1;
    }

    public static int countItem(Item item) {
        return InventoryUtils.countItem((Item i) -> i == item);
    }

    public static boolean isHoldingChestplate() {
        var mainHand = mc.player.getMainHandStack().getItem();
        return mainHand == Items.NETHERITE_CHESTPLATE ||
                mainHand == Items.CHAINMAIL_CHESTPLATE ||
                mainHand == Items.IRON_CHESTPLATE ||
                mainHand == Items.GOLDEN_CHESTPLATE ||
                mainHand == Items.DIAMOND_CHESTPLATE ||
                mainHand == Items.LEATHER_CHESTPLATE;
    }

    public static boolean isOffhandHoldingChestplate() {
        var offhand = mc.player.getOffHandStack().getItem();
        return offhand == Items.NETHERITE_CHESTPLATE ||
                offhand == Items.CHAINMAIL_CHESTPLATE ||
                offhand == Items.IRON_CHESTPLATE ||
                offhand == Items.GOLDEN_CHESTPLATE ||
                offhand == Items.DIAMOND_CHESTPLATE ||
                offhand == Items.LEATHER_CHESTPLATE;
    }

    public static int findChestplateSlot() {
        final PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i <= 8; i++) {
            Item item = inv.getStack(i).getItem();
            if (item == Items.NETHERITE_CHESTPLATE ||
                    item == Items.CHAINMAIL_CHESTPLATE ||
                    item == Items.IRON_CHESTPLATE ||
                    item == Items.GOLDEN_CHESTPLATE ||
                    item == Items.DIAMOND_CHESTPLATE ||
                    item == Items.LEATHER_CHESTPLATE) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isWearingElytra() {
        return mc.player.getInventory().getStack(38).getItem() == Items.ELYTRA;
    }

    public static ItemStack getMainHandItem() {
        return mc.player.getMainHandStack();
    }

    public static boolean isHoldingItem(Item item) {
        return mc.player.getMainHandStack().getItem() == item;
    }

    public static boolean isHoldingAnyHand(Item item) {
        return isHoldingItem(item) || isOffhandHoldingItem(item);
    }

    public static boolean isOffhandHoldingItem(Item item) {
        return mc.player.getOffHandStack().getItem() == item;
    }

    public static boolean isHoldingSwordItem() {
        Item mainHand = mc.player.getMainHandStack().getItem();
        return mainHand == Items.WOODEN_SWORD ||
                mainHand == Items.STONE_SWORD ||
                mainHand == Items.COPPER_SWORD ||
                mainHand == Items.IRON_SWORD ||
                mainHand == Items.GOLDEN_SWORD ||
                mainHand == Items.DIAMOND_SWORD ||
                mainHand == Items.NETHERITE_SWORD;
    }

    public static int findSwordInHotbar() {
        return findItemWithPredicateInHotbar(stack -> isSword(stack.getItem()));
    }

    public static boolean isHoldingSpearItem(){
        Item mainHand = mc.player.getMainHandStack().getItem();
        return mainHand == Items.WOODEN_SPEAR ||
                mainHand == Items.STONE_SPEAR ||
                mainHand == Items.COPPER_SPEAR ||
                mainHand == Items.IRON_SPEAR ||
                mainHand == Items.GOLDEN_SPEAR ||
                mainHand == Items.DIAMOND_SPEAR ||
                mainHand == Items.NETHERITE_SPEAR;
    }

    public static int findSpearInHotbar() {
        return findItemWithPredicateInHotbar(stack -> isItemSpear(stack.getItem()));
    }

    public static boolean isItemSpear(Item item){
        return item == Items.WOODEN_SPEAR ||
                item == Items.STONE_SPEAR ||
                item == Items.COPPER_SPEAR ||
                item == Items.IRON_SPEAR ||
                item == Items.GOLDEN_SPEAR ||
                item == Items.DIAMOND_SPEAR ||
                item == Items.NETHERITE_SPEAR;
    }

    public static boolean isHoldingAxeItem() {
        Item mainHand = mc.player.getMainHandStack().getItem();
        return mainHand == Items.WOODEN_AXE ||
                mainHand == Items.STONE_AXE ||
                mainHand == Items.COPPER_AXE ||
                mainHand == Items.IRON_AXE ||
                mainHand == Items.GOLDEN_AXE ||
                mainHand == Items.DIAMOND_AXE ||
                mainHand == Items.NETHERITE_AXE;
    }

    public static boolean isHoldingPickaxeItem() {
        Item mainHand = mc.player.getMainHandStack().getItem();
        return mainHand == Items.WOODEN_PICKAXE ||
                mainHand == Items.STONE_PICKAXE ||
                mainHand == Items.COPPER_PICKAXE ||
                mainHand == Items.IRON_PICKAXE ||
                mainHand == Items.GOLDEN_PICKAXE ||
                mainHand == Items.DIAMOND_PICKAXE ||
                mainHand == Items.NETHERITE_PICKAXE;
    }

    public static boolean isHoldingShovelItem() {
        Item mainHand = mc.player.getMainHandStack().getItem();
        return mainHand == Items.WOODEN_SHOVEL ||
                mainHand == Items.STONE_SHOVEL ||
                mainHand == Items.COPPER_SHOVEL ||
                mainHand == Items.IRON_SHOVEL ||
                mainHand == Items.GOLDEN_SHOVEL ||
                mainHand == Items.DIAMOND_SHOVEL ||
                mainHand == Items.NETHERITE_SHOVEL;
    }

    public static boolean isHoldingHoeItem() {
        Item mainHand = mc.player.getMainHandStack().getItem();
        return mainHand == Items.WOODEN_HOE ||
                mainHand == Items.STONE_HOE ||
                mainHand == Items.COPPER_HOE ||
                mainHand == Items.IRON_HOE ||
                mainHand == Items.GOLDEN_HOE ||
                mainHand == Items.DIAMOND_HOE ||
                mainHand == Items.NETHERITE_HOE;
    }

    public static int findItemWithEnchantmentInHotbar(Predicate<ItemStack> predicate, RegistryKey<Enchantment> enchantKey, boolean allowNoEnch) {
        if (enchantKey != null) {
            PlayerInventory inventory = mc.player.getInventory();
            for (int i = 0; i <= 8; i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack != null && !stack.isEmpty() && predicate.test(stack)) {
                    if (mc.world != null) {
                        var opt = mc.world.getRegistryManager().getOptionalEntry(enchantKey);
                        if (opt.isPresent() && EnchantmentHelper.getLevel(opt.get(), stack) > 0) {
                            return i;
                        }
                    }
                }
            }
        }

        if (allowNoEnch) {
            PlayerInventory inventory = mc.player.getInventory();
            for (int i = 0; i <= 8; i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack != null && !stack.isEmpty() && predicate.test(stack)) {
                    return i;
                }
            }
        }

        return -1;
    }

    public static int findMaceWithEnchantmentInHotbar(RegistryKey<Enchantment> enchantKey, boolean allowNoEnch) {
        return findItemWithEnchantmentInHotbar(stack -> stack.getItem() instanceof MaceItem, enchantKey, allowNoEnch);
    }

    public static void swapSilentTo(int slot) {
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    public static void swapSilentBack() {
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
    }

}
