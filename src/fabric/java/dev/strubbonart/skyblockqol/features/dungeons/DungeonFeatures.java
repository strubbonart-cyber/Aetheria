package dev.strubbonart.skyblockqol.features.dungeons;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Initial Fabric migration of Aetheria's Hide Blessing Messages option.
 * It only filters the exact dungeon blessing broadcast, never ordinary chat.
 */
public final class DungeonFeatures {
    private static final Pattern BLESSING = Pattern.compile(
            "DUNGEON BUFF! .*?found a Blessing of \\w+.*!",
            Pattern.CASE_INSENSITIVE
    );
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-dungeon.properties");
    private static boolean hideBlessingMessages;

    private DungeonFeatures() {}

    public static void initialize() {
        load();
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
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(
                                    "Hide dungeon blessing messages: "
                                            + (hideBlessingMessages ? "ON" : "OFF")));
                            return 1;
                        }))));
    }

    private static void load() {
        if (!Files.exists(DATA_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(DATA_FILE)) {
            properties.load(input);
            hideBlessingMessages = Boolean.parseBoolean(properties.getProperty("hideBlessingMessages", "false"));
        } catch (IOException ignored) {
            hideBlessingMessages = false;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("hideBlessingMessages", Boolean.toString(hideBlessingMessages));
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
