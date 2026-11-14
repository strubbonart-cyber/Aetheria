package dev.strubbonart.skyblockqol.features.dungeons;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom Spirit Leap / InfiniLeap interface.
 * Displays the live dungeon map and a clickable list of party members. Selecting
 * a member clicks that teammate's matching server-provided chest slot normally.
 */
public final class DungeonLeapScreen extends Screen {
    private final Screen parent;
    private final GenericContainerScreenHandler handler;
    private final List<String> partyMembers = new ArrayList<>();
    private int mapX;
    private int mapY;
    private int mapSize;
    private float mapScale;

    public DungeonLeapScreen(Screen parent, GenericContainerScreenHandler handler) {
        super(Text.literal("Dungeon Leap"));
        this.parent = parent;
        this.handler = handler;
    }

    @Override
    protected void init() {
        partyMembers.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        for (String name : DungeonMapOverlay.getOrderedPartyNames(client)) {
            if (client.player != null && name.equalsIgnoreCase(client.player.getName().getString())) continue;
            if (!partyMembers.contains(name)) partyMembers.add(name);
        }

        mapScale = Math.min(1.35F, Math.max(0.72F, (height - 92.0F) / 128.0F));
        mapSize = Math.round(128 * mapScale);
        mapX = Math.max(8, width / 2 - mapSize - 16);
        mapY = Math.max(58, height / 2 - mapSize / 2);

        int buttonX = Math.min(width - 136, width / 2 + 14);
        int buttonY = 60;
        int buttonWidth = Math.max(96, Math.min(126, width / 2 - 24));
        int buttonHeight = 22;
        for (int i = 0; i < partyMembers.size(); i++) {
            String player = partyMembers.get(i);
            int row = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(player), button -> leapToPlayer(player))
                    .dimensions(buttonX, buttonY + row * (buttonHeight + 5), buttonWidth, buttonHeight)
                    .build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> returnToParent())
                .dimensions(width / 2 - 48, height - 28, 96, 20)
                .build());
    }

    private void leapToPlayer(String playerName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return;

        int chestSlotCount = Math.min(handler.getRows() * 9, handler.slots.size());
        for (int slot = 0; slot < chestSlotCount; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;

            String displayName = strip(stack.getName().getString());
            if (!displayName.toLowerCase(Locale.ROOT).contains(playerName.toLowerCase(Locale.ROOT))) continue;

            client.interactionManager.clickSlot(
                    handler.syncId,
                    slot,
                    0,
                    SlotActionType.PICKUP,
                    client.player
            );
            // Let the server process the ordinary chest-slot click. Return to
            // the original handled screen in case the server leaves it open.
            returnToParent();
            return;
        }

        client.player.sendMessage(Text.literal("Could not find " + playerName + " in this Leap menu."), true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0xAA090F1B);
        String title = "Spirit Leap / InfiniLeap";
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Select a teammate to use their Leap destination",
                width / 2, 34, 0xFFB9D9FF);

        drawLiveMap(context);

        if (partyMembers.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No party members found",
                    Math.min(width - 140, width / 2 + 14), 62, 0xFFFFAA55);
        } else {
            context.drawTextWithShadow(textRenderer, "PARTY", Math.min(width - 136, width / 2 + 14),
                    43, 0xFF55FFFF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLiveMap(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || mapSize <= 0) return;

        ItemStack mapStack = client.player.getInventory().getStack(8);
        if (mapStack.isEmpty()) return;
        MapIdComponent mapId = mapStack.get(DataComponentTypes.MAP_ID);
        if (mapId == null) return;
        MapState state = client.world.getMapState(mapId);
        if (state == null) return;

        context.fill(mapX - 4, mapY - 4, mapX + mapSize + 4, mapY + mapSize + 4, 0xB8000000);
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(mapX, mapY, 0.0F);
        matrices.scale(mapScale, mapScale, 1.0F);
        client.gameRenderer.getMapRenderer().draw(
                matrices, context.getVertexConsumers(), mapId, state, false, 0x00F000F0);
        matrices.pop();

        drawPlayerNames(context, state, client);
    }

    private void drawPlayerNames(DrawContext context, MapState state, MinecraftClient client) {
        List<String> names = DungeonMapOverlay.getOrderedPartyNames(client);
        int index = 0;
        for (MapDecoration decoration : state.getDecorations()) {
            if (!decoration.getAssetId().getPath().startsWith("player")) continue;
            String name = decoration.name().map(Text::getString).orElse(null);
            if (name == null || name.isBlank()) {
                if (index >= names.size()) break;
                name = names.get(index);
            }
            index++;
            int px = Math.round((decoration.x() / 2.0F + 64.0F) * mapScale);
            int py = Math.round((decoration.z() / 2.0F + 64.0F) * mapScale);
            if (px < 0 || py < 0 || px > mapSize || py > mapSize) continue;
            context.drawTextWithShadow(textRenderer, name, mapX + px + 2, mapY + py - 4,
                    name.equalsIgnoreCase(client.player.getName().getString()) ? 0xFFFFFFFF : 0xFFFFE68A);
        }
    }

    private void returnToParent() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static String strip(String value) {
        return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
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
