package dev.strubbonart.skyblockqol.features.farming;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only Garden visitor shopping helper. It learns item requirements and
 * rewards from the hovered server-provided Accept Offer item tooltip.
 */
public final class GardenVisitorFeatures {
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-visitors.properties");
    private static final Pattern NAME_X_AMOUNT = Pattern.compile("^(.+?) x([\\d,]+)$");
    private static final Pattern AMOUNT_X_NAME = Pattern.compile("^([\\d,]+)x (.+)$");
    private static final Pattern COPPER = Pattern.compile("^\\+([\\d,.]+) Copper$");
    private static final Pattern BITS = Pattern.compile("^\\+([\\d,.]+) Bits$");

    private static final Map<String, Integer> NEEDS = new LinkedHashMap<>();
    private static final Map<String, Integer> REWARDS = new LinkedHashMap<>();
    private static final List<String> BONUS_LINES = new ArrayList<>();

    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean overlayVisible;
    private static String visitorName = "Garden Visitor";
    private static long capturedAt;
    private static int x = 8;
    private static int y = 330;
    private static float scale = 1.0F;

    private GardenVisitorFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("visitlist")
                        .then(ClientCommandManager.literal("show").executes(context -> {
                            if (!NEEDS.isEmpty()) overlayVisible = true;
                            context.getSource().sendFeedback(Text.literal("Visitor shopping list shown."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("hide").executes(context -> {
                            overlayVisible = false;
                            context.getSource().sendFeedback(Text.literal("Visitor shopping list hidden."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            clear();
                            context.getSource().sendFeedback(Text.literal("Visitor shopping list cleared."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(
                                    "Visitor list " + (enabled ? "enabled" : "disabled")
                                            + " | " + NEEDS.size() + " required item types"
                                            + " | " + (overlayVisible ? "shown" : "hidden")));
                            return 1;
                        }))
                ));
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) overlayVisible = false;
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
        y = 330;
        scale = 1.0F;
        save();
    }

    /** Called from HandledScreen.render while the Accept Offer item is hovered. */
    public static void captureOffer(net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen,
                                    ItemStack stack) {
        if (!enabled || !GardenFeatures.isInGarden() || stack == null || stack.isEmpty()) return;
        String stackName = stripFormatting(stack.getName().getString()).trim();
        if (!stackName.toLowerCase(Locale.ROOT).contains("accept offer")) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
        Map<String, Integer> newNeeds = new LinkedHashMap<>();
        Map<String, Integer> newRewards = new LinkedHashMap<>();
        List<String> newBonuses = new ArrayList<>();
        String section = "";

        for (Text lineText : tooltip) {
            String line = stripFormatting(lineText.getString()).trim();
            if (line.equalsIgnoreCase("Items Required:")) {
                section = "needs";
                continue;
            }
            if (line.equalsIgnoreCase("Rewards")) {
                section = "rewards";
                continue;
            }
            if (line.isEmpty() || line.startsWith("You ") || line.startsWith("Click ")) continue;

            Matcher copper = COPPER.matcher(line);
            if (copper.matches()) {
                newBonuses.add("Copper: +" + copper.group(1));
                continue;
            }
            Matcher bits = BITS.matcher(line);
            if (bits.matches()) {
                newBonuses.add("Bits: +" + bits.group(1));
                continue;
            }

            if (!section.equals("needs") && !section.equals("rewards")) continue;
            Matcher itemAmount = NAME_X_AMOUNT.matcher(line);
            String name = null;
            int amount = 0;
            if (itemAmount.matches()) {
                name = itemAmount.group(1).trim();
                amount = parseAmount(itemAmount.group(2));
            } else {
                itemAmount = AMOUNT_X_NAME.matcher(line);
                if (itemAmount.matches()) {
                    amount = parseAmount(itemAmount.group(1));
                    name = itemAmount.group(2).trim();
                }
            }
            if (name == null || name.isBlank() || amount <= 0) continue;
            Map<String, Integer> destination = section.equals("needs") ? newNeeds : newRewards;
            destination.merge(name, amount, Integer::sum);
        }

        if (newNeeds.isEmpty() && newRewards.isEmpty() && newBonuses.isEmpty()) return;
        NEEDS.clear();
        NEEDS.putAll(newNeeds);
        REWARDS.clear();
        REWARDS.putAll(newRewards);
        BONUS_LINES.clear();
        BONUS_LINES.addAll(newBonuses);

        String title = stripFormatting(screen.getTitle().getString()).trim();
        visitorName = title.isBlank() ? "Garden Visitor" : title;
        capturedAt = System.currentTimeMillis();
        overlayVisible = true;
        save();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || !overlayVisible || client.player == null || client.world == null
                || !GardenFeatures.isInGarden() || NEEDS.isEmpty()) return;

        if (System.currentTimeMillis() - capturedAt > 600_000L) {
            overlayVisible = false;
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Visitor Shopping List");
        lines.add(visitorName);
        int shown = 0;
        int totalTypes = NEEDS.size();
        for (Map.Entry<String, Integer> item : NEEDS.entrySet()) {
            int have = countInventory(item.getKey());
            int missing = Math.max(0, item.getValue() - have);
            String status = missing == 0 ? "READY" : "Need " + missing;
            lines.add(abbreviate(item.getKey(), 20) + ": " + have + "/" + item.getValue() + " (" + status + ")");
            shown++;
            if (shown >= 4) break;
        }
        if (totalTypes > shown) lines.add("+" + (totalTypes - shown) + " more required items");
        for (String bonus : BONUS_LINES) {
            if (lines.size() >= 8) break;
            lines.add(bonus);
        }
        if (!REWARDS.isEmpty() && lines.size() < 8) {
            Map.Entry<String, Integer> first = REWARDS.entrySet().iterator().next();
            lines.add("Reward: " + abbreviate(first.getKey(), 16) + " x" + first.getValue());
        }

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.size() * 12 + 8;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFF68B8FF);
        int textY = 5;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFF8CCFFF : 0xFFD8E6F5;
            if (lines.get(i).contains("(READY)")) color = 0xFF77DD88;
            context.drawTextWithShadow(client.textRenderer, lines.get(i), 7, textY, color);
            textY += 12;
        }
        matrices.pop();
    }

    private static int countInventory(String displayName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0;
        String target = normalize(displayName);
        int count = 0;
        for (ItemStack stack : client.player.getInventory().main) {
            if (!stack.isEmpty() && normalize(stack.getName().getString()).equals(target)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private static int parseAmount(String value) {
        try {
            return Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String normalize(String value) {
        return stripFormatting(value).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void clear() {
        NEEDS.clear();
        REWARDS.clear();
        BONUS_LINES.clear();
        capturedAt = 0L;
        overlayVisible = false;
        save();
    }

    private static void load() {
        if (!Files.isRegularFile(DATA_FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(DATA_FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            overlayVisible = Boolean.parseBoolean(p.getProperty("overlayVisible", "false"));
            visitorName = p.getProperty("visitorName", "Garden Visitor");
            x = Integer.parseInt(p.getProperty("x", "8"));
            y = Integer.parseInt(p.getProperty("y", "330"));
            scale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(p.getProperty("scale", "1.0"))));
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("need.")) NEEDS.put(key.substring(5), Integer.parseInt(p.getProperty(key, "0")));
                if (key.startsWith("reward.")) REWARDS.put(key.substring(7), Integer.parseInt(p.getProperty(key, "0")));
                if (key.startsWith("bonus.")) BONUS_LINES.add(p.getProperty(key, ""));
            }
            if (!NEEDS.isEmpty()) capturedAt = System.currentTimeMillis();
        } catch (IOException | NumberFormatException ignored) {
            clear();
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("overlayVisible", Boolean.toString(overlayVisible));
        p.setProperty("visitorName", visitorName);
        p.setProperty("x", Integer.toString(x));
        p.setProperty("y", Integer.toString(y));
        p.setProperty("scale", Float.toString(scale));
        for (Map.Entry<String, Integer> entry : NEEDS.entrySet()) {
            p.setProperty("need." + entry.getKey(), Integer.toString(entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : REWARDS.entrySet()) {
            p.setProperty("reward." + entry.getKey(), Integer.toString(entry.getValue()));
        }
        for (int i = 0; i < BONUS_LINES.size(); i++) {
            p.setProperty("bonus." + i, BONUS_LINES.get(i));
        }
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(DATA_FILE)) {
                p.store(out, "Skyblock QoL Garden Visitor Settings");
            }
        } catch (IOException ignored) {
            // Keep in-memory visitor list if saving fails.
        }
    }
}