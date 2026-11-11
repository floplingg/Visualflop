package dev.tony.visuals.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.tony.visuals.Config;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spotify "now playing" through the official Web API.
 * Sign-in uses the PKCE flow: your browser opens Spotify, you approve, Spotify redirects to a tiny
 * local page this mod serves on 127.0.0.1. Only the refresh token is saved (in the mod config).
 */
public final class Spotify {
    private Spotify() {}

    public static final String REDIRECT = "http://127.0.0.1:43821/callback";
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ScheduledExecutorService NET = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "flopvisuals-spotify");
        t.setDaemon(true);
        return t;
    });

    public record Track(String title, String artist, String artUrl, long durationMs, long progressMs, boolean playing, long fetchedAt) {
        public long progressNow() {
            long p = progressMs + (playing ? System.currentTimeMillis() - fetchedAt : 0);
            return Math.max(0, durationMs > 0 ? Math.min(durationMs, p) : p);
        }
    }

    private static volatile Track track;
    private static volatile String status = "Not connected";
    private static volatile Identifier art;
    private static String artFor = "";
    private static String accessToken;
    private static long accessExpires;
    private static ServerSocket server;
    private static boolean started;
    private static long backoffUntil;

    public static Track track() { return track; }

    /** Shows a message in the Status row. */
    public static void note(String message) {
        status = message;
    }
    public static Identifier art() { return art; }

    private static volatile int artColour = -1;

    /** The cover's most vivid colour (0xRRGGBB), or -1 with no cover. */
    public static int artColour() {
        return art == null ? -1 : artColour;
    }

    /** Average of the colourful pixels, weighted by how colourful they are; then lifted so it reads as a glow. */
    private static int vibrant(int[] px) {
        double r = 0, g = 0, b = 0, wsum = 0;
        int step = Math.max(1, px.length / 4096);
        for (int i = 0; i < px.length; i += step) {
            int c = px[i];
            int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
            int mx = Math.max(cr, Math.max(cg, cb)), mn = Math.min(cr, Math.min(cg, cb));
            double sat = mx == 0 ? 0 : (mx - mn) / (double) mx;
            double w = 0.05 + sat * sat * (mx / 255.0);
            r += cr * w; g += cg * w; b += cb * w; wsum += w;
        }
        if (wsum <= 0) return -1;
        float fr = (float) (r / wsum / 255), fg = (float) (g / wsum / 255), fb = (float) (b / wsum / 255);
        float mx = Math.max(fr, Math.max(fg, fb)), mn = Math.min(fr, Math.min(fg, fb)), d = mx - mn;
        float h = d == 0 ? 0 : mx == fr ? ((fg - fb) / d) / 6f : mx == fg ? ((fb - fr) / d + 2) / 6f : ((fr - fg) / d + 4) / 6f;
        float sat = mx == 0 ? 0 : d / mx;
        return dev.tony.visuals.Theme.hsv(h, Math.min(1f, sat * 1.2f + 0.1f), Math.max(0.75f, mx));
    }

    static void setTrack(Track t) {
        track = t;
        if (t == null) {
            art = null;
            artFor = "";
            // Forget which song we were on, so the cover comes back when it resumes (from the cache, or looked up again).
            wantedArt = "";
            artKey = "";
        }
    }

    /** Cover image bytes (PNG or JPEG) from any source. */
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("flopvisuals");

    /** For the automated test: pretend a song is playing with this cover (null = let the iTunes backup find one). */
    public static void testInject(String title, String artist, byte[] cover) {
        songChanged(title, artist);
        setTrack(new Track(title, artist, "", 200_000, 60_000, true, System.currentTimeMillis()));
        if (cover != null) setArt(cover);
    }

    static void setArt(byte[] bytes) {
        String key = wantedArt;
        if (key.isEmpty()) return;
        NET.execute(() -> {
            try {
                if (key.equals(wantedArt)) {
                    uploadArt(bytes, key);
                    artKey = key;
                }
            } catch (Throwable e) {
                LOG.warn("Cover from Windows couldn't be read; looking it up online instead", e);
                lookupCover(key, 0);
            }
        });
    }

    /** Covers already loaded this session, by song, so going back to a song shows its cover instantly. */
    private static final java.util.LinkedHashMap<String, Identifier> COVERS = new java.util.LinkedHashMap<>(32, 0.75f, true);
    private static final Map<String, Integer> COVER_COLOURS = new java.util.HashMap<>();

    private static volatile String wantedArt = "", artKey = "";

    /**
     * Called when the song changes (Windows reader). Shows the cover straight from the cache if we've had it before;
     * otherwise waits briefly for Windows to send it, then looks it up online: Apple Music (several countries,
     * picked by the title's language), then Deezer, retrying a few times if the network hiccups.
     */
    static void songChanged(String title, String artist) {
        String key = title + "\u0001" + artist;
        if (key.equals(wantedArt)) return;
        wantedArt = key;
        Identifier cached;
        synchronized (COVERS) {
            cached = COVERS.get(key);
        }
        if (cached != null) {
            synchronized (COVERS) {
                artColour = COVER_COLOURS.getOrDefault(key, -1);
            }
            art = cached;
            artKey = key;
            return;
        }
        art = null;
        NET.schedule(() -> lookupCover(key, 0), 1500, TimeUnit.MILLISECONDS);
    }

    private static void lookupCover(String key, int attempt) {
        if (!key.equals(wantedArt) || key.equals(artKey)) return;
        String[] parts = key.split("\u0001", 2);
        String title = parts[0], artist = parts.length > 1 ? parts[1] : "";
        try {
            String url = itunesCover(title, artist);
            if (url == null) url = deezerCover(title, artist);
            if (url != null) {
                HttpResponse<byte[]> img = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
                if (img.statusCode() == 200 && key.equals(wantedArt) && !key.equals(artKey)) {
                    uploadArt(img.body(), key);
                    artKey = key;
                    return;
                }
            }
        } catch (Throwable e) {
            LOG.warn("Cover lookup failed (try " + (attempt + 1) + ")", e);
        }
        // Not found or offline for a moment: try again a bit later.
        if (attempt < 3) NET.schedule(() -> lookupCover(key, attempt + 1), 4000L * (attempt + 1), TimeUnit.MILLISECONDS);
    }

    /** "Song (feat. X) - Remastered 2011" -> "Song". */
    private static String cleanTitle(String t) {
        return t.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "").replaceAll("(?i)\\s+-\\s+.*(remaster|version|edit|mix|live|mono|stereo).*$", "").trim();
    }

    private static String firstArtist(String a) {
        return a.split(",| & | x | feat\\.? | ft\\.? ", 2)[0].trim();
    }

    private static String norm(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFKC).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** How well a result matches: 3 = title and artist, 2 = title, 1 = artist. */
    private static int score(String wantTitle, String wantArtist, String title, String artist) {
        String t = norm(cleanTitle(title)), wt = norm(cleanTitle(wantTitle)), a = norm(artist), wa = norm(firstArtist(wantArtist));
        boolean tm = !wt.isEmpty() && (t.equals(wt) || t.contains(wt) || wt.contains(t) && !t.isEmpty());
        boolean am = !wa.isEmpty() && (a.contains(wa) || wa.contains(a) && !a.isEmpty());
        return (tm ? 2 : 0) + (am ? 1 : 0);
    }

    private static String itunesCover(String title, String artist) throws Exception {
        String term = java.net.URLEncoder.encode(cleanTitle(title) + " " + firstArtist(artist), StandardCharsets.UTF_8);
        // The US store first, then the one that matches the title's language.
        java.util.List<String> countries = new java.util.ArrayList<>(java.util.List.of("us"));
        if (title.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL)) countries.add("kr");
        if (title.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
            || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA)) countries.add("jp");
        if (title.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
            countries.add("tw");
            countries.add("cn");
        }
        String fallback = null;
        for (String country : countries) {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://itunes.apple.com/search?media=music&entity=song&limit=10&country=" + country + "&term=" + term))
                .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) continue;
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!root.has("results")) continue;
            int best = 0;
            String url = null;
            for (var e : root.getAsJsonArray("results")) {
                JsonObject o = e.getAsJsonObject();
                if (!o.has("artworkUrl100")) continue;
                int sc = score(title, artist, o.has("trackName") ? o.get("trackName").getAsString() : "",
                    o.has("artistName") ? o.get("artistName").getAsString() : "");
                String u = o.get("artworkUrl100").getAsString().replace("100x100bb", "600x600bb");
                if (sc > best) {
                    best = sc;
                    url = u;
                }
                if (fallback == null && sc >= 1) fallback = u;
            }
            if (best >= 2) return url;
        }
        return fallback;
    }

    private static String deezerCover(String title, String artist) throws Exception {
        String q = java.net.URLEncoder.encode(cleanTitle(title) + " " + firstArtist(artist), StandardCharsets.UTF_8);
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.deezer.com/search?limit=10&q=" + q))
            .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
        JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!root.has("data")) return null;
        String url = null;
        int best = 0;
        for (var e : root.getAsJsonArray("data")) {
            JsonObject o = e.getAsJsonObject();
            if (!o.has("album")) continue;
            JsonObject album = o.getAsJsonObject("album");
            String u = album.has("cover_xl") && !album.get("cover_xl").isJsonNull() ? album.get("cover_xl").getAsString() : null;
            if (u == null) continue;
            int sc = score(title, artist, o.has("title") ? o.get("title").getAsString() : "",
                o.has("artist") ? o.getAsJsonObject("artist").get("name").getAsString() : "");
            if (sc > best) {
                best = sc;
                url = u;
            }
        }
        return best >= 1 ? url : null;
    }

    /** Play/pause ("toggle"), "next" or "previous". The card updates straight away, then catches up with the player. */
    public static void control(String command) {
        Track t = track;
        if (t != null && command.equals("toggle")) {
            track = new Track(t.title(), t.artist(), t.artUrl(), t.durationMs(), t.progressNow(), !t.playing(), System.currentTimeMillis());
        }
        if (windowsMode()) {
            if (!WindowsMedia.send(command)) note("Error: the Windows reader isn't running yet");
            return;
        }
        NET.execute(() -> {
            try {
                if (accessToken == null || System.currentTimeMillis() > accessExpires) refresh();
                String path = switch (command) {
                    case "next" -> "next";
                    case "previous" -> "previous";
                    default -> t != null && t.playing() ? "pause" : "play";
                };
                boolean post = path.equals("next") || path.equals("previous");
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/" + path))
                    .header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(8));
                HttpRequest req = post ? b.POST(HttpRequest.BodyPublishers.noBody()).build() : b.PUT(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 401 || res.statusCode() == 403) status = "Error: reconnect Spotify to allow play/pause and skip";
                NET.schedule(Spotify::poll, 400, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                status = "Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        });
    }

    public static boolean windowsMode() {
        return Config.get().spotify.source == Config.SpotifySource.WINDOWS;
    }

    public static String status() {
        if (windowsMode()) return status.isEmpty() || status.startsWith("Not connected") || status.startsWith("Waiting") ? "Starting…" : status;
        if (Config.get().spotify.refreshToken.isEmpty() && !status.startsWith("Waiting") && !status.startsWith("Error") && !status.startsWith("Client")) return "Not connected";
        return status;
    }

    public static void start() {
        if (started) return;
        started = true;
        NET.scheduleWithFixedDelay(Spotify::poll, 2, 3, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------ sign in

    public static synchronized void connect() {
        Config.Spotify cfg = Config.get().spotify;
        if (cfg.clientId.isBlank()) {
            status = "Error: paste your Client ID first";
            return;
        }
        try {
            String verifier = randomString(64);
            String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            String state = randomString(16);
            stopServer();
            // A tiny one-shot web page on 127.0.0.1 for Spotify to send you back to (plain sockets, works on any Java).
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            try {
                ss.bind(new InetSocketAddress("127.0.0.1", 43821));
            } catch (java.io.IOException e) {
                ss.close();
                status = "Error: port 43821 is busy (close other launchers or restart Minecraft)";
                return;
            }
            server = ss;
            Thread t = new Thread(() -> serve(ss, state, verifier), "flopvisuals-spotify-login");
            t.setDaemon(true);
            t.start();
            String url = "https://accounts.spotify.com/authorize?client_id=" + enc(cfg.clientId)
                + "&response_type=code&redirect_uri=" + enc(REDIRECT)
                + "&code_challenge_method=S256&code_challenge=" + enc(challenge)
                + "&scope=" + enc(SCOPES) + "&state=" + enc(state);
            status = "Waiting for browser…";
            Util.getPlatform().openUri(url);
        } catch (Throwable e) {
            status = "Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** Waits up to 5 minutes for Spotify's redirect, answers it with a small page, then closes. */
    private static void serve(ServerSocket ss, String state, String verifier) {
        try {
            ss.setSoTimeout(300_000);
            while (!ss.isClosed()) {
                try (Socket sock = ss.accept()) {
                    sock.setSoTimeout(10_000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                    String line = in.readLine();   // GET /callback?code=...&state=... HTTP/1.1
                    if (line == null) continue;
                    String[] parts = line.split(" ");
                    String target = parts.length > 1 ? parts[1] : "/";
                    if (!target.startsWith("/callback")) {
                        respond(sock, 404, "Not found", "");
                        continue;
                    }
                    int qi = target.indexOf('?');
                    Map<String, String> q = query(qi >= 0 ? target.substring(qi + 1) : "");
                    boolean ok = state.equals(q.get("state")) && q.containsKey("code");
                    String err = q.getOrDefault("error", "");
                    respond(sock, 200, ok ? "Connected to Flop Visuals" : "Spotify sign-in failed",
                        ok ? "You can close this tab and go back to Minecraft." : "Close this tab and try Connect again." + (err.isEmpty() ? "" : " (" + err + ")"));
                    if (ok) NET.execute(() -> exchange(q.get("code"), verifier));
                    else status = err.isEmpty() ? "Error: sign-in was cancelled" : "Error: " + err;
                    break;
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            if (status.startsWith("Waiting")) status = "Error: sign-in timed out, try Connect again";
        } catch (Exception e) {
            if (status.startsWith("Waiting")) status = "Error: " + e.getMessage();
        } finally {
            stopServer();
        }
    }

    private static void respond(Socket sock, int code, String title, String text) throws java.io.IOException {
        byte[] body = (code == 200 ? page(title, text) : title).getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
            + body.length + "\r\nConnection: close\r\n\r\n";
        OutputStream os = sock.getOutputStream();
        os.write(head.getBytes(StandardCharsets.US_ASCII));
        os.write(body);
        os.flush();
    }

    public static synchronized void disconnect() {
        Config.get().spotify.refreshToken = "";
        Config.save();
        accessToken = null;
        track = null;
        status = "Not connected";
    }

    private static synchronized void stopServer() {
        if (server != null) {
            try {
                server.close();
            } catch (java.io.IOException ignored) {
            }
            server = null;
        }
    }

    private static void exchange(String code, String verifier) {
        try {
            JsonObject json = token("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
                + "&client_id=" + enc(Config.get().spotify.clientId) + "&code_verifier=" + enc(verifier));
            accept(json);
            status = "Connected";
            poll();
        } catch (Exception e) {
            status = "Error: " + e.getMessage();
        }
    }

    private static void refresh() throws Exception {
        Config.Spotify cfg = Config.get().spotify;
        JsonObject json = token("grant_type=refresh_token&refresh_token=" + enc(cfg.refreshToken) + "&client_id=" + enc(cfg.clientId));
        accept(json);
    }

    private static void accept(JsonObject json) {
        accessToken = json.get("access_token").getAsString();
        accessExpires = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000 - 30_000;
        if (json.has("refresh_token")) {
            Config.get().spotify.refreshToken = json.get("refresh_token").getAsString();
            Minecraft.getInstance().execute(Config::save);
        }
    }

    private static JsonObject token(String form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject json;
        try {
            json = JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception("Spotify sign-in answered HTTP " + res.statusCode());
        }
        if (res.statusCode() != 200) {
            String err = json.has("error_description") ? json.get("error_description").getAsString() : "HTTP " + res.statusCode();
            if (res.statusCode() == 400 && json.has("error") && json.get("error").getAsString().equals("invalid_grant")) {
                Config.get().spotify.refreshToken = "";
            }
            throw new Exception(err);
        }
        return json;
    }

    // ------------------------------------------------------------ now playing

    private static void poll() {
        try {
            Config c = Config.get();
            if (!c.widget("spotify").enabled && !c.widget("lyrics").enabled) {
                WindowsMedia.stop();
                return;
            }
            if (windowsMode()) {
                WindowsMedia.ensure();
                return;
            }
            WindowsMedia.stop();
            if (c.spotify.refreshToken.isEmpty() || c.spotify.clientId.isEmpty()) return;
            if (System.currentTimeMillis() < backoffUntil) return;
            if (accessToken == null || System.currentTimeMillis() > accessExpires) refresh();

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing?additional_types=track,episode"))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + accessToken).GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            switch (res.statusCode()) {
                case 200 -> read(JsonParser.parseString(res.body()).getAsJsonObject());
                case 204 -> {
                    track = null;
                    status = "Connected · nothing playing";
                }
                case 401 -> accessToken = null;
                case 403 -> status = "Error: Spotify refused (add your account under User Management in the dashboard)";
                case 429 -> {
                    long wait = res.headers().firstValueAsLong("Retry-After").orElse(10);
                    backoffUntil = System.currentTimeMillis() + wait * 1000;
                }
                default -> status = "Error: Spotify HTTP " + res.statusCode();
            }
        } catch (Throwable e) {
            status = "Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static void read(JsonObject json) {
        JsonElement itemEl = json.get("item");
        if (itemEl == null || itemEl.isJsonNull()) {
            track = null;
            status = "Connected · nothing playing";
            return;
        }
        JsonObject item = itemEl.getAsJsonObject();
        String title = item.get("name").getAsString();
        StringBuilder artist = new StringBuilder();
        String artUrl = "";
        if (item.has("artists")) {
            for (JsonElement a : item.getAsJsonArray("artists")) {
                if (!artist.isEmpty()) artist.append(", ");
                artist.append(a.getAsJsonObject().get("name").getAsString());
            }
            artUrl = pickImage(item.getAsJsonObject("album").getAsJsonArray("images"));
        } else if (item.has("show")) {
            artist.append(item.getAsJsonObject("show").get("name").getAsString());
            artUrl = pickImage(item.getAsJsonArray("images"));
        }
        long duration = item.get("duration_ms").getAsLong();
        long progress = json.has("progress_ms") && !json.get("progress_ms").isJsonNull() ? json.get("progress_ms").getAsLong() : 0;
        boolean playing = json.get("is_playing").getAsBoolean();
        track = new Track(title, artist.toString(), artUrl, duration, progress, playing, System.currentTimeMillis());
        status = playing ? "Playing" : "Paused";
        if (!artUrl.equals(artFor)) {
            artFor = artUrl;
            loadArt(artUrl);
        }
    }

    private static String pickImage(JsonArray images) {
        if (images == null || images.isEmpty()) return "";
        String best = images.get(0).getAsJsonObject().get("url").getAsString();
        for (JsonElement e : images) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("width") && !o.get("width").isJsonNull() && o.get("width").getAsInt() >= 200) best = o.get("url").getAsString();
        }
        return best;
    }

    /** Downloads the cover, rounds its corners and uploads it as a texture. JPEG decoding needs Java's ImageIO. */
    private static void loadArt(String url) {
        if (url.isEmpty()) {
            art = null;
            return;
        }
        try {
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            uploadArt(res.body(), url);
        } catch (Throwable t) {
            art = null;   // no ImageIO, network hiccup: just show the placeholder
        }
    }

    /** Rounds the cover's corners and uploads it as a smooth texture. */
    /**
     * Decodes a JPEG or PNG cover with stb (bundled with Minecraft, no Java desktop imaging needed), scales it to
     * 128 x 128 with smooth supersampling, rounds the corners and uploads it.
     */
    private static int artCounter;

    private static void uploadArt(byte[] bytes, String key) throws java.io.IOException {
        int sw, sh;
        int[] srcPx;
        java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
        try (org.lwjgl.system.MemoryStack st = org.lwjgl.system.MemoryStack.stackPush()) {
            buf.put(bytes).flip();
            java.nio.IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), c = st.mallocInt(1);
            java.nio.ByteBuffer px = org.lwjgl.stb.STBImage.stbi_load_from_memory(buf, w, h, c, 4);
            if (px == null) throw new java.io.IOException("cover image: " + org.lwjgl.stb.STBImage.stbi_failure_reason());
            sw = w.get(0);
            sh = h.get(0);
            srcPx = new int[sw * sh];
            for (int k = 0; k < srcPx.length; k++) {
                int r = px.get(k * 4) & 255, g = px.get(k * 4 + 1) & 255, b = px.get(k * 4 + 2) & 255;
                srcPx[k] = (r << 16) | (g << 8) | b;
            }
            org.lwjgl.stb.STBImage.stbi_image_free(px);
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(buf);
        }
        int vib = vibrant(srcPx);
        {
            int size = 128;
            // Centre-crop to a square, then 4 x 4 supersampled bilinear scaling.
            int side = Math.min(sw, sh), ox = (sw - side) / 2, oy = (sh - side) / 2;
            NativeImage img = new NativeImage(size, size, true);
            float radius = 22;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float rr = 0, gg = 0, bb = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            float fx = ox + (x + (sx + 0.5f) / 4f) * side / size - 0.5f;
                            float fy = oy + (y + (sy + 0.5f) / 4f) * side / size - 0.5f;
                            int x0 = Math.max(0, Math.min(sw - 1, (int) Math.floor(fx))), y0 = Math.max(0, Math.min(sh - 1, (int) Math.floor(fy)));
                            int x1 = Math.min(sw - 1, x0 + 1), y1 = Math.min(sh - 1, y0 + 1);
                            float tx = Math.max(0, Math.min(1, fx - x0)), ty = Math.max(0, Math.min(1, fy - y0));
                            int a = srcPx[y0 * sw + x0], b2 = srcPx[y0 * sw + x1], c2 = srcPx[y1 * sw + x0], d = srcPx[y1 * sw + x1];
                            for (int ch = 0; ch < 3; ch++) {
                                int sh2 = 16 - ch * 8;
                                float top = ((a >> sh2) & 255) * (1 - tx) + ((b2 >> sh2) & 255) * tx;
                                float bot = ((c2 >> sh2) & 255) * (1 - tx) + ((d >> sh2) & 255) * tx;
                                float v = top * (1 - ty) + bot * ty;
                                if (ch == 0) rr += v; else if (ch == 1) gg += v; else bb += v;
                            }
                        }
                    }
                    int rgb = (Math.round(rr / 16) << 16) | (Math.round(gg / 16) << 8) | Math.round(bb / 16);
                    float cx = Math.max(radius - x - 0.5f, Math.max(0, x + 0.5f - (size - radius)));
                    float cy = Math.max(radius - y - 0.5f, Math.max(0, y + 0.5f - (size - radius)));
                    float dd = (float) Math.sqrt(cx * cx + cy * cy);
                    float al = (cx > 0 && cy > 0) ? Math.max(0, Math.min(1, radius - dd + 0.5f)) : 1;
                    img.setPixel(x, y, (Math.round(al * 255) << 24) | rgb);
                }
            }
            Minecraft.getInstance().execute(() -> {
                Identifier id = Identifier.fromNamespaceAndPath("flopvisuals", "spotify/art_" + (artCounter++));
                DynamicTexture tex = new DynamicTexture(() -> "flopvisuals spotify art", img) {
                    {
                        this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                            .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
                    }
                };
                Minecraft.getInstance().getTextureManager().register(id, tex);
                synchronized (COVERS) {
                    COVERS.put(key, id);
                    while (COVERS.size() > 30) {
                        var it = COVERS.entrySet().iterator();
                        Identifier old = it.next().getValue();
                        it.remove();
                        if (!old.equals(art)) Minecraft.getInstance().getTextureManager().release(old);
                    }
                }
                // Only show it if it's still the song playing (the Web API passes its image URL as the key).
                synchronized (COVERS) {
                    COVER_COLOURS.put(key, vib);
                }
                if (key.equals(wantedArt) || key.equals(artFor)) {
                    artColour = vib;
                    art = id;
                }
            });
        }
    }

    // ------------------------------------------------------------ helpers

    private static String randomString(int n) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i > 0) out.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String page(String title, String text) {
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width'><title>" + title
            + "</title><style>body{margin:0;height:100vh;display:grid;place-items:center;background:#111114;color:#f2f2f7;"
            + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Inter,sans-serif}div{padding:28px 36px;border-radius:18px;"
            + "background:#1c1c1e;box-shadow:0 10px 40px #0008;text-align:center}h1{font-size:20px;margin:0 0 8px}"
            + "p{margin:0;color:#98989f}</style></head><body><div><h1>" + title + "</h1><p>" + text + "</p></div></body></html>";
    }
}
