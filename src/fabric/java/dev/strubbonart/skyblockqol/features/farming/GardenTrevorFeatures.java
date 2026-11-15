package dev.strubbonart.skyblockqol.features.farming;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trevor quest area finder and session Pelt Tracker. Candidate positions and
 * message patterns are migrated from the original Aetheria implementation.
 */
public final class GardenTrevorFeatures {
    private static final Path DATA_FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-trevor.properties");
    private static final Pattern QUEST_START = Pattern.compile(
            "^\\[NPC\\] Trevor: You can find your (Trackable|Untrackable|Undetected|Endangered|Elusive) (Cow|Pig|Sheep|Rabbit|Chicken|Horse) near the (.+?)\\.?$");
    private static final Pattern QUEST_REWARD = Pattern.compile("^Killing the animal rewarded you (\\d+) Pelts?\\.?$");
    private static final Map<String, List<BlockPos>> SPOTS = createSpots();

    private static boolean initialized;
    private static boolean solverEnabled = true;
    private static boolean peltTrackerEnabled = true;
    private static boolean trapperWarpEnabled;
    private static boolean desertWarpEnabled;
    private static boolean detectAlertEnabled = true;
    private static boolean animalBeaconEnabled = true;
    private static long trapperWarpAvailableUntil;
    private static KeyBinding trapperWarpKey;
    private static KeyBinding desertWarpKey;
    private static int lastDetectedAnimalId = -1;
    private static String questRarity = "";
    private static String questAnimal = "";
    private static String questArea = "";
    private static List<BlockPos> activeSpots = List.of();
    private static int sessionPelts;
    private static long cooldownStartedAt;
    private static long lastActivityAt;
    private static long lastTickAt;
    private static long activeTimeMs;
    private static boolean peltVisible;
    private static int ticksSinceScan;
    private static Entity trackedAnimal;

    private static int hudX = 8;
    private static int hudY = 420;
    private static float hudScale = 1.0F;

