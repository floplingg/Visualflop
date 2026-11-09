package dev.tony.visuals.gui;

import dev.tony.visuals.Config;

/**
 * A value that glides to its target along a cubic-out curve (fast start, soft landing) over a fixed time.
 * Every hover, press, switch and slide in the menu uses one of these.
 */
public final class Ease {
    private final float seconds;
    private float from, to, value;
    private long start;
    private boolean set;

    public Ease(float seconds) {
        this.seconds = seconds;
    }

    public Ease(float seconds, float initial) {
        this(seconds);
        snap(initial);
    }

    public static float cubicOut(float t) {
        float u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    /** Animation length multiplier from the menu settings (0 = instant). */
    static float speed() {
        Config.Menu m = Config.get().menu;
        if (!m.animations) return 0;
        return 1.6f - m.animSpeed * 0.12f;   // 1 = slow ... 10 = quick
    }

    public void snap(float v) {
        from = to = value = v;
        set = true;
    }

    /** Moves towards {@code target} and returns the current value. */
    public float to(float target) {
        long now = System.nanoTime();
        if (!set) snap(target);
        if (target != to) {
            from = value;
            to = target;
            start = now;
        }
        float len = seconds * speed();
        if (len <= 0) return value = to;
        float t = (now - start) / 1e9f / len;
        value = t >= 1 ? to : from + (to - from) * cubicOut(t);
        return value;
    }

    public float value() {
        return value;
    }
}
