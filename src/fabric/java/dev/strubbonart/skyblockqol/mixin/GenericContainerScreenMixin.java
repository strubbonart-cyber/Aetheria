package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.qol.BetterContainersFeature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirects generic chest backgrounds to Aetheria's generated connected-texture image. */
@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin {
    @ModifyArg(
            method = "drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIFFIIII)V"
            ),
            index = 0
    )
    private Identifier skyblockqol$betterContainerBackground(Identifier original) {
        return BetterContainersFeature.getBackgroundTexture(
                (GenericContainerScreen) (Object) this, original);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void skyblockqol$drawWatermark(DrawContext context, int mouseX, int mouseY,
                                           float delta, CallbackInfo ci) {
        BetterContainersFeature.renderWatermark(context, (GenericContainerScreen) (Object) this);
    }
}
