package dev.tony.visuals;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tony.visuals.fx.Petals;
import dev.tony.visuals.fx.Trails;
import dev.tony.visuals.gui.MainScreen;
import dev.tony.visuals.hud.Hud;
import dev.tony.visuals.hud.Stats;
import dev.tony.visuals.sky.TopoSky;
import dev.tony.visuals.glass.GlassGround;
import dev.tony.visuals.spotify.Spotify;
import dev.tony.visuals.wallpaper.Wallpapers;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class FlopVisuals implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("flopvisuals", "main"));
    /** Shows up in Options > Controls > Key Binds under "Flop Visuals". */
    public static KeyMapping menuKey, playPauseKey, nextKey, previousKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        menuKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));
        playPauseKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.playpause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        nextKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        previousKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.previous", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        dev.tony.visuals.hud.SpotifyWidget.registerClicks();
        TopoSky.init();
        GlassGround.init();
        dev.tony.visuals.fx.AuraFx.init();
        Wallpapers.init();
        Spotify.start();
        // The sky goes in before entities (and at the far plane), so players, mobs and nametags always draw over it.
        WorldRenderEvents.BEFORE_ENTITIES.register(TopoSky::render);
        WorldRenderEvents.AFTER_ENTITIES.register(dev.tony.visuals.fx.AuraFx::render);   // before the glass, so it shows in the reflection
        WorldRenderEvents.AFTER_ENTITIES.register(GlassGround::render);
        WorldRenderEvents.END_MAIN.register(GlassGround::renderWater);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addLast(Identifier.parse("flopvisuals:hud"), Hud::render);
    }

    private void onTick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new MainScreen(null));
        }

        while (playPauseKey.consumeClick()) dev.tony.visuals.spotify.Spotify.control("toggle");
        while (nextKey.consumeClick()) dev.tony.visuals.spotify.Spotify.control("next");
        while (previousKey.consumeClick()) dev.tony.visuals.spotify.Spotify.control("previous");

        Stats.tick(mc);
        Trails.tick(mc);
        Petals.tick(mc);
    }
}
