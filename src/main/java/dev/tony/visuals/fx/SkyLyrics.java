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
        if (dev.tony.visuals.compat.Shaders.active()) return;   // drawn after the shader pack instead
        draw(ctx.consumers());
    }

    private static boolean warned;

    /** Registers the text pipelines at startup, like the other effects. */
    public static void init() {
        SkyText.init();
    }

    public static void draw(MultiBufferSource source) {
        try {
            drawLyrics(source);
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                org.slf4j.LoggerFactory.getLogger("flopvisuals").warn("Sky lyrics failed to draw", e);
            }
        }
    }

    private static void drawLyrics(MultiBufferSource source) {
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

        if (source == null) return;
        float t = Math.min(1, (System.nanoTime() - lineChanged) / 1e9f / 0.35f);
        float in = cubicOut(t);
        int accent = Config.theme().accent() & 0xFFFFFF;
        int rgb = cfg.skyAccent ? accent : cfg.skyColour & 0xFFFFFF;
        int glow = cfg.skyGlow ? (cfg.skyGlowAccent ? accent : cfg.skyGlowColour & 0xFFFFFF) : -1;
        // Size scales with distance, so the words look the same size however far away you put them.
        float pixel = 0.045f * dist * cfg.skyScale / SkyText.EM;
        Matrix4f m = new Matrix4f().translate(x, y, z).rotate(Axis.YP.rotationDegrees(180 - yaw)).scale(pixel, -pixel, pixel);
        float em = SkyText.EM;

        // Old line drifts up and fades; new line rises in.
        if (in < 1 && !prevText.isEmpty()) line(mc.font, source, cfg, prevText, m, -0.9f * em * in, 1 - in, rgb, glow);
        if (!text.isEmpty()) line(mc.font, source, cfg, text, m, 0.6f * em * (1 - in), in, rgb, glow);
        if (cfg.skyNext && !next.isEmpty()) {
            Matrix4f n = new Matrix4f(m).translate(0, em * (1.25f + 0.4f * (1 - in)), 0).scale(0.6f);
            line(mc.font, source, cfg, next, n, 0, 0.5f * in, 0xFFFFFF, -1);
        }
    }

    /** For the screenshot test: {texture, width, height} of a line as the sky would draw it, or null. */
    public static Object[] preview(String text) {
        SkyText.Line l = SkyText.get(text, Config.get().lyrics.skyFont, Config.get().lyrics.skyGlowSize);
        return l == null ? null : new Object[]{l.id(), l.w(), l.h()};
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

    private static void line(Font font, MultiBufferSource source, Config.Lyrics cfg, String s, Matrix4f m, float dy,
                             float alpha, int rgb, int glow) {
        if (alpha <= 0.02f) return;
        SkyText.Line l = SkyText.get(s, cfg.skyFont, cfg.skyGlowSize);
        float glowA = glow >= 0 ? alpha * cfg.skyGlowStrength / 100f : 0;
        if (l != null) {
            SkyText.draw(source, l, m, dy, rgb, alpha, glow, glowA);
            return;
        }
        // Minecraft's own font (chosen, or for letters the smooth fonts don't have, like Chinese or Korean).
        Component c = styled(s, cfg.skyFont == Config.SkyFont.MINECRAFT ? Config.SkyFont.MINECRAFT : cfg.skyFont);
        float k = SkyText.EM / 9f;
        Matrix4f fm = new Matrix4f(m).translate(0, dy, 0).scale(k);
        float w = font.width(c);
        int a = Math.max(5, Math.round(alpha * 255));
        if (glow >= 0 && glowA > 0) {
            int ga = Math.max(5, Math.round(glowA * 60));
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4;
                Matrix4f gm = new Matrix4f(fm).translate((float) Math.cos(ang) * 0.6f * cfg.skyGlowSize, (float) Math.sin(ang) * 0.6f * cfg.skyGlowSize, 0);
                font.drawInBatch(c, -w / 2f, -4.5f, (ga << 24) | glow, false, gm, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        }
        font.drawInBatch(c, -w / 2f, -4.5f, (Math.max(5, a / 4) << 24) | rgb, false, fm, source, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(c, -w / 2f, -4.5f, (a << 24) | rgb, false, fm, source, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }
}
