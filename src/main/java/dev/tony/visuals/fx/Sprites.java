package dev.tony.visuals.fx;

import dev.tony.visuals.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/** Looks up our hi-res particle textures (assets/flopvisuals/textures/particle) in the particle atlas. */
public final class Sprites {
    private Sprites() {}

    public static TextureAtlasSprite get(String name) {
        TextureAtlas atlas = ((MinecraftAccessor) Minecraft.getInstance()).flopvisuals$atlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
        return atlas.getSprite(Identifier.fromNamespaceAndPath("flopvisuals", name));
    }
}
