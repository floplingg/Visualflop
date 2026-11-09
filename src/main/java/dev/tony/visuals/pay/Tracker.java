package dev.tony.visuals.pay;

import dev.tony.visuals.Config;

import java.util.List;

/**
 * Money tracker: every "Name paid you $X" adds to your total and every "You paid Name $X" takes away from it, while
 * tracking is on. Pause it when you aren't gambling so normal trades don't count. Saved, so it survives restarts.
 */
public final class Tracker {
    private Tracker() {}

    private static final int MAX_POINTS = 400;
    private static long lastSave;

    public static boolean on() {
        return Config.get().gamble.tracking;
    }

    public static double profit() {
        return Config.get().gamble.profit;
    }

    public static void add(double amount) {
        Config.Gamble g = Config.get().gamble;
        if (!g.tracking || amount == 0) return;
        if (g.history.isEmpty()) g.history.add(0.0);
        g.profit += amount;
        g.history.add(g.profit);
        while (g.history.size() > MAX_POINTS) g.history.remove(0);
        // Don't write the file on every payment in a fast burst.
        long now = System.currentTimeMillis();
        if (now - lastSave > 2000) {
            lastSave = now;
            Config.save();
        }
    }

    public static void reset() {
        Config.Gamble g = Config.get().gamble;
        g.profit = 0;
        g.history.clear();
        Config.save();
    }

    public static List<Double> history() {
        return Config.get().gamble.history;
    }

    /** "+(102.63M)" / "-(4.10M)". */
    public static String label(double v) {
        return (v < 0 ? "-" : "+") + "(" + compact(Math.abs(v)) + ")";
    }

    /** 102.63M, 1.25B, 950.00K, 12 */
    public static String compact(double v) {
        if (v >= 1e12) return String.format(java.util.Locale.ROOT, "%.2fT", v / 1e12);
        if (v >= 1e9) return String.format(java.util.Locale.ROOT, "%.2fB", v / 1e9);
        if (v >= 1e6) return String.format(java.util.Locale.ROOT, "%.2fM", v / 1e6);
        if (v >= 1e3) return String.format(java.util.Locale.ROOT, "%.2fK", v / 1e3);
        return String.format(java.util.Locale.ROOT, "%.0f", v);
    }
}
