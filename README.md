# ElytraToggle

A lightweight client-side Fabric mod for Minecraft 1.21.11.

## Keybinds

- **R — Toggle Elytra:** Equip an Elytra from your inventory. Press again to return it to its original inventory slot. If a chest item is equipped, it is moved into the Elytra's former slot and restored when toggled off. This also works when the chest slot is empty.
- **G — Swap Elytra/Chestplate:** Choose the chestplate with the highest maximum durability from your inventory and swap it with your Elytra. For example, a Netherite chestplate is chosen over a Leather chestplate, even if the Leather one has more remaining durability. If your currently equipped chestplate is already the most durable option, G swaps with that instead. The Elytra is placed in the chosen chestplate's original inventory slot, and pressing G again reverses the swap. If no chestplate is equipped or available in inventory, G still equips the Elytra, but that action is one-way and pressing G again will not remove it.

Each keybind tracks its own swap, so one key cannot accidentally reverse the other key's action.

Both keybinds can be changed in Minecraft's **Options → Controls → Key Binds** menu.
