package dev.strubbonart.skyblockqol.features.farming;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Garden pest kill parser, local tracker HUD, and pest cooldown alert. */
public final class GardenPestFeatures {
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-pests.properties");
    private static final Pattern PEST_KILL = Pattern.compile(
            "You received ([\\d,]+)x (.+?) for killing (?:a|an) (.+?)!");
    private static final Pattern DURATION = Pattern.compile(
            "(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?");

    private static final Map<String, Long> KILLS = new HashMap<>();
    private static final Map<String, Long> DROPS = new HashMap<>();
    private static boolean initialized;
    private static boolean trackerEnabled = true;
    private static boolean overlayVisible;
    private static boolean cooldownAlertEnabled;
    private static int cooldownThreshold = 30;
    private static boolean cooldownArmed = true;
    private static long alertUntil;
    private static long lastCooldownMs = -1L;
    private static long cooldownReadAt;
    private static long sessionStartedAt = System.currentTimeMillis();
    private static long lastSaveAt;

    private static int hudX = 8;
    private static int hudY = 250;
    private static float hudScale = 1.0F;

    private GardenPestFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> recordMessage(message.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("pesttracker")
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            resetTracker();
                            context.getSource().sendFeedback(Text.literal("Pest Tracker data reset."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("show").executes(context -> {
                            setOverlayVisible(true);
                            context.getSource().sendFeedback(Text.literal("Pest Tracker HUD shown."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("hide").executes(context -> {
                            setOverlayVisible(false);
                            context.getSource().sendFeedback(Text.literal("Pest Tracker HUD hidden."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("toggle").executes(context -> {
                            setOverlayVisible(!overlayVisible);
                            context.getSource().sendFeedback(Text.literal("Pest Tracker HUD " + (overlayVisible ? "shown." : "hidden.")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(status()));
                            return 1;
                        }))
                ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("asmpest")
                        .then(ClientCommandManager.literal("off").executes(context -> {
                            setCooldownAlertEnabled(false);
                            context.getSource().sendFeedback(Text.literal("Pest cooldown alert disabled."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(
                                    "Pest alert " + (cooldownAlertEnabled ? "ON" : "OFF")
                                            + ", threshold " + cooldownThreshold + "s"));
                            return 1;
                        }))
                        .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(5, 300))
                                .executes(context -> {
                                    cooldownThreshold = IntegerArgumentType.getInteger(context, "seconds");
                                    cooldownAlertEnabled = true;
                                    save();
                                    context.getSource().sendFeedback(Text.literal(
                                            "Pest cooldown alert set to " + cooldownThreshold + " seconds."));
                                    return 1;
                                }))
                ));
    }

    public static boolean isTrackerEnabled() { return trackerEnabled; }
    public static boolean isCooldownAlertEnabled() { return cooldownAlertEnabled; }
    public static int getCooldownThreshold() { return cooldownThreshold; }

    public static void setTrackerEnabled(boolean value) {
        trackerEnabled = value;
        if (!value) overlayVisible = false;
        save();
    }

    public static void setCooldownAlertEnabled(boolean value) {
        cooldownAlertEnabled = value;
        cooldownArmed = true;
        save();
    }

    public static void adjustCooldownThreshold(int delta) {
        cooldownThreshold = Math.max(5, Math.min(300, cooldownThreshold + delta));
        save();
    }

    public static void resetTracker() {
        KILLS.clear();
        DROPS.clear();
        sessionStartedAt = System.currentTimeMillis();
        save();
    }

    public static void setOverlayVisible(boolean value) {
        overlayVisible = value && trackerEnabled;
    }

    public static int getLayoutX() { return hudX; }
    public static int getLayoutY() { return hudY; }
    public static float getLayoutScale() { return hudScale; }

    public static void updateLayout(int x, int y, float scale) {
        hudX = Math.max(0, Math.min(5000, x));
        hudY = Math.max(0, Math.min(5000, y));
        hudScale = Math.max(0.5F, Math.min(3.0F, scale));
    }

    public static void saveLayout() { save(); }

    public static void resetLayout() {
        hudX = 8;
        hudY = 250;
        hudScale = 1.0F;
        save();
    }

    private static void recordMessage(String raw) {
        if (!trackerEnabled || !GardenFeatures.isInGarden()) return;
        String message = stripFormatting(raw);
        Matcher matcher = PEST_KILL.matcher(message);
        if (!matcher.find()) return;
        long quantity;
        try {
            quantity = Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return;
        }
        String drop = matcher.group(2).trim();
        String pest = matcher.group(3).trim();
        KILLS.merge(pest, 1L, Long::sum);
        DROPS.merge(drop, quantity, Long::sum);
        overlayVisible = true;
        save();
    }

    private static void tick() {
        if (!cooldownAlertEnabled || !GardenFeatures.isInGarden()) {
            cooldownArmed = true;
            lastCooldownMs = -1L;
            return;
        }

        long remaining = readCooldownMs();
        if (remaining < 0L) {
            cooldownArmed = true;
            lastCooldownMs = -1L;
            return;
        }
        lastCooldownMs = remaining;
        cooldownReadAt = System.currentTimeMillis();

        long threshold = cooldownThreshold * 1000L;
        if (!cooldownArmed && remaining > threshold) cooldownArmed = true;
        if (cooldownArmed && remaining <= threshold) {
            cooldownArmed = false;
            alertUntil = System.currentTimeMillis() + 3_000L;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§6[Garden] §cPest cooldown is below "
                        + cooldownThreshold + "s!"), false);
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastSaveAt > 30_000L) save();
    }

    private static long readCooldownMs() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return -1L;
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            Text display = entry.getDisplayName();
            String line = stripFormatting(display == null
                    ? entry.getProfile().getName() : display.getString()).trim();
            int marker = line.indexOf("Cooldown:");
            if (marker < 0) continue;
            return parseDuration(line.substring(marker + "Cooldown:".length()).trim());
        }
        return -1L;
    }

    private static long parseDuration(String value) {
        if (value.matches("\\d+:\\d{2}")) {
            String[] parts = value.split(":");
            try {
                return (Long.parseLong(parts[0]) * 60L + Long.parseLong(parts[1])) * 1000L;
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        Matcher matcher = DURATION.matcher(value);
        if (!matcher.find()) return -1L;
        long millis = 0L;
        boolean found = false;
        if (matcher.group(1) != null) { millis += Long.parseLong(matcher.group(1)) * 3_600_000L; found = true; }
        if (matcher.group(2) != null) { millis += Long.parseLong(matcher.group(2)) * 60_000L; found = true; }
        if (matcher.group(3) != null) { millis += Long.parseLong(matcher.group(3)) * 1_000L; found = true; }
        return found ? millis : -1L;
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !GardenFeatures.isInGarden()) return;

        if (trackerEnabled && overlayVisible) {
            long kills = KILLS.values().stream().mapToLong(Long::longValue).sum();
            long drops = DROPS.values().stream().mapToLong(Long::longValue).sum();
            String[] lines = new String[Math.min(6, 3 + KILLS.size())];
            lines[0] = "Pest Tracker";
            lines[1] = "Kills: " + kills + " | Drops: " + drops;
            long elapsed = Math.max(1_000L, System.currentTimeMillis() - sessionStartedAt);
            lines[2] = "Session: " + formatDuration(elapsed);
            int index = 3;
            for (Map.Entry<String, Long> entry : KILLS.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).limit(3).toList()) {
                if (index >= lines.length) break;
                lines[index++] = entry.getKey() + ": " + entry.getValue();
            }
            renderPanel(context, lines);
        }

        long now = System.currentTimeMillis();
        if (cooldownAlertEnabled && now < alertUntil) {
            long remaining = Math.max(0L, lastCooldownMs - (now - cooldownReadAt));
            context.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal("PEST ALERT  " + formatDuration(remaining)),
                    context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 2 - 30, 0xFFFF5555);
        }
    }

    private static void renderPanel(DrawContext context, String[] lines) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.length * 12 + 8;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(hudX, hudY, 0.0F);
        matrices.scale(hudScale, hudScale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFF55DDEE);
        int y = 5;
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(client.textRenderer, lines[i], 7, y,
                    i == 0 ? 0xFFFFFFFF : 0xFFD8E6F5);
            y += 12;
        }
        matrices.pop();
    }

