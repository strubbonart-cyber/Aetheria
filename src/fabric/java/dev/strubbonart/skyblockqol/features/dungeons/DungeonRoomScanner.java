package dev.strubbonart.skyblockqol.features.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First Fabric port of Aetheria's room-hash detector and secret waypoint layer.
 *
 * Room hashes use the original sorted block-frequency algorithm. Modern 1.21
 * block-state strings are hashed directly and then passed through Aetheria's
 * known legacy hash aliases. A name is displayed only on an exact data match.
 * The initial scanner supports rectangular rooms; L-shaped rooms are a later
 * compatibility slice.
 */
public final class DungeonRoomScanner {
    private static final Path SETTINGS = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-dungeon-room.properties");
    private static final Pattern SECRETS_FOUND = Pattern.compile("Secrets Found:\\s*(\\d+)/(\\d+)");

    private static boolean roomOverlayEnabled = true;
    private static boolean secretFinderEnabled = true;
    private static int tickCount;
    private static int markerUpdateTicks;
    private static Object lastWorld;
    private static RoomSnapshot currentRoom;
    private static int lastSecretsFound = -1;

    private DungeonRoomScanner() {}

    public static void initialize() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(DungeonRoomScanner::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register(DungeonRoomScanner::onMessage);
        UseBlockCallback.EVENT.register(DungeonRoomScanner::onUseBlock);
        HudRenderCallback.EVENT.register(DungeonRoomScanner::renderHud);
        WorldRenderEvents.AFTER_ENTITIES.register(DungeonRoomScanner::renderWorld);
    }

    public static boolean isRoomOverlayEnabled() { return roomOverlayEnabled; }
    public static boolean isSecretFinderEnabled() { return secretFinderEnabled; }

    public static void setRoomOverlayEnabled(boolean value) {
        roomOverlayEnabled = value;
        save();
    }

    public static void setSecretFinderEnabled(boolean value) {
        secretFinderEnabled = value;
        if (!value && currentRoom != null) currentRoom.secrets.clear();
        save();
    }

    public static int getActiveSecretCount() {
        if (currentRoom == null) return -1;
        int count = 0;
        for (SecretMarker marker : currentRoom.secrets) if (!marker.collected) count++;
        return count;
    }

    public static String getStatus() {
        if (currentRoom == null) return "Room scanner: waiting";
        if (currentRoom.name == null) return "Room scanner: unmatched hash " + shortHash(currentRoom.hash);
        return "Room: " + currentRoom.name + " | Secrets: "
                + (secretFinderEnabled ? getActiveSecretCount() : "OFF");
    }

    private static void tick(MinecraftClient client) {
        if (client.world != lastWorld) {
            lastWorld = client.world;
            currentRoom = null;
            tickCount = 0;
            markerUpdateTicks = 0;
            lastSecretsFound = -1;
        }
        if (client.world == null || client.player == null || !DungeonRunOverlay.isInDungeon()
                || (!roomOverlayEnabled && !secretFinderEnabled)) {
            currentRoom = null;
            return;
        }

        BlockPos playerPos = client.player.getBlockPos();
        if (currentRoom != null && currentRoom.contains(playerPos)) {
            updateRelativePlayerPosition(playerPos);
            if (secretFinderEnabled && ++markerUpdateTicks >= 5) {
                markerUpdateTicks = 0;
                updateSecretStates(client.world, client.player);
            }
            return;
        }

        if (++tickCount < 20) return;
        tickCount = 0;
        markerUpdateTicks = 0;

        JsonObject roomData = DungeonDataRepository.getRoomData();
        if (roomData == null) return;
        currentRoom = detectRoom(client.world, playerPos, roomData,
                secretFinderEnabled ? DungeonDataRepository.getSecretData() : null);
        lastSecretsFound = -1;
    }

