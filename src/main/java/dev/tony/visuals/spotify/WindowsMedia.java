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
            String script;
            try (InputStream in = WindowsMedia.class.getResourceAsStream("/assets/flopvisuals/spotify/media.ps1")) {
                if (in == null) throw new IllegalStateException("helper script missing");
                script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            long me = ProcessHandle.current().pid();
            String command = "& {\n" + script + "\n} -ParentId " + me;
            String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded);
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

    private static void handle(JsonObject o) {
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
