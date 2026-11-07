package dev.tony.visuals.fx;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tony.visuals.Config;
import dev.tony.visuals.sky.SkyStyles;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Orbiting orbs with fading comet trails, a halo (ring, crown, twin ring, sun, horns or starlight) and a soft aura
 * around you. Everything is a handful of glowing quads shaped per pixel by one shader: a few hundred vertices a frame.
 */
public final class AuraFx {
    private AuraFx() {}

    // Shapes the shader knows (stored in the whole part of the U coordinate, two units per shape).
    private static final int ORB = 0, GLOW = 1, RING = 2, CROWN = 3, SUN = 4, HORN = 5, SPARKLE = 6;

    public static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("flopvisuals", "pipeline/aura"))
            .withVertexShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/aura"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/aura"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build());

    private static RenderType type;
    private static final long START = System.nanoTime();

    public static void init() {}

    private static RenderType type() {
        if (type == null) type = RenderType.create("flopvisuals_aura", RenderSetup.builder(PIPELINE).createRenderSetup());
        return type;
    }

    public static void render(WorldRenderContext ctx) {
        if (dev.tony.visuals.compat.Shaders.active()) return;
        draw(ctx.consumers());
    }

    public static void draw(MultiBufferSource source) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Config.Aura a = Config.get().aura;
        if (p == null || mc.level == null || p.isSpectator() || p.isInvisible() || p.isSleeping()) return;
        if (!a.orbs && !a.halo && !a.aura) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        boolean firstPerson = !cam.isDetached();
        if (firstPerson && !(a.orbs && a.orbsFirstPerson)) return;

        float pt = cam.getPartialTickTime();
        Vec3 pos = p.getPosition(pt);
        Vec3 c = cam.position();
        Q q = new Q(source.getBuffer(type()), c, cam.leftVector(), cam.upVector());
        int rgb = SkyStyles.colour(Config.SkyStyle.TOPOGRAPHIC, a.colour);
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float t = (System.nanoTime() - START) / 1e9f;
        float height = p.getBbHeight();

        if (a.aura && !firstPerson) {
            float pulse = 0.85f + 0.15f * (float) Math.sin(t * 1.8);
            q.billboard(pos.x, pos.y + height * 0.5, pos.z, 1.25f * pulse, GLOW, r, g, b, 0.30f);
            for (int i = 0; i < 7; i++) {
                float phase = (t * 0.45f + i / 7f) % 1f;
                double ang = i * 2.4 + t * 0.3;
                double wx = pos.x + Math.cos(ang) * 0.42, wz = pos.z + Math.sin(ang) * 0.42;
                float fade = (float) Math.sin(phase * Math.PI);
                q.billboard(wx, pos.y + phase * height * 1.15, wz, 0.22f + 0.12f * phase, GLOW, r, g, b, 0.45f * fade);
            }
        }

        if (a.orbs) {
            int n = a.orbCount;
            float speed = 0.35f + a.orbSpeed * 0.17f;
            double cy = pos.y + height * 0.55;
            for (int i = 0; i < n; i++) {
                double base = t * speed + i * Math.PI * 2 / n;
                int trail = a.orbTrails ? 16 : 0;
                for (int k = trail; k >= 0; k--) {
                    double ang = base - k * 0.07;
                    double ox = pos.x + Math.cos(ang) * 1.15;
                    double oy = cy + Math.sin(ang * 2 + i) * 0.14;
                    double oz = pos.z + Math.sin(ang) * 1.15;
                    // In first person, fade anything right in front of the lens.
                    double dc = Math.sqrt((ox - c.x) * (ox - c.x) + (oy - c.y) * (oy - c.y) + (oz - c.z) * (oz - c.z));
                    float near = firstPerson ? (float) Math.max(0, Math.min(1, (dc - 0.45) / 0.5)) : 1f;
                    if (near <= 0) continue;
                    if (k == 0) {
                        q.billboard(ox, oy, oz, 0.22f, ORB, r, g, b, near);
                    } else {
                        float f = 1f - k / (trail + 1f);
                        q.billboard(ox, oy, oz, 0.20f * f + 0.03f, GLOW, r, g, b, 0.55f * f * f * near);
                    }
                }
            }
        }

        if (a.halo && !firstPerson) {
            float bob = (float) Math.sin(t * 2.0) * 0.035f;
            double top = pos.y + height;
            double hx = pos.x, hz = pos.z;
            switch (a.haloStyle) {
                case RING -> q.flat(hx, top + 0.28 + bob, hz, 0.42f, RING, r, g, b, 1f);
                case TWIN_RING -> {
                    q.flat(hx, top + 0.24 + bob, hz, 0.44f, RING, r, g, b, 1f);
                    q.flat(hx, top + 0.42 + bob * 1.4, hz, 0.30f, RING, r, g, b, 0.85f);
                }
                case CROWN -> q.band(hx, top - 0.02, hz, 0.34f, 0.30f, 20, t * 0.2f, CROWN, r, g, b, 1f);
                case SUN -> {
                    // A radiant disc behind your head, facing the camera.
                    Vector3f away = new Vector3f((float) (hx - c.x), 0, (float) (hz - c.z));
                    if (away.lengthSquared() > 1e-4f) away.normalize(0.35f);
                    q.billboard(hx + away.x, top - 0.22, hz + away.z, 0.75f, SUN, r, g, b, 1f);
                }
                case HORNS -> {
                    float yaw = (float) Math.toRadians(p.getViewYRot(pt));
                    double sx = Math.cos(yaw) * 0.2, sz = Math.sin(yaw) * 0.2;
                    q.billboard(hx + sx, top + 0.05, hz + sz, 0.2f, HORN, r, g, b, 1f, true);
                    q.billboard(hx - sx, top + 0.05, hz - sz, 0.2f, HORN, r, g, b, 1f, false);
                }
                case STARLIGHT -> {
                    for (int i = 0; i < 7; i++) {
                        double ang = t * 0.6 + i * Math.PI * 2 / 7;
                        float tw = 0.75f + 0.25f * (float) Math.sin(t * 3 + i * 1.7);
                        q.billboard(hx + Math.cos(ang) * 0.36, top + 0.26 + bob + Math.sin(ang * 3) * 0.03, hz + Math.sin(ang) * 0.36,
                            0.11f * tw, SPARKLE, r, g, b, 1f);
                    }
                    q.flat(hx, top + 0.26 + bob, hz, 0.42f, RING, r, g, b, 0.3f);
                }
            }
        }
        if (source instanceof MultiBufferSource.BufferSource bs) bs.endBatch(type());
    }

    /** Writes glowing quads relative to the camera. */
    private record Q(VertexConsumer vc, Vec3 cam, Vector3fc left, Vector3fc up) {
        void v(double x, double y, double z, float u, float vv, float r, float g, float b, float a) {
            vc.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .setUv(u, vv)
                .setColor(r, g, b, Math.max(0, Math.min(1, a)));
        }

        void billboard(double x, double y, double z, float half, int shape, float r, float g, float b, float a) {
            billboard(x, y, z, half, shape, r, g, b, a, false);
        }

        /** Camera-facing square of half-size {@code half}. */
        void billboard(double x, double y, double z, float half, int shape, float r, float g, float b, float a, boolean mirror) {
            float lx = left.x() * half, ly = left.y() * half, lz = left.z() * half;
            float ux = up.x() * half, uy = up.y() * half, uz = up.z() * half;
            float s = shape * 2f;
            float u0 = mirror ? 1 : 0, u1 = mirror ? 0 : 1;
            v(x + lx - ux, y + ly - uy, z + lz - uz, s + u0, 0, r, g, b, a);
            v(x - lx - ux, y - ly - uy, z - lz - uz, s + u1, 0, r, g, b, a);
            v(x - lx + ux, y - ly + uy, z - lz + uz, s + u1, 1, r, g, b, a);
            v(x + lx + ux, y + ly + uy, z + lz + uz, s + u0, 1, r, g, b, a);
        }

        /** Horizontal square (rings above your head). */
        void flat(double x, double y, double z, float half, int shape, float r, float g, float b, float a) {
            float s = shape * 2f;
            v(x - half, y, z - half, s, 0, r, g, b, a);
            v(x - half, y, z + half, s, 1, r, g, b, a);
            v(x + half, y, z + half, s + 1, 1, r, g, b, a);
            v(x + half, y, z - half, s + 1, 0, r, g, b, a);
        }

        /** Upright band around a circle (the crown). U runs once around, V from bottom to top. */
        void band(double x, double y, double z, float radius, float height, int segments, float spin, int shape,
                  float r, float g, float b, float a) {
            float s = shape * 2f;
            for (int i = 0; i < segments; i++) {
                double a0 = spin + Math.PI * 2 * i / segments, a1 = spin + Math.PI * 2 * (i + 1) / segments;
                double x0 = x + Math.cos(a0) * radius, z0 = z + Math.sin(a0) * radius;
                double x1 = x + Math.cos(a1) * radius, z1 = z + Math.sin(a1) * radius;
                float u0 = s + i / (float) segments, u1 = s + (i + 1) / (float) segments - 1e-4f;
                v(x0, y, z0, u0, 0, r, g, b, a);
                v(x1, y, z1, u1, 0, r, g, b, a);
                v(x1, y + height, z1, u1, 1, r, g, b, a);
                v(x0, y + height, z0, u0, 1, r, g, b, a);
            }
        }
    }
}
