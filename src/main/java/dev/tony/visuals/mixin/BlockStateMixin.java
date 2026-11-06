package dev.tony.visuals.mixin;

import dev.tony.visuals.glass.GrassHider;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the glass ground hide short and tall grass (they only stop being drawn; nothing else changes). */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void flopvisuals$hideGrass(CallbackInfoReturnable<RenderShape> cir) {
        if (GrassHider.active() && (Object) this instanceof BlockState state && GrassHider.hides(state)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }
}
