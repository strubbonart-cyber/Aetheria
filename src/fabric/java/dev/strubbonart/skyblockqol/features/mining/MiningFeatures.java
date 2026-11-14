package dev.strubbonart.skyblockqol.features.mining;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First Fabric-native migration slice for Aetheria's Mining features.
 *
 * This class intentionally keeps the source feature logic self-contained while
 * the old Forge event/configuration framework is replaced. Feature settings
 * and counters are persisted in the Fabric config directory.
 */
public final class MiningFeatures {
    private static final Pattern CHEST = Pattern.compile("You uncovered a treasure chest!");
    private static final Pattern COMPACT = Pattern.compile("COMPACT! You found an? Enchanted Hard Stone!");
    private static final Pattern POWDER = Pattern.compile("(?:Gemstone|Mithril) Powder x([\\d,]+)");
    private static final Pattern GEM = Pattern.compile("\\b(Rough|Flawed|Fine|Flawless) (Ruby|Sapphire|Amber|Amethyst|Jade|Topaz|Jasper|Opal|Citrine|Aquamarine|Peridot|Onyx) Gemstone x([\\d,]+)");
    private static final Pattern PRISTINE = Pattern.compile("PRISTINE!.*?(Flawed|Fine|Flawless) (Ruby|Sapphire|Amber|Amethyst|Jade|Topaz|Jasper|Opal|Citrine|Aquamarine|Peridot|Onyx) Gemstone.*?x([\\d,]+)");
    private static final Pattern DIAMOND_ESSENCE = Pattern.compile("Diamond Essence x([\\d,]+)");
    private static final Pattern GOLD_ESSENCE = Pattern.compile("Gold Essence x([\\d,]+)");
    private static final Pattern OIL = Pattern.compile("Oil Barrel(?: x([\\d,]+))?");
    private static final Pattern ROPE = Pattern.compile("Ascension Rope(?: x([\\d,]+))?");
    private static final Pattern COMPASS = Pattern.compile("Wishing Compass(?: x([\\d,]+))?");
    private static final Pattern HEART = Pattern.compile("Jungle Heart(?: x([\\d,]+))?");
    private static final Pattern EGG = Pattern.compile("Prehistoric Egg(?: x([\\d,]+))?");
    private static final Pattern PICKONIMBUS = Pattern.compile("Pickonimbus 2000(?: x([\\d,]+))?");
    private static final Pattern SLUDGE = Pattern.compile("Sludge Juice(?: x([\\d,]+))?");
    private static final Pattern YOGGIE = Pattern.compile("Yoggie(?: x([\\d,]+))?");
    private static final Pattern ROBOT = Pattern.compile("(?:FTX 3070|Synthetic Heart|Control Switch|Robotron Reflector|Electron Transmitter|Superlite Motor)(?: x([\\d,]+))?");
    private static final Pattern TREASURITE = Pattern.compile("Treasurite(?: x([\\d,]+))?");
    private static final Pattern GOBLIN_EGG = Pattern.compile("(?:Green |Red |Yellow |Blue )?Goblin Egg(?: x([\\d,]+))?");
    private static final Pattern GOLD_COMPACT = Pattern.compile("COMPACT!.*Enchanted Gold");

    private static final String[] FETCHUR_ITEMS = {
            "Yellow Stained Glass x20", "Compass x1", "Mithril x20",
            "Firework Rocket x1", "Wooden Door x1 (any type)",
            "Rabbit's Foot x3", "Superboom TNT x1", "Pumpkin x1",
            "Flint and Steel x1", "Nether Quartz Ore x50", "Red Wool x50"
    };

    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-mining.properties");

    private static boolean initialized;
    private static boolean powderFilterEnabled;
    private static boolean showPowderHud = true;
    private static boolean showPristineHud = true;
    private static boolean showFetchurHud = true;

    private static long gemstonePowder;
    private static long mithrilPowder;
    private static long chests;
    private static long gemstoneDrops;
    private static long pristineProcs;
    private static long pristineGemstones;
    private static long diamondEssence;
    private static long goldEssence;
    private static long oilBarrels;
    private static long ascensionRopes;
    private static long wishingCompasses;
    private static long jungleHearts;
    private static long prehistoricEggs;
    private static long pickonimbus;
    private static long sludgeJuice;
    private static long yoggies;
    private static long robotParts;
    private static long treasurite;
    private static long goblinEggs;
    private static long hardStoneCompacts;
    private static long goldCompacts;
    private static long goldIngots;
    private static long enchantedGold;
    private static long lastGoldOreBreakTime;
    private static int previousGoldInventory = -1;
    private static int previousEnchantedGoldInventory = -1;
    private static long lastSaveTime;

