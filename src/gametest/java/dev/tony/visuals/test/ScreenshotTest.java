package dev.tony.visuals.test;

import dev.tony.visuals.Config;
import dev.tony.visuals.gui.MainScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Shows the chat box (for the screenshot; the real chat screen needs a world). */
class ChatViewScreen extends net.minecraft.client.gui.screens.Screen {
    ChatViewScreen() {
        super(net.minecraft.network.chat.Component.literal("chat"));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.gui.getChat().render(g, font, mc.gui.getGuiTicks(), mx, my, true, false);
    }
}

/** A plain screen that shows the Spotify card, for the screenshot. */
class CardScreen extends net.minecraft.client.gui.screens.Screen {
    CardScreen() {
        super(net.minecraft.network.chat.Component.literal("card"));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int[] size = dev.tony.visuals.hud.SpotifyWidget.size();
        g.pose().pushMatrix();
        g.pose().translate(20, 20);
        g.pose().scale(2, 2);
        dev.tony.visuals.hud.SpotifyWidget.draw(g, size[0], size[1]);
        g.pose().popMatrix();
    }
}

/** Money gained/lost, the money graph and the auction card, for the screenshot. */
class MoneyScreen extends net.minecraft.client.gui.screens.Screen {
    MoneyScreen() {
        super(net.minecraft.network.chat.Component.literal("money"));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.pose().pushMatrix();
        g.pose().translate(20, 20);
        g.pose().scale(2, 2);
        int[] a = dev.tony.visuals.hud.MoneyWidgets.profitSize();
        dev.tony.visuals.hud.MoneyWidgets.drawProfit(g, a[0], a[1]);
        g.pose().translate(0, a[1] + 6);
        int[] b = dev.tony.visuals.hud.MoneyWidgets.graphSize();
        dev.tony.visuals.hud.MoneyWidgets.drawGraph(g, b[0], b[1]);
        g.pose().translate(b[0] + 8, 0);
        int[] c = dev.tony.visuals.hud.MoneyWidgets.auctionSize();
        dev.tony.visuals.hud.MoneyWidgets.drawAuction(g, c[0], c[1]);
        g.pose().popMatrix();
    }
}

