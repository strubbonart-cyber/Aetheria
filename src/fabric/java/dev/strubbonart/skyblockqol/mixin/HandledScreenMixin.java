package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.farming.GardenPlotNumbers;
import dev.strubbonart.skyblockqol.features.farming.GardenVisitorFeatures;
import dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures;
import dev.strubbonart.skyblockqol.features.qol.AnvilCombineFeature;
import dev.strubbonart.skyblockqol.features.qol.PartyFinderFloorTip;
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
    @Shadow protected Slot focusedSlot;

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void skyblockqol$drawGardenPlotNumber(DrawContext context, Slot slot, CallbackInfo ci) {
        GardenPlotNumbers.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        ExtraQolFeatures.renderHoppitySlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        AnvilCombineFeature.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        PartyFinderFloorTip.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void skyblockqol$captureVisitorOffer(DrawContext context, int mouseX, int mouseY,
                                                 float delta, CallbackInfo ci) {
        if (focusedSlot != null && !focusedSlot.getStack().isEmpty()) {
            GardenVisitorFeatures.captureOffer((HandledScreen<?>) (Object) this, focusedSlot.getStack());
        }
    }
}