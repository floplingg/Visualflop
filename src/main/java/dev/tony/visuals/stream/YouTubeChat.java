package dev.tony.visuals.stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tony.visuals.stream.StreamChat.AlertKind;
import dev.tony.visuals.stream.StreamChat.Platform;
import dev.tony.visuals.stream.StreamChat.Role;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a YouTube live stream's chat the way the pop-out chat window does: load the chat page once,
 * then keep asking YouTube's web endpoint for what's new. No API key or account of yours is needed.
 */
final class YouTubeChat {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final String source;
    private volatile boolean stopped;
    private volatile String status = "Looking for the stream…";
    private volatile int viewers = -1;

    private String videoId, apiKey, clientVersion = "2.20241120.01.00";

    private YouTubeChat(String source) {
        this.source = source;
    }

    static YouTubeChat start(String source) {
        YouTubeChat y = new YouTubeChat(source);
        Thread t = new Thread(y::run, "Flop Visuals YouTube chat");
        t.setDaemon(true);
        t.start();
        return y;
    }

    String source() {
        return source;
    }

    String status() {
        return status;
    }

    int viewers() {
        return viewers;
    }

    void stop() {
        stopped = true;
    }

    // ------------------------------------------------------------ main loop

    private void run() {
        while (!stopped) {
            try {
                videoId = findLiveVideo(source);
                if (videoId == null) {
                    status = "Not live right now (checking every minute)";
                    viewers = -1;
                    sleep(60_000);
                    continue;
                }
                String continuation = openChat(videoId);
                if (continuation == null) {
                    status = "This stream has no live chat";
                    sleep(60_000);
                    continue;
                }
                status = "Connected";
                long nextViewers = 0;
                while (!stopped && continuation != null) {
                    if (System.currentTimeMillis() >= nextViewers) {
                        nextViewers = System.currentTimeMillis() + 30_000;
                        pollViewers();
                    }
                    JsonObject res = post("live_chat/get_live_chat", "\"continuation\":" + quote(continuation));
                    JsonObject lcc = obj(obj(res, "continuationContents"), "liveChatContinuation");
                    if (lcc == null) break;   // the stream ended
                    JsonArray actions = lcc.has("actions") ? lcc.getAsJsonArray("actions") : new JsonArray();
                    for (JsonElement a : actions) action(a.getAsJsonObject());
                    continuation = null;
                    long wait = 3000;
                    JsonArray conts = lcc.has("continuations") ? lcc.getAsJsonArray("continuations") : new JsonArray();
                    for (JsonElement c : conts) {
                        for (String kind : new String[]{"invalidationContinuationData", "timedContinuationData", "reloadContinuationData"}) {
                            JsonObject d = obj(c.getAsJsonObject(), kind);
                            if (d != null && d.has("continuation")) {
                                continuation = d.get("continuation").getAsString();
                                if (d.has("timeoutMs")) wait = d.get("timeoutMs").getAsLong();
                            }
                        }
                    }
                    sleep(Math.max(1000, Math.min(8000, wait)));
                }
                status = "Stream ended (checking every minute)";
                viewers = -1;
                sleep(60_000);
            } catch (Exception e) {
                status = "Retrying… (" + e.getClass().getSimpleName() + ")";
                sleep(15_000);
            }
        }
        status = "Disconnected";
    }

    // ------------------------------------------------------------ finding the stream

    private static final Pattern VIDEO = Pattern.compile("(?:v=|youtu\\.be/|/live/|/shorts/)([A-Za-z0-9_-]{11})");

    /** A live video id for a link, @handle or channel; null when the channel isn't live. */
    private String findLiveVideo(String in) throws Exception {
        String s = in.trim();
        Matcher m = VIDEO.matcher(s);
        if (m.find()) return m.group(1);
        if (s.matches("[A-Za-z0-9_-]{11}") && !s.startsWith("@")) return s;
        String channel;
        if (s.contains("youtube.com/")) {
            channel = s.replaceAll("^(https?://)?(www\\.|m\\.)?youtube\\.com", "https://www.youtube.com").replaceAll("[?#].*$", "").replaceAll("/+$", "");
            channel = channel.replaceAll("/(live|streams|videos|featured)$", "");
        } else if (s.startsWith("UC") && s.length() == 24) {
            channel = "https://www.youtube.com/channel/" + s;
        } else {
            channel = "https://www.youtube.com/@" + s.replaceAll("^@", "");
        }
        String html = page(channel + "/live");
        // A channel that's live redirects /live to its stream; the canonical link names the video.
        Matcher c = Pattern.compile("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})\"").matcher(html);
        if (!c.find()) return null;
        if (!html.contains("\"isLive\":true") && !html.contains("\"isLiveNow\":true")) return null;
        return c.group(1);
    }

    /** Loads the pop-out chat once for the API key, client version and the first continuation. */
    private String openChat(String id) throws Exception {
        String html = page("https://www.youtube.com/live_chat?is_popout=1&v=" + id);
        Matcher k = Pattern.compile("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").matcher(html);
        apiKey = k.find() ? k.group(1) : null;
        Matcher v = Pattern.compile("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").matcher(html);
        if (v.find()) clientVersion = v.group(1);
        int at = html.indexOf("\"liveChatRenderer\"");
        if (at < 0) return null;
        Matcher c = Pattern.compile("\"continuation\":\"([^\"]+)\"").matcher(html);
        return c.find(at) ? c.group(1) : null;
    }

