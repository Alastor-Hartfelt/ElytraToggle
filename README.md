# ElytraToggle

A lightweight client-side Fabric mod for Minecraft 1.21.11.

## Keybinds

- **R — Toggle Elytra:** Equip an Elytra from your inventory. Press again to unequip it into the first empty inventory slot. If an item was in the chest slot when equipping, that item is moved into the first available inventory slot.
- **G — Swap Elytra/Chestplate:** Swap an Elytra with the highest-maximum-durability chestplate available. If an Elytra is already equipped, G can swap it with the best chestplate in your inventory; pressing G again reverses that swap. If a chestplate is equipped and an Elytra is in your inventory, G swaps them as before. If no chestplate exists, G can still equip an inventory Elytra, but that action cannot be toggled off with G.

Each keybind tracks its own swap, so one key cannot accidentally reverse the other key's action.

Both keybinds can be changed in Minecraft's **Options → Controls → Key Binds** menu.
