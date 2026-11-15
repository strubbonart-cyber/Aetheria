package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Keeps the current pointer coordinates when Minecraft releases the mouse for a GUI. */
public final class CursorResetFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-cursor-reset.properties");
    private static boolean enabled = true;
    private static boolean initialized;

    private CursorResetFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
    }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        } catch (IOException ignored) {
            enabled = true;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL Cursor Position");
            }
        } catch (IOException ignored) {
            // The current session continues to use the selected value.
        }
    }
}
