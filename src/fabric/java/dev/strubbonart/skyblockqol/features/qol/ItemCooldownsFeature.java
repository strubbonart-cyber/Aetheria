package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Item ability cooldowns and defensive proc timers with configurable HUD position. */
public final class ItemCooldownsFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-item-cooldowns.properties");
    private static final Map<String, Long> COOLDOWN_DURATIONS = new LinkedHashMap<>();
    private static final Map<String, Long> ABILITY_DURATIONS = new LinkedHashMap<>();
    private static final Map<String, Long> INVINCIBILITY_DURATIONS = new LinkedHashMap<>();
    private static final Map<String, Long> ACTIVE_TIMERS = new LinkedHashMap<>();
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    private static final class Trigger {
        final Pattern pattern;
        final String[] itemIds;
        final boolean cooldown;
        final boolean ability;
        final boolean invincibility;

        Trigger(String expression, boolean regex, boolean cooldown, boolean ability,
                boolean invincibility, String... ids) {
            pattern = Pattern.compile(expression, regex ? Pattern.CASE_INSENSITIVE : Pattern.LITERAL);
            itemIds = ids;
            this.cooldown = cooldown;
            this.ability = ability;
            this.invincibility = invincibility;
        }
    }

    private static final List<Trigger> CHAT_TRIGGERS = new ArrayList<>();
    private static final List<Trigger> ACTIONBAR_TRIGGERS = new ArrayList<>();
    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean showWhenEmpty;
    private static int x = 8;
    private static int y = 230;
    private static float scale = 1.0F;

    private ItemCooldownsFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        addFallbackData();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String raw = strip(message.getString());
            processTriggers(raw, overlay ? ACTIONBAR_TRIGGERS : CHAT_TRIGGERS);
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("itemcooldowns")
                        .executes(context -> {
                            context.getSource().sendFeedback(Text.literal(status()));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            resetTimers();
                            context.getSource().sendFeedback(Text.literal("Item cooldown timers reset."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("show").executes(context -> {
                            setEnabled(true);
                            context.getSource().sendFeedback(Text.literal("Item cooldown HUD enabled."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("hide").executes(context -> {
                            setEnabled(false);
                            context.getSource().sendFeedback(Text.literal("Item cooldown HUD disabled."));
                            return 1;
                        }))
                ));
    }

    private static void addFallbackData() {
        addCooldown("GYROKINETIC_WAND", 30_000L, "Gyrokinetic Wand");
        addCooldown("ICE_SPRAY_WAND", 5_000L, "Ice Spray Wand");
        addCooldown("FIRE_VEIL_WAND", 1_000L, "Fire Veil Wand");
        addCooldown("ATOMSPLIT_KATANA", 5_000L, "Atomsplit Katana");
        addCooldown("MIDAS_STAFF", 3_000L, "Midas Staff");
        addCooldown("STARRED_MIDAS_STAFF", 3_000L, "Midas Staff");
        addCooldown("RAGNAROK_AXE", 120_000L, "Ragnarok Axe");
        addCooldown("BONZO_MASK", 360_000L, "Bonzo's Mask");
        addCooldown("STARRED_BONZO_MASK", 360_000L, "Bonzo's Mask");
        addCooldown("SPIRIT_MASK", 30_000L, "Spirit Mask");
        addCooldown("STARRED_SPIRIT_MASK", 30_000L, "Spirit Mask");

        ABILITY_DURATIONS.put("FIRE_VEIL_WAND", 5_000L);
        for (String id : new String[]{"BONZO_MASK", "STARRED_BONZO_MASK"}) {
            INVINCIBILITY_DURATIONS.put(id, 5_000L);
        }
        for (String id : new String[]{"SPIRIT_MASK", "STARRED_SPIRIT_MASK"}) {
            INVINCIBILITY_DURATIONS.put(id, 5_000L);
        }

        CHAT_TRIGGERS.add(new Trigger("Bonzo's Mask saved your life", false, true, false, true,
                "BONZO_MASK", "STARRED_BONZO_MASK"));
        CHAT_TRIGGERS.add(new Trigger("Spirit Mask saved you from death", false, true, false, true,
                "SPIRIT_MASK", "STARRED_SPIRIT_MASK"));
        ACTIONBAR_TRIGGERS.add(new Trigger("Gravity Storm", false, true, false, false,
                "GYROKINETIC_WAND"));
        ACTIONBAR_TRIGGERS.add(new Trigger("BLIZZARD!|Ice Spray", true, true, false, false,
                "ICE_SPRAY_WAND"));
        ACTIONBAR_TRIGGERS.add(new Trigger("Fire Veil", false, true, true, false,
                "FIRE_VEIL_WAND"));
        ACTIONBAR_TRIGGERS.add(new Trigger("Atomsplit", false, true, false, false,
                "ATOMSPLIT_KATANA"));
        ACTIONBAR_TRIGGERS.add(new Trigger("Molten Wave", false, true, false, false,
                "MIDAS_STAFF", "STARRED_MIDAS_STAFF"));
    }

    private static void addCooldown(String id, long duration, String label) {
        COOLDOWN_DURATIONS.put(id, duration);
        LABELS.put(id, label);
    }

    private static void processTriggers(String message, List<Trigger> triggers) {
        if (message == null || message.isBlank()) return;
        for (Trigger trigger : triggers) {
            if (!trigger.pattern.matcher(message).find()) continue;
            for (String id : trigger.itemIds) {
                if (trigger.cooldown) mark("C", id, COOLDOWN_DURATIONS.getOrDefault(id, 0L));
                if (trigger.ability) mark("A", id, ABILITY_DURATIONS.getOrDefault(id, 0L));
                if (trigger.invincibility) mark("I", id, INVINCIBILITY_DURATIONS.getOrDefault(id, 0L));
            }
        }
    }

    private static void mark(String kind, String id, long duration) {
        if (duration > 0L) ACTIVE_TIMERS.put(kind + ":" + id, System.currentTimeMillis() + duration);
    }

    public static boolean isOnCooldown(String itemId) {
        Long end = ACTIVE_TIMERS.get("C:" + normalize(itemId));
        return end != null && end > System.currentTimeMillis();
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
    public static boolean isShowWhenEmpty() { return showWhenEmpty; }
    public static void setShowWhenEmpty(boolean value) { showWhenEmpty = value; save(); }
    public static int getX() { return x; }
    public static int getY() { return y; }
    public static float getScale() { return scale; }

    public static void updateLayout(int nx, int ny, float ns) {
        x = Math.max(0, Math.min(5000, nx));
        y = Math.max(0, Math.min(5000, ny));
        scale = Math.max(0.5F, Math.min(3.0F, ns));
    }

    public static void saveLayout() { save(); }

    public static void resetLayout() {
        x = 8;
        y = 230;
        scale = 1.0F;
        save();
    }

    public static void resetTimers() { ACTIVE_TIMERS.clear(); }

    private static void render(DrawContext context) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        long now = System.currentTimeMillis();
        ACTIVE_TIMERS.entrySet().removeIf(entry -> entry.getValue() <= now);
        List<String> lines = new ArrayList<>();
        lines.add("Item Cooldowns");
        ACTIVE_TIMERS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length != 2) return;
            String kind = switch (parts[0]) {
                case "A" -> "Ability";
                case "I" -> "Invulnerability";
                default -> "Cooldown";
            };
            String label = LABELS.getOrDefault(parts[1], prettyName(parts[1]));
            double seconds = Math.max(0L, entry.getValue() - now) / 1000.0D;
            lines.add(label + " (" + kind + "): " + String.format(Locale.ROOT, "%.1fs", seconds));
        });

        if (lines.size() == 1 && !showWhenEmpty) return;
        if (lines.size() == 1) lines.add("No active cooldowns");

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.size() * 12 + 8;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB9000000);
        context.fill(0, 0, width, 2, 0xFFCC8866);
        int drawY = 5;
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, lines.get(i), 7, drawY,
                    i == 0 ? 0xFFFFCC88 : 0xFFFFFFFF);
            drawY += 12;
        }
        context.getMatrices().pop();
    }

    private static String status() {
        long now = System.currentTimeMillis();
        ACTIVE_TIMERS.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (ACTIVE_TIMERS.isEmpty()) return "No active item cooldowns.";
        StringBuilder result = new StringBuilder("Active item timers: ");
        for (Map.Entry<String, Long> entry : ACTIVE_TIMERS.entrySet()) {
            if (result.length() > 22) result.append(" | ");
            String[] parts = entry.getKey().split(":", 2);
            String id = parts.length == 2 ? parts[1] : entry.getKey();
            result.append(LABELS.getOrDefault(id, prettyName(id))).append(' ')
                    .append(String.format(Locale.ROOT, "%.1fs",
                            Math.max(0L, entry.getValue() - now) / 1000.0D));
        }
        return result.toString();
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }

    private static String prettyName(String id) {
        StringBuilder result = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            showWhenEmpty = Boolean.parseBoolean(p.getProperty("showWhenEmpty", "false"));
            x = Integer.parseInt(p.getProperty("x", "8"));
            y = Integer.parseInt(p.getProperty("y", "230"));
            scale = Math.max(0.5F, Math.min(3.0F,
                    Float.parseFloat(p.getProperty("scale", "1.0"))));
        } catch (IOException | NumberFormatException ignored) {
            enabled = true;
            showWhenEmpty = false;
            x = 8;
            y = 230;
            scale = 1.0F;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("showWhenEmpty", Boolean.toString(showWhenEmpty));
        p.setProperty("x", Integer.toString(x));
        p.setProperty("y", Integer.toString(y));
        p.setProperty("scale", Float.toString(scale));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Item Cooldown HUD");
            }
        } catch (IOException ignored) {}
    }
}
