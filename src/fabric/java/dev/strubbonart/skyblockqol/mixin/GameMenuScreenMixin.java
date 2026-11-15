package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.qol.QolFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin {
    private static long skyblockqol$lastDisconnectClick;

    @Inject(method = "disconnect", at = @At("HEAD"), cancellable = true)
    private void skyblockqol$confirmDisconnect(CallbackInfo ci) {
        if (!QolFeatures.isConfirmDisconnectEnabled()) {
            skyblockqol$lastDisconnectClick = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - skyblockqol$lastDisconnectClick > 3000L) {
            skyblockqol$lastDisconnectClick = now;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(
                        Text.literal("Click Disconnect again within 3 seconds to confirm.")
                                .formatted(Formatting.RED)
                );
            }
            ci.cancel();
            return;
        }

        skyblockqol$lastDisconnectClick = 0L;
    }
}
