package dev.strubbonart.skyblockqol.gui;

import dev.strubbonart.skyblockqol.features.dungeons.DungeonFeatures;
import dev.strubbonart.skyblockqol.features.qol.GyroWandFeature;
import dev.strubbonart.skyblockqol.features.qol.PartyFinderFloorTip;
import dev.strubbonart.skyblockqol.features.qol.BrewingStandFeature;
import dev.strubbonart.skyblockqol.features.qol.SlotBindsFeature;
import dev.strubbonart.skyblockqol.features.qol.ItemLogAlertsFeature;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonMapOverlay;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonOverlaySettings;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonPhaseTracker;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonRunOverlay;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonRoomScanner;
import dev.strubbonart.skyblockqol.features.mining.CommissionHighlight;
import dev.strubbonart.skyblockqol.features.mining.GhostTrackerFeatures;
import dev.strubbonart.skyblockqol.features.mining.HotmPowderTooltip;
import dev.strubbonart.skyblockqol.features.mining.MiningFeatures;
import dev.strubbonart.skyblockqol.features.mining.PickobulusPreview;
import dev.strubbonart.skyblockqol.features.fishing.FishingTimerFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * In-game settings UI limited to Aetheria's Mining and Dungeon feature groups.
 * All buttons modify the real feature settings; the underlying settings classes
 * persist changes to the config directory.
 */
public final class SkyblockQolSettingsScreen extends Screen {
    private enum Page { HOME, MINING, FILTER, DUNGEONS, QOL, PETS, PERFORMANCE, MORE }
    private enum LayoutTarget { FETCHUR, POWDER, PRISTINE, GOLD, GHOST, MAP, TIMER, SPLITS, BREAKER, ROOM }

    private final Screen parent;
    private final Page page;
    private final LayoutTarget target;

    public SkyblockQolSettingsScreen(Screen parent) {
        this(parent, Page.HOME, LayoutTarget.POWDER);
    }

    private SkyblockQolSettingsScreen(Screen parent, Page page, LayoutTarget target) {
        super(Text.literal("Skyblock QoL Settings"));
        this.parent = parent;
        this.page = page;
        this.target = target;
    }

