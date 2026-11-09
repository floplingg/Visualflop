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

    private static final int W = 228, P = 10, ART = 50, R = 16;
    private static final String[] BUTTONS = {"previous", "toggle", "next"};
    private static final Ease[] HOVER = {new Ease(0.2f, 0), new Ease(0.2f, 0), new Ease(0.2f, 0)};
    private static final Ease[] PRESS = {new Ease(0.25f, 0), new Ease(0.25f, 0), new Ease(0.25f, 0)};
    private static final Ease TINT_R = new Ease(0.6f), TINT_G = new Ease(0.6f), TINT_B = new Ease(0.6f);
    private static float lastProgress;
    private static volatile int hovered = -1;

    private static int topH() {
        return Config.get().spotify.showArt ? ART : 40;
    }

    /** y of the controls row (centre), card coordinates. */
    private static float rowY() {
        float y = P + topH() + 9;
        if (Config.get().spotify.showProgress) y += 3 + 4;
        return y + 9;
    }

    public static int[] size() {
        return new int[]{W, Math.round(rowY() + 9 + P - 1)};
    }

    /** Button centres in card coordinates: {x, y} for previous, play/pause, next. */
    private static float[][] buttons() {
        float y = rowY(), c = W / 2f;
        return new float[][]{{c - 26, y}, {c, y}, {c + 26, y}};
    }

    /** Glow colour: the cover's own colour when there is one, else the accent. Fades between songs. */
    private static int tint(int accent) {
        int c = Spotify.artColour();
        if (c < 0) c = accent & 0xFFFFFF;
        int r = Math.round(TINT_R.to((c >> 16) & 255)), g = Math.round(TINT_G.to((c >> 8) & 255)), b = Math.round(TINT_B.to(c & 255));
        return 0xFF000000 | (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    public static void draw(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Config.Spotify s = Config.get().spotify;
        Spotify.Track tr = Spotify.track();
        int tint = tint(t.accent());
        boolean playing = tr != null && tr.playing();

        // Card: frosted glass with a soft glow in the cover's colour.
        Hud.card(g, 0, 0, w, h, R);
        for (int i = 0; i < 5; i++) {
            float grow = 6 + i * 7;
            Shapes.round(g, P - grow * 0.4f, P - grow * 0.4f, Math.min(w - P, ART + grow * 2.2f), Math.min(h - P, ART + grow), R,
                (Math.round(9 - i * 1.4f) << 24) | (tint & 0xFFFFFF));
        }

        float tx = P;
        if (s.showArt) {
            float ay = P;
            Kit.shadow(g, P, ay, ART, ART, 10, 0.8f);
            Identifier art = Spotify.art();
            if (art != null && tr != null) {
                g.pose().pushMatrix();
                g.pose().translate(P, ay);
                g.pose().scale(ART / 128f, ART / 128f);
                g.blit(RenderPipelines.GUI_TEXTURED, art, 0, 0, 0f, 0f, 128, 128, 128, 128, 0xFFFFFFFF);
                g.pose().popMatrix();
            } else {
                Shapes.round(g, P, ay, ART, ART, 10, 0xFF1D2559);
                Shapes.round(g, P + ART * 0.25f, ay + ART * 0.25f, ART * 0.75f, ART * 0.75f, 10, (0xAA << 24) | (tint & 0xFFFFFF));
                Kit.lineIcon(g, "music", P + ART / 2f - 9, ay + ART / 2f - 9, 18, 0xE6FFFFFF);
            }
            tx = P + ART + 10;
        }
        float maxW = w - tx - P;

        // NOW PLAYING + little equaliser while a song plays.
        float ly = P + 2;
        Kit.tracked(g, playing ? "Now playing" : "Paused", tx, ly, 0.62f, t.labelColour());
        if (playing) {
            float ex = tx + Kit.trackedWidth("Now playing", 0.62f) + 5, time = System.nanoTime() / 1e9f;
            for (int i = 0; i < 3; i++) {
                float bh = 1.5f + 3.5f * (0.5f + 0.5f * (float) Math.sin(time * 8 + i * 2.1f));
                Shapes.round(g, ex + i * 2.6f, ly + 5.5f - bh, 1.6f, bh, 0.8f, tint);
            }
        }
        String title = tr == null ? "Not playing" : tr.title();
        String artist = tr == null ? Spotify.status() : tr.artist();
        Kit.draw(g, fit(title, maxW, Kit.HEAVY, 1.12f), tx, ly + 10, 1.12f, 0xFFFFFFFF, Kit.HEAVY);
        Kit.draw(g, fit(artist, maxW, Kit.REGULAR, 0.85f), tx, ly + 23, 0.85f, 0xFFC3C8DB, Kit.REGULAR);

        float row = rowY();
        if (s.showProgress) {
            float by = P + topH() + 9, bw = w - 2 * P;
            float frac = tr == null || tr.durationMs() <= 0 ? 0 : tr.progressNow() / (float) tr.durationMs();
            lastProgress = Math.abs(frac - lastProgress) > 0.02f ? lastProgress + (frac - lastProgress) * 0.25f : frac;
            Shapes.round(g, P, by, bw, 3, 1.5f, 0x22FFFFFF);
            Shapes.round(g, P, by, Math.max(3, bw * lastProgress), 3, 1.5f, tint);
            float kx = P + Math.max(1.5f, bw * lastProgress);
            Shapes.circle(g, kx, by + 1.5f, 4.2f, (0x40 << 24) | (tint & 0xFFFFFF));
            Shapes.circle(g, kx, by + 1.5f, 2.6f, 0xFFFFFFFF);
            String a = tr == null ? "0:00" : clock(tr.progressNow()), z = tr == null ? "0:00" : clock(tr.durationMs());
            Kit.draw(g, a, P, row - 3, 0.72f, 0xFFA9B0C7, Kit.MONO);
            Kit.draw(g, z, w - P - Kit.width(z, Kit.MONO, 0.72f), row - 3, 0.72f, 0xFFA9B0C7, Kit.MONO);
        }

        // Controls: previous, a filled play / pause button, next.
        float[][] b = buttons();
        for (int i = 0; i < 3; i++) {
            float hv = HOVER[i].to(hovered == i ? 1 : 0), pr = PRESS[i].to(0);
            float k = 1 + 0.1f * hv - 0.14f * pr;
            if (i == 1) {
                float rr = 9 * k;
                Shapes.circle(g, b[i][0], b[i][1], rr + 3, (Math.round(40 + 40 * hv) << 24) | (tint & 0xFFFFFF));
                Shapes.circle(g, b[i][0], b[i][1], rr, 0xFFFFFFFF);
                String icon = playing ? "pause" : "play";
                float sz = 9 * k;
                Kit.lineIcon(g, icon, b[i][0] - sz / 2 + (playing ? 0 : 0.6f), b[i][1] - sz / 2, sz, 0xFF0B0D16);
            } else {
                if (hv > 0.01f) Shapes.circle(g, b[i][0], b[i][1], 7.5f, (Math.round(36 * hv) << 24) | 0xFFFFFF);
                float sz = 8.5f * k;
                Kit.lineIcon(g, i == 0 ? "prev" : "next", b[i][0] - sz / 2, b[i][1] - sz / 2, sz, Kit.lerpColour(0xFFC3C8DB, 0xFFFFFFFF, hv));
            }
        }
    }

    private static String fit(String s, float maxW, int face, float scale) {
        if (Kit.width(s, face, scale) <= maxW) return s;
        String out = s;
        while (out.length() > 1 && Kit.width(out + "…", face, scale) > maxW) out = out.substring(0, out.length() - 1);
        return out + "…";
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
        for (int i = 0; i < 3; i++) if (Math.hypot(l[0] - b[i][0], l[1] - b[i][1]) <= (i == 1 ? 10 : 8)) return i;
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
