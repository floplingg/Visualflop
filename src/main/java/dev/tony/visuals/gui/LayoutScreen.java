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
    private float resetX, doneX, btnY, resetW, doneW, resetHover, doneHover;
    private long lastFrame;
    private String dragging, selected;
    private double grabX, grabY;
    private int guideX = -1, guideY = -1;

    public LayoutScreen(Screen parent) {
        super(Component.literal("HUD layout"));
        this.parent = parent;

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
        Kit.refresh();
        g.fill(0, 0, width, height, 0x40000000);
        int grid = (Kit.ACCENT & 0xFFFFFF) | 0x10000000;
        for (int x = 0; x < width; x += 20) g.fill(x, 0, x + 1, height, grid);
        for (int y = 0; y < height; y += 20) g.fill(0, y, width, y + 1, grid);
        int guide = (Kit.ACCENT & 0xFFFFFF) | 0xB0000000;
        if (guideX >= 0) g.fill(guideX, 0, guideX + 1, height, guide);
        if (guideY >= 0) g.fill(0, guideY, width, guideY + 1, guide);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Kit.refresh();
        Hud.newFrame();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        super.render(g, mouseX, mouseY, partialTick);
        String hover = dragging != null ? dragging : widgetAt(mouseX, mouseY);
        for (String id : Config.WIDGETS) {
            Config.Widget w = Config.get().widget(id);
            if (!w.enabled) continue;
            Hud.Placement p = Hud.draw(g, font, id, w, width, height);
            boolean lit = id.equals(hover) || id.equals(selected);
            int c = lit ? Kit.ACCENT : (Kit.ACCENT & 0xFFFFFF) | 0x50000000;
            Shapes.ring(g, p.x() - 1.5f, p.y() - 1.5f, p.w() + 3, p.h() + 3, Config.theme().rounding + 1.5f, lit ? 1f : 0.5f, c);
            if (lit) {
                String label = Config.widgetName(id) + "  " + Math.round(w.scale * 100) + "%";
                float lw = Kit.width(label, true) + 14, lh = 15;
                float ly = p.y() > 20 ? p.y() - lh - 4 : p.y() + p.h() + 4;
                float lx = Math.max(2, Math.min(width - lw - 2, p.x()));
                Kit.shadow(g, lx, ly, lw, lh, lh / 2, 0.8f);
                Shapes.round(g, lx, ly, lw, lh, lh / 2, Kit.PANEL);
                Kit.text(g, label, lx + 7, ly + 4, Kit.LABEL, true);
            }
        }

        // Floating help card in the middle.
        String help = "Drag to move · Scroll to resize · Right-click to hide · Arrows nudge · Shift = no snap";
        float hw = Kit.width(help) + 28, hh = 52, hx = (width - hw) / 2f, hy = height / 2f - hh / 2f;
        Kit.shadow(g, hx, hy, hw, hh, 12, 1.2f);
        Shapes.round(g, hx, hy, hw, hh, 12, Kit.PANEL);
        Shapes.ring(g, hx, hy, hw, hh, 12, 0.5f, Kit.dark ? 0x26FFFFFF : 0x1A000000);
        Kit.textCenter(g, help, width / 2f, hy + 10, Kit.SECONDARY, false);
        resetW = Kit.width("Reset sizes", true) + 22;
        doneW = Kit.width("Done", true) + 26;
        btnY = hy + 26;
        resetX = width / 2f - resetW - 4;
        doneX = width / 2f + 4;
        resetHover = Kit.approach(resetHover, inBtn(mouseX, mouseY, resetX, resetW) ? 1 : 0, 14, dt);
        doneHover = Kit.approach(doneHover, inBtn(mouseX, mouseY, doneX, doneW) ? 1 : 0, 14, dt);
        Kit.pill(g, resetX, btnY, resetW, 18, "Reset sizes", false, resetHover);
        Kit.pill(g, doneX, btnY, doneW, 18, "Done", true, doneHover);
    }

    private boolean inBtn(double mx, double my, float x, float w) {
        return mx >= x && mx < x + w && my >= btnY && my < btnY + 18;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0 && inBtn(mx, my, resetX, resetW)) {
            for (Config.Widget w : Config.get().widgets.values()) w.scale = 1f;
            Config.save();
            return true;
        }
        if (event.button() == 0 && inBtn(mx, my, doneX, doneW)) {
            onClose();
            return true;
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