    private GardenTrevorFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        trapperWarpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skyblockqol.trapper_warp",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                "category.skyblockqol"
        ));
        desertWarpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skyblockqol.desert_warp",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.skyblockqol"
        ));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register((context, tickCounter) -> renderHud(context));
        WorldRenderEvents.AFTER_ENTITIES.register(GardenTrevorFeatures::renderWorld);
    }

    public static boolean isSolverEnabled() { return solverEnabled; }
    public static boolean isPeltTrackerEnabled() { return peltTrackerEnabled; }
    public static boolean isTrapperWarpEnabled() { return trapperWarpEnabled; }
    public static void setTrapperWarpEnabled(boolean value) { trapperWarpEnabled = value; save(); }
    public static boolean isDesertWarpEnabled() { return desertWarpEnabled; }
    public static void setDesertWarpEnabled(boolean value) { desertWarpEnabled = value; save(); }
    public static boolean isDetectAlertEnabled() { return detectAlertEnabled; }
    public static void setDetectAlertEnabled(boolean value) { detectAlertEnabled = value; save(); }
    public static boolean isAnimalBeaconEnabled() { return animalBeaconEnabled; }
    public static void setAnimalBeaconEnabled(boolean value) { animalBeaconEnabled = value; save(); }

    public static void setSolverEnabled(boolean value) { solverEnabled = value; save(); }
    public static void setPeltTrackerEnabled(boolean value) { peltTrackerEnabled = value; save(); }

    public static int getLayoutX() { return hudX; }
    public static int getLayoutY() { return hudY; }
    public static float getLayoutScale() { return hudScale; }

    public static void updateLayout(int x, int y, float scale) {
        hudX = Math.max(0, Math.min(5000, x));
        hudY = Math.max(0, Math.min(5000, y));
        hudScale = Math.max(0.5F, Math.min(3.0F, scale));
    }

    public static void saveLayout() { save(); }

    public static void resetLayout() {
        hudX = 8;
        hudY = 420;
        hudScale = 1.0F;
        save();
    }

    public static void resetPeltTracker() {
        sessionPelts = 0;
        activeTimeMs = 0L;
        peltVisible = false;
        cooldownStartedAt = 0L;
        lastActivityAt = 0L;
        save();
    }

    private static void onMessage(String raw) {
        String message = stripFormatting(raw).trim();
        Matcher quest = QUEST_START.matcher(message);
        if (quest.matches()) {
            questRarity = quest.group(1).toLowerCase(Locale.ROOT);
            questAnimal = quest.group(2).toLowerCase(Locale.ROOT);
            questArea = quest.group(3).toLowerCase(Locale.ROOT).trim();
            activeSpots = SPOTS.getOrDefault(questArea, List.of());
            cooldownStartedAt = System.currentTimeMillis();
            lastActivityAt = cooldownStartedAt;
            peltVisible = true;
            trackedAnimal = null;
            lastDetectedAnimalId = -1;
            MinecraftClient client = MinecraftClient.getInstance();
            if (desertWarpEnabled && isDesertArea(questArea) && client.player != null) {
                client.player.sendMessage(Text.literal("§6[Garden] §ePress §b"
                        + desertWarpKey.getBoundKeyLocalizedText().getString()
                        + "§e to warp to the Desert for this hunt."), false);
            }
            return;
        }

        Matcher reward = QUEST_REWARD.matcher(message);
        if (reward.matches()) {
            try {
                sessionPelts += Integer.parseInt(reward.group(1));
            } catch (NumberFormatException ignored) {
                return;
            }
            lastActivityAt = System.currentTimeMillis();
            peltVisible = true;
            if (trapperWarpEnabled) {
                trapperWarpAvailableUntil = System.currentTimeMillis() + 5_000L;
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§6[Garden] §ePress §b"
                            + trapperWarpKey.getBoundKeyLocalizedText().getString()
                            + "§e within 5 seconds to warp to the Trapper."), false);
                }
            }
            questRarity = "";
            questAnimal = "";
            questArea = "";
            activeSpots = List.of();
            trackedAnimal = null;
            save();
        }
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (lastTickAt == 0L) lastTickAt = now;
        long delta = Math.max(0L, Math.min(1_000L, now - lastTickAt));
        lastTickAt = now;

        if (peltVisible && now - lastActivityAt <= 180_000L) {
            activeTimeMs += delta;
        } else if (peltVisible) {
            peltVisible = false;
            save();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (trapperWarpKey != null) {
            while (trapperWarpKey.wasPressed()) {
                if (trapperWarpEnabled && client.currentScreen == null && client.player != null
                        && client.getNetworkHandler() != null && now <= trapperWarpAvailableUntil) {
                    client.getNetworkHandler().sendChatCommand("warp trapper");
                    trapperWarpAvailableUntil = 0L;
                }
            }
        }
        if (desertWarpKey != null) {
            while (desertWarpKey.wasPressed()) {
                if (desertWarpEnabled && !questAnimal.isEmpty() && isDesertArea(questArea)
                        && client.currentScreen == null && client.player != null
                        && client.getNetworkHandler() != null && GardenFeatures.isInGarden()) {
                    client.getNetworkHandler().sendChatCommand("warp desert");
                }
            }
        }
        if (!solverEnabled || questAnimal.isEmpty() || client.world == null || client.player == null) {
            trackedAnimal = null;
            return;
        }
        if (++ticksSinceScan < 5) return;
        ticksSinceScan = 0;

        Box searchBox = client.player.getBoundingBox().expand(128.0);
        trackedAnimal = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : client.world.getOtherEntities(client.player, searchBox,
                candidate -> candidate instanceof ArmorStandEntity)) {
            String name = stripFormatting(entity.getName().getString()).toLowerCase(Locale.ROOT);
            if (!name.contains(questRarity) || !name.contains(questAnimal)) continue;
            double distance = client.player.squaredDistanceTo(entity);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                trackedAnimal = entity;
            }
        }
        if (trackedAnimal != null && detectAlertEnabled
                && trackedAnimal.getId() != lastDetectedAnimalId) {
            lastDetectedAnimalId = trackedAnimal.getId();
            client.player.sendMessage(Text.literal("§6[Garden] §eAnimal detected: §b"
                    + titleCase(questRarity) + " " + titleCase(questAnimal)), false);
        }
    }

    private static void renderHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!peltTrackerEnabled || !peltVisible || client.player == null || client.world == null) return;
        if (System.currentTimeMillis() - lastActivityAt > 180_000L) return;

        int peltsPerHour = activeTimeMs <= 0L ? 0
                : (int) Math.round(sessionPelts * 3_600_000.0 / activeTimeMs);
        List<String> lines = new ArrayList<>();
        lines.add("Pelt Tracker");
        lines.add("Pelts: " + sessionPelts);
        lines.add("Pelts/h: " + peltsPerHour);
        if (cooldownStartedAt > 0L) {
            long remaining = 20_000L - (System.currentTimeMillis() - cooldownStartedAt);
            lines.add(remaining > 0L
                    ? "Cooldown: " + (long) Math.ceil(remaining / 1000.0) + "s"
                    : "Cooldown: Ready!");
        }
        if (!questAnimal.isEmpty()) lines.add(questRarity + " " + questAnimal + " near " + questArea);

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.size() * 12 + 8;

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(hudX, hudY, 0.0F);
        matrices.scale(hudScale, hudScale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFFFFB84D);
        int y = 5;
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, lines.get(i), 7, y,
                    i == 0 ? 0xFFFFD477 : 0xFFFFFFFF);
            y += 12;
        }
        matrices.pop();
    }

    private static void renderWorld(WorldRenderContext context) {
        if (!solverEnabled || questAnimal.isEmpty() || context.matrixStack() == null
                || context.consumers() == null || context.camera() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        for (BlockPos pos : activeSpots) {
            Box box = new Box(pos).expand(0.15, 0.25, 0.15);
            WorldRenderer.drawBox(matrices, lines, box, 0.05F, 0.85F, 1.0F, 0.95F);
        }

        if (trackedAnimal != null && !trackedAnimal.isRemoved()) {
            Box animalBox = trackedAnimal.getBoundingBox().expand(0.2);
            WorldRenderer.drawBox(matrices, lines, animalBox, 1.0F, 0.75F, 0.15F, 1.0F);
            if (animalBeaconEnabled) {
                Box beam = new Box(trackedAnimal.getX() - 0.12D, trackedAnimal.getY(),
                        trackedAnimal.getZ() - 0.12D, trackedAnimal.getX() + 0.12D,
                        trackedAnimal.getY() + 40.0D, trackedAnimal.getZ() + 0.12D);
                WorldRenderer.drawBox(matrices, lines, beam, 1.0F, 0.72F, 0.12F, 0.85F);
            }
        }
        matrices.pop();
    }

    private static Map<String, List<BlockPos>> createSpots() {
        Map<String, List<BlockPos>> spots = new HashMap<>();
        spots.put("desert settlement", List.of(
                new BlockPos(184, 77, -352), new BlockPos(139, 77, -375)));
        spots.put("oasis", List.of(
                new BlockPos(104, 65, -473), new BlockPos(116, 65, -416),
                new BlockPos(165, 77, -464)));
        spots.put("mushroom gorge", List.of(
                new BlockPos(220, 41, -578), new BlockPos(234, 54, -500),
                new BlockPos(187, 42, -520), new BlockPos(303, 51, -409),
                new BlockPos(189, 43, -443)));
        spots.put("overgrown mushroom cave", List.of(
                new BlockPos(247, 57, -421), new BlockPos(248, 58, -369)));
        return Map.copyOf(spots);
    }

    private static boolean isDesertArea(String value) {
        return "desert settlement".equals(value) || "oasis".equals(value);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder result = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void load() {
        if (!Files.isRegularFile(DATA_FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(DATA_FILE)) {
            p.load(in);
            solverEnabled = Boolean.parseBoolean(p.getProperty("solverEnabled", "true"));
            peltTrackerEnabled = Boolean.parseBoolean(p.getProperty("peltTrackerEnabled", "true"));
            trapperWarpEnabled = Boolean.parseBoolean(p.getProperty("trapperWarpEnabled", "false"));
            desertWarpEnabled = Boolean.parseBoolean(p.getProperty("desertWarpEnabled", "false"));
            detectAlertEnabled = Boolean.parseBoolean(p.getProperty("detectAlertEnabled", "true"));
            animalBeaconEnabled = Boolean.parseBoolean(p.getProperty("animalBeaconEnabled", "true"));
            hudX = Integer.parseInt(p.getProperty("hudX", "8"));
            hudY = Integer.parseInt(p.getProperty("hudY", "420"));
            hudScale = Math.max(0.5F, Math.min(3.0F,
                    Float.parseFloat(p.getProperty("hudScale", "1.0"))));
        } catch (IOException | NumberFormatException ignored) {
            solverEnabled = true;
            peltTrackerEnabled = true;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("solverEnabled", Boolean.toString(solverEnabled));
        p.setProperty("peltTrackerEnabled", Boolean.toString(peltTrackerEnabled));
        p.setProperty("trapperWarpEnabled", Boolean.toString(trapperWarpEnabled));
        p.setProperty("desertWarpEnabled", Boolean.toString(desertWarpEnabled));
        p.setProperty("detectAlertEnabled", Boolean.toString(detectAlertEnabled));
        p.setProperty("animalBeaconEnabled", Boolean.toString(animalBeaconEnabled));
        p.setProperty("hudX", Integer.toString(hudX));
        p.setProperty("hudY", Integer.toString(hudY));
        p.setProperty("hudScale", Float.toString(hudScale));
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(DATA_FILE)) {
                p.store(out, "Skyblock QoL Trevor Settings");
            }
        } catch (IOException ignored) {
            // Keep in-memory settings usable.
        }
    }
}