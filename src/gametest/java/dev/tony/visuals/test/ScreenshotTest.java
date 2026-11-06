package dev.tony.visuals.test;

import dev.tony.visuals.Config;
import dev.tony.visuals.gui.ColourSheet;
import dev.tony.visuals.gui.MainScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Screenshots of the wallpaper, every menu page and (if the world loads in time) the sky and glass ground. */
public class ScreenshotTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitTicks(40);
        context.takeScreenshot("01-title-wallpaper");

        String[] names = {"appearance", "hud", "keystrokes", "trails", "ambience", "sky", "glass", "wallpaper", "spotify"};
        for (int i = 0; i < names.length; i++) {
            int page = i;
            context.setScreen(() -> new MainScreen(null, page));
            context.waitTicks(12);
            context.takeScreenshot(String.format("%02d-menu-%s", i + 2, names[i]));
        }
        Config.get().theme.lightMenus = true;
        context.setScreen(() -> new MainScreen(null, 0));
        context.waitTicks(12);
        context.takeScreenshot("11-menu-light");
        Config.get().theme.lightMenus = false;
        context.setScreen(() -> new ColourSheet(null, "Accent colour", () -> Config.get().theme.accentColor, v -> Config.get().theme.accentColor = v));
        context.waitTicks(12);
        context.takeScreenshot("12-colour-picker");
        context.setScreen(() -> null);

        Config.get().trails.onlyWhenMoving = false;
        Config.get().trails.density = 6;
        Config.get().petals.density = 10;
        Config.get().petals.indoors = true;
        context.runOnClient(mc -> {
            mc.options.renderDistance().set(2);
            mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(10);
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(mc -> mc.player.setXRot(-25f));
            context.waitTicks(60);
            context.takeScreenshot("20-sky");
            context.runOnClient(mc -> mc.player.setXRot(50f));
            context.waitTicks(20);
            context.takeScreenshot("21-glass-ground");
        }
    }
}
