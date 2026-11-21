package dev.tony.visuals.stream;

import dev.tony.visuals.stream.StreamChat.AlertKind;
import dev.tony.visuals.stream.StreamChat.Platform;
import dev.tony.visuals.stream.StreamChat.Role;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Twitch channel's chat over Twitch's IRC interface, logged in anonymously (read-only, no account or token).
 * Subs, gifted subs, bits and raids arrive in the same stream, so they become alerts.
 */
final class TwitchChat {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private final String channel;
    private volatile boolean stopped;
    private volatile String status = "Connecting…";
    private volatile int viewers = -1;
    private volatile Socket socket;

    private TwitchChat(String channel) {
        this.channel = channel;
    }

    /** "twitch.tv/Name", "@Name", "Name" → "name". */
    static String channelOf(String input) {
        if (input == null) return "";
        String s = input.trim();
        Matcher m = Pattern.compile("twitch\\.tv/([A-Za-z0-9_]+)").matcher(s);
        if (m.find()) s = m.group(1);
        s = s.replaceAll("^[@#]", "");
        return s.matches("[A-Za-z0-9_]{2,25}") ? s.toLowerCase(Locale.ROOT) : "";
    }

    static TwitchChat start(String channel) {
        TwitchChat t = new TwitchChat(channel);
        Thread chat = new Thread(t::run, "Flop Visuals Twitch chat");
        chat.setDaemon(true);
        chat.start();
        Thread stats = new Thread(t::pollViewers, "Flop Visuals Twitch viewers");
        stats.setDaemon(true);
        stats.start();
        return t;
    }

    String channel() {
        return channel;
    }

    String status() {
        return status;
    }

    int viewers() {
        return viewers;
    }

