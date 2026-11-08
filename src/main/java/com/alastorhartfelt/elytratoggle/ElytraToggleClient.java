package com.alastorhartfelt.elytratoggle;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
            new KeyMapping(
                    "key.elytratoggle.toggle",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_R,
                    CATEGORY
            )
    );

    // Player-inventory index (0-35) where the Elytra was found.
    private static int originalInventorySlot = -1;

    // If a chest item was present when equipping, it is moved into the Elytra's old slot.
    private static Item displacedChestItem = Items.AIR;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_KEY.consumeClick()) {
                toggleElytra(client);
            }
        });
    }

    private static void toggleElytra(Minecraft client) {
        if (client.player == null || client.gameMode == null || client.screen != null) {
            return;
        }

        var player = client.player;
        var inventory = player.getInventory();
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);

        // Return the Elytra that this mod equipped.
        if (originalInventorySlot >= 0 && chestStack.is(Items.ELYTRA)) {
            ItemStack originalSlotStack = inventory.getItem(originalInventorySlot);

            if (!originalSlotStack.isEmpty()
                    && originalSlotStack.getItem() != displacedChestItem) {
                player.displayClientMessage(
                        Component.literal("ElytraToggle: Your original inventory slot is occupied."),
                        true
                );
                return;
            }

            if (!originalSlotStack.isEmpty() && displacedChestItem != Items.AIR) {
                // Restore the displaced chest item to the chest slot, and the Elytra to its old slot.
                clickSlot(client, player, inventorySlotToMenuSlot(originalInventorySlot));
                clickSlot(client, player, CHEST_MENU_SLOT);
                clickSlot(client, player, inventorySlotToMenuSlot(originalInventorySlot));
            } else {
                // The chest slot was empty, or the displaced chest item has since been moved.
                clickSlot(client, player, CHEST_MENU_SLOT);
                clickSlot(client, player, inventorySlotToMenuSlot(originalInventorySlot));
            }

            originalInventorySlot = -1;
            displacedChestItem = Items.AIR;
            player.displayClientMessage(Component.literal("Elytra unequipped."), true);
            return;
        }

        // Do not interfere with an Elytra that was equipped before this mod's toggle was used.
        if (chestStack.is(Items.ELYTRA)) {
            player.displayClientMessage(
                    Component.literal("ElytraToggle: This Elytra wasn't equipped by the toggle."),
                    true
            );
            return;
        }

        // Find the first Elytra in the player's 36 main-inventory slots.
        int elytraSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).is(Items.ELYTRA)) {
                elytraSlot = i;
                break;
            }
        }

        if (elytraSlot < 0) {
            player.displayClientMessage(Component.literal("No Elytra found in your inventory."), true);
            return;
        }

        originalInventorySlot = elytraSlot;
        displacedChestItem = chestStack.isEmpty() ? Items.AIR : chestStack.getItem();

        // Pick up the Elytra, put it in the chest slot, then place any displaced chest item
        // into the Elytra's original inventory slot.
        int sourceMenuSlot = inventorySlotToMenuSlot(elytraSlot);
        clickSlot(client, player, sourceMenuSlot);
        clickSlot(client, player, CHEST_MENU_SLOT);

        if (!displacedChestItem.equals(Items.AIR)) {
            clickSlot(client, player, sourceMenuSlot);
        }

        player.displayClientMessage(Component.literal("Elytra equipped."), true);
    }

    private static int inventorySlotToMenuSlot(int inventorySlot) {
        // In the player inventory menu, hotbar slots (0-8) are menu slots (36-44).
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private static void clickSlot(Minecraft client, net.minecraft.client.player.LocalPlayer player, int menuSlot) {
        client.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                menuSlot,
                0,
                ClickType.PICKUP,
                player
        );
    }
}
