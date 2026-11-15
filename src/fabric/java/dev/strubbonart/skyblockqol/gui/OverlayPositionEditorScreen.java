package dev.strubbonart.skyblockqol.gui;

import dev.strubbonart.skyblockqol.features.dungeons.DungeonMapOverlay;
import dev.strubbonart.skyblockqol.features.farming.GardenFeatures;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonOverlaySettings;
import dev.strubbonart.skyblockqol.features.mining.GhostTrackerFeatures;
import dev.strubbonart.skyblockqol.features.mining.MiningFeatures;
import dev.strubbonart.skyblockqol.features.pets.CurrentPetFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Common drag-and-drop editor used by every HUD overlay.
 * Positions are top-left coordinates in Minecraft's scaled GUI pixels.
 */
public final class OverlayPositionEditorScreen extends Screen {
    public enum Target {
        FETCHUR, POWDER, PRISTINE, GOLD, GHOST,
        DUNGEON_MAP, DUNGEON_TIMER, DUNGEON_SPLITS, DUNGEON_BREAKER, DUNGEON_ROOM,
        CURRENT_PET, GARDEN_BPS, GARDEN_TRACKER, GARDEN_PITCH_YAW
    }

    private final Screen parent;
    private final Target target;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean dirty;

    public OverlayPositionEditorScreen(Screen parent, Target target) {
        super(Text.literal("Edit Overlay Position"));
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        int y = height - 34;
        addButton("SIZE -", width / 2 - 164, y, 72, 20,
                () -> changeScale(-0.1F));
        addButton("SIZE +", width / 2 - 84, y, 72, 20,
                () -> changeScale(0.1F));
        addButton("RESET", width / 2 + 2, y, 72, 20,
                this::resetLayout);
        addButton("DONE", width / 2 + 84, y, 80, 20,
                this::close);
    }

