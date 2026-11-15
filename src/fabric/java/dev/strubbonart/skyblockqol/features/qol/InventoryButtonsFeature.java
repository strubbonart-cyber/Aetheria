package dev.strubbonart.skyblockqol.features.qol;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.strubbonart.skyblockqol.mixin.ScreenAccessorMixin;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Configurable clickable command shortcuts added to inventory and container screens. */
public final class InventoryButtonsFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-inventory-buttons.properties");
    private static final List<Shortcut> BUTTONS = new ArrayList<>();
    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean disableInTerminals = true;

    private static final class Shortcut {
        int x;
        int y;
        String label;
        String command;
        boolean playerInventoryOnly;

        Shortcut(int x, int y, String label, String command, boolean playerInventoryOnly) {
            this.x = x;
            this.y = y;
            this.label = label;
            this.command = command;
            this.playerInventoryOnly = playerInventoryOnly;
        }
    }

    private InventoryButtonsFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!enabled || !(screen instanceof HandledScreen<?> handled)) return;
            if (disableInTerminals && isTerminal(handled)) return;

            for (Shortcut shortcut : BUTTONS) {
                if (shortcut.playerInventoryOnly && !(handled instanceof InventoryScreen)) continue;
                int bx = Math.max(0, Math.min(scaledWidth - 18, scaledWidth / 2 + shortcut.x));
                int by = Math.max(0, Math.min(scaledHeight - 18, scaledHeight / 2 + shortcut.y));
                ButtonWidget button = ButtonWidget.builder(Text.literal(shortcut.label), widget ->
                        execute(shortcut.command))
                        .dimensions(bx, by, 18, 18)
                        .build();
                ((ScreenAccessorMixin) (Object) screen).skyblockqol$addDrawableChild(button);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("asmbuttons")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal(
                                "Use /asmbuttons add <x> <y> <label> <command>, list, move, remove, clear, on or off."));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(-400, 400))
                                    .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(-300, 300))
                                            .then(ClientCommandManager.argument("label", StringArgumentType.word())
                                                    .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                                            .executes(context -> {
                                                                addButton(
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        StringArgumentType.getString(context, "label"),
                                                                        StringArgumentType.getString(context, "command"),
                                                                        false
                                                                );
                                                                context.getSource().sendFeedback(Text.literal("Inventory shortcut added."));
                                                                return 1;
                                                            }))))))
                    .then(ClientCommandManager.literal("addinv")
                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(-400, 400))
                                    .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(-300, 300))
                                            .then(ClientCommandManager.argument("label", StringArgumentType.word())
                                                    .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                                            .executes(context -> {
                                                                addButton(
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        StringArgumentType.getString(context, "label"),
                                                                        StringArgumentType.getString(context, "command"),
                                                                        true
                                                                );
                                                                context.getSource().sendFeedback(Text.literal("Player-inventory shortcut added."));
                                                                return 1;
                                                            }))))))
                    .then(ClientCommandManager.literal("list").executes(context -> {
                        if (BUTTONS.isEmpty()) {
                            context.getSource().sendFeedback(Text.literal("No inventory shortcuts configured."));
                        } else {
                            for (int i = 0; i < BUTTONS.size(); i++) {
                                Shortcut b = BUTTONS.get(i);
                                context.getSource().sendFeedback(Text.literal((i + 1) + ". "
                                        + b.label + " @ " + b.x + "," + b.y + " -> /" + b.command
                                        + (b.playerInventoryOnly ? " [inventory only]" : "")));
                            }
                        }
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("move")
                            .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(-400, 400))
                                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(-300, 300))
                                                    .executes(context -> {
                                                        int index = IntegerArgumentType.getInteger(context, "index") - 1;
                                                        if (index < 0 || index >= BUTTONS.size()) {
                                                            context.getSource().sendFeedback(Text.literal("Shortcut index not found."));
                                                            return 0;
                                                        }
                                                        Shortcut b = BUTTONS.get(index);
                                                        b.x = IntegerArgumentType.getInteger(context, "x");
                                                        b.y = IntegerArgumentType.getInteger(context, "y");
                                                        save();
                                                        context.getSource().sendFeedback(Text.literal("Shortcut position updated. Reopen the inventory to refresh."));
                                                        return 1;
                                                    })))))
                    .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> {
                                        int index = IntegerArgumentType.getInteger(context, "index") - 1;
                                        if (index < 0 || index >= BUTTONS.size()) {
                                            context.getSource().sendFeedback(Text.literal("Shortcut index not found."));
                                            return 0;
                                        }
                                        BUTTONS.remove(index);
                                        save();
                                        context.getSource().sendFeedback(Text.literal("Shortcut removed."));
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("clear").executes(context -> {
                        int count = BUTTONS.size();
                        BUTTONS.clear();
                        save();
                        context.getSource().sendFeedback(Text.literal("Removed " + count + " inventory shortcuts."));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("on").executes(context -> {
                        setEnabled(true);
                        context.getSource().sendFeedback(Text.literal("Inventory buttons enabled."));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("off").executes(context -> {
                        setEnabled(false);
                        context.getSource().sendFeedback(Text.literal("Inventory buttons disabled."));
                        return 1;
                    }));
            dispatcher.register(root);
        });
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
    public static boolean isDisableInTerminals() { return disableInTerminals; }
    public static void setDisableInTerminals(boolean value) { disableInTerminals = value; save(); }
    public static int getButtonCount() { return BUTTONS.size(); }

    private static void addButton(int x, int y, String label, String command, boolean inventoryOnly) {
        String cleanCommand = command == null ? "" : command.trim();
        if (cleanCommand.startsWith("/")) cleanCommand = cleanCommand.substring(1);
        if (cleanCommand.isBlank()) return;
        String cleanLabel = label == null ? "?" : label.trim();
        if (cleanLabel.isBlank()) cleanLabel = "?";
        if (cleanLabel.length() > 2) cleanLabel = cleanLabel.substring(0, 2);
        BUTTONS.add(new Shortcut(x, y, cleanLabel, cleanCommand, inventoryOnly));
        save();
    }

    private static void execute(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;
        String clean = command.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (!clean.isBlank()) client.getNetworkHandler().sendChatCommand(clean);
    }

    private static boolean isTerminal(HandledScreen<?> screen) {
        String title = strip(screen.getTitle().getString()).toLowerCase();
        return title.equals("click in order!")
                || title.startsWith("select all the")
                || title.startsWith("what starts with")
                || title.contains("complete the maze")
                || title.contains("navigate the maze");
    }

    private static String strip(String text) {
        return text == null ? "" : text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            disableInTerminals = Boolean.parseBoolean(p.getProperty("disableInTerminals", "true"));
            int count = Math.max(0, Integer.parseInt(p.getProperty("count", "0")));
            for (int i = 0; i < count; i++) {
                String prefix = "button." + i + ".";
                String command = p.getProperty(prefix + "command", "");
                if (command.isBlank()) continue;
                BUTTONS.add(new Shortcut(
                        Integer.parseInt(p.getProperty(prefix + "x", "0")),
                        Integer.parseInt(p.getProperty(prefix + "y", "0")),
                        p.getProperty(prefix + "label", "?"),
                        command,
                        Boolean.parseBoolean(p.getProperty(prefix + "playerInventoryOnly", "false"))
                ));
            }
        } catch (IOException | NumberFormatException ignored) {
            BUTTONS.clear();
            enabled = true;
            disableInTerminals = true;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("disableInTerminals", Boolean.toString(disableInTerminals));
        p.setProperty("count", Integer.toString(BUTTONS.size()));
        for (int i = 0; i < BUTTONS.size(); i++) {
            Shortcut b = BUTTONS.get(i);
            String prefix = "button." + i + ".";
            p.setProperty(prefix + "x", Integer.toString(b.x));
            p.setProperty(prefix + "y", Integer.toString(b.y));
            p.setProperty(prefix + "label", b.label);
            p.setProperty(prefix + "command", b.command);
            p.setProperty(prefix + "playerInventoryOnly", Boolean.toString(b.playerInventoryOnly));
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Inventory Buttons");
            }
        } catch (IOException ignored) {
            // Keep the current session shortcuts in memory.
        }
    }
}
