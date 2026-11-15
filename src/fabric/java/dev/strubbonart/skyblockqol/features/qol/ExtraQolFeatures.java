package dev.strubbonart.skyblockqol.features.qol;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** Additional local-only QoL: watched player presence and Hoppity rabbit slot highlights. */
public final class ExtraQolFeatures {
    private static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-extra-qol.properties");
    private static final Set<String> WATCHED = new HashSet<>();
    private static final Set<String> CURRENTLY_PRESENT = new HashSet<>();

    private static boolean initialized;
    private static boolean joinLeaveEnabled = true;
    private static boolean hoppityHighlightEnabled = true;
    private static boolean chatStateRestoreEnabled = false;
    private static String savedChatDraft = "";
    private static boolean restoreChatDraft;
    private static long lastPresenceScan;

    private ExtraQolFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> scanPresence());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("watchplayer")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "player");
                                            WATCHED.add(name.toLowerCase(Locale.ROOT));
                                            save();
                                            context.getSource().sendFeedback(Text.literal("Watching " + name + "."));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "player");
                                            WATCHED.remove(name.toLowerCase(Locale.ROOT));
                                            CURRENTLY_PRESENT.remove(name.toLowerCase(Locale.ROOT));
                                            save();
                                            context.getSource().sendFeedback(Text.literal("Stopped watching " + name + "."));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("clear").executes(context -> {
                            WATCHED.clear();
                            CURRENTLY_PRESENT.clear();
                            save();
                            context.getSource().sendFeedback(Text.literal("Watched player list cleared."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("list").executes(context -> {
                            String names = WATCHED.stream().sorted().collect(Collectors.joining(", "));
                            context.getSource().sendFeedback(Text.literal(
                                    WATCHED.isEmpty() ? "No watched players." : "Watched: " + names));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("on").executes(context -> {
                            setJoinLeaveEnabled(true);
                            context.getSource().sendFeedback(Text.literal("Join/leave notifier enabled."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("off").executes(context -> {
                            setJoinLeaveEnabled(false);
                            context.getSource().sendFeedback(Text.literal("Join/leave notifier disabled."));
                            return 1;
                        }))
                ));
    }

    public static boolean isJoinLeaveEnabled() { return joinLeaveEnabled; }
    public static boolean isHoppityHighlightEnabled() { return hoppityHighlightEnabled; }
    public static int getWatchedCount() { return WATCHED.size(); }
    public static boolean isChatStateRestoreEnabled() { return chatStateRestoreEnabled; }
    public static void setChatStateRestoreEnabled(boolean value) {
        chatStateRestoreEnabled = value;
        if (!value) clearChatDraft();
        save();
    }
    public static String getSavedChatDraft() { return savedChatDraft; }
    public static boolean shouldRestoreChatDraft() { return chatStateRestoreEnabled && restoreChatDraft && !savedChatDraft.isEmpty(); }
    public static void updateChatDraft(String value) {
        if (!chatStateRestoreEnabled) return;
        savedChatDraft = value == null ? "" : value;
        restoreChatDraft = !savedChatDraft.isEmpty();
        save();
    }
    public static void clearChatDraft() {
        savedChatDraft = "";
        restoreChatDraft = false;
        save();
    }

    public static void setJoinLeaveEnabled(boolean value) {
        joinLeaveEnabled = value;
        CURRENTLY_PRESENT.clear();
        save();
    }

    public static void setHoppityHighlightEnabled(boolean value) {
        hoppityHighlightEnabled = value;
        save();
    }

    private static void scanPresence() {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        if (now - lastPresenceScan < 1_000L) return;
        lastPresenceScan = now;

        if (!joinLeaveEnabled || WATCHED.isEmpty() || client.getNetworkHandler() == null
                || client.player == null) {
            CURRENTLY_PRESENT.clear();
            return;
        }

        Set<String> present = client.getNetworkHandler().getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(profile -> profile.getName().toLowerCase(Locale.ROOT))
                .filter(name -> !name.equals(client.player.getGameProfile().getName().toLowerCase(Locale.ROOT)))
                .filter(WATCHED::contains)
                .collect(Collectors.toSet());

        if (CURRENTLY_PRESENT.isEmpty()) {
            CURRENTLY_PRESENT.addAll(present);
            return;
        }

        for (String name : present) {
            if (CURRENTLY_PRESENT.add(name)) {
                client.player.sendMessage(Text.literal("§8[Skyblock QoL] §a" + name + " joined."), false);
            }
        }

        Set<String> left = new HashSet<>(CURRENTLY_PRESENT);
        left.removeAll(present);
        for (String name : left) {
            CURRENTLY_PRESENT.remove(name);
            client.player.sendMessage(Text.literal("§8[Skyblock QoL] §c" + name + " left."), false);
        }
    }

    /** Draws an outline when the hovered item in Hoppity menus is a new rabbit reward. */
    public static void renderHoppitySlot(HandledScreen<?> screen, DrawContext context,
                                         Slot slot, int screenX, int screenY) {
        if (!hoppityHighlightEnabled || slot == null || slot.getStack().isEmpty()) return;
        String title = strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!title.contains("hoppity")) return;

        String name = strip(slot.getStack().getName().getString()).toUpperCase(Locale.ROOT);
        boolean newRabbit = name.contains("NEW RABBIT!");
        if (!newRabbit) return;

        int x = screenX + slot.x;
        int y = screenY + slot.y;
        int color = 0xD000FF00;
        context.fill(x, y, x + 16, y + 1, color);
        context.fill(x, y + 15, x + 16, y + 16, color);
        context.fill(x, y, x + 1, y + 16, color);
        context.fill(x + 15, y, x + 16, y + 16, color);
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static void load() {
        if (!Files.isRegularFile(SETTINGS_FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
            p.load(in);
            joinLeaveEnabled = Boolean.parseBoolean(p.getProperty("joinLeaveEnabled", "true"));
            hoppityHighlightEnabled = Boolean.parseBoolean(p.getProperty("hoppityHighlightEnabled", "true"));
            chatStateRestoreEnabled = Boolean.parseBoolean(p.getProperty("chatStateRestoreEnabled", "false"));
            savedChatDraft = p.getProperty("savedChatDraft", "");
            restoreChatDraft = Boolean.parseBoolean(p.getProperty("restoreChatDraft", "false"));
            String watched = p.getProperty("watchedPlayers", "");
            WATCHED.addAll(Arrays.stream(watched.split(","))
                    .map(String::trim).filter(value -> !value.isEmpty())
                    .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet()));
        } catch (IOException ignored) {
            WATCHED.clear();
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("joinLeaveEnabled", Boolean.toString(joinLeaveEnabled));
        p.setProperty("hoppityHighlightEnabled", Boolean.toString(hoppityHighlightEnabled));
        p.setProperty("chatStateRestoreEnabled", Boolean.toString(chatStateRestoreEnabled));
        p.setProperty("savedChatDraft", savedChatDraft);
        p.setProperty("restoreChatDraft", Boolean.toString(restoreChatDraft));
        p.setProperty("watchedPlayers", WATCHED.stream().sorted().collect(Collectors.joining(",")));
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(SETTINGS_FILE)) {
                p.store(out, "Skyblock QoL extra feature settings");
            }
        } catch (IOException ignored) {
            // Retain live session settings.
        }
    }
}