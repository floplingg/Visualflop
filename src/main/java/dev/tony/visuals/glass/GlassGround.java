package dev.tony.visuals.glass;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tony.visuals.Config;
import dev.tony.visuals.sky.TopoSky;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Glass ground: a glossy layer on the top surface around you that mirrors the topographic sky.
 * The surface is found from the heightmap and cached; it is rebuilt only when you move a block
 * or twice a second. The reflection itself is worked out per pixel on the GPU.
 */
public final class GlassGround {
    private GlassGround() {}

    public static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("flopvisuals", "pipeline/topo_glass"))
            .withVertexShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/topo_glass"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/topo_glass"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthBias(-1f, -2f)
            .withCull(false)
            .build());

    private static RenderType type;
    /** Cached tops: x, top y, z, distance fade (0..1). */
    private static float[] tops = new float[0];
    private static int count;
    private static int builtX = Integer.MIN_VALUE, builtZ, builtY, builtRadius;
    private static long builtAt;
    private static boolean builtWater;

    public static void init() {}

    private static RenderType type() {
        if (type == null) type = RenderType.create("flopvisuals_topo_glass", RenderSetup.builder(PIPELINE).createRenderSetup());
        return type;
    }

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Config.Glass gc = Config.get().glass;
        ClientLevel level = mc.level;
        if (!gc.enabled || gc.reflection <= 0 || level == null || mc.player == null) return;
        if (!TopoSky.activeIn(level) && !Config.get().sky.allDimensions && level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;

        BlockPos me = mc.player.blockPosition();
        long now = System.currentTimeMillis();
        if (me.getX() != builtX || me.getZ() != builtZ || me.getY() != builtY || gc.radius != builtRadius
            || gc.water != builtWater || now - builtAt > 500) {
            rebuild(level, me, gc.radius, gc.water);
        }
        if (count == 0) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        int accent = Config.theme().accent();
        int r = (accent >> 16) & 255, g = (accent >> 8) & 255, b = accent & 255;
        float strength = gc.reflection / 100f;
        float time = TopoSky.time(), packed = TopoSky.packed();

        MultiBufferSource source = ctx.consumers();
        VertexConsumer vc = source.getBuffer(type());
        for (int i = 0; i < count; i++) {
            int o = i * 4;
            float x = (float) (tops[o] - cam.x), y = (float) (tops[o + 1] + 0.003 - cam.y), z = (float) (tops[o + 2] - cam.z);
            int a = Math.round(255 * strength * tops[o + 3]);
            if (a <= 0) continue;
            vc.addVertex(x, y, z).setUv(time, packed).setColor(r, g, b, a);
            vc.addVertex(x, y, z + 1).setUv(time, packed).setColor(r, g, b, a);
            vc.addVertex(x + 1, y, z + 1).setUv(time, packed).setColor(r, g, b, a);
            vc.addVertex(x + 1, y, z).setUv(time, packed).setColor(r, g, b, a);
        }
        if (source instanceof MultiBufferSource.BufferSource bs) bs.endBatch(type());
    }

    private static void rebuild(ClientLevel level, BlockPos me, int radius, boolean water) {
        builtX = me.getX();
        builtZ = me.getZ();
        builtY = me.getY();
        builtRadius = radius;
        builtWater = water;
        builtAt = System.currentTimeMillis();
        int side = radius * 2 + 1;
        if (tops.length < side * side * 4) tops = new float[side * side * 4];
        count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                float d = (float) Math.sqrt(dx * dx + dz * dz) / radius;
                if (d > 1) continue;
                int x = me.getX() + dx, z = me.getZ() + dz;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                // Skip surfaces far above you (tree canopies, overhangs) so caves and forests look right.
                if (h > me.getY() + 6 || h < me.getY() - 24) continue;
                pos.set(x, h - 1, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(BlockTags.LEAVES)) continue;
                double top;
                if (!state.getFluidState().isEmpty()) {
                    if (!water) continue;
                    top = h - 1 + state.getFluidState().getHeight(level, pos);
                } else {
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    top = h - 1 + shape.max(Direction.Axis.Y);
                }
                float fade = 1f - smooth(0.6f, 1f, d);
                int o = count * 4;
                tops[o] = x;
                tops[o + 1] = (float) top;
                tops[o + 2] = z;
                tops[o + 3] = fade;
                count++;
            }
        }
    }

    private static float smooth(float a, float b, float v) {
        float t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
