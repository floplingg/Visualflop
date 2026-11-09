package dev.tony.visuals.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Type a line of text (Enter or Done saves, Esc cancels). */
public class TextEditScreen extends Screen {
    private final Screen parent;
    private final String initial;
    private final Consumer<String> done;
    private EditBox box;

    public TextEditScreen(Screen parent, String title, String initial, Consumer<String> done) {
        super(Component.literal(title));
        this.parent = parent;
        this.initial = initial;
        this.done = done;
    }

    @Override
    protected void init() {
        int w = Math.min(280, width - 40);
        box = new EditBox(font, (width - w) / 2, height / 2 - 12, w, 20, getTitle());
        box.setMaxLength(160);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(width / 2 - 104, height / 2 + 16, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, height / 2 + 16, 100, 20).build());
    }

    private void save() {
        done.accept(box.getValue());
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Kit.refresh();
        Kit.textCenter(g, getTitle().getString(), width / 2f, height / 2f - 34, 0xFFFFFFFF, true);
        Kit.textCenter(g, "Tip: {fps} {coords} {ping} {clock} {biome} {speed} {direction} {cps} {day} show live values",
            width / 2f, height / 2f + 46, 0xFFA9B0C7, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
