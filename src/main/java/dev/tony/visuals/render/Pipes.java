package dev.tony.visuals.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Building blocks for 26.3's renderer ("renderpearl"), laid out the way vanilla builds its own pipelines:
 * bind groups for the uniform blocks and samplers, one vertex binding, quads, and reversed depth (nearer = larger).
 */
public final class Pipes {
    private Pipes() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("flopvisuals", path);
    }

    /** Depth test used by vanilla's world pipelines: reversed-Z, so "in front or equal" is GREATER_THAN_OR_EQUAL. */
    public static DepthStencilState depthTest(boolean write) {
        return new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, write);
    }

    public static DepthStencilState noDepth() {
        return new DepthStencilState(CompareOp.ALWAYS_PASS, false);
    }

    /** A world pipeline: Globals + Projection + DynamicTransforms, one shader name for both stages, quads, no culling. */
    public static RenderPipeline.Builder world(String name, String shader, VertexFormat format) {
        return RenderPipeline.builder()
            .withLocation(id("pipeline/" + name))
            .withVertexShader(id("core/" + shader))
            .withFragmentShader(id("core/" + shader))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexBinding(0, format)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false);
    }

    /** A GUI pipeline like vanilla's textured GUI one (Sampler0, translucent, position-uv-colour quads). */
    public static RenderPipeline.Builder guiTextured(String name, String shader) {
        return RenderPipeline.builder()
            .withLocation(id("pipeline/" + name))
            .withVertexShader(id("core/" + shader))
            .withFragmentShader(id("core/" + shader))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS);
    }

    public static ColorTargetState blend(BlendFunction f) {
        return new ColorTargetState(f);
    }

    /** Bind group with our own named samplers (e.g. "SceneColor", "SceneDepth"). */
    public static BindGroupLayout samplers(String... names) {
        BindGroupLayout.Builder b = BindGroupLayout.builder();
        for (String n : names) b.withUniform(n, UniformType.COMBINED_IMAGE_SAMPLER);
        return b.build();
    }

    public static RenderPipeline register(RenderPipeline p) {
        return RenderPipelines.register(p);
    }

    public static RenderType type(String name, RenderPipeline p) {
        return RenderType.create(name, RenderSetup.builder(p).createRenderSetup());
    }

    /** A render type that binds textures by sampler name: pairs of (sampler name, texture id). */
    public static RenderType type(String name, RenderPipeline p, Object... samplerAndTexture) {
        RenderSetup.RenderSetupBuilder b = RenderSetup.builder(p);
        for (int i = 0; i + 1 < samplerAndTexture.length; i += 2) {
            b.withTexture((String) samplerAndTexture[i], (Identifier) samplerAndTexture[i + 1],
                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        }
        return RenderType.create(name, b.createRenderSetup());
    }
}
