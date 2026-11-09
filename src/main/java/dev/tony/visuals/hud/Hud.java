package dev.tony.visuals.hud;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.gui.LayoutScreen;
import dev.tony.visuals.spotify.Spotify;
import dev.tony.visuals.gui.Shapes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws every enabled widget. Each widget is a smooth rounded card in the shared theme. */
public final class Hud {
    private Hud() {}

    public record Placement(int x, int y, int w, int h, int rawW, int rawH) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // ------------------------------------------------------------ entry point

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Stats.frame(mc);
        newFrame();
        if (mc.screen instanceof LayoutScreen) return;   // the layout editor draws its own copy
        Font font = mc.font;
        Backdrop.capture();
        for (String id : Config.allWidgets()) {
            Config.Widget w = Config.get().widget(id);
            if (!w.enabled) continue;
            // Lyrics only show while a song with lyrics is playing.
            if (id.equals("lyrics") && !LyricsWidget.hasLyrics()) continue;
            draw(g, font, id, w, g.guiWidth(), g.guiHeight());
        }
        YouTubeWidget.drawFullscreen(g, g.guiWidth(), g.guiHeight());
        MoneyWidgets.drawAuctionOverlay(g, g.guiWidth(), g.guiHeight());
    }

    // ------------------------------------------------------------ layout

    /** Unscaled size {w, h}. */
    public static int[] size(Font font, String id) {
        Theme t = Config.theme();
        if (id.equals("keystrokes")) return Keystrokes.size();
        if (id.equals("spotify")) return SpotifyWidget.size();
        if (id.equals("lyrics")) return LyricsWidget.size();
        if (id.equals("profit")) return MoneyWidgets.profitSize();
        if (id.equals("moneygraph")) return MoneyWidgets.graphSize();
        if (id.equals("youtube")) return YouTubeWidget.size();
        if (id.startsWith("text:")) {
            Config.CustomText ct = Config.get().text(id);
            String s = ct == null ? "" : fill(ct.text);
            int w = Kit.width(s, ct != null && ct.bold);
            return new int[]{w + 2 * t.padding + 2, 8 + 2 * t.padding + 1};
        }
        if (id.equals("armor")) {
            int n = armorItems().size();
            return new int[]{Math.max(1, n) * 18 + 2 * t.padding - 2 + strip(t), 16 + 2 * t.padding + (n > 0 ? 9 : 0)};
        }
        String[] parts = text(id);
        float w = chipLabelW(parts[0]) + Kit.width(parts[1], Kit.MONO, 1);
        return new int[]{(int) Math.ceil(w + 2 * padH(t) + strip(t)), 8 + 2 * padV(t)};
    }

    private static int strip(Theme t) {
        return t.accentStrip ? 3 : 0;
    }

    public static Placement place(Font font, String id, Config.Widget w, int screenW, int screenH) {
        int[] raw = size(font, id);
        int sw = Math.round(raw[0] * w.scale), sh = Math.round(raw[1] * w.scale);
        int x = Math.round(w.x * Math.max(0, screenW - sw));
        int y = Math.round(w.y * Math.max(0, screenH - sh));
        return new Placement(x, y, sw, sh, raw[0], raw[1]);
    }

    public static Placement draw(GuiGraphics g, Font font, String id, Config.Widget w, int screenW, int screenH) {
        Placement p = place(font, id, w, screenW, screenH);
        g.pose().pushMatrix();
        g.pose().translate(p.x(), p.y());
        g.pose().scale(w.scale, w.scale);
        switch (id) {
            case "keystrokes" -> Keystrokes.draw(g, font);
            case "armor" -> drawArmor(g, font, p.rawW(), p.rawH());
            case "spotify" -> SpotifyWidget.draw(g, p.rawW(), p.rawH());
            case "lyrics" -> LyricsWidget.draw(g, p.rawW(), p.rawH());
            case "profit" -> MoneyWidgets.drawProfit(g, p.rawW(), p.rawH());
            case "moneygraph" -> MoneyWidgets.drawGraph(g, p.rawW(), p.rawH());
            case "youtube" -> YouTubeWidget.draw(g, p.rawW(), p.rawH());
            default -> {
                if (id.startsWith("text:")) drawCustom(g, id, p.rawW(), p.rawH());
                else drawText(g, font, id, p.rawW(), p.rawH());
            }
        }
        g.pose().popMatrix();
        return p;
    }

    // ------------------------------------------------------------ shared card

    public static void card(GuiGraphics g, float x, float y, float w, float h) {
        card(g, x, y, w, h, Config.theme().rounding);
    }

    /** Glass card from the preview: soft shadow, frosted blur of the game behind, dark tint, hairline edge. */
    public static void card(GuiGraphics g, float x, float y, float w, float h, float radius) {
        Theme t = Config.theme();
        float r = Math.min(radius, Math.min(w, h) / 2);
        if (t.widgetOpacity > 0) Kit.shadow(g, x, y, w, h, r, 0.55f);
        boolean blurred = t.hudBlur && t.widgetOpacity > 0 && Backdrop.draw(g, x, y, w, h, r);
        if (t.outline && t.outlineOpacity > 0) {
            int edge = t.outlineAccent ? t.accent() : 0xFFFFFF;
            Shapes.ring(g, x, y, w, h, r, 0.5f, Theme.argb(edge, t.outlineOpacity));
        }
        if (t.widgetOpacity > 0) Shapes.round(g, x, y, w, h, r, Theme.argb(t.background, blurred ? Math.round(t.widgetOpacity * 0.55f) : t.widgetOpacity));
        if (t.accentStrip) Shapes.roundClip(g, x, y, w, h, r, x, y, x + 2.5f, y + h, Theme.opaque(t.accent()));
    }

    private static int mixAccent(Theme t, float k) {
        int a = t.accent(), b = t.background;
        int r = Math.round(((a >> 16) & 255) * k + ((b >> 16) & 255) * (1 - k));
        int gr = Math.round(((a >> 8) & 255) * k + ((b >> 8) & 255) * (1 - k));
        int bl = Math.round((a & 255) * k + (b & 255) * (1 - k));
        return r << 16 | gr << 8 | bl;
    }

    // ------------------------------------------------------------ text widgets

    private static void drawText(GuiGraphics g, Font font, String id, int w, int h) {
        Theme t = Config.theme();
        card(g, 0, 0, w, h);
        String[] parts = text(id);
        float x = padH(t) + strip(t), y = padV(t);
        if (!parts[0].isEmpty()) {
            // Small, wide-spaced label sitting on the number's baseline, like "FPS 240" in the preview.
            Kit.tracked(g, parts[0], x, y + 2.2f, LABEL_SCALE, t.labelColour());
            x += chipLabelW(parts[0]);
        }
        if (t.textShadow) Kit.draw(g, parts[1], x + 0.5f, y + 0.5f, 1, 0x80000000, Kit.MONO);
        Kit.draw(g, parts[1], x, y, 1, t.valueColour(), Kit.MONO);
    }

    /** For the screenshot test. */
    public static void drawChip(GuiGraphics g, String id, int w, int h) {
        drawText(g, Minecraft.getInstance().font, id, w, h);
    }

    private static final float LABEL_SCALE = 0.72f;

    private static float chipLabelW(String label) {
        return label.isEmpty() ? 0 : Kit.trackedWidth(label, LABEL_SCALE) + 5;
    }

    static int padV(Theme t) {
        return t.padding + 2;
    }

    static int padH(Theme t) {
        return t.padding + 5;
    }

    private static void drawCustom(GuiGraphics g, String id, int w, int h) {
        Theme t = Config.theme();
        Config.CustomText ct = Config.get().text(id);
        if (ct == null) return;
        if (ct.background) card(g, 0, 0, w, h);
        Kit.text(g, fill(ct.text), t.padding + 1, t.padding + 1, 0xFF000000 | (ct.colour & 0xFFFFFF), ct.bold, t.textShadow);
    }

    /** Swaps {fps}, {coords}, {ping}, {clock}, {biome}, {speed}, {direction}, {cps}, {day} for live values. */
    public static String fill(String s) {
        if (s == null || s.indexOf('{') < 0) return s == null ? "" : s;
        for (String key : new String[]{"fps", "coords", "ping", "clock", "biome", "speed", "direction", "cps", "day"}) {
            String tag = "{" + key + "}";
            if (s.contains(tag)) s = s.replace(tag, text(key)[1]);
        }
        return s;
    }

    private static final java.util.Map<String, String[]> textCache = new java.util.HashMap<>();
    private static List<ItemStack> armorCache;

    /** Forget this frame's computed text (called once per frame). */
    public static void newFrame() {
        textCache.clear();
        armorCache = null;
    }

    /** {label, value} for each text widget, worked out once per frame. */
    public static String[] text(String id) {
        return textCache.computeIfAbsent(id, Hud::computeText);
    }

    private static String[] computeText(String id) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        return switch (id) {
            case "fps" -> new String[]{"FPS", String.valueOf(mc.getFps())};
            case "coords" -> {
                if (p == null) yield new String[]{"XYZ", "0, 64, 0"};
                BlockPos b = p.blockPosition();
                String v = b.getX() + ", " + b.getY() + ", " + b.getZ();
                if (p.level().dimension() == Level.NETHER) v += "  §7(" + b.getX() * 8 + ", " + b.getZ() * 8 + ")";
                else if (p.level().dimension() == Level.OVERWORLD) v += "  §7(" + Math.floorDiv(b.getX(), 8) + ", " + Math.floorDiv(b.getZ(), 8) + ")";
                yield new String[]{"XYZ", v};
            }
            case "direction" -> {
                if (p == null) yield new String[]{"Facing", "North (-Z)"};
                String d = switch (p.getDirection()) {
                    case NORTH -> "North (-Z)";
                    case SOUTH -> "South (+Z)";
                    case EAST -> "East (+X)";
                    case WEST -> "West (-X)";
                    default -> p.getDirection().getName();
                };
                yield new String[]{"Facing", d};
            }
            case "speed" -> new String[]{"Speed", String.format(Locale.ROOT, "%.1f b/s", Stats.speed())};
            case "ping" -> new String[]{"Ping", ping(mc) + " ms"};
            case "cps" -> new String[]{"CPS", Stats.cps(true) + " | " + Stats.cps(false)};
            case "clock" -> new String[]{"", LocalTime.now().format(CLOCK)};
            case "biome" -> {
                String name = Stats.biome();
                yield new String[]{"Biome", name.isEmpty() ? "Plains" : pretty(name)};
            }
            case "day" -> new String[]{"Day", mc.level == null ? "1" : String.valueOf(mc.level.getDayTime() / 24000L + 1)};
            default -> new String[]{"", id};
        };
    }

    private static int ping(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private static String pretty(String registryName) {
        String s = registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName;
        StringBuilder out = new StringBuilder();
        for (String word : s.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    // ------------------------------------------------------------ armor widget

    private static List<ItemStack> armorItems() {
        if (armorCache != null) return armorCache;
        List<ItemStack> out = new ArrayList<>();
        armorCache = out;
        Player p = Minecraft.getInstance().player;
        if (p == null) return out;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack s = p.getItemBySlot(slot);
            if (!s.isEmpty()) out.add(s);
        }
        ItemStack hand = p.getMainHandItem();
        if (!hand.isEmpty() && hand.isDamageableItem()) out.add(hand);
        return out;
    }

    private static void drawArmor(GuiGraphics g, Font font, int w, int h) {
        Theme t = Config.theme();
        card(g, 0, 0, w, h);
        List<ItemStack> items = armorItems();
        if (items.isEmpty()) {
            String s = "No armor";
            Kit.text(g, s, (w - Kit.width(s)) / 2f, (h - 8) / 2f, Theme.opaque(t.muted), false, t.textShadow);
            return;
        }
        int x = t.padding + strip(t), y = t.padding;
        for (ItemStack s : items) {
            g.renderItem(s, x, y);
            if (s.isDamageableItem()) {
                int pct = Math.round(100f * (s.getMaxDamage() - s.getDamageValue()) / Math.max(1, s.getMaxDamage()));
                String v = String.valueOf(pct);
                int c = pct > 50 ? t.text : pct > 20 ? 0xFFD166 : 0xFF5555;
                g.pose().pushMatrix();
                g.pose().translate(x + 8, y + 17);
                g.pose().scale(0.75f, 0.75f);
                Kit.text(g, v, -Kit.width(v) / 2f, 0, Theme.opaque(c), false, t.textShadow);
                g.pose().popMatrix();
            }
            x += 18;
        }
    }
}
