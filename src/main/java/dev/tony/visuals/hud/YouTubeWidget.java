package dev.tony.visuals.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Ease;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.gui.Shapes;
import dev.tony.visuals.youtube.YouTube;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * YouTube card: the video (or its thumbnail), title, channel, a play / pause button and a fullscreen button.
 * Fullscreen fills the whole screen with the video; open chat (T) to click the controls.
 */
public final class YouTubeWidget {
    private YouTubeWidget() {}

    private static final int W = 240, P = 7;
    private static final int VW = W - 2 * P, VH = Math.round(VW * 9 / 16f);
    private static final Ease[] HOVER = {new Ease(0.2f, 0), new Ease(0.2f, 0), new Ease(0.2f, 0), new Ease(0.2f, 0)};
    private static final Ease PRESS = new Ease(0.25f, 0);
    private static final Ease CONTROLS = new Ease(0.3f, 0);
    private static volatile int hovered = -1;
    private static long poked;
    private static boolean chatOpen;

    public static int[] size() {
        return new int[]{W, P + VH + 7 + 22 + P - 1};
    }

    private static float rowY() {
        return P + VH + 7 + 10;
    }

    /** {x, y} centres in card coordinates: 0 play / pause, 1 fullscreen. */
    private static float[][] buttons() {
        return new float[][]{{W - P - 30, rowY()}, {W - P - 9, rowY()}};
    }

    public static void blitView(GuiGraphics g, GpuTextureView view, int x, int y, int w, int h) {
        g.guiRenderState.submitGuiElement(new BlitRenderState(RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
            new Matrix3x2f(g.pose()), x, y, x + w, y + h, 0f, 1f, 0f, 1f, 0xFFFFFFFF, g.scissorStack.peek()));
    }

    public static void draw(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Hud.card(g, 0, 0, w, h, 14);
        int vx = P, vy = P;
        // Video area.
        Shapes.round(g, vx, vy, VW, VH, 9, 0xFF000000);
        // Render the page at the card's real size in screen pixels, so the video is sharp.
        float k = (float) Minecraft.getInstance().getWindow().getGuiScale() * Math.max(0.1f, Math.abs(g.pose().m00));
        GpuTextureView view = !YouTube.fullscreen() && YouTube.videoId() != null ? YouTube.frame(Math.round(VW * k), Math.round(VH * k)) : null;
        if (view != null) {
            blitView(g, view, vx, vy, VW, VH);
        } else {
            Identifier th = YouTube.thumbnail();
            if (th != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, th, vx, vy, 0f, 0f, VW, VH, VW, VH, 0xFFFFFFFF);
                Shapes.round(g, vx, vy, VW, VH, 0, 0x8C000000);
            }
            String msg = YouTube.videoId() == null ? "Paste a link on the YouTube page" : YouTube.fullscreen() ? "Playing fullscreen" : YouTube.status();
            Kit.lineIcon(g, "youtube", vx + VW / 2f - 11, vy + VH / 2f - 17, 22, 0xE6FFFFFF);
            float mw = Kit.width(msg, Kit.REGULAR, 0.8f);
            Kit.draw(g, msg, vx + VW / 2f - mw / 2, vy + VH / 2f + 9, 0.8f, 0xFFC3C8DB, Kit.REGULAR);
        }
        // Soften the video's square corners into the card.
        Shapes.ring(g, vx, vy, VW, VH, 9, 0.5f, 0x1AFFFFFF);

        float ty = P + VH + 7;
        float maxW = W - 2 * P - 46;
        String title = YouTube.videoId() == null ? "No video" : YouTube.title().isEmpty() ? "YouTube" : YouTube.title();
        String sub = YouTube.videoId() == null ? "YouTube" : YouTube.channel().isEmpty() ? YouTube.status() : YouTube.channel();
        Kit.draw(g, fit(title, maxW, Kit.BOLD, 0.95f), P + 1, ty + 1, 0.95f, 0xFFFFFFFF, Kit.BOLD);
        Kit.draw(g, fit(sub, maxW, Kit.REGULAR, 0.8f), P + 1, ty + 12, 0.8f, t.labelColour(), Kit.REGULAR);

