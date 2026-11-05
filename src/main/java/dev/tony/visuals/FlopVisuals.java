package dev.tony.visuals;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tony.visuals.fx.Petals;
import dev.tony.visuals.fx.Trails;
import dev.tony.visuals.gui.MainScreen;
import dev.tony.visuals.hud.Hud;
import dev.tony.visuals.hud.Stats;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class FlopVisuals implements ClientModInitializer {
    private boolean menuKeyWasDown;

    @Override
    public void onInitializeClient() {
        Config.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addLast(Identifier.parse("flopvisuals:hud"), Hud::render);
    }

    private void onTick(Minecraft mc) {
        int key = Config.get().menuKey;
        boolean down = key > GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(mc.getWindow(), key);
        if (down && !menuKeyWasDown && mc.screen == null && mc.player != null) mc.setScreen(new MainScreen(null));
        menuKeyWasDown = down;

        Stats.tick(mc);
        Trails.tick(mc);
        Petals.tick(mc);
    }
}
