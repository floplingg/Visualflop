package dev.tony.visuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

/** Clicks per second and movement speed. */
public final class Stats {
    private Stats() {}

    private static final ArrayDeque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
    private static boolean leftWas, rightWas;
    private static Vec3 lastPos;
    private static double speed;
    private static String biome = "";
    private static int ticks;

    /** Called every frame: catches click presses (a press = key going from up to down). */
    public static void frame(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean l = mc.screen == null && mc.options.keyAttack.isDown();
        boolean r = mc.screen == null && mc.options.keyUse.isDown();
        if (l && !leftWas) left.addLast(now);
        if (r && !rightWas) right.addLast(now);
        leftWas = l;
        rightWas = r;
        trim(left, now);
        trim(right, now);
    }

    private static void trim(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
    }

    public static int cps(boolean leftButton) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = leftButton ? left : right;
        trim(q, now);
        return q.size();
    }

    /** Called every tick: horizontal speed in blocks per second, lightly smoothed. */
    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            lastPos = null;
            return;
        }
        Vec3 pos = mc.player.position();
        if (lastPos != null) {
            double dx = pos.x - lastPos.x, dz = pos.z - lastPos.z;
            double now = Math.sqrt(dx * dx + dz * dz) * 20;
            speed = now > 100 ? speed : speed * 0.6 + now * 0.4;   // ignore teleports
        }
        lastPos = pos;

        // Biome only changes when you walk somewhere, so check twice a second.
        if (++ticks % 10 == 0 && mc.level != null) {
            biome = mc.level.getBiome(mc.player.blockPosition()).getRegisteredName();
        }
    }

    public static String biome() { return biome; }

    public static double speed() { return speed; }
}
