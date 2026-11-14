package dev.strubbonart.skyblockqol.features.mining;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manual-session port of the Aetheria Ghost Tracker.
 * Enable it only while farming ghosts with /ghosttracker toggle.
 */
public final class GhostTrackerFeatures {
    private static final Pattern RARE_DROP = Pattern.compile(
            "RARE DROP!.*?(Sorrow|Volta|Plasma|Ghostly Boots|Bag of Cash).*?\\+(\\d+)% Magic Find",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMBAT_XP = Pattern.compile(
            "\\+([\\d.]+) Combat \\(([\\d,.]+)(?:\\s*/[^)]*)?\\)"
    );
    private static final Pattern COIN_DROP = Pattern.compile(
            "The ghost's death materialized [\\d,]+ coins from the mists!",
            Pattern.CASE_INSENSITIVE
    );
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-ghost.properties");

    private static boolean tracking;
    private static int hudX = 8;
    private static int hudY = 58;
    private static float hudScale = 1.0F;
    private static long kills;
    private static long totalCombatXp;
    private static long sorrow;
    private static long volta;
    private static long plasma;
    private static long ghostlyBoots;
    private static long bagsOfCash;
    private static long coinDrops;
    private static long totalMagicFind;
    private static long magicFindDrops;
    private static long highestMagicFind;
    private static double previousCombatProgress = -1;
    private static long activeTimeMs;
    private static long lastTick = System.currentTimeMillis();
    private static long lastActivity = 0;
    private static long lastSave = 0;

    private GhostTrackerFeatures() {}

    public static boolean isTracking() {
        return tracking;
    }

    public static int getHudX() { return hudX; }
    public static int getHudY() { return hudY; }
    public static float getHudScale() { return hudScale; }

    public static void adjustHudLayout(int dx, int dy, float scaleDelta) {
        hudX = Math.max(0, Math.min(5000, hudX + dx));
        hudY = Math.max(0, Math.min(5000, hudY + dy));
        hudScale = Math.max(0.5F, Math.min(3.0F,
                Math.round((hudScale + scaleDelta) * 10.0F) / 10.0F));
        save();
    }

    public static void resetHudLayout() {
        hudX = 8;
        hudY = 58;
        hudScale = 1.0F;
        save();
    }

    public static void setTracking(boolean value) {
        tracking = value;
        previousCombatProgress = -1;
        lastTick = System.currentTimeMillis();
        save();
    }

    public static void initialize() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientReceiveMessageEvents.ALLOW_GAME.register(GhostTrackerFeatures::onMessage);
        HudRenderCallback.EVENT.register(GhostTrackerFeatures::renderHud);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("ghosttracker")
                        .then(ClientCommandManager.literal("toggle").executes(context -> {
                            setTracking(!tracking);
                            context.getSource().sendFeedback(Text.literal(
                                    "Ghost Tracker " + (tracking ? "enabled. Use only in The Mist." : "disabled.")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(status()));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            reset();
                            context.getSource().sendFeedback(Text.literal("Ghost Tracker counters reset."));
                            return 1;
                        }))));
    }

    private static boolean onMessage(Text text, boolean overlay) {
        if (!tracking) return true;
        String message = text.getString().replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
        long now = System.currentTimeMillis();

        if (overlay) {
            Matcher xp = COMBAT_XP.matcher(message);
            if (xp.find()) {
                try {
                    double xpPerKill = Double.parseDouble(xp.group(1));
                    double currentProgress = Double.parseDouble(xp.group(2).replace(",", ""));
                    if (previousCombatProgress >= 0 && xpPerKill > 0) {
                        double delta = currentProgress - previousCombatProgress;
                        if (delta > 0) {
                            long gainedKills = Math.round(delta / xpPerKill);
                            if (gainedKills > 0 && gainedKills <= 15) {
                                kills += gainedKills;
                                totalCombatXp += Math.round(xpPerKill * gainedKills);
                                activity(now);
                            }
                        }
                    }
                    previousCombatProgress = currentProgress;
                } catch (NumberFormatException ignored) {
                    previousCombatProgress = -1;
                }
            }
        } else {
            if (COIN_DROP.matcher(message).find()) {
                coinDrops++;
                activity(now);
                save();
            }
            Matcher drop = RARE_DROP.matcher(message);
            if (drop.find()) {
                String name = drop.group(1).toLowerCase(Locale.ROOT);
                int mf;
                try { mf = Integer.parseInt(drop.group(2)); }
                catch (NumberFormatException ignored) { mf = 0; }
                switch (name) {
                    case "sorrow" -> sorrow++;
                    case "volta" -> volta++;
                    case "plasma" -> plasma++;
                    case "ghostly boots" -> ghostlyBoots++;
                    case "bag of cash" -> bagsOfCash++;
                    default -> { }
                }
                totalMagicFind += mf;
                magicFindDrops++;
                highestMagicFind = Math.max(highestMagicFind, mf);
                activity(now);
                save();
            }
        }
        return true;
    }

    private static void activity(long now) {
        lastActivity = now;
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!tracking) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(hudX, hudY, 0.0F);
        matrices.scale(hudScale, hudScale, 1.0F);
        int y = 0;
        context.drawTextWithShadow(client.textRenderer, "Ghost Tracker (manual mode)", 0, y, 0xFFAA00);
        y += 11;
        context.drawTextWithShadow(client.textRenderer,
                "Kills: " + format(kills) + " | Combat XP: " + format(totalCombatXp), 0, y, 0xFFFFFF);
        y += 11;
        context.drawTextWithShadow(client.textRenderer,
                "Sorrow " + sorrow + " | Volta " + volta + " | Plasma " + plasma, 0, y, 0x55FFFF);
        y += 11;
        context.drawTextWithShadow(client.textRenderer,
                "Ghostly Boots " + ghostlyBoots + " | Bags " + bagsOfCash + " | Coin drops " + coinDrops,
                0, y, 0xFFFFFF);
        y += 11;
        long avgMf = magicFindDrops == 0 ? 0 : totalMagicFind / magicFindDrops;
        context.drawTextWithShadow(client.textRenderer,
                "Magic Find avg " + avgMf + "% | Highest " + highestMagicFind + "%", 0, y, 0xFF55FF);
        matrices.pop();
    }

    private static String status() {
        long avgMf = magicFindDrops == 0 ? 0 : totalMagicFind / magicFindDrops;
        return "Ghost Tracker " + (tracking ? "ON" : "OFF") + " | Kills " + kills
                + " | Sorrow " + sorrow + " | Volta " + volta + " | Plasma " + plasma
                + " | Avg MF " + avgMf + "%";
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (tracking && lastActivity > 0 && now - lastActivity <= 120_000L) {
            activeTimeMs += Math.max(0, now - lastTick);
        }
        lastTick = now;
        if (now - lastSave >= 300_000L) save();
    }

    private static void reset() {
        kills = totalCombatXp = sorrow = volta = plasma = ghostlyBoots = 0;
        bagsOfCash = coinDrops = totalMagicFind = magicFindDrops = highestMagicFind = 0;
        activeTimeMs = 0;
        previousCombatProgress = -1;
        lastActivity = 0;
        save();
    }

    private static void load() {
        if (!Files.exists(DATA_FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(DATA_FILE)) {
            p.load(in);
            tracking = Boolean.parseBoolean(p.getProperty("tracking", "false"));
            kills = number(p, "kills");
            totalCombatXp = number(p, "totalCombatXp");
            sorrow = number(p, "sorrow");
            volta = number(p, "volta");
            plasma = number(p, "plasma");
            ghostlyBoots = number(p, "ghostlyBoots");
            bagsOfCash = number(p, "bagsOfCash");
            coinDrops = number(p, "coinDrops");
            totalMagicFind = number(p, "totalMagicFind");
            magicFindDrops = number(p, "magicFindDrops");
            highestMagicFind = number(p, "highestMagicFind");
            activeTimeMs = number(p, "activeTimeMs");
            hudX = integer(p, "hudX", 8);
            hudY = integer(p, "hudY", 58);
            hudScale = decimal(p, "hudScale", 1.0F);
        } catch (IOException ignored) {
            tracking = false;
        }
    }

    private static long number(Properties p, String key) {
        try { return Long.parseLong(p.getProperty(key, "0")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static int integer(Properties p, String key, int fallback) {
        try { return Math.max(0, Math.min(5000, Integer.parseInt(p.getProperty(key, Integer.toString(fallback))))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static float decimal(Properties p, String key, float fallback) {
        try {
            float value = Float.parseFloat(p.getProperty(key, Float.toString(fallback)));
            return Float.isFinite(value) ? Math.max(0.5F, Math.min(3.0F, value)) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("tracking", Boolean.toString(tracking));
        p.setProperty("kills", Long.toString(kills));
        p.setProperty("totalCombatXp", Long.toString(totalCombatXp));
        p.setProperty("sorrow", Long.toString(sorrow));
        p.setProperty("volta", Long.toString(volta));
        p.setProperty("plasma", Long.toString(plasma));
        p.setProperty("ghostlyBoots", Long.toString(ghostlyBoots));
        p.setProperty("bagsOfCash", Long.toString(bagsOfCash));
        p.setProperty("coinDrops", Long.toString(coinDrops));
        p.setProperty("totalMagicFind", Long.toString(totalMagicFind));
        p.setProperty("magicFindDrops", Long.toString(magicFindDrops));
        p.setProperty("highestMagicFind", Long.toString(highestMagicFind));
        p.setProperty("activeTimeMs", Long.toString(activeTimeMs));
        p.setProperty("hudX", Integer.toString(hudX));
        p.setProperty("hudY", Integer.toString(hudY));
        p.setProperty("hudScale", Float.toString(hudScale));
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(DATA_FILE)) {
                p.store(out, "Skyblock QoL Fabric Ghost Tracker");
            }
            lastSave = System.currentTimeMillis();
        } catch (IOException ignored) {
            // Persistence failure must not affect client stability.
        }
    }

    private static String format(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }
}
