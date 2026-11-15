package dev.strubbonart.skyblockqol.features.farming;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Organic Matter tracker port. It tracks item quantities from server DROP
 * messages and applies the same item conversion table as original Aetheria.
 */
public final class GardenOrganicMatterTracker {
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-organic-matter.properties");
    private static final Pattern DROP = Pattern.compile("DROP! \\S+ dropped ([\\d,]+)x (.+)!");
    private static final Map<String, String> NAME_TO_ID = new HashMap<>();
    private static final Map<String, Double> OM_VALUES = new HashMap<>();
    private static final Map<String, Long> COUNTS = new HashMap<>();

    private static boolean initialized;
    private static boolean enabled = true;
    private static long activeTimeMs;
    private static long lastTickAt;
    private static long lastActivityAt;
    private static boolean timerStarted;
    private static int x = 8;
    private static int y = 330;
    private static float scale = 1.0F;

    private GardenOrganicMatterTracker() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        registerItems();
        load();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onGameMessage(message.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("asmorganicmatter")
                        .executes(context -> {
                            context.getSource().sendFeedback(Text.literal("Organic Matter Tracker "
                                    + (enabled ? "ON" : "OFF") + " | " + formatMatter(totalOrganicMatter()) + " OM"));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("on").executes(context -> {
                            setEnabled(true);
                            context.getSource().sendFeedback(Text.literal("Organic Matter Tracker enabled."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("off").executes(context -> {
                            setEnabled(false);
                            context.getSource().sendFeedback(Text.literal("Organic Matter Tracker disabled."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            reset();
                            context.getSource().sendFeedback(Text.literal("Organic Matter Tracker reset."));
                            return 1;
                        }))
                ));
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }

    public static void reset() {
        COUNTS.clear();
        activeTimeMs = 0L;
        timerStarted = false;
        lastActivityAt = 0L;
        save();
    }

    public static int getX() { return x; }
    public static int getY() { return y; }
    public static float getScale() { return scale; }

    public static void updateLayout(int newX, int newY, float newScale) {
        x = Math.max(0, Math.min(5000, newX));
        y = Math.max(0, Math.min(5000, newY));
        scale = Math.max(0.5F, Math.min(3.0F, newScale));
    }

    public static void saveLayout() { save(); }

    public static void resetLayout() {
        x = 8;
        y = 330;
        scale = 1.0F;
        save();
    }

    private static void onGameMessage(String raw) {
        if (!enabled || !GardenFeatures.isInGarden()) return;
        String clean = stripFormatting(raw);
        Matcher matcher = DROP.matcher(clean);
        if (!matcher.find()) return;

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return;
        }

        String id = NAME_TO_ID.get(normalize(matcher.group(2)));
        if (id == null || !OM_VALUES.containsKey(id)) return;

        COUNTS.merge(id, amount, Long::sum);
        lastActivityAt = System.currentTimeMillis();
        timerStarted = true;
        save();
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (lastTickAt == 0L) lastTickAt = now;
        long delta = Math.max(0L, Math.min(1_000L, now - lastTickAt));
        lastTickAt = now;

        if (enabled && timerStarted && now - lastActivityAt <= 7_000L && GardenFeatures.isInGarden()) {
            activeTimeMs += delta;
        }
        if (now - lastActivityAt > 7_000L && timerStarted) {
            timerStarted = false;
            save();
        }
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.world == null || !GardenFeatures.isInGarden()) return;

        double totalMatter = totalOrganicMatter();
        long totalItems = COUNTS.values().stream().mapToLong(Long::longValue).sum();
        double perHour = activeTimeMs <= 0L ? 0.0 : totalMatter * 3_600_000.0 / activeTimeMs;
        String[] lines = {
                "Organic Matter Tracker",
                "Total Organic Matter: " + formatMatter(totalMatter),
                formatMatter(perHour) + "/h organic matter",
                "Total: " + String.format(Locale.ROOT, "%,d", totalItems) + " items",
                "Playtime: " + formatDuration(activeTimeMs)
        };

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.length * 12 + 8;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFF55DDEE);
        int textY = 5;
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(client.textRenderer, lines[i], 7, textY,
                    i == 0 ? 0xFF55DD99 : 0xFFFFFFFF);
            textY += 12;
        }
        matrices.pop();
    }

    private static double totalOrganicMatter() {
        double total = 0.0;
        for (Map.Entry<String, Long> entry : COUNTS.entrySet()) {
            total += entry.getValue() * OM_VALUES.getOrDefault(entry.getKey(), 0.0);
        }
        return total;
    }

    private static String formatMatter(double value) {
        return String.format(Locale.ROOT, "%,.1f", value);
    }

    private static String formatDuration(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600L, (seconds % 3600L) / 60L);
    }