    private void pollViewers() {
        try {
            JsonObject res = post("updated_metadata", "\"videoId\":" + quote(videoId));
            JsonArray actions = res.has("actions") ? res.getAsJsonArray("actions") : new JsonArray();
            for (JsonElement a : actions) {
                JsonObject vc = obj(obj(obj(a.getAsJsonObject(), "updateViewershipAction"), "viewCount"), "videoViewCountRenderer");
                if (vc == null) continue;
                String n = vc.has("originalViewCount") ? vc.get("originalViewCount").getAsString() : text(obj(vc, "viewCount"));
                n = n.replaceAll("[^0-9]", "");
                if (!n.isEmpty()) viewers = Integer.parseInt(n);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ chat items

    private void action(JsonObject a) {
        JsonObject add = obj(a, "addChatItemAction");
        if (add == null) return;
        JsonObject item = obj(add, "item");
        if (item == null) return;
        JsonObject r;
        if ((r = obj(item, "liveChatTextMessageRenderer")) != null) {
            StreamChat.add(Platform.YOUTUBE, text(obj(r, "authorName")), 0, text(obj(r, "message")), role(r));
        } else if ((r = obj(item, "liveChatPaidMessageRenderer")) != null) {
            String name = text(obj(r, "authorName"));
            String amount = text(obj(r, "purchaseAmountText"));
            String msg = text(obj(r, "message"));
            StreamChat.alert(new StreamChat.Alert(AlertKind.SUPERCHAT, Platform.YOUTUBE, name, "sent a " + amount + " Super Chat", msg));
            StreamChat.add(Platform.YOUTUBE, "★ " + name, 0xFFD34D, amount + (msg.isEmpty() ? "" : " · " + msg), role(r));
        } else if ((r = obj(item, "liveChatPaidStickerRenderer")) != null) {
            String name = text(obj(r, "authorName"));
            String amount = text(obj(r, "purchaseAmountText"));
            StreamChat.alert(new StreamChat.Alert(AlertKind.SUPERCHAT, Platform.YOUTUBE, name, "sent a " + amount + " Super Sticker", ""));
        } else if ((r = obj(item, "liveChatMembershipItemRenderer")) != null) {
            String name = text(obj(r, "authorName"));
            String head = text(obj(r, "headerPrimaryText"));
            if (head.isEmpty()) head = text(obj(r, "headerSubtext"));
            StreamChat.alert(new StreamChat.Alert(AlertKind.MEMBER, Platform.YOUTUBE, name, head.isEmpty() ? "became a member" : head, text(obj(r, "message"))));
        } else if ((r = obj(item, "liveChatSponsorshipsGiftPurchaseAnnouncementRenderer")) != null) {
            JsonObject h = obj(obj(r, "header"), "liveChatSponsorshipsHeaderRenderer");
            String name = text(obj(h, "authorName"));
            StreamChat.alert(new StreamChat.Alert(AlertKind.GIFT, Platform.YOUTUBE, name, text(obj(h, "primaryText")), ""));
        }
    }

    private static Role role(JsonObject r) {
        if (!r.has("authorBadges")) return Role.NONE;
        Role out = Role.NONE;
        for (JsonElement b : r.getAsJsonArray("authorBadges")) {
            JsonObject br = obj(b.getAsJsonObject(), "liveChatAuthorBadgeRenderer");
            if (br == null) continue;
            String icon = obj(br, "icon") != null && obj(br, "icon").has("iconType") ? obj(br, "icon").get("iconType").getAsString() : "";
            if (icon.equals("OWNER")) return Role.OWNER;
            if (icon.equals("MODERATOR")) out = Role.MOD;
            else if (out == Role.NONE && br.has("customThumbnail")) out = Role.SUB;   // member badge
        }
        return out;
    }

    /** simpleText, or runs of text and emoji joined together. */
    private static String text(JsonObject o) {
        if (o == null) return "";
        if (o.has("simpleText")) return o.get("simpleText").getAsString();
        if (!o.has("runs")) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : o.getAsJsonArray("runs")) {
            JsonObject run = e.getAsJsonObject();
            if (run.has("text")) {
                sb.append(run.get("text").getAsString());
            } else if (run.has("emoji")) {
                JsonObject em = run.getAsJsonObject("emoji");
                String id = em.has("emojiId") ? em.get("emojiId").getAsString() : "";
                boolean custom = em.has("isCustomEmoji") && em.get("isCustomEmoji").getAsBoolean();
                if (!custom && !id.isEmpty() && id.codePointCount(0, id.length()) <= 8) sb.append(id);   // a real emoji character
                else if (em.has("shortcuts") && !em.getAsJsonArray("shortcuts").isEmpty()) sb.append(em.getAsJsonArray("shortcuts").get(0).getAsString());
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ http

    private String page(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
            .header("User-Agent", UA).header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "CONSENT=YES+cb; SOCS=CAI").build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) throw new java.io.IOException("HTTP " + r.statusCode());
        return r.body();
    }

    private JsonObject post(String endpoint, String fields) throws Exception {
        String body = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":" + quote(clientVersion) + ",\"hl\":\"en\"}}," + fields + "}";
        String url = "https://www.youtube.com/youtubei/v1/" + endpoint + "?prettyPrint=false" + (apiKey == null ? "" : "&key=" + apiKey);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
            .header("User-Agent", UA).header("Content-Type", "application/json")
            .header("X-Youtube-Client-Name", "1").header("X-Youtube-Client-Version", clientVersion)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) throw new java.io.IOException("HTTP " + r.statusCode());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private static JsonObject obj(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonObject()) return null;
        return o.getAsJsonObject(key);
    }

    private static String quote(String s) {
        return new com.google.gson.Gson().toJson(s);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
