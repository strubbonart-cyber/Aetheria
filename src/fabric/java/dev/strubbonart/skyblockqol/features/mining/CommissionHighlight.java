package dev.strubbonart.skyblockqol.features.mining;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Fabric port of the original Aetheria Commission Highlight.
 * Completed commission items receive a translucent green slot overlay.
 */
public final class CommissionHighlight {
    private static final int COMPLETED_COLOR = 0x8000FF00;
    private static boolean enabled = true;

    private CommissionHighlight() {}

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("commissionhighlight").executes(context -> {
                    boolean active = toggle();
                    context.getSource().sendFeedback(Text.literal("Commission highlight " + (active ? "enabled" : "disabled")));
                    return 1;
                })));
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof HandledScreen<?> handled)) return;
            if (!screen.getTitle().getString().contains("Commissions")) return;

            ScreenEvents.afterRender(screen).register((renderedScreen, context, mouseX, mouseY, tickDelta) -> {
                if (!enabled || !(renderedScreen instanceof HandledScreen<?> handledScreen)) return;
                int totalSlots = handledScreen.getScreenHandler().slots.size();
                int containerSlots = totalSlots - 36;
                if (containerSlots <= 0 || containerSlots % 9 != 0) return;

                // Generic container screens use a 176px width and 114 + 18*rows height.
                int rows = containerSlots / 9;
                int left = (handledScreen.width - 176) / 2;
                int top = (handledScreen.height - (114 + rows * 18)) / 2;

                for (int i = 0; i < containerSlots; i++) {
                    Slot slot = handledScreen.getScreenHandler().slots.get(i);
                    if (!isCompleted(slot.getStack())) continue;
                    int x = left + slot.x;
                    int y = top + slot.y;
                    context.fill(x, y, x + 16, y + 16, COMPLETED_COLOR);
                }
            });
        });
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    private static boolean isCompleted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        for (Text line : lore.lines()) {
            if ("COMPLETED".equals(line.getString().trim())) return true;
        }
        return false;
    }
}
