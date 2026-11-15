package dev.strubbonart.skyblockqol.features.qol;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.Locale;

/** Highlights inventory items matching the single item currently placed in an Anvil input. */
public final class AnvilCombineFeature {
    private static boolean enabled = true;
    private static final int LEFT_INPUT_SLOT = 29;
    private static final int RIGHT_INPUT_SLOT = 33;
    private static final int HIGHLIGHT = 0xB500E85A;

    private AnvilCombineFeature() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static void renderSlot(HandledScreen<?> screen, DrawContext context, Slot slot,
                                  int screenX, int screenY) {
        if (!enabled || slot == null) return;
        String title = strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!title.equals("anvil")) return;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;
        if (handler.slots.size() <= RIGHT_INPUT_SLOT) return;

        ItemStack left = handler.slots.get(LEFT_INPUT_SLOT).getStack();
        ItemStack right = handler.slots.get(RIGHT_INPUT_SLOT).getStack();
        boolean leftFilled = !left.isEmpty();
        boolean rightFilled = !right.isEmpty();
        if (leftFilled == rightFilled) return;

        ItemStack target = leftFilled ? left : right;
        String targetId = skyblockId(target);
        if (targetId.isEmpty()) return;

        int playerInventoryStart = handler.getRows() * 9;
        if (slot.id < playerInventoryStart || slot.getStack().isEmpty()) return;
        if (!targetId.equals(skyblockId(slot.getStack()))) return;

        int x = screenX + slot.x;
        int y = screenY + slot.y;
        context.fill(x, y, x + 16, y + 2, HIGHLIGHT);
        context.fill(x, y + 14, x + 16, y + 16, HIGHLIGHT);
        context.fill(x, y, x + 2, y + 16, HIGHLIGHT);
        context.fill(x + 14, y, x + 16, y + 16, HIGHLIGHT);
    }

    private static String skyblockId(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return "";
        NbtCompound root = customData.copyNbt();
        NbtCompound extra = root.contains("ExtraAttributes") ? root.getCompound("ExtraAttributes") : root;
        return extra.getString("id").trim().toUpperCase(Locale.ROOT);
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }
}