package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Inventory/chest search box with live item dimming and match highlighting. */
public final class InventorySearchFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-inventory-search.properties");
    private static final int BASE_WIDTH = 164;
    private static final int BASE_HEIGHT = 20;

    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean persistSearchText = true;
    private static String query = "";
    private static int x = -1;
    private static int y = -1;
    private static float scale = 1.0F;

    private InventorySearchFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!enabled || !(screen instanceof HandledScreen<?>)) return;

            if (!persistSearchText) query = "";
            TextFieldWidget field = new TextFieldWidget(
                    client.textRenderer,
                    getResolvedX(scaledWidth),
                    getResolvedY(scaledHeight),
                    Math.max(90, Math.round(BASE_WIDTH * scale)),
                    Math.max(16, Math.round(BASE_HEIGHT * scale)),
                    Text.literal("Search items")
            );
            field.setMaxLength(64);
            field.setPlaceholder(Text.literal("Search items..."));
            field.setDrawsBackground(true);
            field.setText(query);
            field.setChangedListener(value -> {
                query = value;
                if (persistSearchText) save();
            });
            screen.addDrawableChild(field);
        });
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static boolean isPersistSearchText() { return persistSearchText; }
    public static void setPersistSearchText(boolean value) {
        persistSearchText = value;
        if (!value) query = "";
        save();
    }

    public static String getQuery() { return query; }
    public static int getX(int screenWidth) { return getResolvedX(screenWidth); }
    public static int getY(int screenHeight) { return getResolvedY(screenHeight); }
    public static float getScale() { return scale; }

    public static void updateLayout(int nextX, int nextY, float nextScale) {
        x = Math.max(0, Math.min(5000, nextX));
        y = Math.max(0, Math.min(5000, nextY));
        scale = Math.max(0.5F, Math.min(3.0F, nextScale));
    }

    public static void saveLayout() { save(); }

    public static void resetLayout() {
        x = -1;
        y = -1;
        scale = 1.0F;
        save();
    }

    public static void renderSlot(HandledScreen<?> screen, DrawContext context, Slot slot,
                                  int guiX, int guiY) {
        if (!enabled || slot == null || query.isBlank()) return;
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;

        boolean matches = matches(stack, query);
        int sx = guiX + slot.x;
        int sy = guiY + slot.y;
        if (!matches) {
            context.fill(sx, sy, sx + 16, sy + 16, 0x99000000);
        } else {
            context.fill(sx, sy, sx + 16, sy + 1, 0xFF55DD88);
            context.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF55DD88);
            context.fill(sx, sy, sx + 1, sy + 16, 0xFF55DD88);
            context.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF55DD88);
        }
    }

    private static boolean matches(ItemStack stack, String rawQuery) {
        String needle = strip(rawQuery).toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) return true;

        String name = strip(stack.getName().getString()).toLowerCase(Locale.ROOT);
        if (name.contains(needle)) return true;

        String id = skyblockId(stack);
        if (id.contains(needle)) return true;

        var lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                if (strip(line.getString()).toLowerCase(Locale.ROOT).contains(needle)) return true;
            }
        }
        return false;
    }

    private static String skyblockId(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return "";
        NbtCompound root = custom.copyNbt();
        NbtCompound extra = root.contains("ExtraAttributes") ? root.getCompound("ExtraAttributes") : root;
        return extra.getString("id").toLowerCase(Locale.ROOT);
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static int getResolvedX(int screenWidth) {
        return x < 0 ? screenWidth / 2 - Math.round(BASE_WIDTH * scale) / 2 : x;
    }

    private static int getResolvedY(int screenHeight) {
        return y < 0 ? screenHeight / 2 - 108 : y;
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            persistSearchText = Boolean.parseBoolean(p.getProperty("persistSearchText", "true"));
            query = p.getProperty("query", "");
            x = Integer.parseInt(p.getProperty("x", "-1"));
            y = Integer.parseInt(p.getProperty("y", "-1"));
            scale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(p.getProperty("scale", "1.0"))));
        } catch (IOException | NumberFormatException ignored) {
            enabled = true;
            persistSearchText = true;
            query = "";
            x = -1;
            y = -1;
            scale = 1.0F;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("persistSearchText", Boolean.toString(persistSearchText));
        p.setProperty("query", persistSearchText ? query : "");
        p.setProperty("x", Integer.toString(x));
        p.setProperty("y", Integer.toString(y));
        p.setProperty("scale", Float.toString(scale));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Inventory Search");
            }
        } catch (IOException ignored) {
            // Keep in-memory search available in this session.
        }
    }
}
