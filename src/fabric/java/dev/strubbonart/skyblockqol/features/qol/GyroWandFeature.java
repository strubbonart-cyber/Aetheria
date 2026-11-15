package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** World-space 8.5-block Gyrokinetic Wand area ring. */
public final class GyroWandFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-gyro.properties");
    private static final double RADIUS = 8.5D;
    private static final int STEPS = 64;
    private static boolean enabled = true;
    private static boolean initialized;

    private GyroWandFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        WorldRenderEvents.AFTER_ENTITIES.register(GyroWandFeature::render);
    }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    private static boolean isHoldingGyro(MinecraftClient client) {
        if (client.player == null) return false;
        var stack = client.player.getMainHandStack();
        if (stack.isEmpty()) return false;
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        NbtCompound root = customData.copyNbt();
        NbtCompound attributes = root.contains("ExtraAttributes")
                ? root.getCompound("ExtraAttributes") : root;
        return "GYROKINETIC_WAND".equals(attributes.getString("id").trim().toUpperCase(Locale.ROOT));
    }

    private static void render(WorldRenderContext context) {
        if (!enabled || context.matrixStack() == null || context.consumers() == null
                || context.camera() == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden
                || !isHoldingGyro(client)) return;

        HitResult hit = client.player.raycast(100.0D, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos target = blockHit.getBlockPos();
        Vec3d center = new Vec3d(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        boolean coolingDown = ItemCooldownsFeature.isOnCooldown("GYROKINETIC_WAND");
        float red = coolingDown ? 1.0F : 0.72F;
        float green = coolingDown ? 0.20F : 0.24F;
        float blue = coolingDown ? 0.20F : 1.0F;
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        MatrixStack.Entry matrix = matrices.peek();
        for (int i = 0; i < STEPS; i++) {
            double a0 = Math.PI * 2.0D * i / STEPS;
            double a1 = Math.PI * 2.0D * (i + 1) / STEPS;
            float x0 = (float) (center.x + Math.cos(a0) * RADIUS);
            float z0 = (float) (center.z + Math.sin(a0) * RADIUS);
            float x1 = (float) (center.x + Math.cos(a1) * RADIUS);
            float z1 = (float) (center.z + Math.sin(a1) * RADIUS);
            lines.vertex(matrix, x0, (float) center.y, z0).color(0.72F, 0.24F, 1.0F, 0.82F)
                    .normal(matrix, 0.0F, 1.0F, 0.0F);
            lines.vertex(matrix, x1, (float) center.y, z1).color(0.72F, 0.24F, 1.0F, 0.82F)
                    .normal(matrix, 0.0F, 1.0F, 0.0F);
        }
        matrices.pop();
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        } catch (IOException ignored) {
            enabled = true;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL Gyro Wand Settings");
            }
        } catch (IOException ignored) {
            // Retain in-memory setting for the current session.
        }
    }
}
