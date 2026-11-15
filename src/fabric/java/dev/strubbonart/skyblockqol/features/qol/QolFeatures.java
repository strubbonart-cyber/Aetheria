package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * First Fabric-native QoL slice.
 *
 * This class intentionally keeps the SkyBlock-specific behavior client-side:
 * it only reads the item's public custom-data component and never modifies
 * the stack or sends commands/packets.
 */
public final class QolFeatures {
    private static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-qol.properties");

    private static boolean showSkyblockId = true;
    private static boolean blockSelectionOverlay;
    private static boolean disableEnchantGlint;
    private static boolean showOwnNametag;
    private static boolean confirmDisconnect = true;
    private static boolean initialized;

    private QolFeatures() {}

    public static boolean isShowSkyblockIdEnabled() {
        return showSkyblockId;
    }

    public static void setShowSkyblockIdEnabled(boolean value) {
        showSkyblockId = value;
        save();
    }

    public static boolean isBlockSelectionOverlayEnabled() {
        return blockSelectionOverlay;
    }

    public static void setBlockSelectionOverlayEnabled(boolean value) {
        blockSelectionOverlay = value;
        save();
    }

    public static boolean isDisableEnchantGlintEnabled() {
        return disableEnchantGlint;
    }

    public static void setDisableEnchantGlintEnabled(boolean value) {
        disableEnchantGlint = value;
        save();
    }

    public static boolean isShowOwnNametagEnabled() {
        return showOwnNametag;
    }

    public static void setShowOwnNametagEnabled(boolean value) {
        showOwnNametag = value;
        save();
    }

    public static boolean isConfirmDisconnectEnabled() {
        return confirmDisconnect;
    }

    public static void setConfirmDisconnectEnabled(boolean value) {
        confirmDisconnect = value;
        save();
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if (!showSkyblockId || stack.isEmpty()) return;
            String id = getSkyblockId(stack);
            if (!id.isEmpty()) {
                lines.add(Text.literal("skyblock:" + id).formatted(Formatting.DARK_GRAY));
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(QolFeatures::renderBlockSelection);
    }

    private static String getSkyblockId(net.minecraft.item.ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return "";

        NbtCompound root = data.copyNbt();
        NbtCompound attributes = root.contains("ExtraAttributes")
                ? root.getCompound("ExtraAttributes") : root;
        return attributes.getString("id").trim();
    }

    private static void renderBlockSelection(WorldRenderContext context) {
        if (!blockSelectionOverlay || context.matrixStack() == null
                || context.consumers() == null || context.camera() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.options.hudHidden) return;
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit)
                || target.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        VoxelShape shape = client.world.getBlockState(pos).getOutlineShape(client.world, pos);
        if (shape.isEmpty()) return;

        Box box = shape.getBoundingBox().offset(pos).expand(0.002);
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, lines, box, 0.22F, 0.92F, 0.98F, 0.95F);

        VertexConsumer fill = context.consumers().getBuffer(RenderLayer.getDebugFilledBox());
        WorldRenderer.renderFilledBox(
                matrices, fill,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                0.22F, 0.92F, 0.98F, 0.10F
        );
        matrices.pop();
    }

    private static void load() {
        if (!Files.isRegularFile(SETTINGS_FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(SETTINGS_FILE)) {
            properties.load(input);
            showSkyblockId = Boolean.parseBoolean(properties.getProperty("showSkyblockId", "true"));
            blockSelectionOverlay = Boolean.parseBoolean(
                    properties.getProperty("blockSelectionOverlay", "false"));
            disableEnchantGlint = Boolean.parseBoolean(
                    properties.getProperty("disableEnchantGlint", "false"));
            showOwnNametag = Boolean.parseBoolean(
                    properties.getProperty("showOwnNametag", "false"));
            confirmDisconnect = Boolean.parseBoolean(
                    properties.getProperty("confirmDisconnect", "true"));
        } catch (IOException ignored) {
            showSkyblockId = true;
            blockSelectionOverlay = false;
            disableEnchantGlint = false;
            showOwnNametag = false;
            confirmDisconnect = true;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("showSkyblockId", Boolean.toString(showSkyblockId));
        properties.setProperty("blockSelectionOverlay", Boolean.toString(blockSelectionOverlay));
        properties.setProperty("disableEnchantGlint", Boolean.toString(disableEnchantGlint));
        properties.setProperty("showOwnNametag", Boolean.toString(showOwnNametag));
        properties.setProperty("confirmDisconnect", Boolean.toString(confirmDisconnect));
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(SETTINGS_FILE)) {
                properties.store(output, "Skyblock QoL Fabric Settings");
            }
        } catch (IOException ignored) {
            // Keep the in-memory setting active if the disk is unavailable.
        }
    }
}
