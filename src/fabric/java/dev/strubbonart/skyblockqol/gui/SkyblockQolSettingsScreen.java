package dev.strubbonart.skyblockqol.gui;

import dev.strubbonart.skyblockqol.features.dungeons.DungeonFeatures;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonMapOverlay;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonOverlaySettings;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonPhaseTracker;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonRunOverlay;
import dev.strubbonart.skyblockqol.features.mining.CommissionHighlight;
import dev.strubbonart.skyblockqol.features.mining.GhostTrackerFeatures;
import dev.strubbonart.skyblockqol.features.mining.HotmPowderTooltip;
import dev.strubbonart.skyblockqol.features.mining.MiningFeatures;
import dev.strubbonart.skyblockqol.features.mining.PickobulusPreview;
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
    private enum Page { HOME, MINING, FILTER, DUNGEONS }
    private enum LayoutTarget { MINING, MAP, TIMER, SPLITS, BREAKER }

    private final Screen parent;
    private final Page page;
    private final LayoutTarget target;

    public SkyblockQolSettingsScreen(Screen parent) {
        this(parent, Page.HOME, LayoutTarget.MINING);
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
                        () -> open(Page.MINING, LayoutTarget.MINING));
                addButton("DUNGEONS", width / 2 - 110, y + 38, 220, 28,
                        () -> open(Page.DUNGEONS, LayoutTarget.MAP));
                addButton("DONE", width / 2 - 110, y + 76, 220, 24, this::close);
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
                addToggle("Pickobulus preview", PickobulusPreview.isEnabled(),
                        right, y, colWidth, () -> MiningFeatures.setMiningFeatureEnabled("pickobulus", !PickobulusPreview.isEnabled()));
                y += 24;
                addToggle("Commission highlight", CommissionHighlight.isEnabled(),
                        left, y, colWidth, () -> MiningFeatures.setMiningFeatureEnabled("commission", !CommissionHighlight.isEnabled()));
                addToggle("Ghost tracker session", GhostTrackerFeatures.isTracking(),
                        right, y, colWidth, () -> GhostTrackerFeatures.setTracking(!GhostTrackerFeatures.isTracking()));
                y += 24;
                addToggle("HOTM powder tooltip", HotmPowderTooltip.isEnabled(),
                        left, y, colWidth, () -> MiningFeatures.setMiningFeatureEnabled("hotm", !HotmPowderTooltip.isEnabled()));
                addButton("CHAT FILTER OPTIONS", right, y, colWidth, 20,
                        () -> open(Page.FILTER, LayoutTarget.MINING));

                y += 32;
                addLayoutControlRow(left, y, LayoutTarget.MINING);
                addButton("RESET MINING HUD LAYOUT", left, y + 26, 300, 20,
                        MiningFeatures::resetHudLayout);
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
                addButton("BACK TO MINING", left, height - 34, 300, 20,
                        () -> open(Page.MINING, LayoutTarget.MINING));
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

                y += 31;
                String[] options = {"MAP", "TIMER", "SPLITS", "BREAKER"};
                int bx = left;
                for (String option : options) {
                    LayoutTarget selected = LayoutTarget.valueOf(option);
                    String label = target == selected ? "[" + option + "]" : option;
                    addButton(label, bx, y, 71, 20, () -> open(Page.DUNGEONS, selected));
                    bx += 76;
                }
                addLayoutControlRow(left, y + 26, target);
                addButton("RESET SELECTED LAYOUT", left, y + 52, 300, 20,
                        this::resetSelectedLayout);
                addButton("REFRESH ROOM / SECRET DATA", left, y + 76, 300, 20,
                        () -> dev.strubbonart.skyblockqol.features.dungeons.DungeonDataRepository.refresh());
            }
        }

        if (page != Page.HOME) {
            addButton("BACK", left, height - 32, 300, 20,
                    () -> open(Page.HOME, LayoutTarget.MINING));
        }
    }

    private void addLayoutControlRow(int x, int y, LayoutTarget selected) {
        int buttonWidth = 48;
        String[] labels = {"X -", "X +", "Y -", "Y +", "SIZE -", "SIZE +"};
        int[][] changes = {{-10, 0}, {10, 0}, {0, -10}, {0, 10}, {0, 0}, {0, 0}};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            addButton(labels[i], x + i * 50, y, buttonWidth, 20, () -> {
                if (selected == LayoutTarget.MINING) {
                    MiningFeatures.adjustHudLayout(changes[index][0], changes[index][1],
                            index == 4 ? -0.1F : index == 5 ? 0.1F : 0.0F);
                } else {
                    adjustDungeonLayout(selected, changes[index][0], changes[index][1],
                            index == 4 ? -0.1F : index == 5 ? 0.1F : 0.0F);
                }
            });
        }
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
            default -> DungeonOverlaySettings.Widget.TIMER;
        };
        int x = DungeonOverlaySettings.x(widget) + dx;
        int y = DungeonOverlaySettings.y(widget) + dy;
        float scale = Math.max(0.5F, Math.min(3.0F,
                Math.round((DungeonOverlaySettings.scale(widget) + ds) * 10.0F) / 10.0F));
        DungeonOverlaySettings.set(widget, Math.max(0, x), Math.max(0, y), scale);
    }

    private void resetSelectedLayout() {
        if (target == LayoutTarget.MINING) {
            MiningFeatures.resetHudLayout();
        } else if (target == LayoutTarget.MAP) {
            DungeonMapOverlay.resetLayout();
        } else {
            DungeonOverlaySettings.Widget widget = switch (target) {
                case TIMER -> DungeonOverlaySettings.Widget.TIMER;
                case SPLITS -> DungeonOverlaySettings.Widget.SPLITS;
                case BREAKER -> DungeonOverlaySettings.Widget.BREAKER;
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
                default -> 156;
            };
            DungeonOverlaySettings.set(widget, x, y, 1.0F);
        }
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
        };
        context.drawTextWithShadow(textRenderer, heading, width / 2 - textRenderer.getWidth(heading) / 2,
                42, 0xFFFFFFFF);

        if (page == Page.MINING) {
            String layout = "Mining HUD  X: " + MiningFeatures.getHudX()
                    + "  Y: " + MiningFeatures.getHudY()
                    + "  Scale: " + String.format(Locale.ROOT, "%.1fx", MiningFeatures.getHudScale());
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
