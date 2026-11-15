package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

/** Configurable, lightweight bouncing DVD text screensaver. */
public final class DvdScreensaverFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-dvd.properties");
    private static boolean initialized;
    private static boolean enabled;
    private static int size = 96;
    private static int speed = 30;
    private static double x = 100.0;
    private static double y = 100.0;
    private static double dx = 1.0;
    private static double dy = 1.0;
    private static int color = 0xFFFFFF;
    private static long lastFrameAt;
    private static boolean started;

    private DvdScreensaverFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("dvd")
                        .executes(context -> {
                            setEnabled(!enabled);
                            context.getSource().sendFeedback(Text.literal("DVD screensaver "
                                    + (enabled ? "enabled." : "disabled.")));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("corner").executes(context -> {
                            forceCornerHit();
                            context.getSource().sendFeedback(Text.literal("DVD logo reset to top-left."));
                            return 1;
                        }))));
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    public static boolean isEnabled() { return enabled; }
    public static int getSize() { return size; }
    public static int getSpeed() { return speed; }

    public static void setEnabled(boolean value) {
        enabled = value;
        started = false;
        lastFrameAt = System.nanoTime();
        save();
    }

    public static void adjustSize(int delta) {
        size = Math.max(48, Math.min(220, size + delta));
        save();
    }

    public static void adjustSpeed(int delta) {
        speed = Math.max(5, Math.min(120, speed + delta));
        save();
    }

    private static void forceCornerHit() {
        x = 0;
        y = 0;
        dx = 1;
        dy = 1;
        color = 0xFFFFFF;
        started = true;
        lastFrameAt = System.nanoTime();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.world == null || client.currentScreen != null) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int boxWidth = size;
        int boxHeight = Math.max(28, (int) Math.round(size * 0.553));

        if (!started) {
            x = Math.max(0, (screenWidth - boxWidth) / 2.0);
            y = Math.max(0, (screenHeight - boxHeight) / 2.0);
            dx = ThreadLocalRandom.current().nextBoolean() ? 1.0 : -1.0;
            dy = ThreadLocalRandom.current().nextBoolean() ? 1.0 : -1.0;
            started = true;
            lastFrameAt = System.nanoTime();
        }

        long now = System.nanoTime();
        double delta = Math.min(0.05, Math.max(0.0, (now - lastFrameAt) / 1_000_000_000.0));
        lastFrameAt = now;
        double movement = speed * delta;
        x += dx * movement;
        y += dy * movement;

        boolean hitX = false;
        boolean hitY = false;
        if (x <= 0) { x = 0; dx = 1; hitX = true; }
        else if (x + boxWidth >= screenWidth) { x = Math.max(0, screenWidth - boxWidth); dx = -1; hitX = true; }

        if (y <= 0) { y = 0; dy = 1; hitY = true; }
        else if (y + boxHeight >= screenHeight) { y = Math.max(0, screenHeight - boxHeight); dy = -1; hitY = true; }

        if (hitX || hitY) color = ThreadLocalRandom.current().nextInt(0x101010, 0xFFFFFF);
        if (hitX && hitY) {
            client.player.sendMessage(Text.literal("§d✦ LEGENDARY CORNER HIT! ✦"), false);
        }

        int px = (int) Math.round(x);
        int py = (int) Math.round(y);
        int border = 0xCC000000 | color;
        context.fill(px, py, px + boxWidth, py + boxHeight, 0x55000000);
        context.fill(px, py, px + boxWidth, py + 2, border);
        context.fill(px, py + boxHeight - 2, px + boxWidth, py + boxHeight, border);
        context.fill(px, py, px + 2, py + boxHeight, border);
        context.fill(px + boxWidth - 2, py, px + boxWidth, py + boxHeight, border);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("DVD"),
                px + boxWidth / 2, py + boxHeight / 2 - 4, color);
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "false"));
            size = Math.max(48, Math.min(220, Integer.parseInt(p.getProperty("size", "96"))));
            speed = Math.max(5, Math.min(120, Integer.parseInt(p.getProperty("speed", "30"))));
        } catch (IOException | NumberFormatException ignored) {
            enabled = false;
            size = 96;
            speed = 30;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("size", Integer.toString(size));
        p.setProperty("speed", Integer.toString(speed));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL DVD Screensaver");
            }
        } catch (IOException ignored) {}
    }
}