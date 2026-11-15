package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.qol.CursorResetFeature;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Prevents Mouse.unlockCursor from moving the pointer to the window center. */
@Mixin(Mouse.class)
public abstract class MouseCursorMixin {
    @Shadow private double x;
    @Shadow private double y;

    @ModifyArgs(
            method = "unlockCursor",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursorPos(JDD)V"
            )
    )
    private void skyblockqol$preservePointerPosition(Args args) {
        if (!CursorResetFeature.isEnabled()) return;
        args.set(1, x);
        args.set(2, y);
    }
}
