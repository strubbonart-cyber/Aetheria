package dev.strubbonart.skyblockqol.features.mining;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;
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

    private static final Pattern ALREADY_LOOTED = Pattern.compile("This chest has already been looted\\.");
    private static final Pattern BREAKING_POWER = Pattern.compile("You need a tool with a Breaking Power of .+? to mine .+");
    private static final Pattern POWDER_LINE = Pattern.compile("(?:Gemstone|Mithril) Powder x([\\d,]+)");
    private static final Pattern ESSENCE_LINE = Pattern.compile("(?:Diamond|Gold) Essence x([\\d,]+)");
    private static final Pattern GEM_LINE = Pattern.compile("\\b(Rough|Flawed|Fine|Flawless) (Ruby|Sapphire|Amber|Amethyst|Jade|Topaz|Jasper|Opal|Citrine|Aquamarine|Peridot|Onyx) Gemstone x([\\d,]+)");


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
    private static boolean hideChestOpen = true;
    private static boolean hideAlreadyLooted = true;
    private static boolean hideBreakingPower = true;
    private static boolean hideCompact = false;
    private static boolean hideRewardWrappers = true;
    private static boolean hidePowder = true;
    private static int powderThreshold;
    private static boolean hideEssence = true;
    private static int essenceThreshold;
    private static boolean hideGemstones = true;
    private static String gemstoneTierFilter = "Hide Rough";
    private static boolean hideGoblinEggs;
    private static boolean hideOilBarrel = true;
    private static boolean hideAscensionRope = true;
    private static boolean hideWishingCompass = true;
    private static boolean hideJungleHeart = true;
    private static boolean hidePrehistoricEgg = true;
    private static boolean hidePickonimbus = true;
    private static boolean hideSludgeJuice = true;
    private static boolean hideYoggie = true;
    private static boolean hideRobotParts = true;
    private static boolean hideTreasurite = true;
    private static boolean showPowderHud = true;
    private static boolean showPristineHud = true;
    private static boolean showFetchurHud = true;
    private static boolean showGoldHud = true;
    private static boolean showPowderGemBreakdown = true;
    private static boolean showPristineGemBreakdown = true;
    private enum HudPanel { FETCHUR, POWDER, PRISTINE, GOLD }

    private static final class HudLayout {
        private int x;
        private int y;
        private float scale;

        private HudLayout(int x, int y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }

    private static final EnumMap<HudPanel, HudLayout> HUD_LAYOUTS = createDefaultHudLayouts();
    // Retained for compatibility with the original /mining hud position and scale commands.
    private static int hudX = 8;
    private static int hudY = 24;
    private static float hudScale = 1.0F;

    private static long gemstonePowder;
    private static long mithrilPowder;
    private static long chests;
    private static long gemstoneDrops;
    private static final Map<String, Long> powderGemstones = new HashMap<>();
    private static final Map<String, Long> pristineGemstonesByQuality = new HashMap<>();
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
    private static boolean goldOnlyWhileMining = true;
    private static boolean goldDisplayEnchanted;
    private static long goldActiveTimeMs;
    private static long goldSessionTimeMs;
    private static long goldLastActivityTime;
    private static long goldLastTickTime = System.currentTimeMillis();
    private static boolean goldTimerStarted;
    private static boolean goldTimerRunning;
    private static long lastGoldOreBreakTime;
    private static int previousGoldInventory = -1;
    private static int previousEnchantedGoldInventory = -1;
    private static long lastSaveTime;
    private static boolean trackerPaused;
    private static boolean sessionStarted;
    private static long lastActivityTime;
    private static long lastTickTime = System.currentTimeMillis();
    private static long activeTimeMs;

    private MiningFeatures() {}

    public static void setMiningFeatureEnabled(String feature, boolean value) {
        switch (feature) {
            case "pickobulus" -> PickobulusPreview.setEnabled(value);
            case "commission" -> CommissionHighlight.setEnabled(value);
            case "hotm" -> HotmPowderTooltip.setEnabled(value);
            default -> { return; }
        }
        save();
    }

    public static boolean isGoldOnlyWhileMining() { return goldOnlyWhileMining; }

    public static void setGoldOnlyWhileMining(boolean value) {
        goldOnlyWhileMining = value;
        save();
    }

    public static boolean isGoldDisplayEnchanted() { return goldDisplayEnchanted; }

    public static void toggleGoldDisplayUnit() {
        goldDisplayEnchanted = !goldDisplayEnchanted;
        save();
    }

    public static boolean isGemstoneBreakdownEnabled(String tracker) {
        return "pristine".equals(tracker) ? showPristineGemBreakdown : showPowderGemBreakdown;
    }

    public static void setGemstoneBreakdownEnabled(String tracker, boolean value) {
        if ("pristine".equals(tracker)) showPristineGemBreakdown = value;
        else showPowderGemBreakdown = value;
        save();
    }

    public static int getPowderThreshold() {
        return powderThreshold;
    }

    public static int getEssenceThreshold() {
        return essenceThreshold;
    }

    public static void adjustPowderThreshold(int delta) {
        powderThreshold = Math.max(0, Math.min(60_000, powderThreshold + delta));
        save();
    }

    public static void adjustEssenceThreshold(int delta) {
        essenceThreshold = Math.max(0, Math.min(20, essenceThreshold + delta));
        save();
    }

    public static boolean isTrackerPaused() {
        return trackerPaused;
    }

    public static void setTrackerPaused(boolean value) {
        trackerPaused = value;
        long now = System.currentTimeMillis();
        lastTickTime = now;
        if (!value) {
            sessionStarted = true;
            lastActivityTime = now;
        }
        save();
    }

    public static boolean isPowderFilterEnabled() {
        return powderFilterEnabled;
    }

    public static void setPowderFilterEnabled(boolean value) {
        powderFilterEnabled = value;
        save();
    }

    public static boolean isHudPanelEnabled(String panel) {
        return switch (panel) {
            case "fetchur" -> showFetchurHud;
            case "powder" -> showPowderHud;
            case "pristine" -> showPristineHud;
            case "gold" -> showGoldHud;
            default -> false;
        };
    }

    public static void setHudPanelEnabled(String panel, boolean value) {
        switch (panel) {
            case "fetchur" -> showFetchurHud = value;
            case "powder" -> showPowderHud = value;
            case "pristine" -> showPristineHud = value;
            case "gold" -> showGoldHud = value;
            default -> { return; }
        }
        save();
    }

    private static EnumMap<HudPanel, HudLayout> createDefaultHudLayouts() {
        EnumMap<HudPanel, HudLayout> layouts = new EnumMap<>(HudPanel.class);
        layouts.put(HudPanel.FETCHUR, new HudLayout(8, 8, 1.0F));
        layouts.put(HudPanel.POWDER, new HudLayout(8, 24, 1.0F));
        layouts.put(HudPanel.PRISTINE, new HudLayout(8, 200, 1.0F));
        layouts.put(HudPanel.GOLD, new HudLayout(8, 345, 1.0F));
        return layouts;
    }

    private static HudLayout layout(String panel) {
        HudPanel key;
        try {
            key = HudPanel.valueOf(panel.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            key = HudPanel.POWDER;
        }
        return HUD_LAYOUTS.get(key);
    }

    public static int getHudX() { return getHudX("powder"); }
    public static int getHudY() { return getHudY("powder"); }
    public static float getHudScale() { return getHudScale("powder"); }
    public static int getHudX(String panel) { return layout(panel).x; }
    public static int getHudY(String panel) { return layout(panel).y; }
    public static float getHudScale(String panel) { return layout(panel).scale; }

    public static void updateHudLayout(String panel, int x, int y, float scale) {
        HudLayout value = layout(panel);
        value.x = Math.max(0, Math.min(5000, x));
        value.y = Math.max(0, Math.min(5000, y));
        value.scale = Math.max(0.5F, Math.min(3.0F, scale));
        syncLegacyHudLayout();
    }

    public static void saveHudLayout() {
        syncLegacyHudLayout();
        save();
    }

    public static void adjustHudLayout(int dx, int dy, float scaleDelta) {
        adjustHudLayout("powder", dx, dy, scaleDelta);
    }

    public static void adjustHudLayout(String panel, int dx, int dy, float scaleDelta) {
        HudLayout value = layout(panel);
        value.x = Math.max(0, Math.min(5000, value.x + dx));
        value.y = Math.max(0, Math.min(5000, value.y + dy));
        value.scale = Math.max(0.5F, Math.min(3.0F,
                Math.round((value.scale + scaleDelta) * 10.0F) / 10.0F));
        syncLegacyHudLayout();
        save();
    }

    public static void resetHudLayout(String panel) {
        HudPanel key;
        try {
            key = HudPanel.valueOf(panel.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            key = HudPanel.POWDER;
        }
        HUD_LAYOUTS.put(key, createDefaultHudLayouts().get(key));
        syncLegacyHudLayout();
        save();
    }

    public static void resetHudLayout() {
        HUD_LAYOUTS.clear();
        HUD_LAYOUTS.putAll(createDefaultHudLayouts());
        syncLegacyHudLayout();
        save();
    }

    private static void syncLegacyHudLayout() {
        HudLayout powder = HUD_LAYOUTS.get(HudPanel.POWDER);
        hudX = powder.x;
        hudY = powder.y;
        hudScale = powder.scale;
    }

    private static void setAllHudPositions(int x, int y) {
        HUD_LAYOUTS.put(HudPanel.FETCHUR, new HudLayout(x, y, HUD_LAYOUTS.get(HudPanel.FETCHUR).scale));
        HUD_LAYOUTS.put(HudPanel.POWDER, new HudLayout(x, y + 16, HUD_LAYOUTS.get(HudPanel.POWDER).scale));
        HUD_LAYOUTS.put(HudPanel.PRISTINE, new HudLayout(x, y + 192, HUD_LAYOUTS.get(HudPanel.PRISTINE).scale));
        HUD_LAYOUTS.put(HudPanel.GOLD, new HudLayout(x, y + 337, HUD_LAYOUTS.get(HudPanel.GOLD).scale));
        syncLegacyHudLayout();
        save();
    }

    private static void setAllHudScales(float scale) {
        float bounded = Math.max(0.5F, Math.min(3.0F, scale));
        HUD_LAYOUTS.values().forEach(value -> value.scale = bounded);
        syncLegacyHudLayout();
        save();
    }

    public static boolean getFilterOption(String option) {
        return switch (option) {
            case "chest" -> hideChestOpen;
            case "already-looted" -> hideAlreadyLooted;
            case "breaking-power" -> hideBreakingPower;
            case "compact" -> hideCompact;
            case "wrappers" -> hideRewardWrappers;
            case "powder" -> hidePowder;
            case "essence" -> hideEssence;
            case "gemstones" -> hideGemstones;
            case "goblin-eggs" -> hideGoblinEggs;
            case "special-drops" -> hideOilBarrel && hideAscensionRope && hideWishingCompass
                    && hideJungleHeart && hidePrehistoricEgg && hidePickonimbus
                    && hideSludgeJuice && hideYoggie && hideRobotParts && hideTreasurite;
            default -> false;
        };
    }

    public static void setFilterOption(String option, boolean value) {
        switch (option) {
            case "chest" -> hideChestOpen = value;
            case "already-looted" -> hideAlreadyLooted = value;
            case "breaking-power" -> hideBreakingPower = value;
            case "compact" -> hideCompact = value;
            case "wrappers" -> hideRewardWrappers = value;
            case "powder" -> hidePowder = value;
            case "essence" -> hideEssence = value;
            case "gemstones" -> hideGemstones = value;
            case "goblin-eggs" -> hideGoblinEggs = value;
            case "special-drops" -> {
                hideOilBarrel = value;
                hideAscensionRope = value;
                hideWishingCompass = value;
                hideJungleHeart = value;
                hidePrehistoricEgg = value;
                hidePickonimbus = value;
                hideSludgeJuice = value;
                hideYoggie = value;
                hideRobotParts = value;
                hideTreasurite = value;
            }
            default -> { return; }
        }
        save();
    }

    public static String getGemstoneTierFilter() {
        return gemstoneTierFilter;
    }

    public static void cycleGemstoneTierFilter() {
        gemstoneTierFilter = switch (gemstoneTierFilter) {
            case "Show All" -> "Hide Rough";
            case "Hide Rough" -> "Hide Rough & Flawed";
            case "Hide Rough & Flawed" -> "Hide All";
            default -> "Show All";
        };
        save();
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientReceiveMessageEvents.ALLOW_GAME.register(MiningFeatures::onGameMessage);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick();
            scanGoldInventory(client);
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!isGoldArea()) return ActionResult.PASS;
            var state = world.getBlockState(pos);
            if (state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE)
                    || state.isOf(Blocks.NETHER_GOLD_ORE) || state.isOf(Blocks.GOLD_BLOCK)
                    || state.isOf(Blocks.RAW_GOLD_BLOCK)) {
                lastGoldOreBreakTime = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });
        HudRenderCallback.EVENT.register(MiningFeatures::renderHud);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("mining")
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(statusLine()));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("filter")
                                .executes(context -> {
                                    powderFilterEnabled = !powderFilterEnabled;
                                    save();
                                    context.getSource().sendFeedback(Text.literal(
                                            "Powder chat filter " + (powderFilterEnabled ? "enabled" : "disabled")));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("chest").executes(context -> toggleFilterSetting("chest", context.getSource())))
                                .then(ClientCommandManager.literal("already-looted").executes(context -> toggleFilterSetting("already-looted", context.getSource())))
                                .then(ClientCommandManager.literal("breaking-power").executes(context -> toggleFilterSetting("breaking-power", context.getSource())))
                                .then(ClientCommandManager.literal("compact").executes(context -> toggleFilterSetting("compact", context.getSource())))
                                .then(ClientCommandManager.literal("wrappers").executes(context -> toggleFilterSetting("wrappers", context.getSource())))
                                .then(ClientCommandManager.literal("powder").executes(context -> toggleFilterSetting("powder", context.getSource())))
                                .then(ClientCommandManager.literal("essence").executes(context -> toggleFilterSetting("essence", context.getSource())))
                                .then(ClientCommandManager.literal("gemstones").executes(context -> toggleFilterSetting("gemstones", context.getSource())))
                                .then(ClientCommandManager.literal("goblin-eggs").executes(context -> toggleFilterSetting("goblin-eggs", context.getSource())))
                                .then(ClientCommandManager.literal("special-drops").executes(context -> toggleFilterSetting("special-drops", context.getSource())))
                                .then(ClientCommandManager.literal("tiers")
                                        .then(ClientCommandManager.literal("show-all").executes(context -> setGemstoneTier("Show All", context.getSource())))
                                        .then(ClientCommandManager.literal("hide-rough").executes(context -> setGemstoneTier("Hide Rough", context.getSource())))
                                        .then(ClientCommandManager.literal("hide-rough-flawed").executes(context -> setGemstoneTier("Hide Rough & Flawed", context.getSource())))
                                        .then(ClientCommandManager.literal("hide-all").executes(context -> setGemstoneTier("Hide All", context.getSource())))))
                        .then(ClientCommandManager.literal("threshold")
                                .then(ClientCommandManager.literal("powder")
                                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(0, 60000))
                                                .executes(context -> {
                                                    powderThreshold = IntegerArgumentType.getInteger(context, "amount");
                                                    save();
                                                    context.getSource().sendFeedback(Text.literal("Powder hide threshold: " + powderThreshold));
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("essence")
                                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(0, 20))
                                                .executes(context -> {
                                                    essenceThreshold = IntegerArgumentType.getInteger(context, "amount");
                                                    save();
                                                    context.getSource().sendFeedback(Text.literal("Essence hide threshold: " + essenceThreshold));
                                                    return 1;
                                                }))))
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            reset();
                            context.getSource().sendFeedback(Text.literal("Mining counters reset."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("pause").executes(context -> {
                            trackerPaused = true;
                            save();
                            context.getSource().sendFeedback(Text.literal("Mining trackers paused."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("resume").executes(context -> {
                            trackerPaused = false;
                            sessionStarted = true;
                            lastActivityTime = System.currentTimeMillis();
                            lastTickTime = lastActivityTime;
                            save();
                            context.getSource().sendFeedback(Text.literal("Mining trackers resumed."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("hud")
                                .executes(context -> {
                                    boolean next = !(showPowderHud || showPristineHud || showFetchurHud || showGoldHud);
                                    showPowderHud = next;
                                    showPristineHud = next;
                                    showFetchurHud = next;
                                    showGoldHud = next;
                                    save();
                                    context.getSource().sendFeedback(Text.literal(
                                            "All Mining HUD panels " + (next ? "enabled" : "disabled")));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("fetchur").executes(context -> {
                                    showFetchurHud = !showFetchurHud;
                                    save();
                                    context.getSource().sendFeedback(Text.literal("Fetchur HUD " + (showFetchurHud ? "enabled" : "disabled")));
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("powder").executes(context -> {
                                    showPowderHud = !showPowderHud;
                                    save();
                                    context.getSource().sendFeedback(Text.literal("Powder HUD " + (showPowderHud ? "enabled" : "disabled")));
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("pristine").executes(context -> {
                                    showPristineHud = !showPristineHud;
                                    save();
                                    context.getSource().sendFeedback(Text.literal("Pristine HUD " + (showPristineHud ? "enabled" : "disabled")));
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("gold").executes(context -> {
                                    showGoldHud = !showGoldHud;
                                    save();
                                    context.getSource().sendFeedback(Text.literal("Gold HUD " + (showGoldHud ? "enabled" : "disabled")));
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("position")
                                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0, 5000))
                                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0, 5000))
                                                        .executes(context -> {
                                                            setAllHudPositions(IntegerArgumentType.getInteger(context, "x"),
                                                                    IntegerArgumentType.getInteger(context, "y"));
                                                            context.getSource().sendFeedback(Text.literal("Mining HUD panels repositioned."));
                                                            return 1;
                                                        }))))
                                .then(ClientCommandManager.literal("scale")
                                        .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.5F, 3.0F))
                                                .executes(context -> {
                                                    setAllHudScales(FloatArgumentType.getFloat(context, "value"));
                                                    context.getSource().sendFeedback(Text.literal("Mining HUD panel scale updated."));
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("reset").executes(context -> {
                                    resetHudLayout();
                                    showPowderHud = showPristineHud = showFetchurHud = showGoldHud = true;
                                    save();
                                    context.getSource().sendFeedback(Text.literal("Mining HUD settings reset."));
                                    return 1;
                                })))));
    }

    private static boolean onGameMessage(Text message, boolean overlay) {
        String raw = message.getString();
        String clean = stripFormatting(raw).trim();
        if (clean.isEmpty()) return !powderFilterEnabled;

        boolean inHollows = isCrystalHollows();
        boolean isPristine = clean.contains("PRISTINE!");
        boolean changed = false;

        // Powder, chest, gemstone, Pristine and Crystal Hollows reward stats are
        // intentionally limited to Crystal Hollows, matching the original tracker.
        if (inHollows) {
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
                long count = amount(matcher.group(1));
                if (clean.contains("Gemstone Powder")) gemstonePowder += count;
                else mithrilPowder += count;
                changed = true;
            }

            matcher = PRISTINE.matcher(clean);
            if (matcher.find()) {
                pristineProcs++;
                long count = amount(matcher.group(3));
                pristineGemstones += count;
                addGemstoneCount(pristineGemstonesByQuality, matcher.group(1), matcher.group(2), count);
                changed = true;
            } else if (!isPristine) {
                matcher = GEM.matcher(clean);
                if (matcher.find()) {
                    long count = amount(matcher.group(3));
                    gemstoneDrops += count;
                    addGemstoneCount(powderGemstones, matcher.group(1), matcher.group(2), count);
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
        }

        // Gold is available in both the Dwarven Mines and Crystal Hollows.
        if (GOLD_COMPACT.matcher(raw).find() && isGoldArea()
                && (!goldOnlyWhileMining || System.currentTimeMillis() - lastGoldOreBreakTime <= 20_000L)) {
            goldCompacts++;
            markGoldActivity(System.currentTimeMillis());
            changed = true;
        }
        if (changed) {
            markActivity();
            save();
        }

        return !shouldHide(clean, raw);
    }

    private static void addGemstoneCount(Map<String, Long> counts, String quality, String gemstone, long amount) {
        if (quality == null || gemstone == null || amount <= 0) return;
        String key = quality + "_" + gemstone;
        counts.merge(key, amount, Long::sum);
    }

    private static long gemCount(Map<String, Long> counts, String quality, String gem) {
        return counts.getOrDefault(quality + "_" + gem, 0L);
    }

    private static String compactPowderGemLine(String gem) {
        long rough = gemCount(powderGemstones, "Rough", gem);
        long flawed = gemCount(powderGemstones, "Flawed", gem);
        long fine = gemCount(powderGemstones, "Fine", gem);
        long flawless = gemCount(powderGemstones, "Flawless", gem);
        long base = rough + flawed * 80L + fine * 6_400L + flawless * 512_000L;
        if (base == 0L) return null;
        long[] tiers = {base / 512_000L, (base % 512_000L) / 6_400L,
                (base % 6_400L) / 80L, base % 80L};
        return gem + " F/Fn/Fd/R " + tiers[0] + "/" + tiers[1] + "/" + tiers[2] + "/" + tiers[3];
    }

    private static String compactPristineGemLine(String gem) {
        long flawed = gemCount(pristineGemstonesByQuality, "Flawed", gem);
        long fine = gemCount(pristineGemstonesByQuality, "Fine", gem);
        long flawless = gemCount(pristineGemstonesByQuality, "Flawless", gem);
        long base = flawed + fine * 80L + flawless * 6_400L;
        if (base == 0L) return null;
        long[] tiers = {base / 6_400L, (base % 6_400L) / 80L, base % 80L};
        return gem + " F/Fn/Fd " + tiers[0] + "/" + tiers[1] + "/" + tiers[2];
    }

    private static void addGemstoneBreakdownLines(DrawContext context, MinecraftClient client, int y,
                                                   Map<String, Long> counts, boolean pristine) {
        String[] gems = {"Ruby", "Sapphire", "Amber", "Amethyst", "Jade", "Topaz",
                "Jasper", "Opal", "Citrine", "Aquamarine", "Peridot", "Onyx"};
        for (String gem : gems) {
            String line = pristine ? compactPristineGemLine(gem) : compactPowderGemLine(gem);
            if (line == null) continue;
            context.drawTextWithShadow(client.textRenderer, line, 0, y, pristine ? 0xFFFF88FF : 0xFFB8E8FF);
            y += 10;
        }
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
        if (!powderFilterEnabled || !isCrystalHollows() || clean.contains("PRISTINE!")) return false;
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("party >") || lower.startsWith("guild >")
                || lower.startsWith("officer >") || lower.startsWith("co-op >")
                || lower.startsWith("from ") || lower.startsWith("to ")) return false;

        if (hideRewardWrappers && (lower.contains("chest lockpicked")
                || lower.contains("loot chest collected") || lower.equals("rewards")
                || clean.startsWith("▬"))) return true;
        if (CHEST.matcher(clean).find()) return hideChestOpen;
        if (ALREADY_LOOTED.matcher(clean).find()) return hideAlreadyLooted;
        if (BREAKING_POWER.matcher(clean).find()) return hideBreakingPower;
        if (COMPACT.matcher(clean).find()) return hideCompact;

        Matcher matcher = POWDER_LINE.matcher(clean);
        if (matcher.find() && hidePowder) {
            int count = (int) Math.min(Integer.MAX_VALUE, amount(matcher.group(1)));
            return powderThreshold == 0 || count < powderThreshold;
        }

        matcher = ESSENCE_LINE.matcher(clean);
        if (matcher.find() && hideEssence) {
            int count = (int) Math.min(Integer.MAX_VALUE, amount(matcher.group(1)));
            return essenceThreshold == 0 || count < essenceThreshold;
        }

        matcher = GEM_LINE.matcher(clean);
        if (matcher.find() && hideGemstones) {
            String tier = matcher.group(1);
            return switch (gemstoneTierFilter) {
                case "Show All" -> false;
                case "Hide All" -> true;
                case "Hide Rough" -> tier.equals("Rough");
                case "Hide Rough & Flawed" -> tier.equals("Rough") || tier.equals("Flawed");
                default -> false;
            };
        }

        if (OIL.matcher(clean).find()) return hideOilBarrel;
        if (ROPE.matcher(clean).find()) return hideAscensionRope;
        if (COMPASS.matcher(clean).find()) return hideWishingCompass;
        if (HEART.matcher(clean).find()) return hideJungleHeart;
        if (EGG.matcher(clean).find()) return hidePrehistoricEgg;
        if (PICKONIMBUS.matcher(clean).find()) return hidePickonimbus;
        if (SLUDGE.matcher(clean).find()) return hideSludgeJuice;
        if (YOGGIE.matcher(clean).find()) return hideYoggie;
        if (ROBOT.matcher(clean).find()) return hideRobotParts;
        if (TREASURITE.matcher(clean).find()) return hideTreasurite;
        if (GOBLIN_EGG.matcher(clean).find()) return hideGoblinEggs;
        return false;
    }

    private static boolean isCrystalHollows() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        ScoreboardObjective objective = client.world.getScoreboard()
                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return false;
        for (ScoreboardEntry entry : client.world.getScoreboard().getScoreboardEntries(objective)) {
            if (entry.hidden()) continue;
            String line;
            if (entry.display() != null) {
                line = entry.display().getString();
            } else {
                Team team = client.world.getScoreboard().getScoreHolderTeam(entry.owner());
                line = team == null ? entry.name().getString()
                        : team.decorateName(entry.name()).getString();
            }
            if (stripFormatting(line).toLowerCase(Locale.ROOT).contains("crystal hollows")) return true;
        }
        return false;
    }

    private static int toggleFilterSetting(String setting, FabricClientCommandSource source) {
        boolean value;
        switch (setting) {
            case "chest": hideChestOpen = !hideChestOpen; value = hideChestOpen; break;
            case "already-looted": hideAlreadyLooted = !hideAlreadyLooted; value = hideAlreadyLooted; break;
            case "breaking-power": hideBreakingPower = !hideBreakingPower; value = hideBreakingPower; break;
            case "compact": hideCompact = !hideCompact; value = hideCompact; break;
            case "wrappers": hideRewardWrappers = !hideRewardWrappers; value = hideRewardWrappers; break;
            case "powder": hidePowder = !hidePowder; value = hidePowder; break;
            case "essence": hideEssence = !hideEssence; value = hideEssence; break;
            case "gemstones": hideGemstones = !hideGemstones; value = hideGemstones; break;
            case "goblin-eggs": hideGoblinEggs = !hideGoblinEggs; value = hideGoblinEggs; break;
            case "special-drops":
                hideOilBarrel = !hideOilBarrel;
                hideAscensionRope = hideOilBarrel;
                hideWishingCompass = hideOilBarrel;
                hideJungleHeart = hideOilBarrel;
                hidePrehistoricEgg = hideOilBarrel;
                hidePickonimbus = hideOilBarrel;
                hideSludgeJuice = hideOilBarrel;
                hideYoggie = hideOilBarrel;
                hideRobotParts = hideOilBarrel;
                hideTreasurite = hideOilBarrel;
                value = hideOilBarrel;
                break;
            default: source.sendFeedback(Text.literal("Unknown filter setting.")); return 0;
        }
        save();
        source.sendFeedback(Text.literal(setting + " hide setting " + (value ? "enabled" : "disabled")));
        return 1;
    }

    private static int setGemstoneTier(String tier, FabricClientCommandSource source) {
        gemstoneTierFilter = tier;
        save();
        source.sendFeedback(Text.literal("Gemstone tier filter: " + tier));
        return 1;
    }

    private static void markActivity() {
        long now = System.currentTimeMillis();
        sessionStarted = true;
        lastActivityTime = now;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        long delta = Math.max(0L, Math.min(1_000L, now - lastTickTime));
        lastTickTime = now;
        if (!trackerPaused && sessionStarted && now - lastActivityTime <= 120_000L) {
            activeTimeMs += delta;
        }

        long goldDelta = Math.max(0L, Math.min(1_000L, now - goldLastTickTime));
        goldLastTickTime = now;
        if (goldTimerRunning) {
            if (now - goldLastActivityTime <= 10_000L && isGoldArea()) {
                goldActiveTimeMs += goldDelta;
                goldSessionTimeMs += goldDelta;
            } else {
                goldTimerRunning = false;
            }
        }
        if (now - lastSaveTime > 30_000L) save();
    }

    private static long perHour(long amount) {
        if (activeTimeMs <= 0L) return 0L;
        return Math.round(amount * 3_600_000.0 / activeTimeMs);
    }

    private static String duration(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remaining = seconds % 60L;
        return hours > 0L
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remaining)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, remaining);
    }

    private static boolean isGoldArea() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        ScoreboardObjective objective = client.world.getScoreboard()
                .getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return false;
        for (ScoreboardEntry entry : client.world.getScoreboard().getScoreboardEntries(objective)) {
            if (entry.hidden()) continue;
            String line;
            if (entry.display() != null) {
                line = entry.display().getString();
            } else {
                Team team = client.world.getScoreboard().getScoreHolderTeam(entry.owner());
                line = team == null ? entry.name().getString()
                        : team.decorateName(entry.name()).getString();
            }
            String clean = stripFormatting(line).toLowerCase(Locale.ROOT);
            if (clean.contains("dwarven mines") || clean.contains("crystal hollows")) return true;
        }
        return false;
    }

    private static void markGoldActivity(long now) {
        goldTimerStarted = true;
        goldTimerRunning = true;
        goldLastActivityTime = now;
        if (goldLastTickTime <= 0L) goldLastTickTime = now;
    }

    private static long goldTotalAsIngots() {
        return goldIngots + enchantedGold * 160L;
    }

    private static long goldTotalAsEnchanted() {
        return goldIngots / 160L + enchantedGold;
    }

    private static long goldDisplayTotal() {
        return goldDisplayEnchanted ? goldTotalAsEnchanted() : goldTotalAsIngots();
    }

    private static long goldPerHour(long amount) {
        if (goldActiveTimeMs <= 0L) return 0L;
        return Math.round(amount * 3_600_000.0 / goldActiveTimeMs);
    }

    private static void scanGoldInventory(MinecraftClient client) {
        if (client.player == null || client.world == null || !isGoldArea()) {
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
        long now = System.currentTimeMillis();
        boolean miningGold = now - lastGoldOreBreakTime <= 20_000L;
        if ((!goldOnlyWhileMining || miningGold) && (ingotDelta > 0 || enchantedDelta > 0)) {
            if (ingotDelta > 0) goldIngots += ingotDelta;
            if (enchantedDelta > 0) enchantedGold += enchantedDelta;
            markGoldActivity(now);
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

        var matrices = context.getMatrices();
        if (showFetchurHud) {
            HudLayout layout = HUD_LAYOUTS.get(HudPanel.FETCHUR);
            matrices.push();
            matrices.translate(layout.x, layout.y, 0.0F);
            matrices.scale(layout.scale, layout.scale, 1.0F);
            context.drawTextWithShadow(client.textRenderer, "Fetchur: " + fetchurItem(), 0, 0, 0x55FFFF);
            matrices.pop();
        }

        if (showPowderHud) {
            HudLayout layout = HUD_LAYOUTS.get(HudPanel.POWDER);
            matrices.push();
            matrices.translate(layout.x, layout.y, 0.0F);
            matrices.scale(layout.scale, layout.scale, 1.0F);
            int y = 0;
            context.drawTextWithShadow(client.textRenderer,
                    "Powder: " + format(gemstonePowder) + " Gemstone | " + format(mithrilPowder) + " Mithril",
                    0, y, 0xFFFFFF);
            y += 11;
            context.drawTextWithShadow(client.textRenderer,
                    "Chests: " + format(chests) + " | Gems: " + format(gemstoneDrops)
                            + " | Hard Stone compacts: " + format(hardStoneCompacts),
                    0, y, 0xFFFFFF);
            y += 11;
            context.drawTextWithShadow(client.textRenderer,
                    "Essence: " + format(diamondEssence) + " Diamond, " + format(goldEssence) + " Gold",
                    0, y, 0xAAAAFF);
            y += 11;
            if (showPowderGemBreakdown) {
                int before = y;
                for (String gem : new String[]{"Ruby", "Sapphire", "Amber", "Amethyst", "Jade", "Topaz",
                        "Jasper", "Opal", "Citrine", "Aquamarine", "Peridot", "Onyx"}) {
                    String line = compactPowderGemLine(gem);
                    if (line == null) continue;
                    context.drawTextWithShadow(client.textRenderer, line, 0, y, 0xFFB8E8FF);
                    y += 10;
                }
                if (y == before) {
                    context.drawTextWithShadow(client.textRenderer, "Gemstone quality: no drops yet", 0, y, 0xFFB8E8FF);
                    y += 10;
                }
            }
            if (sessionStarted) {
                context.drawTextWithShadow(client.textRenderer,
                        "Mining active: " + duration(activeTimeMs)
                                + " | Powder/h: " + format(perHour(gemstonePowder + mithrilPowder))
                                + " | Pristine/h: " + format(perHour(pristineGemstones)),
                        0, y, trackerPaused ? 0xFF5555 : 0x55FF55);
            }
            matrices.pop();
        }

        if (showPristineHud) {
            HudLayout layout = HUD_LAYOUTS.get(HudPanel.PRISTINE);
            matrices.push();
            matrices.translate(layout.x, layout.y, 0.0F);
            matrices.scale(layout.scale, layout.scale, 1.0F);
            int y = 0;
            context.drawTextWithShadow(client.textRenderer,
                    "Pristine: " + format(pristineGemstones) + " gems | " + format(pristineProcs) + " procs",
                    0, y, 0xFF55FF);
            y += 11;
            if (showPristineGemBreakdown) {
                for (String gem : new String[]{"Ruby", "Sapphire", "Amber", "Amethyst", "Jade", "Topaz",
                        "Jasper", "Opal", "Citrine", "Aquamarine", "Peridot", "Onyx"}) {
                    String line = compactPristineGemLine(gem);
                    if (line == null) continue;
                    context.drawTextWithShadow(client.textRenderer, line, 0, y, 0xFFFF88FF);
                    y += 10;
                }
            }
            matrices.pop();
        }

        if (showGoldHud) {
            HudLayout layout = HUD_LAYOUTS.get(HudPanel.GOLD);
            matrices.push();
            matrices.translate(layout.x, layout.y, 0.0F);
            matrices.scale(layout.scale, layout.scale, 1.0F);
            int y = 0;
            String unit = goldDisplayEnchanted ? "enchanted gold" : "gold ingots";
            context.drawTextWithShadow(client.textRenderer,
                    "Gold: " + format(goldDisplayTotal()) + " " + unit
                            + " (" + format(goldPerHour(goldDisplayTotal())) + "/h)",
                    0, y, 0xFFFF55);
            y += 11;
            context.drawTextWithShadow(client.textRenderer,
                    "Raw: " + format(goldTotalAsIngots()) + " ingots | " + format(goldTotalAsEnchanted())
                            + " enchanted | " + format(goldCompacts) + " compacts",
                    0, y, 0xFFFFFF);
            y += 11;
            if (goldTimerStarted) {
                context.drawTextWithShadow(client.textRenderer,
                        "Gold active: " + duration(goldActiveTimeMs)
                                + " | Session: " + duration(goldSessionTimeMs),
                        0, y, 0xFFFFAA);
            }
            matrices.pop();
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
                + " | Filter " + (powderFilterEnabled ? "ON" : "OFF")
                + " | Tracker " + (trackerPaused ? "PAUSED" : sessionStarted ? "ACTIVE" : "IDLE")
                + " | Active " + duration(activeTimeMs)
                + " | Powder threshold " + powderThreshold + " | Essence threshold " + essenceThreshold
                + " | Gem tier " + gemstoneTierFilter;
    }

    private static void reset() {
        powderGemstones.clear();
        pristineGemstonesByQuality.clear();
        gemstonePowder = mithrilPowder = chests = gemstoneDrops = 0;
        pristineProcs = pristineGemstones = diamondEssence = goldEssence = 0;
        oilBarrels = ascensionRopes = wishingCompasses = jungleHearts = 0;
        prehistoricEggs = pickonimbus = sludgeJuice = yoggies = 0;
        robotParts = treasurite = goblinEggs = hardStoneCompacts = goldCompacts = 0;
        goldIngots = enchantedGold = 0;
        goldActiveTimeMs = 0L;
        goldSessionTimeMs = 0L;
        goldLastActivityTime = 0L;
        goldLastTickTime = System.currentTimeMillis();
        goldTimerStarted = false;
        goldTimerRunning = false;
        activeTimeMs = 0L;
        sessionStarted = false;
        trackerPaused = false;
        lastActivityTime = 0L;
        lastTickTime = System.currentTimeMillis();
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
            showGoldHud = Boolean.parseBoolean(p.getProperty("showGoldHud", "true"));
            goldOnlyWhileMining = Boolean.parseBoolean(p.getProperty("goldOnlyWhileMining", "true"));
            goldDisplayEnchanted = Boolean.parseBoolean(p.getProperty("goldDisplayEnchanted", "false"));
            goldActiveTimeMs = value(p, "goldActiveTimeMs");
            goldSessionTimeMs = 0L;
            goldTimerStarted = goldActiveTimeMs > 0L;
            goldTimerRunning = false;
            goldLastTickTime = System.currentTimeMillis();
            showPowderGemBreakdown = Boolean.parseBoolean(p.getProperty("showPowderGemBreakdown", "true"));
            showPristineGemBreakdown = Boolean.parseBoolean(p.getProperty("showPristineGemBreakdown", "true"));
            powderGemstones.clear();
            pristineGemstonesByQuality.clear();
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("powderGem.")) powderGemstones.put(key.substring("powderGem.".length()), value(p, key));
                else if (key.startsWith("pristineGem.")) pristineGemstonesByQuality.put(key.substring("pristineGem.".length()), value(p, key));
            }
            PickobulusPreview.setEnabled(Boolean.parseBoolean(p.getProperty("pickobulusEnabled", "true")));
            CommissionHighlight.setEnabled(Boolean.parseBoolean(p.getProperty("commissionHighlightEnabled", "true")));
            HotmPowderTooltip.setEnabled(Boolean.parseBoolean(p.getProperty("hotmTooltipEnabled", "true")));
            hudX = (int) value(p, "hudX");
            hudY = (int) value(p, "hudY");
            hudScale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(p.getProperty("hudScale", "1.0"))));
            loadHudLayouts(p);
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
            hideChestOpen = Boolean.parseBoolean(p.getProperty("hideChestOpen", "true"));
            hideAlreadyLooted = Boolean.parseBoolean(p.getProperty("hideAlreadyLooted", "true"));
            hideBreakingPower = Boolean.parseBoolean(p.getProperty("hideBreakingPower", "true"));
            hideCompact = Boolean.parseBoolean(p.getProperty("hideCompact", "false"));
            hideRewardWrappers = Boolean.parseBoolean(p.getProperty("hideRewardWrappers", "true"));
            hidePowder = Boolean.parseBoolean(p.getProperty("hidePowder", "true"));
            powderThreshold = (int) value(p, "powderThreshold");
            hideEssence = Boolean.parseBoolean(p.getProperty("hideEssence", "true"));
            essenceThreshold = (int) value(p, "essenceThreshold");
            hideGemstones = Boolean.parseBoolean(p.getProperty("hideGemstones", "true"));
            gemstoneTierFilter = p.getProperty("gemstoneTierFilter", "Hide Rough");
            hideGoblinEggs = Boolean.parseBoolean(p.getProperty("hideGoblinEggs", "false"));
            hideOilBarrel = Boolean.parseBoolean(p.getProperty("hideOilBarrel", "true"));
            hideAscensionRope = Boolean.parseBoolean(p.getProperty("hideAscensionRope", "true"));
            hideWishingCompass = Boolean.parseBoolean(p.getProperty("hideWishingCompass", "true"));
            hideJungleHeart = Boolean.parseBoolean(p.getProperty("hideJungleHeart", "true"));
            hidePrehistoricEgg = Boolean.parseBoolean(p.getProperty("hidePrehistoricEgg", "true"));
            hidePickonimbus = Boolean.parseBoolean(p.getProperty("hidePickonimbus", "true"));
            hideSludgeJuice = Boolean.parseBoolean(p.getProperty("hideSludgeJuice", "true"));
            hideYoggie = Boolean.parseBoolean(p.getProperty("hideYoggie", "true"));
            hideRobotParts = Boolean.parseBoolean(p.getProperty("hideRobotParts", "true"));
            hideTreasurite = Boolean.parseBoolean(p.getProperty("hideTreasurite", "true"));
            activeTimeMs = value(p, "activeTimeMs");
            trackerPaused = Boolean.parseBoolean(p.getProperty("trackerPaused", "false"));
            sessionStarted = Boolean.parseBoolean(p.getProperty("sessionStarted", "false"));
            lastTickTime = System.currentTimeMillis();
        } catch (IOException ignored) {
            // A corrupt or unavailable stats file must not prevent the client from starting.
        }
    }

    private static void loadHudLayouts(Properties p) {
        int oldX = hudX;
        int oldY = hudY;
        float oldScale = hudScale;
        HUD_LAYOUTS.put(HudPanel.FETCHUR, new HudLayout(oldX, oldY, oldScale));
        HUD_LAYOUTS.put(HudPanel.POWDER, new HudLayout(oldX, oldY + 16, oldScale));
        HUD_LAYOUTS.put(HudPanel.PRISTINE, new HudLayout(oldX, oldY + 192, oldScale));
        HUD_LAYOUTS.put(HudPanel.GOLD, new HudLayout(oldX, oldY + 337, oldScale));
        for (HudPanel panel : HudPanel.values()) {
            HudLayout layout = HUD_LAYOUTS.get(panel);
            String prefix = "hud." + panel.name().toLowerCase(Locale.ROOT) + ".";
            layout.x = boundedInt(p.getProperty(prefix + "x"), layout.x);
            layout.y = boundedInt(p.getProperty(prefix + "y"), layout.y);
            layout.scale = boundedScale(p.getProperty(prefix + "scale"), layout.scale);
        }
        syncLegacyHudLayout();
    }

    private static int boundedInt(String raw, int fallback) {
        try { return Math.max(0, Math.min(5000, Integer.parseInt(raw == null ? Integer.toString(fallback) : raw))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static float boundedScale(String raw, float fallback) {
        try {
            float value = Float.parseFloat(raw == null ? Float.toString(fallback) : raw);
            return Float.isFinite(value) ? Math.max(0.5F, Math.min(3.0F, value)) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
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
        p.setProperty("showGoldHud", Boolean.toString(showGoldHud));
        p.setProperty("goldOnlyWhileMining", Boolean.toString(goldOnlyWhileMining));
        p.setProperty("goldDisplayEnchanted", Boolean.toString(goldDisplayEnchanted));
        p.setProperty("goldActiveTimeMs", Long.toString(goldActiveTimeMs));
        p.setProperty("showPowderGemBreakdown", Boolean.toString(showPowderGemBreakdown));
        p.setProperty("showPristineGemBreakdown", Boolean.toString(showPristineGemBreakdown));
        powderGemstones.forEach((key, count) -> p.setProperty("powderGem." + key, Long.toString(count)));
        pristineGemstonesByQuality.forEach((key, count) -> p.setProperty("pristineGem." + key, Long.toString(count)));
        p.setProperty("pickobulusEnabled", Boolean.toString(PickobulusPreview.isEnabled()));
        p.setProperty("commissionHighlightEnabled", Boolean.toString(CommissionHighlight.isEnabled()));
        p.setProperty("hotmTooltipEnabled", Boolean.toString(HotmPowderTooltip.isEnabled()));
        syncLegacyHudLayout();
        p.setProperty("hudX", Integer.toString(hudX));
        p.setProperty("hudY", Integer.toString(hudY));
        p.setProperty("hudScale", Float.toString(hudScale));
        for (HudPanel panel : HudPanel.values()) {
            HudLayout layout = HUD_LAYOUTS.get(panel);
            String prefix = "hud." + panel.name().toLowerCase(Locale.ROOT) + ".";
            p.setProperty(prefix + "x", Integer.toString(layout.x));
            p.setProperty(prefix + "y", Integer.toString(layout.y));
            p.setProperty(prefix + "scale", Float.toString(layout.scale));
        }
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
        p.setProperty("hideChestOpen", Boolean.toString(hideChestOpen));
        p.setProperty("hideAlreadyLooted", Boolean.toString(hideAlreadyLooted));
        p.setProperty("hideBreakingPower", Boolean.toString(hideBreakingPower));
        p.setProperty("hideCompact", Boolean.toString(hideCompact));
        p.setProperty("hideRewardWrappers", Boolean.toString(hideRewardWrappers));
        p.setProperty("hidePowder", Boolean.toString(hidePowder));
        p.setProperty("powderThreshold", Integer.toString(powderThreshold));
        p.setProperty("hideEssence", Boolean.toString(hideEssence));
        p.setProperty("essenceThreshold", Integer.toString(essenceThreshold));
        p.setProperty("hideGemstones", Boolean.toString(hideGemstones));
        p.setProperty("gemstoneTierFilter", gemstoneTierFilter);
        p.setProperty("hideGoblinEggs", Boolean.toString(hideGoblinEggs));
        p.setProperty("hideOilBarrel", Boolean.toString(hideOilBarrel));
        p.setProperty("hideAscensionRope", Boolean.toString(hideAscensionRope));
        p.setProperty("hideWishingCompass", Boolean.toString(hideWishingCompass));
        p.setProperty("hideJungleHeart", Boolean.toString(hideJungleHeart));
        p.setProperty("hidePrehistoricEgg", Boolean.toString(hidePrehistoricEgg));
        p.setProperty("hidePickonimbus", Boolean.toString(hidePickonimbus));
        p.setProperty("hideSludgeJuice", Boolean.toString(hideSludgeJuice));
        p.setProperty("hideYoggie", Boolean.toString(hideYoggie));
        p.setProperty("hideRobotParts", Boolean.toString(hideRobotParts));
        p.setProperty("hideTreasurite", Boolean.toString(hideTreasurite));
        p.setProperty("activeTimeMs", Long.toString(activeTimeMs));
        p.setProperty("trackerPaused", Boolean.toString(trackerPaused));
        p.setProperty("sessionStarted", Boolean.toString(sessionStarted));
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
