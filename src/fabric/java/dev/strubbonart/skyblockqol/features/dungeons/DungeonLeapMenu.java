package dev.strubbonart.skyblockqol.features.dungeons;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

/**
 * Opens the custom Leap UI on top of the server's Spirit Leap/InfiniLeap chest.
 * Slot interaction remains the normal vanilla chest click, so the server
 * decides whether a particular teammate is a valid Leap destination.
 */
public final class DungeonLeapMenu {
    private DungeonLeapMenu() {}

    public static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!DungeonFeatures.isLeapMenuEnabled() || !DungeonRunOverlay.isInDungeon()) return;
            if (!(screen instanceof HandledScreen<?> handled)) return;

            String title = strip(handled.getTitle().getString());
            if (!title.equalsIgnoreCase("Spirit Leap")
                    && !title.equalsIgnoreCase("InfiniLeap")
                    && !title.equalsIgnoreCase("Infini Leap")) return;
            if (!(handled.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;

            client.setScreen(new DungeonLeapScreen(screen, handler));
        });
    }

    private static String strip(String value) {
        return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }
}
