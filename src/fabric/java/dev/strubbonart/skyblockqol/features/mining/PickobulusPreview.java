package dev.strubbonart.skyblockqol.features.mining;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Fabric 1.21 port of Aetheria's Pickobulus blast-radius preview.
 * Shows a 7x7x7 wireframe cube centred on the block the player is aiming at
 * while holding a pickaxe whose lore identifies the Pickobulus ability.
 */
public final class PickobulusPreview {
    private static final double REACH = 30.0;
    private static final int RADIUS = 3;
    private static final String LORE_MARKER = "Ability: Pickobulus";

    private static boolean enabled = true;
    private static Box previewBox;

    private PickobulusPreview() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(PickobulusPreview::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(PickobulusPreview::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("pickobuluspreview").executes(context -> {
                    enabled = !enabled;
                    previewBox = null;
                    context.getSource().sendFeedback(Text.literal(
                            "Pickobulus preview " + (enabled ? "enabled" : "disabled")));
                    return 1;
                })));
    }

    private static void tick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null || client.options.hudHidden
                || !hasPickobulusLore(client)) {
            previewBox = null;
            return;
        }

        HitResult hit = client.player.raycast(REACH, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            previewBox = null;
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        previewBox = new Box(
                pos.getX() - RADIUS,
                pos.getY() - RADIUS,
                pos.getZ() - RADIUS,
                pos.getX() + RADIUS + 1.0,
                pos.getY() + RADIUS + 1.0,
                pos.getZ() + RADIUS + 1.0
        );
    }

    private static boolean hasPickobulusLore(MinecraftClient client) {
        if (client.player == null) return false;
        var held = client.player.getMainHandStack();
        if (held.isEmpty()) return false;

        LoreComponent lore = held.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        for (Text line : lore.lines()) {
            String clean = line.getString().replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
            if (clean.toLowerCase(Locale.ROOT).contains(LORE_MARKER.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static void render(WorldRenderContext context) {
        Box box = previewBox;
        if (!enabled || box == null || context.matrixStack() == null || context.consumers() == null
                || context.camera() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, lines, box, 1.0F, 0.39F, 0.78F, 0.95F);

        VertexConsumer fill = context.consumers().getBuffer(RenderLayer.getDebugFilledBox());
        WorldRenderer.renderFilledBox(
                matrices, fill,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                1.0F, 0.39F, 0.78F, 0.12F
        );
        matrices.pop();
    }
}
