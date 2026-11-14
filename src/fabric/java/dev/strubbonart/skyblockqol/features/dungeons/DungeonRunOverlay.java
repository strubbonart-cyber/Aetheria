package dev.strubbonart.skyblockqol.features.dungeons;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basic scoreboard-backed Dungeon run timer and floor HUD.
 * This is a small first port, not the full original phase/split tracker.
 */
public final class DungeonRunOverlay {
    private static final Pattern ELAPSED = Pattern.compile("Time Elapsed:\\s*([0-9:]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOOR = Pattern.compile("\\b(F[1-7]|M[1-7])\\b", Pattern.CASE_INSENSITIVE);

    private static boolean enabled = true;
    private static String currentFloor = "";
    private static long elapsedSeconds = -1;
    private static boolean inDungeon;
    private static int ticksSinceSidebarSeen;

    private DungeonRunOverlay() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register(DungeonRunOverlay::render);
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            clear();
            return;
        }

        ScoreboardObjective objective = client.world.getScoreboard()
                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) {
            noSidebar();
            return;
        }

        String title = strip(objective.getDisplayName().getString());
        Collection<ScoreboardEntry> entries = client.world.getScoreboard().getScoreboardEntries(objective);
        StringBuilder combined = new StringBuilder();
        for (ScoreboardEntry entry : entries) {
            if (entry.hidden()) continue;
            String line;
            if (entry.display() != null) {
                line = entry.display().getString();
            } else {
                Team team = client.world.getScoreboard().getScoreHolderTeam(entry.owner());
                line = team == null
                        ? entry.name().getString()
                        : team.decorateName(entry.name()).getString();
            }
            combined.append(strip(line)).append('\n');
        }

        String scoreboardText = combined.toString();
        Matcher timerMatcher = ELAPSED.matcher(scoreboardText);
        boolean foundTimer = timerMatcher.find();
        if (!foundTimer) {
            if (title.toLowerCase(Locale.ROOT).contains("catacombs")
                    || scoreboardText.toLowerCase(Locale.ROOT).contains("dungeon cleared")) {
                noSidebar();
            } else {
                noSidebar();
            }
            return;
        }

        inDungeon = true;
        ticksSinceSidebarSeen = 0;
        elapsedSeconds = parseElapsed(timerMatcher.group(1));
        Matcher floorMatcher = FLOOR.matcher(title + "\n" + scoreboardText);
        currentFloor = floorMatcher.find() ? floorMatcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private static long parseElapsed(String value) {
        try {
            if (value.contains(":")) {
                String[] parts = value.split(":");
                long total = 0;
                for (String part : parts) total = total * 60 + Long.parseLong(part);
                return total;
            }
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void noSidebar() {
        if (++ticksSinceSidebarSeen > 60) clear();
    }

    private static void clear() {
        inDungeon = false;
        currentFloor = "";
        elapsedSeconds = -1;
        ticksSinceSidebarSeen = 0;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!enabled || !inDungeon || elapsedSeconds < 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;
        String floor = currentFloor.isEmpty() ? "Dungeon" : "Dungeon " + currentFloor;
        context.drawTextWithShadow(client.textRenderer,
                floor + "  " + String.format(Locale.ROOT, "%02d:%02d", minutes, seconds),
                8, 156, 0x55FFFF);
    }

    private static String strip(String value) {
        return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }
}
