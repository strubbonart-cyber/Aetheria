package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.farming.GardenPlotNumbers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected int x;
    @Shadow protected int y;

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void skyblockqol$drawGardenPlotNumber(DrawContext context, Slot slot, CallbackInfo ci) {
        GardenPlotNumbers.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
    }
}