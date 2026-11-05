package dev.tony.visuals.mixin;

import dev.tony.visuals.wallpaper.Wallpapers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the title-screen panorama (used by every menu outside a world) for the Flop Visuals wallpaper. */
@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void flopvisuals$wallpaper(GuiGraphics g, float partialTick, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (Wallpapers.draw(g, self.width, self.height)) ci.cancel();
    }
}
