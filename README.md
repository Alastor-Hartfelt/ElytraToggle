# ElytraToggle

A lightweight client-side Fabric mod for Minecraft 1.21.11.

## Keybinds

- **R / Mouse 4 — Toggle Elytra:** Equip an Elytra from your inventory. Press again to return it to its original slot if that slot is free; otherwise it uses the first empty main-inventory slot, then the hotbar. When equipping, a displaced chest item is placed in the main inventory before the hotbar.
- **G / Mouse 5 — Swap Elytra/Chestplate:** Swap an inventory Elytra with the best available chestplate, or, if an Elytra is already equipped, swap it with the highest-maximum-durability chestplate in your inventory. Press the same binding again to reverse the swap. If no chestplate exists, G can still equip an inventory Elytra, but that action cannot be toggled off with G.

Mouse 4 and Mouse 5 are the two common side buttons on a mouse. All four bindings can be changed in Minecraft's **Options → Controls → Key Binds** menu.

Each action keeps its own state so R cannot accidentally reverse a G swap, and vice versa.
