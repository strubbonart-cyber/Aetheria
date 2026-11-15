package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.Resource;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

/** Seven-style connected-texture backgrounds for SkyBlock generic containers. */
public final class BetterContainersFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-better-containers.properties");
    private static final int STYLE_COUNT = 7;
    private static final Identifier DYNAMIC_TEXTURE =
            Identifier.of("skyblockqol", "dynamic/better_containers");

    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean watermarkEnabled = true;
    private static int style;
    private static int watermarkColor = 0xFFFF6666;

    private static int lastHandlerIdentity = -1;
    private static int lastStyle = -1;
    private static boolean dynamicReady;

    private static NativeImage baseImage;
    private static NativeImage slotImage;

    private BetterContainersFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        invalidate();
        save();
    }

    public static int getStyle() { return style; }
    public static void cycleStyle() {
        style = (style + 1) % STYLE_COUNT;
        invalidateImages();
        invalidate();
        save();
    }

    public static boolean isWatermarkEnabled() { return watermarkEnabled; }
    public static void setWatermarkEnabled(boolean value) {
        watermarkEnabled = value;
        save();
    }

    public static void cycleWatermarkColor() {
        int[] colors = {
                0xFFFF6666, 0xFFFFD75E, 0xFF55DDAA, 0xFF55BBFF,
                0xFFCC88FF, 0xFFFFFFFF
        };
        int next = 0;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == watermarkColor) {
                next = (i + 1) % colors.length;
                break;
            }
        }
        watermarkColor = colors[next];
        save();
    }

    /** Returns the replacement background id when a chest contains blank filler panes. */
    public static Identifier getBackgroundTexture(GenericContainerScreen screen, Identifier original) {
        if (!enabled || screen == null) return original;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return original;

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int containerSlots = handler.slots.size() - 36;
        if (containerSlots <= 0 || containerSlots % 9 != 0) return original;

        int identity = System.identityHashCode(handler);
        if (identity != lastHandlerIdentity || style != lastStyle) {
            lastHandlerIdentity = identity;
            lastStyle = style;
            regenerate(handler, containerSlots);
        }

        return dynamicReady ? DYNAMIC_TEXTURE : original;
    }

    /** Cancel rendering the black filler pane over the generated background. */
    public static boolean shouldRenderStack(ItemStack stack) {
        return !isBlankPane(stack);
    }

    /** Draw the original Aetheria top-right menu watermark. */
    public static void renderWatermark(DrawContext context, GenericContainerScreen screen) {
        if (!enabled || !watermarkEnabled || !dynamicReady || screen == null) return;

        int containerSlots = screen.getScreenHandler().slots.size() - 36;
        int rows = Math.max(1, containerSlots / 9);
        int guiX = (context.getScaledWindowWidth() - 176) / 2;
        int guiY = (context.getScaledWindowHeight() - (114 + rows * 18)) / 2;
        MinecraftClient client = MinecraftClient.getInstance();
        String label = "ASM";
        int drawX = guiX + 176 - client.textRenderer.getWidth(label) - 10;
        context.drawTextWithShadow(client.textRenderer, Text.literal(label),
                drawX, guiY + 6, watermarkColor);
    }

    private static boolean isBlankPane(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !stack.isOf(Blocks.BLACK_STAINED_GLASS_PANE.asItem())) return false;
        Text customName = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (customName == null) return false;
        return strip(customName.getString()).trim().isEmpty();
    }

    private static void regenerate(GenericContainerScreenHandler handler, int containerSlots) {
        dynamicReady = false;
        boolean[][] real = new boolean[9][containerSlots / 9];
        boolean hasBlank = false;
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            boolean blank = isBlankPane(stack);
            real[i % 9][i / 9] = !blank;
            hasBlank |= blank;
        }
        if (!hasBlank || !loadImages()) return;

        NativeImage output = null;
        try {
            int width = baseImage.getWidth();
            int height = baseImage.getHeight();
            int hMult = Math.max(1, width / 256);
            int vMult = Math.max(1, height / 256);

            output = new NativeImage(baseImage.getFormat(), width, height, false);
            output.copyFrom(baseImage);

            int rows = containerSlots / 9;
            for (int i = 0; i < containerSlots; i++) {
                int xi = i % 9;
                int yi = i / 9;
                if (!real[xi][yi]) continue;

                boolean up = yi > 0 && real[xi][yi - 1];
                boolean right = xi < 8 && real[xi + 1][yi];
                boolean down = yi < rows - 1 && real[xi][yi + 1];
                boolean left = xi > 0 && real[xi - 1][yi];
                boolean ul = xi > 0 && yi > 0 && real[xi - 1][yi - 1];
                boolean ur = xi < 8 && yi > 0 && real[xi + 1][yi - 1];
                boolean dr = xi < 8 && yi < rows - 1 && real[xi + 1][yi + 1];
                boolean dl = xi > 0 && yi < rows - 1 && real[xi - 1][yi + 1];

                int ctm = getCtmIndex(up, right, down, left, ul, ur, dr, dl);
                int sourceX = (ctm % 12) * 19 * hMult;
                int sourceY = (ctm / 12) * 19 * vMult;
                int destX = 7 * hMult + xi * 18 * hMult;
                int destY = 17 * vMult + yi * 18 * vMult;

                output.copyRect(slotImage, sourceX, sourceY, destX, destY,
                        18 * hMult, 18 * vMult, false, false);
            }

            NativeImageBackedTexture texture = new NativeImageBackedTexture(output);
            output = null; // Ownership transferred to the texture.
            MinecraftClient.getInstance().getTextureManager().registerTexture(DYNAMIC_TEXTURE, texture);
            dynamicReady = true;
        } catch (Exception error) {
            dynamicReady = false;
            System.err.println("[Skyblock QoL] Better Containers texture generation failed: " + error.getMessage());
        } finally {
            if (output != null) output.close();
        }
    }

    private static boolean loadImages() {
        if (baseImage != null && slotImage != null && lastStyle == style) return true;
        invalidateImages();
        MinecraftClient client = MinecraftClient.getInstance();
        int number = style + 1;
        Identifier bg = Identifier.of("aetheria",
                "dynamic_54/style" + number + "/dynamic_54.png");
        Identifier slots = Identifier.of("aetheria",
                "dynamic_54/style" + number + "/dynamic_54_slot_ctm.png");
        try {
            Optional<Resource> bgResource = client.getResourceManager().getResource(bg);
            Optional<Resource> slotResource = client.getResourceManager().getResource(slots);
            if (bgResource.isEmpty() || slotResource.isEmpty()) return false;
            try (InputStream in = bgResource.get().getInputStream()) {
                baseImage = NativeImage.read(in);
            }
            try (InputStream in = slotResource.get().getInputStream()) {
                slotImage = NativeImage.read(in);
            }
            return baseImage != null && slotImage != null
                    && baseImage.getWidth() >= 256 && baseImage.getHeight() >= 256
                    && slotImage.getWidth() >= 227 && slotImage.getHeight() >= 75;
        } catch (IOException error) {
            invalidateImages();
            System.err.println("[Skyblock QoL] Could not load Better Containers style " + number + ": " + error.getMessage());
            return false;
        }
    }

    private static void invalidateImages() {
        if (baseImage != null) baseImage.close();
        if (slotImage != null) slotImage.close();
        baseImage = null;
        slotImage = null;
        lastStyle = -1;
    }

    private static void invalidate() {
        dynamicReady = false;
        lastHandlerIdentity = -1;
    }

    private static int getCtmIndex(boolean up, boolean right, boolean down, boolean left,
                                   boolean ul, boolean ur, boolean dr, boolean dl) {
        if (up && right && down && left) {
            if (ul && ur && dr && dl) return 26;
            if (ul && ur && dr) return 33;
            if (ul && ur && dl) return 32;
            if (ul && ur) return 11;
            if (ul && dr && dl) return 44;
            if (ul && dr) return 35;
            if (ul && dl) return 10;
            if (ul) return 20;
            if (ur && dr && dl) return 45;
            if (ur && dr) return 23;
            if (ur && dl) return 34;
            if (ur) return 8;
            if (dr && dl) return 22;
            if (dr) return 9;
            if (dl) return 21;
            return 46;
        }
        if (up && right && down) return (!ur && !dr) ? 6 : !ur ? 28 : !dr ? 30 : 25;
        if (up && right && left) return (!ul && !ur) ? 18 : !ul ? 40 : !ur ? 42 : 38;
        if (up && right) return ur ? 37 : 16;
        if (up && down && left) return (!ul && !dl) ? 19 : !ul ? 43 : !dl ? 41 : 27;
        if (up && down) return 24;
        if (up && left) return ul ? 39 : 17;
        if (up) return 36;
        if (right && down && left) return (!dl && !dr) ? 7 : !dl ? 31 : !dr ? 29 : 14;
        if (right && down) return dr ? 13 : 4;
        if (right && left) return 2;
        if (right) return 1;
        if (down && left) return dl ? 15 : 5;
        if (down) return 12;
        if (left) return 3;
        return 0;
    }

    private static String strip(String text) {
        return text == null ? "" : text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            watermarkEnabled = Boolean.parseBoolean(p.getProperty("watermarkEnabled", "true"));
            style = Math.max(0, Math.min(STYLE_COUNT - 1,
                    Integer.parseInt(p.getProperty("style", "0"))));
            watermarkColor = (int) Long.parseLong(p.getProperty("watermarkColor", "4294932070"));
        } catch (IOException | NumberFormatException ignored) {
            enabled = true;
            watermarkEnabled = true;
            style = 0;
            watermarkColor = 0xFFFF6666;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("watermarkEnabled", Boolean.toString(watermarkEnabled));
        p.setProperty("style", Integer.toString(style));
        p.setProperty("watermarkColor", Long.toString(Integer.toUnsignedLong(watermarkColor)));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Better Containers");
            }
        } catch (IOException ignored) {
            // Keep current session settings active.
        }
    }
}
