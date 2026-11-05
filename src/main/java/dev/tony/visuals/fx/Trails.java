package dev.tony.visuals.fx;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.fx.FlopParticle.Motion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Hi-res client-side particles behind you while you move. Only you see them. */
public final class Trails {
    private Trails() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static Vec3 last;

    public static void tick(Minecraft mc) {
        Config.Trails cfg = Config.get().trails;
        Player p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.isPaused()) {
            last = null;
            return;
        }
        Vec3 pos = p.position();
        Vec3 motion = last == null ? Vec3.ZERO : pos.subtract(last);
        last = pos;
        if (!cfg.enabled || p.isSpectator() || p.isInvisible()) return;

        boolean flying = p.isFallFlying();
        if (flying && !cfg.whileFlying) return;
        double moved = motion.horizontalDistance() + Math.abs(motion.y) * 0.5;
        if (cfg.onlyWhenMoving && moved < 0.02) return;
        if (moved > 10) return;   // teleport

        double spread = cfg.spread * 0.06;
        double baseY = flying || p.isSwimming() ? p.getBbHeight() * 0.4 : 0.15;
        for (int i = 0; i < cfg.density; i++) {
            double t = RANDOM.nextDouble();
            double x = pos.x - motion.x * (0.6 + t) + gauss(spread);
            double y = pos.y - motion.y * (0.6 + t) + baseY + gauss(spread * 0.6);
            double z = pos.z - motion.z * (0.6 + t) + gauss(spread);
            FlopParticle fp = make(level, cfg.type, x, y, z);
            if (fp != null) mc.particleEngine.add(fp);
        }
    }

    private static FlopParticle make(ClientLevel level, Config.TrailType type, double x, double y, double z) {
        float s = 0.7f + RANDOM.nextFloat() * 0.6f;
        double vx = gauss(0.01), vy = RANDOM.nextDouble() * 0.01, vz = gauss(0.01);
        return switch (type) {
            case STARDUST -> new FlopParticle(level, x, y, z, vx * 0.5, vy, vz * 0.5, Sprites.get("sparkle"), Motion.DRIFT,
                0.16f * s, 30 + RANDOM.nextInt(20), mixWhite(Config.theme().accent(), 0.7f), 1f, 0, true, true, true);
            case ACCENT_DUST -> new FlopParticle(level, x, y, z, vx, vy, vz, Sprites.get("glow"), Motion.DRIFT,
                0.13f * s, 25 + RANDOM.nextInt(15), Config.theme().accent(), 0.9f, 0, true, false, true);
            case FEATHERS -> new FlopParticle(level, x, y + 0.3, z, vx * 2, 0.03, vz * 2, Sprites.get("feather"), Motion.FALL,
                0.24f * s, 60 + RANDOM.nextInt(30), 0xFFFFFF, 1f, gaussF(0.05f), false, false, false);
            case SPARKS -> new FlopParticle(level, x, y, z, gauss(0.07), 0.04 + RANDOM.nextDouble() * 0.06, gauss(0.07), Sprites.get("sparkle"),
                Motion.BURST, 0.10f * s, 10 + RANDOM.nextInt(10), 0xFFD36B, 1f, 0, true, false, true);
            case FLAMES -> new FlopParticle(level, x, y, z, vx, 0.01, vz, Sprites.get("flame"), Motion.RISE,
                0.16f * s, 14 + RANDOM.nextInt(10), 0xFF8A2B, 0.95f, 0, true, false, true);
            case SOUL_FLAMES -> new FlopParticle(level, x, y, z, vx, 0.01, vz, Sprites.get("flame"), Motion.RISE,
                0.16f * s, 14 + RANDOM.nextInt(10), 0x5CE1FF, 0.95f, 0, true, false, true);
            case HEARTS -> RANDOM.nextInt(3) != 0 ? null : new FlopParticle(level, x, y + 0.2, z, vx, 0.02, vz, Sprites.get("heart"),
                Motion.RISE, 0.16f * s, 30 + RANDOM.nextInt(15), 0xFFFFFF, 1f, 0, false, false, false);
            case ENCHANT -> new FlopParticle(level, x, y, z, gauss(0.02), 0.02, gauss(0.02), Sprites.get("sparkle"), Motion.RISE,
                0.12f * s, 25 + RANDOM.nextInt(15), 0xB58CFF, 1f, 0, true, true, true);
            case NOTES -> RANDOM.nextInt(2) != 0 ? null : new FlopParticle(level, x, y + 0.2, z, vx, 0.02, vz, Sprites.get("note"),
                Motion.RISE, 0.16f * s, 30 + RANDOM.nextInt(10), Theme.hsv(RANDOM.nextFloat(), 0.6f, 1f), 1f, 0, true, false, false);
            case SNOW -> new FlopParticle(level, x, y + 0.2, z, vx, 0, vz, Sprites.get("snowflake"), Motion.FALL,
                0.12f * s, 50 + RANDOM.nextInt(30), 0xFFFFFF, 0.95f, gaussF(0.04f), false, false, false);
            case CHERRY -> new FlopParticle(level, x, y + 0.2, z, vx * 2, 0.01, vz * 2, Sprites.get("cherry_petal"), Motion.FALL,
                0.15f * s, 50 + RANDOM.nextInt(30), 0xFFFFFF, 1f, gaussF(0.08f), false, false, false);
            case GLOW -> new FlopParticle(level, x, y, z, vx, vy, vz, Sprites.get("glow"), Motion.FLOAT,
                0.14f * s, 35 + RANDOM.nextInt(20), 0x7BF5E0, 0.9f, 0, true, true, true);
            case TOTEM -> new FlopParticle(level, x, y, z, gauss(0.06), 0.03 + RANDOM.nextDouble() * 0.05, gauss(0.06), Sprites.get("sparkle"),
                Motion.BURST, 0.13f * s, 18 + RANDOM.nextInt(12), RANDOM.nextBoolean() ? 0xFFD94A : 0x8CF05A, 1f, 0, true, false, true);
            case CLOUDS -> new FlopParticle(level, x, y, z, vx * 0.3, 0.004, vz * 0.3, Sprites.get("puff"), Motion.DRIFT,
                0.30f * s, 30 + RANDOM.nextInt(20), 0xFFFFFF, 0.55f, gaussF(0.01f), false, false, false);
        };
    }

    static int mixWhite(int rgb, float k) {
        int r = Math.round(((rgb >> 16) & 255) * (1 - k) + 255 * k);
        int g = Math.round(((rgb >> 8) & 255) * (1 - k) + 255 * k);
        int b = Math.round((rgb & 255) * (1 - k) + 255 * k);
        return r << 16 | g << 8 | b;
    }

    private static double gauss(double s) {
        return RANDOM.nextGaussian() * s;
    }

    private static float gaussF(float s) {
        return (float) RANDOM.nextGaussian() * s;
    }
}
