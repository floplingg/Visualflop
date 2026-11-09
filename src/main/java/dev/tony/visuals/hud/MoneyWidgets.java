package dev.tony.visuals.hud;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.gui.Shapes;
import dev.tony.visuals.pay.Auction;
import dev.tony.visuals.pay.Payments;
import dev.tony.visuals.pay.Tracker;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Money gained / lost, the money graph, and the auction countdown. */
public final class MoneyWidgets {
    private MoneyWidgets() {}

    private static final int GREEN = 0xFF7CFF8A, RED = 0xFFFF6B6B, DIM = 0xFFA9B0C7;

    // ------------------------------------------------------------ +(102.63M)

    private static String profitText() {
        return Tracker.label(Tracker.profit());
    }

    public static int[] profitSize() {
        Theme t = Config.theme();
        int w = Kit.width(profitText(), true) + (Tracker.on() ? 0 : Kit.width(" paused") + 0);
        return new int[]{w + 2 * t.padding + 2 + 8, 8 + 2 * t.padding + 1};
    }

    public static void drawProfit(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Hud.card(g, 0, 0, w, h);
        double p = Tracker.profit();
        int col = p < 0 ? RED : GREEN;
        // Tracking dot: lit while tracking, hollow grey while paused.
        float cy = h / 2f;
        if (Tracker.on()) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(System.nanoTime() / 2.5e8);
            Shapes.circle(g, t.padding + 3, cy, 3.2f, (Math.round(70 * pulse) << 24) | (col & 0xFFFFFF));
            Shapes.circle(g, t.padding + 3, cy, 2f, col);
        } else {
            Shapes.circle(g, t.padding + 3, cy, 2.2f, 0xFF6A7088);
        }
        float x = t.padding + 9;
        Kit.text(g, profitText(), x, t.padding + 1, col, true, t.textShadow);
        if (!Tracker.on()) Kit.text(g, " paused", x + Kit.width(profitText(), true), t.padding + 1, DIM, false, t.textShadow);
    }

    // ------------------------------------------------------------ graph

    private static final int GW = 150, GH = 70;

    public static int[] graphSize() {
        return new int[]{GW, GH};
    }

    public static void drawGraph(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Hud.card(g, 0, 0, w, h);
        double p = Tracker.profit();
        Kit.textScaled(g, "PROFIT", 6, 5, 0.7f, DIM, true);
        String lab = Tracker.label(p);
        Kit.textScaled(g, lab, w - 6 - Kit.width(lab, true) * 0.8f, 4.5f, 0.8f, p < 0 ? RED : GREEN, true);

        List<Double> hist = Tracker.history();
        float gx = 6, gy = 16, gw = w - 12, gh = h - 22;
        if (hist.size() < 2) {
            Kit.textCenter(g, Tracker.on() ? "Waiting for payments…" : "Tracking paused", w / 2f, gy + gh / 2 - 4, DIM, false);
            return;
        }
        double lo = 0, hi = 0;
        for (double v : hist) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (hi - lo < 1) hi = lo + 1;
        double pad = (hi - lo) * 0.08;
        lo -= pad;
        hi += pad;
        final double flo = lo, fhi = hi;
        java.util.function.DoubleUnaryOperator yOf = v -> gy + gh - (v - flo) / (fhi - flo) * gh;
        float zero = (float) yOf.applyAsDouble(0);
        // Zero line, dashed.
        for (float x = gx; x < gx + gw; x += 5) Shapes.round(g, x, zero - 0.25f, 2.5f, 0.5f, 0, 0x40FFFFFF);

        int n = hist.size();
        float prevY = Float.NaN;
        int cols = Math.round(gw);
        for (int i = 0; i <= cols; i++) {
            double f = i / (double) cols * (n - 1);
            int a = (int) Math.floor(f), b = Math.min(n - 1, a + 1);
            // Step-like ease between points so each payment reads as a jump.
            double k = f - a;
            double v = hist.get(a) + (hist.get(b) - hist.get(a)) * k;
            float y = (float) yOf.applyAsDouble(v);
            int col = v < 0 ? RED : GREEN;
            float x = gx + i;
            // Soft fill toward zero.
            float top = Math.min(y, zero), bot = Math.max(y, zero);
            if (bot - top > 0.5f) Shapes.round(g, x, top, 1, bot - top, 0, (0x22 << 24) | (col & 0xFFFFFF));
            float y0 = Float.isNaN(prevY) ? y : prevY;
            float t0 = Math.min(y0, y) - 0.6f, t1 = Math.max(y0, y) + 0.6f;
            Shapes.round(g, x - 0.5f, t0, 1.4f, t1 - t0, 0, col);
            prevY = y;
        }
        float ly = (float) yOf.applyAsDouble(hist.get(n - 1));
        Shapes.circle(g, gx + gw, ly, 2.2f, p < 0 ? RED : GREEN);
    }

    // ------------------------------------------------------------ auction

    private static final int AW = 170;

    public static int[] auctionSize() {
        int rows = Auction.active() ? Math.min(3, Math.max(1, Auction.ranking().size())) : 0;
        return new int[]{AW, Auction.active() ? 38 + rows * 11 + 4 : 26};
    }

    public static void drawAuction(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        int accent = 0xFF000000 | (t.accent() & 0xFFFFFF);
        Kit.shadow(g, 0, 0, w, h, 10, 0.8f);
        Shapes.round(g, 0, 0, w, h, 10, 0x9E0E101E);
        Shapes.ring(g, 0, 0, w, h, 10, 0.5f, 0x1AFFFFFF);
        Kit.textScaled(g, "AUCTION", 8, 6, 0.7f, DIM, true);
        if (!Auction.active()) {
            String r = Auction.result();
            Kit.text(g, Kit.fit(r != null ? r : "No auction running", w - 16, true), 8, 13, r != null ? GREEN : 0xFFC3C8DB, r != null, t.textShadow);
            return;
        }
        float left = Auction.secondsLeft();
        String time = left >= 60 ? (int) left / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", (int) left % 60) : String.format(java.util.Locale.ROOT, "%.1fs", left);
        int tc = left < 5 ? RED : 0xFFFFFFFF;
        Kit.textRight(g, time, w - 8, 5, tc, true);
        Kit.text(g, Kit.fit(Auction.item(), w - 16 - Kit.width(time, true) - 6, true), 8, 15, 0xFFFFD36B, true, t.textShadow);
        // Countdown bar.
        float bw = w - 16;
        Shapes.round(g, 8, 27, bw, 2.5f, 1.25f, 0x24FFFFFF);
        Shapes.round(g, 8, 27, Math.max(2.5f, bw * Auction.fraction()), 2.5f, 1.25f, left < 5 ? RED : accent);

        List<Auction.Bid> r = Auction.ranking();
        if (r.isEmpty()) {
            Kit.text(g, "No bids yet: pay to bid", 8, 36, DIM, false, t.textShadow);
            return;
        }
        for (int i = 0; i < Math.min(3, r.size()); i++) {
            Auction.Bid b = r.get(i);
            float y = 36 + i * 11;
            int col = i == 0 ? GREEN : 0xFFC3C8DB;
            Kit.text(g, (i + 1) + ". " + Kit.fit(b.player(), w - 80, i == 0), 8, y, col, i == 0, t.textShadow);
            Kit.textRight(g, "$" + Payments.money(b.total()), w - 8, y, col, i == 0);
        }
    }
}
