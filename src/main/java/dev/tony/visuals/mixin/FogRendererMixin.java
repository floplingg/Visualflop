package dev.tony.visuals.mixin;

import dev.tony.visuals.sky.TopoSky;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pulls the distance fog towards the custom sky's horizon colour. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void flopvisuals$tintFog(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                                     CallbackInfoReturnable<FogData> cir) {
        FogData data = cir.getReturnValue();
        if (data != null) TopoSky.tintFog(camera, level, data.color);
    }
}