/** Screenshots of the wallpapers, the menu (with a dropdown open) and, in a world, every sky and the glass ground. */
public class ScreenshotTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitTicks(40);
        if (Boolean.getBoolean("flop.worldOnly")) { world(context, new String[]{"topographic", "galaxy", "aether", "nocturne", "amethyst"}); return; }
        String[] walls = {"topographic", "galaxy", "aether", "nocturne", "amethyst"};
        for (int i = 0; i < walls.length; i++) {
            int k = i;
            context.runOnClient(mc -> Config.get().wallpaper.mode = Config.WallpaperMode.values()[k]);
            context.waitTicks(6);
            context.takeScreenshot("01-wallpaper-" + walls[i]);
        }

        String[] names = {"appearance", "hud", "keystrokes", "trails", "ambience", "orbs-halo", "sky", "glass", "wallpaper", "spotify"};
        for (int i = 0; i < names.length; i++) {
            int page = i;
            context.setScreen(() -> new MainScreen(null, page));
            context.waitTicks(20);
            context.takeScreenshot(String.format("%02d-menu-%s", i + 2, names[i]));
        }
        context.setScreen(() -> new MainScreen(null, 5));
        context.waitTicks(20);
        // HUD page with a text label added.
        context.runOnClient(mc -> {
            dev.tony.visuals.Config.CustomText ct = new dev.tony.visuals.Config.CustomText();
            ct.text = "Hello {fps}";
            ct.colour = 0xFFA235;
            dev.tony.visuals.Config.get().texts.add(ct);
        });
        context.setScreen(() -> new MainScreen(null, 1));
        context.waitTicks(20);
        context.takeScreenshot("11-menu-hud-text");
        // Spotify card: a JPEG cover decoded by the mod, then a cover found through the iTunes backup.
        context.runOnClient(mc -> {
            try (var in = ScreenshotTest.class.getResourceAsStream("/test_cover.jpg")) {
                dev.tony.visuals.spotify.Spotify.testInject("Test Song", "Test Artist", in.readAllBytes());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        context.setScreen(CardScreen::new);
        context.waitTicks(40);
        context.takeScreenshot("12-spotify-card-jpeg");
        context.runOnClient(mc -> dev.tony.visuals.spotify.Spotify.testInject("Blinding Lights", "The Weeknd", null));
        context.waitTicks(160);
        context.takeScreenshot("13-spotify-card-itunes");
        // Gamble: payments in chat.
        context.runOnClient(mc -> {
            dev.tony.visuals.pay.Tracker.start();
            String[] msgs = {"Steve paid you $5M.", "Steve paid you $5M.", "Steve paid you $5M.", "Alex paid you $2.1M.",
                "Bob paid you $500K.", "Kim paid you $3.2M.", "Lee paid you $1,500,000.",
                "G4PP13 paid you $ 1.1M", "G4PP13 paid you $ 1.2M", "G4PP13 paid you $ 1M", "G4PP13 paid you $ 1M", "You paid Shade2511 $ 10.2M", "Max paid you $ 40M",
                "You paid Max $ 25M"};
            for (String m : msgs) {
                boolean took = dev.tony.visuals.pay.Payments.handle(net.minecraft.network.chat.Component.literal(m));
                org.slf4j.LoggerFactory.getLogger("flopvisuals-test").info("payment '{}' handled={}", m, took);
                if (!took) mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(m));
            }
            dev.tony.visuals.pay.Payments.click(net.minecraft.resources.Identifier.fromNamespaceAndPath("flopvisuals", "pay/3/win"));
            dev.tony.visuals.pay.Payments.click(net.minecraft.resources.Identifier.fromNamespaceAndPath("flopvisuals", "pay/2/lose"));
            for (var line : ((dev.tony.visuals.mixin.ChatComponentAccessor) mc.gui.getChat()).flopvisuals$allMessages()) {
                org.slf4j.LoggerFactory.getLogger("flopvisuals-test").info("chat: {}", line.content().getString());
            }
        });
        context.setScreen(ChatViewScreen::new);
        context.waitTicks(10);
        context.takeScreenshot("14-gamble-chat");
        context.runOnClient(mc -> dev.tony.visuals.pay.Payments.click(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("flopvisuals", "pay/1/win")));
        context.waitTicks(20);
        context.takeScreenshot("15-gamble-wins-picker");
        context.setScreen(() -> null);
        context.runOnClient(mc -> org.slf4j.LoggerFactory.getLogger("flopvisuals-test").info("profit {} history {}",
            dev.tony.visuals.pay.Tracker.label(dev.tony.visuals.pay.Tracker.profit()), dev.tony.visuals.pay.Tracker.history()));
        // Auction: three bidders, one pays twice.
        context.runOnClient(mc -> {
            Config.get().auction.seconds = 5;
            dev.tony.visuals.pay.Auction.start("Netherite Ingot");
            for (String m : new String[]{"Steve paid you $ 2M", "G4PP13 paid you $ 3.5M", "Steve paid you $ 2.5M", "Alex paid you $ 1M"}) {
                dev.tony.visuals.pay.Payments.handle(net.minecraft.network.chat.Component.literal(m));
            }
        });
        context.setScreen(MoneyScreen::new);
        context.waitTicks(10);
        context.takeScreenshot("16-money-and-auction");
        context.waitTicks(120);
        context.runOnClient(mc -> {
            for (var line : ((dev.tony.visuals.mixin.ChatComponentAccessor) mc.gui.getChat()).flopvisuals$allMessages()) {
                org.slf4j.LoggerFactory.getLogger("flopvisuals-test").info("chat: {}", line.content().getString());
            }
        });
        context.takeScreenshot("17-auction-finished");
        context.setScreen(ChatViewScreen::new);
        context.waitTicks(10);
        context.takeScreenshot("18-auction-chat");
        context.setScreen(() -> new MainScreen(null, 10));
        context.waitTicks(20);
        context.takeScreenshot("19-menu-gamble");
        context.setScreen(() -> new MainScreen(null, 11));
        context.waitTicks(20);
        context.takeScreenshot("19-menu-auction");
        context.setScreen(() -> null);
        // The animated wallpaper is slow on CI's software renderer and also shows behind the loading screen.
        Config.get().wallpaper.mode = Config.WallpaperMode.VANILLA;

        world(context, walls);
    }

    private void world(ClientGameTestContext context, String[] walls) {
        Config.get().wallpaper.mode = Config.WallpaperMode.VANILLA;
        Config.get().trails.enabled = false;
        Config.get().petals.enabled = false;
        context.runOnClient(mc -> {
            mc.options.renderDistance().set(2);
            mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(260);
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        });
        boolean sky = Config.get().sky.enabled, glass = Config.get().glass.enabled;
        Config.get().sky.enabled = false;
        Config.get().glass.enabled = false;
        try (TestSingleplayerContext world = context.worldBuilder()
                .create()) {
            world.getClientWorld().waitForChunksRender();
            Config.get().sky.enabled = sky;
            Config.get().glass.enabled = glass;
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runCommand("execute as @p at @s run fill ~-2 ~ ~4 ~2 ~4 ~4 minecraft:quartz_block");
            world.getServer().runCommand("execute as @p at @s run setblock ~3 ~2 ~2 minecraft:gold_block");
            world.getServer().runCommand("execute as @p at @s run summon minecraft:villager ~-1 ~ ~3 {NoAI:1b,Rotation:[180f,0f]}");
            context.runOnClient(mc -> {
                mc.player.setYRot(0f);
                mc.player.setXRot(-25f);
            });
            for (int i = 0; i < walls.length; i++) {
                int k = i;
                context.runOnClient(mc -> Config.get().sky.style = Config.SkyStyle.values()[k]);
                context.waitTicks(10);
                context.takeScreenshot("20-sky-" + walls[i]);
            }
            context.runOnClient(mc -> {
                Config.get().sky.style = Config.SkyStyle.TOPOGRAPHIC;
                mc.player.setXRot(28f);
            });
            context.waitTicks(10);
            context.takeScreenshot("30-glass-first-person");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            context.takeScreenshot("31-glass-third-person");
            context.runOnClient(mc -> Config.get().sky.style = Config.SkyStyle.GALAXY);
            context.waitTicks(10);
            context.takeScreenshot("32-glass-galaxy");
        }
    }
}
