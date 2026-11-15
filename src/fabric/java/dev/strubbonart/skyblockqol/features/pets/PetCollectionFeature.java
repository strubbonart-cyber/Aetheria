package dev.strubbonart.skyblockqol.features.pets;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import dev.strubbonart.skyblockqol.gui.PetCollectionScreen;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local cache of pets seen in the player's Hypixel Pets menu. */
public final class PetCollectionFeature {
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-pet-collection.properties");
    private static final Pattern LEVELLED_PET = Pattern.compile("^\\[Lvl\\s+(\\d+)]\\s+(.+)$");
    private static final Map<String, PetEntry> PETS = new LinkedHashMap<>();
    private static boolean initialized;
    private static long lastScan;

    public record PetEntry(String name, int level, int rarityColor, boolean specialSkin, boolean active) {}

    private PetCollectionFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("petcollection")
                        .executes(context -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            client.setScreen(new PetCollectionScreen(client.currentScreen));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("clear").executes(context -> {
                            PETS.clear();
                            save();
                            context.getSource().sendFeedback(Text.literal("Local pet collection cleared."));
                            return 1;
                        }))
                ));
    }

    public static List<PetEntry> getEntries() {
        return PETS.values().stream()
                .sorted(Comparator.comparing(PetEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static int getPetCount() { return PETS.size(); }

    public static void scanPetsMenu(HandledScreen<?> screen) {
        long now = System.currentTimeMillis();
        if (now - lastScan < 500L) return;
        lastScan = now;

        if (!(screen.getScreenHandler() instanceof net.minecraft.screen.GenericContainerScreenHandler handler)) return;
        if (!strip(screen.getTitle().getString()).startsWith("Pets")) return;

        for (Map.Entry<String, PetEntry> entry : new ArrayList<>(PETS.entrySet())) {
            PetEntry value = entry.getValue();
            if (value.active()) {
                PETS.put(entry.getKey(), new PetEntry(value.name(), value.level(),
                        value.rarityColor(), value.specialSkin(), false));
            }
        }

        int containerSlots = Math.max(0, handler.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty() || !stack.isOf(Items.PLAYER_HEAD)) continue;

            Matcher matcher = LEVELLED_PET.matcher(strip(stack.getName().getString()));
            if (!matcher.matches()) continue;

            int level;
            try {
                level = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }

            String rawName = matcher.group(2).trim();
            boolean skin = rawName.contains("✦");
            String name = rawName.replace("✦", "").trim();
            if (name.isEmpty()) continue;

            TextColor color = stack.getName().getStyle().getColor();
            int rarityRgb = color == null ? colorForNameColor(stack.getName()) : color.getRgb();
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            boolean active = lore != null && lore.lines().stream()
                    .map(line -> strip(line.getString()).toLowerCase(Locale.ROOT))
                    .anyMatch(line -> line.contains("click to despawn"));

            String key = name.toLowerCase(Locale.ROOT) + (skin ? ":skin" : "");
            PETS.put(key, new PetEntry(name, level, rarityRgb, skin, active));
        }
        save();
    }

    private static int colorForNameColor(Text text) {
        Formatting formatting = text.getStyle().getColor() == null ? null : null;
        return 0xFFFFFF;
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static void load() {
        if (!Files.isRegularFile(DATA_FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(DATA_FILE)) {
            p.load(in);
            int count = Math.max(0, Integer.parseInt(p.getProperty("count", "0")));
            for (int i = 0; i < count; i++) {
                String prefix = "pet." + i + ".";
                String name = p.getProperty(prefix + "name", "");
                if (name.isBlank()) continue;
                int level = Integer.parseInt(p.getProperty(prefix + "level", "0"));
                int color = Integer.parseInt(p.getProperty(prefix + "color", "16777215"));
                boolean skin = Boolean.parseBoolean(p.getProperty(prefix + "skin", "false"));
                boolean active = Boolean.parseBoolean(p.getProperty(prefix + "active", "false"));
                PETS.put(name.toLowerCase(Locale.ROOT) + (skin ? ":skin" : ""),
                        new PetEntry(name, level, color, skin, active));
            }
        } catch (IOException | NumberFormatException ignored) {
            PETS.clear();
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("count", Integer.toString(PETS.size()));
        int i = 0;
        for (PetEntry pet : PETS.values()) {
            String prefix = "pet." + i++ + ".";
            p.setProperty(prefix + "name", pet.name());
            p.setProperty(prefix + "level", Integer.toString(pet.level()));
            p.setProperty(prefix + "color", Integer.toString(pet.rarityColor()));
            p.setProperty(prefix + "skin", Boolean.toString(pet.specialSkin()));
            p.setProperty(prefix + "active", Boolean.toString(pet.active()));
        }
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(DATA_FILE)) {
                p.store(out, "Skyblock QoL Local Pet Collection");
            }
        } catch (IOException ignored) {
            // In-memory collection remains available for this session.
        }
    }
}