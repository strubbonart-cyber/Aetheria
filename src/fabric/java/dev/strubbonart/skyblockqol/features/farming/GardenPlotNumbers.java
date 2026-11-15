package dev.strubbonart.skyblockqol.features.farming;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Garden Configure Plots item overlay and slot highlights. */
public final class GardenPlotNumbers {
    private static final Pattern PLOT_NAME = Pattern.compile("Plot\\s*-\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static boolean enabled = true;
    private static boolean highlightSlots = true;

    private GardenPlotNumbers() {}

    public static boolean isEnabled() { return enabled; }
    public static boolean isHighlightSlotsEnabled() { return highlightSlots; }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static void setHighlightSlotsEnabled(boolean value) {
        highlightSlots = value;
        save();
    }

    public static void renderSlot(HandledScreen<?> screen, DrawContext context, Slot slot,
                                  int screenX, int screenY) {
        if (!enabled || slot == null || !GardenFeatures.isInGarden()) return;
        String title = stripFormatting(screen.getTitle().getString());
        if (!title.toLowerCase(java.util.Locale.ROOT).contains("configure plots")) return;

        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;

        Matcher matcher = PLOT_NAME.matcher(stripFormatting(stack.getName().getString()));
        if (!matcher.find()) return;

        int plotNumber;
        try {
            plotNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return;
        }

        int state = classify(stack);
        int color = switch (state) {
            case 0 -> 0xFF55DD77; // unlocked
            case 1 -> 0xFFFFD34E; // buyable
            case 2 -> 0xFFFF8844; // missing materials
            case 3 -> 0xFF777777; // locked
            default -> 0xFFFFFFFF;
        };

        int x = screenX + slot.x;
        int y = screenY + slot.y;

        if (highlightSlots && (state == 0 || state == 1 || state == 2)) {
            int highlight = state == 0 ? 0xA055DD77 : state == 1 ? 0xA0FFD34E : 0xA0FF8844;
            context.fill(x, y, x + 16, y + 1, highlight);
            context.fill(x, y + 15, x + 16, y + 16, highlight);
            context.fill(x, y, x + 1, y + 16, highlight);
            context.fill(x + 15, y, x + 16, y + 16, highlight);
        }

        String label = Integer.toString(plotNumber);
        int textX = x + 15 - MinecraftClient.getInstance().textRenderer.getWidth(label);
        context.fill(textX - 1, y, x + 16, y + 8, 0xAA000000);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label,
                textX, y + 1, color);
    }

    private static int classify(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
        boolean unlocked = false;
        boolean buyable = false;
        boolean missing = false;
        boolean locked = false;

        for (Text line : tooltip) {
            String clean = stripFormatting(line.getString());
            if (clean.contains("Left-click to modify!")
                    || clean.contains("Right-click to teleport to this plot!")) unlocked = true;
            if (clean.contains("Click to purchase")) buyable = true;
            if (clean.contains("You need more")) missing = true;
            if (clean.contains("Garden Level") || clean.contains("adjacent plot")) locked = true;
        }

        if (unlocked) return 0;
        if (buyable) return 1;
        if (missing) return 2;
        if (locked) return 3;
        return -1;
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void save() {
        GardenFeatures.setPlotNumbersEnabled(enabled);
        GardenFeatures.setPlotHighlightsEnabled(highlightSlots);
    }

    public static void loadSettings(boolean enabledValue, boolean highlightValue) {
        enabled = enabledValue;
        highlightSlots = highlightValue;
    }
}