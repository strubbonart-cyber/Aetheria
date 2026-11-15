package dev.strubbonart.skyblockqol.features.farming;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * First Fabric-native Garden slice. This deliberately counts mature crop-block
 * breaks rather than guessing item drop quantities, and records only while the
 * sidebar identifies the Garden.
 */
public final class GardenFeatures {
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-garden.properties");
    private static final Set<String> CROPS = Set.of(
            "wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa",
            "melon", "pumpkin", "sugar_cane", "cactus", "brown_mushroom",
            "red_mushroom"
    );
    private static final ArrayDeque<Long> BLOCK_BREAKS = new ArrayDeque<>();
    private static final Map<String, Long> CROP_BREAKS = new HashMap<>();
    private static final Map<String, Layout> LAYOUTS = new HashMap<>();

    private static boolean initialized;
    private static boolean bpsEnabled = true;
    private static boolean trackerEnabled = true;
    private static boolean pitchEnabled;
    private static long totalHarvests;
    private static long sessionStartedAt = System.currentTimeMillis();
    private static long lastSaveAt;

    private static final class Layout {
        int x;
        int y;
        float scale;
        Layout(int x, int y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }

    private GardenFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        createDefaultLayouts();
        load();

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!world.isClient || player != client.player || !isGarden()) return;

            long now = System.currentTimeMillis();
            BLOCK_BREAKS.addLast(now);
            String crop = cropId(state);
            if (crop != null) {
                CROP_BREAKS.merge(crop, 1L, Long::sum);
                totalHarvests++;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            while (!BLOCK_BREAKS.isEmpty() && now - BLOCK_BREAKS.peekFirst() > 1_000L) {
                BLOCK_BREAKS.removeFirst();
            }
            if (now - lastSaveAt > 30_000L) save();
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    public static boolean isBpsEnabled() { return bpsEnabled; }
    public static boolean isTrackerEnabled() { return trackerEnabled; }
    public static boolean isPitchEnabled() { return pitchEnabled; }

    public static void setBpsEnabled(boolean enabled) {
        bpsEnabled = enabled;
        save();
    }

    public static void setTrackerEnabled(boolean enabled) {
        trackerEnabled = enabled;
        save();
    }

    public static void setPitchEnabled(boolean enabled) {
        pitchEnabled = enabled;
        save();
    }

    public static long getTotalHarvests() { return totalHarvests; }

    public static void resetTracker() {
        totalHarvests = 0L;
        CROP_BREAKS.clear();
        sessionStartedAt = System.currentTimeMillis();
        save();
    }

    public static int getLayoutX(String id) { return layout(id).x; }
    public static int getLayoutY(String id) { return layout(id).y; }
    public static float getLayoutScale(String id) { return layout(id).scale; }

    public static void updateLayout(String id, int x, int y, float scale) {
        Layout value = layout(id);
        value.x = Math.max(0, Math.min(5000, x));
        value.y = Math.max(0, Math.min(5000, y));
        value.scale = Math.max(0.5F, Math.min(3.0F, scale));
    }

    public static void saveLayout() { save(); }

    public static void resetLayout(String id) {
        Layout defaults = defaults(id);
        Layout target = layout(id);
        target.x = defaults.x;
        target.y = defaults.y;
        target.scale = defaults.scale;
        save();
    }

    private static void createDefaultLayouts() {
        LAYOUTS.put("bps", new Layout(8, 8, 1.0F));
        LAYOUTS.put("tracker", new Layout(8, 100, 1.0F));
        LAYOUTS.put("pitch", new Layout(8, 190, 1.0F));
    }

    private static Layout defaults(String id) {
        return switch (id) {
            case "tracker" -> new Layout(8, 100, 1.0F);
            case "pitch" -> new Layout(8, 190, 1.0F);
            default -> new Layout(8, 8, 1.0F);
        };
    }

    private static Layout layout(String id) {
        return LAYOUTS.computeIfAbsent(id, key -> defaults(key));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !isGarden()) return;

        if (bpsEnabled) {
            int recent = BLOCK_BREAKS.size();
            renderPanel(context, "bps", new String[]{
                    "Garden BPS",
                    "Blocks broken: " + recent,
                    String.format(Locale.ROOT, "Rate: %.1f blocks/s", (double) recent)
            });
        }

        if (trackerEnabled) {
            long elapsed = Math.max(1_000L, System.currentTimeMillis() - sessionStartedAt);
            long perHour = Math.round(totalHarvests * 3_600_000.0 / elapsed);
            String[] cropLines = new String[4];
            cropLines[0] = "Farming Tracker";
            cropLines[1] = "Mature crop blocks: " + totalHarvests;
            cropLines[2] = "Rate: " + perHour + " blocks/hour";
            cropLines[3] = "Session: " + formatDuration(elapsed);
            renderPanel(context, "tracker", cropLines);
        }

        if (pitchEnabled) {
            float yaw = client.player.getYaw();
            float pitch = client.player.getPitch();
            renderPanel(context, "pitch", new String[]{
                    String.format(Locale.ROOT, "Yaw: %.2f°", yaw),
                    String.format(Locale.ROOT, "Pitch: %.2f°", pitch)
            });
        }
    }

