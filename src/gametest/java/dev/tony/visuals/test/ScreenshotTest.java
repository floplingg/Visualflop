package dev.tony.visuals.test;

import dev.tony.visuals.Config;
import dev.tony.visuals.gui.MainScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Opens a world and takes screenshots of the sky, HUD, particles and menu. */
public class ScreenshotTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Config.get().trails.onlyWhenMoving = false;
        Config.get().trails.density = 6;
        Config.get().petals.density = 10;
        Config.get().petals.indoors = true;
        Config.get().widget("speed").enabled = true;
        Config.get().widget("clock").enabled = true;

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(mc -> {
                mc.player.setXRot(-25f);
                mc.player.setYRot(30f);
            });
            context.waitTicks(80);
            context.takeScreenshot("1-sky");

            context.runOnClient(mc -> mc.player.setXRot(55f));
            context.waitTicks(20);
            context.takeScreenshot("2-ground-particles");

            context.runOnClient(mc -> mc.player.setXRot(-80f));
            context.waitTicks(20);
            context.takeScreenshot("3-sky-up");

            Config.get().petals.type = Config.PetalType.FIREFLIES;
            Config.get().trails.type = Config.TrailType.FEATHERS;
            context.runOnClient(mc -> mc.player.setXRot(20f));
            context.waitTicks(60);
            context.takeScreenshot("4-fireflies-feathers");

            context.setScreen(() -> new MainScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("5-menu");
        }
    }
}
