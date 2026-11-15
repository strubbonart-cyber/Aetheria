package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional removal of the fire overlay from rendered entities. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private void skyblockqol$hideEntityFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             Entity entity, Quaternionf rotation, CallbackInfo ci) {
        if (MiscQolFeatures.isDisableEntityFireEnabled()) ci.cancel();
    }
}
