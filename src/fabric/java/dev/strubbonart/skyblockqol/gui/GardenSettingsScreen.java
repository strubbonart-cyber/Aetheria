package dev.strubbonart.skyblockqol.gui;

import dev.strubbonart.skyblockqol.features.farming.GardenFeatures;
import dev.strubbonart.skyblockqol.features.farming.GardenPestFeatures;
import dev.strubbonart.skyblockqol.features.farming.GardenPlotNumbers;
import dev.strubbonart.skyblockqol.features.farming.GardenOrganicMatterTracker;
import dev.strubbonart.skyblockqol.features.farming.GardenTrevorFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Dedicated settings page for the first Garden feature slice. */
public final class GardenSettingsScreen extends Screen {
    private final Screen parent;

    public GardenSettingsScreen(Screen parent) {
        super(Text.literal("Garden Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        int y = 72;
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
        addToggle("Pest Tracker", GardenPestFeatures.isTrackerEnabled(), x, y,
                () -> GardenPestFeatures.setTrackerEnabled(!GardenPestFeatures.isTrackerEnabled()));
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
        addButton("EDIT ORGANIC MATTER POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_OM_TRACKER));
        y += 26;
        addButton("EDIT PELT TRACKER POSITION", x, y, 300, 22,
                () -> openEditor(OverlayPositionEditorScreen.Target.GARDEN_PELT_TRACKER));
        y += 32;
        addButton("RESET FARMING STATS", x, y, 100, 22, GardenFeatures::resetTracker);
        addButton("RESET PEST STATS", x + 104, y, 96, 22, GardenPestFeatures::resetTracker);
        addButton("RESET OM STATS", x + 204, y, 96, 22, GardenOrganicMatterTracker::reset);
        addButton("RESET PELTS", x, y + 26, 300, 20, GardenTrevorFeatures::resetPeltTracker);
        addButton("BACK", x, height - 32, 300, 20, this::close);
    }

    private void addToggle(String label, boolean enabled, int x, int y, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + (enabled ? "ON" : "OFF")),
                button -> {
                    action.run();
                    if (client != null) client.setScreen(new GardenSettingsScreen(parent));
                }).dimensions(x, y, 300, 22).build());
    }

    private void addButton(String label, int x, int y, int w, int h, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> action.run())
                .dimensions(x, y, w, h).build());
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
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}