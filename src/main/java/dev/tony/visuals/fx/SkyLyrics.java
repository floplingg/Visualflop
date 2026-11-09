package dev.tony.visuals.fx;

import com.mojang.math.Axis;
import dev.tony.visuals.Config;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.spotify.Lyrics;
import dev.tony.visuals.spotify.Spotify;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Sky lyrics: the sung line floats in the air ahead of you. Turning your head left or right, it follows a moment later
 * with a cubic-out glide (instantly if you turn fast). Looking up or down doesn't move it.
 */
public final class SkyLyrics {
    private SkyLyrics() {}

    private static final float DELAY = 0.08f, GLIDE = 0.55f, FAST = 220f;   // seconds, seconds, degrees per second

    private static float shown = Float.NaN, from, to, lastTarget;
    private static long glideStart, lastNanos, moveSince;
    private static int lastIndex = -1;
    private static long lineChanged;
    private static String prevText = "";

    private static float wrap(float a) {
        a %= 360f;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    private static float cubicOut(float t) {
        float u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    /** Eased yaw that trails the camera's yaw. */
    private static float follow(float target) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1e9f;
        lastNanos = now;
        if (Float.isNaN(shown) || dt > 0.5f) {
            shown = from = to = lastTarget = target;
            return shown;
        }
        float speed = dt > 0 ? Math.abs(wrap(target - lastTarget)) / dt : 0;
        lastTarget = target;
        if (speed > FAST) {
            // Turning fast: keep the words in front of you.
            shown = from = to = target;
            glideStart = 0;
            return shown;
        }
        if (Math.abs(wrap(target - to)) > 0.3f) {
            // A new place to go: start gliding there after a tiny pause.
            if (moveSince == 0) moveSince = now;
            if ((now - moveSince) / 1e9f >= DELAY) {
                from = shown;
                to = target;
                glideStart = now;
                moveSince = 0;
            }
        } else {
            moveSince = 0;
        }
        if (glideStart != 0) {
            float k = cubicOut((now - glideStart) / 1e9f / GLIDE);
            shown = wrap(from + wrap(to - from) * k);
            if (k >= 1) glideStart = 0;
        }
        return shown;
    }

    public static void render(WorldRenderContext ctx) {
        Config.Lyrics cfg = Config.get().lyrics;
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.sky || mc.player == null || mc.options.hideGui) {
            shown = Float.NaN;
            return;
        }
        Spotify.Track tr = Spotify.track();
        Lyrics.follow(tr);
        List<Lyrics.Line> lines = Lyrics.lines();
        if (tr == null || !tr.playing() || lines.isEmpty()) return;
        int idx = Lyrics.current(tr.progressNow());
        if (idx < 0) return;
        String text = lines.get(idx).text().trim();
        if (idx != lastIndex) {
            prevText = lastIndex >= 0 && lastIndex < lines.size() ? lines.get(lastIndex).text().trim() : "";
            lastIndex = idx;
            lineChanged = System.nanoTime();
        }
        String next = idx + 1 < lines.size() ? lines.get(idx + 1).text().trim() : "";

        Camera cam = mc.gameRenderer.getMainCamera();
        float yaw = follow(cam.yRot());
        double rad = Math.toRadians(yaw);
        float dist = cfg.skyDistance;
        // Ahead of you, level with your eyes plus a little lift; pitch is ignored.
        float x = (float) (-Math.sin(rad) * dist), z = (float) (Math.cos(rad) * dist), y = cfg.skyHeight;

        MultiBufferSource source = ctx.consumers();
        if (source == null) return;
        float t = Math.min(1, (System.nanoTime() - lineChanged) / 1e9f / 0.35f);
        float in = cubicOut(t);
        float scale = 0.035f * cfg.skyScale * dist / 6f;
        int accent = Config.theme().accent() & 0xFFFFFF;
        int rgb = cfg.skyAccent ? accent : cfg.skyColour & 0xFFFFFF;
        int glow = cfg.skyGlow ? (cfg.skyGlowAccent ? accent : cfg.skyGlowColour & 0xFFFFFF) : -1;

        // Old line drifts up and fades; new line rises in.
        if (in < 1 && !prevText.isEmpty()) draw(mc.font, source, cfg, prevText, x, y, z, yaw, scale, 14 * -in, 1 - in, rgb, glow);
        if (!text.isEmpty()) draw(mc.font, source, cfg, text, x, y, z, yaw, scale, 10 * (1 - in), in, rgb, glow);
        if (cfg.skyNext && !next.isEmpty()) draw(mc.font, source, cfg, next, x, y, z, yaw, scale * 0.62f, 26 + 8 * (1 - in), 0.5f * in, 0xFFFFFF, -1);
    }

    private static Component styled(String s, Config.SkyFont f) {
        return switch (f) {
            case HEAVY -> Kit.face(s, Kit.HEAVY);
            case BOLD -> Kit.face(s, Kit.BOLD);
            case REGULAR -> Kit.face(s, Kit.REGULAR);
            case MONO -> Kit.face(s, Kit.MONO);
            case MINECRAFT -> Component.literal(s);
        };
    }

    // Glow: soft copies of the words in a ring behind them.
    private static final int GLOW_TAPS = 12;

    private static void draw(Font font, MultiBufferSource source, Config.Lyrics cfg, String s, float x, float y, float z, float yaw,
                             float scale, float dy, float alpha, int rgb, int glow) {
        if (alpha <= 0.02f) return;
        Component c = styled(s, cfg.skyFont);
        Matrix4f m = new Matrix4f()
            .translate(x, y, z)
            .rotate(Axis.YP.rotationDegrees(180 - yaw))
            .scale(scale, -scale, scale);
        float w = font.width(c);
        if (glow >= 0 && cfg.skyGlowStrength > 0) {
            float strength = cfg.skyGlowStrength / 100f;
            for (int ring = 1; ring <= 2; ring++) {
                float rad = 1.1f * ring * cfg.skyGlowSize;
                int a = Math.max(5, Math.round(alpha * strength * (ring == 1 ? 46 : 22)));
                for (int i = 0; i < GLOW_TAPS; i++) {
                    double ang = (i + (ring == 2 ? 0.5 : 0)) * Math.PI * 2 / GLOW_TAPS;
                    // A hair behind the words so they never fight for the same depth.
                    Matrix4f gm = new Matrix4f(m).translate((float) Math.cos(ang) * rad, (float) Math.sin(ang) * rad, 0.4f + ring * 0.2f);
                    font.drawInBatch(c, -w / 2f, dy, (a << 24) | glow, false, gm, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                }
            }
        }
        int a = Math.max(5, Math.round(alpha * 255));
        // Faint through walls, full strength in the open (like name tags).
        font.drawInBatch(c, -w / 2f, dy, (Math.max(5, a / 4) << 24) | rgb, false, m, source, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(c, -w / 2f, dy, (a << 24) | rgb, false, m, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }
}
