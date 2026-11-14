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
    private static int hudX = 8;
    private static int hudY = 8;
    private static float hudScale = 1.0F;

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
    private static boolean trackerPaused;
    private static boolean sessionStarted;
    private static long lastActivityTime;
    private static long lastTickTime = System.currentTimeMillis();
    private static long activeTimeMs;

    private MiningFeatures() {}

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

    public static int getHudX() { return hudX; }
    public static int getHudY() { return hudY; }
    public static float getHudScale() { return hudScale; }

    public static void adjustHudLayout(int dx, int dy, float scaleDelta) {
        hudX = Math.max(0, Math.min(5000, hudX + dx));
        hudY = Math.max(0, Math.min(5000, hudY + dy));
        hudScale = Math.max(0.5F, Math.min(3.0F,
                Math.round((hudScale + scaleDelta) * 10.0F) / 10.0F));
        save();
    }

    public static void resetHudLayout() {
        hudX = 8;
        hudY = 8;
        hudScale = 1.0F;
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
                                                            hudX = IntegerArgumentType.getInteger(context, "x");
                                                            hudY = IntegerArgumentType.getInteger(context, "y");
                                                            save();
                                                            context.getSource().sendFeedback(Text.literal("Mining HUD position: " + hudX + ", " + hudY));
                                                            return 1;
                                                        }))))
                                .then(ClientCommandManager.literal("scale")
                                        .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.5F, 3.0F))
                                                .executes(context -> {
                                                    hudScale = FloatArgumentType.getFloat(context, "value");
                                                    save();
                                                    context.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT, "Mining HUD scale: %.2fx", hudScale)));
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("reset").executes(context -> {
                                    hudX = hudY = 8;
                                    hudScale = 1.0F;
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
        if (changed) {
            markActivity();
            save();
        }

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
        if (!showFetchurHud && !showPowderHud && !showPristineHud && !showGoldHud && !sessionStarted) return;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(hudX, hudY, 0.0F);
        matrices.scale(hudScale, hudScale, 1.0F);

        int y = 0;
        if (showFetchurHud) {
            context.drawTextWithShadow(client.textRenderer, "Fetchur: " + fetchurItem(), 0, y, 0x55FFFF);
            y += 11;
        }
        if (showPowderHud) {
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
        }
        if (showPristineHud) {
            context.drawTextWithShadow(client.textRenderer,
                    "Pristine: " + format(pristineGemstones) + " gems | " + format(pristineProcs) + " procs",
                    0, y, 0xFF55FF);
            y += 11;
        }
        if (showGoldHud) {
            context.drawTextWithShadow(client.textRenderer,
                    "Gold Tracker: " + format(goldIngots) + " ingots | " + format(enchantedGold)
                            + " enchanted | " + format(goldCompacts) + " compacts",
                    0, y, 0xFFFF55);
            y += 11;
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
        gemstonePowder = mithrilPowder = chests = gemstoneDrops = 0;
        pristineProcs = pristineGemstones = diamondEssence = goldEssence = 0;
        oilBarrels = ascensionRopes = wishingCompasses = jungleHearts = 0;
        prehistoricEggs = pickonimbus = sludgeJuice = yoggies = 0;
        robotParts = treasurite = goblinEggs = hardStoneCompacts = goldCompacts = 0;
        goldIngots = enchantedGold = 0;
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
            hudX = (int) value(p, "hudX");
            hudY = (int) value(p, "hudY");
            hudScale = Math.max(0.5F, Math.min(3.0F, Float.parseFloat(p.getProperty("hudScale", "1.0"))));
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
        p.setProperty("hudX", Integer.toString(hudX));
        p.setProperty("hudY", Integer.toString(hudY));
        p.setProperty("hudScale", Float.toString(hudScale));
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
