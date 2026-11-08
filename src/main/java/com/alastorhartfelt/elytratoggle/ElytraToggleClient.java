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

    // Keep each hotkey's transaction separate so pressing one cannot undo the other.
    private static final SwapState TOGGLE_STATE = new SwapState();
    private static final SwapState SWAP_STATE = new SwapState();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_KEY.consumeClick()) {
                toggleElytra(client, false, TOGGLE_STATE);
            }
            while (SWAP_KEY.consumeClick()) {
                toggleElytra(client, true, SWAP_STATE);
            }
        });
    }

    private static void toggleElytra(Minecraft client, boolean requireChestItem, SwapState state) {
        if (client.player == null || client.gameMode == null || client.screen != null) {
            return;
        }

        var player = client.player;
        var inventory = player.getInventory();
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);

        // Reverse only the transaction started by this specific hotkey.
        if (state.originalInventorySlot >= 0 && chestStack.is(Items.ELYTRA)) {
            ItemStack originalSlotStack = inventory.getItem(state.originalInventorySlot);

            if (!originalSlotStack.isEmpty()
                    && originalSlotStack.getItem() != state.displacedChestItem) {
                player.displayClientMessage(
                        Component.literal("ElytraToggle: Your original inventory slot is occupied."),
                        true
                );
                return;
            }

            if (!originalSlotStack.isEmpty() && state.displacedChestItem != Items.AIR) {
                clickSlot(client, player, inventorySlotToMenuSlot(state.originalInventorySlot));
                clickSlot(client, player, CHEST_MENU_SLOT);
                clickSlot(client, player, inventorySlotToMenuSlot(state.originalInventorySlot));
            } else {
                clickSlot(client, player, CHEST_MENU_SLOT);
                clickSlot(client, player, inventorySlotToMenuSlot(state.originalInventorySlot));
            }

            state.clear();
            player.displayClientMessage(Component.literal("Elytra swap reverted."), true);
            return;
        }

        // Never take over an Elytra that was equipped before this hotkey was used.
        if (chestStack.is(Items.ELYTRA)) {
            player.displayClientMessage(
                    Component.literal("ElytraToggle: An Elytra is already equipped."),
                    true
            );
            return;
        }

        // G is specifically the Elytra/chest-slot swap: it requires an equipped item.
        // It deliberately does not search for, equip, or otherwise inspect inventory chestplates.
        if (requireChestItem && chestStack.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("ElytraToggle: Equip a chestplate first to use Swap Elytra/Chestplate."),
                    true
            );
            return;
        }

        // Only search for an Elytra. Inventory chestplates are intentionally ignored.
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

        state.originalInventorySlot = elytraSlot;
        state.displacedChestItem = chestStack.isEmpty() ? Items.AIR : chestStack.getItem();

        // Put the currently equipped chest item in the Elytra's former inventory slot.
        int sourceMenuSlot = inventorySlotToMenuSlot(elytraSlot);
        clickSlot(client, player, sourceMenuSlot);
        clickSlot(client, player, CHEST_MENU_SLOT);

        if (state.displacedChestItem != Items.AIR) {
            clickSlot(client, player, sourceMenuSlot);
        }

        player.displayClientMessage(
                Component.literal(state.displacedChestItem == Items.AIR
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

    private static final class SwapState {
        private int originalInventorySlot = -1;
        private Item displacedChestItem = Items.AIR;

        private void clear() {
            originalInventorySlot = -1;
            displacedChestItem = Items.AIR;
        }
    }
}
