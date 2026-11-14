package dev.strubbonart.skyblockqol.features.dungeons;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Displays the live dungeon map from hotbar slot 9. Map decorations are drawn
 * by vanilla's MapRenderer, keeping the server-provided player markers intact.
 * Custom Aetheria room labels/player-name decorations are a later migration slice.
 */
public final class DungeonMapOverlay {
    private static final Path SETTINGS = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-dungeon-map.properties");
    private static final int MAP_SIZE = 128;
    private static final int PADDING = 5;

    private static boolean enabled;

    private DungeonMapOverlay() {}

    public static void initialize() {
        load();
        HudRenderCallback.EVENT.register(DungeonMapOverlay::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("dungeonmap").executes(context -> {
                    enabled = !enabled;
                    save();
                    context.getSource().sendFeedback(Text.literal(
                            "Dungeon map HUD " + (enabled ? "enabled" : "disabled")));
                    return 1;
                })));
    }

    public static boolean toggle() {
        enabled = !enabled;
        save();
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!enabled || !DungeonRunOverlay.isInDungeon()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden
                || client.currentScreen != null) return;

        ItemStack mapStack = client.player.getInventory().getStack(8);
        if (mapStack.isEmpty()) return;

        MapIdComponent mapId = mapStack.get(DataComponentTypes.MAP_ID);
        if (mapId == null) return;

        MapState mapState = client.world.getMapState(mapId);
        if (mapState == null) return;

        int x = context.getScaledWindowWidth() - MAP_SIZE - PADDING - 8;
        int y = 8;

        context.fill(x - 3, y - 3, x + MAP_SIZE + 3, y + MAP_SIZE + 3, 0x99000000);
        context.drawTextWithShadow(client.textRenderer, "Dungeon Map", x, y - 11, 0xFFFFFFFF);

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        client.gameRenderer.getMapRenderer().draw(
                matrices,
                context.getVertexConsumers(),
                mapId,
                mapState,
                false,
                0x00F000F0
        );
        matrices.pop();
    }

    private static void load() {
        if (!Files.isRegularFile(SETTINGS)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(SETTINGS)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
        } catch (IOException ignored) {
            enabled = false;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        try {
            Files.createDirectories(SETTINGS.getParent());
            try (OutputStream output = Files.newOutputStream(SETTINGS)) {
                properties.store(output, "Skyblock QoL Dungeon Map Settings");
            }
        } catch (IOException ignored) {
            // The HUD remains usable for the current session if persistence fails.
        }
    }
}
