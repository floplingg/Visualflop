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
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Glass ground: a glossy layer on the ground around you that mirrors the topographic sky.
 *
 * - Only full-width surfaces reflect (full blocks, slabs, snow...), so walls, fences and posts stay clean.
 * - Reflections are darkened smoothly by the game's light level, blended across block corners
 *   like smooth lighting, so shade under overhangs fades in softly.
 * - The edge of the reflective circle fades per vertex for a perfectly round, soft border.
 */
public final class GlassGround {
    private GlassGround() {}

    public static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("flopvisuals", "pipeline/topo_glass"))
            .withVertexShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/topo_glass"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/topo_glass"))
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthBias(-1f, -2f)
            .withCull(false)
            .build());

    private static final int F = 9;   // floats per face
    private static RenderType type;
    /** Faces: kind (0 top, 1 east, 2 west, 3 south, 4 north), x, z, yTop, yBottom, shade at 4 corners. */
    private static float[] faces = new float[0];
    private static int count;
    private static int builtX = Integer.MIN_VALUE, builtZ, builtY, builtRadius;
    private static long builtAt;
    private static boolean builtWater, logged;
    private static double centreX, centreZ;

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
        Vec3 pp = mc.player.position();
        centreX = pp.x;
        centreZ = pp.z;
        int accent = Config.theme().accent();
        float ar = ((accent >> 16) & 255) / 255f, ag = ((accent >> 8) & 255) / 255f, ab = (accent & 255) / 255f;
        float strength = gc.reflection / 100f;
        float radius = gc.radius;
        float time = TopoSky.time(), packed = TopoSky.packed();
        final float e = 0.003f;

        MultiBufferSource source = ctx.consumers();
        VertexConsumer vc = source.getBuffer(type());
        Emitter em = new Emitter(vc, cam, ar, ag, ab, strength, radius, time, packed);
        for (int i = 0; i < count; i++) {
            int o = i * F;
            int kind = (int) faces[o];
            float x = faces[o + 1], z = faces[o + 2], yt = faces[o + 3], yb = faces[o + 4];
            float s0 = faces[o + 5], s1 = faces[o + 6], s2 = faces[o + 7], s3 = faces[o + 8];
            switch (kind) {
                case 0 -> {   // corners: (x,z) (x,z+1) (x+1,z+1) (x+1,z)
                    em.v(x, yt + e, z, s0, 0, 1, 0);
                    em.v(x, yt + e, z + 1, s1, 0, 1, 0);
                    em.v(x + 1, yt + e, z + 1, s2, 0, 1, 0);
                    em.v(x + 1, yt + e, z, s3, 0, 1, 0);
                }
                case 1 -> {   // east: top edge shades s0 (z) s1 (z+1), bottom darker
                    float px = x + 1 + e;
                    em.v(px, yb, z, s0 * 0.55f, 1, 0, 0);
                    em.v(px, yt, z, s0, 1, 0, 0);
                    em.v(px, yt, z + 1, s1, 1, 0, 0);
                    em.v(px, yb, z + 1, s1 * 0.55f, 1, 0, 0);
                }
                case 2 -> {
                    float px = x - e;
                    em.v(px, yb, z, s0 * 0.55f, -1, 0, 0);
                    em.v(px, yb, z + 1, s1 * 0.55f, -1, 0, 0);
                    em.v(px, yt, z + 1, s1, -1, 0, 0);
                    em.v(px, yt, z, s0, -1, 0, 0);
                }
                case 3 -> {
                    float pz = z + 1 + e;
                    em.v(x, yb, pz, s0 * 0.55f, 0, 0, 1);
                    em.v(x + 1, yb, pz, s1 * 0.55f, 0, 0, 1);
                    em.v(x + 1, yt, pz, s1, 0, 0, 1);
                    em.v(x, yt, pz, s0, 0, 0, 1);
                }
                default -> {
                    float pz = z - e;
                    em.v(x, yb, pz, s0 * 0.55f, 0, 0, -1);
                    em.v(x, yt, pz, s0, 0, 0, -1);
                    em.v(x + 1, yt, pz, s1, 0, 0, -1);
                    em.v(x + 1, yb, pz, s1 * 0.55f, 0, 0, -1);
                }
            }
        }
        if (source instanceof MultiBufferSource.BufferSource bs) bs.endBatch(type());
        if (!logged) {
            logged = true;
            org.slf4j.LoggerFactory.getLogger("flopvisuals").info("Glass ground drawing {} faces", count);
        }
    }

    /** Writes one vertex: light shade darkens the colour, distance from you fades it out smoothly. */
    private record Emitter(VertexConsumer vc, Vec3 cam, float r, float g, float b, float strength, float radius,
                           float time, float packed) {
        void v(float x, float y, float z, float shade, float nx, float ny, float nz) {
            double dx = x - centreX, dz = z - centreZ;
            float d = (float) Math.sqrt(dx * dx + dz * dz) / radius;
            float fade = 1f - smooth(0.55f, 1f, d);
            int a = Math.round(255 * strength * fade);
            float k = shade;
            vc.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .setColor(Math.round(r * k * 255), Math.round(g * k * 255), Math.round(b * k * 255), Math.max(0, a))
                .setUv(time, packed)
                .setUv1(Math.floorMod(Math.round(x), 16384), Math.floorMod(Math.round(z), 16384))
                .setUv2((int) Math.max(-32000, Math.min(32000, Math.round(y * 8))), 0)
                .setNormal(nx, ny, nz);
        }
    }

    private static void rebuild(ClientLevel level, BlockPos me, int radius, boolean water) {
        builtX = me.getX();
        builtZ = me.getZ();
        builtY = me.getY();
        builtRadius = radius;
        builtWater = water;
        builtAt = System.currentTimeMillis();
        int side = radius * 2 + 5;
        int ox = me.getX() - radius - 2, oz = me.getZ() - radius - 2;
        double[] top = new double[side * side];     // reflective top height, NaN if none
        double[] ground = new double[side * side];  // raw ground height for side faces
        float[] light = new float[side * side];     // 0..1 shade for each column
        boolean[] full = new boolean[side * side];  // top block is a full cube (can have reflective sides)
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int idx = i * side + j, x = ox + i, z = oz + j;
                // Find the ground below you: scan down from just above your head, so overhangs,
                // floating builds and trees above don't hide the surface underneath.
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int y = Math.min(h - 1, me.getY() + 3);
                int floor = Math.max(level.getMinY(), me.getY() - 160);
                BlockState state = null;
                boolean buried = false;
                if (y < h - 1) {
                    // We start inside the column (under a roof or beside a hill): only keep going if that spot is open.
                    pos.set(x, y, z);
                    BlockState start = level.getBlockState(pos);
                    buried = !start.isAir() && !start.getCollisionShape(level, pos).isEmpty();
                }
                if (buried) y = floor - 1;
                for (; y >= floor; y--) {
                    pos.set(x, y, z);
                    state = level.getBlockState(pos);
                    if (!state.isAir() && (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty())) break;
                }
                ground[idx] = y + 1;
                top[idx] = Double.NaN;
                pos.set(x, y + 1, z);
                int sky = level.getBrightness(LightLayer.SKY, pos), block = level.getBrightness(LightLayer.BLOCK, pos);
                float l = Math.max(sky, block * 0.85f) / 15f;
                light[idx] = 0.10f + 0.90f * (float) Math.pow(l, 2.2);
                if (y < floor || state == null || state.is(BlockTags.LEAVES)) continue;
                pos.set(x, y, z);
                if (!state.getFluidState().isEmpty()) {
                    if (water) top[idx] = y + state.getFluidState().getHeight(level, pos);
                    continue;
                }
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (shape.isEmpty()) continue;
                AABB box = shape.bounds();
                // Only surfaces that cover the whole block (no walls, fences, posts, panes, chains...).
                if (box.minX > 0.01 || box.maxX < 0.99 || box.minZ > 0.01 || box.maxZ < 0.99) continue;
                top[idx] = y + box.maxY;
                full[idx] = box.maxY > 0.99 && box.minY < 0.01 && state.isCollisionShapeFullBlock(level, pos);
            }
        }

        if (faces.length < side * side * 5 * F) faces = new float[side * side * 5 * F];
        count = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) continue;
                int i = dx + radius + 2, j = dz + radius + 2, idx = i * side + j;
                double t = top[idx];
                if (Double.isNaN(t)) continue;
                int x = ox + i, z = oz + j;
                // Smooth lighting: each corner averages the four columns that meet there.
                float c00 = corner(light, side, i, j), c01 = corner(light, side, i, j + 1);
                float c11 = corner(light, side, i + 1, j + 1), c10 = corner(light, side, i + 1, j);
                add(0, x, z, t, t, c00, c01, c11, c10);
                if (!full[idx]) continue;
                for (int k = 0; k < 4; k++) {
                    int n = (i + dirs[k][0]) * side + (j + dirs[k][1]);
                    double nt = Double.isNaN(top[n]) ? ground[n] : top[n];
                    if (nt > t - 0.99) continue;   // only real steps of a block or more
                    double yb = Math.max(nt, t - 6);
                    switch (k) {
                        case 0 -> add(1, x, z, t, yb, c10 * 0.85f, c11 * 0.85f, 0, 0);
                        case 1 -> add(2, x, z, t, yb, c00 * 0.85f, c01 * 0.85f, 0, 0);
                        case 2 -> add(3, x, z, t, yb, c01 * 0.75f, c11 * 0.75f, 0, 0);
                        default -> add(4, x, z, t, yb, c00 * 0.75f, c10 * 0.75f, 0, 0);
                    }
                }
            }
        }
    }

    /** Average light of the (up to) four columns around corner (i, j). */
    private static float corner(float[] light, int side, int i, int j) {
        float sum = 0;
        int n = 0;
        for (int a = i - 1; a <= i; a++) {
            for (int b = j - 1; b <= j; b++) {
                if (a < 0 || b < 0 || a >= side || b >= side) continue;
                sum += light[a * side + b];
                n++;
            }
        }
        return n == 0 ? 1 : sum / n;
    }

    private static void add(int kind, int x, int z, double yt, double yb, float s0, float s1, float s2, float s3) {
        int o = count * F;
        faces[o] = kind;
        faces[o + 1] = x;
        faces[o + 2] = z;
        faces[o + 3] = (float) yt;
        faces[o + 4] = (float) yb;
        faces[o + 5] = s0;
        faces[o + 6] = s1;
        faces[o + 7] = s2;
        faces[o + 8] = s3;
        count++;
    }

    private static float smooth(float a, float b, float v) {
        float t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
