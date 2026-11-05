package dev.tony.visuals.fx;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Client-side particles behind you while you move. Only you see them. */
public final class Trails {
    private Trails() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static Vec3 last;
    private static ItemParticleOption feather;

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
        // Fill the gap between last and this tick so fast movement still makes a solid line.
        for (int i = 0; i < cfg.density; i++) {
            double t = RANDOM.nextDouble();
            double x = pos.x - motion.x * (0.6 + t) + gauss(spread);
            double y = pos.y - motion.y * (0.6 + t) + baseY + gauss(spread * 0.6);
            double z = pos.z - motion.z * (0.6 + t) + gauss(spread);
            spawn(level, cfg.type, x, y, z, spread);
        }
    }

    private static void spawn(ClientLevel level, Config.TrailType type, double x, double y, double z, double spread) {
        double vx = gauss(0.01), vy = 0.01 + RANDOM.nextDouble() * 0.02, vz = gauss(0.01);
        ParticleOptions o;
        switch (type) {
            case STARDUST -> { o = ParticleTypes.END_ROD; vy = RANDOM.nextDouble() * 0.015; vx *= 0.5; vz *= 0.5; }
            case ACCENT_DUST -> { o = new DustParticleOptions(Config.theme().accent(), 0.9f + RANDOM.nextFloat() * 0.5f); }
            case FEATHERS -> {
                if (feather == null) feather = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.FEATHER));
                o = feather;
                vy = 0.03 + RANDOM.nextDouble() * 0.04;
            }
            case SPARKS -> { o = ParticleTypes.ELECTRIC_SPARK; vx = gauss(0.06); vy = RANDOM.nextDouble() * 0.06; vz = gauss(0.06); }
            case FLAMES -> o = ParticleTypes.FLAME;
            case SOUL_FLAMES -> o = ParticleTypes.SOUL_FIRE_FLAME;
            case HEARTS -> { if (RANDOM.nextInt(3) != 0) return; o = ParticleTypes.HEART; }
            case ENCHANT -> { o = ParticleTypes.ENCHANT; vx = gauss(0.4); vy = RANDOM.nextDouble() * 0.4; vz = gauss(0.4); }
            case NOTES -> { if (RANDOM.nextInt(2) != 0) return; o = ParticleTypes.NOTE; vx = RANDOM.nextDouble(); vy = 0; vz = 0; }
            case SNOW -> o = ParticleTypes.SNOWFLAKE;
            case CHERRY -> o = ParticleTypes.CHERRY_LEAVES;
            case GLOW -> o = ParticleTypes.GLOW;
            case TOTEM -> { o = ParticleTypes.TOTEM_OF_UNDYING; vx = gauss(0.08); vy = RANDOM.nextDouble() * 0.12; vz = gauss(0.08); }
            case CLOUDS -> { o = ParticleTypes.CLOUD; vx *= 0.3; vy = 0.005; vz *= 0.3; }
            default -> o = ParticleTypes.END_ROD;
        }
        level.addParticle(o, x, y, z, vx, vy, vz);
    }

    private static double gauss(double s) {
        return RANDOM.nextGaussian() * s;
    }
}
