package dev.tony.visuals.glass;

import dev.tony.visuals.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** Short and tall grass poke through the glass and look out of place, so they can be hidden. */
public final class GrassHider {
    private GrassHider() {}

    private static Set<Block> blocks;

    public static boolean active() {
        Config c = Config.get();
        return c != null && c.glass != null && c.glass.enabled && c.glass.hideGrass;
    }

    public static boolean hides(BlockState state) {
        if (blocks == null) {
            blocks = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
                Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS, Blocks.BUSH, Blocks.DEAD_BUSH);
        }
        return blocks.contains(state.getBlock());
    }

    /** Redraw the world after the setting changes. */
    public static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
    }
}
