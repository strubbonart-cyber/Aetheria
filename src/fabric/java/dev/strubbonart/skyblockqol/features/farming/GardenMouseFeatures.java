package dev.strubbonart.skyblockqol.features.farming;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** Garden farming mouse sensitivity modifier and camera-look lock. */
public final class GardenMouseFeatures {
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-mouse.properties");
    private static final Set<String> FARMING_TOOLS = Set.of(
            "THEORETICAL_HOE_WHEAT_1", "THEORETICAL_HOE_WHEAT_2", "THEORETICAL_HOE_WHEAT_3",
            "THEORETICAL_HOE_CARROT_1", "THEORETICAL_HOE_CARROT_2", "THEORETICAL_HOE_CARROT_3",
            "THEORETICAL_HOE_POTATO_1", "THEORETICAL_HOE_POTATO_2", "THEORETICAL_HOE_POTATO_3",
            "THEORETICAL_HOE_CANE_1", "THEORETICAL_HOE_CANE_2", "THEORETICAL_HOE_CANE_3",
            "THEORETICAL_HOE_WARTS_1", "THEORETICAL_HOE_WARTS_2", "THEORETICAL_HOE_WARTS_3",
            "THEORETICAL_HOE_SUNFLOWER_1", "THEORETICAL_HOE_SUNFLOWER_2", "THEORETICAL_HOE_SUNFLOWER_3",
            "THEORETICAL_HOE_WILD_ROSE_1", "THEORETICAL_HOE_WILD_ROSE_2", "THEORETICAL_HOE_WILD_ROSE_3",
            "FUNGI_CUTTER", "FUNGI_CUTTER_2", "FUNGI_CUTTER_3",
            "COCO_CHOPPER", "COCO_CHOPPER_2", "COCO_CHOPPER_3",
            "CACTUS_KNIFE", "CACTUS_KNIFE_2", "CACTUS_KNIFE_3",
            "MELON_DICER", "MELON_DICER_2", "MELON_DICER_3",
            "PUMPKIN_DICER", "PUMPKIN_DICER_2", "PUMPKIN_DICER_3",
            "BASIC_GARDENING_HOE", "ADVANCED_GARDENING_HOE",
            "BASIC_GARDENING_AXE", "ADVANCED_GARDENING_AXE",
            "SQUEAKY_MOUSEMAT"
    );

    private static boolean initialized;
    private static boolean lockEnabled;
    private static boolean sensitivityEnabled;
    private static boolean requireGarden = true;
    private static int sensitivityPercent = 5;
    private static KeyBinding lockKey;

    private GardenMouseFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();

        lockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skyblockqol.lock_mouse",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.skyblockqol"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (lockKey.wasPressed()) toggleLock();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("lockyp")
                        .executes(context -> {
                            toggleLock();
                            context.getSource().sendFeedback(Text.literal(
                                    "Mouse camera lock " + (lockEnabled ? "enabled." : "disabled.")));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("on").executes(context -> {
                            setLockEnabled(true);
                            context.getSource().sendFeedback(Text.literal("Mouse camera lock enabled."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("off").executes(context -> {
                            setLockEnabled(false);
                            context.getSource().sendFeedback(Text.literal("Mouse camera lock disabled."));
                            return 1;
                        }))
                ));

        HudRenderCallback.EVENT.register((context, tickCounter) -> renderLockIndicator(context));
    }

    public static boolean isLockEnabled() { return lockEnabled; }
    public static boolean isSensitivityEnabled() { return sensitivityEnabled; }
    public static int getSensitivityPercent() { return sensitivityPercent; }
    public static boolean isRequireGarden() { return requireGarden; }

    public static void setLockEnabled(boolean enabled) {
        lockEnabled = enabled;
        save();
    }

    public static void toggleLock() {
        setLockEnabled(!lockEnabled);
    }

    public static void setSensitivityEnabled(boolean enabled) {
        sensitivityEnabled = enabled;
        save();
    }

    public static void adjustSensitivityPercent(int delta) {
        sensitivityPercent = Math.max(5, Math.min(100, sensitivityPercent + delta));
        save();
    }

    public static void setRequireGarden(boolean value) {
        requireGarden = value;
        save();
    }

    public static boolean isSensitivityActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!sensitivityEnabled || client.player == null || client.world == null
                || client.currentScreen != null) return false;
        if (requireGarden && !GardenFeatures.isInGarden()) return false;
        return isHoldingFarmingTool(client);
    }

    public static boolean shouldLockCamera() {
        MinecraftClient client = MinecraftClient.getInstance();
        return lockEnabled && client.player != null && client.world != null
                && client.currentScreen == null;
    }

    public static double sensitivityMultiplier() {
        return Math.max(0.05D, Math.min(1.0D, sensitivityPercent / 100.0D));
    }

    private static boolean isHoldingFarmingTool(MinecraftClient client) {
        if (client.player == null) return false;
        var stack = client.player.getMainHandStack();
        if (stack.isEmpty()) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return false;
        NbtCompound root = data.copyNbt();
        NbtCompound attributes = root.contains("ExtraAttributes") ? root.getCompound("ExtraAttributes") : root;
        String id = attributes.getString("id").trim().toUpperCase(Locale.ROOT);
        return FARMING_TOOLS.contains(id);
    }

    private static void renderLockIndicator(DrawContext context) {
        if (!shouldLockCamera()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int centerY = context.getScaledWindowHeight() / 2;
        context.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal("CAMERA LOCKED").styled(style -> style.withBold(true).withColor(0xFFFFCC55)),
                context.getScaledWindowWidth() / 2, centerY + 18, 0xFFFFCC55);
        context.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal("Use /lockyp or your configured key to unlock"),
                context.getScaledWindowWidth() / 2, centerY + 30, 0xFFE0E0E0);
    }

    private static void load() {
        if (!Files.isRegularFile(DATA_FILE)) return;
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(DATA_FILE)) {
            p.load(input);
            lockEnabled = Boolean.parseBoolean(p.getProperty("lockEnabled", "false"));
            sensitivityEnabled = Boolean.parseBoolean(p.getProperty("sensitivityEnabled", "false"));
            requireGarden = Boolean.parseBoolean(p.getProperty("requireGarden", "true"));
            sensitivityPercent = Math.max(5, Math.min(100,
                    Integer.parseInt(p.getProperty("sensitivityPercent", "5"))));
        } catch (IOException | NumberFormatException ignored) {
            lockEnabled = false;
            sensitivityEnabled = false;
            requireGarden = true;
            sensitivityPercent = 5;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("lockEnabled", Boolean.toString(lockEnabled));
        p.setProperty("sensitivityEnabled", Boolean.toString(sensitivityEnabled));
        p.setProperty("requireGarden", Boolean.toString(requireGarden));
        p.setProperty("sensitivityPercent", Integer.toString(sensitivityPercent));
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(DATA_FILE)) {
                p.store(output, "Skyblock QoL Garden Mouse Settings");
            }
        } catch (IOException ignored) {
            // Keep the current session settings active.
        }
    }
}