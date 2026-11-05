package dev.tony.visuals.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * One hi-res Flop Visuals particle. No block collisions (cheap), smooth fade in/out,
 * optional sway, spin, flicker and glow-in-the-dark.
 */
public class FlopParticle extends SingleQuadParticle {
    public enum Motion { FALL, RISE, FLOAT, DRIFT, BURST }

    private final Motion motion;
    private final float baseSize, baseAlpha, spin, phase;
    private final boolean glow, flicker, shrink;

    public FlopParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                        TextureAtlasSprite sprite, Motion motion, float size, int lifetime, int rgb, float alpha,
                        float spin, boolean glow, boolean flicker, boolean shrink) {
        super(level, x, y, z, sprite);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.motion = motion;
        this.baseSize = size;
        this.quadSize = size;
        this.lifetime = Math.max(2, lifetime);
        this.baseAlpha = alpha;
        this.spin = spin;
        this.glow = glow;
        this.flicker = flicker;
        this.shrink = shrink;
        this.phase = random.nextFloat() * 6.283f;
        this.hasPhysics = false;
        this.gravity = 0;
        this.roll = this.oRoll = random.nextFloat() * 6.283f;
        setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        setAlpha(0);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float life = age / (float) lifetime;
        switch (motion) {
            case FALL -> {
                yd = Math.max(yd - 0.0015, -0.06);
                xd += Mth.sin(age * 0.09f + phase) * 0.0025;
                zd += Mth.cos(age * 0.07f + phase) * 0.0025;
                xd *= 0.97;
                zd *= 0.97;
            }
            case RISE -> {
                yd = Math.min(yd + 0.0008, 0.06);
                xd *= 0.93;
                zd *= 0.93;
            }
            case FLOAT -> {
                xd += Mth.sin(age * 0.05f + phase) * 0.0012;
                yd += Mth.cos(age * 0.04f + phase * 1.3f) * 0.0010;
                zd += Mth.sin(age * 0.06f + phase * 0.7f) * 0.0012;
                xd *= 0.95;
                yd *= 0.95;
                zd *= 0.95;
            }
            case DRIFT -> {
                xd *= 0.92;
                yd *= 0.92;
                zd *= 0.92;
            }
            case BURST -> {
                yd -= 0.004;
                xd *= 0.9;
                yd *= 0.95;
                zd *= 0.9;
            }
        }
        x += xd;
        y += yd;
        z += zd;
        setPos(x, y, z);
        roll += spin;

        // Fade in quickly, fade out over the last 35%.
        float a = baseAlpha * Math.min(1f, age / 6f) * Math.min(1f, (1f - life) / 0.35f);
        if (flicker) a *= 0.55f + 0.45f * Mth.sin(age * 0.35f + phase);
        setAlpha(Math.max(0, a));
        quadSize = shrink ? baseSize * (1f - life * 0.7f) : baseSize;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return glow ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
