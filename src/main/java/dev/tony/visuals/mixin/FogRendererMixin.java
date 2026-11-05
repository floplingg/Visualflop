package dev.tony.visuals.mixin;

import dev.tony.visuals.sky.TopoSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blends the distance fog toward the sky's horizon colour so far terrain melts into the custom sky. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void flopvisuals$tintFog(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                     float darken, CallbackInfoReturnable<Vector4f> cir) {
        TopoSky.tintFog(camera, level, cir.getReturnValue());
    }
}