    @Override
    protected void init() {
        int left = width / 2 - 154;
        int right = width / 2 + 8;
        int colWidth = 146;
        int y = 68;

        switch (page) {
            case HOME -> {
                addButton("MINING", width / 2 - 110, y, 220, 28,
                        () -> open(Page.MINING, LayoutTarget.POWDER));
                addButton("DUNGEONS", width / 2 - 110, y + 38, 220, 28,
                        () -> open(Page.DUNGEONS, LayoutTarget.MAP));
                addButton("QUALITY OF LIFE", width / 2 - 110, y + 76, 220, 28,
                        () -> open(Page.QOL, LayoutTarget.POWDER));
                addButton("PETS", width / 2 - 110, y + 114, 220, 28,
                        () -> open(Page.PETS, LayoutTarget.POWDER));
                addButton("GARDEN", width / 2 - 110, y + 152, 220, 28,
                        () -> client.setScreen(new GardenSettingsScreen(this)));
                addButton("PERFORMANCE HUD", width / 2 - 110, y + 190, 220, 28,
                        () -> open(Page.PERFORMANCE, LayoutTarget.POWDER));
                addButton("MORE FEATURES", width / 2 - 110, y + 228, 220, 28,
                        () -> open(Page.MORE, LayoutTarget.POWDER));
                addButton("DONE", width / 2 - 110, y + 266, 220, 24, this::close);
            }
            case MINING -> {
                addToggle("Powder chat filter", MiningFeatures.isPowderFilterEnabled(),
                        left, y, colWidth, () -> MiningFeatures.setPowderFilterEnabled(!MiningFeatures.isPowderFilterEnabled()));
                addToggle("Fetchur HUD", MiningFeatures.isHudPanelEnabled("fetchur"),
                        right, y, colWidth, () -> MiningFeatures.setHudPanelEnabled("fetchur", !MiningFeatures.isHudPanelEnabled("fetchur")));
                y += 24;
                addToggle("Powder tracker HUD", MiningFeatures.isHudPanelEnabled("powder"),
                        left, y, colWidth, () -> MiningFeatures.setHudPanelEnabled("powder", !MiningFeatures.isHudPanelEnabled("powder")));
                addToggle("Pristine tracker HUD", MiningFeatures.isHudPanelEnabled("pristine"),
                        right, y, colWidth, () -> MiningFeatures.setHudPanelEnabled("pristine", !MiningFeatures.isHudPanelEnabled("pristine")));
                y += 24;
                addToggle("Gold tracker HUD", MiningFeatures.isHudPanelEnabled("gold"),
                        left, y, colWidth, () -> MiningFeatures.setHudPanelEnabled("gold", !MiningFeatures.isHudPanelEnabled("gold")));
                addToggle("Only while mining gold", MiningFeatures.isGoldOnlyWhileMining(),
                        right, y, colWidth, () -> MiningFeatures.setGoldOnlyWhileMining(!MiningFeatures.isGoldOnlyWhileMining()));
                y += 24;
                addButton("Gold unit: " + (MiningFeatures.isGoldDisplayEnchanted() ? "ENCHANTED" : "INGOTS"),
                        left, y, colWidth, 20, MiningFeatures::toggleGoldDisplayUnit);
                addToggle("Pickobulus preview", PickobulusPreview.isEnabled(),
                        right, y, colWidth, () -> MiningFeatures.setMiningFeatureEnabled("pickobulus", !PickobulusPreview.isEnabled()));
                y += 24;
                addToggle("Powder gem breakdown", MiningFeatures.isGemstoneBreakdownEnabled("powder"),
                        left, y, colWidth, () -> MiningFeatures.setGemstoneBreakdownEnabled("powder", !MiningFeatures.isGemstoneBreakdownEnabled("powder")));
                addToggle("Pristine gem breakdown", MiningFeatures.isGemstoneBreakdownEnabled("pristine"),
                        right, y, colWidth, () -> MiningFeatures.setGemstoneBreakdownEnabled("pristine", !MiningFeatures.isGemstoneBreakdownEnabled("pristine")));
                y += 24;
                addToggle("Commission highlight", CommissionHighlight.isEnabled(),
                        left, y, colWidth, () -> MiningFeatures.setMiningFeatureEnabled("commission", !CommissionHighlight.isEnabled()));
                addToggle("Ghost tracker + HUD", GhostTrackerFeatures.isTracking(),
                        right, y, colWidth, () -> GhostTrackerFeatures.setTracking(!GhostTrackerFeatures.isTracking()));
                y += 24;
                addToggle("HOTM powder tooltip", HotmPowderTooltip.isEnabled(),
                        left, y, colWidth, () -> MiningFeatures.setMiningFeatureEnabled("hotm", !HotmPowderTooltip.isEnabled()));
                addButton("CHAT FILTER OPTIONS", right, y, colWidth, 20,
                        () -> open(Page.FILTER, LayoutTarget.POWDER));
                y += 24;
                addToggle("Mining active timer", !MiningFeatures.isTrackerPaused(),
                        left, y, colWidth, () -> MiningFeatures.setTrackerPaused(!MiningFeatures.isTrackerPaused()));

                y += 32;
                addButton(target == LayoutTarget.FETCHUR ? "[FETCHUR]" : "FETCHUR",
                        left, y, 95, 20, () -> open(Page.MINING, LayoutTarget.FETCHUR));
                addButton(target == LayoutTarget.POWDER ? "[POWDER]" : "POWDER",
                        left + 100, y, 95, 20, () -> open(Page.MINING, LayoutTarget.POWDER));
                addButton(target == LayoutTarget.PRISTINE ? "[PRISTINE]" : "PRISTINE",
                        left + 200, y, 95, 20, () -> open(Page.MINING, LayoutTarget.PRISTINE));
                y += 24;
                addButton(target == LayoutTarget.GOLD ? "[GOLD]" : "GOLD",
                        left, y, 146, 20, () -> open(Page.MINING, LayoutTarget.GOLD));
                addButton(target == LayoutTarget.GHOST ? "[GHOST TRACKER]" : "GHOST TRACKER",
                        right, y, 146, 20, () -> open(Page.MINING, LayoutTarget.GHOST));
                y += 24;
                addButton("EDIT POSITION", left, y, 146, 20,
                        () -> openPositionEditor(positionTarget(target)));
                addButton("RESET POSITION", right, y, 146, 20,
                        this::resetSelectedLayout);
            }
            case QOL -> {
                addToggle("SkyBlock ID in item tooltip",
                        dev.strubbonart.skyblockqol.features.qol.QolFeatures.isShowSkyblockIdEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.QolFeatures.setShowSkyblockIdEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.QolFeatures.isShowSkyblockIdEnabled()));
                y += 26;
                addToggle("Custom block selection highlight",
                        dev.strubbonart.skyblockqol.features.qol.QolFeatures.isBlockSelectionOverlayEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.QolFeatures.setBlockSelectionOverlayEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.QolFeatures.isBlockSelectionOverlayEnabled()));
                y += 26;
                addToggle("Disable enchant glint",
                        dev.strubbonart.skyblockqol.features.qol.QolFeatures.isDisableEnchantGlintEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.QolFeatures.setDisableEnchantGlintEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.QolFeatures.isDisableEnchantGlintEnabled()));
                y += 26;
                addToggle("Show own nametag",
                        dev.strubbonart.skyblockqol.features.qol.QolFeatures.isShowOwnNametagEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.QolFeatures.setShowOwnNametagEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.QolFeatures.isShowOwnNametagEnabled()));
                y += 26;
                addToggle("Confirm disconnect",
                        dev.strubbonart.skyblockqol.features.qol.QolFeatures.isConfirmDisconnectEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.QolFeatures.setConfirmDisconnectEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.QolFeatures.isConfirmDisconnectEnabled()));
                y += 26;
                addToggle("Enchant level coloring",
                        dev.strubbonart.skyblockqol.features.qol.QolFeatures.isEnchantHighlightEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.QolFeatures.setEnchantHighlightEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.QolFeatures.isEnchantHighlightEnabled()));
                y += 26;
                addToggle("Missing enchants (hold Shift)",
                        dev.strubbonart.skyblockqol.features.qol.QolFeatures.isMissingEnchantsEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.QolFeatures.setMissingEnchantsEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.QolFeatures.isMissingEnchantsEnabled()));
                y += 26;
                addToggle("Hide enchant descriptions",
                        dev.strubbonart.skyblockqol.features.qol.QolFeatures.isHideEnchantDescriptionsEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.QolFeatures.setHideEnchantDescriptionsEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.QolFeatures.isHideEnchantDescriptionsEnabled()));
                y += 26;
                addToggle("SkyBlock XP in chat (server support required)",
                        dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isSkyblockXpInChatEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.setSkyblockXpInChatEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isSkyblockXpInChatEnabled()));
                y += 26;
                addToggle("Disable entity fire overlay",
                        dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isDisableEntityFireEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.setDisableEntityFireEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isDisableEntityFireEnabled()));
                y += 26;
                addToggle("Watched player join/leave (" +
                                dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.getWatchedCount() + ")",
                        dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.isJoinLeaveEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.setJoinLeaveEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.isJoinLeaveEnabled()));
                y += 26;
                addToggle("Hoppity new rabbit highlight",
                        dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.isHoppityHighlightEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.setHoppityHighlightEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.isHoppityHighlightEnabled()));
                y += 26;
                addToggle("Restore unfinished chat draft",
                        dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.isChatStateRestoreEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.setChatStateRestoreEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures.isChatStateRestoreEnabled()));
                y += 26;
                addToggle("Kill Combo HUD",
                        dev.strubbonart.skyblockqol.features.qol.KillComboFeature.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.KillComboFeature.setEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.KillComboFeature.isEnabled()));
                y += 26;
                addButton("EDIT KILL COMBO POSITION", left, y, 300, 20,
                        () -> openPositionEditor(OverlayPositionEditorScreen.Target.QOL_KILL_COMBO));
                y += 26;
                addToggle("DVD screensaver", 
                        dev.strubbonart.skyblockqol.features.qol.DvdScreensaverFeature.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.DvdScreensaverFeature.setEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.DvdScreensaverFeature.isEnabled()));
                y += 26;
                addToggle("Anvil Combine Helper",
                        dev.strubbonart.skyblockqol.features.qol.AnvilCombineFeature.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.AnvilCombineFeature.setEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.AnvilCombineFeature.isEnabled()));
                y += 26;
                addToggle("Item Pickup Log HUD",
                        dev.strubbonart.skyblockqol.features.qol.ItemPickupLogFeature.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.ItemPickupLogFeature.setEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.ItemPickupLogFeature.isEnabled()));
                y += 26;
                addButton("EDIT ITEM LOG POSITION", left, y, 300, 20,
                        () -> openPositionEditor(OverlayPositionEditorScreen.Target.QOL_ITEM_LOG));
                y += 26;
                addToggle("Rare Drop Tracker HUD",
                        dev.strubbonart.skyblockqol.features.qol.RareDropTrackerFeature.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.RareDropTrackerFeature.setEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.RareDropTrackerFeature.isEnabled()));
                y += 26;
                addToggle("Rare Drop chat alerts",
                        dev.strubbonart.skyblockqol.features.qol.RareDropTrackerFeature.isChatAlertsEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.RareDropTrackerFeature.setChatAlertsEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.RareDropTrackerFeature.isChatAlertsEnabled()));
                y += 26;
                addButton("EDIT RARE DROP POSITION", left, y, 300, 20,
                        () -> openPositionEditor(OverlayPositionEditorScreen.Target.QOL_RARE_DROP));
                y += 26;
                addToggle("Skill XP tooltip (hold Shift for remaining XP)",
                        dev.strubbonart.skyblockqol.features.qol.SkillXpTooltipFeature.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.SkillXpTooltipFeature.setEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.SkillXpTooltipFeature.isEnabled()));
                y += 26;
                addButton("DVD size: " + dev.strubbonart.skyblockqol.features.qol.DvdScreensaverFeature.getSize() + "  (+/-)",
                        left, y, 146, 20,
                        () -> dev.strubbonart.skyblockqol.features.qol.DvdScreensaverFeature.adjustSize(12));
                addButton("DVD speed: " + dev.strubbonart.skyblockqol.features.qol.DvdScreensaverFeature.getSpeed() + "  (+/-)",
                        right, y, 146, 20,
                        () -> dev.strubbonart.skyblockqol.features.qol.DvdScreensaverFeature.adjustSpeed(5));


            }
            case MORE -> {
                addToggle("Gyro Wand area ring", GyroWandFeature.isEnabled(),
                        left, y, 300,
                        () -> GyroWandFeature.setEnabled(!GyroWandFeature.isEnabled()));
                y += 28;
                addToggle("Party Finder floor labels", PartyFinderFloorTip.isEnabled(),
                        left, y, 300,
                        () -> PartyFinderFloorTip.setEnabled(!PartyFinderFloorTip.isEnabled()));
                y += 28;
                addToggle("Brewing Stand timer highlight", BrewingStandFeature.isEnabled(),
                        left, y, 300,
                        () -> BrewingStandFeature.setEnabled(!BrewingStandFeature.isEnabled()));
                y += 28;
                addToggle("No item swap lowering animation",
                        dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isNoSwapAnimationEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.setNoSwapAnimationEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isNoSwapAnimationEnabled()));
                y += 28;
                addToggle("Item Stack Tips (books / Catacombs passes)",
                        dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isItemStackTipsEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.setItemStackTipsEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isItemStackTipsEnabled()));
                y += 28;
                addToggle("Damage Formatter (1,234,567 -> 1.2M)",
                        dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isDamageFormatterEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.setDamageFormatterEnabled(
                                !dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures.isDamageFormatterEnabled()));
                y += 28;
                addToggle("Slot Binds (bind inventory slots to hotbar slots)",
                        SlotBindsFeature.isEnabled(), left, y, 300,
                        () -> SlotBindsFeature.setEnabled(!SlotBindsFeature.isEnabled()));
                y += 28;
                addToggle("Always show Slot Bind lines",
                        SlotBindsFeature.isAlwaysShowLinesEnabled(), left, y, 300,
                        () -> SlotBindsFeature.setAlwaysShowLinesEnabled(
                                !SlotBindsFeature.isAlwaysShowLinesEnabled()));
                y += 28;
                addToggle("Item Log Alerts (" + ItemLogAlertsFeature.getAlertCount() + " tracked)",
                        ItemLogAlertsFeature.isEnabled(), left, y, 300,
                        () -> ItemLogAlertsFeature.setEnabled(!ItemLogAlertsFeature.isEnabled()));
                y += 28;
                addToggle("Item Log Alerts: First Time Only",
                        ItemLogAlertsFeature.isFirstTimeOnly(), left, y, 300,
                        () -> ItemLogAlertsFeature.setFirstTimeOnly(!ItemLogAlertsFeature.isFirstTimeOnly()));
                y += 28;
                addToggle("Use original item display name",
                        ItemLogAlertsFeature.isUseDisplayName(), left, y, 300,
                        () -> ItemLogAlertsFeature.setUseDisplayName(!ItemLogAlertsFeature.isUseDisplayName()));
                y += 28;
                addToggle("Play alert sound",
                        ItemLogAlertsFeature.isPlaySound(), left, y, 300,
                        () -> ItemLogAlertsFeature.setPlaySound(!ItemLogAlertsFeature.isPlaySound()));
                y += 28;
                addToggle("Trophy Fish HUD", dev.strubbonart.skyblockqol.features.fishing.TrophyFishFeature.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.fishing.TrophyFishFeature.setEnabled(
                                !dev.strubbonart.skyblockqol.features.fishing.TrophyFishFeature.isEnabled()));
                y += 28;
                addButton("EDIT TROPHY FISH POSITION", left, y, 300, 20,
                        () -> openPositionEditor(OverlayPositionEditorScreen.Target.TROPHY_FISH));
                y += 28;
                addToggle("Fishing Timer", FishingTimerFeature.isEnabled(),
                        left, y, 300, () -> FishingTimerFeature.setEnabled(!FishingTimerFeature.isEnabled()));
                y += 28;
                addButton("Alert time: " + FishingTimerFeature.getAlertTimeSeconds() + "s  (-5)",
                        left, y, 146, 20, () -> FishingTimerFeature.adjustAlertTime(-5));
                addButton("Alert time: " + FishingTimerFeature.getAlertTimeSeconds() + "s  (+5)",
                        right, y, 146, 20, () -> FishingTimerFeature.adjustAlertTime(5));
                y += 28;
                addButton("EDIT FISHING TIMER POSITION", left, y, 300, 20,
                        () -> openPositionEditor(OverlayPositionEditorScreen.Target.FISHING_TIMER));
            }
            case PERFORMANCE -> {
                addToggle("Performance HUD", dev.strubbonart.skyblockqol.features.hud.PerformanceHud.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.hud.PerformanceHud.setEnabled(
                                !dev.strubbonart.skyblockqol.features.hud.PerformanceHud.isEnabled()));
                y += 28;
                addButton("EDIT PERFORMANCE HUD POSITION", left, y, 300, 20,
                        () -> openPositionEditor(OverlayPositionEditorScreen.Target.PERFORMANCE_HUD));
                y += 26;
                addButton("RESET POSITION", left, y, 300, 20,
                        dev.strubbonart.skyblockqol.features.hud.PerformanceHud::resetLayout);
            }
            case PETS -> {
                addToggle("Current Pet HUD",
                        dev.strubbonart.skyblockqol.features.pets.CurrentPetFeature.isEnabled(),
                        left, y, 300,
                        () -> dev.strubbonart.skyblockqol.features.pets.CurrentPetFeature.setEnabled(
                                !dev.strubbonart.skyblockqol.features.pets.CurrentPetFeature.isEnabled()));
                y += 26;
                addButton("EDIT PET HUD POSITION", left, y, 300, 20,
                        () -> openPositionEditor(OverlayPositionEditorScreen.Target.CURRENT_PET));
                y += 28;
                addButton("VIEW PET COLLECTION (" + dev.strubbonart.skyblockqol.features.pets.PetCollectionFeature.getPetCount() + ")",
                        left, y, 300, 20,
                        () -> client.setScreen(new PetCollectionScreen(this)));
            }
            case FILTER -> {
                String[] names = {"chest", "already-looted", "breaking-power", "compact", "wrappers",
                        "powder", "essence", "gemstones", "goblin-eggs", "special-drops"};
                String[] labels = {"Hide chest messages", "Hide already-looted", "Hide break-power warning",
                        "Hide compact messages", "Hide reward wrappers", "Hide powder messages",
                        "Hide essence messages", "Hide gemstone drops", "Hide Goblin Eggs", "Hide special drops"};
                for (int i = 0; i < names.length; i++) {
                    int col = i % 2;
                    int row = i / 2;
                    String key = names[i];
                    addToggle(labels[i], MiningFeatures.getFilterOption(key),
                            col == 0 ? left : right, y + row * 24, colWidth,
                            () -> MiningFeatures.setFilterOption(key, !MiningFeatures.getFilterOption(key)));
                }
                int tierY = y + 5 * 24 + 4;
                addButton("Gemstone tiers: " + MiningFeatures.getGemstoneTierFilter(),
                        left, tierY, 300, 22, MiningFeatures::cycleGemstoneTierFilter);
                addButton("Powder -100  [" + MiningFeatures.getPowderThreshold() + "]",
                        left, tierY + 26, 146, 20, () -> MiningFeatures.adjustPowderThreshold(-100));
                addButton("Powder +100  [" + MiningFeatures.getPowderThreshold() + "]",
                        right, tierY + 26, 146, 20, () -> MiningFeatures.adjustPowderThreshold(100));
                addButton("Essence -1  [" + MiningFeatures.getEssenceThreshold() + "]",
                        left, tierY + 50, 146, 20, () -> MiningFeatures.adjustEssenceThreshold(-1));
                addButton("Essence +1  [" + MiningFeatures.getEssenceThreshold() + "]",
                        right, tierY + 50, 146, 20, () -> MiningFeatures.adjustEssenceThreshold(1));
            }
            case DUNGEONS -> {
                addToggle("Hide blessing messages", DungeonFeatures.isHideBlessingMessagesEnabled(),
                        left, y, colWidth, () -> DungeonFeatures.setHideBlessingMessagesEnabled(!DungeonFeatures.isHideBlessingMessagesEnabled()));
                addToggle("Dungeon Breaker overlay", DungeonFeatures.isBreakerOverlayEnabled(),
                        right, y, colWidth, () -> DungeonFeatures.setBreakerOverlayEnabled(!DungeonFeatures.isBreakerOverlayEnabled()));
                y += 24;
                addToggle("Boss highlights", DungeonFeatures.areBossHighlightsEnabled(),
                        left, y, colWidth, () -> DungeonFeatures.setBossHighlightsEnabled(!DungeonFeatures.areBossHighlightsEnabled()));
                addToggle("Blood mob highlights", DungeonFeatures.areBloodHighlightsEnabled(),
                        right, y, colWidth, () -> DungeonFeatures.setBloodHighlightsEnabled(!DungeonFeatures.areBloodHighlightsEnabled()));
                y += 24;
                addToggle("Dungeon timer HUD", DungeonRunOverlay.isEnabled(),
                        left, y, colWidth, () -> DungeonRunOverlay.setEnabled(!DungeonRunOverlay.isEnabled()));
                addToggle("Dungeon split HUD", DungeonPhaseTracker.isHudEnabled(),
                        right, y, colWidth, () -> DungeonPhaseTracker.setHudEnabled(!DungeonPhaseTracker.isHudEnabled()));
                y += 24;
                addToggle("Dungeon map HUD", DungeonMapOverlay.isEnabled(),
                        left, y, colWidth, () -> DungeonMapOverlay.setEnabled(!DungeonMapOverlay.isEnabled()));
                addToggle("Map player labels", DungeonMapOverlay.areLabelsEnabled(),
                        right, y, colWidth, () -> DungeonMapOverlay.setLabelsEnabled(!DungeonMapOverlay.areLabelsEnabled()));

                y += 24;
                addToggle("Room overlay", DungeonRoomScanner.isRoomOverlayEnabled(),
                        left, y, colWidth, () -> DungeonRoomScanner.setRoomOverlayEnabled(!DungeonRoomScanner.isRoomOverlayEnabled()));
                addToggle("Secret finder", DungeonRoomScanner.isSecretFinderEnabled(),
                        right, y, colWidth, () -> DungeonRoomScanner.setSecretFinderEnabled(!DungeonRoomScanner.isSecretFinderEnabled()));

                y += 24;
                addToggle("Spirit Leap / InfiniLeap menu", DungeonFeatures.isLeapMenuEnabled(),
                        left, y, 300, () -> DungeonFeatures.setLeapMenuEnabled(!DungeonFeatures.isLeapMenuEnabled()));

                y += 31;
                String[] options = {"MAP", "TIMER", "SPLITS", "BREAKER", "ROOM"};
                int bx = left;
                for (String option : options) {
                    LayoutTarget selected = LayoutTarget.valueOf(option);
                    String label = target == selected ? "[" + option + "]" : option;
                    addButton(label, bx, y, 56, 20, () -> open(Page.DUNGEONS, selected));
                    bx += 60;
                }
                addButton("EDIT POSITION", left, y + 26, 146, 20,
                        () -> openPositionEditor(positionTarget(target)));
                addButton("RESET POSITION", right, y + 26, 146, 20,
                        this::resetSelectedLayout);
                addButton("REFRESH ROOM / SECRET DATA", left, y + 52, 300, 20,
                        () -> dev.strubbonart.skyblockqol.features.dungeons.DungeonDataRepository.refresh());
            }
        }

        if (page != Page.HOME) {
            if (page == Page.FILTER) {
                addButton("BACK TO MINING", left, height - 32, 300, 20,
                        () -> open(Page.MINING, LayoutTarget.POWDER));
            } else if (page == Page.QOL || page == Page.PETS || page == Page.MORE || page == Page.PERFORMANCE) {
                addButton("BACK", left, height - 32, 300, 20,
                        () -> open(Page.HOME, LayoutTarget.POWDER));
            } else {
                addButton("BACK", left, height - 32, 300, 20,
                        () -> open(Page.HOME, LayoutTarget.POWDER));
            }
        }
    }

    private boolean isMiningLayoutTarget(LayoutTarget selected) {
        return selected == LayoutTarget.FETCHUR || selected == LayoutTarget.POWDER
                || selected == LayoutTarget.PRISTINE || selected == LayoutTarget.GOLD;
    }

    private void adjustDungeonLayout(LayoutTarget selected, int dx, int dy, float ds) {
        if (selected == LayoutTarget.MAP) {
            DungeonMapOverlay.adjustLayout(dx, dy, ds);
            return;
        }
        DungeonOverlaySettings.Widget widget = switch (selected) {
            case TIMER -> DungeonOverlaySettings.Widget.TIMER;
            case SPLITS -> DungeonOverlaySettings.Widget.SPLITS;
            case BREAKER -> DungeonOverlaySettings.Widget.BREAKER;
            case ROOM -> DungeonOverlaySettings.Widget.ROOM;
            default -> DungeonOverlaySettings.Widget.TIMER;
        };
        int x = DungeonOverlaySettings.x(widget) + dx;
        int y = DungeonOverlaySettings.y(widget) + dy;
        float scale = Math.max(0.5F, Math.min(3.0F,
                Math.round((DungeonOverlaySettings.scale(widget) + ds) * 10.0F) / 10.0F));
        DungeonOverlaySettings.set(widget, Math.max(0, x), Math.max(0, y), scale);
    }

    private void resetSelectedLayout() {
        if (isMiningLayoutTarget(target)) {
            MiningFeatures.resetHudLayout(target.name().toLowerCase(Locale.ROOT));
        } else if (target == LayoutTarget.GHOST) {
            GhostTrackerFeatures.resetHudLayout();
        } else if (target == LayoutTarget.MAP) {
            DungeonMapOverlay.resetLayout();
        } else {
            DungeonOverlaySettings.Widget widget = switch (target) {
                case TIMER -> DungeonOverlaySettings.Widget.TIMER;
                case SPLITS -> DungeonOverlaySettings.Widget.SPLITS;
                case BREAKER -> DungeonOverlaySettings.Widget.BREAKER;
                case ROOM -> DungeonOverlaySettings.Widget.ROOM;
                default -> DungeonOverlaySettings.Widget.TIMER;
            };
            int x = switch (target) {
                case TIMER -> 8;
                case SPLITS -> 8;
                case BREAKER -> 8;
                default -> 8;
            };
            int y = switch (target) {
                case TIMER -> 156;
                case SPLITS -> 178;
                case BREAKER -> 112;
                case ROOM -> 212;
                default -> 156;
            };
            DungeonOverlaySettings.set(widget, x, y, 1.0F);
        }
    }

    private OverlayPositionEditorScreen.Target positionTarget(LayoutTarget selected) {
        return switch (selected) {
            case FETCHUR -> OverlayPositionEditorScreen.Target.FETCHUR;
            case POWDER -> OverlayPositionEditorScreen.Target.POWDER;
            case PRISTINE -> OverlayPositionEditorScreen.Target.PRISTINE;
            case GOLD -> OverlayPositionEditorScreen.Target.GOLD;
            case GHOST -> OverlayPositionEditorScreen.Target.GHOST;
            case MAP -> OverlayPositionEditorScreen.Target.DUNGEON_MAP;
            case TIMER -> OverlayPositionEditorScreen.Target.DUNGEON_TIMER;
            case SPLITS -> OverlayPositionEditorScreen.Target.DUNGEON_SPLITS;
            case BREAKER -> OverlayPositionEditorScreen.Target.DUNGEON_BREAKER;
            case ROOM -> OverlayPositionEditorScreen.Target.DUNGEON_ROOM;
        };
    }

    private void openPositionEditor(OverlayPositionEditorScreen.Target selected) {
        MinecraftClient.getInstance().setScreen(new OverlayPositionEditorScreen(this, selected));
    }

    private void addToggle(String name, boolean active, int x, int y, int w, Runnable action) {
        addButton(name + ": " + (active ? "ON" : "OFF"), x, y, w, 20, action);
    }

    private void addButton(String name, int x, int y, int w, int h, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name), button -> {
            action.run();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen == this) {
                client.setScreen(new SkyblockQolSettingsScreen(parent, page, target));
            }
        }).dimensions(x, y, w, h).build());
    }

    private void open(Page nextPage, LayoutTarget nextTarget) {
        MinecraftClient.getInstance().setScreen(
                new SkyblockQolSettingsScreen(parent, nextPage, nextTarget));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int panelLeft = width / 2 - 166;
        int panelTop = 32;
        int panelBottom = height - 24;
        context.fill(panelLeft, panelTop, panelLeft + 332, panelBottom, 0xCC101722);
        String heading = switch (page) {
            case HOME -> "Skyblock QoL";
            case MINING -> "Mining Settings";
            case FILTER -> "Powder Chat Filter";
            case DUNGEONS -> "Dungeon Settings";
            case QOL -> "Quality of Life";
            case PETS -> "Pets";
            case PERFORMANCE -> "Performance HUD";
            case MORE -> "More Features";
        };
        context.drawTextWithShadow(textRenderer, heading, width / 2 - textRenderer.getWidth(heading) / 2,
                42, 0xFFFFFFFF);

        if (page == Page.MINING) {
            String layout = target == LayoutTarget.GHOST
                    ? "Ghost HUD  X: " + GhostTrackerFeatures.getHudX()
                        + "  Y: " + GhostTrackerFeatures.getHudY()
                        + "  Scale: " + String.format(Locale.ROOT, "%.1fx", GhostTrackerFeatures.getHudScale())
                    : target.name() + " HUD  X: " + MiningFeatures.getHudX(target.name().toLowerCase(Locale.ROOT))
                        + "  Y: " + MiningFeatures.getHudY(target.name().toLowerCase(Locale.ROOT))
                        + "  Scale: " + String.format(Locale.ROOT, "%.1fx",
                                MiningFeatures.getHudScale(target.name().toLowerCase(Locale.ROOT)));
            context.drawTextWithShadow(textRenderer, layout, width / 2 - textRenderer.getWidth(layout) / 2,
                    height - 60, 0xFFB8D8FF);
        } else if (page == Page.DUNGEONS) {
            String layout = target + " layout: " + dungeonLayoutText();
            context.drawTextWithShadow(textRenderer, layout,
                    width / 2 - textRenderer.getWidth(layout) / 2, height - 60, 0xFFB8D8FF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String dungeonLayoutText() {
        if (target == LayoutTarget.MAP) {
            String x = DungeonMapOverlay.getPositionX() < 0
                    ? "AUTO" : Integer.toString(DungeonMapOverlay.getPositionX());
            return "X " + x + "  Y " + DungeonMapOverlay.getPositionY()
                    + "  Scale " + String.format(Locale.ROOT, "%.1fx", DungeonMapOverlay.getScale());
        }
        DungeonOverlaySettings.Widget widget = switch (target) {
            case TIMER -> DungeonOverlaySettings.Widget.TIMER;
            case SPLITS -> DungeonOverlaySettings.Widget.SPLITS;
            case BREAKER -> DungeonOverlaySettings.Widget.BREAKER;
            case ROOM -> DungeonOverlaySettings.Widget.ROOM;
            default -> DungeonOverlaySettings.Widget.TIMER;
        };
        return "X " + DungeonOverlaySettings.x(widget) + "  Y " + DungeonOverlaySettings.y(widget)
                + "  Scale " + String.format(Locale.ROOT, "%.1fx", DungeonOverlaySettings.scale(widget));
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
