package dev.strubbonart.skyblockqol.features.mining;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fabric port of Aetheria's HOTM perk powder-spent and next-ten-level tooltip.
 * It is active only while the Heart of the Mountain menu is open.
 */
public final class HotmPowderTooltip {
    private static final String HOTM_TITLE = "Heart of the Mountain";
    private static final Pattern LEVEL_PATTERN = Pattern.compile("Level\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static boolean enabled = true;
    private static final Text SHIFT_HINT = Text.literal("[SHIFT: powder for next 10 levels]")
            .formatted(Formatting.DARK_GRAY);

    private HotmPowderTooltip() {}

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) { enabled = value; }

    public static void initialize() {
        ItemTooltipCallback.EVENT.register(HotmPowderTooltip::appendTooltip);
    }

    private static void appendTooltip(ItemStack stack, Item.TooltipContext tooltipContext,
                                      TooltipType tooltipType, List<Text> lines) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null
                || !HOTM_TITLE.equals(client.currentScreen.getTitle().getString())) return;
        if (stack.isEmpty()) return;

        HotmPerkData perk = HotmPerkData.findByGuiName(stack.getName().getString().trim());
        if (perk == null) return;

        int rawLevel = parseLevel(lines);
        if (rawLevel < 0) return;
        rawLevel = Math.min(rawLevel, perk.maxLevel);

        int insertAt = Math.min(2, lines.size());
        lines.add(insertAt, spentLine(perk, rawLevel));

        if (rawLevel >= perk.maxLevel) return;
        boolean shiftDown = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (!shiftDown) {
            lines.add(Math.min(insertAt + 1, lines.size()), SHIFT_HINT);
            return;
        }

        int targetLevel = Math.min(rawLevel + 10, perk.maxLevel);
        int levels = targetLevel - rawLevel;
        long nextCost = perk.calculateTotalCost(targetLevel) - perk.calculateTotalCost(rawLevel);
        String levelWord = levels == 1 ? "level" : "levels";
        lines.add(Math.min(insertAt + 1, lines.size()), Text.literal(
                perk.powderType.displayName + " for " + levels + " " + levelWord + ": "
                        + format(nextCost)).formatted(Formatting.GRAY));
    }

    private static Text spentLine(HotmPerkData perk, int level) {
        long spent = perk.calculateTotalCost(level);
        long max = perk.totalCostMaxLevel;
        double percent = max > 0 ? spent * 100.0 / max : 100.0;

        String result = perk.powderType.displayName + " spent: ";
        if (level >= perk.maxLevel) {
            result += format(max) + " (Max level)";
        } else {
            // Original Aetheria default format: current investment / maximum investment.
            result += format(spent) + " / " + format(max);
        }
        return Text.literal(result).formatted(Formatting.GRAY);
    }

    private static int parseLevel(List<Text> lines) {
        for (Text line : lines) {
            Matcher matcher = LEVEL_PATTERN.matcher(line.getString());
            if (!matcher.find()) continue;
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String format(long number) {
        return String.format(Locale.US, "%,d", number);
    }
}
