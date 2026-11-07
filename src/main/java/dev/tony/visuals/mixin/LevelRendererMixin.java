package dev.tony.visuals.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.tony.visuals.compat.Shaders;
import dev.tony.visuals.glass.GlassGround;
import dev.tony.visuals.sky.TopoSky;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With an Iris shader pack, the pack paints its own sky and relights everything drawn during the world pass.
 * So in that case the sky and glass are drawn here instead: after the pack has produced its final image,
 * depth-tested against the scene so blocks still cover them (the same spot minimap waypoint beams use).
 */
@Mixin(value = LevelRenderer.class, priority = 1500)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void flopvisuals$afterShaders(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                                          Matrix4f modelView, Matrix4f projection, Matrix4f other, GpuBufferSlice fog,
                                          Vector4f fogColour, boolean sky, CallbackInfo ci) {
        if (!Shaders.active()) return;
        Minecraft mc = Minecraft.getInstance();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.mul(modelView);
        try {
            MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
            TopoSky.draw(source);
            dev.tony.visuals.fx.AuraFx.draw(source);
            GlassGround.renderAfterShaders(source);
            source.endBatch();
        } finally {
            stack.popMatrix();
        }
    }
}
