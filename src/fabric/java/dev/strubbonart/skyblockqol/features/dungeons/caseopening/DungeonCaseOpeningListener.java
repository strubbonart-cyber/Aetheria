package dev.strubbonart.skyblockqol.features.dungeons.caseopening;

import dev.strubbonart.skyblockqol.features.dungeons.DungeonFeatures;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonRunOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Cosmetic Dungeon chest reveal. The winning item is read from the server-filled
 * chest before the animation opens; this feature never rolls or changes rewards.
 */
public final class DungeonCaseOpeningListener {
    private static final int SCAN_DELAY_TICKS = 3;
    private static final int REWARD_SLOT_START = 10;
    private static final int REWARD_SLOT_END = 16;

    private static Screen pendingScreen;
    private static GenericContainerScreenHandler pendingHandler;
    private static DungeonDropData.Floor pendingFloor;
    private static DungeonDropData.CaseMaterial pendingMaterial;
    private static int pendingTicks;
    private static Screen bypassScreen;

    private DungeonCaseOpeningListener() {}

    public static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen == bypassScreen) {
                bypassScreen = null;
                return;
            }
            if (!DungeonFeatures.isCaseOpeningAnimationEnabled() || !DungeonRunOverlay.isInDungeon()) return;
            if (!(screen instanceof HandledScreen<?> handled)) return;
            if (!(handled.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;

            String title = strip(handled.getTitle().getString());
            DungeonDropData.CaseMaterial material;
            if (title.equalsIgnoreCase("Obsidian Chest")) {
                material = DungeonDropData.CaseMaterial.OBSIDIAN;
            } else if (title.equalsIgnoreCase("Bedrock Chest")) {
                material = DungeonDropData.CaseMaterial.BEDROCK;
            } else {
                return;
            }

            DungeonDropData.Floor floor =
                    DungeonDropData.Floor.fromDungeonFloor(DungeonRunOverlay.getCurrentFloor());
            if (floor == null) return;

            pendingScreen = screen;
            pendingHandler = handler;
            pendingFloor = floor;
            pendingMaterial = material;
            pendingTicks = 0;
        });

        ClientTickEvents.END_CLIENT_TICK.register(DungeonCaseOpeningListener::tick);
    }

    public static void suppressWrapFor(Screen screen) {
        bypassScreen = screen;
    }

    private static void tick(MinecraftClient client) {
        if (pendingScreen == null) return;
        if (client.currentScreen != pendingScreen || !DungeonFeatures.isCaseOpeningAnimationEnabled()
                || client.player == null || client.world == null) {
            clearPending();
            return;
        }

        if (++pendingTicks < SCAN_DELAY_TICKS) return;

        List<DungeonDropData.Rule> drops = DungeonDropData.getDrops(pendingMaterial, pendingFloor);
        if (drops.isEmpty()) {
            clearPending();
            return;
        }

        DungeonDropData.Rule selected = null;
        ItemStack selectedStack = ItemStack.EMPTY;
        int limit = Math.min(REWARD_SLOT_END, pendingHandler.getRows() * 9 - 1);
        for (int slot = REWARD_SLOT_START; slot <= limit; slot++) {
            ItemStack stack = pendingHandler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;
            String id = getSkyblockId(stack);
            if (id.isEmpty()) continue;

            DungeonDropData.Rule match = drops.stream()
                    .filter(rule -> rule.item.name().equals(id))
                    .min(Comparator.comparingInt((DungeonDropData.Rule rule) -> rule.rarity)
                            .thenComparing(rule -> rule.item.name()))
                    .orElse(null);
            if (match == null) continue;

            if (selected == null || match.rarity < selected.rarity
                    || (match.rarity == selected.rarity
                    && match.item.name().compareTo(selected.item.name()) < 0)) {
                selected = match;
                selectedStack = stack.copy();
            }
        }

        Screen parent = pendingScreen;
        DungeonDropData.Floor floor = pendingFloor;
        DungeonDropData.CaseMaterial material = pendingMaterial;
        clearPending();

        if (selected != null) {
            client.setScreen(new DungeonCaseOpeningScreen(parent, material, floor, drops, selected, selectedStack));
        }
    }

    private static String getSkyblockId(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return "";
        NbtCompound customData = component.copyNbt();
        if (customData.contains("ExtraAttributes")) {
            String id = customData.getCompound("ExtraAttributes").getString("id");
            if (!id.isBlank()) return id;
        }
        return customData.getString("id");
    }

    private static void clearPending() {
        pendingScreen = null;
        pendingHandler = null;
        pendingFloor = null;
        pendingMaterial = null;
        pendingTicks = 0;
    }

    private static String strip(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }
}
