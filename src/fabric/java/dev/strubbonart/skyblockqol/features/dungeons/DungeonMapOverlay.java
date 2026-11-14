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
import net.minecraft.item.map.MapDecoration;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.client.network.PlayerListEntry;
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
        drawPlayerLabels(context, mapState, x, y, client);
    }

    private static void drawPlayerLabels(DrawContext context, MapState mapState,
                                          int mapX, int mapY, MinecraftClient client) {
        java.util.List<String> names = getOrderedPartyNames(client);
        if (names.isEmpty()) return;

        int playerIndex = 0;
        for (MapDecoration decoration : mapState.getDecorations()) {
            String asset = decoration.getAssetId().getPath();
            if (!asset.startsWith("player")) continue;

            String name = decoration.name().map(Text::getString).orElse(null);
            if (name == null || name.isBlank()) {
                if (playerIndex >= names.size()) break;
                name = names.get(playerIndex);
            }
            playerIndex++;

            int px = (int) Math.round(decoration.x() / 2.0 + 64.0);
            int pz = (int) Math.round(decoration.z() / 2.0 + 64.0);
            if (px < 0 || px >= MAP_SIZE || pz < 0 || pz >= MAP_SIZE) continue;

            int color = name.equalsIgnoreCase(client.player.getName().getString())
                    ? 0xFFFFFFFF : 0xFFFFE68A;
            context.drawTextWithShadow(client.textRenderer, name, mapX + px + 3, mapY + pz - 4, color);
        }
    }

    private static java.util.List<String> getOrderedPartyNames(MinecraftClient client) {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) return names;

        String self = client.player.getName().getString();
        names.add(self);

        java.util.List<String> tabNames = new java.util.ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) continue;
            String name = entry.getProfile().getName();
            if (isPlausibleUsername(name) && !tabNames.contains(name)) tabNames.add(name);
        }

        ScoreboardObjective objective = client.world.getScoreboard()
                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return names;

        java.util.List<String> lines = new java.util.ArrayList<>();
        for (ScoreboardEntry entry : client.world.getScoreboard().getScoreboardEntries(objective)) {
            if (entry.hidden()) continue;
            String line;
            if (entry.display() != null) {
                line = stripFormatting(entry.display().getString());
            } else {
                Team team = client.world.getScoreboard().getScoreHolderTeam(entry.owner());
                line = stripFormatting(team == null
                        ? entry.name().getString()
                        : team.decorateName(entry.name()).getString());
            }
            lines.add(line);
        }

        for (String line : lines) {
            for (String tabName : tabNames) {
                boolean matches = line.contains(tabName)
                        || (tabName.length() > 12 && line.contains(tabName.substring(0, 12)));
                if (matches && !containsIgnoreCase(names, tabName)) names.add(tabName);
            }
        }
        return names;
    }

    private static boolean isPlausibleUsername(String name) {
        if (name == null || name.length() < 3 || name.length() > 16) return false;
        boolean hasLetter = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_')) return false;
            if (Character.isLetter(c)) hasLetter = true;
        }
        return hasLetter;
    }

    private static boolean containsIgnoreCase(java.util.List<String> names, String value) {
        for (String name : names) if (name.equalsIgnoreCase(value)) return true;
        return false;
    }

    private static String stripFormatting(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
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
