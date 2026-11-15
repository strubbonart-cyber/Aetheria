package dev.strubbonart.skyblockqol;

import dev.strubbonart.skyblockqol.gui.SkyblockQolSettingsScreen;
import dev.strubbonart.skyblockqol.features.qol.QolFeatures;
import dev.strubbonart.skyblockqol.features.qol.EnchantTooltipFeature;
import dev.strubbonart.skyblockqol.features.pets.CurrentPetFeature;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonFeatures;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonRunOverlay;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonPhaseTracker;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonMobHighlights;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonLeapMenu;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonDataRepository;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonMapOverlay;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonRoomScanner;
import dev.strubbonart.skyblockqol.features.mining.MiningFeatures;
import dev.strubbonart.skyblockqol.features.mining.PickobulusPreview;
import dev.strubbonart.skyblockqol.features.mining.CommissionHighlight;
import dev.strubbonart.skyblockqol.features.mining.GhostTrackerFeatures;
import dev.strubbonart.skyblockqol.features.mining.HotmPowderTooltip;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class SkyblockQolClient implements ClientModInitializer {
    private static KeyBinding openSettingsKey;

    @Override
    public void onInitializeClient() {
        QolFeatures.initialize();
        EnchantTooltipFeature.initialize();
        CurrentPetFeature.initialize();
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skyblockqol.open_settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.skyblockqol"
        ));

        MiningFeatures.initialize();
        PickobulusPreview.initialize();
        CommissionHighlight.initialize();
        HotmPowderTooltip.initialize();
        GhostTrackerFeatures.initialize();

        DungeonFeatures.initialize();
        DungeonRunOverlay.initialize();
        DungeonPhaseTracker.initialize();
        DungeonDataRepository.initialize();
        DungeonMapOverlay.initialize();
        DungeonRoomScanner.initialize();
        DungeonMobHighlights.initialize();
        DungeonLeapMenu.initialize();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("skyblockqol")
                        .executes(context -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            client.setScreen(new SkyblockQolSettingsScreen(client.currentScreen));
                            context.getSource().sendFeedback(Text.literal("Opened Mining & Dungeon settings."));
                            return 1;
                        })));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new SkyblockQolSettingsScreen(null));
                }
            }
        });
    }
}
