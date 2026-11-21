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
    public static KeyMapping menuKey, playPauseKey, nextKey, previousKey, ytPauseKey, ytFullscreenKey,
        streamerModeKey, brbKey, markerKey, liveKey;

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
        ytPauseKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.ytpause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        ytFullscreenKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.ytfullscreen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        streamerModeKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.streamermode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        brbKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.brb", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        markerKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.marker", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        liveKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.live", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        dev.tony.visuals.stream.Streamer.init();
        dev.tony.visuals.hud.SpotifyWidget.registerClicks();
        dev.tony.visuals.hud.YouTubeWidget.registerClicks();
        dev.tony.visuals.pay.Payments.init();
        TopoSky.init();
        GlassGround.init();
        dev.tony.visuals.fx.AuraFx.init();
        dev.tony.visuals.fx.SkyLyrics.init();
        dev.tony.visuals.hud.Backdrop.init();
        Wallpapers.init();
        Spotify.start();
        // The sky goes in before entities (and at the far plane), so players, mobs and nametags always draw over it.
        WorldRenderEvents.BEFORE_ENTITIES.register(TopoSky::render);
        WorldRenderEvents.AFTER_ENTITIES.register(dev.tony.visuals.fx.AuraFx::render);   // before the glass, so it shows in the reflection
        WorldRenderEvents.AFTER_ENTITIES.register(GlassGround::render);
        WorldRenderEvents.END_MAIN.register(GlassGround::renderWater);
        WorldRenderEvents.AFTER_ENTITIES.register(dev.tony.visuals.fx.SkyLyrics::render);
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
        while (ytPauseKey.consumeClick()) {
            dev.tony.visuals.youtube.YouTube.togglePause();
            dev.tony.visuals.hud.YouTubeWidget.poke();
        }
        while (ytFullscreenKey.consumeClick()) {
            dev.tony.visuals.youtube.YouTube.toggleFullscreen();
            dev.tony.visuals.hud.YouTubeWidget.poke();
        }
        while (streamerModeKey.consumeClick()) dev.tony.visuals.stream.Streamer.togglePrivacy();
        while (brbKey.consumeClick()) dev.tony.visuals.stream.Streamer.toggleBrb();
        while (markerKey.consumeClick()) dev.tony.visuals.stream.Streamer.marker();
        while (liveKey.consumeClick()) dev.tony.visuals.stream.Streamer.toggleLive();
        dev.tony.visuals.youtube.YouTube.tick();

        Stats.tick(mc);
        Trails.tick(mc);
        Petals.tick(mc);
    }
}
