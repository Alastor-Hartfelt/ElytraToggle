package com.alastorhartfelt.elytratoggle;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class ElytraToggleClient implements ClientModInitializer {
    private static final int CHEST_MENU_SLOT = InventoryMenu.ARMOR_SLOT_START + 1;
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("elytratoggle", "main"));

    private final PressMapping toggleKey = new PressMapping("key.elytratoggle.toggle", GLFW.GLFW_KEY_R);
    private final PressMapping swapKey = new PressMapping("key.elytratoggle.swap", GLFW.GLFW_KEY_G);

    private LocalPlayer togglePlayer;
    private int originalElytraSlot = -1;

    @Override
    public void onInitializeClient() {
        ElytraToggleConfig.load();
        KeyMappingHelper.registerKeyMapping(toggleKey);
        KeyMappingHelper.registerKeyMapping(swapKey);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(Minecraft client) {
        boolean togglePressed = toggleKey.consumePress();
        boolean swapPressed = swapKey.consumePress();
        LocalPlayer player = client.player;

        if (player == null || player != togglePlayer || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            togglePlayer = null;
            originalElytraSlot = -1;
        }

        if ((!togglePressed && !swapPressed) || player == null || client.gameMode == null
                || !player.isAlive() || player.isSpectator() || client.gui.screen() != null
                || player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }

        if (togglePressed) {
            toggleElytra(client, player);
        } else {
            swapBestChestplate(client, player);
        }
    }

    private void toggleElytra(Minecraft client, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack equipped = player.getItemBySlot(EquipmentSlot.CHEST);

        if (equipped.is(Items.ELYTRA)) {
            int destination = originalElytraSlot;
            if (destination < 0 || !inventory.getItem(destination).isEmpty()) {
                destination = findEmptyInventoryFirst(inventory, -1);
            }
            if (destination < 0) {
                showStatus(player, "ElytraToggle: No empty inventory slot for the Elytra.");
            } else if (exchangeChestItem(client, player, destination, destination)) {
                togglePlayer = null;
                originalElytraSlot = -1;
                showStatus(player, "Elytra unequipped.");
            }
            return;
        }

        int source = findElytraSlot(inventory);
        if (source < 0) {
            showStatus(player, "No Elytra found in your inventory.");
            return;
        }

        // Keep the Elytra's old slot free when there is another place for the chest item.
        int destination = equipped.isEmpty() ? source : findEmptyInventoryFirst(inventory, source);
        if (destination < 0) {
            destination = source;
        }
        if (exchangeChestItem(client, player, source, destination)) {
            togglePlayer = player;
            originalElytraSlot = source;
            showStatus(player, "Elytra equipped.");
        }
    }

    private static void swapBestChestplate(Minecraft client, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack equipped = player.getItemBySlot(EquipmentSlot.CHEST);
        if (equipped.is(Items.ELYTRA)) {
            int source = findBestChestplateSlot(inventory);
            if (source < 0) {
                showStatus(player, "No chestplate found in your inventory.");
            } else if (exchangeChestItem(client, player, source, source)) {
                showStatus(player, "Elytra swapped with your best chestplate.");
            }
        } else if (chestplateQuality(equipped) > 0) {
            int source = findElytraSlot(inventory);
            if (source < 0) {
                showStatus(player, "No Elytra found in your inventory.");
            } else if (exchangeChestItem(client, player, source, source)) {
                showStatus(player, "Elytra and chestplate swapped.");
            }
        } else {
            showStatus(player, "G only swaps an equipped Elytra and chestplate.");
        }
    }

    private static boolean exchangeChestItem(Minecraft client, LocalPlayer player, int source, int destination) {
        Inventory inventory = player.getInventory();
        Slot chest = player.inventoryMenu.getSlot(CHEST_MENU_SLOT);
        Slot sourceSlot = player.inventoryMenu.getSlot(menuSlot(source));
        Slot destinationSlot = player.inventoryMenu.getSlot(menuSlot(destination));
        ItemStack incoming = inventory.getItem(source);
        if (!canSwap(player, chest, incoming)
                || (source != destination && !destinationSlot.getItem().isEmpty())
                || !canSwap(player, destinationSlot, chest.getItem())) {
            showStatus(player, "ElytraToggle: The chest item cannot be moved.");
            return false;
        }

        // Vanilla number-key swaps leave the cursor empty, even with a full inventory.
        // For main-inventory slots, stage the exchange in a non-selected hotbar slot.
        int hotbar = stagingSlot(inventory, source, destination);
        ItemStack originalHotbarStack = inventory.getItem(hotbar).copy();
        if (source != hotbar && !swapWithHotbar(client, player, sourceSlot, hotbar)) {
            return false;
        }
        if (!swapWithHotbar(client, player, chest, hotbar)) {
            // Restore staging only if the chest click left both staging slots as expected.
            if (source != hotbar && ItemStack.matches(sourceSlot.getItem(), originalHotbarStack)
                    && ItemStack.matches(inventory.getItem(hotbar), incoming)) {
                swapWithHotbar(client, player, sourceSlot, hotbar);
            }
            return false;
        }

        if (destination != hotbar && !swapWithHotbar(client, player, destinationSlot, hotbar)) {
            return false;
        }
        return source == destination || source == hotbar || originalHotbarStack.isEmpty()
                || swapWithHotbar(client, player, sourceSlot, hotbar);
    }

    private static boolean canSwap(LocalPlayer player, Slot slot, ItemStack incoming) {
        return slot.isActive() && (slot.getItem().isEmpty() || slot.mayPickup(player))
                && (incoming.isEmpty() || (slot.mayPlace(incoming)
                        && incoming.getCount() <= slot.getMaxStackSize(incoming)));
    }

    private static boolean swapWithHotbar(Minecraft client, LocalPlayer player, Slot slot, int hotbar) {
        Inventory inventory = player.getInventory();
        ItemStack hotbarStack = inventory.getItem(hotbar).copy();
        ItemStack slotStack = slot.getItem().copy();
        if (ItemStack.matches(hotbarStack, slotStack)) {
            return true;
        }
        if (!canSwap(player, slot, hotbarStack)) {
            return false;
        }
        client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot.index, hotbar,
                ContainerInput.SWAP, player);
        return ItemStack.matches(slot.getItem(), hotbarStack)
                && ItemStack.matches(inventory.getItem(hotbar), slotStack);
    }

    private static int stagingSlot(Inventory inventory, int source, int destination) {
        if (Inventory.isHotbarSlot(source)) return source;
        if (Inventory.isHotbarSlot(destination)) return destination;

        int fallback = inventory.getSelectedSlot() == 0 ? 1 : 0;
        for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
            if (slot != inventory.getSelectedSlot() && inventory.getItem(slot).isEmpty()) return slot;
        }
        return fallback;
    }

    private static int menuSlot(int inventorySlot) {
        return Inventory.isHotbarSlot(inventorySlot)
                ? InventoryMenu.USE_ROW_SLOT_START + inventorySlot : inventorySlot;
    }

    private static int findEmptyInventoryFirst(Inventory inventory, int excluded) {
        for (int slot = Inventory.SELECTION_SIZE; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != excluded && inventory.getItem(slot).isEmpty()) return slot;
        }
        for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
            if (slot != excluded && inventory.getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static int findElytraSlot(Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).is(Items.ELYTRA)) return slot;
        }
        return -1;
    }

    private static int findBestChestplateSlot(Inventory inventory) {
        int bestSlot = -1;
        int bestQuality = 0;
        int bestDurability = -1;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            int quality = chestplateQuality(stack);
            int durability = stack.getMaxDamage() - stack.getDamageValue();
            if (quality > 0 && (quality > bestQuality
                    || (quality == bestQuality && durability > bestDurability))) {
                bestSlot = slot;
                bestQuality = quality;
                bestDurability = durability;
            }
        }
        return bestSlot;
    }

    private static int chestplateQuality(ItemStack stack) {
        if (stack.is(Items.NETHERITE_CHESTPLATE)) return 6;
        if (stack.is(Items.DIAMOND_CHESTPLATE)) return 5;
        if (stack.is(Items.IRON_CHESTPLATE)) return 4;
        if (stack.is(Items.CHAINMAIL_CHESTPLATE)) return 3;
        if (stack.is(Items.GOLDEN_CHESTPLATE)) return 2;
        if (stack.is(Items.LEATHER_CHESTPLATE)) return 1;
        return 0;
    }

    private static void showStatus(LocalPlayer player, String message) {
        if (ElytraToggleConfig.showActionbarMessages()) {
            player.sendOverlayMessage(Component.literal(message));
        }
    }

    private static final class PressMapping extends KeyMapping {
        private boolean pressed;

        private PressMapping(String name, int key) {
            super(name, InputConstants.Type.KEYSYM, key, CATEGORY);
        }

        @Override
        public void setDown(boolean down) {
            pressed |= down && !isDown();
            super.setDown(down);
        }

        private boolean consumePress() {
            boolean clicked = consumeClick();
            while (consumeClick()) {
                // Drain repeat events. A held key must not perform another swap.
            }
            boolean result = pressed && clicked;
            pressed = false;
            return result;
        }
    }
}
