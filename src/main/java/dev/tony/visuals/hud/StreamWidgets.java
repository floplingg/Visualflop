package dev.tony.visuals.hud;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Ease;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.gui.Shapes;
import dev.tony.visuals.spotify.Spotify;
import dev.tony.visuals.stream.StreamChat;
import dev.tony.visuals.stream.StreamChat.Alert;
import dev.tony.visuals.stream.StreamChat.Message;
import dev.tony.visuals.stream.Streamer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Stream chat card, LIVE chip, alert pop-ups, the confirmation toast and the BRB screen. */
public final class StreamWidgets {
    private StreamWidgets() {}

    public static final int TWITCH = 0xFF9146FF, YOUTUBE = 0xFFFF2E4D, GAME = 0xFF4ADE80;
    private static final int DIM = 0xFFA9B0C7, SOFT = 0xFFD5D9E6, RED = 0xFFFF4D5E, GOLD = 0xFFFFD36B;
    private static final float LINE = 10.5f, TEXT = 0.82f;

    private static Config.Streamer s() {
        return Config.get().streamer;
    }

    private static int fade(int argb, float o) {
        return (Math.round((argb >>> 24) * Math.max(0, Math.min(1, o))) << 24) | (argb & 0xFFFFFF);
    }

    private static int platformColour(StreamChat.Platform p) {
        return switch (p) {
            case TWITCH -> TWITCH;
            case YOUTUBE -> YOUTUBE;
            case GAME -> GAME;
        };
    }

    // ------------------------------------------------------------ stream chat card

    private static final int HEAD = 20;

    public static int[] chatSize() {
        return new int[]{s().chatWidth, Math.round(HEAD + s().chatLines * LINE + 7)};
    }

    /** One wrapped row of a message, ready to draw. */
    private record Row(Message m, boolean first, String text, float alpha) {}

    public static void drawChat(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Hud.card(g, 0, 0, w, h, 12);
        // Header: label, which platforms are on, viewer count.
        Kit.tracked(g, "Stream chat", 10, 8, 0.72f, t.labelColour());
        float hx = w - 10;
        int viewers = StreamChat.viewers();
        if (s().viewerCount && viewers >= 0) {
            String v = viewers + "";
            hx -= Kit.width(v, Kit.MONO, 0.72f);
            Kit.draw(g, v, hx, 8, 0.72f, SOFT, Kit.MONO);
            hx -= 4;
            eye(g, hx - 6, 10.5f, DIM);
            hx -= 12;
        }
        if (!s().youtube.isBlank()) {
            dot(g, hx - 2.5f, 10.5f, YOUTUBE, StreamChat.youtubeStatus().equals("Connected"));
            hx -= 8;
        }
        if (!s().twitch.isBlank()) {
            dot(g, hx - 2.5f, 10.5f, TWITCH, StreamChat.twitchStatus().startsWith("Connected"));
            hx -= 8;
        }
        if (s().gameChat) dot(g, hx - 2.5f, 10.5f, GAME, true);
        Shapes.round(g, 10, HEAD - 3, w - 20, 0.5f, 0, 0x18FFFFFF);

        List<Row> rows = rows(w - 20);
        int max = s().chatLines;
        if (rows.isEmpty()) {
            String msg = s().twitch.isBlank() && s().youtube.isBlank() && !s().gameChat ? "Add your channel on the Streamer page" : "Waiting for chat…";
            Kit.draw(g, msg, w / 2f - Kit.width(msg, Kit.REGULAR, 0.78f) / 2, HEAD + (h - HEAD) / 2f - 5, 0.78f, DIM, Kit.REGULAR);
            return;
        }
        int from = Math.max(0, rows.size() - max);
        float y = h - 6 - (rows.size() - from) * LINE;
        for (int i = from; i < rows.size(); i++) {
            Row r = rows.get(i);
            Message m = r.m();
            float a = r.alpha();
            float x = 10;
            if (m.highlight()) {
                int acc = t.accent() & 0xFFFFFF;
                Shapes.round(g, 6, y - 1.5f, w - 12, LINE, 3, fade((0x30 << 24) | acc, a));
                Shapes.round(g, 6, y - 1.5f, 1.5f, LINE, 0.75f, fade(0xFF000000 | acc, a));
            }
            if (r.first()) {
                if (s().platformBadges) {
                    Shapes.circle(g, x + 2, y + 3.4f, 2f, fade(platformColour(m.platform()), a));
                    x += 7;
                }
                String role = roleTag(m.role());
                if (!role.isEmpty()) {
                    int rc = roleColour(m.role());
                    float rw = Kit.width(role, Kit.BOLD, 0.55f) + 4;
                    Shapes.round(g, x, y + 0.2f, rw, 6.6f, 2, fade((0x40 << 24) | (rc & 0xFFFFFF), a));
                    Kit.draw(g, role, x + 2, y + 1.2f, 0.55f, fade(rc, a), Kit.BOLD);
                    x += rw + 3;
                }
                if (!m.author().isEmpty()) {
                    int nc = s().nameColours ? StreamChat.readable(m.colour() != 0 ? m.colour() : StreamChat.nameColour(m.author())) : SOFT;
                    String name = m.author() + ":";
                    Kit.draw(g, name, x, y, TEXT, fade(0xFF000000 | nc, a), Kit.BOLD);
                    x += Kit.width(name, Kit.BOLD, TEXT) + 3;
                }
            } else {
                x = 10 + (s().platformBadges ? 7 : 0);
            }
            Kit.draw(g, r.text(), x, y, TEXT, fade(m.platform() == StreamChat.Platform.GAME ? 0xFFC9CED8 : 0xFFFFFFFF, a), Kit.REGULAR);
            y += LINE;
        }
    }

