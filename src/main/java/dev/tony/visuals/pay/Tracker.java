package dev.tony.visuals.pay;

import dev.tony.visuals.Config;

import java.util.List;

/**
 * Money tracker, in gambling sessions: Start a session when you begin gambling and End it when you stop. During a
 * session every "Name paid you $X" adds and every "You paid Name $X" takes away. Outside a session nothing counts, so
 * normal trades stay out of it. Finished sessions are kept with their totals. Saved, so it survives restarts.
 */
public final class Tracker {
    private Tracker() {}

    private static final int MAX_POINTS = 400, MAX_SESSIONS = 100;
    private static long lastSave;

    private static Config.Gamble cfg() {
        return Config.get().gamble;
    }

    /** True while a session is running. */
    public static boolean on() {
        return cfg().current != null;
    }

    public static Config.Session current() {
        return cfg().current;
    }

    /** The running session, or the last finished one, or null. */
    public static Config.Session shown() {
        Config.Gamble g = cfg();
        if (g.current != null) return g.current;
        return g.sessions.isEmpty() ? null : g.sessions.get(g.sessions.size() - 1);
    }

    public static double profit() {
        Config.Session s = shown();
        return s == null ? 0 : s.profit;
    }

    public static List<Double> history() {
        Config.Session s = shown();
        return s == null ? List.of() : s.history;
    }

    public static void start() {
        Config.Gamble g = cfg();
        if (g.current != null) return;
        g.current = new Config.Session();
        g.current.history.add(0.0);
        Config.save();
    }

    public static void end() {
        Config.Gamble g = cfg();
        if (g.current == null) return;
        g.current.end = System.currentTimeMillis();
        g.sessions.add(g.current);
        while (g.sessions.size() > MAX_SESSIONS) g.sessions.remove(0);
        g.current = null;
        Config.save();
    }

    /** Every finished session plus the running one. */
    public static double allTime() {
        double t = 0;
        for (Config.Session s : cfg().sessions) t += s.profit;
        if (cfg().current != null) t += cfg().current.profit;
        return t;
    }

    public static void add(double amount) {
        Config.Session s = cfg().current;
        if (s == null || amount == 0) return;
        if (s.history.isEmpty()) s.history.add(0.0);
        s.profit += amount;
        s.history.add(s.profit);
        while (s.history.size() > MAX_POINTS) s.history.remove(0);
        // Don't write the file on every payment in a fast burst.
        long now = System.currentTimeMillis();
        if (now - lastSave > 2000) {
            lastSave = now;
            Config.save();
        }
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

    /** "1h 12m" / "8m" / "40s" */
    public static String duration(Config.Session s) {
        long ms = (s.end > 0 ? s.end : System.currentTimeMillis()) - s.start;
        long sec = Math.max(0, ms / 1000);
        if (sec >= 3600) return sec / 3600 + "h " + (sec % 3600) / 60 + "m";
        if (sec >= 60) return sec / 60 + "m";
        return sec + "s";
    }

    /** "Nov 8, 9:41 PM" */
    public static String when(Config.Session s) {
        return new java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.ENGLISH).format(new java.util.Date(s.start));
    }
}
