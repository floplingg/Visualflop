package dev.tony.visuals.pay;

import dev.tony.visuals.gui.Ease;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.gui.Shapes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.IntConsumer;

/** "How many did they win?" for a stacked bet: pick 0 … N, see what to pay, Confirm. */
public class WinsScreen extends Screen {
    private final Screen parent;
    private final String player;
    private final int count;
    private final double perWin;
    private final IntConsumer done;
    private int pick = -1;
    private final Ease open = new Ease(0.32f, 0);
    private final Ease[] hover;
    private final Ease confirmHover = new Ease(0.2f, 0);
    private final Ease selX = new Ease(0.25f), selY = new Ease(0.25f);

    private float px, py, pw, ph;
    private static final int CELL = 24, GAP = 5, PER_ROW = 8;

    public WinsScreen(Screen parent, String player, int count, double perWin, IntConsumer done) {
        super(Component.literal("How many did they win?"));
        this.parent = parent;
        this.player = player;
        this.count = count;
        this.perWin = perWin;
        this.done = done;
        this.hover = new Ease[count + 1];
        for (int i = 0; i <= count; i++) hover[i] = new Ease(0.2f, 0);
    }

    private int cols() {
        return Math.min(PER_ROW, count + 1);
    }

    private int rows() {
        return (count + 1 + PER_ROW - 1) / PER_ROW;
    }

    @Override
    protected void init() {
        pw = Math.max(220, cols() * (CELL + GAP) - GAP + 32);
        ph = 74 + rows() * (CELL + GAP) + 34;
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
    }

    private float[] cell(int i) {
        float gridW = cols() * (CELL + GAP) - GAP;
        return new float[]{px + (pw - gridW) / 2f + (i % PER_ROW) * (CELL + GAP), py + 52 + (i / PER_ROW) * (CELL + GAP)};
    }

    private float[] confirm() {
        float w = 100, h = 20;
        return new float[]{px + (pw - w) / 2f, py + ph - h - 12, w, h};
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Kit.refresh();
        float o = open.to(1);
        g.fill(0, 0, width, height, ((int) (0x66 * o) << 24) | 0x05060C);
        float s = 0.94f + 0.06f * o;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, height / 2f + (1 - o) * 14);
        g.pose().scale(s, s);
        g.pose().translate(-width / 2f, -height / 2f);

        Kit.shadow(g, px, py, pw, ph, 16, 1.6f);
        Shapes.round(g, px, py, pw, ph, 16, 0xE00C0E1C);
        Shapes.ring(g, px, py, pw, ph, 16, 0.5f, 0x1FFFFFFF);
        Kit.textCenter(g, "How many did " + player + " win?", px + pw / 2, py + 14, 0xFFEEF0FA, true);
        Kit.textCenter(g, count + " bets stacked · $" + Payments.money(perWin) + " per win", px + pw / 2, py + 28, 0xFFA9B0C7, false);

        if (pick >= 0) {
            float[] c = cell(pick);
            float sx = selX.to(c[0]), sy = selY.to(c[1]);
            Shapes.round(g, sx - 2, sy - 2, CELL + 4, CELL + 4, 9, 0x5B000000 | (Kit.ACCENT & 0xFFFFFF));
            Shapes.round(g, sx, sy, CELL, CELL, 7, 0xFF000000 | (Kit.ACCENT & 0xFFFFFF));
        }
        for (int i = 0; i <= count; i++) {
            float[] c = cell(i);
            float hv = hover[i].to(i != pick && inside(mx, my, c[0], c[1], CELL, CELL) ? 1 : 0);
            if (i != pick) Shapes.round(g, c[0], c[1], CELL, CELL, 7, Kit.lerpColour(0x14FFFFFF, 0x2EFFFFFF, hv));
            int tc = i == pick ? (Kit.luminance(Kit.ACCENT) > 0.6f ? 0xFF0B0D16 : 0xFFFFFFFF) : 0xFFEEF0FA;
            Kit.textCenter(g, String.valueOf(i), c[0] + CELL / 2f, c[1] + (CELL - 8) / 2f, tc, true);
        }

        String result = pick < 0 ? "Pick a number" : pick == 0 ? "They lost every bet" : "Pay " + player + " $" + Payments.money(perWin * pick);
        Kit.textCenter(g, result, px + pw / 2, py + ph - 50, pick > 0 ? 0xFF7CFF8A : 0xFFB4BACE, true);

        float[] b = confirm();
        boolean ok = pick >= 0;
        float hv = confirmHover.to(ok && inside(mx, my, b[0], b[1], b[2], b[3]) ? 1 : 0);
        int fill = ok ? Kit.lerpColour(0xFF000000 | (Kit.ACCENT & 0xFFFFFF), 0xFFFFFFFF, hv * 0.15f) : 0x1FFFFFFF;
        Shapes.round(g, b[0], b[1], b[2], b[3], b[3] / 2, fill);
        Kit.textCenter(g, "Confirm", b[0] + b[2] / 2, b[1] + 6, ok ? (Kit.luminance(Kit.ACCENT) > 0.6f ? 0xFF0B0D16 : 0xFFFFFFFF) : 0xFF7A7F92, true);
        g.pose().popMatrix();
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int i = 0; i <= count; i++) {
            float[] c = cell(i);
            if (inside(mx, my, c[0], c[1], CELL, CELL)) {
                if (pick < 0) {
                    selX.snap(c[0]);
                    selY.snap(c[1]);
                }
                pick = i;
                ping();
                return true;
            }
        }
        float[] b = confirm();
        if (pick >= 0 && inside(mx, my, b[0], b[1], b[2], b[3])) {
            ping();
            done.accept(pick);
            minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void ping() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.18F));
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
