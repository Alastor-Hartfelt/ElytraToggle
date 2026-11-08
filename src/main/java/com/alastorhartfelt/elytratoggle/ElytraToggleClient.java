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
        ElytraToggleConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Drain queued clicks, but run each action at most once per tick.
            // This prevents rapid/repeated input from toggling a swap several
            // times in one tick and leaving the inventory in an unexpected state.
            boolean togglePressed = consumePendingClicks(TOGGLE_KEY);
            boolean swapPressed = consumePendingClicks(SWAP_KEY);

            if (!togglePressed && !swapPressed) {
                return;
            }
            if (!canUseInventory(client)) {
                return;
            }

            // Minecraft permits two actions to be assigned to the same key.
            // Avoid firing both inventory transactions on one physical press.
            if (togglePressed && swapPressed) {
                showStatus(client.player, "ElytraToggle: Assign different keys to R and G actions.");
                return;
            }

            if (togglePressed) {
                toggleElytra(client, TOGGLE_STATE);
            }
            if (swapPressed) {
                swapBestChestplate(client, SWAP_STATE);
            }
        });
    }

    private static boolean consumePendingClicks(KeyMapping key) {
        boolean pressed = false;
        while (key.consumeClick()) {
            pressed = true;
        }
        return pressed;
    }

    // R: equip an Elytra; on the next press return it to its original slot
    // when free, otherwise use the main inventory before the hotbar.
    private static void toggleElytra(Minecraft client, SwapState state) {
        if (!canUseInventory(client)) return;
        LocalPlayer player = client.player;
        var inventory = player.getInventory();
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);

        if (state.toggleActive) {
            if (!chestStack.is(Items.ELYTRA)) {
                showStatus(player, "ElytraToggle: R's Elytra is not equipped.");
                return;
            }
            int destination = isInventorySlotEmpty(inventory, state.originalElytraSlot)
                    ? state.originalElytraSlot : findEmptyInventoryFirst(inventory);
            if (destination < 0) {
                showStatus(player, "ElytraToggle: No empty inventory slot for the Elytra.");
                return;
            }
            clickSlot(client, player, CHEST_MENU_SLOT);
            clickSlot(client, player, inventorySlotToMenuSlot(destination));
            state.clear();
            showStatus(player, "Elytra unequipped.");
            return;
        }

        if (chestStack.is(Items.ELYTRA)) {
            showStatus(player, "ElytraToggle: An Elytra is already equipped.");
            return;
        }
        int elytraSlot = findElytraSlot(inventory);
        if (elytraSlot < 0) {
            showStatus(player, "No Elytra found in your inventory.");
            return;
        }

        state.originalElytraSlot = elytraSlot;
        int elytraMenuSlot = inventorySlotToMenuSlot(elytraSlot);
        clickSlot(client, player, elytraMenuSlot);
        clickSlot(client, player, CHEST_MENU_SLOT);
        if (!chestStack.isEmpty()) {
            int destination = findEmptyInventoryFirst(inventory);
            if (destination < 0) destination = elytraSlot;
            clickSlot(client, player, inventorySlotToMenuSlot(destination));
        }
        state.toggleActive = true;
        showStatus(player, "Elytra equipped.");
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

        // Reverse the simple swap by locating the displaced chestplate again.
        // Its slot may have changed since the original swap.
        if (state.originalElytraSlot >= 0 && state.inventoryChestplateSlot < 0
                && chestStack.is(Items.ELYTRA)) {
            int chestplateSlot = findItemSlot(inventory, state.displacedChestItem, state.originalElytraSlot);
            if (chestplateSlot < 0) {
                showStatus(player, "ElytraToggle: Could not find the displaced chestplate to reverse the swap.");
                return;
            }

            int chestplateMenuSlot = inventorySlotToMenuSlot(chestplateSlot);
            clickSlot(client, player, chestplateMenuSlot);
            clickSlot(client, player, CHEST_MENU_SLOT);
            clickSlot(client, player, chestplateMenuSlot);
            state.clear();
            showStatus(player, "Elytra/chestplate swap reverted.");
            return;
        }

        // Reverse an inventory-chestplate transaction. The Elytra was placed in
        // the selected chestplate's old slot; the old chest item went to the
        // Elytra's original slot.
        if (state.inventoryChestplateSlot >= 0) {
            if (state.elytraWasEquipped) {
                if (!chestStack.is(state.selectedChestplateItem)) {
                    showStatus(player, "ElytraToggle: The equipped chestplate changed; cannot safely reverse the swap.");
                    return;
                }
                int elytraSlot = findElytraSlot(inventory);
                if (elytraSlot < 0) {
                    showStatus(player, "ElytraToggle: Could not find your Elytra to reverse the swap.");
                    return;
                }
                int elytraMenuSlot = inventorySlotToMenuSlot(elytraSlot);
                clickSlot(client, player, elytraMenuSlot);
                clickSlot(client, player, CHEST_MENU_SLOT);
                clickSlot(client, player, elytraMenuSlot);
                state.clear();
                showStatus(player, "Elytra/chestplate swap reverted.");
                return;
            }

            if (chestStack.isEmpty() || chestStack.getItem() != state.selectedChestplateItem) {
                showStatus(player, "ElytraToggle: The equipped chestplate changed; cannot safely reverse the swap.");
                return;
            }

            int elytraSlot = findElytraSlot(inventory);
            if (elytraSlot < 0) {
                showStatus(player, "ElytraToggle: Could not find your Elytra to reverse the swap.");
                return;
            }

            int displacedSlot = state.displacedChestItem == Items.AIR
                    ? -1 : findItemSlot(inventory, state.displacedChestItem, state.originalElytraSlot);
            if (state.displacedChestItem != Items.AIR && displacedSlot < 0) {
                showStatus(player, "ElytraToggle: Could not find the displaced chest item to reverse the swap.");
                return;
            }

            int elytraDestination = displacedSlot >= 0
                    ? displacedSlot
                    : (isInventorySlotEmpty(inventory, state.originalElytraSlot)
                        ? state.originalElytraSlot : findEmptyInventoryFirst(inventory));
            if (elytraDestination < 0 || elytraDestination == elytraSlot) {
                showStatus(player, "ElytraToggle: No safe inventory slot to restore the Elytra.");
                return;
            }

            int chestplateDestination = isInventorySlotEmpty(inventory, state.inventoryChestplateSlot)
                    ? state.inventoryChestplateSlot : findEmptyInventoryFirstExcluding(inventory, elytraDestination);
            if (chestplateDestination < 0 || chestplateDestination == elytraDestination) {
                showStatus(player, "ElytraToggle: No safe inventory slot to restore the chestplate.");
                return;
            }

            int elytraMenuSlot = inventorySlotToMenuSlot(elytraSlot);
            clickSlot(client, player, elytraMenuSlot);
            clickSlot(client, player, inventorySlotToMenuSlot(elytraDestination));
            clickSlot(client, player, CHEST_MENU_SLOT);
            clickSlot(client, player, inventorySlotToMenuSlot(chestplateDestination));

            state.clear();
            showStatus(player, "Elytra/chestplate swap reverted.");
            return;
        }

        if (chestStack.is(Items.ELYTRA)) {
            int bestSlot = findBestChestplateSlot(inventory);
            if (bestSlot < 0) {
                showStatus(player, "No chestplate found in your inventory.");
                return;
            }
            state.inventoryChestplateSlot = bestSlot;
            state.selectedChestplateItem = inventory.getItem(bestSlot).getItem();
            state.elytraWasEquipped = true;
            int menuSlot = inventorySlotToMenuSlot(bestSlot);
            clickSlot(client, player, menuSlot);
            clickSlot(client, player, CHEST_MENU_SLOT);
            clickSlot(client, player, menuSlot);
            showStatus(player, "Elytra swapped with your best chestplate.");
            return;
        }

        int elytraSlot = findElytraSlot(inventory);
        if (elytraSlot < 0) {
            showStatus(player, "No Elytra found in your inventory.");
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
            showStatus(player, "Elytra equipped. No chestplate found; G will not toggle it off.");
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

        showStatus(player, "Best-durability chestplate equipped; Elytra moved to its old slot.");
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

        showStatus(player, "Elytra and equipped chestplate swapped.");
    }

    private static void showStatus(LocalPlayer player, String message) {
        if (ElytraToggleConfig.showActionbarMessages()) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static boolean canUseInventory(Minecraft client) {
        return client.player != null && client.gameMode != null && client.screen == null;
    }

    private static int findItemSlot(
            net.minecraft.world.entity.player.Inventory inventory, Item item, int preferredSlot
    ) {
        if (item == Items.AIR) return -1;
        if (preferredSlot >= 0 && preferredSlot < 36
                && inventory.getItem(preferredSlot).getItem() == item) {
            return preferredSlot;
        }
        for (int i = 0; i < 36; i++) {
            if (i != preferredSlot && inventory.getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    private static int findEmptyInventoryFirstExcluding(
            net.minecraft.world.entity.player.Inventory inventory, int excludedSlot
    ) {
        for (int i = 9; i < 36; i++) {
            if (i != excludedSlot && inventory.getItem(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i != excludedSlot && inventory.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private static int findEmptyInventoryFirst(net.minecraft.world.entity.player.Inventory inventory) {
        // Main inventory first (9-35), then hotbar (0-8).
        for (int i = 9; i < 36; i++) {
            if (inventory.getItem(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private static boolean isInventorySlotEmpty(
            net.minecraft.world.entity.player.Inventory inventory, int slot
    ) {
        return slot >= 0 && slot < 36 && inventory.getItem(slot).isEmpty();
    }

    private static int findBestChestplateSlot(net.minecraft.world.entity.player.Inventory inventory) {
        int bestSlot = -1;
        int bestDurability = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (isChestplate(candidate) && candidate.getMaxDamage() > bestDurability) {
                bestDurability = candidate.getMaxDamage();
                bestSlot = i;
            }
        }
        return bestSlot;
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
        private boolean toggleActive;
        private boolean elytraWasEquipped;
        private int originalElytraSlot = -1;
        private int inventoryChestplateSlot = -1;
        private Item selectedChestplateItem = Items.AIR;
        private Item displacedChestItem = Items.AIR;

        private void clear() {
            toggleActive = false;
            elytraWasEquipped = false;
            originalElytraSlot = -1;
            inventoryChestplateSlot = -1;
            selectedChestplateItem = Items.AIR;
            displacedChestItem = Items.AIR;
        }
    }
}