        float[][] b = buttons();
        int tint = 0xFF000000 | (t.accent() & 0xFFFFFF);
        float hv0 = HOVER[0].to(hovered == 0 ? 1 : 0), pr = PRESS.to(0);
        float r = 8.5f * (1 + 0.1f * hv0 - 0.12f * pr);
        Shapes.circle(g, b[0][0], b[0][1], r + 2.5f, (Math.round(40 + 40 * hv0) << 24) | (tint & 0xFFFFFF));
        Shapes.circle(g, b[0][0], b[0][1], r, 0xFFFFFFFF);
        boolean playing = YouTube.videoId() != null && !YouTube.paused();
        Kit.lineIcon(g, playing ? "pause" : "play", b[0][0] - 4.5f + (playing ? 0 : 0.5f), b[0][1] - 4.5f, 9, 0xFF0B0D16);
        float hv1 = HOVER[1].to(hovered == 1 ? 1 : 0);
        if (hv1 > 0.01f) Shapes.circle(g, b[1][0], b[1][1], 8, (Math.round(36 * hv1) << 24) | 0xFFFFFF);
        Kit.lineIcon(g, "expand", b[1][0] - 4.5f, b[1][1] - 4.5f, 9, Kit.lerpColour(0xFFC3C8DB, 0xFFFFFFFF, hv1));
    }

    private static String fit(String s, float maxW, int face, float scale) {
        if (Kit.width(s, face, scale) <= maxW) return s;
        String out = s;
        while (out.length() > 1 && Kit.width(out + "…", face, scale) > maxW) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    // ------------------------------------------------------------ fullscreen

    private static float[] pill(int sw, int sh) {
        float w = 230, h = 34;
        return new float[]{(sw - w) / 2f, sh - h - 18, w, h};
    }

    /** {x, y} of play / pause and exit-fullscreen inside the pill (screen coordinates). */
    private static float[][] pillButtons(int sw, int sh) {
        float[] p = pill(sw, sh);
        return new float[][]{{p[0] + 19, p[1] + p[3] / 2}, {p[0] + p[2] - 17, p[1] + p[3] / 2}};
    }

    public static void drawFullscreen(GuiGraphics g, int sw, int sh) {
        if (!YouTube.fullscreen()) {
            CONTROLS.snap(0);
            return;
        }
        g.fill(0, 0, sw, sh, 0xFF000000);
        var win = Minecraft.getInstance().getWindow();
        GpuTextureView view = YouTube.frame(win.getWidth(), win.getHeight());
        if (view != null) {
            blitView(g, view, 0, 0, sw, sh);
        } else {
            Identifier th = YouTube.thumbnail();
            if (th != null) {
                int vh = Math.min(sh, sw * 9 / 16), vw = vh * 16 / 9;
                g.blit(RenderPipelines.GUI_TEXTURED, th, (sw - vw) / 2, (sh - vh) / 2, 0f, 0f, vw, vh, vw, vh, 0x80FFFFFF);
            }
            String msg = YouTube.status();
            Kit.draw(g, msg, sw / 2f - Kit.width(msg, Kit.BOLD, 1.2f) / 2, sh / 2f - 5, 1.2f, 0xFFFFFFFF, Kit.BOLD);
        }

        // Controls: show for a moment after a change, while paused, or while chat is open.
        boolean show = chatOpen || YouTube.paused() || System.currentTimeMillis() - poked < 2500;
        float o = CONTROLS.to(show ? 1 : 0);
        if (o < 0.02f) return;
        float[] p = pill(sw, sh);
        float lift = (1 - o) * 8;
        g.pose().pushMatrix();
        g.pose().translate(0, lift);
        Shapes.round(g, p[0], p[1], p[2], p[3], p[3] / 2, (Math.round(0xB8 * o) << 24) | 0x0E101E);
        Shapes.ring(g, p[0], p[1], p[2], p[3], p[3] / 2, 0.5f, (Math.round(0x26 * o) << 24) | 0xFFFFFF);
        float[][] b = pillButtons(sw, sh);
        float hv0 = HOVER[2].to(hovered == 2 ? 1 : 0);
        Shapes.circle(g, b[0][0], b[0][1], 11 * (1 + 0.08f * hv0), (Math.round(255 * o) << 24) | 0xFFFFFF);
        boolean playing = !YouTube.paused();
        int dark = (Math.round(255 * o) << 24) | 0x0B0D16;
        Kit.lineIcon(g, playing ? "pause" : "play", b[0][0] - 5.5f + (playing ? 0 : 0.6f), b[0][1] - 5.5f, 11, dark);
        String title = YouTube.title().isEmpty() ? "YouTube" : YouTube.title();
        float tx = b[0][0] + 18, maxW = b[1][0] - 14 - tx;
        Kit.draw(g, fit(title, maxW, Kit.BOLD, 0.9f), tx, p[1] + 8, 0.9f, (Math.round(255 * o) << 24) | 0xFFFFFF, Kit.BOLD);
        String ch = YouTube.channel();
        if (!ch.isEmpty()) Kit.draw(g, fit(ch, maxW, Kit.REGULAR, 0.75f), tx, p[1] + 19, 0.75f, (Math.round(200 * o) << 24) | 0xC3C8DB, Kit.REGULAR);
        float hv1 = HOVER[3].to(hovered == 3 ? 1 : 0);
        if (hv1 > 0.01f) Shapes.circle(g, b[1][0], b[1][1], 10, (Math.round(40 * hv1 * o) << 24) | 0xFFFFFF);
        Kit.lineIcon(g, "shrink", b[1][0] - 5, b[1][1] - 5, 10, (Math.round(255 * o) << 24) | 0xFFFFFF);
        g.pose().popMatrix();
        if (!chatOpen && !YouTube.paused()) {
            String hint = "Open chat (T) for controls";
            Kit.draw(g, hint, sw / 2f - Kit.width(hint, Kit.REGULAR, 0.7f) / 2, p[1] - 11 + lift, 0.7f, (Math.round(150 * o) << 24) | 0xFFFFFF, Kit.REGULAR);
        }
    }

    // ------------------------------------------------------------ clicks (chat open)

    private static int at(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        if (YouTube.fullscreen()) {
            float[][] b = pillButtons(sw, sh);
            if (Math.hypot(mx - b[0][0], my - b[0][1]) <= 12) return 2;
            if (Math.hypot(mx - b[1][0], my - b[1][1]) <= 11) return 3;
            return -1;
        }
        Config.Widget wd = Config.get().widget("youtube");
        if (!wd.enabled) return -1;
        Hud.Placement p = Hud.place(mc.font, "youtube", wd, sw, sh);
        float lx = (float) ((mx - p.x()) / wd.scale), ly = (float) ((my - p.y()) / wd.scale);
        float[][] b = buttons();
        if (Math.hypot(lx - b[0][0], ly - b[0][1]) <= 10) return 0;
        if (Math.hypot(lx - b[1][0], ly - b[1][1]) <= 9) return 1;
        if (lx >= P && lx < P + VW && ly >= P && ly < P + VH && YouTube.videoId() != null) return 4;   // click the video: pause
        return -1;
    }

    public static void poke() {
        poked = System.currentTimeMillis();
    }

    public static void registerClicks() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!(screen instanceof ChatScreen)) return;
            chatOpen = true;
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (event.button() != 0) return true;
                int i = at(event.x(), event.y());
                if (i < 0) return true;
                PRESS.snap(1);
                poke();
                switch (i) {
                    case 0, 2, 4 -> YouTube.togglePause();
                    case 1, 3 -> YouTube.toggleFullscreen();
                    default -> {}
                }
                return false;
            });
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> hovered = at(mx, my));
            ScreenEvents.remove(screen).register(s -> {
                hovered = -1;
                chatOpen = false;
            });
        });
    }
}