    private static List<Row> rows(float width) {
        Config.Streamer st = s();
        long now = System.currentTimeMillis();
        List<Row> out = new ArrayList<>();
        for (Message m : StreamChat.recent(st.chatLines)) {
            float alpha = 1;
            if (st.fadeSeconds > 0) {
                float age = (now - m.time()) / 1000f;
                if (age > st.fadeSeconds + 1.5f) continue;
                if (age > st.fadeSeconds) alpha = 1 - (age - st.fadeSeconds) / 1.5f;
            }
            float in = Math.min(1, (now - m.time()) / 250f);   // quick fade-in for new lines
            alpha *= in;
            float lead = (st.platformBadges ? 7 : 0);
            String role = roleTag(m.role());
            if (!role.isEmpty()) lead += Kit.width(role, Kit.BOLD, 0.55f) + 7;
            if (!m.author().isEmpty()) lead += Kit.width(m.author() + ":", Kit.BOLD, TEXT) + 3;
            List<String> lines = wrap(m.text(), width - lead, width - (st.platformBadges ? 7 : 0));
            for (int i = 0; i < lines.size(); i++) out.add(new Row(m, i == 0, lines.get(i), alpha));
        }
        return out;
    }

    /** Word wrap: the first line is shorter (it shares the row with the name). Very long words are cut. */
    static List<String> wrap(String text, float first, float rest) {
        List<String> out = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float limit = Math.max(20, first);
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (Kit.width(candidate, Kit.REGULAR, TEXT) <= limit) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                limit = rest;
            }
            // A single word wider than the card: break it by characters.
            String w = word;
            while (Kit.width(w, Kit.REGULAR, TEXT) > limit && w.length() > 1) {
                int n = w.length();
                while (n > 1 && Kit.width(w.substring(0, n), Kit.REGULAR, TEXT) > limit) n--;
                out.add(w.substring(0, n));
                w = w.substring(n);
                limit = rest;
            }
            line.append(w);
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (out.size() > 4) {   // keep spam from taking over the card
            out = new ArrayList<>(out.subList(0, 4));
            out.set(3, Kit.fit(out.get(3), (int) (rest / TEXT) - 6, false) + "…");
        }
        return out;
    }

    private static String roleTag(StreamChat.Role r) {
        return switch (r) {
            case OWNER -> "HOST";
            case MOD -> "MOD";
            case VIP -> "VIP";
            default -> "";
        };
    }

    private static int roleColour(StreamChat.Role r) {
        return switch (r) {
            case OWNER -> 0xFFFF6B7A;
            case MOD -> 0xFF4ADE80;
            case VIP -> 0xFFF472B6;
            default -> SOFT;
        };
    }

    private static void dot(GuiGraphics g, float cx, float cy, int colour, boolean on) {
        if (on) Shapes.circle(g, cx, cy, 2.3f, colour);
        else Shapes.ring(g, cx - 2.2f, cy - 2.2f, 4.4f, 4.4f, 2.2f, 0.6f, colour);
    }

    /** Tiny eye for the viewer count. */
    private static void eye(GuiGraphics g, float cx, float cy, int colour) {
        Shapes.ring(g, cx - 4, cy - 2.4f, 8, 4.8f, 2.4f, 0.6f, colour);
        Shapes.circle(g, cx, cy, 1.3f, colour);
    }

    // ------------------------------------------------------------ LIVE chip

    private static final float LABEL = 0.72f;

    private static String liveLabel() {
        return Streamer.live() ? "Live" : "Offline";
    }

    private static String liveValue() {
        if (!Streamer.live()) return "--:--";
        return Streamer.uptime();
    }

    private static String viewersText() {
        int v = StreamChat.viewers();
        return s().viewerCount && v >= 0 ? String.valueOf(v) : "";
    }

    public static int[] liveSize() {
        Theme t = Config.theme();
        float w = 10 + Kit.trackedWidth(liveLabel(), LABEL) + 6 + Kit.width(liveValue(), Kit.MONO, 1);
        String v = viewersText();
        if (!v.isEmpty()) w += 9 + 12 + Kit.width(v, Kit.MONO, 1);
        return new int[]{(int) Math.ceil(w + 2 * Hud.padH(t)), 8 + 2 * Hud.padV(t)};
    }

    public static void drawLive(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Hud.card(g, 0, 0, w, h);
        float x = Hud.padH(t), cy = h / 2f;
        if (Streamer.live()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 2.6e8);
            Shapes.circle(g, x + 3, cy, 3 + 2.2f * pulse, (Math.round(80 * (1 - pulse)) << 24) | (RED & 0xFFFFFF));
            Shapes.circle(g, x + 3, cy, 2.8f, RED);
        } else {
            Shapes.ring(g, x + 0.6f, cy - 2.4f, 4.8f, 4.8f, 2.4f, 0.7f, 0xFF6A7088);
        }
        x += 10;
        Kit.tracked(g, liveLabel(), x, Hud.padV(t) + 2.2f, LABEL, Streamer.live() ? RED : t.labelColour());
        x += Kit.trackedWidth(liveLabel(), LABEL) + 6;
        Kit.draw(g, liveValue(), x, Hud.padV(t), 1, t.valueColour(), Kit.MONO);
        x += Kit.width(liveValue(), Kit.MONO, 1);
        String v = viewersText();
        if (!v.isEmpty()) {
            Shapes.round(g, x + 4.5f, cy - 3.5f, 0.6f, 7, 0, 0x30FFFFFF);
            x += 9;
            eye(g, x + 4.5f, cy, DIM);
            x += 12;
            Kit.draw(g, v, x, Hud.padV(t), 1, t.valueColour(), Kit.MONO);
        }
    }

    // ------------------------------------------------------------ alerts

    private static final long ALERT_MS = 6500;
    private static Alert showing;
    private static long showingSince;

    private static int alertColour(StreamChat.AlertKind k) {
        return switch (k) {
            case SUB, MEMBER -> 0xFFB18CFF;
            case GIFT -> 0xFFFF7AC8;
            case BITS -> 0xFF5CE1FF;
            case RAID -> 0xFFFF9F43;
            case SUPERCHAT -> GOLD;
            case TEST -> 0xFF000000 | Config.theme().accent();
        };
    }

    private static String alertTitle(StreamChat.AlertKind k) {
        return switch (k) {
            case SUB -> "New subscriber";
            case MEMBER -> "New member";
            case GIFT -> "Gifted subs";
            case BITS -> "Cheer";
            case RAID -> "Raid";
            case SUPERCHAT -> "Super Chat";
            case TEST -> "Test alert";
        };
    }

    private static String alertGlyph(StreamChat.AlertKind k) {
        return switch (k) {
            case SUB, MEMBER, TEST -> "★";
            case GIFT -> "♥";
            case BITS -> "◆";
            case RAID -> "»";
            case SUPERCHAT -> "$";
        };
    }

    public static void drawAlerts(GuiGraphics g, int sw, int sh) {
        long now = System.currentTimeMillis();
        if (showing == null || now - showingSince > ALERT_MS) {
            showing = StreamChat.nextAlert();
            showingSince = now;
            if (showing != null && s().alertSound) {
                boolean big = showing.kind() == StreamChat.AlertKind.RAID || showing.kind() == StreamChat.AlertKind.SUPERCHAT
                    || showing.kind() == StreamChat.AlertKind.GIFT;
                Minecraft mc = Minecraft.getInstance();
                net.minecraft.sounds.SoundEvent e = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(
                    net.minecraft.resources.Identifier.withDefaultNamespace(big ? "ui.toast.challenge_complete" : "entity.player.levelup"));
                if (e != null) mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(e, big ? 1f : 1.4f, 0.55f));
            }
        }
        if (showing == null) return;
        float age = (now - showingSince) / 1000f, total = ALERT_MS / 1000f;
        float in = Math.min(1, age / 0.45f), out = Math.min(1, (total - age) / 0.4f);
        float o = Math.max(0, Math.min(in, out));
        float slide = 1 - backOut(Math.min(1, age / 0.55f));
        float w = 250, h = 66;
        float scale = Math.min(1.25f, Math.max(0.8f, sw / 520f));
        g.pose().pushMatrix();
        g.pose().translate(sw / 2f, 12 - slide * 30 + (1 - out) * -8);
        g.pose().scale(scale, scale);
        g.pose().translate(-w / 2, 0);
        drawAlert(g, showing, w, h, o, age, Math.max(0, 1 - age / total));
        g.pose().popMatrix();
    }

    /** Overshoots a little, then settles: the alert "pops" in. */
    private static float backOut(float t) {
        float c = 1.6f, u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static void drawAlert(GuiGraphics g, Alert a, float w, float h, float o, float age, float left) {
        int col = alertColour(a.kind());
        Kit.shadow(g, 0, 0, w, h, 16, 1.3f * o);
        boolean blurred = Config.theme().hudBlur && o > 0.98f && Backdrop.draw(g, 0, 0, w, h, 16);
        Shapes.round(g, 0, 0, w, h, 16, fade(blurred ? 0x8C0D0F1C : 0xE00D0F1C, o));
        // Soft colour wash from the left, and a hairline edge in the alert colour.
        for (int i = 0; i < 6; i++) {
            float k = i / 6f;
            Shapes.roundClip(g, 0, 0, w, h, 16, w * k * 0.6f, 0, w * (k + 1 / 6f) * 0.6f, h, fade(((int) (34 * (1 - k)) << 24) | (col & 0xFFFFFF), o));
        }
        Shapes.ring(g, 0, 0, w, h, 16, 0.6f, fade((0x70 << 24) | (col & 0xFFFFFF), o));

        // Icon: pulsing circle with a glyph, and a few sparks orbiting it.
        float cx = 33, cy = h / 2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(age * 6);
        Shapes.circle(g, cx, cy, 17 + 3 * pulse, fade((0x30 << 24) | (col & 0xFFFFFF), o));
        Shapes.circle(g, cx, cy, 15, fade(col, o));
        String glyph = alertGlyph(a.kind());
        float gs = 1.7f;
        Kit.draw(g, glyph, cx - Kit.width(glyph, Kit.HEAVY, gs) / 2 + 0.3f, cy - 6.6f, gs, fade(0xFF10121C, o), Kit.HEAVY);
        for (int i = 0; i < 5; i++) {
            double ang = age * 2.2 + i * Math.PI * 2 / 5;
            float r = 21 + 2 * (float) Math.sin(age * 3 + i);
            Shapes.circle(g, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, 1.1f, fade((0xC0 << 24) | (col & 0xFFFFFF), o));
        }

        float tx = 58, tw = w - tx - 14;
        Kit.tracked(g, alertTitle(a.kind()), tx, 10, 0.68f, fade(col, o));
        String platform = a.platform() == StreamChat.Platform.YOUTUBE ? "YouTube" : a.platform() == StreamChat.Platform.TWITCH ? "Twitch" : "";
        if (!platform.isEmpty()) {
            float pw = Kit.trackedWidth(platform, 0.6f);
            Kit.tracked(g, platform, w - 14 - pw, 10.5f, 0.6f, fade(DIM, o));
        }
        Kit.draw(g, Kit.fit(a.name(), (int) (tw / 1.45f), true), tx, 20, 1.45f, fade(0xFFFFFFFF, o), Kit.HEAVY);
        Kit.draw(g, Kit.fit(a.headline(), (int) (tw / 0.85f), false), tx, 36, 0.85f, fade(SOFT, o), Kit.REGULAR);
        if (!a.detail().isEmpty()) {
            Kit.draw(g, Kit.fit("“" + a.detail() + "”", (int) (tw / 0.75f), false), tx, 47, 0.75f, fade(DIM, o), Kit.REGULAR);
        }
        // Time left.
        Shapes.round(g, 16, h - 4, w - 32, 1.5f, 0.75f, fade(0x18FFFFFF, o));
        Shapes.round(g, 16, h - 4, (w - 32) * left, 1.5f, 0.75f, fade(col, o));
    }

    // ------------------------------------------------------------ toast (streamer mode on, marker saved…)

    public static void drawToast(GuiGraphics g, int sw, int sh) {
        long at = Streamer.toastAt();
        if (at == 0) return;
        float age = (System.currentTimeMillis() - at) / 1000f;
        if (age > 2.6f) return;
        float o = Math.min(Math.min(1, age / 0.2f), Math.min(1, (2.6f - age) / 0.35f));
        String title = Streamer.toastTitle(), text = Streamer.toastText();
        float w = Math.max(Kit.width(title, Kit.HEAVY, 1), Kit.width(text, Kit.REGULAR, 0.75f)) + 28;
        float h = text.isEmpty() ? 22 : 30;
        float x = sw / 2f - w / 2, y = sh * 0.2f + (1 - o) * 6;
        Kit.shadow(g, x, y, w, h, h / 2, 0.9f * o);
        Shapes.round(g, x, y, w, h, h / 2, fade(0xE00D0F1C, o));
        Shapes.ring(g, x, y, w, h, h / 2, 0.5f, fade(0x30FFFFFF, o));
        Kit.draw(g, title, sw / 2f - Kit.width(title, Kit.HEAVY, 1) / 2, y + 7, 1, fade(0xFFFFFFFF, o), Kit.HEAVY);
        if (!text.isEmpty()) Kit.draw(g, text, sw / 2f - Kit.width(text, Kit.REGULAR, 0.75f) / 2, y + 18, 0.75f, fade(DIM, o), Kit.REGULAR);
    }

    // ------------------------------------------------------------ BRB screen

    private static final Ease BRB = new Ease(0.4f, 0);

    public static void drawBrb(GuiGraphics g, int sw, int sh) {
        float o = BRB.to(Streamer.brb() ? 1 : 0);
        if (o < 0.01f) return;
        Theme t = Config.theme();
        int acc = t.accent() & 0xFFFFFF;
        float time = (System.currentTimeMillis() - Streamer.brbStart()) / 1000f;

        // Frosted game behind, darkened, with slow accent glows drifting around.
        if (t.hudBlur) Backdrop.draw(g, 0, 0, sw, sh, 0);
        Shapes.round(g, 0, 0, sw, sh, 0, fade(0xD0080A14, o));
        for (int i = 0; i < 3; i++) {
            float gx = sw * (0.25f + 0.5f * (0.5f + 0.5f * (float) Math.sin(time * 0.13 + i * 2.1)));
            float gy = sh * (0.3f + 0.4f * (0.5f + 0.5f * (float) Math.cos(time * 0.11 + i * 1.7)));
            float r = sh * (0.35f + 0.08f * i);
            for (int k = 8; k >= 1; k--) Shapes.circle(g, gx, gy, r * k / 8f, fade((5 << 24) | acc, o));
        }

        // Title, subtitle, countdown.
        String title = s().brbTitle.isBlank() ? "Be right back" : s().brbTitle;
        float ts = Math.min(4f, (sw * 0.8f) / Math.max(1, Kit.width(title, Kit.HEAVY, 1)));
        float cy = sh * 0.42f;
        Kit.draw(g, title, sw / 2f - Kit.width(title, Kit.HEAVY, ts) / 2, cy - ts * 9, ts, fade(0xFFFFFFFF, o), Kit.HEAVY);
        String sub = s().brbSubtitle;
        if (!sub.isBlank()) {
            float ss = Math.min(1.3f, ts * 0.38f);
            Kit.draw(g, sub, sw / 2f - Kit.width(sub, Kit.REGULAR, ss) / 2, cy + 6, ss, fade(SOFT, o), Kit.REGULAR);
        }
        long left = Streamer.brbLeft();
        if (left != Long.MIN_VALUE) {
            String clock = left > 0 ? Streamer.clock(left + 999) : "Back any second";
            float cs = left > 0 ? Math.min(2.4f, ts * 0.6f) : Math.min(1.3f, ts * 0.4f);
            int face = left > 0 ? Kit.MONO : Kit.BOLD;
            float cw = Kit.width(clock, face, cs);
            float py = cy + 26;
            float pw = cw + 26, ph = cs * 9 + 10;
            Shapes.round(g, sw / 2f - pw / 2, py, pw, ph, ph / 2, fade(0x22FFFFFF, o));
            Shapes.ring(g, sw / 2f - pw / 2, py, pw, ph, ph / 2, 0.5f, fade((0x80 << 24) | acc, o));
            Kit.draw(g, clock, sw / 2f - cw / 2, py + 5.5f, cs, fade(0xFF000000 | acc, o), face);
            // Progress along the bottom of the screen.
            float frac = Math.max(0, Math.min(1, 1 - left / (s().brbMinutes * 60_000f)));
            Shapes.round(g, 0, sh - 2, sw, 2, 0, fade(0x18FFFFFF, o));
            Shapes.round(g, 0, sh - 2, sw * frac, 2, 0, fade(0xFF000000 | acc, o));
        }

        // Top left: LIVE time. Bottom left: what's playing.
        if (Streamer.live()) {
            Shapes.circle(g, 16, 16, 3, fade(RED, o));
            Kit.tracked(g, "Live " + Streamer.uptime(), 24, 13, 0.8f, fade(SOFT, o));
        }
        Spotify.Track tr = Spotify.track();
        if (tr != null) {
            String np = "♪  " + tr.title() + (tr.artist().isEmpty() ? "" : "  ·  " + tr.artist());
            Kit.draw(g, Kit.fit(np, (int) (sw * 0.45f / 0.9f), false), 16, sh - 20, 0.9f, fade(SOFT, o), Kit.REGULAR);
        }

        // Bottom right: the latest chat.
        if (s().brbChat) {
            List<Message> recent = StreamChat.recent(6);
            float w = Math.min(230, sw * 0.36f), y = sh - 14;
            for (int i = recent.size() - 1; i >= 0 && y > sh * 0.55f; i--) {
                Message m = recent.get(i);
                if (m.platform() == StreamChat.Platform.GAME) continue;
                String name = m.author() + ": ";
                float nw = Kit.width(name, Kit.BOLD, TEXT);
                String text = Kit.fit(m.text(), (int) ((w - nw) / TEXT), false);
                y -= LINE + 3;
                float x = sw - 16 - w;
                Shapes.round(g, x - 6, y - 3, w + 12, LINE + 2, 5, fade(0x50000000, o));
                int nc = StreamChat.readable(m.colour() != 0 ? m.colour() : StreamChat.nameColour(m.author()));
                Kit.draw(g, name, x, y, TEXT, fade(0xFF000000 | nc, o), Kit.BOLD);
                Kit.draw(g, text, x + nw, y, TEXT, fade(0xFFFFFFFF, o), Kit.REGULAR);
            }
        }
    }
}
