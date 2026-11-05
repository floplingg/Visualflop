package dev.tony.visuals.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/** Base for Flop Visuals menus: centred themed panel with custom-drawn buttons. */
public abstract class FlopScreen extends Screen {
    protected final Screen parent;
    protected final List<Ui.Button> buttons = new ArrayList<>();
    protected int px, py, pw, ph;

    protected FlopScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    @Override
    protected final void init() {
        buttons.clear();
        layout();
    }

    protected abstract void layout();

    protected String headerRight() { return ""; }

    protected void renderContent(GuiGraphics g, int mouseX, int mouseY) {}

    protected boolean clickContent(double mx, double my, int button) { return false; }

    protected void panel(int w, int h) {
        pw = w;
        ph = Math.min(h, height - 8);
        px = (width - w) / 2;
        py = Math.max(4, (height - ph) / 2);
    }

    protected Ui.Button add(Ui.Button b) {
        buttons.add(b);
        return b;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Ui.DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.applyTheme();
        Ui.panel(g, font, px, py, pw, ph, title.getString().toUpperCase(), headerRight());
        renderContent(g, mouseX, mouseY);
        for (Ui.Button b : buttons) b.render(g, font, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            for (Ui.Button b : List.copyOf(buttons)) {
                if (b.enabled && b.inside(event.x(), event.y())) {
                    playClick();
                    b.onClick.run();
                    return true;
                }
            }
        }
        return clickContent(event.x(), event.y(), event.button());
    }

    protected void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
