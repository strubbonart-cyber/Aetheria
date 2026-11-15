package dev.strubbonart.skyblockqol.gui;

import dev.strubbonart.skyblockqol.features.pets.CurrentPetFeature;
import dev.strubbonart.skyblockqol.features.pets.PetCollectionFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Read-only browser for pets discovered in the player's Pets menu. */
public final class PetCollectionScreen extends Screen {
    private final Screen parent;
    private int page;

    public PetCollectionScreen(Screen parent) {
        super(Text.literal("Pet Collection"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("PREVIOUS"), button -> {
            page = Math.max(0, page - 1);
        }).dimensions(width / 2 - 154, height - 34, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("NEXT"), button -> {
            page++;
        }).dimensions(width / 2 - 49, height - 34, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("BACK"), button ->
                MinecraftClient.getInstance().setScreen(parent))
                .dimensions(width / 2 + 56, height - 34, 95, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFFFF);
        String current = CurrentPetFeature.getPetName().isBlank()
                ? "Unknown" : "[Lvl " + CurrentPetFeature.getPetLevel() + "] " + CurrentPetFeature.getPetName();
        context.drawCenteredTextWithShadow(textRenderer, "Active pet: " + current,
                width / 2, 40, 0xFFFFDD88);

        List<PetCollectionFeature.PetEntry> pets = PetCollectionFeature.getEntries();
        int pageSize = 12;
        int pages = Math.max(1, (pets.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pages - 1));
        context.drawCenteredTextWithShadow(textRenderer,
                pets.size() + " pets discovered  |  Page " + (page + 1) + "/" + pages,
                width / 2, 58, 0xFFB8D8FF);

        if (pets.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    "Open the Hypixel Pets menu to populate this collection.",
                    width / 2, 104, 0xFFAAAAAA);
        } else {
            int start = page * pageSize;
            int shown = Math.min(pageSize, pets.size() - start);
            int y = 86;
            for (int i = 0; i < shown; i++) {
                PetCollectionFeature.PetEntry pet = pets.get(start + i);
                String marker = pet.active() ? "  [ACTIVE]" : "";
                String skin = pet.specialSkin() ? "  ✦" : "";
                Text line = Text.literal("[Lvl " + pet.level() + "] " + pet.name() + skin + marker)
                        .styled(style -> style.withColor(pet.rarityColor()));
                context.drawTextWithShadow(textRenderer, line, width / 2 - 140, y, 0xFFFFFFFF);
                y += 20;
            }
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