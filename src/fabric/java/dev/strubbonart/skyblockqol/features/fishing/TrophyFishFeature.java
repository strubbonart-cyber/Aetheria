package dev.strubbonart.skyblockqol.features.fishing;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local Trophy Fish counter, Odger collection scanner, and configurable HUD. */
public final class TrophyFishFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-trophy-fish.properties");
    private static final Pattern CATCH = Pattern.compile(
            "TROPHY FISH!\\s+You caught an?\\s+(.+?)\\s+(BRONZE|SILVER|GOLD|DIAMOND)!?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RANK_CAUGHT = Pattern.compile(
            "^(?:GOLD\\s+)?(Bronze|Silver|Gold|Diamond)\\s+[✔✓](?:\\s+\\((\\d+)\\))?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RANK_EMPTY = Pattern.compile(
            "^(Bronze|Silver|Gold|Diamond)\\s+[✖✗✘]$",
            Pattern.CASE_INSENSITIVE
    );

    public enum Rarity {
        BRONZE("Bronze", "§8"),
        SILVER("Silver", "§7"),
        GOLD("Gold", "§6"),
        DIAMOND("Diamond", "§b");

        final String display;
        final String color;
        Rarity(String display, String color) {
            this.display = display;
            this.color = color;
        }

        static Rarity parse(String value) {
            for (Rarity rarity : values()) {
                if (rarity.display.equalsIgnoreCase(value)) return rarity;
            }
            return null;
        }
    }

    private static final Map<String, EnumMap<Rarity, Integer>> COUNTS = new LinkedHashMap<>();
    private static boolean initialized;
    private static boolean enabled = true;
    private static int x = 8;
    private static int y = 230;
    private static float scale = 1.0F;

    private TrophyFishFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> recordCatch(message.getString()));
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> appendOdgerTotal(stack, context, type, lines));

        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) ->
                        dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("trophyfish")
                                .executes(command -> {
                                    command.getSource().sendFeedback(Text.literal(status()));
                                    return 1;
                                })
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("reset")
                                        .executes(command -> {
                                            COUNTS.clear();
                                            save();
                                            command.getSource().sendFeedback(Text.literal("Trophy Fish data reset."));
                                            return 1;
                                        }))
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("show")
                                        .executes(command -> {
                                            setEnabled(true);
                                            command.getSource().sendFeedback(Text.literal("Trophy Fish HUD shown."));
                                            return 1;
                                        }))
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("hide")
                                        .executes(command -> {
                                            setEnabled(false);
                                            command.getSource().sendFeedback(Text.literal("Trophy Fish HUD hidden."));
                                            return 1;
                                        }))
                        )
        );
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
        y = 230;
        scale = 1.0F;
        save();
    }

    /** Scans items in Odger's Trophy Fishing menu to seed existing caught tiers. */
    public static void scanOdger(HandledScreen<?> screen) {
        if (screen == null || !strip(screen.getTitle().getString()).equalsIgnoreCase("Trophy Fishing")) return;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;
        int containerSlots = Math.max(0, handler.slots.size() - 36);
        boolean changed = false;
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            String fish = cleanFishName(stack.getName().getString());
            if (fish.isBlank()) continue;
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore == null) continue;
            boolean sawRarity = false;
            for (Text lineText : lore.lines()) {
                String line = strip(lineText.getString()).trim();
                Matcher caught = RANK_CAUGHT.matcher(line);
                if (caught.matches()) {
                    sawRarity = true;
                    Rarity rarity = Rarity.parse(caught.group(1));
                    if (rarity != null && getCount(fish, rarity) == 0) {
                        setCount(fish, rarity, 1);
                        changed = true;
                    }
                } else if (RANK_EMPTY.matcher(line).matches()) {
                    sawRarity = true;
                }
            }
            if (sawRarity) COUNTS.computeIfAbsent(fish, key -> new EnumMap<>(Rarity.class));
        }
        if (changed) save();
    }

    private static void appendOdgerTotal(ItemStack stack, net.minecraft.item.Item.TooltipContext context,
                                         net.minecraft.item.tooltip.TooltipType type, List<Text> lines) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)
                || !strip(screen.getTitle().getString()).equalsIgnoreCase("Trophy Fishing")) return;
        if (stack == null || stack.isEmpty()) return;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().stream().noneMatch(line ->
                strip(line.getString()).toLowerCase(Locale.ROOT).contains("discovered"))) return;
        String fish = cleanFishName(stack.getName().getString());
        int total = getTotal(fish);
        if (total <= 0) return;
        Rarity best = getBestRarity(fish);
        lines.add(Text.empty());
        lines.add(Text.literal("Total catches: " + total + "  Best: " + best.display)
                .styled(style -> style.withColor(best == null ? 0xFFFFFF : colorRgb(best))));
    }

    private static int colorRgb(Rarity rarity) {
        return switch (rarity) {
            case BRONZE -> 0x888888;
            case SILVER -> 0xBBBBBB;
            case GOLD -> 0xFFAA33;
            case DIAMOND -> 0x55DDFF;
        };
    }

    private static void recordCatch(String raw) {
        String clean = strip(raw);
        Matcher matcher = CATCH.matcher(clean);
        if (!matcher.find()) return;
        String fish = cleanFishName(matcher.group(1));
        Rarity rarity = Rarity.parse(matcher.group(2));
        if (fish.isBlank() || rarity == null) return;
        EnumMap<Rarity, Integer> counts = COUNTS.computeIfAbsent(fish, key -> new EnumMap<>(Rarity.class));
        counts.merge(rarity, 1, Integer::sum);
        save();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.world == null || COUNTS.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        lines.add("Trophy Fish");
        COUNTS.entrySet().stream()
                .filter(entry -> getTotal(entry.getKey()) > 0)
                .sorted(Comparator.comparingInt((Map.Entry<String, EnumMap<Rarity, Integer>> entry)
                        -> getTotal(entry.getKey())).reversed())
                .limit(12)
                .forEach(entry -> {
                    String fish = entry.getKey();
                    lines.add(bestRarity(fish).color + fish + "  §8" + getCount(fish, Rarity.BRONZE)
                            + " §7" + getCount(fish, Rarity.SILVER)
                            + " §6" + getCount(fish, Rarity.GOLD)
                            + " §b" + getCount(fish, Rarity.DIAMOND));
                });

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.size() * 12 + 8;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFFFFBB55);
        int textY = 5;
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, lines.get(i), 7, textY,
                    i == 0 ? 0xFFFFDD88 : 0xFFFFFFFF);
            textY += 12;
        }
        context.getMatrices().pop();
    }

    private static int getCount(String fish, Rarity rarity) {
        Map<Rarity, Integer> counts = COUNTS.get(fish);
        return counts == null ? 0 : counts.getOrDefault(rarity, 0);
    }

    private static void setCount(String fish, Rarity rarity, int count) {
        COUNTS.computeIfAbsent(fish, key -> new EnumMap<>(Rarity.class)).put(rarity, Math.max(0, count));
    }

    private static int getTotal(String fish) {
        return java.util.Arrays.stream(Rarity.values()).mapToInt(rarity -> getCount(fish, rarity)).sum();
    }

    private static Rarity bestRarity(String fish) {
        Rarity best = Rarity.BRONZE;
        for (Rarity rarity : Rarity.values()) if (getCount(fish, rarity) > 0) best = rarity;
        return best;
    }

    private static Rarity getBestRarity(String fish) {
        return COUNTS.containsKey(fish) ? bestRarity(fish) : Rarity.BRONZE;
    }

    private static String cleanFishName(String value) {
        return strip(value).replace("✦", "").trim();
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static String status() {
        int total = COUNTS.keySet().stream().mapToInt(TrophyFishFeature::getTotal).sum();
        return "Trophy Fish: " + COUNTS.size() + " species, " + total + " total catches.";
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            x = Integer.parseInt(properties.getProperty("x", "8"));
            y = Integer.parseInt(properties.getProperty("y", "230"));
            scale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(properties.getProperty("scale", "1.0"))));
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("fish.")) continue;
                String[] parts = key.split("\\.");
                if (parts.length != 3) continue;
                String fish = parts[1];
                Rarity rarity;
                try { rarity = Rarity.valueOf(parts[2]); } catch (IllegalArgumentException ignored) { continue; }
                int count = Integer.parseInt(properties.getProperty(key, "0"));
                COUNTS.computeIfAbsent(fish, name -> new EnumMap<>(Rarity.class)).put(rarity, count);
            }
        } catch (IOException | NumberFormatException ignored) {
            COUNTS.clear();
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("x", Integer.toString(x));
        properties.setProperty("y", Integer.toString(y));
        properties.setProperty("scale", Float.toString(scale));
        for (Map.Entry<String, EnumMap<Rarity, Integer>> entry : COUNTS.entrySet()) {
            for (Map.Entry<Rarity, Integer> rarity : entry.getValue().entrySet()) {
                String escapedFish = entry.getKey().replace("\\", "_").replace(".", "_");
                properties.setProperty("fish." + escapedFish + "." + rarity.getKey().name(),
                        Integer.toString(rarity.getValue()));
            }
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL Trophy Fish Data");
            }
        } catch (IOException ignored) {
            // Keep in-memory fish data even when saving is unavailable.
        }
    }
}
