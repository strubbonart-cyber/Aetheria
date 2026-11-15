package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.farming.GardenMouseFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Applies the Garden camera lock and farming sensitivity scale to local player look input. */
@Mixin(Entity.class)
public abstract class EntityLookMixin {
    @ModifyArgs(method = "changeLookDirection(DD)V", at = @At("HEAD"))
    private void skyblockqol$modifyLookDelta(Args args) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (Object) this != client.player) return;

        if (GardenMouseFeatures.shouldLockCamera()) {
            args.set(0, 0.0D);
            args.set(1, 0.0D);
            return;
        }

        if (GardenMouseFeatures.isSensitivityActive()) {
            double multiplier = GardenMouseFeatures.sensitivityMultiplier();
            args.set(0, (Double) args.get(0) * multiplier);
            args.set(1, (Double) args.get(1) * multiplier);
        }
    }
}