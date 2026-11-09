package dev.tony.visuals.mixin;

import dev.tony.visuals.pay.Payments;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Handles clicks on the payment buttons in chat ourselves (they never go to the server). */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void flopvisuals$payButtons(Style style, boolean insertion, CallbackInfoReturnable<Boolean> cir) {
        if (insertion) return;
        if (style.getClickEvent() instanceof ClickEvent.Custom custom && Payments.click(custom.id())) {
            cir.setReturnValue(true);
        }
    }
}
