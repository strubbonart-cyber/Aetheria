package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Inventory slot-to-hotbar bindings. The bind key selects two slots in the
 * player inventory; Shift+left-clicking either bound slot swaps it with its
 * partner's hotbar position. This operates only in the player's inventory UI.
 */
public final class SlotBindsFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-slot-binds.properties");
    private static final Map<Integer, Integer> BINDS = new LinkedHashMap<>();

    private static boolean initialized;
    private static boolean enabled;
    private static boolean alwaysShowLines;
    private static Integer pendingSlot;
    private static KeyBinding bindKey;

    private SlotBindsFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        bindKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skyblockqol.slot_bind",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.skyblockqol"
        ));
    }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) {
        enabled = value;
        pendingSlot = null;
        save();
    }

    public static boolean isAlwaysShowLinesEnabled() { return alwaysShowLines; }

    public static void setAlwaysShowLinesEnabled(boolean value) {
        alwaysShowLines = value;
        save();
    }

    public static boolean onKeyPressed(HandledScreen<?> screen, int keyCode,
                                       int scanCode, Slot focusedSlot) {
        if (!enabled || bindKey == null || !bindKey.matchesKey(keyCode, scanCode)) return false;
        if (!(screen instanceof InventoryScreen) || focusedSlot == null) {
            pendingSlot = null;
            return true;
        }

        int clicked = focusedSlot.id;
        if (!isValidSlot(clicked)) {
            pendingSlot = null;
            return true;
        }

        if (pendingSlot == null) {
            Integer existing = BINDS.get(clicked);
            if (existing != null) {
                removeBind(clicked);
                save();
                message("Removed slot bind.");
            } else {
                pendingSlot = clicked;
                message("Select the second slot with your Slot Binds key.");
            }
            return true;
        }

        int first = pendingSlot;
        pendingSlot = null;
        if (first == clicked) {
            message("A slot cannot be bound to itself.");
        } else if (!canBind(first, clicked)) {
            message("A bind must connect an inventory slot to a hotbar slot.");
        } else {
            addBind(first, clicked);
            save();
            message("Slot bind saved.");
        }
        return true;
    }

    public static boolean onShiftClick(HandledScreen<?> screen, Slot slot, int slotId,
                                       int button, SlotActionType actionType) {
        if (!enabled || !(screen instanceof InventoryScreen) || slot == null
                || button != 0 || actionType != SlotActionType.QUICK_MOVE) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return false;
        if (!isShiftDown(client)) return false;

        Integer partner = BINDS.get(slotId);
        if (partner == null || !isValidSlot(partner) || partner == slotId) return false;

        int hotbarSlot = isHotbar(slotId) ? slotId : partner;
        int sourceSlot = isHotbar(slotId) ? partner : slotId;
        if (!isHotbar(hotbarSlot)) return false;

        client.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                sourceSlot,
                hotbarSlot - 36,
                SlotActionType.SWAP,
                client.player
        );
        return true;
    }

    public static void onScreenClosed(HandledScreen<?> screen) {
        if (screen instanceof InventoryScreen) pendingSlot = null;
    }

    public static void render(HandledScreen<?> screen, DrawContext context,
                              int screenX, int screenY, Slot hovered, int mouseX, int mouseY) {
        if (!enabled || !(screen instanceof InventoryScreen)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        if (pendingSlot != null) {
            Slot start = getSlot(screen, pendingSlot);
            if (start != null) {
                drawLine(context, screenX + start.x + 8, screenY + start.y + 8,
                        mouseX, mouseY, 0xFFFFB84D);
            }
            return;
        }

        if (alwaysShowLines) {
            for (Map.Entry<Integer, Integer> entry : BINDS.entrySet()) {
                if (entry.getKey() >= entry.getValue()) continue;
                Slot first = getSlot(screen, entry.getKey());
                Slot second = getSlot(screen, entry.getValue());
                if (first == null || second == null) continue;
                drawLine(context, screenX + first.x + 8, screenY + first.y + 8,
                        screenX + second.x + 8, screenY + second.y + 8, 0xC8FFAA00);
            }
        } else if (isShiftDown(client) && hovered != null) {
            Integer partner = BINDS.get(hovered.id);
            if (partner == null) return;
            Slot second = getSlot(screen, partner);
            if (second == null) return;
            drawLine(context, screenX + hovered.x + 8, screenY + hovered.y + 8,
                    screenX + second.x + 8, screenY + second.y + 8, 0xFFFFAA00);
        }
    }

    private static Slot getSlot(HandledScreen<?> screen, int slotId) {
        if (slotId < 0 || slotId >= screen.getScreenHandler().slots.size()) return null;
        return screen.getScreenHandler().slots.get(slotId);
    }

    private static boolean isShiftDown(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isValidSlot(int slot) { return slot >= 5 && slot <= 44; }
    private static boolean isHotbar(int slot) { return slot >= 36 && slot <= 44; }
    private static boolean isArmor(int slot) { return slot >= 5 && slot <= 8; }

    private static boolean canBind(int first, int second) {
        return !(isArmor(first) && !isHotbar(second))
                && !(isArmor(second) && !isHotbar(first))
                && (isHotbar(first) || isHotbar(second));
    }

    private static void addBind(int first, int second) {
        removeBind(first);
        removeBind(second);
        BINDS.put(first, second);
        BINDS.put(second, first);
    }

    private static void removeBind(int slot) {
        Integer other = BINDS.remove(slot);
        if (other != null) BINDS.remove(other);
    }

    private static void drawLine(DrawContext context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;

        while (true) {
            context.fill(x0 - 1, y0 - 1, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) {
                error += dy;
                x0 += sx;
            }
            if (twice <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private static void message(String value) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§8[Skyblock QoL] §e" + value), false);
        }
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
            alwaysShowLines = Boolean.parseBoolean(properties.getProperty("alwaysShowLines", "false"));
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("bind.")) continue;
                try {
                    int slot = Integer.parseInt(key.substring(5));
                    int partner = Integer.parseInt(properties.getProperty(key, "-1"));
                    if (isValidSlot(slot) && isValidSlot(partner) && slot != partner) {
                        BINDS.put(slot, partner);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore one malformed bind entry without discarding other bindings.
                }
            }
            BINDS.entrySet().removeIf(entry ->
                    !Integer.valueOf(entry.getKey()).equals(BINDS.get(entry.getValue())));
        } catch (IOException ignored) {
            BINDS.clear();
            enabled = false;
            alwaysShowLines = false;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("alwaysShowLines", Boolean.toString(alwaysShowLines));
        for (Map.Entry<Integer, Integer> entry : BINDS.entrySet()) {
            properties.setProperty("bind." + entry.getKey(), Integer.toString(entry.getValue()));
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL slot binds");
            }
        } catch (IOException ignored) {
            // Keep live bindings for this session.
        }
    }
}
