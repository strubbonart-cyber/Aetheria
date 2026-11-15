package dev.strubbonart.skyblockqol.features.qol;

import net.minecraft.item.Item;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds XP-to-max-level information to SkyBlock skill and dungeon item tooltips. */
public final class SkillXpTooltipFeature {
    private static final Pattern XP_LINE = Pattern.compile("^([\\d,]+)/[\\d.,]+[KMBk]?$");
    private static final Pattern PROGRESS_LINE = Pattern.compile("Progress to Level (\\d+):");
    private static final String HINT = "[SHIFT: XP to max level]";

    private static final long[] STANDARD_XP = {50,125,200,300,500,750,1000,1500,2000,3500,5000,7500,10000,15000,20000,30000,50000,75000,100000,200000,300000,400000,500000,600000,700000,800000,900000,1000000,1100000,1200000,1300000,1400000,1500000,1600000,1700000,1800000,1900000,2000000,2100000,2200000,2300000,2400000,2500000,2600000,2750000,2900000,3100000,3400000,3700000,4000000,4300000,4600000,4900000,5200000,5500000,5800000,6100000,6400000,6700000,7000000};
    private static final long[] DUNGEON_XP = {50,75,110,160,230,330,470,670,950,1340,1890,2665,3760,5260,7380,10300,14400,20000,27600,38000,52500,71500,97000,132000,180000,243000,328000,445000,600000,800000,1065000,1410000,1900000,2500000,3300000,4300000,5600000,7200000,9200000,12000000,15000000,19000000,24000000,30000000,38000000,48000000,60000000,75000000,93000000,116250000};
    private static final long[] RUNE_SOCIAL_XP = {50,100,125,160,200,250,315,400,500,625,785,1000,1250,1565,2000,2500,3125,4000,5000,6250,7850,9800,12250,15300,19100};

    private static boolean enabled = true;
    private static boolean initialized;

    private SkillXpTooltipFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ItemTooltipCallback.EVENT.register(SkillXpTooltipFeature::onTooltip);
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    private static void onTooltip(ItemStack stack, Item.TooltipContext context,
                                  TooltipType type, List<Text> lines) {
        if (!enabled || stack.isEmpty() || lines.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return;

        String title = strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        boolean isSkill = title.contains("view skills") || title.contains("your skills");
        boolean isDungeon = title.contains("view dungeon stats") || title.contains("dungeoneering");
        if (!isSkill && !isDungeon) return;

        int level = -1;
        long currentXp = -1L;
        for (Text line : lines) {
            Matcher progress = PROGRESS_LINE.matcher(strip(line.getString()));
            if (progress.find()) {
                try { level = Integer.parseInt(progress.group(1)) - 1; }
                catch (NumberFormatException ignored) { level = -1; }
            }
            String clean = strip(line.getString()).trim();
            if (clean.startsWith("—") || clean.startsWith("-")) clean = clean.substring(1).trim();
            Matcher xp = XP_LINE.matcher(clean);
            if (xp.matches()) {
                try { currentXp = Long.parseLong(xp.group(1).replace(",", "")); }
                catch (NumberFormatException ignored) { currentXp = -1L; }
            }
        }
        if (level < 0 || currentXp < 0) return;

        long[] table;
        int maxLevel;
        String itemName = strip(lines.get(0).getString()).toLowerCase(Locale.ROOT);
        if (isDungeon) {
            table = DUNGEON_XP;
            maxLevel = 50;
        } else if (itemName.contains("social") || itemName.contains("runecrafting")) {
            table = RUNE_SOCIAL_XP;
            maxLevel = 25;
        } else {
            table = STANDARD_XP;
            maxLevel = 60;
        }

        if (!isShiftDown(client)) {
            lines.add(Text.literal(HINT).formatted(Formatting.DARK_GRAY));
            return;
        }

        if (level >= maxLevel) {
            lines.add(Text.literal("Max level reached!").formatted(Formatting.GREEN));
            return;
        }

        long totalAtCurrent = totalXpForLevel(table, level);
        long totalAtMax = totalXpForLevel(table, maxLevel);
        long remaining = Math.max(0L, totalAtMax - (totalAtCurrent + currentXp));
        lines.add(Text.literal("XP to max (Lv" + maxLevel + "): ")
                .formatted(Formatting.AQUA)
                .append(Text.literal(formatXp(remaining)).formatted(Formatting.YELLOW)));
    }

    private static boolean isShiftDown(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static long totalXpForLevel(long[] table, int level) {
        long total = 0L;
        for (int i = 0; i < level && i < table.length; i++) total += table[i];
        return total;
    }

    private static String formatXp(long xp) {
        if (xp >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fB", xp / 1e9);
        if (xp >= 1_000_000L) return String.format(Locale.ROOT, "%.2fM", xp / 1e6);
        if (xp >= 1_000L) return String.format(Locale.ROOT, "%.1fK", xp / 1e3);
        return Long.toString(Math.max(0L, xp));
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("(?i)§.", "");
    }
}