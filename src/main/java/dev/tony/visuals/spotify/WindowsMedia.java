package dev.tony.visuals.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Free "now playing" with no Spotify account setup: reads Windows' media controls (what the volume popup shows) through
 * a small PowerShell helper. Works with the Spotify app and the web player in a browser, including the song position
 * for the progress bar and the album cover. Windows only.
 */
public final class WindowsMedia {
    private WindowsMedia() {}

    private static Process proc;
    private static long restartAt;
    private static int lastArt;
    private static boolean hooked;

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** Starts the helper if it isn't running (restarts it at most every 10 seconds if it stops). */
    public static synchronized void ensure() {
        if (!supported()) {
            Spotify.note("Error: reading from Windows only works on Windows. Switch Source to Web API.");
            return;
        }
        if (proc != null && proc.isAlive()) return;
        if (System.currentTimeMillis() < restartAt) return;
        restartAt = System.currentTimeMillis() + 10_000;
        try {
            // The helper is saved as a plain, readable script next to the mod's settings and run as a file
            // (no encoded commands), so you can open it and see exactly what it does.
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("flop-visuals");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve("spotify-now-playing.ps1");
            byte[] script;
            try (InputStream in = WindowsMedia.class.getResourceAsStream("/assets/flopvisuals/spotify/media.ps1")) {
                if (in == null) throw new IllegalStateException("helper script missing");
                script = in.readAllBytes();
            }
            if (!java.nio.file.Files.exists(file) || !java.util.Arrays.equals(java.nio.file.Files.readAllBytes(file), script)) {
                java.nio.file.Files.write(file, script);
            }
            long me = ProcessHandle.current().pid();
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass", "-File", file.toAbsolutePath().toString(), "-ParentId", Long.toString(me));
            pb.redirectErrorStream(true);
            proc = pb.start();
            Process p = proc;
            Spotify.note("Reading from Windows…");
            Thread reader = new Thread(() -> read(p), "flopvisuals-windows-media");
            reader.setDaemon(true);
            reader.start();
            if (!hooked) {
                hooked = true;
                Runtime.getRuntime().addShutdownHook(new Thread(WindowsMedia::stop));
            }
        } catch (Throwable e) {
            Spotify.note("Error: couldn't start the Windows reader (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
    }

    public static synchronized void stop() {
        if (proc != null) {
            proc.destroy();
            proc = null;
        }
    }

    private static void read(Process p) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("{")) continue;
                try {
                    handle(JsonParser.parseString(line).getAsJsonObject());
                } catch (Exception ignored) {
                    // a half line or odd output: skip it
                }
            }
        } catch (Exception ignored) {
            // the helper closed
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static long num(JsonObject o, String k) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Sends play/pause ("toggle"), "next" or "previous" to the song Windows is showing. */
    public static synchronized boolean send(String command) {
        if (proc == null || !proc.isAlive()) return false;
        try {
            var out = proc.getOutputStream();
            out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void handle(JsonObject o) {
        if (o.has("ack")) return;
        String error = str(o, "error");
        String title = str(o, "title");
        if (title.isEmpty()) {
            Spotify.setTrack(null);
            Spotify.note(error.isEmpty() ? "Nothing playing (open Spotify in the app or a browser)" : "Error: " + error);
            return;
        }
        boolean playing = o.has("playing") && o.get("playing").getAsBoolean();
        long duration = Math.max(0, num(o, "durationMs"));
        long position = Math.max(0, num(o, "positionMs"));
        long updated = num(o, "updatedMs"), now = num(o, "nowMs");
        // Windows only refreshes the position on play, pause and seek, so carry it forward from when it was set.
        if (playing && updated > 0 && now > updated) position += now - updated;
        if (duration > 0) position = Math.min(position, duration);
        Spotify.songChanged(title, str(o, "artist"));
        Spotify.setTrack(new Spotify.Track(title, str(o, "artist"), "", duration, position, playing, System.currentTimeMillis()));
        Spotify.note((playing ? "Playing" : "Paused") + " · from the Spotify " + ("app".equals(str(o, "source")) ? "app" : "web player"));
        String art = str(o, "art");
        if (!art.isEmpty()) {
            int key = art.hashCode();
            if (key != lastArt) {
                lastArt = key;
                try {
                    Spotify.setArt(Base64.getDecoder().decode(art));
                } catch (Exception ignored) {
                    // bad cover: keep the placeholder
                }
            }
        }
    }
}
