package com.alastorhartfelt.elytratoggle;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ElytraToggleModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }

    private static final class ConfigScreen extends Screen {
        private final Screen parent;

        private ConfigScreen(Screen parent) {
            super(Component.literal("ElytraToggle Settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            this.addRenderableWidget(
                    CycleButton.onOffBuilder(ElytraToggleConfig.showActionbarMessages())
                            .create(
                                    centerX - 100,
                                    centerY - 12,
                                    200,
                                    20,
                                    Component.literal("Show actionbar messages"),
                                    (button, value) -> ElytraToggleConfig.setShowActionbarMessages(value)
                            )
            );

            this.addRenderableWidget(
                    Button.builder(Component.literal("Done"), button -> this.onClose())
                            .bounds(centerX - 100, centerY + 18, 200, 20)
                            .build()
            );
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(this.parent);
        }
    }
}
