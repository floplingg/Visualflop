package dev.tony.visuals.fx;

import dev.tony.visuals.Config;
import dev.tony.visuals.fx.FlopParticle.Motion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** Hi-res ambient petals (or fireflies, snow...) in a bubble around you only. */
public final class Petals {
    private Petals() {}

    private static final RandomSource RANDOM = RandomSource.create();

    public static void tick(Minecraft mc) {
        Config.Petals cfg = Config.get().petals;
        Player p = mc.player;
        ClientLevel level = mc.level;
        if (!cfg.enabled || p == null || level == null || mc.isPaused()) return;

        boolean floating = switch (cfg.type) {
            case FIREFLIES, SPORE_BLOSSOM, CRIMSON, WARPED, END_RODS -> true;
            default -> false;
        };
        // Density 1-10 = 0.5 to 5 per tick on average.
        float want = cfg.density * 0.5f;
        int count = (int) want + (RANDOM.nextFloat() < want - (int) want ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(RANDOM.nextDouble()) * cfg.radius;
            double x = p.getX() + Math.cos(a) * d;
            double z = p.getZ() + Math.sin(a) * d;
            double y = floating ? p.getY() + RANDOM.nextDouble() * 2.8 : p.getY() + 2.5 + RANDOM.nextDouble() * Math.min(5, cfg.radius);

            BlockPos at = BlockPos.containing(x, y, z);
            if (!level.getBlockState(at).isAir()) continue;
            if (!cfg.indoors && !level.canSeeSky(at)) continue;

            float s = 0.75f + RANDOM.nextFloat() * 0.5f;
            int life = 90 + RANDOM.nextInt(80);
            float sway = (float) RANDOM.nextGaussian() * 0.07f;
            FlopParticle fp = switch (cfg.type) {
                case CHERRY -> new FlopParticle(level, x, y, z, 0, -0.01, 0, Sprites.get("cherry_petal"), Motion.FALL, 0.14f * s, life, 0xFFFFFF, 1f, sway, false, false, false);
                case PALE_OAK -> new FlopParticle(level, x, y, z, 0, -0.01, 0, Sprites.get("pale_petal"), Motion.FALL, 0.14f * s, life, 0xFFFFFF, 1f, sway, false, false, false);
                case SNOWFLAKES -> new FlopParticle(level, x, y, z, 0, -0.01, 0, Sprites.get("snowflake"), Motion.FALL, 0.10f * s, life, 0xFFFFFF, 0.95f, sway * 0.5f, false, false, false);
                case ASH -> new FlopParticle(level, x, y, z, 0, -0.005, 0, Sprites.get("ash"), Motion.FALL, 0.06f * s, life, 0xB8B8BE, 0.9f, sway, false, false, false);
                case SPORE_BLOSSOM -> new FlopParticle(level, x, y, z, 0, 0, 0, Sprites.get("glow"), Motion.FLOAT, 0.06f * s, life, RANDOM.nextBoolean() ? 0xF7A8D8 : 0xA8F0B0, 0.85f, 0, true, false, false);
                case FIREFLIES -> new FlopParticle(level, x, y, z, 0, 0, 0, Sprites.get("glow"), Motion.FLOAT, 0.09f * s, life, 0xE8FF6A, 1f, 0, true, true, false);
                case CRIMSON -> new FlopParticle(level, x, y, z, 0, 0, 0, Sprites.get("glow"), Motion.FLOAT, 0.06f * s, life, 0xFF4B5C, 0.9f, 0, true, false, false);
                case WARPED -> new FlopParticle(level, x, y, z, 0, 0, 0, Sprites.get("glow"), Motion.FLOAT, 0.06f * s, life, 0x4BE3D2, 0.9f, 0, true, false, false);
                case END_RODS -> new FlopParticle(level, x, y, z, 0, 0.01, 0, Sprites.get("sparkle"), Motion.RISE, 0.10f * s, life, 0xFFFFFF, 1f, 0, true, true, false);
            };
            mc.particleEngine.add(fp);
        }
    }
}
