package dev.tony.visuals.sky;

import dev.tony.visuals.Config;

/** The sky looks you can pick, and the colour each one is drawn in. */
public final class SkyStyles {
    private SkyStyles() {}

    /** Each look's own colour, used when the colour is set to "Signature". */
    public static int signature(Config.SkyStyle style) {
        return switch (style) {
            case TOPOGRAPHIC -> Config.theme().accent() & 0xFFFFFF;
            case GALAXY -> 0x8A4DFF;
            case AETHER -> 0xC9D0DC;
            case NOCTURNE -> 0x8462C4;
            case AMETHYST -> 0x7B45D6;
        };
    }

    public static int colour(Config.SkyStyle style, Config.Palette palette) {
        if (palette == null) palette = Config.Palette.SIGNATURE;
        return switch (palette) {
            case SIGNATURE -> signature(style);
            case ACCENT -> Config.theme().accent() & 0xFFFFFF;
            case VIOLET -> 0x8A4DFF;
            case AMBER -> 0xFFA235;
            case ICE -> 0x5FD3FF;
            case ROSE -> 0xFF6FB5;
            case EMERALD -> 0x3FE09A;
            case SILVER -> 0xC9D0DC;
            case CRIMSON -> 0xFF3B4E;
            case OCEAN -> 0x2F7BFF;
            case SUNSET -> 0xFF6A3D;
            case GOLD -> 0xFFD34D;
        };
    }

    /** Colour of the in-world sky right now. */
    public static int skyColour() {
        Config.Sky s = Config.get().sky;
        return colour(s.style, s.colour);
    }

    public static int skyStyle() {
        return Config.get().sky.style.ordinal();
    }
}
