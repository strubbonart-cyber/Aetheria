package dev.strubbonart.skyblockqol.gui;

import dev.strubbonart.skyblockqol.features.farming.GardenFeatures;
import dev.strubbonart.skyblockqol.features.farming.GardenPestFeatures;
import dev.strubbonart.skyblockqol.features.farming.GardenPlotNumbers;
import dev.strubbonart.skyblockqol.features.farming.GardenOrganicMatterTracker;
import dev.strubbonart.skyblockqol.features.farming.GardenTrevorFeatures;
import dev.strubbonart.skyblockqol.features.farming.GardenMouseFeatures;
import dev.strubbonart.skyblockqol.features.farming.GardenVisitorFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Dedicated settings page for the first Garden feature slice. */
public final class GardenSettingsScreen extends Screen {
    private final Screen parent;
    private int scrollOffset;
    private int contentBottom;

    public GardenSettingsScreen(Screen parent) {
        super(Text.literal("Garden Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        int y = 72 - scrollOffset;
        contentBottom = y;
        addToggle("Blocks-per-second HUD", GardenFeatures.isBpsEnabled(), x, y,
                () -> GardenFeatures.setBpsEnabled(!GardenFeatures.isBpsEnabled()));
        y += 28;
        addToggle("Farming harvest tracker", GardenFeatures.isTrackerEnabled(), x, y,
                () -> GardenFeatures.setTrackerEnabled(!GardenFeatures.isTrackerEnabled()));
        y += 28;
        addToggle("Precise yaw / pitch HUD", GardenFeatures.isPitchEnabled(), x, y,
                () -> GardenFeatures.setPitchEnabled(!GardenFeatures.isPitchEnabled()));
        y += 28;
        addToggle("Garden plot numbers", GardenPlotNumbers.isEnabled(), x, y,
                () -> GardenPlotNumbers.setEnabled(!GardenPlotNumbers.isEnabled()));
        y += 28;
        addToggle("Highlight unlocked / buyable plots", GardenPlotNumbers.isHighlightSlotsEnabled(), x, y,
                () -> GardenPlotNumbers.setHighlightSlotsEnabled(!GardenPlotNumbers.isHighlightSlotsEnabled()));
        y += 28;
        addToggle("Organic Matter Tracker", GardenOrganicMatterTracker.isEnabled(), x, y,
                () -> GardenOrganicMatterTracker.setEnabled(!GardenOrganicMatterTracker.isEnabled()));
        y += 28;
        addToggle("Trevor Solver (spawn markers)", GardenTrevorFeatures.isSolverEnabled(), x, y,
                () -> GardenTrevorFeatures.setSolverEnabled(!GardenTrevorFeatures.isSolverEnabled()));
        y += 28;
        addToggle("Trevor Pelt Tracker", GardenTrevorFeatures.isPeltTrackerEnabled(), x, y,
                () -> GardenTrevorFeatures.setPeltTrackerEnabled(!GardenTrevorFeatures.isPeltTrackerEnabled()));
        y += 28;
        addToggle("Trapper warp within 5s after hunt (default F)",
                GardenTrevorFeatures.isTrapperWarpEnabled(), x, y,
                () -> GardenTrevorFeatures.setTrapperWarpEnabled(!GardenTrevorFeatures.isTrapperWarpEnabled()));
        y += 28;
        addToggle("Lock camera yaw / pitch", GardenMouseFeatures.isLockEnabled(), x, y,
                () -> GardenMouseFeatures.setLockEnabled(!GardenMouseFeatures.isLockEnabled()));
        y += 28;
        addToggle("Reduce sensitivity with farming tools", GardenMouseFeatures.isSensitivityEnabled(), x, y,
                () -> GardenMouseFeatures.setSensitivityEnabled(!GardenMouseFeatures.isSensitivityEnabled()));
        y += 28;
        addToggle("Sensitivity requires Garden", GardenMouseFeatures.isRequireGarden(), x, y,
                () -> GardenMouseFeatures.setRequireGarden(!GardenMouseFeatures.isRequireGarden()));
        y += 28;
        addToggle("Visitor shopping list", GardenVisitorFeatures.isEnabled(), x, y,
                () -> GardenVisitorFeatures.setEnabled(!GardenVisitorFeatures.isEnabled()));
        y += 28;
        addButton("Sensitivity: " + GardenMouseFeatures.getSensitivityPercent() + "%  (-5)",
                x, y, 146, 22, () -> GardenMouseFeatures.adjustSensitivityPercent(-5));
        addButton("Sensitivity: " + GardenMouseFeatures.getSensitivityPercent() + "%  (+5)",
                x + 154, y, 146, 22, () -> GardenMouseFeatures.adjustSensitivityPercent(5));
        y += 28;
        addToggle("Pest Tracker", GardenPestFeatures.isTrackerEnabled(), x, y,
                () -> GardenPestFeatures.setTrackerEnabled(!GardenPestFeatures.isTrackerEnabled()));
        y += 28;
        addToggle("Pest Finder info HUD", GardenPestFeatures.isFinderEnabled(), x, y,
                () -> GardenPestFeatures.setFinderEnabled(!GardenPestFeatures.isFinderEnabled()));
        y += 28;
        addToggle("Show Pest Finder only while holding vacuum", GardenPestFeatures.isShowFinderOnlyWithVacuum(), x, y,
                () -> GardenPestFeatures.setShowFinderOnlyWithVacuum(!GardenPestFeatures.isShowFinderOnlyWithVacuum()));
        y += 28;
        addToggle("Pest Finder warp key", GardenPestFeatures.isWarpEnabled(), x, y,
                () -> GardenPestFeatures.setWarpEnabled(!GardenPestFeatures.isWarpEnabled()));
        y += 28;
        addButton("Warp target: " + (GardenPestFeatures.getWarpTargetMode() == 0 ? "CLOSEST" : "MOST PESTS"),
                x, y, 300, 22, GardenPestFeatures::cycleWarpTargetMode);
        y += 28;
        addToggle("Pest Cooldown Alert", GardenPestFeatures.isCooldownAlertEnabled(), x, y,
                () -> GardenPestFeatures.setCooldownAlertEnabled(!GardenPestFeatures.isCooldownAlertEnabled()));
        y += 28;
        addButton("Cooldown threshold: " + GardenPestFeatures.getCooldownThreshold() + "s  (-5)",
                x, y, 146, 22, () -> GardenPestFeatures.adjustCooldownThreshold(-5));
        addButton("Cooldown threshold: " + GardenPestFeatures.getCooldownThreshold() + "s  (+5)",
                x + 154, y, 146, 22, () -> GardenPestFeatures.adjustCooldownThreshold(5));
        y += 32;
        addButton("EDIT BPS POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_BPS));
        y += 26;
        addButton("EDIT FARMING TRACKER POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_TRACKER));
        y += 26;
        addButton("EDIT YAW / PITCH POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_PITCH_YAW));
        y += 26;
        addButton("EDIT PEST TRACKER POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_PEST_TRACKER));
        y += 26;
        addButton("EDIT PEST FINDER POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_PEST_FINDER));
        y += 26;
        addButton("EDIT ORGANIC MATTER POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_OM_TRACKER));
        y += 26;
        addButton("EDIT PELT TRACKER POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_PELT_TRACKER));
        y += 26;
        addButton("EDIT VISITOR LIST POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_VISITOR_LIST));
        y += 32;
        addButton("RESET FARMING STATS", x, y, 100, 22, GardenFeatures::resetTracker);
        addButton("RESET PEST STATS", x + 104, y, 96, 22, GardenPestFeatures::resetTracker);
        addButton("RESET OM STATS", x + 204, y, 96, 22, GardenOrganicMatterTracker::reset);
        addButton("RESET PELTS", x, y + 26, 300, 20, GardenTrevorFeatures::resetPeltTracker);
        addButton("BACK", x, height - 32, 300, 20, this::close);
        contentBottom = y + scrollOffset + 52;
    }

    private void addToggle(String label, boolean enabled, int x, int y, Runnable action) {
        ButtonWidget widget = ButtonWidget.builder(Text.literal(label + ": " + (enabled ? "ON" : "OFF")),
                button -> {
                    action.run();
                    refreshSettings();
                }).dimensions(x, y, 300, 22).build();
        widget.visible = isInsideContent(widget.getY(), widget.getHeight());
        addDrawableChild(widget);
    }

    private void addButton(String label, int x, int y, int w, int h, Runnable action) {
        ButtonWidget widget = ButtonWidget.builder(Text.literal(label), button -> action.run())
                .dimensions(x, y, w, h).build();
        widget.visible = label.equals("BACK") || isInsideContent(widget.getY(), widget.getHeight());
        addDrawableChild(widget);
    }

    private boolean isInsideContent(int buttonY, int buttonHeight) {
        return buttonY >= 64 && buttonY + buttonHeight <= height - 42;
    }

    private void refreshSettings() {
        if (client == null) return;
        GardenSettingsScreen next = new GardenSettingsScreen(parent);
        next.scrollOffset = scrollOffset;
        client.setScreen(next);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= 60 && mouseY <= height - 42) {
            int maxScroll = Math.max(0, contentBottom - (height - 48));
            int next = Math.max(0, Math.min(maxScroll,
                    scrollOffset + (verticalAmount < 0 ? 34 : -34)));
            if (next != scrollOffset) {
                scrollOffset = next;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void openEditor(OverlayPositionEditorScreen.Target target) {
        MinecraftClient.getInstance().setScreen(new OverlayPositionEditorScreen(this, target));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                "Harvest counts record mature crop-block breaks, not exact drop stacks.",
                width / 2, 48, 0xFFB8D8FF);
        int maxScroll = Math.max(0, contentBottom - (height - 48));
        if (maxScroll > 0) {
            context.drawTextWithShadow(textRenderer, "Scroll to see more settings",
                    width / 2 + 165, 58, 0xFFB8D8FF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}