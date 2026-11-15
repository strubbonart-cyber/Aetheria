package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.calculator.SignCalculatorFeature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a live expression preview and writes the evaluated result on sign submit. */
@Mixin(AbstractSignEditScreen.class)
public abstract class SignCalculatorMixin {
    @Shadow @Final private String[] messages;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void skyblockqol$renderCalculatorPreview(DrawContext context, int mouseX, int mouseY,
                                                     float delta, CallbackInfo ci) {
        String preview = SignCalculatorFeature.preview(messages);
        if (preview == null) return;
        int color = preview.contains(" = ") && !preview.endsWith("Result too long")
                ? 0xFF55DD88 : 0xFFFF7777;
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal(preview), context.getScaledWindowWidth() / 2, 56, color);
    }

    @Inject(method = "finishEditing()V", at = @At("HEAD"))
    private void skyblockqol$applySignCalculation(CallbackInfo ci) {
        SignCalculatorFeature.apply(messages);
    }
}
