package dev.strubbonart.skyblockqol.features.dungeons;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fabric-native port of the original Aetheria Dungeon phase/split tracker.
 * Uses scoreboard elapsed time as the clock and chat announcements as split events.
 */
public final class DungeonPhaseTracker {
    private static final Pattern CLEAR_PROGRESS = Pattern.compile("Dungeon Cleared:\\s*(\\d+)%");
    private static final Pattern BLOOD_DOOR = Pattern.compile("The BLOOD DOOR has been opened!");
    private static final Pattern RUN_FAILED = Pattern.compile("Warning! This dungeon will close in 10s");
    private static final Pattern BOSS_SLAIN = Pattern.compile("Defeated (.+) in \\d");
    private static final Pattern SCORE = Pattern.compile("Team Score:\\s*(\\d+)\\s*\\((.{1,2})\\)\\s*(\\(NEW RECORD!\\))?");
    private static final Pattern XP = Pattern.compile("(\\+[\\d,.]+\\s?\\w+ Experience)(?:\\(.+\\))?");

    private static final Pattern SCARF_P1_START = Pattern.compile("BOSS.*Scarf.*ARISE, MY CREATIONS!");
    private static final Pattern SCARF_P1_END = Pattern.compile("BOSS.*Scarf.*Those toys are not strong enough I see");
    private static final Pattern SCARF_P2_END = Pattern.compile("BOSS.*Scarf.*You'll never beat my teacher");

    private static final Pattern PROFESSOR_P1_START = Pattern.compile("BOSS.*The Professor.*I'll show you real power!");
    private static final Pattern PROFESSOR_P1_END = Pattern.compile("BOSS.*The Professor.*Oh\\? You found my Guardians' one weakness\\?");
    private static final Pattern PROFESSOR_P2_END = Pattern.compile("BOSS.*The Professor.*I see\\. You have forced me to use my ultimate technique");
    private static final Pattern PROFESSOR_P3_END = Pattern.compile("BOSS.*The Professor.*What\\?! My Guardian power is unbeatable!");

    private static final Pattern TERRA_START = Pattern.compile("BOSS.*Sadan.*I am the bridge between this realm");
    private static final Pattern TERRA_END = Pattern.compile("BOSS.*Sadan.*ENOUGH!");
    private static final Pattern GIANTS_END = Pattern.compile("BOSS.*Sadan.*I.m sorry but I need to concentrate");
    private static final Pattern SADAN_END = Pattern.compile("BOSS.*Sadan.*FATHER, FORGIVE ME");

    private static final Pattern MAXOR_START = Pattern.compile("BOSS.*Maxor.*WELL WELL WELL");
    private static final Pattern MAXOR_END = Pattern.compile("BOSS.*Maxor.*TOO YOUNG TO DIE");
    private static final Pattern STORM_START = Pattern.compile("BOSS.*Storm.*Pathetic Maxor");
    private static final Pattern STORM_END = Pattern.compile("BOSS.*Storm.*At least my son");
    private static final Pattern TERMINAL_START = Pattern.compile("BOSS.*Goldor.*Who dares trespass");
    private static final Pattern GOLDOR_FIGHT = Pattern.compile("The Core entrance is opening");
    private static final Pattern GOLDOR_END = Pattern.compile("BOSS.*Goldor.*You have done it");
    private static final Pattern NECRON_START = Pattern.compile("BOSS.*Necron.*Finally, I heard so much");
    private static final Pattern NECRON_END = Pattern.compile("BOSS.*Necron.*The Catacombs.*are no more");
    private static final Pattern WITHER_START = Pattern.compile("BOSS.*(?:WITHER KING.*You\\.\\.\\. again|The Wither King.*Ohh)");
    private static final Pattern WITHER_END = Pattern.compile("BOSS.*WITHER KING.*My strengths are depleting");

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, Long> starts = new LinkedHashMap<>();
    private static final Map<String, Long> ends = new LinkedHashMap<>();
    private static final List<String> xpLines = new ArrayList<>();

    private static boolean running;
    private static boolean ended;
    private static boolean hudEnabled = true;
    private static boolean failed;
    private static int clearPercent;
    private static long clearTime = -1;
    private static long bloodTime = -1;
    private static long bossEndTime = -1;
    private static long lastElapsed = -1;
    private static int ticksOutsideDungeon;
    private static String bossName;
    private static String score;
    private static String grade;
    private static boolean scorePb;
    private static Object lastWorld;

    static {
        LABELS.put("scarfP1", "Scarf P1");
        LABELS.put("scarfP2", "Scarf P2");
        LABELS.put("professorP1", "Professor P1");
        LABELS.put("professorP2", "Professor P2");
        LABELS.put("professorP3", "Professor P3");
        LABELS.put("terra", "Terracotta");
        LABELS.put("giants", "Giants");
        LABELS.put("sadan", "Sadan");
        LABELS.put("maxor", "Maxor");
        LABELS.put("storm", "Storm");
        LABELS.put("terminals", "Terminals");
        LABELS.put("goldor", "Goldor");
        LABELS.put("necron", "Necron");
        LABELS.put("witherKing", "Wither King");
    }

