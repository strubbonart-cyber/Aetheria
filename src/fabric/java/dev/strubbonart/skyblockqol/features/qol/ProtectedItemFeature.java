package dev.strubbonart.skyblockqol.features.qol;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Protects individual SkyBlock item instances from accidental dropping and selling. */
public final class ProtectedItemFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-protected-items.properties");
    private static final Set<String> PROTECTED_UUIDS = new TreeSet<>();

    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean showStar = true;
    private static boolean chatNotifications = true;

    private ProtectedItemFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("asmprotect")
                    .executes(context -> {
                        toggleHeldItem();
                        return 1;
                    })
                    .then(ClientCommandManager.literal("list").executes(context -> {
                        if (PROTECTED_UUIDS.isEmpty()) {
                            context.getSource().sendFeedback(Text.literal("No protected items."));
                        } else {
                            context.getSource().sendFeedback(Text.literal(
                                    "Protected item UUIDs (" + PROTECTED_UUIDS.size() + "):"));
                            for (String uuid : PROTECTED_UUIDS) {
                                context.getSource().sendFeedback(Text.literal(" - " + uuid));
                            }
                        }
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("clear").executes(context -> {
                        int count = PROTECTED_UUIDS.size();
                        PROTECTED_UUIDS.clear();
                        save();
                        context.getSource().sendFeedback(Text.literal("Cleared " + count + " protected item(s)."));
                        return 1;
                    }));
            dispatcher.register(root);
            dispatcher.register(ClientCommandManager.literal("athrprotect")
                    .redirect(dispatcher.getRoot().getChild("asmprotect")));
            dispatcher.register(ClientCommandManager.literal("aetheriaprotect")
                    .redirect(dispatcher.getRoot().getChild("asmprotect")));
            dispatcher.register(ClientCommandManager.literal("jefprotect")
                    .redirect(dispatcher.getRoot().getChild("asmprotect")));
        });
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
    public static boolean isShowStar() { return showStar; }
    public static void setShowStar(boolean value) { showStar = value; save(); }
    public static boolean isChatNotifications() { return chatNotifications; }
    public static void setChatNotifications(boolean value) { chatNotifications = value; save(); }
    public static int getProtectedCount() { return PROTECTED_UUIDS.size(); }

    public static boolean isProtected(ItemStack stack) {
        if (!enabled || stack == null || stack.isEmpty()) return false;
        String uuid = getItemUuid(stack);
        return uuid != null && PROTECTED_UUIDS.contains(uuid);
    }

    public static void toggleHeldItem() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty()) {
            notify("You are not holding an item.");
            return;
        }
        String uuid = getItemUuid(held);
        if (uuid == null) {
            notify("This item has no SkyBlock UUID and cannot be protected.");
            return;
        }
        if (!PROTECTED_UUIDS.add(uuid)) {
            PROTECTED_UUIDS.remove(uuid);
            save();
            notify(strip(held.getName().getString()) + " is no longer protected.");
        } else {
            save();
            notify(strip(held.getName().getString()) + " is now protected.");
        }
    }

    /** Called from HandledScreenMixin before the server receives a dangerous slot click. */
    public static boolean shouldBlockSlotClick(HandledScreen<?> screen, Slot slot,
                                               int slotId, SlotActionType actionType) {
        if (!enabled || screen == null) return false;
        ItemStack cursor = screen.getScreenHandler().getCursorStack();
        ItemStack clicked = slot == null ? ItemStack.EMPTY : slot.getStack();

        if (actionType == SlotActionType.THROW && isProtected(clicked)) {
            notifyBlocked(clicked, "dropped");
            return true;
        }

        if (slotId == -999 && isProtected(cursor)) {
            notifyBlocked(cursor, "thrown away");
            return true;
        }

        if (!isDangerousMenu(screen)) return false;
        if (isProtected(clicked)) {
            notifyBlocked(clicked, "moved in this menu");
            return true;
        }
        if (isProtected(cursor)) {
            notifyBlocked(cursor, "moved in this menu");
            return true;
        }
        return false;
    }

    /** Called by ClientPlayerEntityMixin before an ordinary Q drop from the hotbar. */
    public static boolean shouldBlockSelectedDrop() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null) return false;
        ItemStack held = client.player.getMainHandStack();
        if (!isProtected(held)) return false;
        notifyBlocked(held, "dropped");
        return true;
    }

    public static void renderStar(HandledScreen<?> screen, DrawContext context, Slot slot,
                                  int guiX, int guiY) {
        if (!enabled || !showStar || !isProtected(slot == null ? ItemStack.EMPTY : slot.getStack())) return;
        int color = 0xFF000000 | 0x00FFD700;
        int x = guiX + slot.x + 1;
        int y = guiY + slot.y;
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("★"), x, y, color);
    }

    private static boolean isDangerousMenu(HandledScreen<?> screen) {
        String title = strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (title.contains("auction")) return true;
        if (title.startsWith("you ") || title.contains("trading")
                || title.contains("trades") || title.contains("exchange")) return true;
        if (title.contains("salvage")) return true;
        if (title.contains("shop") && !title.contains("workshop")) return true;
        if (title.contains("sell offer") || title.contains("create sell offer")
                || title.contains("bazaar")) return true;

        for (Slot candidate : screen.getScreenHandler().slots) {
            ItemStack stack = candidate.getStack();
            if (stack.isEmpty()) continue;
            if (strip(stack.getName().getString()).equalsIgnoreCase("Sell Item")) return true;
            var lore = stack.get(DataComponentTypes.LORE);
            if (lore != null && lore.lines().stream().anyMatch(line ->
                    strip(line.getString()).toLowerCase(Locale.ROOT).contains("click to buyback"))) {
                return true;
            }
        }
        return false;
    }

    private static String getItemUuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return null;
        NbtCompound root = custom.copyNbt();
        NbtCompound extra = root.contains("ExtraAttributes") ? root.getCompound("ExtraAttributes") : root;
        String uuid = extra.getString("uuid").trim();
        return uuid.isEmpty() ? null : uuid;
    }

    private static void notifyBlocked(ItemStack stack, String action) {
        notify(strip(stack.getName().getString()) + " is protected and cannot be " + action + ".");
    }

    private static void notify(String message) {
        if (!chatNotifications) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§b[ItemProtect] §r" + message), false);
        }
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            showStar = Boolean.parseBoolean(p.getProperty("showStar", "true"));
            chatNotifications = Boolean.parseBoolean(p.getProperty("chatNotifications", "true"));
            int count = Math.max(0, Integer.parseInt(p.getProperty("count", "0")));
            for (int i = 0; i < count; i++) {
                String uuid = p.getProperty("uuid." + i, "");
                if (!uuid.isBlank()) PROTECTED_UUIDS.add(uuid);
            }
        } catch (IOException | NumberFormatException ignored) {
            PROTECTED_UUIDS.clear();
            enabled = true;
            showStar = true;
            chatNotifications = true;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("showStar", Boolean.toString(showStar));
        p.setProperty("chatNotifications", Boolean.toString(chatNotifications));
        p.setProperty("count", Integer.toString(PROTECTED_UUIDS.size()));
        int index = 0;
        for (String uuid : PROTECTED_UUIDS) p.setProperty("uuid." + index++, uuid);
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Protected Items");
            }
        } catch (IOException ignored) {
            // Keep the session protection set active if storage is unavailable.
        }
    }
}
