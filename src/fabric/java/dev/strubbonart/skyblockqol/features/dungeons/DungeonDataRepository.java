package dev.strubbonart.skyblockqol.features.dungeons;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads and caches the original Aetheria dungeon room and secret location
 * datasets. Their GPL-3.0 copyright/license metadata is preserved in each JSON.
 * Downloads are asynchronous so joining a world never waits on network I/O.
 */
public final class DungeonDataRepository {
    private static final String ROOM_URL =
            "https://raw.githubusercontent.com/aetheria-org/Aetheria-REPO/main/data/dungeonrooms.json";
    private static final String SECRET_URL =
            "https://raw.githubusercontent.com/aetheria-org/Aetheria-REPO/main/data/secretlocations.json";

    private static final Path DATA_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol").resolve("dungeon-data");
    private static final Path ROOM_FILE = DATA_DIR.resolve("dungeonrooms.json");
    private static final Path SECRET_FILE = DATA_DIR.resolve("secretlocations.json");

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SkyblockQoL-DungeonData");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile JsonObject roomData;
    private static volatile JsonObject secretData;
    private static volatile String roomStatus = "not loaded";
    private static volatile String secretStatus = "not loaded";
    private static volatile boolean initialized;

    private DungeonDataRepository() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        roomData = readCache(ROOM_FILE);
        secretData = readCache(SECRET_FILE);
        roomStatus = roomData == null ? "waiting for download" : "cached";
        secretStatus = secretData == null ? "waiting for download" : "cached";
        refresh();
    }

    public static void refresh() {
        EXECUTOR.execute(() -> {
            fetch(ROOM_URL, ROOM_FILE, true);
            fetch(SECRET_URL, SECRET_FILE, false);
        });
    }

    public static JsonObject getRoomData() {
        JsonObject data = roomData;
        return data == null ? null : data.deepCopy();
    }

    public static JsonObject getSecretData() {
        JsonObject data = secretData;
        return data == null ? null : data.deepCopy();
    }

    public static String getStatus() {
        return "Room data: " + roomStatus + " | Secret data: " + secretStatus;
    }

    private static JsonObject readCache(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return null;
            JsonObject object = parsed.getAsJsonObject();
            if (!hasLicenseMetadata(object)) return null;
            return object;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void fetch(String address, Path destination, boolean rooms) {
        HttpURLConnection connection = null;
        try {
            if (rooms) roomStatus = "downloading";
            else secretStatus = "downloading";

            connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(12_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "SkyblockQoL-Fabric/1.21 dungeon-data-cache");
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[8192];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    body.append(buffer, 0, count);
                    if (body.length() > 8_000_000) throw new IOException("Dataset exceeds size limit");
                }
            }

            JsonElement parsed = JsonParser.parseString(body.toString());
            if (!parsed.isJsonObject() || !hasLicenseMetadata(parsed.getAsJsonObject())) {
                throw new IOException("Dataset is missing its expected GPL-3.0 attribution metadata");
            }
            JsonObject object = parsed.getAsJsonObject();

            Files.createDirectories(DATA_DIR);
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, body.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, destination,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            if (rooms) {
                roomData = object;
                roomStatus = "ready (GPL-3.0 data cached)";
            } else {
                secretData = object;
                secretStatus = "ready (GPL-3.0 data cached)";
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (rooms) roomStatus = roomData == null ? "unavailable: " + message : "using cached copy";
            else secretStatus = secretData == null ? "unavailable: " + message : "using cached copy";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean hasLicenseMetadata(JsonObject object) {
        return object.has("copyright") && object.get("copyright").isJsonPrimitive()
                && object.has("license") && object.get("license").isJsonPrimitive()
                && object.get("license").getAsString().contains("GPL-3.0");
    }
}
