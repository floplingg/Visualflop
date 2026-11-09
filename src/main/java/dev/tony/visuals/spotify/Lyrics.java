package dev.tony.visuals.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tony.visuals.Config;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-synced lyrics for the song you're playing. Sources, in order: LRCLIB (lrclib.net, a free community database),
 * then NetEase Cloud Music (very wide catalogue, strong on Asian and new releases), then, if only plain lyrics exist,
 * plain lyrics with estimated timing. Looked up once per song in the background and cached.
 */
public final class Lyrics {
    private Lyrics() {}

    public record Line(long timeMs, String text) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final ExecutorService NET = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "flopvisuals-lyrics");
        t.setDaemon(true);
        return t;
    });
    private static final String AGENT = "FlopVisuals (https://github.com/floplingg/Visualflop)";
    private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");

    /** A few recent songs, so skipping back and forth doesn't look them up again. */
    private static final Map<String, List<Line>> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Line>> e) {
            return size() > 24;
        }
    };

    private static volatile String forKey = "";
    private static volatile List<Line> lines = List.of();
    private static volatile String status = "";
    private static volatile String source = "";

    /** Where the current lyrics came from (shown in the menu). */
    public static String source() {
        return source;
    }

    public static String status() {
        return status;
    }

    public static List<Line> lines() {
        return lines;
    }

    /** Index of the line being sung now, or -1 before the first line. */
    public static int current(long progressMs) {
        List<Line> l = lines;
        long t = progressMs + Config.get().lyrics.offsetMs;
        int idx = -1;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).timeMs() <= t) idx = i;
            else break;
        }
        return idx;
    }

    /** Called every frame the widget draws; starts a lookup when the song changes. */
    public static void follow(Spotify.Track track) {
        if (track == null) return;
        String key = track.title() + "\u0001" + track.artist();
        if (key.equals(forKey)) return;
        forKey = key;
        synchronized (CACHE) {
            List<Line> cached = CACHE.get(key);
            if (cached != null) {
                lines = cached;
                status = cached.isEmpty() ? "No lyrics found for this song" : "";
                return;
            }
        }
        lines = List.of();
        status = "Finding lyrics…";
        String title = track.title(), artist = track.artist();
        long duration = track.durationMs();
        NET.execute(() -> {
            List<Line> found = List.of();
            String why = "No lyrics found for this song";
            String from = "";
            boolean reached = false;
            plainFallback = null;
            try {
                found = lookup(title, artist, duration);
                reached = true;
                if (found == null) {
                    found = List.of();
                    why = "Instrumental";
                } else if (!found.isEmpty()) {
                    from = "LRCLIB";
                }
            } catch (Exception ignored) {
                // try the next source
            }
            if (found.isEmpty() && !why.equals("Instrumental")) {
                try {
                    List<Line> ne = netease(title, artist, duration);
                    reached = true;
                    if (ne == null) why = "Instrumental";
                    else if (!ne.isEmpty()) {
                        found = ne;
                        from = "NetEase";
                    }
                } catch (Exception ignored) {
                    // fall through
                }
            }
            if (found.isEmpty() && !why.equals("Instrumental") && plainFallback != null && duration > 0 && Config.get().lyrics.approximate) {
                found = approximate(plainFallback, duration);
                from = "estimated timing";
            }
            if (!reached && found.isEmpty()) why = "Couldn't reach the lyrics services";
            String finalFrom = from;
            synchronized (CACHE) {
                if (!why.startsWith("Couldn't")) CACHE.put(key, found);
            }
            if (key.equals(forKey)) {
                lines = found;
                source = finalFrom;
                status = found.isEmpty() ? why : "";
            }
        });
    }

    /** Synced lines, an empty list when only plain or no lyrics exist, or null for instrumentals. */
    private static List<Line> lookup(String title, String artist, long durationMs) throws Exception {
        String clean = cleanTitle(title);
        String firstArtist = artist.split(",| & | feat\\. | ft\\. ")[0].trim();
        // Exact match first (best when the length matches), then a search.
        if (durationMs > 0) {
            String q = "track_name=" + enc(clean) + "&artist_name=" + enc(firstArtist) + "&duration=" + Math.round(durationMs / 1000.0);
            JsonObject o = getObject("https://lrclib.net/api/get?" + q);
            if (o != null) {
                if (o.has("instrumental") && o.get("instrumental").getAsBoolean()) return null;
                String synced = str(o, "syncedLyrics");
                if (!synced.isEmpty()) return parse(synced);
                if (!str(o, "plainLyrics").isEmpty()) plainFallback = str(o, "plainLyrics");
            }
        }
        JsonArray results = getArray("https://lrclib.net/api/search?track_name=" + enc(clean) + "&artist_name=" + enc(firstArtist));
        if (results == null || results.isEmpty()) results = getArray("https://lrclib.net/api/search?q=" + enc(clean + " " + firstArtist));
        if (results == null) return List.of();
        JsonObject best = null;
        double bestScore = Double.MAX_VALUE;
        boolean anyInstrumental = false;
        for (JsonElement e : results) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("instrumental") && o.get("instrumental").getAsBoolean()) anyInstrumental = true;
            if (str(o, "syncedLyrics").isEmpty()) {
                double dd = o.has("duration") && durationMs > 0 ? Math.abs(o.get("duration").getAsDouble() - durationMs / 1000.0) : 99;
                if (plainFallback == null && dd <= 4 && !str(o, "plainLyrics").isEmpty()) plainFallback = str(o, "plainLyrics");
                continue;
            }
            double d = o.has("duration") && durationMs > 0 ? Math.abs(o.get("duration").getAsDouble() - durationMs / 1000.0) : 5;
            if (d < bestScore) {
                bestScore = d;
                best = o;
            }
        }
        if (best != null && bestScore <= 8) return parse(str(best, "syncedLyrics"));
        return anyInstrumental ? null : List.of();
    }

    private static volatile String plainFallback;

    // ------------------------------------------------------------ NetEase Cloud Music

    private static final Pattern CREDIT = Pattern.compile("^(作词|作曲|编曲|制作人|混音|母带|和声|吉他|贝斯|鼓|录音|监制|出品|Lyricist|Composer|Arranger|Producer|Lyrics by|Composed by|Written by)\\s*[:：]", Pattern.CASE_INSENSITIVE);

    /** Synced lines from NetEase, empty when nothing matched, null for instrumentals. */
    private static List<Line> netease(String title, String artist, long durationMs) throws Exception {
        String clean = cleanTitle(title);
        String firstArtist = artist.split(",| & | feat\\. | ft\\. ")[0].trim();
        String body = getNe("https://music.163.com/api/search/get/web?type=1&limit=12&s=" + enc(clean + " " + firstArtist));
        if (body == null) return List.of();
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("result") || !root.getAsJsonObject("result").has("songs")) return List.of();
        long bestId = -1;
        double bestScore = Double.MAX_VALUE;
        String wantTitle = norm(clean), wantArtist = norm(firstArtist);
        for (JsonElement e : root.getAsJsonObject("result").getAsJsonArray("songs")) {
            JsonObject song = e.getAsJsonObject();
            String name = norm(str(song, "name"));
            StringBuilder artists = new StringBuilder();
            if (song.has("artists")) for (JsonElement a : song.getAsJsonArray("artists")) artists.append(norm(str(a.getAsJsonObject(), "name"))).append(' ');
            boolean titleOk = name.contains(wantTitle) || wantTitle.contains(name);
            boolean artistOk = wantArtist.isEmpty() || artists.toString().contains(wantArtist) || wantArtist.contains(artists.toString().trim());
            if (!titleOk || !artistOk) continue;
            double d = song.has("duration") && durationMs > 0 ? Math.abs(song.get("duration").getAsLong() - durationMs) / 1000.0 : 3;
            if (d < bestScore) {
                bestScore = d;
                bestId = song.get("id").getAsLong();
            }
        }
        if (bestId < 0 || bestScore > 6) return List.of();
        String lyr = getNe("https://music.163.com/api/song/lyric?lv=1&kv=1&tv=-1&id=" + bestId);
        if (lyr == null) return List.of();
        JsonObject lo = JsonParser.parseString(lyr).getAsJsonObject();
        if (lo.has("nolyric") && lo.get("nolyric").getAsBoolean()) return null;
        if (!lo.has("lrc")) return List.of();
        String lrc = str(lo.getAsJsonObject("lrc"), "lyric");
        if (lrc.contains("纯音乐，请欣赏") || lrc.contains("纯音乐, 请欣赏")) return null;
        List<Line> out = new ArrayList<>();
        for (Line l : parse(lrc)) if (!CREDIT.matcher(l.text()).find()) out.add(l);
        // Lyrics with every line at 0:00 aren't really synced.
        if (out.size() > 3 && out.get(out.size() - 1).timeMs() == 0) return List.of();
        return List.copyOf(out);
    }

    private static String norm(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String getNe(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) FlopVisuals")
            .header("Referer", "https://music.163.com/")
            .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 ? res.body() : null;
    }

    /** Spreads plain lyrics over the song by line length (marked as estimated in the menu). */
    private static List<Line> approximate(String plain, long durationMs) {
        List<String> text = new ArrayList<>();
        for (String l : plain.split("\\r?\\n")) text.add(l.trim());
        while (!text.isEmpty() && text.get(0).isEmpty()) text.remove(0);
        if (text.isEmpty()) return List.of();
        long start = Math.round(durationMs * 0.07), end = Math.round(durationMs * 0.93);
        double total = 0;
        for (String l : text) total += l.isEmpty() ? 6 : 10 + l.length();
        List<Line> out = new ArrayList<>();
        double at = start;
        for (String l : text) {
            out.add(new Line(Math.round(at), l));
            at += (end - start) * ((l.isEmpty() ? 6 : 10 + l.length()) / total);
        }
        return List.copyOf(out);
    }

    /** Drops "(Remastered 2011)", "- Live", "[Official Video]" and the like that stop a match. */
    static String cleanTitle(String t) {
        String s = t.replaceAll("\\s*[\\(\\[](?i:[^\\)\\]]*(remaster|live|version|edit|mix|official|video|audio|lyrics|feat|ft\\.)[^\\)\\]]*)[\\)\\]]", "");
        s = s.replaceAll("\\s+-\\s+(?i:.*(remaster|live|version|edit|mix)).*$", "");
        return s.trim().isEmpty() ? t : s.trim();
    }

    static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TAG.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long ms = Long.parseLong(m.group(1)) * 60_000L + Math.round(Double.parseDouble(m.group(2)) * 1000);
                times.add(ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long ms : times) out.add(new Line(ms, text));
        }
        out.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
        return List.copyOf(out);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", AGENT).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) return null;
        if (res.statusCode() != 200) throw new Exception("HTTP " + res.statusCode());
        return res.body();
    }

    private static JsonObject getObject(String url) throws Exception {
        String body = get(url);
        return body == null ? null : JsonParser.parseString(body).getAsJsonObject();
    }

    private static JsonArray getArray(String url) throws Exception {
        String body = get(url);
        return body == null ? null : JsonParser.parseString(body).getAsJsonArray();
    }
}
