package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Removes the first-person equip lowering when the selected item changes. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"),
            argsOnly = true, index = 7)
    private float skyblockqol$disableSwapLowering(float equipProgress) {
        return MiscQolFeatures.isNoSwapAnimationEnabled() ? 1.0F : equipProgress;
    }
}
