package dev.strubbonart.skyblockqol.features.dungeons.caseopening;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Cosmetic carousel for a reward already selected by the Dungeon server. */
public final class DungeonCaseOpeningScreen extends Screen {
    private static final long ANIMATION_MS = 2_800L;
    private static final int CARD_WIDTH = 88;

    private final Screen parent;
    private final DungeonDropData.CaseMaterial material;
    private final DungeonDropData.Floor floor;
    private final List<DungeonDropData.Rule> drops;
    private final DungeonDropData.Rule reward;
    private final ItemStack rewardStack;
    private final long startedAt = System.currentTimeMillis();
    private ButtonWidget continueButton;

    public DungeonCaseOpeningScreen(Screen parent,
                                    DungeonDropData.CaseMaterial material,
                                    DungeonDropData.Floor floor,
                                    List<DungeonDropData.Rule> drops,
                                    DungeonDropData.Rule reward,
                                    ItemStack rewardStack) {
        super(Text.literal("Dungeon Chest Reveal"));
        this.parent = parent;
        this.material = material;
        this.floor = floor;
        this.drops = List.copyOf(drops);
        this.reward = reward;
        this.rewardStack = rewardStack.copy();
    }

    @Override
    protected void init() {
        continueButton = ButtonWidget.builder(Text.literal("Continue"), button -> returnToParent())
                .dimensions(width / 2 - 48, height - 34, 96, 20)
                .build();
        continueButton.active = false;
        addDrawableChild(continueButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0xD90B0C18);

        context.drawCenteredTextWithShadow(textRenderer,
                material.name() + " CHEST  |  " + floor.name(), width / 2, 20, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                "Reward reveal is visual only. The server selected the reward.",
                width / 2, 38, 0xFFB8C6D9);

        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
        boolean finished = elapsed >= ANIMATION_MS;
        continueButton.active = finished;

        if (!finished && !drops.isEmpty()) {
            renderCarousel(context, elapsed);
            context.drawCenteredTextWithShadow(textRenderer, "OPENING CHEST...",
                    width / 2, height / 2 + 57, 0xFFFFD56A);
        } else {
            renderReward(context);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCarousel(DrawContext context, long elapsed) {
        int centerX = width / 2;
        int centerY = height / 2;
        context.fill(0, centerY - 62, width, centerY + 53, 0x66000000);
        double position = elapsed / 105.0;
        int baseIndex = (int) Math.floor(position);
        double fraction = position - baseIndex;

        for (int relative = -3; relative <= 3; relative++) {
            int index = Math.floorMod(baseIndex + relative, drops.size());
            DungeonDropData.Rule rule = drops.get(index);
            int x = centerX + relative * CARD_WIDTH - (int) Math.round(fraction * CARD_WIDTH);
            int y = centerY - 42;
            int color = rarityColor(rule.rarity);
            context.fill(x - 39, y, x + 39, y + 84, 0xCC171D2B);
            context.fill(x - 39, y, x + 39, y + 2, color);
            String name = displayName(rule.item.name());
            if (textRenderer.getWidth(name) > 72) {
                name = textRenderer.trimToWidth(Text.literal(name), 72).getString();
            }
            context.drawCenteredTextWithShadow(textRenderer, name, x, y + 39, color);
            context.drawCenteredTextWithShadow(textRenderer, rarityName(rule.rarity),
                    x, y + 60, 0xFFBBBBBB);
        }
        context.fill(centerX - 2, centerY - 65, centerX + 2, centerY + 55, 0xFFFFD56A);
    }

    private void renderReward(DrawContext context) {
        int centerX = width / 2;
        int centerY = height / 2 - 18;
        context.fill(centerX - 142, centerY - 73, centerX + 142, centerY + 90, 0xDD171D2B);
        context.fill(centerX - 142, centerY - 73, centerX + 142, centerY - 70, rarityColor(reward.rarity));
        context.drawCenteredTextWithShadow(textRenderer, "REWARD REVEALED",
                centerX, centerY - 55, 0xFFFFD56A);

        context.drawItem(rewardStack, centerX - 8, centerY - 25);
        context.drawItemInSlot(textRenderer, rewardStack, centerX - 8, centerY - 25);

        String name = rewardStack.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer, name, centerX,
                centerY + 4, rarityColor(reward.rarity));
        context.drawCenteredTextWithShadow(textRenderer, rarityName(reward.rarity),
                centerX, centerY + 21, 0xFFCCCCCC);
    }

    private static String rarityName(int rarity) {
        return switch (rarity) {
            case 1 -> "Pray to RNGesus";
            case 2 -> "Divine";
            case 3 -> "Mythic";
            case 4 -> "Legendary";
            case 5 -> "Epic";
            case 6 -> "Fish";
            default -> "Common";
        };
    }

    private static int rarityColor(int rarity) {
        return switch (rarity) {
            case 1 -> 0xFFFF55FF;
            case 2 -> 0xFFFFAA00;
            case 3 -> 0xFFFF5555;
            case 4 -> 0xFFAA55FF;
            case 5 -> 0xFF55AAFF;
            case 6 -> 0xFF55FFFF;
            default -> 0xFFAAAAAA;
        };
    }

    private static String displayName(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).replace('_', ' ').split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void returnToParent() {
        DungeonCaseOpeningListener.suppressWrapFor(parent);
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void close() {
        returnToParent();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
