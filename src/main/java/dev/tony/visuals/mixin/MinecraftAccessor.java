package dev.tony.visuals.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("atlasManager")
    AtlasManager flopvisuals$atlasManager();
}
