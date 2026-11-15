package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.farming.GardenPlotNumbers;
import dev.strubbonart.skyblockqol.features.farming.GardenVisitorFeatures;
import dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures;
import dev.strubbonart.skyblockqol.features.qol.AnvilCombineFeature;
import dev.strubbonart.skyblockqol.features.qol.PartyFinderFloorTip;
import dev.strubbonart.skyblockqol.features.qol.BrewingStandFeature;
import dev.strubbonart.skyblockqol.features.qol.ItemStackTipsFeature;
import dev.strubbonart.skyblockqol.features.qol.SlotBindsFeature;
import dev.strubbonart.skyblockqol.features.qol.InventorySearchFeature;
import dev.strubbonart.skyblockqol.features.qol.ProtectedItemFeature;
import dev.strubbonart.skyblockqol.features.qol.BetterContainersFeature;
import dev.strubbonart.skyblockqol.features.fishing.TrophyFishFeature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.screen.slot.SlotActionType;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected Slot focusedSlot;

    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void skyblockqol$hideBlankBetterContainerPane(DrawContext context, Slot slot, CallbackInfo ci) {
        if (BetterContainersFeature.isEnabled() && slot != null
                && !BetterContainersFeature.shouldRenderStack(slot.getStack())) {
            ci.cancel();
        }
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void skyblockqol$drawGardenPlotNumber(DrawContext context, Slot slot, CallbackInfo ci) {
        GardenPlotNumbers.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        ExtraQolFeatures.renderHoppitySlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        AnvilCombineFeature.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        PartyFinderFloorTip.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        ItemStackTipsFeature.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        InventorySearchFeature.renderSlot((HandledScreen<?>) (Object) this, context, slot, x, y);
        ProtectedItemFeature.renderStar((HandledScreen<?>) (Object) this, context, slot, x, y);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void skyblockqol$captureVisitorOffer(DrawContext context, int mouseX, int mouseY,
                                                 float delta, CallbackInfo ci) {
        BrewingStandFeature.captureTimer((HandledScreen<?>) (Object) this);
        TrophyFishFeature.scanOdger((HandledScreen<?>) (Object) this);
        if (focusedSlot != null && !focusedSlot.getStack().isEmpty()) {
            GardenVisitorFeatures.captureOffer((HandledScreen<?>) (Object) this, focusedSlot.getStack());
        }
        SlotBindsFeature.render((HandledScreen<?>) (Object) this, context, x, y, focusedSlot, mouseX, mouseY);
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void skyblockqol$slotBindKey(int keyCode, int scanCode, int modifiers,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (SlotBindsFeature.onKeyPressed((HandledScreen<?>) (Object) this,
                keyCode, scanCode, focusedSlot)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"), cancellable = true)
    private void skyblockqol$boundSlotSwap(Slot slot, int slotId, int button,
                                           SlotActionType actionType, CallbackInfo ci) {
        if (ProtectedItemFeature.shouldBlockSlotClick((HandledScreen<?>) (Object) this,
                slot, slotId, actionType)) {
            ci.cancel();
            return;
        }
        if (SlotBindsFeature.onShiftClick((HandledScreen<?>) (Object) this,
                slot, slotId, button, actionType)) {
            ci.cancel();
        }
    }

    @Inject(method = "removed()V", at = @At("TAIL"))
    private void skyblockqol$clearPendingSlotBind(CallbackInfo ci) {
        SlotBindsFeature.onScreenClosed((HandledScreen<?>) (Object) this);
    }
}