package com.alastorhartfelt.elytratoggle;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ElytraToggleConfig {
    private static final String ACTIONBAR_MESSAGES_KEY = "showActionbarMessages";
    private static boolean showActionbarMessages = true;

    private ElytraToggleConfig() {
    }

    public static void load() {
        Path file = getConfigPath();
        if (!Files.exists(file)) {
            save();
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            showActionbarMessages = Boolean.parseBoolean(
                    properties.getProperty(ACTIONBAR_MESSAGES_KEY, "true")
            );
        } catch (IOException ignored) {
            showActionbarMessages = true;
        }
    }

    public static boolean showActionbarMessages() {
        return showActionbarMessages;
    }

    public static void setShowActionbarMessages(boolean enabled) {
        showActionbarMessages = enabled;
        save();
    }

    private static void save() {
        Path file = getConfigPath();
        Properties properties = new Properties();
        properties.setProperty(ACTIONBAR_MESSAGES_KEY, Boolean.toString(showActionbarMessages));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "ElytraToggle client configuration");
            }
        } catch (IOException ignored) {
            // Keep the in-memory setting even if the config file cannot be written.
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("elytratoggle.properties");
    }
}
