package dev.strubbonart.skyblockqol;

import dev.strubbonart.skyblockqol.features.hud.PerformanceHud;
import dev.strubbonart.skyblockqol.features.calculator.CalculatorCommand;
import dev.strubbonart.skyblockqol.features.dungeons.DungeonFeatures;
import dev.strubbonart.skyblockqol.features.mining.MiningFeatures;
import dev.strubbonart.skyblockqol.features.roman.RomanNumeralCommands;
import dev.strubbonart.skyblockqol.features.timer.CountdownTimer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class SkyblockQolClient implements ClientModInitializer {
    private static KeyBinding toggleHudKey;

    @Override
    public void onInitializeClient() {
        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skyblockqol.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.skyblockqol.general"
        ));

        PerformanceHud.initialize();
        CountdownTimer.initialize();
        RomanNumeralCommands.register();
        CalculatorCommand.register();
        MiningFeatures.initialize();
        DungeonFeatures.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHudKey.wasPressed()) {
                boolean enabled = PerformanceHud.toggle();
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Performance HUD " + (enabled ? "enabled" : "disabled")),
                            true
                    );
                }
            }
        });
    }
}