    private DungeonPhaseTracker() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(DungeonPhaseTracker::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register(DungeonPhaseTracker::onMessage);
        HudRenderCallback.EVENT.register(DungeonPhaseTracker::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("dungeonsplits").executes(context -> {
                    hudEnabled = !hudEnabled;
                    context.getSource().sendFeedback(Text.literal(
                            "Dungeon split HUD " + (hudEnabled ? "enabled" : "disabled")));
                    return 1;
                })));
    }

    private static void tick(MinecraftClient client) {
        if (client.world != lastWorld) {
            lastWorld = client.world;
            resetRun();
        }
        if (client.world == null || client.player == null) {
            resetRun();
            return;
        }

        if (!DungeonRunOverlay.isInDungeon()) {
            if (++ticksOutsideDungeon > 60) resetRun();
            return;
        }
        ticksOutsideDungeon = 0;

        long elapsed = DungeonRunOverlay.getElapsedSeconds();
        if (elapsed < 0) return;
        if (!running || (ended && lastElapsed > 5 && elapsed <= 3)) {
            resetRun();
            running = true;
        }
        lastElapsed = elapsed;

        ScoreboardObjective objective = client.world.getScoreboard()
                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null || clearTime >= 0) return;

        StringBuilder lines = new StringBuilder();
        for (ScoreboardEntry entry : client.world.getScoreboard().getScoreboardEntries(objective)) {
            if (entry.hidden()) continue;
            String line;
            if (entry.display() != null) {
                line = entry.display().getString();
            } else {
                Team team = client.world.getScoreboard().getScoreHolderTeam(entry.owner());
                line = team == null ? entry.name().getString()
                        : team.decorateName(entry.name()).getString();
            }
            lines.append(strip(line)).append('\n');
        }
        Matcher clear = CLEAR_PROGRESS.matcher(lines);
        if (clear.find()) {
            try {
                int next = Integer.parseInt(clear.group(1));
                if (next >= 100 && clearPercent < 100) clearTime = elapsed;
                clearPercent = next;
            } catch (NumberFormatException ignored) {
                // Ignore malformed scoreboard updates.
            }
        }
    }

    private static boolean onMessage(Text message, boolean overlay) {
        if (!running || ended || message == null) return true;
        String clean = strip(message.getString()).trim();
        if (clean.isEmpty()) return true;
        long time = DungeonRunOverlay.getElapsedSeconds();
        if (time < 0) return true;

        if (BLOOD_DOOR.matcher(clean).find() && bloodTime < 0) bloodTime = time;

        Matcher scoreMatcher = SCORE.matcher(clean);
        if (scoreMatcher.find()) {
            score = scoreMatcher.group(1);
            grade = scoreMatcher.group(2);
            scorePb = scoreMatcher.group(3) != null;
        }
        Matcher xpMatcher = XP.matcher(clean);
        if (xpMatcher.find()) xpLines.add(xpMatcher.group(1).replace("Experience", "EXP").replace("Catacombs", "Cata"));

        String floor = DungeonRunOverlay.getCurrentFloor().toUpperCase(Locale.ROOT);
        if (floor.equals("F2") || floor.equals("M2")) {
            capture(clean, "scarfP1", SCARF_P1_START, SCARF_P1_END, time);
            if (SCARF_P1_END.matcher(clean).find()) start("scarfP2", time);
            if (SCARF_P2_END.matcher(clean).find()) end("scarfP2", time);
        } else if (floor.equals("F3") || floor.equals("M3")) {
            capture(clean, "professorP1", PROFESSOR_P1_START, PROFESSOR_P1_END, time);
            if (PROFESSOR_P1_END.matcher(clean).find()) start("professorP2", time);
            if (PROFESSOR_P2_END.matcher(clean).find()) start("professorP3", time);
            if (PROFESSOR_P2_END.matcher(clean).find()) end("professorP2", time);
            if (PROFESSOR_P3_END.matcher(clean).find()) end("professorP3", time);
        } else if (floor.equals("F6") || floor.equals("M6")) {
            capture(clean, "terra", TERRA_START, TERRA_END, time);
            if (TERRA_END.matcher(clean).find()) start("giants", time);
            if (GIANTS_END.matcher(clean).find()) end("giants", time);
            if (GIANTS_END.matcher(clean).find()) start("sadan", time);
            if (SADAN_END.matcher(clean).find()) end("sadan", time);
        } else if (floor.equals("F7") || floor.equals("M7")) {
            capture(clean, "maxor", MAXOR_START, MAXOR_END, time);
            capture(clean, "storm", STORM_START, STORM_END, time);
            if (TERMINAL_START.matcher(clean).find()) start("terminals", time);
            if (GOLDOR_FIGHT.matcher(clean).find()) {
                end("terminals", time);
                start("goldor", time);
            }
            if (GOLDOR_END.matcher(clean).find()) end("goldor", time);
            if (GOLDOR_END.matcher(clean).find()) start("necron", time);
            if (NECRON_END.matcher(clean).find()) end("necron", time);
            capture(clean, "witherKing", WITHER_START, WITHER_END, time);
        }

        Matcher slain = BOSS_SLAIN.matcher(clean);
        if (slain.find()) {
            bossName = slain.group(1).trim();
            bossEndTime = time;
            finish(false);
        } else if (RUN_FAILED.matcher(clean).find()) {
            failed = true;
            finish(true);
        } else if (floor.equals("M7") && WITHER_END.matcher(clean).find()) {
            bossName = "Wither King";
            bossEndTime = time;
            finish(false);
        }
        return true;
    }

    private static void capture(String message, String key, Pattern startPattern,
                                Pattern endPattern, long time) {
        if (startPattern.matcher(message).find()) start(key, time);
        if (endPattern.matcher(message).find()) end(key, time);
    }

    private static void start(String key, long time) {
        if (!starts.containsKey(key)) starts.put(key, time);
    }

    private static void end(String key, long time) {
        if (starts.containsKey(key) && !ends.containsKey(key)) ends.put(key, time);
    }

    private static void finish(boolean runFailed) {
        if (ended) return;
        failed = runFailed;
        ended = true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String floor = DungeonRunOverlay.getCurrentFloor();
        StringBuilder summary = new StringBuilder("Dungeon ")
                .append(floor.isEmpty() ? "run" : floor)
                .append(failed ? " failed" : " complete");
        if (bossName != null) summary.append(" | Boss: ").append(bossName);
        client.player.sendMessage(Text.literal(summary.toString()), false);

        if (score != null) {
            client.player.sendMessage(Text.literal("Score: " + score + " (" + grade + ")"
                    + (scorePb ? " NEW RECORD!" : "")), false);
        }
        if (clearTime >= 0) client.player.sendMessage(Text.literal("Clear: " + format(clearTime)), false);
        if (bloodTime >= 0) client.player.sendMessage(Text.literal("Blood rush: " + format(bloodTime)), false);
        if (bossEndTime >= 0) client.player.sendMessage(Text.literal("Run time: " + format(bossEndTime)), false);

        for (Map.Entry<String, Long> entry : starts.entrySet()) {
            Long end = ends.get(entry.getKey());
            if (end == null || end < entry.getValue()) continue;
            String label = LABELS.getOrDefault(entry.getKey(), entry.getKey());
            client.player.sendMessage(Text.literal(label + ": " + format(end - entry.getValue())), false);
        }
        for (String xp : xpLines) client.player.sendMessage(Text.literal(xp), false);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!hudEnabled || !running || ended) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        int y = 178;
        String floor = DungeonRunOverlay.getCurrentFloor();
        context.drawTextWithShadow(client.textRenderer,
                "Dungeon splits " + (floor.isEmpty() ? "" : floor) + " | " + format(lastElapsed),
                8, y, 0x55FFFF);
        y += 12;
        if (clearTime >= 0) {
            context.drawTextWithShadow(client.textRenderer, "Clear: " + format(clearTime), 8, y, 0x55FF55);
            y += 11;
        } else if (clearPercent > 0) {
            context.drawTextWithShadow(client.textRenderer, "Cleared: " + clearPercent + "%", 8, y, 0xFFFFFF);
            y += 11;
        }
        if (bloodTime >= 0) {
            context.drawTextWithShadow(client.textRenderer, "Blood: " + format(bloodTime), 8, y, 0xFF5555);
            y += 11;
        }
        for (Map.Entry<String, Long> entry : starts.entrySet()) {
            if (ends.containsKey(entry.getKey())) continue;
            String label = LABELS.getOrDefault(entry.getKey(), entry.getKey());
            context.drawTextWithShadow(client.textRenderer,
                    label + ": " + format(Math.max(0, lastElapsed - entry.getValue())),
                    8, y, 0xFFFF55);
            y += 11;
            break;
        }
    }

    private static void resetRun() {
        running = false;
        ended = false;
        failed = false;
        clearPercent = 0;
        clearTime = -1;
        bloodTime = -1;
        bossEndTime = -1;
        lastElapsed = -1;
        ticksOutsideDungeon = 0;
        bossName = null;
        score = null;
        grade = null;
        scorePb = false;
        starts.clear();
        ends.clear();
        xpLines.clear();
    }

    private static String format(long seconds) {
        if (seconds < 0) return "--:--";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        return String.format(Locale.ROOT, "%02d:%02d", minutes, secs);
    }

    private static String strip(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}
