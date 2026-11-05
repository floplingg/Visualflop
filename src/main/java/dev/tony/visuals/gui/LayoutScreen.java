package dev.tony.visuals.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tony.visuals.Config;
import dev.tony.visuals.hud.Hud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Drag any widget to move it, scroll over it to resize, right-click to hide it.
 * Snaps to screen edges, the centre lines and other widgets' edges; hold Shift to place freely.
 */
public class LayoutScreen extends Screen {
    private static final int SNAP = 5;

    private final Screen parent;
    private final Ui.Button reset, done;
    private String dragging, selected;
    private double grabX, grabY;
    private int guideX = -1, guideY = -1;

    public LayoutScreen(Screen parent) {
        super(Component.literal("HUD layout"));
        this.parent = parent;
        reset = new Ui.Button(0, 0, 90, 18, () -> "Reset sizes", () -> {
            for (Config.Widget w : Config.get().widgets.values()) w.scale = 1f;
        });
        done = new Ui.Button(0, 0, 90, 18, () -> "§lDone", this::onClose).highlight(() -> true);
    }

    private static boolean shift() {
        var win = net.minecraft.client.Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private Hud.Placement placement(String id) {
        return Hud.place(font, id, Config.get().widget(id), width, height);
    }

    private String widgetAt(double mx, double my) {
        String found = null;
        for (String id : Config.WIDGETS) {
            if (Config.get().widget(id).enabled && placement(id).contains(mx, my)) found = id;
        }
        return found;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x48000000);
        int grid = (Ui.PURPLE & 0xFFFFFF) | 0x12000000;
        for (int x = 0; x < width; x += 20) g.fill(x, 0, x + 1, height, grid);
        for (int y = 0; y < height; y += 20) g.fill(0, y, width, y + 1, grid);
        int guide = (Ui.PURPLE & 0xFFFFFF) | 0xB0000000;
        if (guideX >= 0) g.fill(guideX, 0, guideX + 1, height, guide);
        if (guideY >= 0) g.fill(0, guideY, width, guideY + 1, guide);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.applyTheme();
        super.render(g, mouseX, mouseY, partialTick);
        String hover = dragging != null ? dragging : widgetAt(mouseX, mouseY);
        for (String id : Config.WIDGETS) {
            Config.Widget w = Config.get().widget(id);
            if (!w.enabled) continue;
            Hud.Placement p = Hud.draw(g, font, id, w, width, height);
            boolean lit = id.equals(hover) || id.equals(selected);
            int c = lit ? Ui.PURPLE : (Ui.PURPLE & 0xFFFFFF) | 0x50000000;
            Shapes.ring(g, p.x() - 1.5f, p.y() - 1.5f, p.w() + 3, p.h() + 3, Ui.RADIUS + 1, lit ? 0.75f : 0.5f, c);
            if (lit) {
                String label = Config.widgetName(id) + " · " + Math.round(w.scale * 100) + "%";
                int ly = p.y() > 12 ? p.y() - 11 : p.y() + p.h() + 3;
                int lw = font.width(label) + 8;
                int lx = Math.max(2, Math.min(width - lw - 2, p.x()));
                Ui.box(g, lx, ly, lw, 10, Ui.PANEL, Ui.PURPLE_DEEP);
                g.drawString(font, label, lx + 4, ly + 1, Ui.TEXT, false);
            }
        }

        String help = "Drag to move · Scroll to resize · Right-click to hide · Arrows nudge · Shift = no snap";
        int hw = font.width(help) + 16, hx = (width - hw) / 2, hy = height / 2 - 24;
        Ui.box(g, hx, hy, hw, 16, Ui.PANEL, Ui.PURPLE_DEEP);
        g.drawString(font, help, hx + 8, hy + 4, Ui.MUTED, false);
        reset.x = width / 2 - 94; reset.y = hy + 22;
        done.x = width / 2 + 4; done.y = hy + 22;
        reset.render(g, font, mouseX, mouseY);
        done.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0) {
            for (Ui.Button b : new Ui.Button[]{reset, done}) {
                if (b.inside(mx, my)) {
                    b.onClick.run();
                    Config.save();
                    return true;
                }
            }
        }
        String id = widgetAt(mx, my);
        if (id == null) {
            selected = null;
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == 1) {
            Config.get().widget(id).enabled = false;
            Config.save();
            return true;
        }
        if (event.button() == 0) {
            Hud.Placement p = placement(id);
            dragging = selected = id;
            grabX = mx - p.x();
            grabY = my - p.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        Hud.Placement p = placement(dragging);
        double nx = event.x() - grabX, ny = event.y() - grabY;
        guideX = guideY = -1;
        if (!shift()) {
            double[] sx = snap(nx, p.w(), width, true);
            double[] sy = snap(ny, p.h(), height, false);
            nx = sx[0];
            ny = sy[0];
            guideX = (int) sx[1];
            guideY = (int) sy[1];
        }
        setPos(dragging, p, nx, ny);
        return true;
    }

    /** {new position, guide line or -1}. Snaps to screen edges, centre and other widgets' edges. */
    private double[] snap(double pos, int size, int screen, boolean horizontal) {
        if (Math.abs(pos) < SNAP) return new double[]{0, -1};
        if (Math.abs(pos + size - screen) < SNAP) return new double[]{screen - size, -1};
        if (Math.abs(pos + size / 2.0 - screen / 2.0) < SNAP) return new double[]{screen / 2.0 - size / 2.0, screen / 2.0};
        for (String other : Config.WIDGETS) {
            if (other.equals(dragging) || !Config.get().widget(other).enabled) continue;
            Hud.Placement o = placement(other);
            int a = horizontal ? o.x() : o.y(), b = a + (horizontal ? o.w() : o.h());
            double[][] tries = {{pos, a}, {pos, b}, {pos + size, a}, {pos + size, b}};
            for (double[] t : tries) {
                if (Math.abs(t[0] - t[1]) < SNAP) return new double[]{pos + (t[1] - t[0]), t[1]};
            }
        }
        return new double[]{pos, -1};
    }

    private void setPos(String id, Hud.Placement p, double nx, double ny) {
        Config.Widget w = Config.get().widget(id);
        nx = Math.max(0, Math.min(width - p.w(), nx));
        ny = Math.max(0, Math.min(height - p.h(), ny));
        w.x = width <= p.w() ? 0 : (float) (nx / (width - p.w()));
        w.y = height <= p.h() ? 0 : (float) (ny / (height - p.h()));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) Config.save();
        dragging = null;
        guideX = guideY = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        String id = widgetAt(mouseX, mouseY);
        if (id == null) return false;
        Config.Widget w = Config.get().widget(id);
        w.scale = Math.round((w.scale + (vertical > 0 ? 0.05f : -0.05f)) * 20) / 20f;
        w.scale = Math.max(0.5f, Math.min(3f, w.scale));
        selected = id;
        Config.save();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selected == null) return super.keyPressed(event);
        int step = shift() ? 10 : 1, dx = 0, dy = 0;
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> dx = -step;
            case GLFW.GLFW_KEY_RIGHT -> dx = step;
            case GLFW.GLFW_KEY_UP -> dy = -step;
            case GLFW.GLFW_KEY_DOWN -> dy = step;
            default -> { return super.keyPressed(event); }
        }
        Hud.Placement p = placement(selected);
        setPos(selected, p, p.x() + dx, p.y() + dy);
        Config.save();
        return true;
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
