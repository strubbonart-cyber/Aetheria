package dev.strubbonart.skyblockqol.features.dungeons;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fabric world-space box highlights for dungeon bosses and Blood Room mobs.
 * It ports Aetheria's armor-stand-name association approach to modern entities.
 */
public final class DungeonMobHighlights {
    private static final Pattern BLOOD_MOB_NAME = Pattern.compile(
            ".*(?:Putrid|Reaper|Vader|Frost|Cannibal|Revoker|Tear|Mr\\\\.? Dead|Skull|Walker|Psycho|Ooze|Freak|Flamer|Mute|Leech|Parasite).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final int BONZO = 0xFFB45CFF;
    private static final int SCARF = 0xFFFFA040;
    private static final int SCARF_MINION = 0xFF55AAFF;
    private static final int PROFESSOR = 0xFFFF5555;
    private static final int BLOOD = 0xFFFF3333;

    private static final Map<UUID, Highlight> HIGHLIGHTS = new HashMap<>();
    private static int scanTicks;

    private DungeonMobHighlights() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(DungeonMobHighlights::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(DungeonMobHighlights::render);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null || !DungeonRunOverlay.isInDungeon()) {
            HIGHLIGHTS.clear();
            scanTicks = 0;
            return;
        }

        if (++scanTicks < 4) return;
        scanTicks = 0;

        Map<UUID, Highlight> found = new HashMap<>();
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity armorStand)) continue;
            String raw = armorStand.getName().getString();
            if (raw == null || raw.isBlank()) continue;
            String name = strip(raw).toLowerCase(Locale.ROOT);

            int color = bossColor(name);
            if (color != 0 && DungeonFeatures.areBossHighlightsEnabled()) {
                LivingEntity mob = findAssociatedMob(client, armorStand);
                if (mob != null && mob.isAlive()) {
                    found.put(mob.getUuid(), new Highlight(mob, color));
                }
            }

            if (DungeonFeatures.areBloodHighlightsEnabled() && BLOOD_MOB_NAME.matcher(name).matches()) {
                LivingEntity mob = findAssociatedMob(client, armorStand);
                if (mob != null && mob.isAlive() && !isDying(mob)) {
                    found.put(mob.getUuid(), new Highlight(mob, BLOOD));
                }
            }
        }
        HIGHLIGHTS.clear();
        HIGHLIGHTS.putAll(found);
    }

    private static LivingEntity findAssociatedMob(MinecraftClient client, ArmorStandEntity stand) {
        Box search = stand.getBoundingBox().expand(1.0, 3.0, 1.0);
        return client.world.getEntitiesByClass(
                LivingEntity.class,
                search,
                entity -> entity != client.player
                        && !(entity instanceof ArmorStandEntity)
                        && entity.isAlive()
        ).stream().min((a, b) -> Double.compare(
                a.squaredDistanceTo(stand),
                b.squaredDistanceTo(stand)
        )).orElse(null);
    }

    private static int bossColor(String name) {
        if (name.contains("bonzo")) return BONZO;
        if (name.contains("undead mage") || name.contains("undead archer")
                || name.contains("undead warrior") || name.contains("undead priest")) return SCARF_MINION;
        if (name.contains("scarf")) return SCARF;
        if (name.contains("healthy") || name.contains("chaos") || name.contains("laser")
                || name.contains("rogue") || name.contains("reinforced")
                || name.contains("professor")) return PROFESSOR;
        return 0;
    }

    private static boolean isDying(LivingEntity entity) {
        if (!entity.isAlive() || entity.getHealth() <= 0.1F) return true;
        Text display = entity.getDisplayName();
        String name = display == null ? "" : strip(display.getString());
        return name.matches(".*(?:0/\\d+❤|0❤)$");
    }

    private static void render(WorldRenderContext context) {
        if (HIGHLIGHTS.isEmpty() || context.matrixStack() == null
                || context.consumers() == null || context.camera() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || !DungeonRunOverlay.isInDungeon()) return;

        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        for (Highlight highlight : HIGHLIGHTS.values()) {
            LivingEntity entity = highlight.entity;
            if (!entity.isAlive() || entity.isInvisible()) continue;
            float r = ((highlight.argb >> 16) & 255) / 255.0F;
            float g = ((highlight.argb >> 8) & 255) / 255.0F;
            float b = (highlight.argb & 255) / 255.0F;
            float a = ((highlight.argb >>> 24) & 255) / 255.0F;
            WorldRenderer.drawBox(
                    matrices,
                    lines,
                    entity.getBoundingBox().expand(0.08, 0.04, 0.08),
                    r, g, b, a
            );
        }
        matrices.pop();
    }

    private static String strip(String value) {
        return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static final class Highlight {
        private final LivingEntity entity;
        private final int argb;

        private Highlight(LivingEntity entity, int argb) {
            this.entity = entity;
            this.argb = argb;
        }
    }
}
