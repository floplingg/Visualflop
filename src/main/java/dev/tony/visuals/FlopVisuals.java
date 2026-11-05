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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class FlopVisuals implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("flopvisuals", "main"));
    /** Shows up in Options > Controls > Key Binds under "Flop Visuals". */
    public static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        menuKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.flopvisuals.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addLast(Identifier.parse("flopvisuals:hud"), Hud::render);
    }

    private void onTick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new MainScreen(null));
        }

        Stats.tick(mc);
        Trails.tick(mc);
        Petals.tick(mc);
    }
}
