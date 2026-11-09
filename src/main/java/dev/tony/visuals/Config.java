package dev.tony.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything saved to config/flop-visuals/config.json. */
public final class Config {
    public Theme theme = new Theme();
    public Map<String, Widget> widgets = new LinkedHashMap<>();
    public Keystrokes keystrokes = new Keystrokes();
    public Trails trails = new Trails();
    public Petals petals = new Petals();
    public Aura aura = new Aura();
    public Lyrics lyrics = new Lyrics();
    public Menu menu = new Menu();
    public Gamble gamble = new Gamble();
    public Sky sky = new Sky();
    public Glass glass = new Glass();
    public Wallpaper wallpaper = new Wallpaper();
    public Spotify spotify = new Spotify();

    /** One HUD element: on/off, where it sits (0..1 of the free space) and its size. */
    public static final class Widget {
        public boolean enabled;
        public float x, y;
        public float scale = 1f;

        public Widget() {}

        Widget(boolean enabled, float x, float y) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
        }
    }

    public static final class Keystrokes {
        public boolean mouse = true;
        public boolean space = true;
        public boolean cps = true;
        public int keySize = 20;
        public int gap = 2;
        /** Fade speed of the pressed highlight, 1 (slow) to 10 (instant). */
        public int fade = 7;
        public boolean background = true;
    }

    public enum TrailType {
        STARDUST, ACCENT_DUST, FEATHERS, SPARKS, FLAMES, SOUL_FLAMES, HEARTS, ENCHANT, NOTES, SNOW, CHERRY, GLOW, TOTEM, CLOUDS
    }

    public static final class Trails {
        public boolean enabled = true;
        public TrailType type = TrailType.STARDUST;
        /** Particles per tick while moving, 1 to 10. */
        public int density = 3;
        public boolean onlyWhenMoving = true;
        public boolean whileFlying = true;
        /** How far particles drift, 1 to 10. */
        public int spread = 4;
    }

    /** DonutSMP bet helper for payments in chat. */
    public static final class Gamble {
        public boolean enabled = true;
        /** Payments below this are left alone. */
        public double minimum = 1_000_000;
        /** Payments above this are left alone too (0 = no maximum). */
        public double maximum = 0;
        /** What a win pays back, as a multiple of the bet (2 = double). */
        public float payout = 2f;
        public boolean stack = true;
        public boolean tags = true;
        /** The gambling session running now (money is only tracked during one), or null. */
        public Session current;
        /** Finished sessions, newest last. */
        public List<Session> sessions = new ArrayList<>();
    }

    /** One gambling session: from Start to End, what you gained or lost. */
    public static final class Session {
        public long start = System.currentTimeMillis();
        public long end;
        public double profit;
        /** Running total after each payment, for the money graph (newest last). */
        public List<Double> history = new ArrayList<>();
    }

    /** DonutSMP auctions: highest total payment before the countdown ends wins the item. */
    public static final class Auction {
        public List<String> items = new ArrayList<>(List.of("Netherite Ingot"));
        public int seconds = 30;
        /** Say "<IGN> wins the <item>!" in public chat when it ends. */
        public boolean announce = true;
    }

    public Auction auction = new Auction();

    /** YouTube on the HUD. */
    public static final class YouTube {
        /** Last link opened. */
        public String url = "";
        public int volume = 80;
    }

    public YouTube youtube = new YouTube();

    public enum MenuPosition { CENTER, LEFT, RIGHT }

    /** How the Flop Visuals window itself looks. */
    public static final class Menu {
        public MenuPosition position = MenuPosition.CENTER;
        /** Window size, 70-130 %. */
        public int scale = 100;
        /** Glass opacity, 40-100 %. */
        public int opacity = 74;
        /** Corner roundness, 6-24. */
        public int radius = 16;
        public boolean blurWorld = true;
        public boolean animations = true;
        /** 1 slow ... 10 quick. */
        public int animSpeed = 6;
        public boolean sidebarLabels = true;
    }

    /** Synced lyrics card. */
    public static final class Lyrics {
        /** Lines shown above and below the sung line (0-2). */
        public int context = 1;
        /** Shift the timing if lyrics run early or late, in milliseconds. */
        public int offsetMs = 0;
        public int width = 240;
        public boolean center = true;
        public boolean accentLine = true;
        /** When a song only has plain lyrics, show them with estimated timing. */
        public boolean approximate = true;
        /** Sky lyrics: the sung line floats in the air ahead of you. */
        public boolean sky = false;
        public float skyDistance = 6f;
        public float skyHeight = 1.2f;
        public float skyScale = 1f;
        public boolean skyNext = true;
        public SkyFont skyFont = SkyFont.HEAVY;
        /** Text colour: the accent, or skyColour. */
        public boolean skyAccent = true;
        public int skyColour = 0xFFFFFF;
        /** Soft glow behind the words. */
        public boolean skyGlow = true;
        public boolean skyGlowAccent = true;
        public int skyGlowColour = 0x7DD3FC;
        public int skyGlowStrength = 60;
        public float skyGlowSize = 1f;
    }

    public enum SkyFont { HEAVY, BOLD, REGULAR, MONO, MINECRAFT }

    public enum HaloStyle { RING, CROWN, TWIN_RING, SUN, HORNS, STARLIGHT }

    /** Orbs, halo and aura around you. */
    public static final class Aura {
        public boolean orbs = true;
        public boolean orbTrails = true;
        /** Number of orbs, 1-12. */
        public int orbCount = 6;
        /** Orbit speed, 1-10. */
        public int orbSpeed = 5;
        public boolean orbsFirstPerson = true;
        public boolean halo = true;
        public HaloStyle haloStyle = HaloStyle.RING;
        public boolean aura = true;
        public Palette colour = Palette.ACCENT;
    }

    public enum PetalType { CHERRY, PALE_OAK, SPORE_BLOSSOM, FIREFLIES, SNOWFLAKES, ASH, CRIMSON, WARPED, END_RODS }

    public static final class Petals {
        public boolean enabled = true;
        public PetalType type = PetalType.CHERRY;
        /** Radius around you in blocks, 2 to 16. */
        public int radius = 7;
        /** New petals per tick, 1 to 10. */
        public int density = 3;
        public boolean indoors = false;
    }

    public enum SkyStyle { TOPOGRAPHIC, GALAXY, AETHER, NOCTURNE, AMETHYST }

    /** Colour a sky look is drawn in. Signature = that look's own colour; Accent = your accent colour. */
    public enum Palette { SIGNATURE, ACCENT, VIOLET, AMBER, ICE, ROSE, EMERALD, SILVER, CRIMSON, OCEAN, SUNSET, GOLD }

    public static final class Sky {
        public boolean enabled = true;
        public SkyStyle style = SkyStyle.TOPOGRAPHIC;
        public Palette colour = Palette.SIGNATURE;
        /** How much of the vanilla sky is covered, 0-100. 100 = full accent sky. */
        public int opacity = 100;
        /** Number of contour lines across the sky, 3-16. */
        public int lines = 7;
        /** Animation speed, 0-10. */
        public int speed = 5;
        /** Background brightness, 0-99. */
        public int brightness = 60;
        /** Tint distant fog to match the sky. */
        public boolean tintFog = true;
        public boolean allDimensions = false;
    }

    public static final class Glass {
        public boolean enabled = true;
        /** How much of the sky the ground reflects, 0-100. */
        public int reflection = 80;
        /** Radius around you in blocks, 8-48. */
        public int radius = 24;
        /** Also make water reflective. */
        public boolean water = false;
        /** Hide short and tall grass while the glass ground is on. */
        public boolean hideGrass = true;
        /** Mirror real blocks, players and mobs (screen-space reflections). */
        public boolean realReflections = true;
    }

    public enum WallpaperMode { TOPOGRAPHIC, GALAXY, AETHER, NOCTURNE, AMETHYST, IMAGE, VANILLA }

    public static final class Wallpaper {
        public WallpaperMode mode = WallpaperMode.TOPOGRAPHIC;
        public Palette colour = Palette.SIGNATURE;
        /** File name in config/flop-visuals/wallpapers. */
        public String image = "";
        /** Darken the wallpaper behind menus, 0-80. */
        public int dim = 15;
    }

    /** Where now-playing comes from: Windows' media controls (free, no setup) or Spotify's Web API (needs Premium). */
    public enum SpotifySource { WINDOWS, WEB_API }

    public static final class Spotify {
        public SpotifySource source = SpotifySource.WINDOWS;
        public String clientId = "";
        public String refreshToken = "";
        public boolean showArt = true;
        public boolean showProgress = true;
    }

    // ------------------------------------------------------------ widget list

    public static final String[] WIDGETS = {
        "keystrokes", "fps", "coords", "direction", "speed", "ping", "cps", "clock", "biome", "armor", "day", "spotify", "lyrics", "profit", "moneygraph", "youtube",
    };

    /** Your own text labels on the HUD. Each one is a widget with the id "text:<id>". */
    public static final class CustomText {
        public String id = Long.toHexString(System.nanoTime());
        public String text = "Hello!";
        public int colour = 0xFFFFFF;
        public boolean bold = true;
        public boolean background = true;
    }

    public List<CustomText> texts = new ArrayList<>();

    public CustomText text(String widgetId) {
        if (!widgetId.startsWith("text:")) return null;
        String id = widgetId.substring(5);
        for (CustomText t : texts) if (t.id.equals(id)) return t;
        return null;
    }

    /** Built-in widgets plus your text labels. */
    public static List<String> allWidgets() {
        List<String> out = new ArrayList<>(java.util.Arrays.asList(WIDGETS));
        for (CustomText t : get().texts) out.add("text:" + t.id);
        return out;
    }

    public static String widgetName(String id) {
        if (id.startsWith("text:")) {
            CustomText t = get().text(id);
            return t == null ? "Text" : "Text: " + t.text;
        }
        return switch (id) {
            case "keystrokes" -> "Keystrokes";
            case "fps" -> "FPS";
            case "coords" -> "Coordinates";
            case "direction" -> "Direction";
            case "speed" -> "Speed";
            case "ping" -> "Ping";
            case "cps" -> "CPS";
            case "clock" -> "Clock";
            case "biome" -> "Biome";
            case "armor" -> "Armor";
            case "day" -> "Day";
            case "spotify" -> "Spotify";
            case "lyrics" -> "Lyrics";
            case "profit" -> "Money gained / lost";
            case "moneygraph" -> "Money graph";
            case "youtube" -> "YouTube";
            default -> id;
        };
    }

    private void defaults() {
        if (widgets == null) widgets = new LinkedHashMap<>();
        putDefault("keystrokes", true, 0.01f, 0.97f);
        putDefault("fps", true, 0.005f, 0.01f);
        putDefault("coords", true, 0.005f, 0.06f);
        putDefault("direction", true, 0.005f, 0.11f);
        putDefault("speed", false, 0.005f, 0.16f);
        putDefault("ping", true, 0.995f, 0.01f);
        putDefault("cps", false, 0.995f, 0.06f);
        putDefault("clock", false, 0.995f, 0.11f);
        putDefault("biome", false, 0.005f, 0.21f);
        putDefault("armor", false, 0.995f, 0.97f);
        putDefault("day", false, 0.995f, 0.16f);
        putDefault("spotify", false, 0.5f, 0.02f);
        putDefault("lyrics", false, 0.5f, 0.86f);
        putDefault("profit", false, 0.005f, 0.26f);
        putDefault("moneygraph", false, 0.005f, 0.33f);
        putDefault("youtube", false, 0.995f, 0.05f);
    }

    private void putDefault(String id, boolean on, float x, float y) {
        widgets.putIfAbsent(id, new Widget(on, x, y));
    }

    public Widget widget(String id) {
        Widget w = widgets.get(id);
        if (w == null) {
            defaults();
            w = widgets.get(id);
        }
        if (w == null) {
            // A new text label starts in the middle of the screen.
            w = new Widget(true, 0.5f, 0.4f);
            widgets.put(id, w);
        }
        return w;
    }

    // ------------------------------------------------------------ load / save

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("flop-visuals");
    private static final Path FILE = DIR.resolve("config.json");
    private static Config instance = new Config();

    public static Config get() { return instance; }

    public static Theme theme() { return instance.theme; }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Config c = GSON.fromJson(r, Config.class);
                    if (c != null) instance = c;
                }
            }
        } catch (Exception ignored) {
        }
        instance.clamp();
    }

    public static void save() {
        instance.clamp();
        try {
            Files.createDirectories(DIR);
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception ignored) {
        }
    }

    private void clamp() {
        if (theme == null) theme = new Theme();
        if (keystrokes == null) keystrokes = new Keystrokes();
        if (trails == null) trails = new Trails();
        if (petals == null) petals = new Petals();
        if (sky == null) sky = new Sky();
        if (glass == null) glass = new Glass();
        if (wallpaper == null) wallpaper = new Wallpaper();
        if (spotify == null) spotify = new Spotify();
        if (spotify.source == null) spotify.source = SpotifySource.WINDOWS;
        if (aura == null) aura = new Aura();
        if (lyrics == null) lyrics = new Lyrics();
        if (menu == null) menu = new Menu();
        if (gamble == null) gamble = new Gamble();
        if (gamble.sessions == null) gamble.sessions = new ArrayList<>();
        if (gamble.current != null && gamble.current.history == null) gamble.current.history = new ArrayList<>();
        for (Session ss : gamble.sessions) if (ss.history == null) ss.history = new ArrayList<>();
        if (auction == null) auction = new Auction();
        if (youtube == null) youtube = new YouTube();
        youtube.volume = Math.max(0, Math.min(100, youtube.volume));
        if (lyrics.skyFont == null) lyrics.skyFont = SkyFont.HEAVY;
        lyrics.skyDistance = Math.max(3, Math.min(16, lyrics.skyDistance <= 0 ? 6 : lyrics.skyDistance));
        lyrics.skyScale = Math.max(0.5f, Math.min(2.5f, lyrics.skyScale <= 0 ? 1 : lyrics.skyScale));
        lyrics.skyGlowStrength = Math.max(0, Math.min(100, lyrics.skyGlowStrength));
        lyrics.skyGlowSize = Math.max(0.3f, Math.min(3f, lyrics.skyGlowSize <= 0 ? 1 : lyrics.skyGlowSize));
        if (auction.items == null) auction.items = new ArrayList<>();
        auction.seconds = Math.max(5, Math.min(600, auction.seconds));
        gamble.payout = Math.max(1f, Math.min(10f, gamble.payout));
        gamble.minimum = Math.max(0, gamble.minimum);
        if (texts == null) texts = new ArrayList<>();
        texts.removeIf(t -> t == null || t.id == null);
        for (CustomText t : texts) if (t.text == null) t.text = "";
        if (menu.position == null) menu.position = MenuPosition.CENTER;
        menu.scale = Theme.c(menu.scale, 70, 130);
        menu.opacity = Theme.c(menu.opacity, 40, 100);
        menu.radius = Theme.c(menu.radius, 6, 24);
        menu.animSpeed = Theme.c(menu.animSpeed, 1, 10);
        lyrics.context = Theme.c(lyrics.context, 0, 2);
        lyrics.offsetMs = Theme.c(lyrics.offsetMs, -3000, 3000);
        lyrics.width = Theme.c(lyrics.width, 140, 360);
        if (aura.haloStyle == null) aura.haloStyle = HaloStyle.RING;
        if (aura.colour == null) aura.colour = Palette.ACCENT;
        aura.orbCount = Theme.c(aura.orbCount, 1, 12);
        aura.orbSpeed = Theme.c(aura.orbSpeed, 1, 10);
        if (wallpaper.mode == null) wallpaper.mode = WallpaperMode.TOPOGRAPHIC;
        if (wallpaper.image == null) wallpaper.image = "";
        if (wallpaper.colour == null) wallpaper.colour = Palette.SIGNATURE;
        if (sky.style == null) sky.style = SkyStyle.TOPOGRAPHIC;
        if (sky.colour == null) sky.colour = Palette.SIGNATURE;
        if (spotify.clientId == null) spotify.clientId = "";
        if (spotify.refreshToken == null) spotify.refreshToken = "";
        glass.reflection = Theme.c(glass.reflection, 0, 100);
        glass.radius = Theme.c(glass.radius, 8, 48);
        wallpaper.dim = Theme.c(wallpaper.dim, 0, 80);
        if (trails.type == null) trails.type = TrailType.STARDUST;
        if (petals.type == null) petals.type = PetalType.CHERRY;
        theme.clamp();
        defaults();
        for (Widget w : widgets.values()) {
            w.x = Math.max(0, Math.min(1, w.x));
            w.y = Math.max(0, Math.min(1, w.y));
            w.scale = Math.max(0.5f, Math.min(3f, Float.isNaN(w.scale) ? 1 : w.scale));
        }
        keystrokes.keySize = Theme.c(keystrokes.keySize, 12, 40);
        keystrokes.gap = Theme.c(keystrokes.gap, 0, 6);
        keystrokes.fade = Theme.c(keystrokes.fade, 1, 10);
        trails.density = Theme.c(trails.density, 1, 10);
        trails.spread = Theme.c(trails.spread, 1, 10);
        petals.radius = Theme.c(petals.radius, 2, 16);
        petals.density = Theme.c(petals.density, 1, 10);
        sky.opacity = Theme.c(sky.opacity, 0, 100);
        sky.lines = Theme.c(sky.lines, 3, 16);
        sky.speed = Theme.c(sky.speed, 0, 10);
        sky.brightness = Theme.c(sky.brightness, 0, 99);
    }
}
