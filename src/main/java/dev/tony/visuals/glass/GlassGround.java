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
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthBias(-1f, -2f)
            .withCull(false)
            .build());

    private static RenderType type;
    /** Cached faces: kind (0 top, 1 east, 2 west, 3 south, 4 north), x, z, yTop, yBottom, fade. */
    private static float[] faces = new float[0];
    private static int count;
    private static int builtX = Integer.MIN_VALUE, builtZ, builtY, builtRadius;
    private static long builtAt;
    private static boolean builtWater, logged;

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
        if (!Config.get().sky.allDimensions && level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;

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
        final float e = 0.003f;

        MultiBufferSource source = ctx.consumers();
        VertexConsumer vc = source.getBuffer(type());
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            int kind = (int) faces[o];
            float x = (float) (faces[o + 1] - cam.x), z = (float) (faces[o + 2] - cam.z);
            float yt = (float) (faces[o + 3] - cam.y), yb = (float) (faces[o + 4] - cam.y);
            int a = Math.round(255 * strength * faces[o + 5]);
            if (a <= 0) continue;
            switch (kind) {
                case 0 -> {
                    v(vc, x, yt + e, z, time, packed, r, g, b, a, 0, 1, 0);
                    v(vc, x, yt + e, z + 1, time, packed, r, g, b, a, 0, 1, 0);
                    v(vc, x + 1, yt + e, z + 1, time, packed, r, g, b, a, 0, 1, 0);
                    v(vc, x + 1, yt + e, z, time, packed, r, g, b, a, 0, 1, 0);
                }
                case 1 -> {
                    float px = x + 1 + e;
                    v(vc, px, yb, z, time, packed, r, g, b, a, 1, 0, 0);
                    v(vc, px, yt, z, time, packed, r, g, b, a, 1, 0, 0);
                    v(vc, px, yt, z + 1, time, packed, r, g, b, a, 1, 0, 0);
                    v(vc, px, yb, z + 1, time, packed, r, g, b, a, 1, 0, 0);
                }
                case 2 -> {
                    float px = x - e;
                    v(vc, px, yb, z, time, packed, r, g, b, a, -1, 0, 0);
                    v(vc, px, yb, z + 1, time, packed, r, g, b, a, -1, 0, 0);
                    v(vc, px, yt, z + 1, time, packed, r, g, b, a, -1, 0, 0);
                    v(vc, px, yt, z, time, packed, r, g, b, a, -1, 0, 0);
                }
                case 3 -> {
                    float pz = z + 1 + e;
                    v(vc, x, yb, pz, time, packed, r, g, b, a, 0, 0, 1);
                    v(vc, x + 1, yb, pz, time, packed, r, g, b, a, 0, 0, 1);
                    v(vc, x + 1, yt, pz, time, packed, r, g, b, a, 0, 0, 1);
                    v(vc, x, yt, pz, time, packed, r, g, b, a, 0, 0, 1);
                }
                default -> {
                    float pz = z - e;
                    v(vc, x, yb, pz, time, packed, r, g, b, a, 0, 0, -1);
                    v(vc, x, yt, pz, time, packed, r, g, b, a, 0, 0, -1);
                    v(vc, x + 1, yt, pz, time, packed, r, g, b, a, 0, 0, -1);
                    v(vc, x + 1, yb, pz, time, packed, r, g, b, a, 0, 0, -1);
                }
            }
        }
        if (source instanceof MultiBufferSource.BufferSource bs) bs.endBatch(type());
        if (!logged) {
            logged = true;
            org.slf4j.LoggerFactory.getLogger("flopvisuals").info("Glass ground drawing {} faces (buffer source: {})", count, source.getClass().getName());
        }
    }

    private static void v(VertexConsumer vc, float x, float y, float z, float time, float packed,
                          int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.addVertex(x, y, z).setUv(time, packed).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    private static void rebuild(ClientLevel level, BlockPos me, int radius, boolean water) {
        builtX = me.getX();
        builtZ = me.getZ();
        builtY = me.getY();
        builtRadius = radius;
        builtWater = water;
        builtAt = System.currentTimeMillis();
        int side = radius * 2 + 3;
        // Ground height per column (top of the highest solid block), NaN where there's nothing to reflect.
        double[] top = new double[side * side];
        int[] raw = new int[side * side];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int x = me.getX() - radius - 1 + i, z = me.getZ() - radius - 1 + j;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                raw[i * side + j] = h;
                top[i * side + j] = Double.NaN;
                if (h > me.getY() + 8 || h < me.getY() - 24) continue;
                pos.set(x, h - 1, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(BlockTags.LEAVES)) continue;
                if (!state.getFluidState().isEmpty()) {
                    if (water) top[i * side + j] = h - 1 + state.getFluidState().getHeight(level, pos);
                    continue;
                }
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (shape.isEmpty()) continue;
                top[i * side + j] = h - 1 + shape.max(Direction.Axis.Y);
            }
        }
        if (faces.length < side * side * 5 * 6) faces = new float[side * side * 5 * 6];
        count = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                float d = (float) Math.sqrt(dx * dx + dz * dz) / radius;
                if (d > 1) continue;
                int i = dx + radius + 1, j = dz + radius + 1;
                double t = top[i * side + j];
                if (Double.isNaN(t)) continue;
                float fade = 1f - smooth(0.6f, 1f, d);
                int x = me.getX() + dx, z = me.getZ() + dz;
                add(0, x, z, t, t, fade);
                // Exposed sides where the neighbour column is lower (cliffs, steps, the edges of builds).
                for (int k = 0; k < 4; k++) {
                    int ni = i + dirs[k][0], nj = j + dirs[k][1];
                    double nt = Double.isNaN(top[ni * side + nj]) ? raw[ni * side + nj] : top[ni * side + nj];
                    if (nt < t - 0.01) add(k + 1, x, z, t, Math.max(nt, t - 8), fade);
                }
            }
        }
    }

    private static void add(int kind, int x, int z, double yt, double yb, float fade) {
        int o = count * 6;
        faces[o] = kind;
        faces[o + 1] = x;
        faces[o + 2] = z;
        faces[o + 3] = (float) yt;
        faces[o + 4] = (float) yb;
        faces[o + 5] = fade;
        count++;
    }

    private static float smooth(float a, float b, float v) {
        float t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