    private static RoomSnapshot detectRoom(World world, BlockPos player, JsonObject roomData,
                                           JsonObject secretData) {
        int x = player.getX();
        int z = player.getZ();
        Scan scan = new Scan(world);

        int top = scan.dungeonTop(x, z);
        if (top < 0) return null;

        int north = scan.endOfRoom(x, top, z, Direction.NORTH);
        int south = scan.endOfRoom(x, top, z, Direction.SOUTH);
        int east = scan.endOfRoom(x, top, z, Direction.EAST);
        int west = scan.endOfRoom(x, top, z, Direction.WEST);
        if (north < 0 || south < 0 || east < 0 || west < 0 || west >= east || north >= south) return null;

        // Aetheria's original rectangle path requires matching opposing widths.
        int northWidth = scan.endOfRoom(x, top, north, Direction.EAST)
                - scan.endOfRoom(x, top, north, Direction.WEST);
        int southWidth = scan.endOfRoom(x, top, south, Direction.EAST)
                - scan.endOfRoom(x, top, south, Direction.WEST);
        int eastWidth = scan.endOfRoom(east, top, z, Direction.SOUTH)
                - scan.endOfRoom(east, top, z, Direction.NORTH);
        int westWidth = scan.endOfRoom(west, top, z, Direction.SOUTH)
                - scan.endOfRoom(west, top, z, Direction.NORTH);
        if (northWidth != southWidth || eastWidth != westWidth) return null;

        int floor = scan.dungeonBottom(x, z);
        if (floor < 0) return null;

        String frequency = scan.blockFrequency(west, east, north, south, top);
        if (frequency == null) return null;
        String rawHash = md5(frequency);
        String hash = resolveHash(rawHash);
        String floorFrequency = scan.floorFrequency(west, east, north, south, top);
        String floorHash = floorFrequency == null ? null : md5(floorFrequency);

        JsonObject match = findRoom(roomData, hash, floorHash);
        String name = match == null ? null : getString(match, "name", null);
        String alias = match == null ? null : getString(match, "alias", name);
        String category = match == null ? null : getString(match, "category", "General");
        String notes = match == null ? null : getString(match, "notes", null);

        Corner origin = findOrigin(world, west, east, north, south, top);
        RoomSnapshot snapshot = new RoomSnapshot(
                west, east, north, south, floor, top, hash,
                name, alias, category, notes, origin
        );
        updateRelativePlayerPosition(player, snapshot);

        if (secretFinderEnabled && match != null && secretData != null && name != null && origin != null) {
            loadSecrets(snapshot, secretData, name);
        }
        return snapshot;
    }

    private static JsonObject findRoom(JsonObject roomData, String hash, String floorHash) {
        if (hash == null || !roomData.has(hash) || !roomData.get(hash).isJsonArray()) return null;
        JsonArray rooms = roomData.getAsJsonArray(hash);
        if (rooms.isEmpty()) return null;
        JsonObject first = rooms.get(0).getAsJsonObject();
        if (rooms.size() == 1) return first;
        if (floorHash != null) {
            for (JsonElement element : rooms) {
                if (!element.isJsonObject()) continue;
                JsonObject candidate = element.getAsJsonObject();
                String expected = getString(candidate, "floorhash", null);
                if (floorHash.equals(expected)) return candidate;
            }
        }
        return first;
    }

    private static void loadSecrets(RoomSnapshot snapshot, JsonObject secretData, String roomName) {
        snapshot.secrets.clear();
        if (!secretData.has(roomName) || !secretData.get(roomName).isJsonArray()) return;
        JsonArray secrets = secretData.getAsJsonArray(roomName);
        for (JsonElement element : secrets) {
            if (!element.isJsonObject()) continue;
            JsonObject secret = element.getAsJsonObject();
            String category = getString(secret, "category", "");
            if (category.equals("fairysoul")) continue;
            String name = getString(secret, "secretName", "Secret");
            if (!secret.has("x") || !secret.has("y") || !secret.has("z")) continue;
            BlockPos relative = new BlockPos(secret.get("x").getAsInt(),
                    secret.get("y").getAsInt(), secret.get("z").getAsInt());
            BlockPos absolute = relativeToActual(relative, snapshot.origin.position, snapshot.origin.corner);
            if (absolute != null) snapshot.secrets.add(new SecretMarker(absolute, category, name));
        }
    }