    private static void renderPanel(DrawContext context, String id, String[] lines) {
        MinecraftClient client = MinecraftClient.getInstance();
        Layout layout = layout(id);
        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.length * 12 + 8;

        context.getMatrices().push();
        context.getMatrices().translate(layout.x, layout.y, 0.0F);
        context.getMatrices().scale(layout.scale, layout.scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFF55DDEE);
        int y = 5;
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(client.textRenderer, lines[i], 7, y,
                    i == 0 ? 0xFFFFFFFF : 0xFFD8E6F5);
            y += 12;
        }
        context.getMatrices().pop();
    }

    public static boolean isInGarden() { return isGarden(); }

    private static boolean isGarden() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        ScoreboardObjective objective = client.world.getScoreboard()
                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return false;
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
            String clean = stripFormatting(line).toLowerCase(Locale.ROOT);
            if (clean.contains("garden")) return true;
        }
        return false;
    }

    private static String cropId(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (!CROPS.contains(id)) return null;

        if (id.equals("wheat") || id.equals("carrots") || id.equals("potatoes")) {
            if (state.contains(Properties.AGE_7) && state.get(Properties.AGE_7) < 7) return null;
        } else if (id.equals("beetroots") || id.equals("nether_wart")) {
            if (state.contains(Properties.AGE_3) && state.get(Properties.AGE_3) < 3) return null;
        } else if (id.equals("cocoa")) {
            if (state.contains(Properties.AGE_2) && state.get(Properties.AGE_2) < 2) return null;
        }
        return id;
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds % 60L);
    }

    private static void load() {
        if (!Files.isRegularFile(DATA_FILE)) return;
        java.util.Properties p = new java.util.Properties();
        try (InputStream input = Files.newInputStream(DATA_FILE)) {
            p.load(input);
            bpsEnabled = Boolean.parseBoolean(p.getProperty("bpsEnabled", "true"));
            trackerEnabled = Boolean.parseBoolean(p.getProperty("trackerEnabled", "true"));
            pitchEnabled = Boolean.parseBoolean(p.getProperty("pitchEnabled", "false"));
            totalHarvests = Long.parseLong(p.getProperty("totalHarvests", "0"));
            sessionStartedAt = System.currentTimeMillis();
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("crop.")) CROP_BREAKS.put(key.substring(5),
                        Long.parseLong(p.getProperty(key, "0")));
            }
            for (String id : new String[]{"bps", "tracker", "pitch"}) {
                Layout value = layout(id);
                value.x = Integer.parseInt(p.getProperty(id + ".x", Integer.toString(value.x)));
                value.y = Integer.parseInt(p.getProperty(id + ".y", Integer.toString(value.y)));
                value.scale = Math.max(0.5F, Math.min(3.0F,
                        Float.parseFloat(p.getProperty(id + ".scale", Float.toString(value.scale)))));
            }
        } catch (IOException | NumberFormatException ignored) {
            createDefaultLayouts();
            totalHarvests = 0L;
            CROP_BREAKS.clear();
        }
    }

    private static void save() {
        java.util.Properties p = new java.util.Properties();
        p.setProperty("bpsEnabled", Boolean.toString(bpsEnabled));
        p.setProperty("trackerEnabled", Boolean.toString(trackerEnabled));
        p.setProperty("pitchEnabled", Boolean.toString(pitchEnabled));
        p.setProperty("totalHarvests", Long.toString(totalHarvests));
        for (Map.Entry<String, Long> entry : CROP_BREAKS.entrySet()) {
            p.setProperty("crop." + entry.getKey(), Long.toString(entry.getValue()));
        }
        for (String id : new String[]{"bps", "tracker", "pitch"}) {
            Layout value = layout(id);
            p.setProperty(id + ".x", Integer.toString(value.x));
            p.setProperty(id + ".y", Integer.toString(value.y));
            p.setProperty(id + ".scale", Float.toString(value.scale));
        }
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(DATA_FILE)) {
                p.store(output, "Skyblock QoL Garden Settings");
            }
        } catch (IOException ignored) {
            // Keep settings active for this session.
        }
        lastSaveAt = System.currentTimeMillis();
    }
}