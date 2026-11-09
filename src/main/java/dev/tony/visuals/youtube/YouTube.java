package dev.tony.visuals.youtube;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.tony.visuals.Config;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube on the HUD. The video itself plays in an embedded Chromium browser from the free MCEF Modern mod (it's
 * YouTube's own player, sound included); Flop Visuals keeps just the video, sized to the card or the whole screen.
 * Without MCEF Modern the card still shows the thumbnail, title and channel. MCEF is used through reflection so
 * Flop Visuals works the same with or without it installed.
 */
public final class YouTube {
    private YouTube() {}

    static final Logger LOG = LoggerFactory.getLogger("flopvisuals-youtube");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Pattern ID = Pattern.compile("(?:youtu\\.be/|[?&]v=|/shorts/|/live/|/embed/|/v/)([A-Za-z0-9_-]{11})");
    private static final Pattern TIME = Pattern.compile("[?&#]t=(\\d+)");

    private static String videoId;
    private static volatile String title = "", channel = "", status = "";
    private static volatile Identifier thumb;
    private static boolean paused, fullscreen;
    private static int startAt;

    // MCEF (reflection).
    private static Object api, browser;
    private static Object init;
    private static boolean initFailed;
    private static int bw, bh;
    private static long nextInject;

    /** MCEF Modern installed? */
    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("mcef-modern");
    }

    public static String videoId() { return videoId; }
    public static String title() { return title; }
    public static String channel() { return channel; }
    public static String status() { return status; }
    public static Identifier thumbnail() { return thumb; }
    public static boolean paused() { return paused; }
    public static boolean fullscreen() { return fullscreen && videoId != null; }
    public static boolean playing() { return browser != null && !paused; }

    /** "https://youtu.be/dQw4w9WgXcQ", "…watch?v=…", shorts, live, or just the 11-character id. */
    public static String parseId(String link) {
        if (link == null) return null;
        link = link.trim();
        if (link.matches("[A-Za-z0-9_-]{11}")) return link;
        Matcher m = ID.matcher(link);
        return m.find() ? m.group(1) : null;
    }

    /** Loads a link. Returns false if it isn't a YouTube link. */
    public static boolean open(String link) {
        String id = parseId(link);
        if (id == null) return false;
        Matcher t = TIME.matcher(link);
        startAt = t.find() ? Integer.parseInt(t.group(1)) : 0;
        closeBrowser();
        videoId = id;
        paused = false;
        title = "";
        channel = "";
        thumb = null;
        Config.get().youtube.url = link.trim();
        Config.save();
        fetchInfo(id);
        if (!available()) status = "Install MCEF Modern to play videos";
        return true;
    }

    public static void stop() {
        closeBrowser();
        videoId = null;
        fullscreen = false;
        status = "";
    }

    public static void togglePause() {
        if (videoId == null) {
            String last = Config.get().youtube.url;
            if (last != null && !last.isBlank()) open(last);
            return;
        }
        paused = !paused;
        nextInject = 0;
    }

    public static void toggleFullscreen() {
        if (videoId == null) return;
        fullscreen = !fullscreen;
    }

    public static void exitFullscreen() {
        fullscreen = false;
    }

    // ------------------------------------------------------------ info + thumbnail

    private static void fetchInfo(String id) {
        CompletableFuture.runAsync(() -> {
            try {
                String watch = "https://www.youtube.com/watch?v=" + id;
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(watch, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", "FlopVisuals").build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (id.equals(videoId)) {
                        title = o.has("title") ? o.get("title").getAsString() : "";
                        channel = o.has("author_name") ? o.get("author_name").getAsString() : "";
                    }
                } else if (r.statusCode() == 401 || r.statusCode() == 403) {
                    if (id.equals(videoId)) title = "Private or age-restricted video";
                }
            } catch (Exception e) {
                LOG.warn("Couldn't read the video's title", e);
            }
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg")).timeout(Duration.ofSeconds(10)).build();
                HttpResponse<byte[]> r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() == 200 && id.equals(videoId)) uploadThumb(r.body());
            } catch (Exception e) {
                LOG.warn("Couldn't load the thumbnail", e);
            }
        });
    }

    private static void uploadThumb(byte[] bytes) {
        java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
        NativeImage img;
        try (org.lwjgl.system.MemoryStack st = org.lwjgl.system.MemoryStack.stackPush()) {
            buf.put(bytes).flip();
            java.nio.IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), c = st.mallocInt(1);
            java.nio.ByteBuffer px = org.lwjgl.stb.STBImage.stbi_load_from_memory(buf, w, h, c, 4);
            if (px == null) return;
            int sw = w.get(0), sh = h.get(0);
            img = new NativeImage(sw, sh, false);
            for (int y = 0; y < sh; y++) {
                for (int x = 0; x < sw; x++) {
                    int k = (y * sw + x) * 4;
                    int r = px.get(k) & 255, g = px.get(k + 1) & 255, b = px.get(k + 2) & 255;
                    img.setPixel(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
                }
            }
            org.lwjgl.stb.STBImage.stbi_image_free(px);
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(buf);
        }
        NativeImage done = img;
        Minecraft.getInstance().execute(() -> {
            Identifier id = Identifier.fromNamespaceAndPath("flopvisuals", "youtube/thumb");
            DynamicTexture tex = new DynamicTexture(() -> "flopvisuals youtube thumbnail", done) {
                {
                    this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                        .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
                }
            };
            Minecraft.getInstance().getTextureManager().register(id, tex);
            thumb = id;
        });
    }

    // ------------------------------------------------------------ the browser (render thread)

    /**
     * Keeps the browser alive and sized to {w, h} (GUI units). Returns the video picture, or null if there isn't one
     * (yet). Call every frame the video is on screen.
     */
    public static GpuTextureView frame(int w, int h) {
        if (videoId == null || !available() || initFailed) return null;
        try {
            if (api == null) {
                Class<?> apiClass = Class.forName("net.dimaskama.mcef.api.MCEFApi");
                if (init == null) init = apiClass.getMethod("initialize").invoke(null);
                Class<?> ic = Class.forName("net.dimaskama.mcef.api.MCEFApi$Initialization");
                Method stage = ic.getMethod("getStage");
                String s = String.valueOf(stage.invoke(init));
                if (!"DONE".equals(s)) {
                    CompletableFuture<?> f = (CompletableFuture<?>) ic.getMethod("getFuture").invoke(init);
                    if (f.isCompletedExceptionally()) {
                        initFailed = true;
                        status = "MCEF Modern couldn't start";
                        return null;
                    }
                    Method pct = ic.getMethod("getPercentage");
                    float p = (float) pct.invoke(init);
                    status = switch (s) {
                        case "DOWNLOADING" -> "Downloading the video player" + (p >= 0 ? " " + Math.round(p) + "%" : "…") + " (first time only)";
                        case "EXTRACTING", "INSTALL" -> "Installing the video player…";
                        default -> "Starting the video player…";
                    };
                    return null;
                }
                api = apiClass.getMethod("getInstance").invoke(null);
            }
            if (browser == null) {
                Class<?> apiClass = Class.forName("net.dimaskama.mcef.api.MCEFApi");
                String url = "https://www.youtube.com/watch?v=" + videoId + (startAt > 0 ? "&t=" + startAt + "s" : "");
                browser = apiClass.getMethod("createBrowser", String.class, boolean.class).invoke(api, url, false);
                bw = bh = 0;
                nextInject = System.currentTimeMillis() + 1500;
                status = "Loading…";
            }
            Class<?> bc = Class.forName("net.dimaskama.mcef.api.MCEFBrowser");
            if (w != bw || h != bh) {
                bc.getMethod("resize", int.class, int.class).invoke(browser, Math.max(16, w), Math.max(9, h));
                bw = w;
                bh = h;
            }
            long now = System.currentTimeMillis();
            if (now >= nextInject) {
                nextInject = now + 1000;
                inject(bc);
            }
            Object view = bc.getMethod("getTextureView").invoke(browser);
            if (view != null) status = paused ? "Paused" : "Playing";
            return (GpuTextureView) view;
        } catch (Throwable e) {
            LOG.warn("YouTube player error", e);
            status = "Video player error: " + e.getClass().getSimpleName();
            initFailed = true;
            closeBrowser();
            return null;
        }
    }

    /** Keeps only the video on the page (full window, black bars), applies volume and play / pause, skips skippable ads. */
    private static void inject(Class<?> bc) throws Exception {
        Object cef = bc.getMethod("getCefBrowser").invoke(browser);
        int vol = Math.max(0, Math.min(100, Config.get().youtube.volume));
        String js = "(function(){var v=document.querySelector('video.html5-main-video')||document.querySelector('video');if(!v)return;"
            + "var s=document.getElementById('fv-style');if(!s){s=document.createElement('style');s.id='fv-style';(document.head||document.documentElement).appendChild(s);}"
            + "s.textContent='html,body{overflow:hidden!important;background:#000!important}"
            + "video.fv{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;"
            + "z-index:2147483646!important;object-fit:contain!important;background:#000!important;transform:none!important}"
            + "#fv-shield{position:fixed;left:0;top:0;right:0;bottom:0;z-index:2147483647;background:transparent}';"
            + "v.classList.add('fv');if(v.parentElement!==document.body){document.body.appendChild(v);}"
            + "if(!document.getElementById('fv-shield')){var d=document.createElement('div');d.id='fv-shield';document.body.appendChild(d);}"
            + "v.volume=" + (vol / 100.0) + ";v.muted=false;"
            + "var b=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern');if(b)b.click();"
            + (paused ? "if(!v.paused)v.pause();" : "if(v.paused&&!v.ended){var p=v.play();if(p&&p.catch)p.catch(function(){});}")
            + "})();";
        Class<?> cc = Class.forName("org.cef.browser.CefBrowser");
        cc.getMethod("executeJavaScript", String.class, String.class, int.class).invoke(cef, js, "flopvisuals", 0);
    }

    private static void closeBrowser() {
        if (browser != null) {
            try {
                Class.forName("net.dimaskama.mcef.api.MCEFBrowser").getMethod("close").invoke(browser);
            } catch (Throwable ignored) {
                // already gone
            }
            browser = null;
        }
        bw = bh = 0;
    }

    /** Called every client tick: closes the player when nothing shows the video any more. */
    public static void tick() {
        boolean shown = fullscreen() || Config.get().widget("youtube").enabled;
        if (!shown && browser != null) {
            closeBrowser();
            paused = true;
        }
    }
}
