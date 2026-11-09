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

    static void setTrack(Track t) {
        track = t;
        if (t == null) {
            art = null;
            artFor = "";
        }
    }

    /** Cover image bytes (PNG or JPEG) from any source. */
    static void setArt(byte[] bytes) {
        NET.execute(() -> {
            try {
                java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
                if (src != null) uploadArt(src);
            } catch (Throwable ignored) {
                // keep the placeholder
            }
        });
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
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new ByteArrayInputStream(res.body()));
            if (src == null) return;
            uploadArt(src);
        } catch (Throwable t) {
            art = null;   // no ImageIO, network hiccup: just show the placeholder
        }
    }

    /** Rounds the cover's corners and uploads it as a smooth texture. */
    private static void uploadArt(java.awt.image.BufferedImage src) {
        {
            int size = 128;
            java.awt.image.BufferedImage sm = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D gr = sm.createGraphics();
            gr.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            gr.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            gr.drawImage(src, 0, 0, size, size, null);
            gr.dispose();
            src = sm;
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
                DynamicTexture tex = new DynamicTexture(() -> "flopvisuals spotify art", img) {
                    {
                        this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                            .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
                    }
                };
                Minecraft.getInstance().getTextureManager().register(id, tex);
                art = id;
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
