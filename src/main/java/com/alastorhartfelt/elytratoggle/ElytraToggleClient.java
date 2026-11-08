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

    private static final KeyMapping SWAP_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.elytratoggle.swap",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,
                    CATEGORY
            )
    );

    // Player-inventory index (0-35) where the Elytra was found.
    private static int originalInventorySlot = -1;

    // Item displaced from the chest slot when this mod equipped the Elytra.
    private static Item displacedChestItem = Items.AIR;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_KEY.consumeClick()) {
                toggleElytra(client, false);
            }
            while (SWAP_KEY.consumeClick()) {
                toggleElytra(client, true);
            }
        });
    }

    private static void toggleElytra(Minecraft client, boolean requireChestItem) {
        if (client.player == null || client.gameMode == null || client.screen != null) {
            return;
        }

        var player = client.player;
        var inventory = player.getInventory();
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);

        // If this mod previously equipped the Elytra, restore it and the displaced chest item.
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
                // Pick up the displaced chest item, take the Elytra, then restore both items.
                clickSlot(client, player, inventorySlotToMenuSlot(originalInventorySlot));
                clickSlot(client, player, CHEST_MENU_SLOT);
                clickSlot(client, player, inventorySlotToMenuSlot(originalInventorySlot));
                clickSlot(client, player, CHEST_MENU_SLOT);
            } else {
                // The original slot is empty, so return the Elytra directly to it.
                clickSlot(client, player, CHEST_MENU_SLOT);
                clickSlot(client, player, inventorySlotToMenuSlot(originalInventorySlot));
            }

            originalInventorySlot = -1;
            displacedChestItem = Items.AIR;
            player.displayClientMessage(Component.literal("Elytra swap reverted."), true);
            return;
        }

        // Do not interfere with an Elytra that was equipped before this mod's action.
        if (chestStack.is(Items.ELYTRA)) {
            player.displayClientMessage(
                    Component.literal("ElytraToggle: This Elytra wasn't equipped by this mod."),
                    true
            );
            return;
        }

        // The dedicated swap key only operates when something is equipped in the chest slot.
        if (requireChestItem && chestStack.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("ElytraToggle: Equip a chestplate first to use Swap Elytra/Chestplate."),
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

        // Equip the Elytra and place the displaced chest item in the Elytra's former slot.
        int sourceMenuSlot = inventorySlotToMenuSlot(elytraSlot);
        clickSlot(client, player, sourceMenuSlot);
        clickSlot(client, player, CHEST_MENU_SLOT);

        if (displacedChestItem != Items.AIR) {
            clickSlot(client, player, sourceMenuSlot);
        }

        player.displayClientMessage(
                Component.literal(displacedChestItem == Items.AIR
                        ? "Elytra equipped."
                        : "Elytra and chest item swapped."),
                true
        );
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
