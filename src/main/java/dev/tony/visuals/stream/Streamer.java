package dev.tony.visuals.stream;

import dev.tony.visuals.Config;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/** The streamer toolkit's state: LIVE timer, streamer mode, BRB screen, clip markers and the little toast that confirms them. */
public final class Streamer {
    private Streamer() {}

    private static Config.Streamer s() {
        return Config.get().streamer;
    }

    public static void init() {
        // Whispers in vanilla (and some servers) are chat messages of the "msg" type: swap them for a hidden note.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
            try {
                if (hideWhispers() && (params.chatType().is(ChatType.MSG_COMMAND_INCOMING) || params.chatType().is(ChatType.MSG_COMMAND_OUTGOING))) {
                    Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(hiddenWhisper());
                    return false;
                }
            } catch (Throwable ignored) {
            }
            return true;
        });
        // Most servers send private messages as plain system lines like "[Steve -> me] hi".
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : filter(message));
        // Mirror the server's chat into the stream chat card when asked.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && s().gameChat) StreamChat.add(StreamChat.Platform.GAME, "", 0, message.getString(), StreamChat.Role.NONE);
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
            if (s().gameChat) StreamChat.add(StreamChat.Platform.GAME, "", 0, message.getString(), StreamChat.Role.NONE);
        });
        StreamChat.connect();
    }

    // ------------------------------------------------------------ streamer mode

    public static boolean privacy() {
        return s().privacy;
    }

    public static void togglePrivacy() {
        s().privacy = !s().privacy;
        Config.save();
        toast(s().privacy ? "Streamer mode on" : "Streamer mode off", s().privacy ? "Coordinates, whispers and money are hidden" : "Everything shows again");
    }

    public static boolean hideCoords() {
        return s().privacy && s().hideCoords;
    }

    public static boolean hideWhispers() {
        return s().privacy && s().hideWhispers;
    }

    public static boolean hideMoney() {
        return s().privacy && s().hideMoney;
    }

    private static final Pattern[] WHISPER = {
        Pattern.compile("(?i)^\\s*\\[?\\s*[\\w.*~]{2,24}\\s*(->|→|➡|»|>>)\\s*(me|you|ich|moi)\\s*\\]?"),
        Pattern.compile("(?i)^\\s*\\[?\\s*(me|you)\\s*(->|→|➡|»|>>)\\s*[\\w.*~]{2,24}\\s*\\]?"),
        Pattern.compile("(?i)\\bwhispers( to you)?\\s*:"),
        Pattern.compile("(?i)^\\s*you whisper to\\b"),
        Pattern.compile("(?i)^\\s*\\[?(from|to)\\]?\\s+[\\w.*~]{2,24}\\s*[:»>]"),
        Pattern.compile("(?i)^\\s*[\\[(]\\s*(msg|pm|dm|whisper|tell)\\s*[\\])]"),
    };

    /** The message, or a "hidden" note in its place when it's a private message and streamer mode hides those. */
    public static Component filter(Component message) {
        if (!hideWhispers()) return message;
        try {
            return looksLikeWhisper(message.getString()) ? hiddenWhisper() : message;
        } catch (Throwable e) {
            return message;
        }
    }

    static boolean looksLikeWhisper(String text) {
        for (Pattern p : WHISPER) if (p.matcher(text).find()) return true;
        return false;
    }

    private static Component hiddenWhisper() {
        return Component.literal("✉ Private message hidden (streamer mode)").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    // ------------------------------------------------------------ LIVE timer

    public static boolean live() {
        return s().liveSince > 0;
    }

    public static void toggleLive() {
        if (live()) {
            s().liveSince = 0;
            toast("Stream ended", "LIVE timer stopped");
        } else {
            s().liveSince = System.currentTimeMillis();
            markers = 0;
            toast("You're live", "LIVE timer started");
        }
        Config.save();
    }

    /** Twitch says the stream started at {@code since}: start (or correct) the timer to match. */
    static void wentLive(long since) {
        if (since <= 0) return;
        Minecraft.getInstance().execute(() -> {
            long cur = s().liveSince;
            if (cur == 0 || Math.abs(cur - since) > 90_000) {
                if (cur == 0) markers = 0;
                s().liveSince = since;
                Config.save();
            }
        });
    }

    public static long uptimeMs() {
        return live() ? Math.max(0, System.currentTimeMillis() - s().liveSince) : 0;
    }

    public static String uptime() {
        return clock(uptimeMs());
    }

    public static String clock(long ms) {
        long t = ms / 1000;
        long h = t / 3600, m = t / 60 % 60, sec = t % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    // ------------------------------------------------------------ BRB screen

    private static boolean brb;
    private static long brbStart;

    public static boolean brb() {
        return brb;
    }

    public static void toggleBrb() {
        brb = !brb;
        brbStart = System.currentTimeMillis();
    }

    public static long brbStart() {
        return brbStart;
    }

    /** Milliseconds left on the BRB countdown (negative once it's over), or Long.MIN_VALUE without a countdown. */
    public static long brbLeft() {
        int mins = s().brbMinutes;
        if (mins <= 0) return Long.MIN_VALUE;
        return brbStart + mins * 60_000L - System.currentTimeMillis();
    }

    // ------------------------------------------------------------ clip markers

    private static int markers;

    public static Path markerFile() {
        return Config.DIR.resolve("stream-markers.txt");
    }

    /** Notes the moment in config/flop-visuals/stream-markers.txt so it's easy to find when editing the VOD. */
    public static void marker() {
        Minecraft mc = Minecraft.getInstance();
        markers++;
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT));
        String stamp = live() ? uptime() : "not live";
        String where = mc.getCurrentServer() != null ? mc.getCurrentServer().name : mc.hasSingleplayerServer() ? "Singleplayer" : "";
        String song = dev.tony.visuals.spotify.Spotify.track() == null ? "" : "  ·  ♪ " + dev.tony.visuals.spotify.Spotify.track().title();
        String line = String.format(Locale.ROOT, "%s  ·  stream %s  ·  marker %d%s%s%n", when, stamp, markers,
            where.isEmpty() ? "" : "  ·  " + where, song);
        try {
            Files.createDirectories(Config.DIR);
            if (!Files.exists(markerFile())) {
                Files.writeString(markerFile(), "Flop Visuals stream markers. Each line is a moment you marked while streaming.\n\n", StandardCharsets.UTF_8);
            }
            Files.writeString(markerFile(), line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            toast("Marker " + markers, live() ? "Saved at " + stamp : "Saved (start the LIVE timer for stream times)");
        } catch (IOException e) {
            toast("Couldn't save the marker", e.getMessage() == null ? "" : e.getMessage());
        }
    }

    public static int markersThisStream() {
        return markers;
    }

    // ------------------------------------------------------------ toast

    private static String toastTitle = "", toastText = "";
    private static long toastAt;

    public static void toast(String title, String text) {
        toastTitle = title;
        toastText = text;
        toastAt = System.currentTimeMillis();
        Sounds.play("ui.button.click", 1.6f, 0.25f);
    }

    public static String toastTitle() {
        return toastTitle;
    }

    public static String toastText() {
        return toastText;
    }

    public static long toastAt() {
        return toastAt;
    }
}
