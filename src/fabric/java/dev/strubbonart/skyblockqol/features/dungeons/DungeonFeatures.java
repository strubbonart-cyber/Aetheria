package dev.strubbonart.skyblockqol.features.dungeons;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Initial Fabric migration of Aetheria's Hide Blessing Messages option.
 * It only filters the exact dungeon blessing broadcast, never ordinary chat.
 */
public final class DungeonFeatures {
    private static final Pattern CHARGES = Pattern.compile("Charges:\\s*(\\d+)/(\\d+)");
    private static final Pattern BLESSING = Pattern.compile(
            "DUNGEON BUFF! .*?found a Blessing of \\w+.*!",
            Pattern.CASE_INSENSITIVE
    );
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-dungeon.properties");
    private static boolean hideBlessingMessages;
    private static boolean breakerOverlayEnabled;
    private static boolean bossHighlightsEnabled = true;
    private static boolean bloodHighlightsEnabled = true;

    private DungeonFeatures() {}

    public static boolean areBossHighlightsEnabled() {
        return bossHighlightsEnabled;
    }

    public static boolean areBloodHighlightsEnabled() {
        return bloodHighlightsEnabled;
    }

    public static void initialize() {
        load();
        HudRenderCallback.EVENT.register(DungeonFeatures::renderBreakerOverlay);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!hideBlessingMessages) return true;
            return !BLESSING.matcher(message.getString()).find();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("dungeon")
                        .then(ClientCommandManager.literal("blessing").executes(context -> {
                            hideBlessingMessages = !hideBlessingMessages;
                            save();
                            context.getSource().sendFeedback(Text.literal(
                                    "Hide dungeon blessing messages "
                                            + (hideBlessingMessages ? "enabled" : "disabled")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("breaker").executes(context -> {
                            breakerOverlayEnabled = !breakerOverlayEnabled;
                            save();
                            context.getSource().sendFeedback(Text.literal(
                                    "Dungeon Breaker overlay "
                                            + (breakerOverlayEnabled ? "enabled" : "disabled")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("bosses").executes(context -> {
                            bossHighlightsEnabled = !bossHighlightsEnabled;
                            save();
                            context.getSource().sendFeedback(Text.literal(
                                    "Dungeon boss highlights " + (bossHighlightsEnabled ? "enabled" : "disabled")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("blood").executes(context -> {
                            bloodHighlightsEnabled = !bloodHighlightsEnabled;
                            save();
                            context.getSource().sendFeedback(Text.literal(
                                    "Dungeon Blood Room highlights " + (bloodHighlightsEnabled ? "enabled" : "disabled")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("timer").executes(context -> {
                            boolean enabled = DungeonRunOverlay.toggle();
                            context.getSource().sendFeedback(Text.literal("Dungeon timer HUD " + (enabled ? "enabled" : "disabled")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(
                                    "Hide dungeon blessing messages: "
                                            + (hideBlessingMessages ? "ON" : "OFF")
                                            + " | Dungeon Breaker overlay: "
                                            + (breakerOverlayEnabled ? "ON" : "OFF")
                                            + " | Dungeon timer HUD: "
                                            + (DungeonRunOverlay.isEnabled() ? "ON" : "OFF")
                                            + " | Boss highlights: " + (bossHighlightsEnabled ? "ON" : "OFF")
                                            + " | Blood highlights: " + (bloodHighlightsEnabled ? "ON" : "OFF")));
                            return 1;
                        }))));
    }

    private static void renderBreakerOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (!breakerOverlayEnabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        ItemStack breaker = null;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = client.player.getInventory().getStack(slot);
            if (candidate.isEmpty()) continue;
            String name = candidate.getName().getString().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("dungeon breaker") || name.contains("dungeonbreaker")) {
                breaker = candidate;
                break;
            }
        }
        if (breaker == null) return;

        LoreComponent lore = breaker.get(DataComponentTypes.LORE);
        if (lore == null) return;
        for (Text line : lore.lines()) {
            Matcher matcher = CHARGES.matcher(line.getString().replaceAll("§[0-9A-FK-ORa-fk-or]", ""));
            if (!matcher.find()) continue;
            int current;
            int maximum;
            try {
                current = Integer.parseInt(matcher.group(1));
                maximum = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
                return;
            }
            int color = current == maximum ? 0x55FF55 : current == 0 ? 0xFF5555 : 0xFFFFFF;
            context.drawTextWithShadow(client.textRenderer, "Dungeon Breaker", 8, 112, 0xFF5555);
            context.drawTextWithShadow(client.textRenderer,
                    "Charges: " + current + "/" + maximum, 8, 123, color);
            return;
        }
    }

    private static void load() {
        if (!Files.exists(DATA_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(DATA_FILE)) {
            properties.load(input);
            hideBlessingMessages = Boolean.parseBoolean(properties.getProperty("hideBlessingMessages", "false"));
            breakerOverlayEnabled = Boolean.parseBoolean(properties.getProperty("breakerOverlayEnabled", "false"));
            bossHighlightsEnabled = Boolean.parseBoolean(properties.getProperty("bossHighlightsEnabled", "true"));
            bloodHighlightsEnabled = Boolean.parseBoolean(properties.getProperty("bloodHighlightsEnabled", "true"));
        } catch (IOException ignored) {
            hideBlessingMessages = false;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("hideBlessingMessages", Boolean.toString(hideBlessingMessages));
        properties.setProperty("breakerOverlayEnabled", Boolean.toString(breakerOverlayEnabled));
        properties.setProperty("bossHighlightsEnabled", Boolean.toString(bossHighlightsEnabled));
        properties.setProperty("bloodHighlightsEnabled", Boolean.toString(bloodHighlightsEnabled));
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(DATA_FILE)) {
                properties.store(output, "Skyblock QoL Fabric Dungeon Settings");
            }
        } catch (IOException ignored) {
            // Do not prevent normal play if the settings file cannot be written.
        }
    }
}
