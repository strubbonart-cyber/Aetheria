package dev.strubbonart.skyblockqol.features.pets;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fabric port of Aetheria's Current Pet HUD tracker.
 *
 * It learns the active pet from the server's Autopet message or the active
 * entry in the Pets menu. It does not equip pets or alter inventory clicks.
 */
public final class CurrentPetFeature {
    private static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-pet.properties");
    private static final Pattern LEVELLED_PET = Pattern.compile("^\\[Lvl\\s+(\\d+)]\\s+(.+)$");
    private static final Pattern AUTOPET = Pattern.compile(
            "Autopet equipped your \\[Lvl\\s+(\\d+)]\\s+(.+?)(?:\\s+✦)?!",
            Pattern.CASE_INSENSITIVE
    );

    private static boolean enabled = true;
    private static String petName = "";
    private static int petLevel;
    private static boolean initialized;
    private static long lastScan;

    private CurrentPetFeature() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static String getPetName() {
        return petName;
    }

    public static int getPetLevel() {
        return petLevel;
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            updateFromAutopetMessage(message);
            return true;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof HandledScreen<?> handled)) return;
            if (!strip(handled.getTitle().getString()).startsWith("Pets")) return;

            ScreenEvents.afterRender(screen).register((renderedScreen, context, mouseX, mouseY, tickDelta) -> {
                if (renderedScreen instanceof HandledScreen<?> petScreen) {
                    scanPetsMenu(petScreen);
                }
            });
        });

        HudRenderCallback.EVENT.register(CurrentPetFeature::renderHud);
    }

    private static void updateFromAutopetMessage(Text message) {
        String clean = strip(message.getString());
        Matcher matcher = AUTOPET.matcher(clean);
        if (!matcher.find()) return;
        try {
            setPet(Integer.parseInt(matcher.group(1)), matcher.group(2));
        } catch (NumberFormatException ignored) {
            // Ignore malformed server text.
        }
    }

    private static void scanPetsMenu(HandledScreen<?> screen) {
        long now = System.currentTimeMillis();
        if (now - lastScan < 500L) return;
        lastScan = now;

        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;
        int containerSlots = Math.max(0, handler.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore == null || lore.lines().stream().noneMatch(line ->
                    strip(line.getString()).contains("Click to despawn"))) continue;

            Matcher matcher = LEVELLED_PET.matcher(strip(stack.getName().getString()));
            if (!matcher.matches()) continue;
            try {
                setPet(Integer.parseInt(matcher.group(1)), matcher.group(2));
            } catch (NumberFormatException ignored) {
                // Ignore malformed menu item text.
            }
            return;
        }
    }

    private static void setPet(int level, String name) {
        String cleanName = strip(name).replace("✦", "").trim();
        if (level <= 0 || cleanName.isEmpty()) return;
        if (petLevel == level && petName.equals(cleanName)) return;
        petLevel = level;
        petName = cleanName;
        save();
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!enabled || petName.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;
        context.drawTextWithShadow(client.textRenderer,
                "Pet: [Lvl " + petLevel + "] " + petName, 8, 72, 0xFFFFAA);
    }

    private static String strip(String value) {
        return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static void load() {
        if (!Files.isRegularFile(SETTINGS_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(SETTINGS_FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            petName = properties.getProperty("petName", "");
            petLevel = Math.max(0, Integer.parseInt(properties.getProperty("petLevel", "0")));
        } catch (Exception ignored) {
            enabled = true;
            petName = "";
            petLevel = 0;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("petName", petName);
        properties.setProperty("petLevel", Integer.toString(petLevel));
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(SETTINGS_FILE)) {
                properties.store(output, "Skyblock QoL Fabric Current Pet");
            }
        } catch (IOException ignored) {
            // Pet tracking keeps working for this session if storage is unavailable.
        }
    }
}
