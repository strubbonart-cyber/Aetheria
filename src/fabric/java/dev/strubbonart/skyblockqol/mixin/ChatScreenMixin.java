package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.qol.ExtraQolFeatures;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers an unfinished chat draft while other screens are opened. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "onChatFieldUpdate", at = @At("TAIL"))
    private void skyblockqol$saveDraft(String text, CallbackInfo ci) {
        if (ExtraQolFeatures.isChatStateRestoreEnabled()) {
            ExtraQolFeatures.updateChatDraft(text);
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void skyblockqol$restoreDraft(CallbackInfo ci) {
        if (ExtraQolFeatures.shouldRestoreChatDraft() && chatField != null) {
            chatField.setText(ExtraQolFeatures.getSavedChatDraft());
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void skyblockqol$clearAfterSendOrEscape(int keyCode, int scanCode, int modifiers,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (ExtraQolFeatures.isChatStateRestoreEnabled()
                && (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            ExtraQolFeatures.clearChatDraft();
        }
    }
}