    void stop() {
        stopped = true;
        try {
            Socket s = socket;
            if (s != null) s.close();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ chat connection

    private void run() {
        int wait = 2;
        while (!stopped) {
            try (Socket s = SSLSocketFactory.getDefault().createSocket()) {
                socket = s;
                status = "Connecting…";
                s.connect(new InetSocketAddress("irc.chat.twitch.tv", 6697), 10_000);
                s.setSoTimeout(6 * 60_000);   // Twitch pings about every 5 minutes
                Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                send(out, "CAP REQ :twitch.tv/tags twitch.tv/commands");
                send(out, "PASS SCHMOOPIIE");
                send(out, "NICK justinfan" + ThreadLocalRandom.current().nextInt(10_000, 99_999));
                send(out, "JOIN #" + channel);
                String line;
                while (!stopped && (line = in.readLine()) != null) {
                    if (line.startsWith("PING")) {
                        send(out, "PONG" + line.substring(4));
                        continue;
                    }
                    if (handle(line)) break;   // asked to reconnect
                    wait = 2;
                }
            } catch (Exception e) {
                if (!stopped) status = "Reconnecting… (" + e.getClass().getSimpleName() + ")";
            }
            socket = null;
            if (stopped) break;
            sleep(wait * 1000L);
            wait = Math.min(60, wait * 2);
        }
        status = "Disconnected";
    }

    private static void send(Writer out, String line) throws Exception {
        out.write(line + "\r\n");
        out.flush();
    }

    /** Returns true when Twitch asks us to reconnect. */
    private boolean handle(String line) {
        Map<String, String> tags = new HashMap<>();
        String rest = line;
        if (rest.startsWith("@")) {
            int sp = rest.indexOf(' ');
            if (sp < 0) return false;
            for (String kv : rest.substring(1, sp).split(";")) {
                int eq = kv.indexOf('=');
                if (eq > 0) tags.put(kv.substring(0, eq), unescape(kv.substring(eq + 1)));
            }
            rest = rest.substring(sp + 1);
        }
        String prefix = "";
        if (rest.startsWith(":")) {
            int sp = rest.indexOf(' ');
            if (sp < 0) return false;
            prefix = rest.substring(1, sp);
            rest = rest.substring(sp + 1);
        }
        String trailing = "";
        int t = rest.indexOf(" :");
        if (t >= 0) {
            trailing = rest.substring(t + 2);
            rest = rest.substring(0, t);
        }
        String command = rest.split(" ")[0];
        switch (command) {
            case "001", "JOIN" -> status = "Connected to #" + channel;
            case "RECONNECT" -> {
                status = "Reconnecting…";
                return true;
            }
            case "NOTICE" -> {
                if (trailing.toLowerCase(Locale.ROOT).contains("does not exist") || trailing.toLowerCase(Locale.ROOT).contains("suspended")) status = trailing;
            }
            case "PRIVMSG" -> privmsg(tags, prefix, trailing);
            case "USERNOTICE" -> usernotice(tags, trailing);
            default -> {}
        }
        return false;
    }

    private void privmsg(Map<String, String> tags, String prefix, String text) {
        String name = tags.getOrDefault("display-name", "");
        if (name.isEmpty()) name = prefix.contains("!") ? prefix.substring(0, prefix.indexOf('!')) : prefix;
        // "/me waves" arrives wrapped in ACTION.
        if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) text = text.substring(8, text.length() - 1);
        int colour = parseColour(tags.get("color"));
        StreamChat.add(Platform.TWITCH, name, colour, text, role(tags.getOrDefault("badges", "")));
        String bits = tags.get("bits");
        if (bits != null && !bits.isEmpty()) {
            int n = parseInt(bits);
            String clean = text.replaceAll("(?i)\\b(cheer|biblethump|cheerwhal|corgo|uni|showlove|party|seemsgood|pride|kappa|frankerz|heyguys|dansgame|elegiggle|trihard|kreygasm|4head|swiftrage|notlikethis|failfish|vohiyo|pjsalt|mrdestructoid|bday|ripcheer|shamrock)\\d+\\b", "").trim();
            StreamChat.alert(new StreamChat.Alert(AlertKind.BITS, Platform.TWITCH, name, "cheered " + n + (n == 1 ? " bit" : " bits"), clean));
        }
    }

    private void usernotice(Map<String, String> tags, String text) {
        String id = tags.getOrDefault("msg-id", "");
        String name = tags.getOrDefault("display-name", tags.getOrDefault("login", "Someone"));
        String sys = tags.getOrDefault("system-msg", "");
        switch (id) {
            case "sub", "resub" -> {
                int months = parseInt(tags.getOrDefault("msg-param-cumulative-months", "1"));
                String plan = tags.getOrDefault("msg-param-sub-plan", "");
                String tier = plan.equals("Prime") ? "with Prime" : plan.equals("2000") ? "at Tier 2" : plan.equals("3000") ? "at Tier 3" : "";
                String head = id.equals("sub") ? ("subscribed " + tier).trim() : "resubscribed · " + months + " months";
                StreamChat.alert(new StreamChat.Alert(AlertKind.SUB, Platform.TWITCH, name, head, text));
            }
            case "subgift", "anonsubgift" -> {
                // Part of a mystery gift? The bundle alert already covers it.
                if (!tags.getOrDefault("msg-param-community-gift-id", "").isEmpty()) break;
                String to = tags.getOrDefault("msg-param-recipient-display-name", "someone");
                StreamChat.alert(new StreamChat.Alert(AlertKind.GIFT, Platform.TWITCH, name, "gifted a sub to " + to, ""));
            }
            case "submysterygift", "anonsubmysterygift" -> {
                int n = parseInt(tags.getOrDefault("msg-param-mass-gift-count", "1"));
                StreamChat.alert(new StreamChat.Alert(AlertKind.GIFT, Platform.TWITCH, name, "gifted " + n + (n == 1 ? " sub" : " subs") + "!", ""));
            }
            case "raid" -> {
                String who = tags.getOrDefault("msg-param-displayName", name);
                int n = parseInt(tags.getOrDefault("msg-param-viewerCount", "0"));
                StreamChat.alert(new StreamChat.Alert(AlertKind.RAID, Platform.TWITCH, who, "is raiding with " + n + (n == 1 ? " viewer" : " viewers"), ""));
            }
            default -> {}
        }
        // Show the notice itself in chat too, highlighted.
        if (!sys.isEmpty()) StreamChat.add(Platform.TWITCH, "★", 0xFFD34D, sys + (text.isEmpty() ? "" : " · " + text), Role.NONE);
    }

    private static Role role(String badges) {
        if (badges.contains("broadcaster/")) return Role.OWNER;
        if (badges.contains("moderator/")) return Role.MOD;
        if (badges.contains("vip/")) return Role.VIP;
        if (badges.contains("subscriber/") || badges.contains("founder/")) return Role.SUB;
        return Role.NONE;
    }

    private static String unescape(String v) {
        StringBuilder out = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\' && i + 1 < v.length()) {
                char n = v.charAt(++i);
                switch (n) {
                    case 's' -> out.append(' ');
                    case ':' -> out.append(';');
                    case '\\' -> out.append('\\');
                    case 'r' -> out.append('\r');
                    case 'n' -> out.append('\n');
                    default -> out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int parseColour(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) return 0;
        return Integer.parseInt(hex.substring(1), 16);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------ viewers and uptime

    /** Twitch's own API needs an app token, so use DecAPI's public endpoints (plain text) every 30 seconds. */
    private void pollViewers() {
        while (!stopped) {
            try {
                String v = get("https://decapi.me/twitch/viewercount/" + channel);
                viewers = v.matches("\\d+") ? Integer.parseInt(v) : -1;
                if (viewers >= 0) {
                    long up = uptimeMs(get("https://decapi.me/twitch/uptime/" + channel));
                    Streamer.wentLive(up > 0 ? System.currentTimeMillis() - up : 0);
                }
            } catch (Exception e) {
                viewers = -1;
            }
            sleep(30_000);
        }
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", "FlopVisuals").build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body().trim();
    }

    /** "1 hour, 2 minutes, 5 seconds" → milliseconds (0 if offline). */
    static long uptimeMs(String s) {
        Matcher m = Pattern.compile("(\\d+)\\s*(day|hour|minute|second)").matcher(s.toLowerCase(Locale.ROOT));
        long ms = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += n * switch (m.group(2)) {
                case "day" -> 86_400_000L;
                case "hour" -> 3_600_000L;
                case "minute" -> 60_000L;
                default -> 1000L;
            };
        }
        return ms;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
