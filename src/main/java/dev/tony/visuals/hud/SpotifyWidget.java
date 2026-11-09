package dev.tony.visuals.hud;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Ease;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.gui.Shapes;
import dev.tony.visuals.spotify.Spotify;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The now-playing card from the preview: rounded dark glass, cover tile (with dancing bars when there's no cover),
 * NOW PLAYING label, title, artist, progress bar with times, and previous / play-pause / next buttons.
 * The buttons can be clicked while chat is open (T), or bound to keys in Controls.
 */
public final class SpotifyWidget {
    private SpotifyWidget() {}

    private static final int W = 200, P = 8, ART = 40;
    private static final String[] BUTTONS = {"previous", "toggle", "next"};
    private static final Ease[] HOVER = {new Ease(0.2f, 0), new Ease(0.2f, 0), new Ease(0.2f, 0)};
    private static final Ease[] PRESS = {new Ease(0.25f, 0), new Ease(0.25f, 0), new Ease(0.25f, 0)};
    private static float lastProgress;
    private static volatile int hovered = -1;

    public static int[] size() {
        Config.Spotify s = Config.get().spotify;
        int text = 6 + 3 + 10 + 2 + 8;
        int bar = s.showProgress ? 5 + 3 + 3 + 6 : 0;
        return new int[]{W, Math.max(s.showArt ? ART : 0, text + bar) + 2 * P};
    }

    /** Button centres in card coordinates: {x, y} for previous, play/pause, next. */
    private static float[][] buttons() {
        float y = P + 3;
        return new float[][]{{W - P - 30, y}, {W - P - 17, y}, {W - P - 4, y}};
    }

    public static void draw(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Config.Spotify s = Config.get().spotify;
        Spotify.Track tr = Spotify.track();
        int accent = 0xFF000000 | (t.accent() & 0xFFFFFF);

        // Card: dark glass with a soft edge and shadow, like the preview.
        Kit.shadow(g, 0, 0, w, h, 12, 0.9f);
        Shapes.round(g, 0, 0, w, h, 12, 0x9E0E101E);
        Shapes.ring(g, 0, 0, w, h, 12, 0.5f, 0x1AFFFFFF);

        float tx = P;
        if (s.showArt) {
            float ay = (h - ART) / 2f;
            Identifier art = Spotify.art();
            if (art != null && tr != null) {
                g.pose().pushMatrix();
                g.pose().translate(P, ay);
                g.pose().scale(ART / 128f, ART / 128f);
                g.blit(RenderPipelines.GUI_TEXTURED, art, 0, 0, 0f, 0f, 128, 128, 128, 128, 0xFFFFFFFF);
                g.pose().popMatrix();
            } else {
                // Gradient-ish tile with three bouncing bars.
                Shapes.round(g, P, ay, ART, ART, 9, 0xFF1D2559);
                Shapes.round(g, P + ART * 0.3f, ay + ART * 0.3f, ART * 0.7f, ART * 0.7f, 9, (0x99 << 24) | (accent & 0xFFFFFF));
                float time = System.nanoTime() / 1e9f;
                boolean moving = tr != null && tr.playing();
                for (int i = 0; i < 3; i++) {
                    float bh = moving ? 5 + 6 * (0.5f + 0.5f * (float) Math.sin(time * 7 + i * 2.1f)) : 4;
                    Shapes.round(g, P + ART / 2f - 6.5f + i * 5, ay + ART - 7 - bh, 3, bh, 1.5f, 0xFFFFFFFF);
                }
            }
            tx = P + ART + 8;
        }
        float maxW = w - tx - P;

        // NOW PLAYING + buttons.
        Kit.textScaled(g, "NOW PLAYING", tx, P + 1, 0.7f, 0xFFA9B0C7, true);
        float[][] b = buttons();
        for (int i = 0; i < 3; i++) {
            float hv = HOVER[i].to(hovered == i ? 1 : 0), pr = PRESS[i].to(0);
            float size = 7.5f * (1 + 0.12f * hv - 0.15f * pr);
            if (hv > 0.01f) Shapes.circle(g, b[i][0], b[i][1], 6.5f, (Math.round(40 * hv) << 24) | 0xFFFFFF);
            String icon = i == 0 ? "prev" : i == 2 ? "next" : (tr != null && tr.playing() ? "pause" : "play");
            int col = Kit.lerpColour(0xFFC3C8DB, 0xFFFFFFFF, hv);
            Kit.lineIcon(g, icon, b[i][0] - size / 2, b[i][1] - size / 2, size, col);
        }

        String title = tr == null ? "Not playing" : tr.title();
        String artist = tr == null ? Spotify.status() : tr.playing() ? tr.artist() : "Paused · " + tr.artist();
        float ty = P + 9;
        g.pose().pushMatrix();
        g.pose().translate(tx, ty);
        g.pose().scale(1.12f, 1.12f);
        Kit.text(g, Kit.fit(title, (int) (maxW / 1.12f), true), 0, 0, 0xFFFFFFFF, true, t.textShadow);
        g.pose().popMatrix();
        Kit.text(g, Kit.fit(artist, (int) maxW, false), tx, ty + 12, 0xFFC3C8DB, false, t.textShadow);

        if (s.showProgress && (tr == null || tr.durationMs() > 0)) {
            float by = ty + 12 + 8 + 5;
            float frac = tr == null || tr.durationMs() <= 0 ? 0 : tr.progressNow() / (float) tr.durationMs();
            // Glide the bar on seek / song change instead of jumping.
            lastProgress = Math.abs(frac - lastProgress) > 0.02f ? lastProgress + (frac - lastProgress) * 0.25f : frac;
            Shapes.round(g, tx, by, maxW, 2.5f, 1.25f, 0x24FFFFFF);
            Shapes.round(g, tx, by, Math.max(2.5f, maxW * lastProgress), 2.5f, 1.25f, accent);
            String a = tr == null ? "0:00" : clock(tr.progressNow()), z = tr == null ? "0:00" : clock(tr.durationMs());
            Kit.textScaled(g, a, tx, by + 5, 0.7f, 0xFFA9B0C7, false);
            float zw = Kit.width(z) * 0.7f;
            Kit.textScaled(g, z, tx + maxW - zw, by + 5, 0.7f, 0xFFA9B0C7, false);
        }
    }

    private static String clock(long ms) {
        long sec = Math.max(0, ms / 1000);
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    // ------------------------------------------------------------ clicking the buttons (chat open)

    /** Card-local mouse position, or null if the card isn't on screen. */
    private static float[] local(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        Config.Widget wd = Config.get().widget("spotify");
        if (!wd.enabled) return null;
        Hud.Placement p = Hud.place(mc.font, "spotify", wd, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        return new float[]{(float) ((mx - p.x()) / wd.scale), (float) ((my - p.y()) / wd.scale)};
    }

    private static int buttonAt(double mx, double my) {
        float[] l = local(mx, my);
        if (l == null) return -1;
        float[][] b = buttons();
        for (int i = 0; i < 3; i++) if (Math.hypot(l[0] - b[i][0], l[1] - b[i][1]) <= 7) return i;
        return -1;
    }

    public static void registerClicks() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                int i = buttonAt(event.x(), event.y());
                if (i < 0 || event.button() != 0) return true;
                PRESS[i].snap(1);
                Spotify.control(BUTTONS[i]);
                return false;   // handled: don't pass the click to chat
            });
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                hovered = buttonAt(mx, my);
            });
            ScreenEvents.remove(screen).register(s -> hovered = -1);
        });
    }
}
