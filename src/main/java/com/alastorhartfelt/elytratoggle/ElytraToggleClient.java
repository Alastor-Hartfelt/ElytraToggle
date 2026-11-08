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

            // R always has priority. If both actions are triggered in the
            // same tick, perform only R so G cannot interfere with its swap.
            if (togglePressed) {
                toggleElytra(client, TOGGLE_STATE);
                return;
            }
            if (swapPressed) {
                swapBestChestplate(client);
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

        // R is an unconditional Elytra toggle: if an Elytra is equipped for
        // any reason (including a previous G swap), R removes it.
        if (chestStack.is(Items.ELYTRA)) {
            int destination = state.toggleActive && isInventorySlotEmpty(inventory, state.originalElytraSlot)
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

    // G: ONLY exchange an equipped Elytra and an actual chestplate.
    // Unlike R, G never equips an Elytra when the chest slot is empty or
    // occupied by some other item.
    private static void swapBestChestplate(Minecraft client) {
        if (!canUseInventory(client)) return;

        LocalPlayer player = client.player;
        var inventory = player.getInventory();
        ItemStack equipped = player.getItemBySlot(EquipmentSlot.CHEST);

        if (equipped.is(Items.ELYTRA)) {
            int chestplateSlot = findBestChestplateSlot(inventory);
            if (chestplateSlot < 0) {
                showStatus(player, "No chestplate found in your inventory.");
                return;
            }

            int menuSlot = inventorySlotToMenuSlot(chestplateSlot);
            clickSlot(client, player, menuSlot);
            clickSlot(client, player, CHEST_MENU_SLOT);
            clickSlot(client, player, menuSlot);
            showStatus(player, "Elytra swapped with your best chestplate.");
            return;
        }

        if (!isChestplate(equipped)) {
            showStatus(player, "G only swaps an equipped Elytra and chestplate.");
            return;
        }

        int elytraSlot = findElytraSlot(inventory);
        if (elytraSlot < 0) {
            showStatus(player, "No Elytra found in your inventory.");
            return;
        }

        int menuSlot = inventorySlotToMenuSlot(elytraSlot);
        clickSlot(client, player, menuSlot);
        clickSlot(client, player, CHEST_MENU_SLOT);
        clickSlot(client, player, menuSlot);
        showStatus(player, "Elytra and chestplate swapped.");
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
