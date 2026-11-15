package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Local inventory-diff pickup/drop log. Counts net item changes, not individual pickup packets. */
public final class ItemPickupLogFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-item-log.properties");
    private static final int MAX_LINES = 12;
    private static final long LIFESPAN_MS = 5_000L;
    private static final int IGNORED_SLOT = 8;
    private static final Map<String, Integer> PREVIOUS = new HashMap<>();
    private static final Map<String, String> LAST_NAMES = new HashMap<>();
    private static final List<LogEntry> LOG = new ArrayList<>();

    private static boolean initialized;
    private static boolean enabled = true;
    private static int x = 8;
    private static int y = 200;
    private static float scale = 1.0F;
    private static long lastScan;

    private record LogEntry(String displayName, int amount, long timestamp) {}

    private ItemPickupLogFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
    public static int getX() { return x; }
    public static int getY() { return y; }
    public static float getScale() { return scale; }
    public static void updateLayout(int nx, int ny, float ns) {
        x = Math.max(0, Math.min(5000, nx));
        y = Math.max(0, Math.min(5000, ny));
        scale = Math.max(0.5F, Math.min(3.0F, ns));
    }
    public static void saveLayout() { save(); }
    public static void resetLayout() { x = 8; y = 200; scale = 1.0F; save(); }

    public static void clear() {
        LOG.clear();
        PREVIOUS.clear();
        LAST_NAMES.clear();
    }

    private static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        LOG.removeIf(entry -> now - entry.timestamp() > LIFESPAN_MS);

        if (!enabled || client.player == null || client.world == null) {
            PREVIOUS.clear();
            LAST_NAMES.clear();
            return;
        }
        if (now - lastScan < 100L || client.currentScreen != null) return;
        lastScan = now;

        Map<String, Integer> current = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        List<ItemStack> inventory = client.player.getInventory().main;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (slot == IGNORED_SLOT) continue;
            ItemStack stack = inventory.get(slot);
            if (stack.isEmpty()) continue;
            String id = internalId(stack);
            if (id.isEmpty()) continue;
            current.merge(id, stack.getCount(), Integer::sum);
            names.putIfAbsent(id, stack.getName().getString());
        }

        if (!PREVIOUS.isEmpty()) {
            Set<String> all = new HashSet<>(PREVIOUS.keySet());
            all.addAll(current.keySet());
            for (String id : all) {
                int delta = current.getOrDefault(id, 0) - PREVIOUS.getOrDefault(id, 0);
                if (delta == 0) continue;
                String name = names.getOrDefault(id, LAST_NAMES.getOrDefault(id, prettyId(id)));
                addLog(name, delta);
                if (delta > 0) RareDropTrackerFeature.onItemChange(id, name, delta);
            }
        }

        PREVIOUS.clear();
        PREVIOUS.putAll(current);
        LAST_NAMES.clear();
        LAST_NAMES.putAll(names);
    }

    private static String internalId(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom != null && !custom.isEmpty()) {
            NbtCompound root = custom.copyNbt();
            NbtCompound extra = root.contains("ExtraAttributes") ? root.getCompound("ExtraAttributes") : root;
            String id = extra.getString("id").trim();
            if (!id.isEmpty()) return id.toLowerCase(Locale.ROOT);
        }
        return Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
    }

    private static String prettyId(String id) {
        int colon = id.indexOf(':');
        String name = colon >= 0 ? id.substring(colon + 1) : id;
        return name.replace('_', ' ');
    }

    private static void addLog(String name, int delta) {
        for (int i = LOG.size() - 1; i >= 0; i--) {
            LogEntry entry = LOG.get(i);
            boolean sameSign = (delta > 0 && entry.amount() > 0) || (delta < 0 && entry.amount() < 0);
            if (entry.displayName().equals(name) && sameSign
                    && System.currentTimeMillis() - entry.timestamp() <= LIFESPAN_MS) {
                LOG.set(i, new LogEntry(name, entry.amount() + delta, System.currentTimeMillis()));
                return;
            }
        }
        LOG.add(new LogEntry(name, delta, System.currentTimeMillis()));
        while (LOG.size() > MAX_LINES) LOG.remove(0);
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.world == null || LOG.isEmpty()) return;
        List<String> lines = new ArrayList<>();
        lines.add("Item Pickup Log");
        for (int i = LOG.size() - 1; i >= 0 && lines.size() <= 6; i--) {
            LogEntry entry = LOG.get(i);
            String sign = entry.amount() > 0 ? "+" : "-";
            lines.add(sign + " " + Math.abs(entry.amount()) + "x " + entry.displayName());
        }

        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        width += 14;
        int height = lines.size() * 12 + 8;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        context.fill(0, 0, width, height, 0xB91B2634);
        context.fill(0, 0, width, 2, 0xFF55DDEE);
        int textY = 5;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFFFF
                    : lines.get(i).startsWith("+") ? 0xFF77DD88 : 0xFFFF7777;
            context.drawTextWithShadow(client.textRenderer, lines.get(i), 7, textY, color);
            textY += 12;
        }
        matrices.pop();
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            x = Integer.parseInt(p.getProperty("x", "8"));
            y = Integer.parseInt(p.getProperty("y", "200"));
            scale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(p.getProperty("scale", "1.0"))));
        } catch (IOException | NumberFormatException ignored) {
            enabled = true;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("x", Integer.toString(x));
        p.setProperty("y", Integer.toString(y));
        p.setProperty("scale", Float.toString(scale));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Item Pickup Log");
            }
        } catch (IOException ignored) {}
    }
}