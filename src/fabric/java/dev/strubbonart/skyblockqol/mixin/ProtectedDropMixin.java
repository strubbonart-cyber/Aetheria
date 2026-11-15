package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.qol.ProtectedItemFeature;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks dropping the currently selected hotbar stack when it is protected. */
@Mixin(ClientPlayerEntity.class)
public abstract class ProtectedDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void skyblockqol$blockProtectedDrop(boolean entireStack,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (ProtectedItemFeature.shouldBlockSelectedDrop()) cir.setReturnValue(false);
    }
}
