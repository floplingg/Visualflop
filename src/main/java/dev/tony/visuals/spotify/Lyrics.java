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
 * Time-synced lyrics for the song you're playing, from LRCLIB (lrclib.net, a free public lyrics database, no key needed).
 * Looked up once per song in the background and cached; the current line follows the song's progress.
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
                status = cached.isEmpty() ? "No synced lyrics for this song" : "";
                return;
            }
        }
        lines = List.of();
        status = "Finding lyrics…";
        String title = track.title(), artist = track.artist();
        long duration = track.durationMs();
        NET.execute(() -> {
            List<Line> found = List.of();
            String why = "No synced lyrics for this song";
            try {
                found = lookup(title, artist, duration);
                if (found == null) {
                    found = List.of();
                    why = "Instrumental";
                }
            } catch (Exception e) {
                why = "Couldn't reach the lyrics service";
            }
            synchronized (CACHE) {
                if (!why.startsWith("Couldn't")) CACHE.put(key, found);
            }
            if (key.equals(forKey)) {
                lines = found;
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
            if (str(o, "syncedLyrics").isEmpty()) continue;
            double d = o.has("duration") && durationMs > 0 ? Math.abs(o.get("duration").getAsDouble() - durationMs / 1000.0) : 5;
            if (d < bestScore) {
                bestScore = d;
                best = o;
            }
        }
        if (best != null && bestScore <= 8) return parse(str(best, "syncedLyrics"));
        return anyInstrumental ? null : List.of();
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
