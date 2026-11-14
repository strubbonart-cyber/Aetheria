package dev.strubbonart.skyblockqol.features.dungeons;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Saved independent layout settings for the Dungeon HUD overlays. */
public final class DungeonOverlaySettings {
    public enum Widget { TIMER, SPLITS, BREAKER }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-dungeon-layout.properties");

    private static int timerX = 8, timerY = 156;
    private static int splitsX = 8, splitsY = 178;
    private static int breakerX = 8, breakerY = 112;
    private static float timerScale = 1.0F, splitsScale = 1.0F, breakerScale = 1.0F;
    private static boolean loaded;

    private DungeonOverlaySettings() {}

    public static void initialize() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            timerX = integer(p, "timerX", 8);
            timerY = integer(p, "timerY", 156);
            splitsX = integer(p, "splitsX", 8);
            splitsY = integer(p, "splitsY", 178);
            breakerX = integer(p, "breakerX", 8);
            breakerY = integer(p, "breakerY", 112);
            timerScale = decimal(p, "timerScale", 1F);
            splitsScale = decimal(p, "splitsScale", 1F);
            breakerScale = decimal(p, "breakerScale", 1F);
        } catch (IOException ignored) {
            resetValues();
        }
    }

    public static int x(Widget widget) {
        initialize();
        return switch (widget) {
            case TIMER -> timerX;
            case SPLITS -> splitsX;
            case BREAKER -> breakerX;
        };
    }

    public static int y(Widget widget) {
        initialize();
        return switch (widget) {
            case TIMER -> timerY;
            case SPLITS -> splitsY;
            case BREAKER -> breakerY;
        };
    }

    public static float scale(Widget widget) {
        initialize();
        return switch (widget) {
            case TIMER -> timerScale;
            case SPLITS -> splitsScale;
            case BREAKER -> breakerScale;
        };
    }

    public static void set(Widget widget, int x, int y, float scale) {
        initialize();
        switch (widget) {
            case TIMER -> { timerX = x; timerY = y; timerScale = scale; }
            case SPLITS -> { splitsX = x; splitsY = y; splitsScale = scale; }
            case BREAKER -> { breakerX = x; breakerY = y; breakerScale = scale; }
        }
        save();
    }

    public static void reset() {
        resetValues();
        save();
    }

    private static void resetValues() {
        timerX = 8; timerY = 156;
        splitsX = 8; splitsY = 178;
        breakerX = 8; breakerY = 112;
        timerScale = splitsScale = breakerScale = 1.0F;
    }

    private static int integer(Properties p, String key, int fallback) {
        try { return Integer.parseInt(p.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static float decimal(Properties p, String key, float fallback) {
        try {
            float value = Float.parseFloat(p.getProperty(key, Float.toString(fallback)));
            return Float.isFinite(value) ? Math.max(0.5F, Math.min(3.0F, value)) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("timerX", Integer.toString(timerX));
        p.setProperty("timerY", Integer.toString(timerY));
        p.setProperty("splitsX", Integer.toString(splitsX));
        p.setProperty("splitsY", Integer.toString(splitsY));
        p.setProperty("breakerX", Integer.toString(breakerX));
        p.setProperty("breakerY", Integer.toString(breakerY));
        p.setProperty("timerScale", String.format(Locale.ROOT, "%.2f", timerScale));
        p.setProperty("splitsScale", String.format(Locale.ROOT, "%.2f", splitsScale));
        p.setProperty("breakerScale", String.format(Locale.ROOT, "%.2f", breakerScale));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Dungeon HUD layout");
            }
        } catch (IOException ignored) {
            // Layout changes remain active during the current session.
        }
    }
}
