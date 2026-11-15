package dev.strubbonart.skyblockqol.features.fishing;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.sound.SoundEvents;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Fishing hook timer with alert sound, HUD display, and editable layout. */
public final class FishingTimerFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-fishing-timer.properties");
    private static boolean initialized;
    private static boolean enabled = true;
    private static int alertTimeSeconds = 15;
    private static int x = 8;
    private static int y = 310;
    private static float scale = 1.0F;
    private static FishingBobberEntity activeHook;
    private static long hookStartedAt;
    private static boolean alertPlayed;

    private FishingTimerFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientTickEvents.END_CLIENT_TICK.register(FishingTimerFeature::tick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) resetHook();
        save();
    }
    public static int getAlertTimeSeconds() { return alertTimeSeconds; }
    public static void adjustAlertTime(int delta) {
        alertTimeSeconds = Math.max(1, Math.min(60, alertTimeSeconds + delta));
        save();
    }
    public static int getX() { return x; }
    public static int getY() { return y; }
    public static float getScale() { return scale; }
    public static void updateLayout(int nextX, int nextY, float nextScale) {
        x = Math.max(0, Math.min(5000, nextX));
        y = Math.max(0, Math.min(5000, nextY));
        scale = Math.max(0.5F, Math.min(3.0F, nextScale));
    }
    public static void saveLayout() { save(); }
    public static void resetLayout() {
        x = 8;
        y = 310;
        scale = 1.0F;
        save();
    }

    private static void tick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) {
            resetHook();
            return;
        }
        FishingBobberEntity hook = client.player.fishHook;
        if (hook == null || hook.isRemoved()) {
            resetHook();
            return;
        }
        if (hook != activeHook) {
            activeHook = hook;
            hookStartedAt = System.currentTimeMillis();
            alertPlayed = false;
        }

        double seconds = (System.currentTimeMillis() - hookStartedAt) / 1000.0D;
        if (seconds >= alertTimeSeconds && !alertPlayed) {
            alertPlayed = true;
            client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.8F);
        } else if (seconds < alertTimeSeconds) {
            alertPlayed = false;
        }
    }

    private static void resetHook() {
        activeHook = null;
        hookStartedAt = 0L;
        alertPlayed = false;
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.player.fishHook == null
                || client.player.fishHook.isRemoved() || hookStartedAt <= 0L) return;

        double seconds = (System.currentTimeMillis() - hookStartedAt) / 1000.0D;
        boolean alerted = seconds >= alertTimeSeconds;
        String timer = String.format(Locale.ROOT, "Fishing: %.1fs", seconds);
        String[] lines = {"Fishing Timer", timer,
                alerted ? "Alert threshold reached" : "Alert at " + alertTimeSeconds + "s"};
        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.length * 12 + 8;

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, alerted ? 0xFFFF5555 : 0xFF55DDEE);
        int textY = 5;
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(client.textRenderer, lines[i], 7, textY,
                    i == 1 && alerted ? 0xFFFF7777 : (i == 0 ? 0xFFFFFFFF : 0xFFD8E6F5));
            textY += 12;
        }
        context.getMatrices().pop();
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            alertTimeSeconds = Math.max(1, Math.min(60,
                    Integer.parseInt(properties.getProperty("alertTimeSeconds", "15"))));
            x = Integer.parseInt(properties.getProperty("x", "8"));
            y = Integer.parseInt(properties.getProperty("y", "310"));
            scale = Math.max(0.5F, Math.min(3.0F,
                    Float.parseFloat(properties.getProperty("scale", "1.0"))));
        } catch (IOException | NumberFormatException ignored) {
            enabled = true;
            alertTimeSeconds = 15;
            x = 8;
            y = 310;
            scale = 1.0F;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("alertTimeSeconds", Integer.toString(alertTimeSeconds));
        properties.setProperty("x", Integer.toString(x));
        properties.setProperty("y", Integer.toString(y));
        properties.setProperty("scale", Float.toString(scale));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL Fishing Timer");
            }
        } catch (IOException ignored) {
            // Keep the current in-memory settings active.
        }
    }
}
