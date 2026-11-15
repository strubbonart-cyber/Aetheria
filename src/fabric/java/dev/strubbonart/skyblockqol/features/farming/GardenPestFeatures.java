package dev.strubbonart.skyblockqol.features.farming;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;
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
import java.util.List;
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
    private static final Map<Integer, Integer> ACTIVE_PEST_PLOTS = new java.util.TreeMap<>();
    private static final Box[] GARDEN_PLOT_BOUNDS = createPlotBounds();
    private static boolean initialized;
    private static boolean trackerEnabled = true;
    private static boolean overlayVisible;
    private static boolean cooldownAlertEnabled;
    private static boolean finderEnabled = true;
    private static boolean warpEnabled = true;
    private static boolean showFinderOnlyWithVacuum = true;
    private static int warpTargetMode;
    private static KeyBinding warpKey;
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

    private static int finderX = 8;
    private static int finderY = 520;
    private static float finderScale = 1.0F;
    private static String gardenAlive = "-";
    private static String gardenPlots = "-";
    private static String gardenSpray = "-";
    private static String gardenRepellent = "-";
    private static String gardenBonus = "-";
    private static String gardenCooldown = "-";
    private static String gardenBonusPestChance = "-";
    private static final Pattern PLOT_ENTRY = Pattern.compile("^(\\d+)(?:\\s*[xX]\\s*(\\d+))?$");

    private GardenPestFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        warpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skyblockqol.pest_warp",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.skyblockqol"
        ));

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
                        .then(ClientCommandManager.literal("test").executes(context -> {
                            long now = System.currentTimeMillis();
                            lastCooldownMs = cooldownThreshold * 1000L;
                            cooldownReadAt = now;
                            alertUntil = now + 3_000L;
                            context.getSource().sendFeedback(Text.literal("Pest cooldown alert test triggered."));
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
    public static boolean isFinderEnabled() { return finderEnabled; }
    public static void setFinderEnabled(boolean value) { finderEnabled = value; save(); }
    public static boolean isWarpEnabled() { return warpEnabled; }
    public static void setWarpEnabled(boolean value) { warpEnabled = value; save(); }
    public static boolean isShowFinderOnlyWithVacuum() { return showFinderOnlyWithVacuum; }
    public static void setShowFinderOnlyWithVacuum(boolean value) { showFinderOnlyWithVacuum = value; save(); }
    public static int getWarpTargetMode() { return warpTargetMode; }
    public static void cycleWarpTargetMode() { warpTargetMode = (warpTargetMode + 1) % 2; save(); }

    public static int getFinderLayoutX() { return finderX; }
    public static int getFinderLayoutY() { return finderY; }
    public static float getFinderLayoutScale() { return finderScale; }

    public static void updateFinderLayout(int x, int y, float scale) {
        finderX = Math.max(0, Math.min(5000, x));
        finderY = Math.max(0, Math.min(5000, y));
        finderScale = Math.max(0.5F, Math.min(3.0F, scale));
    }

    public static void saveFinderLayout() { save(); }

    public static void resetFinderLayout() {
        finderX = 8;
        finderY = 520;
        finderScale = 1.0F;
        save();
    }

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
        boolean inGarden = GardenFeatures.isInGarden();
        if (inGarden) readGardenTablistData();
        MinecraftClient client = MinecraftClient.getInstance();
        if (warpKey != null) {
            while (warpKey.wasPressed()) {
                if (warpEnabled && inGarden && client.currentScreen == null
                        && client.player != null && client.getNetworkHandler() != null) {
                    Integer plot = selectTargetPlot();
                    if (plot != null && !isPlayerInsidePlot(plot, client)) {
                        client.getNetworkHandler().sendChatCommand("tptoplot " + plot);
                    } else if (client.player != null) {
                        client.player.sendMessage(Text.literal("§6[Garden] §eNo other infested plot to warp to."), true);
                    }
                }
            }
        }

        if (!cooldownAlertEnabled || !inGarden) {
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
            List<String> lines = new java.util.ArrayList<>();
            lines.add("Pest Tracker");
            lines.add("Total pests: " + kills + " | Total drops: " + drops);
            long elapsed = Math.max(1_000L, System.currentTimeMillis() - sessionStartedAt);
            lines.add("Session: " + formatDuration(elapsed));
            KILLS.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .limit(2)
                    .forEach(entry -> lines.add(entry.getKey() + ": " + entry.getValue() + " kills"));
            DROPS.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .limit(2)
                    .forEach(entry -> lines.add(entry.getKey() + ": " + entry.getValue() + " drops"));
            renderPanel(context, lines.toArray(String[]::new));
        }

        if (finderEnabled && (!showFinderOnlyWithVacuum || isHoldingVacuum(client))) {
            renderFinder(context);
        }

        long now = System.currentTimeMillis();
        if (cooldownAlertEnabled && now < alertUntil) {
            long remaining = Math.max(0L, lastCooldownMs - (now - cooldownReadAt));
            context.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal("PEST ALERT  " + formatDuration(remaining)),
                    context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 2 - 30, 0xFFFF5555);
        }
    }

    private static void readGardenTablistData() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return;

        Map<Integer, Integer> plots = new java.util.TreeMap<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            Text display = entry.getDisplayName();
            String line = stripFormatting(display == null
                    ? entry.getProfile().getName() : display.getString()).trim();
            if (line.startsWith("Alive: ")) {
                gardenAlive = line.substring("Alive: ".length()).trim();
            } else if (line.startsWith("Plots: ")) {
                String value = line.substring("Plots: ".length()).trim();
                ACTIVE_PEST_PLOTS.clear();
                for (String part : value.split(",")) {
                    Matcher matcher = PLOT_ENTRY.matcher(part.trim());
                    if (!matcher.matches()) continue;
                    try {
                        int plot = Integer.parseInt(matcher.group(1));
                        int count = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
                        if (plot >= 1 && plot <= 24 && count > 0) {
                            plots.put(plot, count);
                            ACTIVE_PEST_PLOTS.put(plot, count);
                        }
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed tablist plot values.
                    }
                }
            } else if (line.startsWith("Spray: ")) {
                gardenSpray = line.substring("Spray: ".length()).trim();
            } else if (line.startsWith("Repellent: ")) {
                gardenRepellent = line.substring("Repellent: ".length()).trim();
            } else if (line.startsWith("Bonus: ")) {
                gardenBonus = line.substring("Bonus: ".length()).trim();
            } else if (line.startsWith("Cooldown: ")) {
                gardenCooldown = line.substring("Cooldown: ".length()).trim();
            } else if (line.startsWith("Bonus Pest Chance: ")) {
                gardenBonusPestChance = line.substring("Bonus Pest Chance: ".length()).trim();
            }
        }

        if (!plots.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (Map.Entry<Integer, Integer> entry : plots.entrySet()) {
                if (text.length() > 0) text.append(", ");
                text.append(entry.getKey());
                if (entry.getValue() > 1) text.append(" x").append(entry.getValue());
            }
            gardenPlots = text.toString();
        } else {
            gardenPlots = "-";
        }
    }

    private static void renderFinder(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        Integer targetPlot = selectTargetPlot();
        String warpHint = warpEnabled && targetPlot != null
                ? "Warp key: " + warpKey.getBoundKeyLocalizedText().getString() + " -> Plot " + targetPlot
                : "Warp key: unbound / no infested plots";
        String[] lines = {
                "Pest Finder",
                "Total alive: " + gardenAlive,
                "Infested plots: " + gardenPlots,
                "Spray: " + gardenSpray,
                "Repellent: " + gardenRepellent,
                "Bonus: " + gardenBonus,
                "Cooldown: " + gardenCooldown,
                "Bonus pest chance: " + gardenBonusPestChance,
                warpHint
        };
        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.length * 12 + 8;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(finderX, finderY, 0.0F);
        matrices.scale(finderScale, finderScale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFF68B8FF);
        int y = 5;
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(client.textRenderer, lines[i], 7, y,
                    i == 0 ? 0xFF8CCFFF : 0xFFD8E6F5);
            y += 12;
        }
        matrices.pop();
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
            finderEnabled = Boolean.parseBoolean(properties.getProperty("finderEnabled", "true"));
            warpEnabled = Boolean.parseBoolean(properties.getProperty("warpEnabled", "true"));
            showFinderOnlyWithVacuum = Boolean.parseBoolean(properties.getProperty("showFinderOnlyWithVacuum", "true"));
            warpTargetMode = Math.max(0, Math.min(1, Integer.parseInt(properties.getProperty("warpTargetMode", "0"))));
            cooldownThreshold = Math.max(5, Math.min(300,
                    Integer.parseInt(properties.getProperty("cooldownThreshold", "30"))));
            hudX = Integer.parseInt(properties.getProperty("hudX", "8"));
            hudY = Integer.parseInt(properties.getProperty("hudY", "250"));
            hudScale = Math.max(0.5F, Math.min(3.0F,
                    Float.parseFloat(properties.getProperty("hudScale", "1.0"))));
            finderX = Integer.parseInt(properties.getProperty("finderX", "8"));
            finderY = Integer.parseInt(properties.getProperty("finderY", "520"));
            finderScale = Math.max(0.5F, Math.min(3.0F,
                    Float.parseFloat(properties.getProperty("finderScale", "1.0"))));
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
        properties.setProperty("finderEnabled", Boolean.toString(finderEnabled));
        properties.setProperty("warpEnabled", Boolean.toString(warpEnabled));
        properties.setProperty("showFinderOnlyWithVacuum", Boolean.toString(showFinderOnlyWithVacuum));
        properties.setProperty("warpTargetMode", Integer.toString(warpTargetMode));
        properties.setProperty("finderX", Integer.toString(finderX));
        properties.setProperty("finderY", Integer.toString(finderY));
        properties.setProperty("finderScale", Float.toString(finderScale));
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

    private static boolean isHoldingVacuum(MinecraftClient client) {
        if (client.player == null) return false;
        var stack = client.player.getMainHandStack();
        if (stack.isEmpty()) return false;
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        NbtCompound root = customData.copyNbt();
        NbtCompound extra = root.contains("ExtraAttributes") ? root.getCompound("ExtraAttributes") : root;
        return extra.getString("id").toUpperCase(Locale.ROOT).contains("VACUUM");
    }

    private static Integer selectTargetPlot() {
        if (ACTIVE_PEST_PLOTS.isEmpty()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return ACTIVE_PEST_PLOTS.keySet().iterator().next();
        Integer best = null;
        for (Integer candidate : ACTIVE_PEST_PLOTS.keySet()) {
            if (candidate < 1 || candidate > GARDEN_PLOT_BOUNDS.length) continue;
            if (isPlayerInsidePlot(candidate, client)) continue;
            if (best == null || beatsTarget(candidate, best, client)) best = candidate;
        }
        return best;
    }

    private static boolean beatsTarget(int candidate, int current, MinecraftClient client) {
        if (warpTargetMode == 1) {
            int candidateCount = ACTIVE_PEST_PLOTS.getOrDefault(candidate, 0);
            int currentCount = ACTIVE_PEST_PLOTS.getOrDefault(current, 0);
            if (candidateCount != currentCount) return candidateCount > currentCount;
        }
        return distanceToPlotSquared(candidate, client) < distanceToPlotSquared(current, client);
    }

    private static double distanceToPlotSquared(int plot, MinecraftClient client) {
        if (client.player == null || plot < 1 || plot > GARDEN_PLOT_BOUNDS.length) return Double.MAX_VALUE;
        Box box = GARDEN_PLOT_BOUNDS[plot - 1];
        double dx = client.player.getX() - (box.minX + box.maxX) * 0.5D;
        double dz = client.player.getZ() - (box.minZ + box.maxZ) * 0.5D;
        return dx * dx + dz * dz;
    }

    private static boolean isPlayerInsidePlot(int plot, MinecraftClient client) {
        if (client.player == null || plot < 1 || plot > GARDEN_PLOT_BOUNDS.length) return false;
        return GARDEN_PLOT_BOUNDS[plot - 1].contains(client.player.getPos());
    }

    private static Box[] createPlotBounds() {
        return new Box[]{
                new Box(192, 67, 96, 288, 100, 192), new Box(96, 67, 192, 192, 100, 288),
                new Box(192, 67, 288, 288, 100, 384), new Box(288, 67, 192, 384, 100, 288),
                new Box(96, 67, 96, 192, 100, 192), new Box(288, 67, 96, 384, 100, 192),
                new Box(96, 67, 288, 192, 100, 384), new Box(288, 67, 288, 384, 100, 384),
                new Box(192, 67, 0, 288, 100, 96), new Box(0, 67, 192, 96, 100, 288),
                new Box(384, 67, 192, 480, 100, 288), new Box(192, 67, 384, 288, 100, 480),
                new Box(96, 67, 0, 192, 100, 96), new Box(288, 67, 0, 384, 100, 96),
                new Box(0, 67, 96, 96, 100, 192), new Box(384, 67, 96, 480, 100, 192),
                new Box(0, 67, 288, 96, 100, 384), new Box(384, 67, 288, 480, 100, 384),
                new Box(96, 67, 384, 192, 100, 480), new Box(288, 67, 384, 384, 100, 480),
                new Box(0, 67, 0, 96, 100, 96), new Box(384, 67, 0, 480, 100, 96),
                new Box(0, 67, 384, 96, 100, 480), new Box(384, 67, 384, 480, 100, 480)
        };
    }

}