    private MiningFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientReceiveMessageEvents.ALLOW_GAME.register(MiningFeatures::onGameMessage);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick();
            scanGoldInventory(client);
        });
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            if (state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE)
                    || state.isOf(Blocks.NETHER_GOLD_ORE) || state.isOf(Blocks.GOLD_BLOCK)
                    || state.isOf(Blocks.RAW_GOLD_BLOCK)) {
                lastGoldOreBreakTime = System.currentTimeMillis();
            }
        });
        HudRenderCallback.EVENT.register(MiningFeatures::renderHud);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("mining")
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(statusLine()));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("filter").executes(context -> {
                            powderFilterEnabled = !powderFilterEnabled;
                            save();
                            context.getSource().sendFeedback(Text.literal(
                                    "Powder chat filter " + (powderFilterEnabled ? "enabled" : "disabled")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            reset();
                            context.getSource().sendFeedback(Text.literal("Mining counters reset."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("hud").executes(context -> {
                            showPowderHud = !showPowderHud;
                            showPristineHud = showPowderHud;
                            showFetchurHud = showPowderHud;
                            context.getSource().sendFeedback(Text.literal(
                                    "Mining HUDs " + (showPowderHud ? "enabled" : "disabled")));
                            return 1;
                        }))));
    }

    private static boolean onGameMessage(Text message, boolean overlay) {
        String raw = message.getString();
        String clean = stripFormatting(raw).trim();
        if (clean.isEmpty()) return !powderFilterEnabled;

        boolean isPristine = clean.contains("PRISTINE!");
        boolean changed = false;

        if (CHEST.matcher(clean).find()) {
            chests++;
            changed = true;
        }
        if (COMPACT.matcher(clean).find()) {
            hardStoneCompacts++;
            changed = true;
        }
        Matcher matcher = POWDER.matcher(clean);
        if (matcher.find()) {
            long amount = amount(matcher.group(1));
            if (clean.contains("Gemstone Powder")) gemstonePowder += amount;
            else mithrilPowder += amount;
            changed = true;
        }

        matcher = PRISTINE.matcher(clean);
        if (matcher.find()) {
            pristineProcs++;
            pristineGemstones += amount(matcher.group(3));
            changed = true;
        } else if (!isPristine) {
            matcher = GEM.matcher(clean);
            if (matcher.find()) {
                gemstoneDrops += amount(matcher.group(3));
                changed = true;
            }
        }

        changed |= addMatch(clean, DIAMOND_ESSENCE, 0);
        changed |= addMatch(clean, GOLD_ESSENCE, 1);
        changed |= addMatch(clean, OIL, 2);
        changed |= addMatch(clean, ROPE, 3);
        changed |= addMatch(clean, COMPASS, 4);
        changed |= addMatch(clean, HEART, 5);
        changed |= addMatch(clean, EGG, 6);
        changed |= addMatch(clean, PICKONIMBUS, 7);
        changed |= addMatch(clean, SLUDGE, 8);
        changed |= addMatch(clean, YOGGIE, 9);
        changed |= addMatch(clean, ROBOT, 10);
        changed |= addMatch(clean, TREASURITE, 11);
        changed |= addMatch(clean, GOBLIN_EGG, 12);

        if (GOLD_COMPACT.matcher(raw).find()) {
            goldCompacts++;
            changed = true;
        }
        if (changed) save();

        return !shouldHide(clean, raw);
    }

    private static boolean addMatch(String message, Pattern pattern, int counter) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) return false;
        long value = matcher.group(1) == null ? 1 : amount(matcher.group(1));
        switch (counter) {
            case 0 -> diamondEssence += value;
            case 1 -> goldEssence += value;
            case 2 -> oilBarrels += value;
            case 3 -> ascensionRopes += value;
            case 4 -> wishingCompasses += value;
            case 5 -> jungleHearts += value;
            case 6 -> prehistoricEggs += value;
            case 7 -> pickonimbus += value;
            case 8 -> sludgeJuice += value;
            case 9 -> yoggies += value;
            case 10 -> robotParts += value;
            case 11 -> treasurite += value;
            case 12 -> goblinEggs += value;
            default -> { return false; }
        }
        return true;
    }

    private static boolean shouldHide(String clean, String raw) {
        if (!powderFilterEnabled || clean.contains("PRISTINE!")) return false;
        // Only suppress known Crystal Hollows mining reward lines; normal player,
        // party, guild and private messages are not matched by these patterns.
        if (CHEST.matcher(clean).find()) return true;
        if (clean.equals("This chest has already been looted.")) return true;
        if (clean.startsWith("You need a tool with a Breaking Power of ")) return true;
        if (clean.startsWith("COMPACT! You found ")) return false;
        if (clean.contains("Gemstone Powder") || clean.contains("Mithril Powder")) return true;
        if (clean.contains("Diamond Essence") || clean.contains("Gold Essence")) return true;
        Matcher gemMatcher = GEM.matcher(clean);
        if (gemMatcher.find()) return "Rough".equals(gemMatcher.group(1));
        if (OIL.matcher(clean).find() || ROPE.matcher(clean).find()
                || COMPASS.matcher(clean).find() || HEART.matcher(clean).find()
                || EGG.matcher(clean).find() || PICKONIMBUS.matcher(clean).find()
                || SLUDGE.matcher(clean).find() || YOGGIE.matcher(clean).find()
                || ROBOT.matcher(clean).find() || TREASURITE.matcher(clean).find()) return true;
        String compact = clean.toUpperCase(Locale.ROOT);
        if (compact.contains("CHEST LOCKPICKED") || compact.contains("LOOT CHEST COLLECTED")
                || compact.equals("REWARDS") || compact.startsWith("▬")) return true;
        return false;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime > 30_000L) save();
    }

    private static void scanGoldInventory(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            previousGoldInventory = previousEnchantedGoldInventory = -1;
            return;
        }
        int ingotCount = 0;
        int enchantedCount = 0;
        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
            if (displayName.contains("enchanted gold")) {
                enchantedCount += stack.getCount();
            } else if (stack.isOf(Items.GOLD_INGOT)) {
                ingotCount += stack.getCount();
            }
        }
        if (previousGoldInventory < 0 || previousEnchantedGoldInventory < 0) {
            previousGoldInventory = ingotCount;
            previousEnchantedGoldInventory = enchantedCount;
            return;
        }
        int ingotDelta = Math.max(0, ingotCount - previousGoldInventory);
        int enchantedDelta = Math.max(0, enchantedCount - previousEnchantedGoldInventory);
        if (System.currentTimeMillis() - lastGoldOreBreakTime <= 20_000L) {
            if (ingotDelta > 0) goldIngots += ingotDelta;
            if (enchantedDelta > 0) enchantedGold += enchantedDelta;
        }
        previousGoldInventory = ingotCount;
        previousEnchantedGoldInventory = enchantedCount;
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden
                || client.currentScreen != null && client.currentScreen.getTitle().getString().equals("Chat")) {
            return;
        }

        int y = 8;
        if (showFetchurHud) {
            context.drawTextWithShadow(client.textRenderer, "Fetchur: " + fetchurItem(), 8, y, 0x55FFFF);
            y += 11;
        }
        if (showPowderHud) {
            context.drawTextWithShadow(client.textRenderer,
                    "Powder: " + format(gemstonePowder) + " Gemstone | " + format(mithrilPowder) + " Mithril",
                    8, y, 0xFFFFFF);
            y += 11;
            context.drawTextWithShadow(client.textRenderer,
                    "Chests: " + format(chests) + " | Gems: " + format(gemstoneDrops)
                            + " | Hard Stone compacts: " + format(hardStoneCompacts),
                    8, y, 0xFFFFFF);
            y += 11;
            context.drawTextWithShadow(client.textRenderer,
                    "Essence: " + format(diamondEssence) + " Diamond, " + format(goldEssence) + " Gold",
                    8, y, 0xAAAAFF);
            y += 11;
        }
        if (showPristineHud) {
            context.drawTextWithShadow(client.textRenderer,
                    "Pristine: " + format(pristineGemstones) + " gems | " + format(pristineProcs) + " procs",
                    8, y, 0xFF55FF);
            y += 11;
        }
        if (showPowderHud) {
            context.drawTextWithShadow(client.textRenderer,
                    "Gold Tracker: " + format(goldIngots) + " ingots | " + format(enchantedGold)
                            + " enchanted | " + format(goldCompacts) + " compacts",
                    8, y, 0xFFFF55);
        }
    }

    private static String fetchurItem() {
        long day = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
        return FETCHUR_ITEMS[(int) Math.floorMod(day, FETCHUR_ITEMS.length)];
    }

    private static String statusLine() {
        return "Mining counters | Powder " + format(gemstonePowder) + " Gemstone / "
                + format(mithrilPowder) + " Mithril | Chests " + format(chests)
                + " | Pristine procs " + format(pristineProcs)
                + " | Gold " + format(goldIngots) + " ingots / " + format(enchantedGold) + " enchanted"
                + " | Filter " + (powderFilterEnabled ? "ON" : "OFF");
    }

    private static void reset() {
        gemstonePowder = mithrilPowder = chests = gemstoneDrops = 0;
        pristineProcs = pristineGemstones = diamondEssence = goldEssence = 0;
        oilBarrels = ascensionRopes = wishingCompasses = jungleHearts = 0;
        prehistoricEggs = pickonimbus = sludgeJuice = yoggies = 0;
        robotParts = treasurite = goblinEggs = hardStoneCompacts = goldCompacts = 0;
        goldIngots = enchantedGold = 0;
        previousGoldInventory = previousEnchantedGoldInventory = -1;
        lastGoldOreBreakTime = 0;
        save();
    }

    private static void load() {
        if (!Files.exists(DATA_FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(DATA_FILE)) {
            p.load(in);
            powderFilterEnabled = Boolean.parseBoolean(p.getProperty("powderFilterEnabled", "false"));
            showPowderHud = Boolean.parseBoolean(p.getProperty("showPowderHud", "true"));
            showPristineHud = Boolean.parseBoolean(p.getProperty("showPristineHud", "true"));
            showFetchurHud = Boolean.parseBoolean(p.getProperty("showFetchurHud", "true"));
            gemstonePowder = value(p, "gemstonePowder");
            mithrilPowder = value(p, "mithrilPowder");
            chests = value(p, "chests");
            gemstoneDrops = value(p, "gemstoneDrops");
            pristineProcs = value(p, "pristineProcs");
            pristineGemstones = value(p, "pristineGemstones");
            diamondEssence = value(p, "diamondEssence");
            goldEssence = value(p, "goldEssence");
            oilBarrels = value(p, "oilBarrels");
            ascensionRopes = value(p, "ascensionRopes");
            wishingCompasses = value(p, "wishingCompasses");
            jungleHearts = value(p, "jungleHearts");
            prehistoricEggs = value(p, "prehistoricEggs");
            pickonimbus = value(p, "pickonimbus");
            sludgeJuice = value(p, "sludgeJuice");
            yoggies = value(p, "yoggies");
            robotParts = value(p, "robotParts");
            treasurite = value(p, "treasurite");
            goblinEggs = value(p, "goblinEggs");
            hardStoneCompacts = value(p, "hardStoneCompacts");
            goldCompacts = value(p, "goldCompacts");
            goldIngots = value(p, "goldIngots");
            enchantedGold = value(p, "enchantedGold");
        } catch (IOException ignored) {
            // A corrupt or unavailable stats file must not prevent the client from starting.
        }
    }

    private static long value(Properties p, String key) {
        try { return Long.parseLong(p.getProperty(key, "0")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("powderFilterEnabled", Boolean.toString(powderFilterEnabled));
        p.setProperty("showPowderHud", Boolean.toString(showPowderHud));
        p.setProperty("showPristineHud", Boolean.toString(showPristineHud));
        p.setProperty("showFetchurHud", Boolean.toString(showFetchurHud));
        p.setProperty("gemstonePowder", Long.toString(gemstonePowder));
        p.setProperty("mithrilPowder", Long.toString(mithrilPowder));
        p.setProperty("chests", Long.toString(chests));
        p.setProperty("gemstoneDrops", Long.toString(gemstoneDrops));
        p.setProperty("pristineProcs", Long.toString(pristineProcs));
        p.setProperty("pristineGemstones", Long.toString(pristineGemstones));
        p.setProperty("diamondEssence", Long.toString(diamondEssence));
        p.setProperty("goldEssence", Long.toString(goldEssence));
        p.setProperty("oilBarrels", Long.toString(oilBarrels));
        p.setProperty("ascensionRopes", Long.toString(ascensionRopes));
        p.setProperty("wishingCompasses", Long.toString(wishingCompasses));
        p.setProperty("jungleHearts", Long.toString(jungleHearts));
        p.setProperty("prehistoricEggs", Long.toString(prehistoricEggs));
        p.setProperty("pickonimbus", Long.toString(pickonimbus));
        p.setProperty("sludgeJuice", Long.toString(sludgeJuice));
        p.setProperty("yoggies", Long.toString(yoggies));
        p.setProperty("robotParts", Long.toString(robotParts));
        p.setProperty("treasurite", Long.toString(treasurite));
        p.setProperty("goblinEggs", Long.toString(goblinEggs));
        p.setProperty("hardStoneCompacts", Long.toString(hardStoneCompacts));
        p.setProperty("goldCompacts", Long.toString(goldCompacts));
        p.setProperty("goldIngots", Long.toString(goldIngots));
        p.setProperty("enchantedGold", Long.toString(enchantedGold));
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(DATA_FILE)) {
                p.store(out, "Skyblock QoL Fabric Mining Statistics");
            }
            lastSaveTime = System.currentTimeMillis();
        } catch (IOException ignored) {
            // Persistence is best-effort; gameplay features remain available.
        }
    }

    private static long amount(String value) {
        try { return Long.parseLong(value.replace(",", "")); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private static String stripFormatting(String input) {
        return input.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