    private void addButton(String label, int x, int y, int w, int h, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
            action.run();
            if (client != null && client.currentScreen == this) {
                client.setScreen(new OverlayPositionEditorScreen(parent, target));
            }
        }).dimensions(x, y, w, h).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        String title = "EDIT POSITION: " + target.name().replace('_', ' ');
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                "Click and hold the overlay preview, then drag it anywhere.",
                width / 2, 42, 0xFFB8D8FF);

        int x = getX();
        int y = getY();
        float scale = getScale();
        int previewWidth = getPreviewWidth();
        int previewHeight = getPreviewHeight();

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.fill(0, 0, previewWidth, previewHeight, 0xD91B2634);
        context.fill(0, 0, previewWidth, 2, 0xFF55DDEE);
        context.fill(0, previewHeight - 2, previewWidth, previewHeight, 0xFF55DDEE);
        context.fill(0, 0, 2, previewHeight, 0xFF55DDEE);
        context.fill(previewWidth - 2, 0, previewWidth, previewHeight, 0xFF55DDEE);

        String[] lines = getPreviewLines();
        int textY = 6;
        for (int i = 0; i < lines.length && textY < previewHeight - 10; i++) {
            context.drawTextWithShadow(textRenderer, lines[i], 7, textY,
                    i == 0 ? 0xFFFFFFFF : 0xFFD8E6F5);
            textY += 11;
        }
        context.drawTextWithShadow(textRenderer, "DRAG TO MOVE", 7,
                Math.max(6, previewHeight - 12), 0xFF55DDEE);
        context.getMatrices().pop();

        String coords = "X: " + x + "   Y: " + y
                + "   Scale: " + String.format(java.util.Locale.ROOT, "%.1fx", scale);
        context.drawCenteredTextWithShadow(textRenderer, coords, width / 2, height - 52, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insidePreview(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - getX();
            dragOffsetY = mouseY - getY();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (dragging && button == 0) {
            int maxX = Math.max(0, width - (int) (getPreviewWidth() * getScale()));
            int maxY = Math.max(0, height - 64 - (int) (getPreviewHeight() * getScale()));
            int x = (int) Math.round(mouseX - dragOffsetX);
            int y = (int) Math.round(mouseY - dragOffsetY);
            setPosition(Math.max(0, Math.min(maxX, x)), Math.max(0, Math.min(maxY, y)));
            dirty = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            saveLayout();
            dirty = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        int w = Math.max(1, (int) (getPreviewWidth() * getScale()));
        int h = Math.max(1, (int) (getPreviewHeight() * getScale()));
        return mouseX >= getX() && mouseX <= getX() + w
                && mouseY >= getY() && mouseY <= getY() + h;
    }

    private void changeScale(float delta) {
        float maximum = target == Target.DUNGEON_MAP ? 2.0F : 3.0F;
        float next = Math.max(0.5F, Math.min(maximum,
                Math.round((getScale() + delta) * 10.0F) / 10.0F));
        setLayout(getX(), getY(), next);
        saveLayout();
    }

    private int getX() {
        return switch (target) {
            case FETCHUR, POWDER, PRISTINE, GOLD ->
                    MiningFeatures.getHudX(target.name().toLowerCase(java.util.Locale.ROOT));
            case GHOST -> GhostTrackerFeatures.getHudX();
            case DUNGEON_MAP -> DungeonMapOverlay.getResolvedPositionX(width);
            case DUNGEON_TIMER, DUNGEON_SPLITS, DUNGEON_BREAKER, DUNGEON_ROOM ->
                    DungeonOverlaySettings.x(dungeonWidget());
            case CURRENT_PET -> CurrentPetFeature.getHudX();
            case GARDEN_BPS, GARDEN_TRACKER, GARDEN_PITCH_YAW -> GardenFeatures.getLayoutX(gardenLayoutKey());
        };
    }

    private int getY() {
        return switch (target) {
            case FETCHUR, POWDER, PRISTINE, GOLD ->
                    MiningFeatures.getHudY(target.name().toLowerCase(java.util.Locale.ROOT));
            case GHOST -> GhostTrackerFeatures.getHudY();
            case DUNGEON_MAP -> DungeonMapOverlay.getPositionY();
            case DUNGEON_TIMER, DUNGEON_SPLITS, DUNGEON_BREAKER, DUNGEON_ROOM ->
                    DungeonOverlaySettings.y(dungeonWidget());
            case CURRENT_PET -> CurrentPetFeature.getHudY();
            case GARDEN_BPS, GARDEN_TRACKER, GARDEN_PITCH_YAW -> GardenFeatures.getLayoutY(gardenLayoutKey());
        };
    }

    private float getScale() {
        return switch (target) {
            case FETCHUR, POWDER, PRISTINE, GOLD ->
                    MiningFeatures.getHudScale(target.name().toLowerCase(java.util.Locale.ROOT));
            case GHOST -> GhostTrackerFeatures.getHudScale();
            case DUNGEON_MAP -> DungeonMapOverlay.getScale();
            case DUNGEON_TIMER, DUNGEON_SPLITS, DUNGEON_BREAKER, DUNGEON_ROOM ->
                    DungeonOverlaySettings.scale(dungeonWidget());
            case CURRENT_PET -> CurrentPetFeature.getHudScale();
            case GARDEN_BPS, GARDEN_TRACKER, GARDEN_PITCH_YAW -> GardenFeatures.getLayoutScale(gardenLayoutKey());
        };
    }

    private void setPosition(int x, int y) {
        setLayout(x, y, getScale());
    }

    private void setLayout(int x, int y, float scale) {
        switch (target) {
            case FETCHUR, POWDER, PRISTINE, GOLD ->
                    MiningFeatures.updateHudLayout(target.name().toLowerCase(java.util.Locale.ROOT), x, y, scale);
            case GHOST -> GhostTrackerFeatures.updateHudLayout(x, y, scale);
            case DUNGEON_MAP -> DungeonMapOverlay.updateLayout(x, y, scale);
            case DUNGEON_TIMER, DUNGEON_SPLITS, DUNGEON_BREAKER, DUNGEON_ROOM ->
                    DungeonOverlaySettings.updateLayout(dungeonWidget(), x, y, scale);
            case CURRENT_PET -> CurrentPetFeature.updateHudLayout(x, y, scale);
            case GARDEN_BPS, GARDEN_TRACKER, GARDEN_PITCH_YAW -> GardenFeatures.updateLayout(gardenLayoutKey(), x, y, scale);
        }
        dirty = true;
    }

    private void saveLayout() {
        switch (target) {
            case FETCHUR, POWDER, PRISTINE, GOLD -> MiningFeatures.saveHudLayout();
            case GHOST -> GhostTrackerFeatures.saveHudLayout();
            case DUNGEON_MAP -> DungeonMapOverlay.saveLayout();
            case DUNGEON_TIMER, DUNGEON_SPLITS, DUNGEON_BREAKER, DUNGEON_ROOM ->
                    DungeonOverlaySettings.saveLayout();
            case CURRENT_PET -> CurrentPetFeature.saveHudLayout();
            case GARDEN_BPS, GARDEN_TRACKER, GARDEN_PITCH_YAW -> GardenFeatures.saveLayout();
        }
        dirty = false;
    }

    private void resetLayout() {
        switch (target) {
            case FETCHUR, POWDER, PRISTINE, GOLD ->
                    MiningFeatures.resetHudLayout(target.name().toLowerCase(java.util.Locale.ROOT));
            case GHOST -> GhostTrackerFeatures.resetHudLayout();
            case DUNGEON_MAP -> DungeonMapOverlay.resetLayout();
            case DUNGEON_TIMER -> DungeonOverlaySettings.set(DungeonOverlaySettings.Widget.TIMER, 8, 156, 1.0F);
            case DUNGEON_SPLITS -> DungeonOverlaySettings.set(DungeonOverlaySettings.Widget.SPLITS, 8, 178, 1.0F);
            case DUNGEON_BREAKER -> DungeonOverlaySettings.set(DungeonOverlaySettings.Widget.BREAKER, 8, 112, 1.0F);
            case DUNGEON_ROOM -> DungeonOverlaySettings.set(DungeonOverlaySettings.Widget.ROOM, 8, 212, 1.0F);
            case CURRENT_PET -> CurrentPetFeature.resetHudLayout();
            case GARDEN_BPS, GARDEN_TRACKER, GARDEN_PITCH_YAW -> GardenFeatures.resetLayout(gardenLayoutKey());
        }
        if (client != null && client.currentScreen == this) {
            client.setScreen(new OverlayPositionEditorScreen(parent, target));
        }
    }

    private String gardenLayoutKey() {
        return switch (target) {
            case GARDEN_BPS -> "bps";
            case GARDEN_TRACKER -> "tracker";
            case GARDEN_PITCH_YAW -> "pitch";
            default -> "bps";
        };
    }

    private DungeonOverlaySettings.Widget dungeonWidget() {
        return switch (target) {
            case DUNGEON_TIMER -> DungeonOverlaySettings.Widget.TIMER;
            case DUNGEON_SPLITS -> DungeonOverlaySettings.Widget.SPLITS;
            case DUNGEON_BREAKER -> DungeonOverlaySettings.Widget.BREAKER;
            default -> DungeonOverlaySettings.Widget.ROOM;
        };
    }

    private int getPreviewWidth() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int textWidth = 0;
        for (String line : getPreviewLines()) textWidth = Math.max(textWidth, mc.textRenderer.getWidth(line));
        return target == Target.DUNGEON_MAP ? 128 : Math.max(112, Math.min(300, textWidth + 16));
    }

    private int getPreviewHeight() {
        return target == Target.DUNGEON_MAP ? 128 : Math.max(28, getPreviewLines().length * 11 + 12);
    }

    private String[] getPreviewLines() {
        return switch (target) {
            case FETCHUR -> new String[]{"Fetchur: Example Item"};
            case POWDER -> new String[]{"Powder: Gemstone | Mithril", "Chests: 0 | Gems: 0", "Mining active: 00:00"};
            case PRISTINE -> new String[]{"Pristine: 0 gems | 0 procs", "Ruby 0 | Sapphire 0"};
            case GOLD -> new String[]{"Gold: 0 ingots (0/h)", "Raw: 0 | Enchanted: 0"};
            case GHOST -> new String[]{"Ghost Tracker", "Kills: 0 | Combat XP: 0", "Sorrow 0 | Volta 0 | Plasma 0"};
            case DUNGEON_MAP -> new String[]{"Dungeon Map"};
            case DUNGEON_TIMER -> new String[]{"Dungeon F7  00:00"};
            case DUNGEON_SPLITS -> new String[]{"Dungeon Splits", "Blood: --  Boss: --"};
            case DUNGEON_BREAKER -> new String[]{"Dungeon Breaker", "Charges: 0/0"};
            case DUNGEON_ROOM -> new String[]{"Dungeon Room", "Secrets: 0 / 0"};
            case CURRENT_PET -> new String[]{"Pet: [Lvl 100] Example Pet"};
            case GARDEN_BPS -> new String[]{"Garden BPS", "Blocks broken: 0", "Rate: 0.0 blocks/s"};
            case GARDEN_TRACKER -> new String[]{"Farming Tracker", "Crop blocks: 0", "Session rate: 0/hour"};
            case GARDEN_PITCH_YAW -> new String[]{"Yaw: 0.00°", "Pitch: 0.00°"};
        };
    }

    @Override
    public void close() {
        if (dirty) saveLayout();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
