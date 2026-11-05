package dev.tony.visuals.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tony.visuals.Config;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

/**
 * Animated glowing contour-line sky in the accent colour.
 * A sphere around the camera, drawn behind the world, coloured per pixel by our own shader
 * (so the lines are sharp at 1080p, 1440p, 4K...). About 1,000 quads a frame, almost free.
 */
public final class TopoSky {
    private TopoSky() {}

    private static final int LAT = 20, LON = 40;
    private static final long START = System.nanoTime();

    public static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("flopvisuals", "pipeline/topo_sky"))
            .withVertexShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/topo_sky"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/topo_sky"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build());

    private static RenderType type;

    private static RenderType type() {
        if (type == null) type = RenderType.create("flopvisuals_topo_sky", RenderSetup.builder(PIPELINE).createRenderSetup());
        return type;
    }

    /** Make sure the class (and pipeline) loads during startup. */
    public static void init() {}

    public static boolean activeIn(ClientLevel level) {
        Config.Sky s = Config.get().sky;
        return s.enabled && level != null && (s.allDimensions || level.dimension() == Level.OVERWORLD);
    }

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!activeIn(mc.level)) return;
        Config.Sky s = Config.get().sky;

        int accent = Config.theme().accent();
        int r = (accent >> 16) & 255, g = (accent >> 8) & 255, b = accent & 255;
        int a = Math.round(s.opacity * 2.55f);
        float time = (float) (((System.nanoTime() - START) / 1e9 * (s.speed / 5.0)) % 20000.0);
        float packed = s.lines + Math.min(0.99f, s.brightness / 100f);
        float radius = Math.max(64f, mc.gameRenderer.getDepthFar() * 0.5f);

        MultiBufferSource source = ctx.consumers();
        VertexConsumer vc = source.getBuffer(type());
        for (int i = 0; i < LAT; i++) {
            double t0 = Math.PI * i / LAT - Math.PI / 2, t1 = Math.PI * (i + 1) / LAT - Math.PI / 2;
            float y0 = (float) Math.sin(t0) * radius, y1 = (float) Math.sin(t1) * radius;
            float c0 = (float) Math.cos(t0) * radius, c1 = (float) Math.cos(t1) * radius;
            for (int j = 0; j < LON; j++) {
                double p0 = Math.PI * 2 * j / LON, p1 = Math.PI * 2 * (j + 1) / LON;
                float cos0 = (float) Math.cos(p0), sin0 = (float) Math.sin(p0);
                float cos1 = (float) Math.cos(p1), sin1 = (float) Math.sin(p1);
                vc.addVertex(c0 * cos0, y0, c0 * sin0).setUv(time, packed).setColor(r, g, b, a);
                vc.addVertex(c0 * cos1, y0, c0 * sin1).setUv(time, packed).setColor(r, g, b, a);
                vc.addVertex(c1 * cos1, y1, c1 * sin1).setUv(time, packed).setColor(r, g, b, a);
                vc.addVertex(c1 * cos0, y1, c1 * sin0).setUv(time, packed).setColor(r, g, b, a);
            }
        }
        if (source instanceof MultiBufferSource.BufferSource bs) bs.endBatch(type());
    }

    /** Pull the distance fog towards the sky's horizon colour. */
    public static void tintFog(Camera camera, ClientLevel level, Vector4f fog) {
        Config.Sky s = Config.get().sky;
        if (!s.tintFog || !activeIn(level) || fog == null) return;
        if (camera.getFluidInCamera() != FogType.NONE) return;
        int accent = Config.theme().accent();
        float k = Math.min(0.99f, s.brightness / 100f) * 1.25f;
        float hr = (((accent >> 16) & 255) / 255f * 0.30f + 0.02f) * k;
        float hg = (((accent >> 8) & 255) / 255f * 0.30f + 0.02f) * k;
        float hb = ((accent & 255) / 255f * 0.30f + 0.02f) * k;
        float m = s.opacity / 100f;
        fog.set(fog.x + (hr - fog.x) * m, fog.y + (hg - fog.y) * m, fog.z + (hb - fog.z) * m, fog.w);
    }
}
