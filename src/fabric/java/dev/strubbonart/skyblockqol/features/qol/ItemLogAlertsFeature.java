package dev.strubbonart.skyblockqol.features.qol;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;

/** Configurable on-screen alerts for selected SkyBlock item pickups. */
public final class ItemLogAlertsFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-item-log-alerts.properties");
    private static final long ALERT_DURATION_MS = 2_000L;
    private static final Map<String, String> ALERTS = new LinkedHashMap<>();
    private static final Set<String> FIRST_TIME_ALERTED = new HashSet<>();

    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean firstTimeOnly = true;
    private static boolean useDisplayName = true;
    private static boolean playSound = true;
    private static String activeMessage = "";
    private static long activeUntil;

    private ItemLogAlertsFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) FIRST_TIME_ALERTED.clear();
            if (System.currentTimeMillis() > activeUntil) activeMessage = "";
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("itemlogalert")
                    .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                    .executes(context -> {
                                        addAlert(StringArgumentType.getString(context, "id"), "");
                                        context.getSource().sendFeedback(Text.literal("Item alert added."));
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                addAlert(StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "text"));
                                                context.getSource().sendFeedback(Text.literal("Item alert added with custom text."));
                                                return 1;
                                            }))))
                    .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                    .executes(context -> {
                                        String id = normalizeId(StringArgumentType.getString(context, "id"));
                                        boolean removed = ALERTS.remove(id) != null;
                                        save();
                                        context.getSource().sendFeedback(Text.literal(
                                                removed ? "Removed alert for " + id + "." : "No alert exists for " + id + "."));
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("list").executes(context -> {
                        if (ALERTS.isEmpty()) {
                            context.getSource().sendFeedback(Text.literal("No item alerts configured. Use /itemlogalert add <id> [text]."));
                        } else {
                            for (Map.Entry<String, String> entry : ALERTS.entrySet()) {
                                context.getSource().sendFeedback(Text.literal(entry.getKey()
                                        + (entry.getValue().isBlank() ? "" : " -> " + entry.getValue())));
                            }
                        }
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("clear").executes(context -> {
                        int count = ALERTS.size();
                        ALERTS.clear();
                        save();
                        context.getSource().sendFeedback(Text.literal("Cleared " + count + " item alerts."));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("mode").executes(context -> {
                        setFirstTimeOnly(!firstTimeOnly);
                        context.getSource().sendFeedback(Text.literal("Item alert mode: "
                                + (firstTimeOnly ? "First Time Only" : "Always") + "."));
                        return 1;
                    }))
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal(
                                "Use /itemlogalert <add|remove|list|clear|mode>."));
                        return 1;
                    }));

            dispatcher.register(ClientCommandManager.literal("ila")
                    .redirect(dispatcher.getRoot().getChild("itemlogalert")));
            dispatcher.register(ClientCommandManager.literal("itemalert")
                    .redirect(dispatcher.getRoot().getChild("itemlogalert")));
        });
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
    public static boolean isFirstTimeOnly() { return firstTimeOnly; }
    public static void setFirstTimeOnly(boolean value) { firstTimeOnly = value; save(); }
    public static boolean isUseDisplayName() { return useDisplayName; }
    public static void setUseDisplayName(boolean value) { useDisplayName = value; save(); }
    public static boolean isPlaySound() { return playSound; }
    public static void setPlaySound(boolean value) { playSound = value; save(); }
    public static int getAlertCount() { return ALERTS.size(); }

    public static void addAlert(String id, String customText) {
        String normalized = normalizeId(id);
        if (normalized.isBlank()) return;
        ALERTS.put(normalized, customText == null ? "" : customText.trim());
        save();
    }

    public static void onItemChange(String internalId, String displayName, int delta) {
        if (!enabled || delta <= 0 || internalId == null) return;
        String id = normalizeId(internalId);
        if (!ALERTS.containsKey(id)) return;
        if (firstTimeOnly && !FIRST_TIME_ALERTED.add(id)) return;

        String custom = ALERTS.getOrDefault(id, "");
        String message = useDisplayName || custom.isBlank()
                ? (displayName == null || displayName.isBlank() ? prettyName(id) : displayName)
                : custom.replace('&', '§');
        activeMessage = message;
        activeUntil = System.currentTimeMillis() + ALERT_DURATION_MS;

        MinecraftClient client = MinecraftClient.getInstance();
        if (playSound && client.player != null) {
            client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.8F);
        }
    }

    private static void render(DrawContext context) {
        if (!enabled || activeMessage.isBlank() || System.currentTimeMillis() > activeUntil) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        String measured = activeMessage.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        int textWidth = client.textRenderer.getWidth(measured);
        float scale = Math.min(3.0F, Math.max(1.0F, (width - 24.0F) / Math.max(1, textWidth)));
        int scaledWidth = (int) (textWidth * scale);
        int y = height / 2 - 14;

        context.getMatrices().push();
        context.getMatrices().translate(width / 2.0F, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        var text = Text.literal(activeMessage.replaceAll("§[0-9A-FK-ORa-fk-or]", ""))
                .styled(style -> style.withColor(Formatting.RED).withBold(true));
        context.drawCenteredTextWithShadow(client.textRenderer, text, 0, 0, 0xFFFF5555);
        context.getMatrices().pop();
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String prettyName(String id) {
        String clean = id;
        int split = clean.lastIndexOf(':');
        if (split >= 0) clean = clean.substring(split + 1);
        String[] words = clean.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            firstTimeOnly = Boolean.parseBoolean(properties.getProperty("firstTimeOnly", "true"));
            useDisplayName = Boolean.parseBoolean(properties.getProperty("useDisplayName", "true"));
            playSound = Boolean.parseBoolean(properties.getProperty("playSound", "true"));
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("alert.")) {
                    ALERTS.put(normalizeId(key.substring(6)), properties.getProperty(key, ""));
                }
            }
        } catch (IOException ignored) {
            ALERTS.clear();
            enabled = true;
            firstTimeOnly = true;
            useDisplayName = true;
            playSound = true;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("firstTimeOnly", Boolean.toString(firstTimeOnly));
        properties.setProperty("useDisplayName", Boolean.toString(useDisplayName));
        properties.setProperty("playSound", Boolean.toString(playSound));
        for (Map.Entry<String, String> entry : ALERTS.entrySet()) {
            properties.setProperty("alert." + entry.getKey(), entry.getValue());
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL Item Log Alerts");
            }
        } catch (IOException ignored) {
            // Keep settings active in memory if persistence is unavailable.
        }
    }
}
