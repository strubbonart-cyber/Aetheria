package dev.strubbonart.skyblockqol.features.qol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.Locale;

/** Renders enchant-book levels and Catacombs pass floors over inventory slots. */
public final class ItemStackTipsFeature {
    private ItemStackTipsFeature() {}

    public static void renderSlot(HandledScreen<?> screen, DrawContext context, Slot slot,
                                  int screenX, int screenY) {
        if (!MiscQolFeatures.isItemStackTipsEnabled() || slot == null) return;
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;

        String id = skyblockId(stack);
        String tip = null;
        if ("ENCHANTED_BOOK".equals(id)) {
            tip = bookLevel(stack.getName().getString());
        } else if (strip(screen.getTitle().getString()).equalsIgnoreCase("Catacombs Gate")) {
            tip = dungeonFloor(id);
        }
        if (tip == null || tip.isBlank()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(tip);
        int x = screenX + slot.x + 16 - textWidth - 1;
        int y = screenY + slot.y + 8;
        context.fill(x - 2, y - 1, x + textWidth + 1, y + 8, 0xC9000000);
        context.drawTextWithShadow(client.textRenderer, Text.literal(tip), x, y, 0xFFFFFFFF);
    }

    private static String bookLevel(String rawName) {
        String clean = strip(rawName).trim();
        if (clean.isEmpty()) return null;
        String[] parts = clean.split("\\s+");
        String numeral = parts[parts.length - 1].replaceAll("[^IVXLCDM]", "");
        if (numeral.isEmpty() || !numeral.equals(parts[parts.length - 1].replaceAll("[^IVXLCDM]", ""))) {
            return null;
        }
        int level = romanToInt(numeral);
        return level > 0 ? Integer.toString(level) : null;
    }

    private static String dungeonFloor(String id) {
        String suffix;
        if (id.startsWith("MASTER_CATACOMBS_PASS_")) {
            suffix = id.substring("MASTER_CATACOMBS_PASS_".length());
        } else if (id.startsWith("CATACOMBS_PASS_")) {
            suffix = id.substring("CATACOMBS_PASS_".length());
        } else {
            return null;
        }
        try {
            int floor = Integer.parseInt(suffix) - 3;
            return floor > 0 ? Integer.toString(floor) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String skyblockId(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null || component.isEmpty()) return "";
        NbtCompound root = component.copyNbt();
        NbtCompound extra = root.contains("ExtraAttributes") ? root.getCompound("ExtraAttributes") : root;
        return extra.getString("id").trim().toUpperCase(Locale.ROOT);
    }

    private static int romanToInt(String roman) {
        String value = roman.toUpperCase(Locale.ROOT);
        int total = 0;
        int previous = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            int current = switch (value.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            if (current == 0) return 0;
            total += current < previous ? -current : current;
            previous = current;
        }
        return total;
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}