    private static void updateSecretStates(World world, PlayerEntity player) {
        if (currentRoom == null) return;
        for (SecretMarker marker : currentRoom.secrets) {
            if (marker.collected) continue;
            BlockPos pos = marker.position;
            boolean air = world.getBlockState(pos).isAir();
            if (!marker.seen && !air) marker.seen = true;

            if ("entrance".equals(marker.category)
                    && player.squaredDistanceTo(pos.getX() + 0.5, player.getY(), pos.getZ() + 0.5) <= 2.25) {
                marker.collected = true;
            } else if (("item".equals(marker.category))
                    && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 2.25) {
                marker.collected = true;
            } else if (marker.seen && air && ("chest".equals(marker.category)
                    || "wither".equals(marker.category) || "superboom".equals(marker.category))) {
                marker.collected = true;
            }
        }
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world,
                                           net.minecraft.util.Hand hand,
                                           net.minecraft.util.hit.BlockHitResult hit) {
        if (!world.isClient || !secretFinderEnabled || currentRoom == null) return ActionResult.PASS;
        BlockPos clicked = hit.getBlockPos();
        BlockState state = world.getBlockState(clicked);
        for (SecretMarker marker : currentRoom.secrets) {
            if (marker.collected || clicked.getSquaredDistance(marker.position) > 4.0) continue;
            if ("lever".equals(marker.category) && state.isOf(Blocks.LEVER)) {
                marker.collected = true;
            } else if ("chest".equals(marker.category)
                    && (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST))) {
                marker.collected = true;
            }
        }
        return ActionResult.PASS;
    }

    private static boolean onMessage(Text message, boolean overlay) {
        if (!secretFinderEnabled || currentRoom == null || !overlay) return true;
        Matcher matcher = SECRETS_FOUND.matcher(strip(message.getString()));
        if (!matcher.find()) return true;
        try {
            int found = Integer.parseInt(matcher.group(1));
            int total = Integer.parseInt(matcher.group(2));
            if (found == total && total > 0) {
                for (SecretMarker marker : currentRoom.secrets) marker.collected = true;
            } else if (lastSecretsFound >= 0 && found > lastSecretsFound) {
                SecretMarker nearest = nearestUncollectedItem();
                if (nearest != null) nearest.collected = true;
            }
            lastSecretsFound = found;
        } catch (NumberFormatException ignored) {
            // Ignore malformed action-bar counters.
        }
        return true;
    }

    private static SecretMarker nearestUncollectedItem() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || currentRoom == null) return null;
        SecretMarker nearest = null;
        double best = Double.MAX_VALUE;
        for (SecretMarker marker : currentRoom.secrets) {
            if (marker.collected || !"item".equals(marker.category)) continue;
            double distance = client.player.squaredDistanceTo(marker.position.getX() + 0.5,
                    marker.position.getY() + 0.5, marker.position.getZ() + 0.5);
            if (distance < best) {
                best = distance;
                nearest = marker;
            }
        }
        return nearest;
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!roomOverlayEnabled || currentRoom == null || !DungeonRunOverlay.isInDungeon()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(
                DungeonOverlaySettings.x(DungeonOverlaySettings.Widget.ROOM),
                DungeonOverlaySettings.y(DungeonOverlaySettings.Widget.ROOM), 0.0F);
        float scale = DungeonOverlaySettings.scale(DungeonOverlaySettings.Widget.ROOM);
        matrices.scale(scale, scale, 1.0F);

        int y = 0;
        String title = currentRoom.name == null
                ? "Unknown room (" + shortHash(currentRoom.hash) + ")"
                : currentRoom.category + " | " + currentRoom.name;
        context.drawTextWithShadow(client.textRenderer, title, 0, y, 0xFF55FFFF);
        if (currentRoom.alias != null && currentRoom.name != null) {
            context.drawTextWithShadow(client.textRenderer, "Alias: " + currentRoom.alias, 0, y + 11, 0xFFFFFFFF);
        }
        if (currentRoom.relativeX != Integer.MIN_VALUE) {
            context.drawTextWithShadow(client.textRenderer,
                    "Relative: (" + currentRoom.relativeX + ", " + currentRoom.relativeZ + ")",
                    0, y + 22, 0xFFB8B8B8);
        }
        if (secretFinderEnabled) {
            int count = getActiveSecretCount();
            context.drawTextWithShadow(client.textRenderer,
                    count < 0 ? "Secrets: waiting for room match" : "Secrets remaining: " + count,
                    0, y + 33, count > 0 ? 0xFF55FF55 : 0xFFFFAA55);
        }
        matrices.pop();
    }

    private static void renderWorld(WorldRenderContext context) {
        if (!secretFinderEnabled || currentRoom == null || currentRoom.secrets.isEmpty()
                || context.matrixStack() == null || context.consumers() == null || context.camera() == null
                || !DungeonRunOverlay.isInDungeon()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        VertexConsumer fills = context.consumers().getBuffer(RenderLayer.getDebugFilledBox());
        for (SecretMarker marker : currentRoom.secrets) {
            if (marker.collected) continue;
            int color = colorForCategory(marker.category);
            float red = ((color >> 16) & 255) / 255.0F;
            float green = ((color >> 8) & 255) / 255.0F;
            float blue = (color & 255) / 255.0F;

            Box box = new Box(marker.position).expand(0.08, 0.08, 0.08);
            WorldRenderer.drawBox(matrices, lines, box, red, green, blue, 0.95F);
            WorldRenderer.renderFilledBox(matrices, fills,
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    red, green, blue, 0.12F);
        }
        matrices.pop();
    }

    private static int colorForCategory(String category) {
        return switch (category) {
            case "chest" -> 0x55DDFF;
            case "lever" -> 0xFFFF55;
            case "bat" -> 0xFF55FF;
            case "item" -> 0x55FF55;
            case "entrance" -> 0xAAAAAA;
            default -> 0xFFAA55;
        };
    }

    private static void updateRelativePlayerPosition(BlockPos player, RoomSnapshot snapshot) {
        if (snapshot.origin == null) return;
        int[] relative = actualToRelative(player, snapshot.origin.position, snapshot.origin.corner);
        snapshot.relativeX = relative[0];
        snapshot.relativeZ = relative[1];
    }

    private static void updateRelativePlayerPosition(BlockPos player) {
        if (currentRoom != null) updateRelativePlayerPosition(player, currentRoom);
    }

    private static int[] actualToRelative(BlockPos actual, BlockPos origin, String corner) {
        int dx = actual.getX() - origin.getX();
        int dz = actual.getZ() - origin.getZ();
        return switch (corner) {
            case "northwest" -> new int[]{dx, dz};
            case "northeast" -> new int[]{dz, -dx};
            case "southeast" -> new int[]{-dx, -dz};
            case "southwest" -> new int[]{-dz, dx};
            default -> new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
        };
    }

    private static BlockPos relativeToActual(BlockPos relative, BlockPos origin, String corner) {
        int x;
        int z;
        switch (corner) {
            case "northwest" -> { x = relative.getX() + origin.getX(); z = relative.getZ() + origin.getZ(); }
            case "northeast" -> { x = origin.getX() - relative.getZ(); z = relative.getX() + origin.getZ(); }
            case "southeast" -> { x = origin.getX() - relative.getX(); z = origin.getZ() - relative.getZ(); }
            case "southwest" -> { x = relative.getZ() + origin.getX(); z = origin.getZ() - relative.getX(); }
            default -> { return null; }
        }
        return new BlockPos(x, relative.getY(), z);
    }

    private static Corner findOrigin(World world, int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                String path = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).getPath();
                if (!path.endsWith("terracotta")) continue;

                boolean north = world.getBlockState(pos.north()).isAir();
                boolean south = world.getBlockState(pos.south()).isAir();
                boolean east = world.getBlockState(pos.east()).isAir();
                boolean west = world.getBlockState(pos.west()).isAir();

                if (north && west) return new Corner(pos, "northwest");
                if (north && east) return new Corner(pos, "northeast");
                if (south && east) return new Corner(pos, "southeast");
                if (south && west) return new Corner(pos, "southwest");
            }
        }
        return null;
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static String strip(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static String shortHash(String hash) {
        return hash == null ? "unknown" : hash.substring(0, Math.min(8, hash.length()));
    }

    private static String md5(String input) {
        if (input == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            return out.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String resolveHash(String hash) {
        if (hash == null) return null;
        return switch (hash) {
            case "eb202d1d318396fc44bd1da3ab00b9cc", "e94d4df3348b347eb6182ef6bd7cb26d" -> "721bf13b2441c9269f8222f4e90f897c";
            case "11ac182bc9abe2cbf21719733d4d58bc" -> "74e45b213b3372fe91b0dd6a7474a588";
            case "3e877ad473671a2767362a93348b9f7f" -> "506f87f8b14643cfcccd3d2845c86e50";
            case "48c22ef4c10a0a5036f9f06c62f295e4", "56ae7d302c9d835d187e001a93372463" -> "710eb845c35f240667bb63f8edb754bf";
            case "03c30fd33553f37b22b9c4b8bed33e1d" -> "c979e9eda7361555ce2d75d63f5305bb";
            case "1d104fa1f828f60074dfc345dcf35032" -> "ac807d34afef330d7275836795c6f734";
            case "2002014fb9fbaa0f896aaadc3854fef4", "4c118368fc6f08b29ee18717999590bd",
                    "aeeeb0546987de22e3c4a45f45d546f9" -> "569c63a07c6ebfe1153d0738f0e44731";
            case "0d25288e91b3380442576b0c0b23fa31" -> "6d788f8bd2fb147f71d1afa6e010a7b8";
            case "2cfcecf71825b76faa4f97787da2e996" -> "263a269d5c93255c60eb721e128f2d20";
            case "d076f0391db006f2282c52ec7c63d520" -> "fe4b5561b73fb082acb80d904ec82294";
            default -> hash;
        };
    }

    private static void load() {
        if (!Files.isRegularFile(SETTINGS)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(SETTINGS)) {
            p.load(in);
            roomOverlayEnabled = Boolean.parseBoolean(p.getProperty("roomOverlayEnabled", "true"));
            secretFinderEnabled = Boolean.parseBoolean(p.getProperty("secretFinderEnabled", "true"));
        } catch (IOException ignored) {
            roomOverlayEnabled = true;
            secretFinderEnabled = true;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("roomOverlayEnabled", Boolean.toString(roomOverlayEnabled));
        p.setProperty("secretFinderEnabled", Boolean.toString(secretFinderEnabled));
        try {
            Files.createDirectories(SETTINGS.getParent());
            try (OutputStream out = Files.newOutputStream(SETTINGS)) {
                p.store(out, "Skyblock QoL Dungeon Room/Secret Finder Settings");
            }
        } catch (IOException ignored) {
            // Keep settings active for this session even if the disk is unavailable.
        }
    }

    private enum Direction { NORTH, SOUTH, EAST, WEST }

    private static final class Scan {
        private final World world;
        private final Map<Long, Integer> topCache = new HashMap<>();
        private final Map<Long, Integer> bottomCache = new HashMap<>();
        private final Map<String, Integer> edgeCache = new HashMap<>();
        private final Map<Long, Integer> heightCache = new HashMap<>();

        private Scan(World world) { this.world = world; }

        private int dungeonTop(int x, int z) {
            long key = key(x, z);
            Integer cached = topCache.get(key);
            if (cached != null) return cached;
            for (int y = 255; y >= 78; y--) {
                if (!world.getBlockState(new BlockPos(x, y, z)).isAir() && checkPlatform(x, y, z)) {
                    topCache.put(key, y);
                    return y;
                }
            }
            topCache.put(key, -1);
            return -1;
        }

        private int dungeonBottom(int x, int z) {
            long key = key(x, z);
            Integer cached = bottomCache.get(key);
            if (cached != null) return cached;
            for (int y = 0; y <= 68; y++) {
                BlockState state = world.getBlockState(new BlockPos(x, y, z));
                if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.STONE)) {
                    bottomCache.put(key, y);
                    return y;
                }
            }
            bottomCache.put(key, -1);
            return -1;
        }

        private int dungeonHeight(int x, int z) {
            long key = key(x, z);
            Integer cached = heightCache.get(key);
            if (cached != null) return cached;
            int top = dungeonTop(x, z);
            int bottom = dungeonBottom(x, z);
            int value = top < 0 || bottom < 0 ? -1 : top - bottom;
            heightCache.put(key, value);
            return value;
        }

        private boolean checkPlatform(int x, int y, int z) {
            int north = 0, south = 0, east = 0, west = 0;
            for (int offset = 0; offset < 10; offset++) {
                if (!world.getBlockState(new BlockPos(x, y, z - offset)).isAir()) north++;
                if (!world.getBlockState(new BlockPos(x, y, z + offset)).isAir()) south++;
                if (!world.getBlockState(new BlockPos(x + offset, y, z)).isAir()) east++;
                if (!world.getBlockState(new BlockPos(x - offset, y, z)).isAir()) west++;
            }
            return north == 10 || south == 10 || east == 10 || west == 10;
        }

        private int endOfRoom(int x, int y, int z, Direction direction) {
            String cacheKey = x + "," + y + "," + z + "," + direction.name();
            Integer cached = edgeCache.get(cacheKey);
            if (cached != null) return cached;
            for (int i = 1; i <= 200; i++) {
                int nx = x, nz = z;
                int px = x, pz = z;
                switch (direction) {
                    case NORTH -> { nz = z - i; pz = z - i + 1; }
                    case SOUTH -> { nz = z + i; pz = z + i - 1; }
                    case EAST -> { nx = x + i; px = x + i - 1; }
                    case WEST -> { nx = x - i; px = x - i + 1; }
                }
                if (world.getBlockState(new BlockPos(nx, y, nz)).isAir()
                        || checkPlatform(nx, y + 1, nz)
                        || Math.abs(dungeonHeight(nx, nz) - dungeonHeight(px, pz)) > 3) {
                    int result = switch (direction) {
                        case NORTH -> z - i + 1;
                        case SOUTH -> z + i - 1;
                        case EAST -> x + i - 1;
                        case WEST -> x - i + 1;
                    };
                    edgeCache.put(cacheKey, result);
                    return result;
                }
            }
            edgeCache.put(cacheKey, -1);
            return -1;
        }

        private String blockFrequency(int minX, int maxX, int minZ, int maxZ, int y) {
            Map<String, Integer> counts = new HashMap<>();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    String state = world.getBlockState(new BlockPos(x, y, z)).toString();
                    counts.merge(state, 1, Integer::sum);
                }
            }
            return frequencyString(counts);
        }

        private String floorFrequency(int minX, int maxX, int minZ, int maxZ, int topY) {
            Map<String, Integer> counts = new HashMap<>();
            int y = 68;
            for (int x = minX + 10; x <= maxX - 10; x++) {
                for (int z = minZ + 10; z <= maxZ - 10; z++) {
                    String block = world.getBlockState(new BlockPos(x, y, z)).getBlock().toString();
                    counts.merge(block, 1, Integer::sum);
                }
            }
            if (counts.isEmpty()) return null;
            return frequencyString(counts);
        }

        private static String frequencyString(Map<String, Integer> counts) {
            if (counts.isEmpty()) return null;
            List<String> frequencies = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                frequencies.add(entry.getKey() + ":" + entry.getValue());
            }
            Collections.sort(frequencies);
            return String.join(",", frequencies);
        }

        private static long key(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }
    }

    private static final class Corner {
        private final BlockPos position;
        private final String corner;
        private Corner(BlockPos position, String corner) {
            this.position = position;
            this.corner = corner;
        }
    }

    private static final class SecretMarker {
        private final BlockPos position;
        private final String category;
        private final String name;
        private boolean collected;
        private boolean seen;
        private SecretMarker(BlockPos position, String category, String name) {
            this.position = position;
            this.category = category;
            this.name = name;
        }
    }

    private static final class RoomSnapshot {
        private final int minX, maxX, minZ, maxZ, floorY, ceilingY;
        private final String hash, name, alias, category, notes;
        private final Corner origin;
        private final List<SecretMarker> secrets = new ArrayList<>();
        private int relativeX = Integer.MIN_VALUE;
        private int relativeZ = Integer.MIN_VALUE;

        private RoomSnapshot(int minX, int maxX, int minZ, int maxZ,
                             int floorY, int ceilingY, String hash,
                             String name, String alias, String category,
                             String notes, Corner origin) {
            this.minX = minX; this.maxX = maxX;
            this.minZ = minZ; this.maxZ = maxZ;
            this.floorY = floorY; this.ceilingY = ceilingY;
            this.hash = hash; this.name = name; this.alias = alias;
            this.category = category; this.notes = notes; this.origin = origin;
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}
