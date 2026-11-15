package dev.strubbonart.skyblockqol.features.qol;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks Hypixel Kill Combo milestones and shows a configurable HUD. */
public final class KillComboFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-kill-combo.properties");
    private static final Pattern COMBO = Pattern.compile("\\\\+(\\\\d+) Kill Combo");
    private static final Pattern EXPIRED = Pattern.compile("reached a (\\\\d+) Kill Combo");
    private static final Pattern VALUE = Pattern.compile("\\\\+(\\\\d+(?:\\\\.\\\\d+)?)");

    private static final Map<String, ComboStat> STATS = new LinkedHashMap<>();
    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean active;
    private static int currentCombo;
    private static int highestCombo;
    private static int x = 8;
    private static int y = 280;
    private static float scale = 1.0F;

    public record ComboStat(int value, int milestone) {}

    private KillComboFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("killcombo")
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            reset();
                            context.getSource().sendFeedback(Text.literal("Kill Combo stats reset."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal("Kill Combo: "
                                    + currentCombo + " current, " + highestCombo + " best."));
                            return 1;
                        }))));
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
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
        x = 8; y = 280; scale = 1.0F; save();
    }

    private static void reset() {
        currentCombo = 0;
        highestCombo = 0;
        active = false;
        STATS.clear();
        save();
    }

    private static void onMessage(String text) {
        if (!enabled || text == null || text.isBlank()) return;
        String clean = strip(text);
        if (clean.contains("Your Kill Combo has expired!")) {
            Matcher expired = EXPIRED.matcher(clean);
            if (expired.find()) {
                try { highestCombo = Math.max(highestCombo, Integer.parseInt(expired.group(1))); }
                catch (NumberFormatException ignored) {}
            }
            currentCombo = 0;
            active = false;
            STATS.clear();
            save();
            return;
        }

        Matcher comboMatcher = COMBO.matcher(clean);
        if (!comboMatcher.find()) return;
        int combo;
        try { combo = Integer.parseInt(comboMatcher.group(1)); }
        catch (NumberFormatException ignored) { return; }

        if (!active) STATS.clear();
        active = true;
        currentCombo = combo;
        highestCombo = Math.max(highestCombo, combo);

        if (combo > 30) { save(); return; }
        int suffix = clean.indexOf(" Kill Combo ");
        if (suffix < 0) { save(); return; }
        String bonus = clean.substring(suffix + " Kill Combo ".length()).trim();
        Matcher value = VALUE.matcher(bonus);
        if (!value.find()) { save(); return; }

        int amount;
        try { amount = (int) Double.parseDouble(value.group(1)); }
        catch (NumberFormatException ignored) { return; }
        String stat = bonus.substring(value.end()).replaceFirst("^%?\\\\s*", "").trim();
        if (!stat.isEmpty()) {
            ComboStat old = STATS.get(stat);
            STATS.put(stat, old == null ? new ComboStat(amount, combo)
                    : new ComboStat(old.value() + amount, Math.max(old.milestone(), combo)));
        }
        save();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.world == null) return;
        if (!active && highestCombo <= 0) return;

        String[] lines = new String[2 + STATS.size()];
        lines[0] = "Kill Combo Tracker";
        lines[1] = "Current: " + (active ? "+" + currentCombo : "(expired)")
                + " | Highest: " + highestCombo;
        int i = 2;
        for (Map.Entry<String, ComboStat> entry : STATS.entrySet()) {
            ComboStat stat = entry.getValue();
            String label = entry.getKey();
            String value = "+" + stat.value() + (label.endsWith("%") ? " " : " ") + label;
            if (isMaxMilestone(label, currentCombo)) value += " MAX";
            if (i < lines.length) lines[i++] = value;
        }

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.length * 12 + 8;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFFFFB84D);
        int textY = 5;
        for (int line = 0; line < lines.length; line++) {
            int color = line == 0 ? 0xFFFFD477 : 0xFFFFFFFF;
            context.drawTextWithShadow(client.textRenderer, lines[line], 7, textY, color);
            textY += 12;
        }
        matrices.pop();
    }

    private static boolean isMaxMilestone(String stat, int combo) {
        return switch (stat) {
            case "✯ Magic Find" -> combo >= 25;
            case "coins per kill" -> combo >= 30;
            case "☯ Combat Wisdom" -> combo >= 20;
            default -> false;
        };
    }

    private static String strip(String value) {
        return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            highestCombo = Math.max(0, Integer.parseInt(p.getProperty("highestCombo", "0")));
            x = Integer.parseInt(p.getProperty("x", "8"));
            y = Integer.parseInt(p.getProperty("y", "280"));
            scale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(p.getProperty("scale", "1.0"))));
        } catch (IOException | NumberFormatException ignored) {
            highestCombo = 0;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("highestCombo", Integer.toString(highestCombo));
        p.setProperty("x", Integer.toString(x));
        p.setProperty("y", Integer.toString(y));
        p.setProperty("scale", Float.toString(scale));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Kill Combo");
            }
        } catch (IOException ignored) {}
    }
}