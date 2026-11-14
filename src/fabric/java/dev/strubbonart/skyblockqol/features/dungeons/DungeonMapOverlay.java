package dev.strubbonart.skyblockqol.features.dungeons;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapState;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Adjustable live Dungeon map HUD.
 *
 * Controls:
 *   /dungeonmap                 toggle map
 *   /dungeonmap labels          toggle player name labels
 *   /dungeonmap position x y   set top-left screen position in scaled GUI pixels
 *   /dungeonmap scale value    set size multiplier (0.5 to 2.0)
 *   /dungeonmap reset           restore default position/size/labels
 */
public final class DungeonMapOverlay {
    private static final Path SETTINGS = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-dungeon-map.properties");
    private static final int BASE_SIZE = 128;
    private static final int PADDING = 5;

    private static boolean enabled;
    private static boolean labelsEnabled = true;
    private static int positionX = -1; // -1 = right-aligned default
    private static int positionY = 8;
    private static float scale = 1.0F;

    private DungeonMapOverlay() {}

    public static void initialize() {
        load();
        HudRenderCallback.EVENT.register(DungeonMapOverlay::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("dungeonmap")
                        .executes(context -> {
                            boolean state = toggle();
                            context.getSource().sendFeedback(Text.literal(
                                    "Dungeon map HUD " + (state ? "enabled" : "disabled")));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("toggle").executes(context -> {
                            boolean state = toggle();
                            context.getSource().sendFeedback(Text.literal(
                                    "Dungeon map HUD " + (state ? "enabled" : "disabled")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("labels").executes(context -> {
                            labelsEnabled = !labelsEnabled;
                            save();
                            context.getSource().sendFeedback(Text.literal(
                                    "Dungeon map player labels " + (labelsEnabled ? "enabled" : "disabled")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("position")
                                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0, 10000))
                                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0, 10000))
                                                .executes(context -> {
                                                    positionX = IntegerArgumentType.getInteger(context, "x");
                                                    positionY = IntegerArgumentType.getInteger(context, "y");
                                                    save();
                                                    context.getSource().sendFeedback(Text.literal(
                                                            "Dungeon map position set to " + positionX + ", " + positionY
                                                                    + " (scaled GUI pixels from top-left)"));
                                                    return 1;
                                                }))))
                        .then(ClientCommandManager.literal("scale")
                                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.5F, 2.0F))
                                        .executes(context -> {
                                            scale = FloatArgumentType.getFloat(context, "value");
                                            save();
                                            context.getSource().sendFeedback(Text.literal(
                                                    String.format(Locale.ROOT, "Dungeon map scale: %.2fx", scale)));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            positionX = -1;
                            positionY = 8;
                            scale = 1.0F;
                            labelsEnabled = true;
                            save();
                            context.getSource().sendFeedback(Text.literal("Dungeon map position, size and labels reset."));
                            return 1;
                        }))));
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

        int mapSize = Math.round(BASE_SIZE * scale);
        int x = positionX < 0
                ? context.getScaledWindowWidth() - mapSize - PADDING - 8
                : positionX;
        int y = positionY;

        context.fill(x - 3, y - 3, x + mapSize + 3, y + mapSize + 3, 0x99000000);
        context.drawTextWithShadow(client.textRenderer, "Dungeon Map", x, y - 11, 0xFFFFFFFF);

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        client.gameRenderer.getMapRenderer().draw(
                matrices,
                context.getVertexConsumers(),
                mapId,
                mapState,
                false,
                0x00F000F0
        );
        matrices.pop();

        if (labelsEnabled) drawPlayerLabels(context, mapState, x, y, client);
    }

    private static void drawPlayerLabels(DrawContext context, MapState mapState,
                                         int mapX, int mapY, MinecraftClient client) {
        List<String> names = getOrderedPartyNames(client);
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

            int px = (int) Math.round((decoration.x() / 2.0 + 64.0) * scale);
            int pz = (int) Math.round((decoration.z() / 2.0 + 64.0) * scale);
            if (px < 0 || px >= BASE_SIZE * scale || pz < 0 || pz >= BASE_SIZE * scale) continue;

            int color = name.equalsIgnoreCase(client.player.getName().getString())
                    ? 0xFFFFFFFF : 0xFFFFE68A;
            context.drawTextWithShadow(client.textRenderer, name, mapX + px + 3, mapY + pz - 4, color);
        }
    }

    private static List<String> getOrderedPartyNames(MinecraftClient client) {
        List<String> names = new ArrayList<>();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) return names;

        String self = client.player.getName().getString();
        names.add(self);

        List<String> tabNames = new ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) continue;
            String name = entry.getProfile().getName();
            if (isPlausibleUsername(name) && !tabNames.contains(name)) tabNames.add(name);
        }

        ScoreboardObjective objective = client.world.getScoreboard()
                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return names;

        List<String> lines = new ArrayList<>();
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

    private static boolean containsIgnoreCase(List<String> names, String value) {
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
            labelsEnabled = Boolean.parseBoolean(properties.getProperty("labelsEnabled", "true"));
            positionX = Integer.parseInt(properties.getProperty("positionX", "-1"));
            positionY = Integer.parseInt(properties.getProperty("positionY", "8"));
            scale = Math.max(0.5F, Math.min(2.0F,
                    Float.parseFloat(properties.getProperty("scale", "1.0"))));
        } catch (Exception ignored) {
            enabled = false;
            labelsEnabled = true;
            positionX = -1;
            positionY = 8;
            scale = 1.0F;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("labelsEnabled", Boolean.toString(labelsEnabled));
        properties.setProperty("positionX", Integer.toString(positionX));
        properties.setProperty("positionY", Integer.toString(positionY));
        properties.setProperty("scale", Float.toString(scale));
        try {
            Files.createDirectories(SETTINGS.getParent());
            try (OutputStream output = Files.newOutputStream(SETTINGS)) {
                properties.store(output, "Skyblock QoL Dungeon Map Settings");
            }
        } catch (IOException ignored) {
            // The current-session HUD remains available if disk persistence fails.
        }
    }
}
