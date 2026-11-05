package dev.tony.visuals.gui;

import dev.tony.visuals.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/** Base for settings menus: light dim so the HUD shows through as a live preview, plus sliders. */
public abstract class SettingsScreen extends FlopScreen {
    protected final List<Slider> sliders = new ArrayList<>();
    private Slider dragging;

    protected SettingsScreen(String title, Screen parent) {
        super(title, parent);
    }

    protected Slider slider(Slider s) {
        sliders.add(s);
        return s;
    }

    @Override
    protected final void layout() {
        sliders.clear();
        dragging = null;
        build();
    }

    protected abstract void build();

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        for (Slider s : sliders) s.render(g, font, mouseX, mouseY, s == dragging);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Slider s : sliders) {
                if (s.inside(event.x(), event.y())) {
                    dragging = s;
                    s.setFromMouse(event.x());
                    changed();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.setFromMouse(event.x());
            changed();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            Config.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (Slider s : sliders) {
            if (s.inside(mouseX, mouseY)) {
                s.nudge(vertical > 0 ? 1 : -1);
                changed();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    /** Called after any slider change. */
    protected void changed() {
        Config.get().theme.clamp();
    }

    @Override
    public void removed() {
        Config.save();
        super.removed();
    }
}