    private static String status() {
        long kills = KILLS.values().stream().mapToLong(Long::longValue).sum();
        long drops = DROPS.values().stream().mapToLong(Long::longValue).sum();
        return "Pest Tracker " + (trackerEnabled ? "enabled" : "disabled")
                + " | kills " + kills + " | drops " + drops
                + " | HUD " + (overlayVisible ? "shown" : "hidden");
    }

    private static String formatDuration(long millis) {
        long sec = Math.max(0L, millis / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", sec / 60L, sec % 60L);
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void load() {
        if (!Files.isRegularFile(DATA_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(DATA_FILE)) {
            properties.load(input);
            trackerEnabled = Boolean.parseBoolean(properties.getProperty("trackerEnabled", "true"));
            overlayVisible = Boolean.parseBoolean(properties.getProperty("overlayVisible", "false"));
            cooldownAlertEnabled = Boolean.parseBoolean(properties.getProperty("cooldownAlertEnabled", "false"));
            cooldownThreshold = Math.max(5, Math.min(300,
                    Integer.parseInt(properties.getProperty("cooldownThreshold", "30"))));
            hudX = Integer.parseInt(properties.getProperty("hudX", "8"));
            hudY = Integer.parseInt(properties.getProperty("hudY", "250"));
            hudScale = Math.max(0.5F, Math.min(3.0F,
                    Float.parseFloat(properties.getProperty("hudScale", "1.0"))));
            sessionStartedAt = System.currentTimeMillis();
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("kill.")) KILLS.put(key.substring(5), Long.parseLong(properties.getProperty(key, "0")));
                if (key.startsWith("drop.")) DROPS.put(key.substring(5), Long.parseLong(properties.getProperty(key, "0")));
            }
        } catch (IOException | NumberFormatException ignored) {
            KILLS.clear();
            DROPS.clear();
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("trackerEnabled", Boolean.toString(trackerEnabled));
        properties.setProperty("overlayVisible", Boolean.toString(overlayVisible));
        properties.setProperty("cooldownAlertEnabled", Boolean.toString(cooldownAlertEnabled));
        properties.setProperty("cooldownThreshold", Integer.toString(cooldownThreshold));
        properties.setProperty("hudX", Integer.toString(hudX));
        properties.setProperty("hudY", Integer.toString(hudY));
        properties.setProperty("hudScale", Float.toString(hudScale));
        for (Map.Entry<String, Long> entry : KILLS.entrySet()) {
            properties.setProperty("kill." + entry.getKey(), Long.toString(entry.getValue()));
        }
        for (Map.Entry<String, Long> entry : DROPS.entrySet()) {
            properties.setProperty("drop." + entry.getKey(), Long.toString(entry.getValue()));
        }
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(DATA_FILE)) {
                properties.store(output, "Skyblock QoL Garden Pest Settings");
            }
        } catch (IOException ignored) {
            // Keep the live settings active if storage is unavailable.
        }
        lastSaveAt = System.currentTimeMillis();
    }
}