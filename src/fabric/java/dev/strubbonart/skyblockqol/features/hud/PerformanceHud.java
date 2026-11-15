package dev.strubbonart.skyblockqol.features.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** FPS, ping, coordinates, and rotation HUD with persistent position and scale. */
public final class PerformanceHud {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-performance-hud.properties");
    private static boolean initialized;
    private static boolean enabled = true;
    private static int x = 8;
    private static int y = 8;
    private static float scale = 1.0F;

    private PerformanceHud() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        HudRenderCallback.EVENT.register(PerformanceHud::render);
    }

    public static boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
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
        y = 8;
        scale = 1.0F;
        save();
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        List<String> lines = new ArrayList<>();
        lines.add("Skyblock QoL | Performance HUD");
        PlayerListEntry entry = client.getNetworkHandler() == null ? null
                : client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        String ping = entry == null ? "--" : entry.getLatency() + " ms";
        lines.add("FPS: " + client.getCurrentFps() + "   Ping: " + ping);
        lines.add(String.format(Locale.ROOT, "XYZ: %.1f  %.1f  %.1f",
                client.player.getX(), client.player.getY(), client.player.getZ()));
        lines.add(String.format(Locale.ROOT, "Yaw: %.1f   Pitch: %.1f",
                client.player.getYaw(), client.player.getPitch()));

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.size() * 12 + 8;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFF55DDEE);
        int textY = 5;
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, lines.get(i), 7, textY,
                    i == 0 ? 0xFF55DDEE : 0xFFFFFFFF);
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
            x = Integer.parseInt(properties.getProperty("x", "8"));
            y = Integer.parseInt(properties.getProperty("y", "8"));
            scale = Math.max(0.5F, Math.min(3.0F,
                    Float.parseFloat(properties.getProperty("scale", "1.0"))));
        } catch (IOException | NumberFormatException ignored) {
            enabled = true;
            x = 8;
            y = 8;
            scale = 1.0F;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("x", Integer.toString(x));
        properties.setProperty("y", Integer.toString(y));
        properties.setProperty("scale", Float.toString(scale));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL Performance HUD");
            }
        } catch (IOException ignored) {
            // Keep in-memory settings active if disk storage is unavailable.
        }
    }
}
