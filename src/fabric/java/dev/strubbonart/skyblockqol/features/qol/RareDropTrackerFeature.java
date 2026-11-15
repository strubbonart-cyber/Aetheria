package dev.strubbonart.skyblockqol.features.qol;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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

/** Manual SkyBlock-ID watch list for rare drops. */
public final class RareDropTrackerFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-rare-drops.properties");
    private static final Map<String, TrackedItem> TRACKED = new LinkedHashMap<>();
    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean chatAlerts = true;
    private static int x = 8;
    private static int y = 400;
    private static float scale = 1.0F;

    private static final class TrackedItem {
        String name;
        int count;
        int goal;
        TrackedItem(String name, int count, int goal) {
            this.name = name; this.count = count; this.goal = goal;
        }
    }

    private RareDropTrackerFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("rdt")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("skyblockId", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String id = normalizeId(StringArgumentType.getString(context, "skyblockId"));
                                            if (id.isBlank()) return 0;
                                            if (TRACKED.containsKey(id)) {
                                                context.getSource().sendFeedback(Text.literal("Already tracking " + id + "."));
                                            } else {
                                                TRACKED.put(id, new TrackedItem(prettyName(id), 0, 0));
                                                save();
                                                context.getSource().sendFeedback(Text.literal("Tracking " + id + "."));
                                            }
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("skyblockId", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String id = normalizeId(StringArgumentType.getString(context, "skyblockId"));
                                            boolean removed = TRACKED.remove(id) != null;
                                            save();
                                            context.getSource().sendFeedback(Text.literal(removed ? "Stopped tracking " + id + "." : "Not tracking " + id + "."));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("goal")
                                .then(ClientCommandManager.argument("skyblockId", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    String id = normalizeId(StringArgumentType.getString(context, "skyblockId"));
                                                    TrackedItem item = TRACKED.get(id);
                                                    if (item == null) {
                                                        context.getSource().sendFeedback(Text.literal("Add the item first with /rdt add <ID>."));
                                                    } else {
                                                        item.goal = IntegerArgumentType.getInteger(context, "amount");
                                                        save();
                                                        context.getSource().sendFeedback(Text.literal(item.name + " goal set to " + item.goal + "."));
                                                    }
                                                    return 1;
                                                }))))
                        .then(ClientCommandManager.literal("list").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(status()));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("clear").executes(context -> {
                            TRACKED.clear(); save();
                            context.getSource().sendFeedback(Text.literal("Rare Drop watch list cleared."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            for (TrackedItem item : TRACKED.values()) item.count = 0;
                            save();
                            context.getSource().sendFeedback(Text.literal("Rare Drop counts reset."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("on").executes(context -> {
                            setEnabled(true);
                            context.getSource().sendFeedback(Text.literal("Rare Drop Tracker enabled."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("off").executes(context -> {
                            setEnabled(false);
                            context.getSource().sendFeedback(Text.literal("Rare Drop Tracker disabled."));
                            return 1;
                        }))
                ));
    }

    public static boolean isEnabled() { return enabled; }
    public static boolean isChatAlertsEnabled() { return chatAlerts; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
    public static void setChatAlertsEnabled(boolean value) { chatAlerts = value; save(); }
    public static int getX() { return x; }
    public static int getY() { return y; }
    public static float getScale() { return scale; }
    public static void updateLayout(int nx, int ny, float ns) {
        x = Math.max(0, Math.min(5000, nx));
        y = Math.max(0, Math.min(5000, ny));
        scale = Math.max(0.5F, Math.min(3.0F, ns));
    }
    public static void saveLayout() { save(); }
    public static void resetLayout() { x = 8; y = 400; scale = 1.0F; save(); }

    public static void onItemChange(String internalId, String displayName, int delta) {
        if (!enabled || delta <= 0 || internalId == null) return;
        TrackedItem item = TRACKED.get(normalizeId(internalId));
        if (item == null) return;
        item.count += delta;
        save();

        if (chatAlerts) {
            String progress = item.goal > 0 ? " (" + item.count + "/" + item.goal + ")" : " (" + item.count + ")";
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) client.player.sendMessage(Text.literal(
                    "§d§lRARE DROP! §r" + item.name + " x" + delta + progress), false);
        }
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.world == null) return;
        List<String> lines = new ArrayList<>();
        lines.add("Rare Drop Tracker");
        for (Map.Entry<String, TrackedItem> entry : TRACKED.entrySet()) {
            TrackedItem item = entry.getValue();
            if (lines.size() >= 8) break;
            String line = item.name + ": " + item.count;
            if (item.goal > 0) line += "/" + item.goal;
            lines.add(line);
        }
        if (lines.size() == 1) lines.add("Use /rdt add <SkyBlock ID>");

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.size() * 12 + 8;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFFCC66EE);
        int textY = 5;
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, lines.get(i), 7, textY,
                    i == 0 ? 0xFFFF88EE : 0xFFFFFFFF);
            textY += 12;
        }
        matrices.pop();
    }

    private static String status() {
        if (TRACKED.isEmpty()) return "Not tracking any items. Add an ID with /rdt add <ID>.";
        StringBuilder result = new StringBuilder("Rare Drop Tracker: ");
        for (Map.Entry<String, TrackedItem> e : TRACKED.entrySet()) {
            if (result.length() > 20) result.append(" | ");
            result.append(e.getValue().name).append(" ").append(e.getValue().count);
            if (e.getValue().goal > 0) result.append("/").append(e.getValue().goal);
        }
        return result.toString();
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String prettyName(String id) {
        String clean = id;
        int split = clean.lastIndexOf(':');
        if (split >= 0) clean = clean.substring(split + 1);
        String[] words = clean.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            chatAlerts = Boolean.parseBoolean(p.getProperty("chatAlerts", "true"));
            x = Integer.parseInt(p.getProperty("x", "8"));
            y = Integer.parseInt(p.getProperty("y", "400"));
            scale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(p.getProperty("scale", "1.0"))));
            int count = Integer.parseInt(p.getProperty("count", "0"));
            for (int i = 0; i < count; i++) {
                String prefix = "item." + i + ".";
                String id = p.getProperty(prefix + "id", "");
                if (id.isBlank()) continue;
                TRACKED.put(id, new TrackedItem(p.getProperty(prefix + "name", prettyName(id)),
                        Integer.parseInt(p.getProperty(prefix + "count", "0")),
                        Integer.parseInt(p.getProperty(prefix + "goal", "0"))));
            }
        } catch (IOException | NumberFormatException ignored) {
            TRACKED.clear();
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("chatAlerts", Boolean.toString(chatAlerts));
        p.setProperty("x", Integer.toString(x));
        p.setProperty("y", Integer.toString(y));
        p.setProperty("scale", Float.toString(scale));
        p.setProperty("count", Integer.toString(TRACKED.size()));
        int index = 0;
        for (Map.Entry<String, TrackedItem> entry : TRACKED.entrySet()) {
            String prefix = "item." + index++ + ".";
            p.setProperty(prefix + "id", entry.getKey());
            p.setProperty(prefix + "name", entry.getValue().name);
            p.setProperty(prefix + "count", Integer.toString(entry.getValue().count));
            p.setProperty(prefix + "goal", Integer.toString(entry.getValue().goal));
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Rare Drop Tracker");
            }
        } catch (IOException ignored) {}
    }
}