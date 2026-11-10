# ElytraToggle

A lightweight client-side Fabric mod for Minecraft Java Edition 26.2. It requires Java 25, Fabric Loader, and Fabric API. Mod Menu is optional.

## Keybinds

- **R — Toggle Elytra.** Equip an Elytra from your inventory, storing any displaced chest item in an available inventory slot. Press again to remove the Elytra. It goes back to its original slot if that slot is free, otherwise it uses an empty main-inventory slot before an empty hotbar slot. R takes priority if both bindings are pressed in the same tick.
- **G — Swap Elytra and chestplate.** Swap an equipped chestplate with an Elytra in your inventory, or swap an equipped Elytra with the best available chestplate. Chestplates are ranked by material, then by remaining durability among chestplates of the same material. The swap requires both items.

Rebind either action to a keyboard key or mouse button in **Options → Controls → Key Binds**. To change the saved action-bar notification setting, open **Mods → ElytraToggle → Configure** in Mod Menu.
