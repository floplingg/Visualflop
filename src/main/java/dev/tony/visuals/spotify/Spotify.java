package dev.tony.visuals.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.sun.net.httpserver.HttpServer;
import dev.tony.visuals.Config;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
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
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state";
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
            return Math.max(0, Math.min(durationMs, p));
        }
    }

    private static volatile Track track;
    private static volatile String status = "Not connected";
    private static volatile Identifier art;
    private static String artFor = "";
    private static String accessToken;
    private static long accessExpires;
    private static HttpServer server;
    private static boolean started;
    private static long backoffUntil;

    public static Track track() { return track; }
    public static Identifier art() { return art; }

    public static String status() {
        if (Config.get().spotify.refreshToken.isEmpty() && !status.startsWith("Waiting") && !status.startsWith("Error")) return "Not connected";
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
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 43821), 0);
            server.createContext("/callback", ex -> {
                Map<String, String> q = query(ex.getRequestURI().getRawQuery());
                boolean ok = state.equals(q.get("state")) && q.containsKey("code");
                String body = page(ok ? "Connected to Flop Visuals" : "Spotify sign-in failed",
                    ok ? "You can close this tab and go back to Minecraft." : "Close this tab and try Connect again.");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
                if (ok) NET.execute(() -> exchange(q.get("code"), verifier));
                else status = "Error: sign-in was cancelled";
                NET.schedule(Spotify::stopServer, 1, TimeUnit.SECONDS);
            });
            server.start();
            String url = "https://accounts.spotify.com/authorize?client_id=" + enc(cfg.clientId)
                + "&response_type=code&redirect_uri=" + enc(REDIRECT)
                + "&code_challenge_method=S256&code_challenge=" + enc(challenge)
                + "&scope=" + enc(SCOPES) + "&state=" + enc(state);
            status = "Waiting for browser…";
            Util.getPlatform().openUri(url);
        } catch (Exception e) {
            status = "Error: " + e.getClass().getSimpleName();
        }
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
            server.stop(0);
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
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
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
            if (c.spotify.refreshToken.isEmpty() || c.spotify.clientId.isEmpty()) return;
            if (!c.widget("spotify").enabled) return;
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
                case 429 -> {
                    long wait = res.headers().firstValueAsLong("Retry-After").orElse(10);
                    backoffUntil = System.currentTimeMillis() + wait * 1000;
                }
                default -> status = "Error: Spotify HTTP " + res.statusCode();
            }
        } catch (Exception e) {
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
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new ByteArrayInputStream(res.body()));
            if (src == null) return;
            int size = 128;
            NativeImage img = new NativeImage(size, size, true);
            float radius = 22;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int rgb = src.getRGB(x * src.getWidth() / size, y * src.getHeight() / size);
                    float cx = Math.max(radius - x - 0.5f, Math.max(0, x + 0.5f - (size - radius)));
                    float cy = Math.max(radius - y - 0.5f, Math.max(0, y + 0.5f - (size - radius)));
                    float d = (float) Math.sqrt(cx * cx + cy * cy);
                    float a = (cx > 0 && cy > 0) ? Math.max(0, Math.min(1, radius - d + 0.5f)) : 1;
                    img.setPixel(x, y, (Math.round(a * 255) << 24) | (rgb & 0xFFFFFF));
                }
            }
            Minecraft.getInstance().execute(() -> {
                Identifier id = Identifier.fromNamespaceAndPath("flopvisuals", "spotify/art");
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "flopvisuals spotify art", img));
                art = id;
            });
        } catch (Throwable t) {
            art = null;   // no ImageIO, network hiccup: just show the placeholder
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
