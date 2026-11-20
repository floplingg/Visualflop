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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;


public class FlopVisuals implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("flopvisuals", "main"));
    /** Shows up in Options > Controls > Key Binds under "Flop Visuals". */
    public static KeyMapping menuKey, playPauseKey, nextKey, previousKey, ytPauseKey, ytFullscreenKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        menuKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.flopvisuals.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY));
        playPauseKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.flopvisuals.playpause", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
        nextKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.flopvisuals.next", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
        previousKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.flopvisuals.previous", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
        ytPauseKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.flopvisuals.ytpause", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
        ytFullscreenKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.flopvisuals.ytfullscreen", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
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
        // 26.3: world effects are submitted with the rest of the world's geometry each frame.
        // The sky sits at the far plane, so it only fills pixels nothing else covered.
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            TopoSky.render(ctx);
            dev.tony.visuals.fx.AuraFx.render(ctx);
            GlassGround.render(ctx);
            dev.tony.visuals.fx.SkyLyrics.render(ctx);
        });
        // Frame copies: for the glass reflections (after solid blocks and mobs) and the HUD blur (whole world).
        // 26.3 draws the whole world in one render pass, and textures can't be copied while it's open, so the glass
        // reflects the previous frame (copied once the pass is closed). One frame behind is invisible in practice.
        LevelRenderEvents.END_MAIN.register(ctx -> {
            GlassGround.capture();
            dev.tony.visuals.hud.Backdrop.capture();
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addLast(Identifier.parse("flopvisuals:hud"), Hud::render);
    }

    private void onTick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new MainScreen(null));
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
        dev.tony.visuals.youtube.YouTube.tick();

        Stats.tick(mc);
        Trails.tick(mc);
        Petals.tick(mc);
    }
}
