package dev.tony.visuals.fx;

import dev.tony.visuals.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** Ambient petals (or fireflies, snow...) drifting in a bubble around you only. */
public final class Petals {
    private Petals() {}

    private static final RandomSource RANDOM = RandomSource.create();

    public static void tick(Minecraft mc) {
        Config.Petals cfg = Config.get().petals;
        Player p = mc.player;
        ClientLevel level = mc.level;
        if (!cfg.enabled || p == null || level == null || mc.isPaused()) return;

        boolean rising = cfg.type == Config.PetalType.END_RODS || cfg.type == Config.PetalType.FIREFLIES;
        // Density 1-10 = 0.5 to 5 petals per tick on average.
        float want = cfg.density * 0.5f;
        int count = (int) want + (RANDOM.nextFloat() < want - (int) want ? 1 : 0);
        for (int i = 0; i < count; i++) {
            // Even spread across a disc around you.
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(RANDOM.nextDouble()) * cfg.radius;
            double x = p.getX() + Math.cos(a) * d;
            double z = p.getZ() + Math.sin(a) * d;
            double y = rising ? p.getY() + RANDOM.nextDouble() * 2.5 : p.getY() + 2 + RANDOM.nextDouble() * Math.min(5, cfg.radius);

            BlockPos at = BlockPos.containing(x, y, z);
            if (!level.getBlockState(at).isAir()) continue;
            if (!cfg.indoors && !level.canSeeSky(at)) continue;

            ParticleOptions o;
            double vx = (RANDOM.nextDouble() - 0.5) * 0.02, vy = -0.02, vz = (RANDOM.nextDouble() - 0.5) * 0.02;
            switch (cfg.type) {
                case PALE_OAK -> o = ParticleTypes.PALE_OAK_LEAVES;
                case SPORE_BLOSSOM -> o = ParticleTypes.SPORE_BLOSSOM_AIR;
                case FIREFLIES -> { o = ParticleTypes.FIREFLY; vy = 0; }
                case SNOWFLAKES -> o = ParticleTypes.SNOWFLAKE;
                case ASH -> o = ParticleTypes.WHITE_ASH;
                case CRIMSON -> o = ParticleTypes.CRIMSON_SPORE;
                case WARPED -> o = ParticleTypes.WARPED_SPORE;
                case END_RODS -> { o = ParticleTypes.END_ROD; vy = 0.01 + RANDOM.nextDouble() * 0.01; }
                default -> o = ParticleTypes.CHERRY_LEAVES;
            }
            level.addParticle(o, x, y, z, vx, vy, vz);
        }
    }
}
