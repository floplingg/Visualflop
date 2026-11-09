package dev.tony.visuals.hud;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Ease;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.gui.Shapes;
import dev.tony.visuals.pay.Auction;
import dev.tony.visuals.pay.Payments;
import dev.tony.visuals.pay.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Money gained / lost, the money graph, and the auction in the middle of the screen. */
public final class MoneyWidgets {
    private MoneyWidgets() {}

    private static final int GREEN = 0xFF4ADE80, RED = 0xFFFF5C6C, DIM = 0xFFA9B0C7, SOFT = 0xFFC3C8DB, GOLD = 0xFFFFD36B;

    private static int tone(double v) {
        return v < 0 ? RED : GREEN;
    }

    private static float pixel() {
        return 1f / (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    // ------------------------------------------------------------ +102.63M

    private static final float LABEL = 0.72f;

    private static String label() {
        return Tracker.on() ? "Session" : "Last session";
    }

    public static int[] profitSize() {
        Theme t = Config.theme();
        float w = 9 + Kit.trackedWidth(label(), LABEL) + 6 + Kit.width(Tracker.label(Tracker.profit()), Kit.MONO, 1);
        return new int[]{(int) Math.ceil(w + 2 * Hud.padH(t)), 8 + 2 * Hud.padV(t)};
    }

    public static void drawProfit(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Hud.card(g, 0, 0, w, h);
        double p = Tracker.profit();
        int col = tone(p);
        float x = Hud.padH(t), cy = h / 2f;
        // Live dot: breathing while a session runs, hollow grey otherwise.
        if (Tracker.on()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 3e8);
            Shapes.circle(g, x + 2.5f, cy, 2.5f + 1.6f * pulse, (Math.round(60 * (1 - pulse)) << 24) | (col & 0xFFFFFF));
            Shapes.circle(g, x + 2.5f, cy, 2.2f, col);
        } else {
            Shapes.ring(g, x + 0.9f, cy - 1.6f, 3.2f, 3.2f, 1.6f, 0.6f, 0xFF6A7088);
        }
        x += 9;
        Kit.tracked(g, label(), x, Hud.padV(t) + 2.2f, LABEL, t.labelColour());
        x += Kit.trackedWidth(label(), LABEL) + 6;
        Kit.draw(g, Tracker.label(p), x, Hud.padV(t), 1, col, Kit.MONO);
    }

    // ------------------------------------------------------------ graph

    private static final int GW = 168, GH = 82;

    public static int[] graphSize() {
        return new int[]{GW, GH};
    }

    public static void drawGraph(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Hud.card(g, 0, 0, w, h, 12);
        double p = Tracker.profit();
        int col = tone(p);
        Config.Session s = Tracker.shown();
        Kit.tracked(g, label(), 9, 8, LABEL, t.labelColour());
        if (s != null) {
            String d = Tracker.duration(s);
            Kit.draw(g, d, w - 9 - Kit.width(d, Kit.MONO, 0.7f), 8, 0.7f, DIM, Kit.MONO);
        }
        Kit.draw(g, Tracker.label(p), 9, 17, 1.35f, col, Kit.MONO);

        List<Double> hist = Tracker.history();
        float gx = 9, gy = 33, gw = w - 18, gh = h - 33 - 9;
        if (hist.size() < 2) {
            String msg = Tracker.on() ? "Waiting for payments…" : "Start a session on the Gamble page";
            Kit.draw(g, msg, w / 2f - Kit.width(msg, Kit.REGULAR, 0.75f) / 2, gy + gh / 2 - 3, 0.75f, DIM, Kit.REGULAR);
            return;
        }
        double lo = 0, hi = 0;
        for (double v : hist) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (hi - lo < 1) hi = lo + 1;
        double pad = (hi - lo) * 0.1;
        lo -= pad;
        hi += pad;
        final double flo = lo, fhi = hi;
        java.util.function.DoubleUnaryOperator yOf = v -> gy + gh - (v - flo) / (fhi - flo) * gh;
        float zero = (float) yOf.applyAsDouble(0);
        for (float x = gx; x < gx + gw; x += 4) Shapes.round(g, x, zero - 0.25f, 2f, 0.5f, 0, 0x30FFFFFF);

        // Drawn one screen pixel wide at a time so the line is crisp at any GUI scale.
        float px = pixel();
        int n = hist.size(), cols = Math.max(2, Math.round(gw / px));
        float prevY = Float.NaN;
        for (int i = 0; i <= cols; i++) {
            double f = i / (double) cols * (n - 1);
            int a = (int) Math.floor(f), b = Math.min(n - 1, a + 1);
            double k = f - a;
            k = k * k * (3 - 2 * k);   // smooth between payments
            double v = hist.get(a) + (hist.get(b) - hist.get(a)) * k;
            float y = (float) yOf.applyAsDouble(v);
            int c = tone(v);
            float x = gx + i * px;
            // Area fill that fades out toward the zero line.
            float top = Math.min(y, zero), bot = Math.max(y, zero);
            float span = bot - top;
            if (span > px) {
                int bands = 4;
                for (int j = 0; j < bands; j++) {
                    float b0 = top + span * j / bands, b1 = top + span * (j + 1) / bands;
                    float near = y < zero ? 1 - (float) j / bands : (float) (j + 1) / bands;
                    Shapes.round(g, x, b0, px, b1 - b0, 0, (Math.round(0x38 * near) << 24) | (c & 0xFFFFFF));
                }
            }
            float y0 = Float.isNaN(prevY) ? y : prevY;
            float t0 = Math.min(y0, y) - 0.55f, t1 = Math.max(y0, y) + 0.55f;
            Shapes.round(g, x, t0, px * 1.5f, t1 - t0, 0, c);
            prevY = y;
        }
        float ly = (float) yOf.applyAsDouble(hist.get(n - 1));
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 3e8);
        Shapes.circle(g, gx + gw, ly, 2.4f + 2f * pulse, (Math.round(70 * (1 - pulse)) << 24) | (col & 0xFFFFFF));
        Shapes.circle(g, gx + gw, ly, 2.2f, col);
        Shapes.circle(g, gx + gw, ly, 1f, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------ auction, centre of the screen

    private static final Ease SHOW = new Ease(0.35f, 0);
    private static final int AW = 260, AH = 150;

    /** Shows by itself while an auction runs (and for a few seconds after, with the winner). About 30% of the screen. */
    public static void drawAuctionOverlay(GuiGraphics g, int sw, int sh) {
        boolean on = Auction.active() || Auction.result() != null;
        float o = SHOW.to(on ? 1 : 0);
        if (o < 0.01f) return;
        float scale = Math.min(sh * 0.30f / AH, sw * 0.5f / AW) * (0.94f + 0.06f * o);
        g.pose().pushMatrix();
        g.pose().translate(sw / 2f, sh / 2f + (1 - o) * 10);
        g.pose().scale(scale, scale);
        g.pose().translate(-AW / 2f, -AH / 2f);
        drawAuction(g, AW, AH, o);
        g.pose().popMatrix();
    }

    private static int fade(int argb, float o) {
        return (Math.round((argb >>> 24) * o) << 24) | (argb & 0xFFFFFF);
    }

    private static void drawAuction(GuiGraphics g, int w, int h, float o) {
        Theme t = Config.theme();
        int accent = 0xFF000000 | (t.accent() & 0xFFFFFF);
        Kit.shadow(g, 0, 0, w, h, 18, 1.2f * o);
        if (!(t.hudBlur && o > 0.98f && Backdrop.draw(g, 0, 0, w, h, 18))) {
            Shapes.round(g, 0, 0, w, h, 18, fade(0xC00E101E, o));
        } else {
            Shapes.round(g, 0, 0, w, h, 18, fade(0x660E101E, o));
        }
        Shapes.ring(g, 0, 0, w, h, 18, 0.5f, fade(0x22FFFFFF, o));
        // Accent glow along the top edge.
        Shapes.roundClip(g, 0, 0, w, h, 18, 30, 0, w - 30, 1.2f, fade((0xCC << 24) | (accent & 0xFFFFFF), o));

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 2.5e8);
        if (!Auction.active()) {
            String r = Auction.result();
            if (r == null) r = "";
            boolean won = r.contains(" wins the ");
            Kit.tracked(g, "Auction ended", 16, 16, 0.85f, fade(DIM, o));
            if (won) {
                String name = r.substring(0, r.indexOf(" wins the "));
                String item = r.substring(r.indexOf(" wins the ") + 10).replaceAll("!$", "");
                float y = 46;
                centre(g, name, w, y, 2.0f, fade(GREEN, o), Kit.HEAVY);
                centre(g, "wins the", w, y + 24, 0.9f, fade(SOFT, o), Kit.REGULAR);
                centre(g, Kit.fit(item, (int) ((w - 30) / 1.5f), true) + "!", w, y + 38, 1.5f, fade(GOLD, o), Kit.HEAVY);
            } else {
                centre(g, r, w, 66, 1.2f, fade(SOFT, o), Kit.BOLD);
            }
            return;
        }

        float left = Auction.secondsLeft();
        boolean hurry = left < 5;
        // Header: live dot + AUCTION, countdown on the right.
        Shapes.circle(g, 19, 19.5f, 2.6f + 2f * pulse, fade((Math.round(70 * (1 - pulse)) << 24) | 0xFF5C6C, o));
        Shapes.circle(g, 19, 19.5f, 2.4f, fade(RED, o));
        Kit.tracked(g, "Live auction", 27, 16f, 0.85f, fade(DIM, o));
        String time = left >= 60 ? (int) left / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", (int) left % 60)
            : String.format(java.util.Locale.ROOT, "%.1f", left);
        float ts = 2.0f;
        float tw = Kit.width(time, Kit.MONO, ts);
        Kit.draw(g, time, w - 16 - tw - (left < 60 ? 7 : 0), 11, ts, fade(hurry ? RED : 0xFFFFFFFF, o), Kit.MONO);
        if (left < 60) Kit.draw(g, "s", w - 16 - 6, 18, 1f, fade(DIM, o), Kit.MONO);

        // Item.
        Kit.draw(g, Kit.fit(Auction.item(), (int) ((w - 32) / 1.6f), true), 16, 34, 1.6f, fade(GOLD, o), Kit.HEAVY);

        // Countdown bar.
        float bw = w - 32, frac = Auction.fraction();
        Shapes.round(g, 16, 56, bw, 3, 1.5f, fade(0x24FFFFFF, o));
        int barCol = hurry ? RED : accent;
        Shapes.round(g, 16, 56, Math.max(3, bw * frac), 3, 1.5f, fade(barCol, o));
        Shapes.circle(g, 16 + Math.max(1.5f, bw * frac), 57.5f, 3.2f, fade((0x55 << 24) | (barCol & 0xFFFFFF), o));

        // Top bids.
        List<Auction.Bid> r = Auction.ranking();
        if (r.isEmpty()) {
            centre(g, "No bids yet: pay to bid", w, 94, 1f, fade(DIM, o), Kit.REGULAR);
            return;
        }
        for (int i = 0; i < Math.min(3, r.size()); i++) {
            Auction.Bid b = r.get(i);
            float y = 68 + i * 25;
            boolean lead = i == 0;
            Shapes.round(g, 12, y, w - 24, 21, 10, fade(lead ? (0x24 << 24) | (GREEN & 0xFFFFFF) : 0x0EFFFFFF, o));
            // Rank badge.
            Shapes.circle(g, 25, y + 10.5f, 6.5f, fade(lead ? GREEN : 0x24FFFFFF, o));
            String rank = String.valueOf(i + 1);
            Kit.draw(g, rank, 25 - Kit.width(rank, Kit.BOLD, 0.8f) / 2, y + 7.5f, 0.8f, fade(lead ? 0xFF0B1A10 : SOFT, o), Kit.BOLD);
            Kit.draw(g, Kit.fit(b.player(), (int) ((w - 130) / 1.05f), lead), 38, y + 6, 1.05f, fade(lead ? 0xFFFFFFFF : SOFT, o), lead ? Kit.HEAVY : Kit.BOLD);
            String amt = "$" + Payments.money(b.total());
            Kit.draw(g, amt, w - 22 - Kit.width(amt, Kit.MONO, 1.05f), y + 6, 1.05f, fade(lead ? GREEN : SOFT, o), Kit.MONO);
        }
    }

    private static void centre(GuiGraphics g, String s, int w, float y, float scale, int colour, int face) {
        Kit.draw(g, s, w / 2f - Kit.width(s, face, scale) / 2, y, scale, colour, face);
    }
}
