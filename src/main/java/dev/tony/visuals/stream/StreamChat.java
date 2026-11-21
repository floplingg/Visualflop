package dev.tony.visuals.stream;

import dev.tony.visuals.Config;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Everything chat-shaped for streamers: messages from Twitch, YouTube and the server, in one list,
 * plus the alert queue (subs, bits, raids, Super Chats, memberships).
 */
public final class StreamChat {
    private StreamChat() {}

    public enum Platform { TWITCH, YOUTUBE, GAME }

    public enum Role { NONE, OWNER, MOD, VIP, SUB }

    /** One chat line. {@code colour} is the name colour (0 = pick one from the name). */
    public record Message(Platform platform, String author, int colour, String text, Role role, boolean highlight, long time) {}

    public enum AlertKind { SUB, GIFT, BITS, RAID, SUPERCHAT, MEMBER, TEST }

    public record Alert(AlertKind kind, Platform platform, String name, String headline, String detail) {}

    private static final int KEEP = 120;
    private static final Deque<Message> MESSAGES = new ArrayDeque<>();
    private static final Deque<Alert> ALERTS = new ArrayDeque<>();
    private static volatile long lastMessage;

    private static TwitchChat twitch;
    private static YouTubeChat youtube;

    // ------------------------------------------------------------ connections

    /** (Re)connects to whatever channels are set. Safe to call any time; unchanged channels keep their connection. */
    public static synchronized void connect() {
        Config.Streamer s = Config.get().streamer;
        String tw = TwitchChat.channelOf(s.twitch);
        if (twitch != null && !twitch.channel().equals(tw)) {
            twitch.stop();
            twitch = null;
        }
        if (twitch == null && !tw.isEmpty()) twitch = TwitchChat.start(tw);

        String yt = s.youtube.trim();
        if (youtube != null && !youtube.source().equals(yt)) {
            youtube.stop();
            youtube = null;
        }
        if (youtube == null && !yt.isEmpty()) youtube = YouTubeChat.start(yt);
    }

    public static synchronized void disconnect() {
        if (twitch != null) twitch.stop();
        if (youtube != null) youtube.stop();
        twitch = null;
        youtube = null;
    }

    public static String twitchStatus() {
        TwitchChat t = twitch;
        if (Config.get().streamer.twitch.isBlank()) return "Not set";
        return t == null ? "Not connected" : t.status();
    }

    public static String youtubeStatus() {
        YouTubeChat y = youtube;
        if (Config.get().streamer.youtube.isBlank()) return "Not set";
        return y == null ? "Not connected" : y.status();
    }

    /** Viewers across Twitch and YouTube, or -1 when nobody's reporting (offline / not set). */
    public static int viewers() {
        int n = -1;
        TwitchChat t = twitch;
        YouTubeChat y = youtube;
        if (t != null && t.viewers() >= 0) n = t.viewers();
        if (y != null && y.viewers() >= 0) n = Math.max(0, n) + y.viewers();
        return n;
    }

    // ------------------------------------------------------------ messages

    public static void add(Platform platform, String author, int colour, String text, Role role) {
        if (text == null) return;
        text = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (text.isEmpty()) return;
        Config.Streamer s = Config.get().streamer;
        if (s.hideCommands && platform != Platform.GAME && (text.startsWith("!") || isBot(author))) return;
        boolean hl = platform != Platform.GAME && mentions(text);
        Message m = new Message(platform, author == null ? "" : author, colour, text, role, hl, System.currentTimeMillis());
        synchronized (MESSAGES) {
            MESSAGES.addLast(m);
            while (MESSAGES.size() > KEEP) MESSAGES.removeFirst();
        }
        lastMessage = m.time();
        if (hl && s.mentionSound) Minecraft.getInstance().execute(() -> Sounds.play("block.note_block.bell", 1.6f, 0.6f));
    }

    private static boolean isBot(String author) {
        if (author == null) return false;
        String a = author.toLowerCase(Locale.ROOT);
        return a.equals("nightbot") || a.equals("streamlabs") || a.equals("streamelements") || a.equals("moobot") || a.equals("fossabot")
            || a.equals("wizebot") || a.equals("botrixoficial");
    }

    /** True when the message says your Minecraft name or one of your mention words. */
    static boolean mentions(String text) {
        String low = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_]+", " ") + " ";
        List<String> words = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null) words.add(mc.getUser().getName());
        for (String w : Config.get().streamer.mentions.split(",")) words.add(w);
        for (String w : words) {
            String k = w == null ? "" : w.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_ ]+", " ").trim();
            if (k.length() >= 2 && low.contains(" " + k + " ")) return true;
        }
        return false;
    }

    /** Newest last. */
    public static List<Message> recent(int n) {
        synchronized (MESSAGES) {
            List<Message> all = new ArrayList<>(MESSAGES);
            return all.subList(Math.max(0, all.size() - n), all.size());
        }
    }

    public static long lastMessageTime() {
        return lastMessage;
    }

    public static void clear() {
        synchronized (MESSAGES) {
            MESSAGES.clear();
        }
    }

    // ------------------------------------------------------------ alerts

    public static void alert(Alert a) {
        Config.Streamer s = Config.get().streamer;
        if (!s.alerts) return;
        boolean wanted = switch (a.kind()) {
            case SUB, GIFT, MEMBER -> s.alertSubs;
            case BITS -> s.alertBits;
            case RAID -> s.alertRaids;
            case SUPERCHAT -> s.alertSuperChats;
            case TEST -> true;
        };
        if (!wanted) return;
        synchronized (ALERTS) {
            if (ALERTS.size() < 30) ALERTS.addLast(a);
        }
    }

    /** Next alert to show, or null. */
    public static Alert nextAlert() {
        synchronized (ALERTS) {
            return ALERTS.pollFirst();
        }
    }

    // ------------------------------------------------------------ helpers

    /** A stable, readable colour for names that don't come with one. */
    public static int nameColour(String name) {
        int[] palette = {0xFF7AB8, 0x7DD3FC, 0xA78BFA, 0x4ADE80, 0xFBBF24, 0xF472B6, 0x60A5FA, 0x34D399, 0xFB923C, 0xC084FC, 0x2DD4BF, 0xF87171};
        return palette[Math.floorMod(name == null ? 0 : name.toLowerCase(Locale.ROOT).hashCode(), palette.length)];
    }

    /** Lift very dark name colours so they stay readable on the dark card. */
    public static int readable(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
        if (lum >= 0.45f) return rgb & 0xFFFFFF;
        float k = (0.45f - lum) / 0.45f * 0.7f;
        r = Math.round(r + (255 - r) * k);
        g = Math.round(g + (255 - g) * k);
        b = Math.round(b + (255 - b) * k);
        return r << 16 | g << 8 | b;
    }
}