    private static void registerItems() {
        item("Wheat", "WHEAT", 1);
        item("Enchanted Wheat", "ENCHANTED_WHEAT", 160);
        item("Enchanted Hay Bale", "ENCHANTED_HAY_BLOCK", 25600);
        item("Enchanted Hay Bale", "ENCHANTED_HAY_BALE", 25600);

        item("Carrot", "CARROT_ITEM", 0.29);
        item("Carrots", "CARROT_ITEM", 0.29);
        item("Enchanted Carrot", "ENCHANTED_CARROT", 46.4);
        item("Enchanted Golden Carrot", "ENCHANTED_GOLDEN_CARROT", 5939.2);

        item("Potato", "POTATO_ITEM", 0.33);
        item("Enchanted Potato", "ENCHANTED_POTATO", 52.8);
        item("Enchanted Baked Potato", "ENCHANTED_BAKED_POTATO", 8448);

        item("Pumpkin", "PUMPKIN", 1);
        item("Enchanted Pumpkin", "ENCHANTED_PUMPKIN", 160);
        item("Polished Pumpkin", "POLISHED_PUMPKIN", 25600);

        item("Melon", "MELON", 0.2);
        item("Enchanted Melon", "ENCHANTED_MELON", 32);
        item("Enchanted Melon Block", "ENCHANTED_MELON_BLOCK", 5120);

        item("Sugar Cane", "SUGAR_CANE", 0.5);
        item("Enchanted Sugar", "ENCHANTED_SUGAR", 80);
        item("Enchanted Sugar Cane", "ENCHANTED_SUGAR_CANE", 12800);

        item("Cocoa Beans", "INK_SACK__3", 0.4);
        item("Enchanted Cocoa Beans", "ENCHANTED_COCOA", 64);
        item("Enchanted Cookie", "ENCHANTED_COOKIE", 0);

        item("Cactus", "CACTUS", 0.5);
        item("Enchanted Cactus Green", "ENCHANTED_CACTUS_GREEN", 80);
        item("Enchanted Cactus", "ENCHANTED_CACTUS", 12800);

        item("Red Mushroom", "RED_MUSHROOM", 1);
        item("Enchanted Red Mushroom", "ENCHANTED_RED_MUSHROOM", 160);
        item("Enchanted Red Mushroom Block", "ENCHANTED_RED_MUSHROOM_BLOCK", 5184);

        item("Brown Mushroom", "BROWN_MUSHROOM", 1);
        item("Enchanted Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM", 160);
        item("Enchanted Brown Mushroom Block", "ENCHANTED_BROWN_MUSHROOM_BLOCK", 5184);

        item("Nether Wart", "NETHER_STALK", 0.33);
        item("Enchanted Nether Wart", "ENCHANTED_NETHER_STALK", 52.8);
        item("Mutant Nether Wart", "MUTANT_NETHER_STALK", 8448);

        item("Seeds", "SEEDS", 1);
        item("Enchanted Seeds", "ENCHANTED_SEEDS", 160);
        item("Box of Seeds", "SEED_BOX", 25600);

        item("Squash", "SQUASH", 10000);
        item("Cropie", "CROPIE", 2500);
        item("Fermento", "FERMENTO", 20000);
        item("Condensed Fermento", "CONDENSED_FERMENTO", 180000);
    }

    private static void item(String name, String id, double value) {
        NAME_TO_ID.put(normalize(name), id);
        OM_VALUES.put(id, value);
    }

    private static String normalize(String value) {
        return stripFormatting(value).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void load() {
        if (!Files.isRegularFile(DATA_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(DATA_FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            activeTimeMs = Long.parseLong(properties.getProperty("activeTimeMs", "0"));
            x = Integer.parseInt(properties.getProperty("x", "8"));
            y = Integer.parseInt(properties.getProperty("y", "330"));
            scale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(properties.getProperty("scale", "1.0"))));
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("count.")) {
                    COUNTS.put(key.substring(6), Long.parseLong(properties.getProperty(key, "0")));
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            COUNTS.clear();
            activeTimeMs = 0L;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("activeTimeMs", Long.toString(activeTimeMs));
        properties.setProperty("x", Integer.toString(x));
        properties.setProperty("y", Integer.toString(y));
        properties.setProperty("scale", Float.toString(scale));
        for (Map.Entry<String, Long> entry : COUNTS.entrySet()) {
            properties.setProperty("count." + entry.getKey(), Long.toString(entry.getValue()));
        }
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(DATA_FILE)) {
                properties.store(output, "Aetheria Garden Organic Matter Tracker");
            }
        } catch (IOException ignored) {
            // Continue with live in-memory data if config storage is unavailable.
        }
    }
}