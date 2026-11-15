package dev.strubbonart.skyblockqol.features.qol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data-driven enchant tooltip support ported from Aetheria.
 * This first Fabric slice colors enchant levels and lists compatible missing
 * enchants while Shift is held. Chroma and the original layout editor are
 * deliberately left for a later pass.
 */
public final class EnchantTooltipFeature {
    private static final Pattern ENCHANT_LINE = Pattern.compile(
            "(?<enchant>[A-Za-z][A-Za-z -]+) (?<levelNumeral>[IVXLCDM]+|\\d+)(?=, |$| [\\d,]+$)"
    );

    private static final Map<String, EnchantMeta> BY_LORE = new HashMap<>();
    private static JsonObject ROOT;
    private static JsonObject ENCHANT_CATEGORIES;
    private static boolean initialized;

    private EnchantTooltipFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        loadDatabase();
        ItemTooltipCallback.EVENT.register(EnchantTooltipFeature::onTooltip);
    }

    private static void loadDatabase() {
        try (InputStream input = EnchantTooltipFeature.class.getResourceAsStream(
                "/skyblockqol/enchants.json")) {
            if (input == null) return;
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                ROOT = JsonParser.parseReader(reader).getAsJsonObject();
            }

            ENCHANT_CATEGORIES = ROOT.getAsJsonObject("enchants");
            loadSection("NORMAL", 2);
            loadSection("ULTIMATE", 0);
            loadSection("STACKING", 1);
        } catch (Exception ignored) {
            ROOT = null;
            ENCHANT_CATEGORIES = null;
            BY_LORE.clear();
        }
    }

    private static void loadSection(String section, int sortType) {
        JsonObject object = ROOT.getAsJsonObject(section);
        if (object == null) return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                JsonObject value = entry.getValue().getAsJsonObject();
                EnchantMeta meta = new EnchantMeta(
                        entry.getKey(),
                        value.get("loreName").getAsString(),
                        value.get("nbtName").getAsString().toLowerCase(Locale.ROOT),
                        value.get("goodLevel").getAsInt(),
                        value.get("maxLevel").getAsInt(),
                        sortType
                );
                BY_LORE.put(meta.loreName.toLowerCase(Locale.ROOT), meta);
            } catch (Exception ignored) {
                // A malformed entry should not disable the remaining enchant data.
            }
        }
    }

    private static void onTooltip(ItemStack stack, net.minecraft.item.Item.TooltipContext tooltipContext,
                                  net.minecraft.item.TooltipType tooltipType, List<Text> lines) {
        if (stack == null || stack.isEmpty() || ROOT == null) return;

        if (QolFeatures.isEnchantHighlightEnabled()) {
            colorEnchantLines(lines);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (QolFeatures.isMissingEnchantsEnabled() && client.options.sneakKey.isPressed()) {
            appendMissingEnchants(stack, lines);
        }
    }

    private static void colorEnchantLines(List<Text> lines) {
        boolean insideEnchantSection = false;
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i).getString();
            if (raw.isBlank()) {
                if (insideEnchantSection) break;
                continue;
            }

            Matcher matcher = ENCHANT_LINE.matcher(raw);
            MutableText rebuilt = Text.empty();
            int cursor = 0;
            boolean changed = false;

            while (matcher.find()) {
                EnchantMeta meta = BY_LORE.get(matcher.group("enchant").trim().toLowerCase(Locale.ROOT));
                if (meta == null) continue;

                if (matcher.start() > cursor) {
                    rebuilt.append(Text.literal(raw.substring(cursor, matcher.start())));
                }
                int level = parseLevel(matcher.group("levelNumeral"));
                rebuilt.append(Text.literal(matcher.group("enchant") + " " + matcher.group("levelNumeral"))
                        .formatted(colorFor(meta, level)));
                cursor = matcher.end();
                changed = true;
            }

            if (changed) {
                rebuilt.append(Text.literal(raw.substring(cursor)));
                lines.set(i, rebuilt);
                insideEnchantSection = true;
            } else if (insideEnchantSection && QolFeatures.isHideEnchantDescriptionsEnabled()) {
                lines.remove(i--);
            }
        }
    }

    private static Formatting colorFor(EnchantMeta meta, int level) {
        if (meta.sortType == 0) return Formatting.DARK_PURPLE;
        if (meta.sortType == 1) return level >= meta.maxLevel ? Formatting.AQUA : Formatting.GOLD;
        if (level >= meta.maxLevel) return Formatting.AQUA;
        if (level >= meta.maxLevel - 1) return Formatting.GOLD;
        if (level >= meta.goodLevel) return Formatting.GREEN;
        return Formatting.GRAY;
    }

    private static int parseLevel(String value) {
        if (value == null || value.isBlank()) return 1;
        if (value.chars().allMatch(Character::isDigit)) {
            try { return Integer.parseInt(value); }
            catch (NumberFormatException ignored) { return 1; }
        }
        return romanToInt(value);
    }

    private static int romanToInt(String value) {
        int total = 0;
        int previous = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            int current = switch (value.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            total += current < previous ? -current : current;
            previous = current;
        }
        return Math.max(1, total);
    }

    private static void appendMissingEnchants(ItemStack stack, List<Text> lines) {
        NbtCompound extra = getExtraAttributes(stack);
        if (extra == null || !extra.contains("enchantments")) return;
        NbtCompound enchantments = extra.getCompound("enchantments");
        Set<String> current = new HashSet<>(enchantments.getKeys());
        if (current.isEmpty()) return;

        String category = itemCategory(stack, extra.getString("id"));
        if (category == null || ENCHANT_CATEGORIES == null
                || !ENCHANT_CATEGORIES.has(category)) return;

        Set<String> ignoredByPool = ignoredPools(current);
        JsonArray possible = ENCHANT_CATEGORIES.getAsJsonArray(category);
        List<String> missing = new ArrayList<>();
        for (JsonElement entry : possible) {
            String id = entry.getAsString();
            if (id.startsWith("ultimate_") || current.contains(id) || ignoredByPool.contains(id)) continue;
            missing.add(titleCase(id.replace('_', ' ')));
        }
        if (missing.isEmpty()) return;

        MutableText line = Text.literal("Missing: ").formatted(Formatting.RED);
        line.append(Text.literal(String.join(", ", missing)).formatted(Formatting.GRAY));
        lines.add(Text.empty());
        lines.add(line);
    }

    private static Set<String> ignoredPools(Set<String> current) {
        Set<String> ignored = new HashSet<>();
        if (ROOT == null || !ROOT.has("enchant_pools")) return ignored;
        try {
            for (JsonElement poolElement : ROOT.getAsJsonArray("enchant_pools")) {
                Set<String> pool = new HashSet<>();
                for (JsonElement entry : poolElement.getAsJsonArray()) pool.add(entry.getAsString());
                for (String id : pool) {
                    if (current.contains(id)) {
                        ignored.addAll(pool);
                        break;
                    }
                }
            }
        } catch (Exception ignoredException) {
            return ignored;
        }
        return ignored;
    }

    private static String itemCategory(ItemStack stack, String id) {
        String upper = id == null ? "" : id.toUpperCase(Locale.ROOT);
        if (upper.contains("GAUNTLET")) return "GAUNTLET";
        if (upper.contains("LONGSWORD")) return "LONGSWORD";
        if (upper.contains("CARNIVAL_MASK")) return "CARNIVAL MASK";
        if (upper.contains("NECKLACE")) return "NECKLACE";
        if (upper.contains("BRACELET")) return "BRACELET";
        if (upper.contains("GLOVES")) return "GLOVES";
        if (upper.contains("CLOAK")) return "CLOAK";
        if (upper.contains("BELT")) return "BELT";

        var item = stack.getItem();
        if (item instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getSlotType();
            return switch (slot) {
                case HEAD -> "HELMET";
                case CHEST -> "CHESTPLATE";
                case LEGS -> "LEGGINGS";
                case FEET -> "BOOTS";
                default -> null;
            };
        }
        if (item instanceof FishingRodItem) return "FISHING ROD";
        if (item instanceof BowItem || item instanceof CrossbowItem) return "BOW";
        if (item instanceof SwordItem) return "SWORD";
        if (item instanceof PickaxeItem) return upper.contains("DRILL") ? "DRILL" : "PICKAXE";
        if (item instanceof AxeItem) return "AXE";
        if (item instanceof ShovelItem) return "SHOVEL";
        if (item instanceof HoeItem) return "HOE";
        if (upper.contains("FISHING")) return "FISHING WEAPON";
        return null;
    }

    private static NbtCompound getExtraAttributes(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null || component.isEmpty()) return null;
        NbtCompound root = component.copyNbt();
        return root.contains("ExtraAttributes") ? root.getCompound("ExtraAttributes") : root;
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.split(" ")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.toString();
    }

    private static final class EnchantMeta {
        final String id;
        final String loreName;
        final String nbtName;
        final int goodLevel;
        final int maxLevel;
        final int sortType;

        EnchantMeta(String id, String loreName, String nbtName,
                    int goodLevel, int maxLevel, int sortType) {
            this.id = id;
            this.loreName = loreName;
            this.nbtName = nbtName;
            this.goodLevel = goodLevel;
            this.maxLevel = maxLevel;
            this.sortType = sortType;
        }
    }
}
