package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Highlights Private Island brewing stands while their displayed brew timer is active. */
public final class BrewingStandFeature {
    private static final Pattern TIMER = Pattern.compile("(\\d+(?:\\.\\d)?)s");
    private static final Map<BlockPos, Long> BREWING_UNTIL = new HashMap<>();
    private static boolean enabled = true;
    private static BlockPos lastInteractedStand;

    private BrewingStandFeature() {}

    public static void initialize() {
        UseBlockCallback.EVENT.register(BrewingStandFeature::onUseBlock);
        ClientTickEvents.END_CLIENT_TICK.register(BrewingStandFeature::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(BrewingStandFeature::render);
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) BREWING_UNTIL.clear();
    }

    private static ActionResult onUseBlock(net.minecraft.entity.player.PlayerEntity player,
                                           World world, Hand hand, BlockHitResult hit) {
        if (!world.isClient || !enabled || !isPrivateIsland()) return ActionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        if (world.getBlockState(pos).isOf(Blocks.BREWING_STAND)) {
            lastInteractedStand = pos.toImmutable();
        }
        return ActionResult.PASS;
    }

    /** Invoked from HandledScreenMixin after a Brewing Stand GUI has rendered. */
    public static void captureTimer(HandledScreen<?> screen) {
        if (!enabled || lastInteractedStand == null || !isPrivateIsland()
                || !strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT).contains("brewing stand")) return;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        for (var slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            for (Text line : stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC)) {
                Matcher matcher = TIMER.matcher(strip(line.getString()));
                if (!matcher.find()) continue;
                try {
                    double seconds = Double.parseDouble(matcher.group(1));
                    BREWING_UNTIL.put(lastInteractedStand, System.currentTimeMillis()
                            + Math.max(0L, Math.round(seconds * 1000.0D)));
                    return;
                } catch (NumberFormatException ignored) {
                    // Ignore malformed timer values and keep scanning tooltip lines.
                }
            }
        }
        BREWING_UNTIL.remove(lastInteractedStand);
    }

    private static void tick(MinecraftClient client) {
        if (!enabled || client.world == null) {
            BREWING_UNTIL.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, Long>> iterator = BREWING_UNTIL.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (entry.getValue() <= now
                    || !client.world.getBlockState(entry.getKey()).isOf(Blocks.BREWING_STAND)) {
                iterator.remove();
            }
        }
    }

    private static void render(WorldRenderContext context) {
        if (!enabled || BREWING_UNTIL.isEmpty() || context.matrixStack() == null
                || context.consumers() == null || context.camera() == null || !isPrivateIsland()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        VertexConsumer fill = context.consumers().getBuffer(RenderLayer.getDebugFilledBox());
        long now = System.currentTimeMillis();
        for (Map.Entry<BlockPos, Long> entry : BREWING_UNTIL.entrySet()) {
            if (entry.getValue() <= now) continue;
            Box box = new Box(entry.getKey()).expand(0.01D);
            WorldRenderer.drawBox(matrices, lines, box, 1.0F, 0.18F, 0.12F, 0.9F);
            WorldRenderer.renderFilledBox(matrices, fill,
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    1.0F, 0.12F, 0.08F, 0.22F);
        }
        matrices.pop();
    }

    private static boolean isPrivateIsland() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        ScoreboardObjective objective = client.world.getScoreboard()
                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return false;
        for (ScoreboardEntry entry : client.world.getScoreboard().getScoreboardEntries(objective)) {
            if (entry.hidden()) continue;
            String line;
            if (entry.display() != null) line = entry.display().getString();
            else {
                Team team = client.world.getScoreboard().getScoreHolderTeam(entry.owner());
                line = team == null ? entry.name().getString()
                        : team.decorateName(entry.name()).getString();
            }
            String clean = strip(line).toLowerCase(Locale.ROOT);
            if (clean.contains("private island") || clean.contains("your island")) return true;
        }
        return false;
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}
