package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Adds F1-F7, M1-M7, or ENT badges to Party Finder player heads. */
public final class PartyFinderFloorTip {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-party-finder.properties");
    private static boolean enabled = true;
    private static boolean initialized;

    private PartyFinderFloorTip() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
    }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static void renderSlot(HandledScreen<?> screen, DrawContext context, Slot slot,
                                  int screenX, int screenY) {
        if (!enabled || slot == null || slot.getStack().isEmpty()) return;
        String title = strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!title.contains("party finder")) return;
        ItemStack stack = slot.getStack();
        if (!stack.isOf(net.minecraft.item.Items.PLAYER_HEAD)) return;

        String floor = findFloor(stack);
        if (floor == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(floor);
        int x = screenX + slot.x + 16 - textWidth - 2;
        int y = screenY + slot.y + 1;
        context.fill(x - 2, y - 1, x + textWidth + 2, y + 9, 0xCC101722);
        context.drawTextWithShadow(client.textRenderer, Text.literal(floor), x, y,
                floor.startsWith("M") ? 0xFFFFAA55 : floor.equals("ENT") ? 0xFF55DDFF : 0xFF77DD88);
    }

    private static String findFloor(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;
        boolean master = false;
        String floor = null;
        for (Text line : lore.lines()) {
            String clean = strip(line.getString()).trim();
            if (clean.startsWith("Dungeon: ")) {
                master = clean.substring("Dungeon: ".length()).trim()
                        .equalsIgnoreCase("Master Catacombs");
            } else if (clean.startsWith("Floor: ")) {
                String value = clean.substring("Floor: ".length()).trim();
                if (value.equalsIgnoreCase("Entrance")) {
                    floor = "ENT";
                } else if (value.regionMatches(true, 0, "Floor ", 0, 6)) {
                    int number = romanToInt(value.substring(6).trim());
                    if (number >= 1 && number <= 7) floor = Integer.toString(number);
                }
            }
        }
        if (floor == null || floor.equals("ENT")) return floor;
        return (master ? "M" : "F") + floor;
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

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        } catch (IOException ignored) {
            enabled = true;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL Party Finder Settings");
            }
        } catch (IOException ignored) {
            // Retain the current setting in memory.
        }
    }
}
