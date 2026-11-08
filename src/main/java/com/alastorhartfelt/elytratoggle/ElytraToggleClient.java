package com.alastorhartfelt.elytratoggle;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class ElytraToggleClient implements ClientModInitializer {
    private static final int CHEST_MENU_SLOT = 6;

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("elytratoggle", "main"));

    private static final KeyMapping TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.elytratoggle.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY)
    );

    private static final KeyMapping SWAP_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.elytratoggle.swap", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY)
    );

    // Each hotkey owns its own transaction so pressing one cannot undo the other.
    private static final SwapState TOGGLE_STATE = new SwapState();
    private static final SwapState SWAP_STATE = new SwapState();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_KEY.consumeClick()) {
                toggleElytra(client, TOGGLE_STATE);
            }
            while (SWAP_KEY.consumeClick()) {
                swapBestChestplate(client, SWAP_STATE);
            }
        });
    }

    // R: equip an Elytra and return it to its original slot on the next press.
    private static void toggleElytra(Minecraft client, SwapState state) {
        if (!canUseInventory(client)) {
            return;
        }

        LocalPlayer player = client.player;
        var inventory = player.getInventory();
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);

        if (state.originalElytraSlot >= 0) {
            if (!chestStack.is(Items.ELYTRA)) {
                player.displayClientMessage(
                        Component.literal("ElytraToggle: R's Elytra is no longer in the chest slot; move it back before toggling off."),
                        true
                );
                return;
            }
            if (!canRestoreSimpleSwap(inventory.getItem(state.originalElytraSlot), state)) {
                player.displayClientMessage(
                        Component.literal("ElytraToggle: Your original inventory slot has changed."),
                        true
                );
                return;
            }

            int originalMenuSlot = inventorySlotToMenuSlot(state.originalElytraSlot);
            clickSlot(client, player, CHEST_MENU_SLOT);
            clickSlot(client, player, originalMenuSlot);

            state.clear();
            player.displayClientMessage(Component.literal("Elytra toggle reverted."), true);
            return;
        }

        if (chestStack.is(Items.ELYTRA)) {
            player.displayClientMessage(Component.literal("ElytraToggle: An Elytra is already equipped."), true);
            return;
        }

        int elytraSlot = findElytraSlot(inventory);
        if (elytraSlot < 0) {
            player.displayClientMessage(Component.literal("No Elytra found in your inventory."), true);
            return;
        }

        state.originalElytraSlot = elytraSlot;
        state.displacedChestItem = chestStack.isEmpty() ? Items.AIR : chestStack.getItem();

        int elytraMenuSlot = inventorySlotToMenuSlot(elytraSlot);
        clickSlot(client, player, elytraMenuSlot);
        clickSlot(client, player, CHEST_MENU_SLOT);
        if (state.displacedChestItem != Items.AIR) {
            clickSlot(client, player, elytraMenuSlot);
        }

        player.displayClientMessage(
                Component.literal(state.displacedChestItem == Items.AIR
                        ? "Elytra equipped."
                        : "Elytra and chest item swapped."),
                true
        );
    }

    // G: prefer the highest-maximum-durability chestplate in inventory. If the
    // equipped chestplate is already the best one, swap with that instead.
    private static void swapBestChestplate(Minecraft client, SwapState state) {
        if (!canUseInventory(client)) {
            return;
        }

        LocalPlayer player = client.player;
        var inventory = player.getInventory();
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);

        // Reverse the simple case where the best chestplate was already equipped.
        if (state.originalElytraSlot >= 0 && state.inventoryChestplateSlot < 0
                && chestStack.is(Items.ELYTRA)) {
            ItemStack originalSlotStack = inventory.getItem(state.originalElytraSlot);
            if (!canRestoreSimpleSwap(originalSlotStack, state)) {
                player.displayClientMessage(
                        Component.literal("ElytraToggle: Your original inventory slot has changed."),
                        true
                );
                return;
            }

            int originalMenuSlot = inventorySlotToMenuSlot(state.originalElytraSlot);
            clickSlot(client, player, originalMenuSlot);
            clickSlot(client, player, CHEST_MENU_SLOT);
            clickSlot(client, player, originalMenuSlot);
            state.clear();
            player.displayClientMessage(Component.literal("Elytra/chestplate swap reverted."), true);
            return;
        }

        // Reverse an inventory-chestplate transaction. The Elytra was placed in
        // the selected chestplate's old slot; the old chest item went to the
        // Elytra's original slot.
        if (state.inventoryChestplateSlot >= 0) {
            ItemStack selectedSlot = inventory.getItem(state.inventoryChestplateSlot);
            ItemStack elytraSlot = inventory.getItem(state.originalElytraSlot);
            boolean chestStillMatches = !chestStack.isEmpty()
                    && chestStack.getItem() == state.selectedChestplateItem;
            boolean selectedSlotHasElytra = selectedSlot.is(Items.ELYTRA);
            boolean elytraSlotMatches = state.displacedChestItem == Items.AIR
                    ? elytraSlot.isEmpty()
                    : !elytraSlot.isEmpty() && elytraSlot.getItem() == state.displacedChestItem;

            if (!chestStillMatches || !selectedSlotHasElytra || !elytraSlotMatches) {
                player.displayClientMessage(
                        Component.literal("ElytraToggle: Inventory changed; cannot safely reverse the swap."),
                        true
                );
                return;
            }

            int selectedMenuSlot = inventorySlotToMenuSlot(state.inventoryChestplateSlot);
            int elytraMenuSlot = inventorySlotToMenuSlot(state.originalElytraSlot);
            clickSlot(client, player, selectedMenuSlot);
            clickSlot(client, player, elytraMenuSlot);
            clickSlot(client, player, CHEST_MENU_SLOT);
            clickSlot(client, player, selectedMenuSlot);

            state.clear();
            player.displayClientMessage(Component.literal("Elytra/chestplate swap reverted."), true);
            return;
        }

        if (chestStack.is(Items.ELYTRA)) {
            player.displayClientMessage(Component.literal("ElytraToggle: An Elytra is already equipped."), true);
            return;
        }

        int elytraSlot = findElytraSlot(inventory);
        if (elytraSlot < 0) {
            player.displayClientMessage(Component.literal("No Elytra found in your inventory."), true);
            return;
        }

        int bestChestplateSlot = -1;
        int bestDurability = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (isChestplate(candidate)) {
                int durability = candidate.getMaxDamage();
                if (durability > bestDurability) {
                    bestDurability = durability;
                    bestChestplateSlot = i;
                }
            }
        }

        // If the equipped chestplate is at least as durable as the best one in
        // inventory, retain the simple chest-slot swap behavior.
        if (isChestplate(chestStack)
                && chestStack.getMaxDamage() >= bestDurability) {
            performSimpleSwap(client, player, chestStack, elytraSlot, state);
            return;
        }

        if (bestChestplateSlot < 0) {
            // No chestplate exists anywhere: G still equips the Elytra, but this
            // is intentionally one-way. Pressing G again will not remove it.
            ItemStack displaced = chestStack;
            int elytraMenuSlot = inventorySlotToMenuSlot(elytraSlot);
            clickSlot(client, player, elytraMenuSlot);
            clickSlot(client, player, CHEST_MENU_SLOT);
            if (!displaced.isEmpty()) {
                clickSlot(client, player, elytraMenuSlot);
            }
            player.displayClientMessage(
                    Component.literal("Elytra equipped. No chestplate found; G will not toggle it off."),
                    true
            );
            return;
        }

        // Four clicks perform a safe three-item rotation:
        // selected chestplate -> chest slot, old chest item -> Elytra slot,
        // Elytra -> selected chestplate's former inventory slot.
        state.originalElytraSlot = elytraSlot;
        state.inventoryChestplateSlot = bestChestplateSlot;
        state.selectedChestplateItem = inventory.getItem(bestChestplateSlot).getItem();
        state.displacedChestItem = chestStack.isEmpty() ? Items.AIR : chestStack.getItem();

        int chestplateMenuSlot = inventorySlotToMenuSlot(bestChestplateSlot);
        int elytraMenuSlot = inventorySlotToMenuSlot(elytraSlot);
        clickSlot(client, player, chestplateMenuSlot);
        clickSlot(client, player, CHEST_MENU_SLOT);
        clickSlot(client, player, elytraMenuSlot);
        clickSlot(client, player, chestplateMenuSlot);

        player.displayClientMessage(
                Component.literal("Best-durability chestplate equipped; Elytra moved to its old slot."),
                true
        );
    }

    private static void performSimpleSwap(
            Minecraft client, LocalPlayer player, ItemStack chestStack, int elytraSlot, SwapState state
    ) {
        state.originalElytraSlot = elytraSlot;
        state.displacedChestItem = chestStack.getItem();

        int elytraMenuSlot = inventorySlotToMenuSlot(elytraSlot);
        clickSlot(client, player, elytraMenuSlot);
        clickSlot(client, player, CHEST_MENU_SLOT);
        clickSlot(client, player, elytraMenuSlot);

        player.displayClientMessage(Component.literal("Elytra and equipped chestplate swapped."), true);
    }

    private static boolean canUseInventory(Minecraft client) {
        return client.player != null && client.gameMode != null && client.screen == null;
    }

    private static boolean canRestoreSimpleSwap(ItemStack originalSlotStack, SwapState state) {
        return originalSlotStack.isEmpty()
                || (state.displacedChestItem != Items.AIR
                && originalSlotStack.getItem() == state.displacedChestItem);
    }

    private static int findElytraSlot(net.minecraft.world.entity.player.Inventory inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).is(Items.ELYTRA)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == Items.LEATHER_CHESTPLATE
                || item == Items.CHAINMAIL_CHESTPLATE
                || item == Items.IRON_CHESTPLATE
                || item == Items.GOLDEN_CHESTPLATE
                || item == Items.DIAMOND_CHESTPLATE
                || item == Items.NETHERITE_CHESTPLATE;
    }

    private static int inventorySlotToMenuSlot(int inventorySlot) {
        // In the player inventory menu, hotbar slots (0-8) are menu slots (36-44).
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private static void clickSlot(Minecraft client, LocalPlayer player, int menuSlot) {
        client.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                menuSlot,
                0,
                ClickType.PICKUP,
                player
        );
    }

    private static final class SwapState {
        private int originalElytraSlot = -1;
        private int inventoryChestplateSlot = -1;
        private Item selectedChestplateItem = Items.AIR;
        private Item displacedChestItem = Items.AIR;

        private void clear() {
            originalElytraSlot = -1;
            inventoryChestplateSlot = -1;
            selectedChestplateItem = Items.AIR;
            displacedChestItem = Items.AIR;
        }
    }
}
