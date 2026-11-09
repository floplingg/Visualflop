package dev.tony.visuals.gui;

import dev.tony.visuals.Config;
import dev.tony.visuals.FlopVisuals;
import dev.tony.visuals.Theme;
import dev.tony.visuals.glass.GrassHider;
import dev.tony.visuals.spotify.Spotify;
import dev.tony.visuals.wallpaper.Wallpapers;
import net.minecraft.util.Util;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The Flop Visuals settings window. Everything previews live and saves automatically. */
public class MainScreen extends SheetScreen {
    private static int lastPage;

    public MainScreen(Screen parent) {
        super("Flop Visuals", parent);
        addPage("Appearance", "appearance", 0x0A84FF, this::appearance);
        addPage("HUD", "hud", 0x8E8E93, this::hud);
        addPage("Keystrokes", "keyboard", 0xBF5AF2, this::keystrokes);
        addPage("Trails", "trails", 0x30D158, this::trails);
        addPage("Ambience", "petals", 0xFF6FAE, this::ambience);
        addPage("Orbs & Halo", "halo", 0xFFD34D, this::orbs);
        addPage("Sky", "sky", 0x5E5CE6, this::sky);
        addPage("Glass Ground", "glass", 0x32ADE6, this::glass);
        addPage("Wallpaper", "wallpaper", 0xFF9F0A, this::wallpaper);
        addPage("Spotify", "music", 0x1DB954, this::spotify);
        addPage("Gamble", "pay", 0xFFD34D, this::gamble);
        addPage("Auction", "gavel", 0xFF8A3D, this::auction);
        page = lastPage;
    }

    public MainScreen(Screen parent, int startPage) {
        this(parent);
        page = Math.max(0, Math.min(pages.size() - 1, startPage));
    }

    @Override
    protected void showPage(int index) {
        super.showPage(index);
        lastPage = page;
    }

    private static Config c() { return Config.get(); }

    private static final String[] DESCRIPTIONS = {
        "Accent colour and super smooth rounded edges across every screen.",
        "Choose what sits on screen. Every module is a rounded glass card.",
        "A rounded keystroke overlay that lights up in your accent as you press.",
        "A cinematic trail that follows you as you move.",
        "Petals and particles that float around you only, not the whole world.",
        "Glowing orbs that circle you, a halo or crown, and a soft aura.",
        "Animated skies: topographic, galaxy, aether, nocturne and amethyst.",
        "A polished glass floor that mirrors blocks, players and mobs.",
        "Set your own backgrounds for the title screen and other menus.",
        "A now-playing card with album art and progress.",
        "DonutSMP bets: mark payments as won or lost right in chat, and track your profit.",
        "DonutSMP auctions: highest total payment before the countdown ends wins.",
    };

    @Override
    protected String describe(int page) {
        return page >= 0 && page < DESCRIPTIONS.length ? DESCRIPTIONS[page] : "";
    }

    // ------------------------------------------------------------ pages

    private List<Sheet.Item> appearance() {
        Theme t = c().theme;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Accent"));
        l.add(new Sheet.Colour("Accent colour", () -> t.accentColor, v -> t.accentColor = v));
        l.add(new Sheet.Toggle("Rainbow accent", () -> t.chroma, v -> t.chroma = v));
        l.add(Sheet.Slider.ints("Rainbow speed", 1, 10, () -> t.chromaSpeed, v -> t.chromaSpeed = v, ""));
        l.add(new Sheet.Chips(Arrays.asList(Theme.PRESETS), null, name -> {
            c().theme = Theme.preset(name, c().theme);
            rebuild();
        }));
        l.add(new Sheet.Footer("Presets set the accent, background and text colours together."));

        Config.Menu mn = c().menu;
        l.add(new Sheet.Header("Menu"));
        l.add(new Sheet.Choice<>("Position", Config.MenuPosition.values(), () -> mn.position, v -> {
            mn.position = v;
            relayout();
        }));
        l.add(Sheet.Slider.ints("Size", 70, 130, () -> mn.scale, v -> mn.scale = v, "%").relayout());
        l.add(Sheet.Slider.ints("Glass opacity", 40, 100, () -> mn.opacity, v -> mn.opacity = v, "%"));
        l.add(Sheet.Slider.ints("Corner roundness", 6, 24, () -> mn.radius, v -> mn.radius = v, ""));
        l.add(new Sheet.Toggle("Blur the world behind", () -> mn.blurWorld, v -> mn.blurWorld = v));
        l.add(new Sheet.Toggle("Sidebar names", () -> mn.sidebarLabels, v -> {
            mn.sidebarLabels = v;
            relayout();
        }));
        l.add(new Sheet.Toggle("Animations", () -> mn.animations, v -> mn.animations = v));
        l.add(Sheet.Slider.ints("Animation speed", 1, 10, () -> mn.animSpeed, v -> mn.animSpeed = v, ""));

        l.add(new Sheet.Header("Menus"));
        l.add(new Sheet.Toggle("Light appearance", () -> t.lightMenus, v -> t.lightMenus = v));
        l.add(new Sheet.Toggle("Smooth font", () -> t.smoothFont, v -> t.smoothFont = v));
        l.add(new Sheet.Toggle("Blur game behind menus", () -> t.blurMenus, v -> t.blurMenus = v));
        l.add(new Sheet.Info("Open this menu", () -> FlopVisuals.menuKey.getTranslatedKeyMessage().getString()));
        l.add(new Sheet.Footer("Change the key in Options › Controls › Key Binds."));

        l.add(new Sheet.Header("HUD cards"));
        l.add(new Sheet.Colour("Background", () -> t.background, v -> t.background = v));
        l.add(Sheet.Slider.ints("Opacity", 0, 100, () -> t.widgetOpacity, v -> t.widgetOpacity = v, "%"));
        l.add(Sheet.Slider.ints("Corner radius", 0, 10, () -> t.rounding, v -> t.rounding = v, ""));
        l.add(Sheet.Slider.ints("Padding", 2, 10, () -> t.padding, v -> t.padding = v, ""));
        l.add(new Sheet.Toggle("Outline", () -> t.outline, v -> t.outline = v));
        l.add(new Sheet.Toggle("Accent outline", () -> t.outlineAccent, v -> t.outlineAccent = v));
        l.add(Sheet.Slider.ints("Outline opacity", 0, 100, () -> t.outlineOpacity, v -> t.outlineOpacity = v, "%"));
        l.add(new Sheet.Toggle("Accent strip", () -> t.accentStrip, v -> t.accentStrip = v));
        l.add(new Sheet.Toggle("Text shadow", () -> t.textShadow, v -> t.textShadow = v));
        l.add(new Sheet.Colour("Text", () -> t.text, v -> t.text = v));
        l.add(new Sheet.Colour("Secondary text", () -> t.muted, v -> t.muted = v));
        return l;
    }

    private List<Sheet.Item> hud() {
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Layout"));
        l.add(new Sheet.Action("Edit HUD layout", () -> "", () -> minecraft.setScreen(new LayoutScreen(this))).accent().chevron());
        l.add(new Sheet.Footer("Drag to move, scroll to resize, right-click to hide."));
        l.add(new Sheet.Header("Text"));
        for (Config.CustomText ct : c().texts) {
            l.add(new Sheet.Action(ct.text.isEmpty() ? "(empty text)" : ct.text, () -> "Edit", () -> minecraft.setScreen(
                new TextEditScreen(this, "Edit text", ct.text, v -> ct.text = v))).chevron());
            l.add(new Sheet.Colour("   Colour", () -> ct.colour, v -> ct.colour = v));
            l.add(new Sheet.Toggle("   Show", () -> c().widget("text:" + ct.id).enabled, v -> c().widget("text:" + ct.id).enabled = v));
            l.add(new Sheet.Toggle("   Bold", () -> ct.bold, v -> ct.bold = v));
            l.add(new Sheet.Toggle("   Background card", () -> ct.background, v -> ct.background = v));
            l.add(new Sheet.Action("   Remove", () -> "", () -> {
                c().texts.remove(ct);
                c().widgets.remove("text:" + ct.id);
                Config.save();
                rebuild();
            }).destructive());
        }
        l.add(new Sheet.Action("Add text", () -> "", () -> minecraft.setScreen(new TextEditScreen(this, "New text", "", v -> {
            if (v.isBlank()) return;
            Config.CustomText ct = new Config.CustomText();
            ct.text = v;
            ct.colour = Config.theme().accent() & 0xFFFFFF;
            c().texts.add(ct);
            c().widget("text:" + ct.id).enabled = true;
            Config.save();
        }))).accent());
        l.add(new Sheet.Footer("Your own labels on screen. Move them in Edit HUD layout. {fps}, {coords}, {ping}, {clock} and friends show live values."));

        l.add(new Sheet.Header("Widgets"));
        for (String id : Config.WIDGETS) {
            Config.Widget w = c().widget(id);
            l.add(new Sheet.Toggle(Config.widgetName(id), () -> w.enabled, v -> w.enabled = v));
        }
        return l;
    }

    private List<Sheet.Item> keystrokes() {
        Config.Keystrokes k = c().keystrokes;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Overlay"));
        l.add(new Sheet.Toggle("Show keystrokes", () -> c().widget("keystrokes").enabled, v -> c().widget("keystrokes").enabled = v));
        l.add(new Sheet.Toggle("Mouse buttons", () -> k.mouse, v -> k.mouse = v));
        l.add(new Sheet.Toggle("Clicks per second", () -> k.cps, v -> k.cps = v));
        l.add(new Sheet.Toggle("Space bar", () -> k.space, v -> k.space = v));
        l.add(new Sheet.Toggle("Key backgrounds", () -> k.background, v -> k.background = v));
        l.add(new Sheet.Header("Size"));
        l.add(Sheet.Slider.ints("Key size", 12, 40, () -> k.keySize, v -> k.keySize = v, " px"));
        l.add(Sheet.Slider.ints("Gap", 0, 6, () -> k.gap, v -> k.gap = v, " px"));
        l.add(Sheet.Slider.ints("Press fade speed", 1, 10, () -> k.fade, v -> k.fade = v, ""));
        l.add(new Sheet.Footer("Pressed keys light up in your accent colour."));
        return l;
    }

    private List<Sheet.Item> trails() {
        Config.Trails tr = c().trails;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Trail"));
        l.add(new Sheet.Toggle("Trails", () -> tr.enabled, v -> tr.enabled = v));
        l.add(new Sheet.Choice<>("Style", Config.TrailType.values(), () -> tr.type, v -> tr.type = v));
        l.add(Sheet.Slider.ints("Density", 1, 10, () -> tr.density, v -> tr.density = v, ""));
        l.add(Sheet.Slider.ints("Spread", 1, 10, () -> tr.spread, v -> tr.spread = v, ""));
        l.add(new Sheet.Toggle("Only when moving", () -> tr.onlyWhenMoving, v -> tr.onlyWhenMoving = v));
        l.add(new Sheet.Toggle("While flying", () -> tr.whileFlying, v -> tr.whileFlying = v));
        l.add(new Sheet.Footer("Only you see trails. Accent Dust uses your accent colour."));
        return l;
    }

    private List<Sheet.Item> ambience() {
        Config.Petals pe = c().petals;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Around you"));
        l.add(new Sheet.Toggle("Ambience", () -> pe.enabled, v -> pe.enabled = v));
        l.add(new Sheet.Choice<>("Type", Config.PetalType.values(), () -> pe.type, v -> pe.type = v));
        l.add(Sheet.Slider.ints("Radius", 2, 16, () -> pe.radius, v -> pe.radius = v, " blocks"));
        l.add(Sheet.Slider.ints("Density", 1, 10, () -> pe.density, v -> pe.density = v, ""));
        l.add(new Sheet.Toggle("Also indoors and underground", () -> pe.indoors, v -> pe.indoors = v));
        l.add(new Sheet.Footer("Spawns only in a bubble around you. Only you see it."));

        return l;
    }

    private List<Sheet.Item> orbs() {
        List<Sheet.Item> l = new ArrayList<>();
        Config.Aura au = c().aura;
        l.add(new Sheet.Header("Orbs"));
        l.add(new Sheet.Toggle("Orbiting orbs", () -> au.orbs, v -> au.orbs = v));
        l.add(new Sheet.Toggle("Fading trails", () -> au.orbTrails, v -> au.orbTrails = v));
        l.add(Sheet.Slider.ints("Orbs", 1, 12, () -> au.orbCount, v -> au.orbCount = v, ""));
        l.add(Sheet.Slider.ints("Orbit speed", 1, 10, () -> au.orbSpeed, v -> au.orbSpeed = v, ""));
        l.add(new Sheet.Toggle("Show orbs in first person", () -> au.orbsFirstPerson, v -> au.orbsFirstPerson = v));
        l.add(new Sheet.Header("Halo and aura"));
        l.add(new Sheet.Toggle("Halo", () -> au.halo, v -> au.halo = v));
        l.add(new Sheet.Choice<>("Halo style", Config.HaloStyle.values(), () -> au.haloStyle, v -> au.haloStyle = v));
        l.add(new Sheet.Toggle("Aura", () -> au.aura, v -> au.aura = v));
        l.add(new Sheet.Choice<>("Colour", Config.Palette.values(), () -> au.colour, v -> au.colour = v));
        l.add(new Sheet.Footer("The halo and aura show in third person (F5); orbs show in first person too."));
        return l;
    }

    private List<Sheet.Item> sky() {
        Config.Sky sk = c().sky;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Sky"));
        l.add(new Sheet.Toggle("Custom sky", () -> sk.enabled, v -> sk.enabled = v));
        l.add(new Sheet.Choice<>("Look", Config.SkyStyle.values(), () -> sk.style, v -> sk.style = v));
        l.add(new Sheet.Choice<>("Colour", Config.Palette.values(), () -> sk.colour, v -> sk.colour = v));
        l.add(Sheet.Slider.ints("Sky cover", 0, 100, () -> sk.opacity, v -> sk.opacity = v, "%"));
        l.add(Sheet.Slider.ints("Lines (topographic)", 3, 16, () -> sk.lines, v -> sk.lines = v, ""));
        l.add(Sheet.Slider.ints("Animation speed", 0, 10, () -> sk.speed, v -> sk.speed = v, ""));
        l.add(Sheet.Slider.ints("Brightness", 0, 99, () -> sk.brightness, v -> sk.brightness = v, "%"));
        l.add(new Sheet.Toggle("Match distance fog", () -> sk.tintFog, v -> sk.tintFog = v));
        l.add(new Sheet.Toggle("Nether and End too", () -> sk.allDimensions, v -> sk.allDimensions = v));
        l.add(new Sheet.Footer("Signature uses each look's own colour; Accent follows your accent colour."));
        return l;
    }

    private List<Sheet.Item> glass() {
        Config.Glass gl = c().glass;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Reflective ground"));
        l.add(new Sheet.Toggle("Glass ground", () -> gl.enabled, v -> {
            gl.enabled = v;
            if (gl.hideGrass) GrassHider.refresh();
        }));
        l.add(Sheet.Slider.ints("Reflection", 0, 100, () -> gl.reflection, v -> gl.reflection = v, "%"));
        l.add(Sheet.Slider.ints("Radius", 8, 48, () -> gl.radius, v -> gl.radius = v, " blocks"));
        l.add(new Sheet.Toggle("Reflect blocks and players", () -> gl.realReflections, v -> gl.realReflections = v));
        l.add(new Sheet.Toggle("Reflective water", () -> gl.water, v -> gl.water = v));
        l.add(new Sheet.Toggle("Hide short and tall grass", () -> gl.hideGrass, v -> {
            gl.hideGrass = v;
            GrassHider.refresh();
        }));
        l.add(new Sheet.Footer("The ground around you is polished glass: it mirrors blocks, players and mobs, with your sky's pattern set into the floor."));
        return l;
    }

    private List<Sheet.Item> wallpaper() {
        Config.Wallpaper wp = c().wallpaper;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Menu background"));
        l.add(new Sheet.Choice<>("Wallpaper", Config.WallpaperMode.values(), () -> wp.mode, v -> wp.mode = v));
        l.add(new Sheet.Choice<>("Colour", Config.Palette.values(), () -> wp.colour, v -> wp.colour = v));
        l.add(Sheet.Slider.ints("Dim", 0, 80, () -> wp.dim, v -> wp.dim = v, "%"));
        l.add(new Sheet.Footer("Shown on the title screen and every menu outside a world."));
        l.add(new Sheet.Header("Your images"));
        List<String> files = Wallpapers.list();
        if (files.isEmpty()) {
            l.add(new Sheet.Info("No images yet", () -> ""));
        } else {
            l.add(new Sheet.Chips(files, () -> wp.image, name -> {
                wp.image = name;
                wp.mode = Config.WallpaperMode.IMAGE;
                Wallpapers.reload();
                Wallpapers.load(name);
            }));
        }
        l.add(new Sheet.Action("Open wallpapers folder", () -> "", () -> Util.getPlatform().openPath(Wallpapers.folder())).accent().chevron());
        l.add(new Sheet.Action("Refresh list", () -> "", () -> {
            Wallpapers.reload();
            rebuild();
        }).accent());
        if (!files.isEmpty()) l.add(new Sheet.Info("Status", () -> Wallpapers.status().isEmpty() ? "Pick an image above" : Wallpapers.status()));
        l.add(new Sheet.Footer("Drop PNG or JPG images into the folder, refresh, then pick one."));
        return l;
    }

    private List<Sheet.Item> spotify() {
        Config.Spotify sp = c().spotify;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Now playing"));
        l.add(new Sheet.Toggle("Show on HUD", () -> c().widget("spotify").enabled, v -> c().widget("spotify").enabled = v));
        l.add(new Sheet.Action("Move it on screen", () -> "", () -> minecraft.setScreen(new LayoutScreen(this))).chevron());
        l.add(new Sheet.Toggle("Album art", () -> sp.showArt, v -> sp.showArt = v));
        l.add(new Sheet.Toggle("Progress bar", () -> sp.showProgress, v -> sp.showProgress = v));
        l.add(new Sheet.Info("Status", Spotify::status));

        Config.Lyrics ly = c().lyrics;
        l.add(new Sheet.Header("Lyrics"));
        l.add(new Sheet.Toggle("Show lyrics on HUD", () -> c().widget("lyrics").enabled, v -> c().widget("lyrics").enabled = v));
        l.add(Sheet.Slider.ints("Lines around", 0, 2, () -> ly.context, v -> ly.context = v, ""));
        l.add(Sheet.Slider.ints("Width", 140, 360, () -> ly.width, v -> ly.width = v, ""));
        l.add(new Sheet.Toggle("Centre the lines", () -> ly.center, v -> ly.center = v));
        l.add(new Sheet.Toggle("Highlight the sung line", () -> ly.accentLine, v -> ly.accentLine = v));
        l.add(new Sheet.Slider("Timing", -3f, 3f, 0.1f, () -> ly.offsetMs / 1000f, v -> ly.offsetMs = Math.round(v * 1000),
            v -> (v > 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.1f s", v)));
        l.add(new Sheet.Info("Lyrics", () -> c().widget("lyrics").enabled
            ? (Spotify.track() == null ? "Play a song" : dev.tony.visuals.spotify.Lyrics.status().isEmpty() ? "From " + dev.tony.visuals.spotify.Lyrics.source() : dev.tony.visuals.spotify.Lyrics.status())
            : "Off"));
        l.add(new Sheet.Toggle("Estimate timing for plain lyrics", () -> ly.approximate, v -> ly.approximate = v));
        l.add(new Sheet.Footer("Lyrics come from LRCLIB (lrclib.net) and NetEase Cloud Music. If they run early or late, nudge Timing."));
        l.add(new Sheet.Header("Controls"));
        l.add(new Sheet.Footer("Open chat (T) and click the buttons on the card, or set keys for play / pause, next and previous in Options → Controls → Flop Visuals."));

        l.add(new Sheet.Header("Source"));
        l.add(new Sheet.Choice<>("Read from", Config.SpotifySource.values(), () -> sp.source, v -> {
            sp.source = v;
            Spotify.note("");
        }));
        if (sp.source == Config.SpotifySource.WINDOWS) {
            l.add(new Sheet.Footer("Free, no setup: reads the song, progress and cover from Windows. Works with the Spotify app and the web player at open.spotify.com in Chrome, Edge or other browsers."));
            return l;
        }
        l.add(new Sheet.Footer("Needs Spotify Premium (since February 2026 Spotify only gives the Web API to Premium accounts)."));
        l.add(new Sheet.Header("Account"));
        l.add(new Sheet.Action("Client ID", () -> sp.clientId.isEmpty() ? "Paste" : mask(sp.clientId), () -> {
            String clip = minecraft.keyboardHandler.getClipboard().replaceAll("\\s", "");
            if (clip.matches("[0-9a-fA-F]{32}")) {
                sp.clientId = clip.toLowerCase(java.util.Locale.ROOT);
                Spotify.note("Client ID saved. Now click Connect Spotify.");
            } else {
                Spotify.note("Error: copy the 32-character Client ID first, then click here");
            }
            rebuild();
        }).chevron());
        if (sp.refreshToken.isEmpty()) {
            l.add(new Sheet.Action("Connect Spotify", () -> "", () -> {
                Spotify.connect();
                rebuild();
            }).accent());
        } else {
            l.add(new Sheet.Action("Disconnect", () -> "", () -> {
                Spotify.disconnect();
                rebuild();
            }).destructive());
        }
        l.add(new Sheet.Header("One-time setup"));
        l.add(new Sheet.Action("Open Spotify Developer Dashboard", () -> "", () ->
            Util.getPlatform().openUri("https://developer.spotify.com/dashboard")).accent().chevron());
        l.add(new Sheet.Footer("1. Create an app. Redirect URI:"));
        l.add(new Sheet.Footer("   " + Spotify.REDIRECT));
        l.add(new Sheet.Footer("2. Tick Web API, save. Under User Management add your Spotify email. Copy the Client ID."));
        l.add(new Sheet.Footer("3. Click Client ID above to paste it, then Connect."));
        return l;
    }

    private static String mask(String id) {
        return id.length() <= 8 ? id : id.substring(0, 4) + "••••" + id.substring(id.length() - 4);
    }

    private List<Sheet.Item> auction() {
        Config.Auction au = c().auction;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Now"));
        l.add(new Sheet.Info("Status", () -> dev.tony.visuals.pay.Auction.active()
            ? dev.tony.visuals.pay.Auction.item() + " · " + (int) Math.ceil(dev.tony.visuals.pay.Auction.secondsLeft()) + "s left"
            : dev.tony.visuals.pay.Auction.result() != null ? dev.tony.visuals.pay.Auction.result() : "No auction running"));
        l.add(new Sheet.Action("Stop auction (no winner)", () -> "", () -> {
            dev.tony.visuals.pay.Auction.cancel();
            rebuild();
        }).destructive());
        l.add(new Sheet.Toggle("Show on HUD", () -> c().widget("auction").enabled, v -> c().widget("auction").enabled = v));
        l.add(new Sheet.Slider("Countdown", 5f, 300f, 5f, () -> (float) au.seconds, v -> au.seconds = Math.round(v),
            v -> Math.round(v) + "s"));
        l.add(new Sheet.Toggle("Announce in public chat", () -> au.announce, v -> au.announce = v));
        l.add(new Sheet.Header("Items"));
        for (String item : new ArrayList<>(au.items)) {
            l.add(new Sheet.Action(item, () -> dev.tony.visuals.pay.Auction.active() ? "" : "Start", () -> {
                if (dev.tony.visuals.pay.Auction.active()) return;
                c().widget("auction").enabled = true;
                Config.save();
                dev.tony.visuals.pay.Auction.start(item);
                onClose();
            }).accent().chevron());
            l.add(new Sheet.Action("   Rename", () -> "", () -> minecraft.setScreen(new TextEditScreen(this, "Item name", item, v -> {
                int i = au.items.indexOf(item);
                if (i >= 0 && !v.isBlank()) au.items.set(i, v.trim());
            }))));
            l.add(new Sheet.Action("   Remove", () -> "", () -> {
                au.items.remove(item);
                Config.save();
                rebuild();
            }).destructive());
        }
        l.add(new Sheet.Action("Add item", () -> "", () -> minecraft.setScreen(new TextEditScreen(this, "New item (e.g. Netherite Ingot)", "", v -> {
            if (!v.isBlank()) au.items.add(v.trim());
            Config.save();
        }))).accent());
        l.add(new Sheet.Footer("Press Start on an item: the countdown begins and every payment counts as a bid (one player's payments add up). When time runs out, the highest total wins: \"<IGN> wins the Netherite Ingot!\". Everyone else is listed in your chat with what to refund. Nothing is ever paid for you."));
        return l;
    }

    private List<Sheet.Item> gamble() {
        Config.Gamble gb = c().gamble;
        List<Sheet.Item> l = new ArrayList<>();
        l.add(new Sheet.Header("Money tracker"));
        l.add(new Sheet.Toggle("Tracking", () -> gb.tracking, v -> gb.tracking = v));
        l.add(new Sheet.Info("Gained / lost", () -> dev.tony.visuals.pay.Tracker.label(gb.profit)
            + (gb.tracking ? "" : " · paused")));
        l.add(new Sheet.Toggle("Show on HUD", () -> c().widget("profit").enabled, v -> c().widget("profit").enabled = v));
        l.add(new Sheet.Toggle("Money graph on HUD", () -> c().widget("moneygraph").enabled, v -> c().widget("moneygraph").enabled = v));
        l.add(new Sheet.Action("Reset to +(0)", () -> "", () -> {
            dev.tony.visuals.pay.Tracker.reset();
            rebuild();
        }).destructive());
        l.add(new Sheet.Footer("While Tracking is on, every \"Name paid you $X\" adds and every \"You paid Name $X\" takes away. Turn it off when you aren't gambling so normal trades don't count."));
        l.add(new Sheet.Header("Payments in chat"));
        l.add(new Sheet.Toggle("Bet buttons on payments", () -> gb.enabled, v -> gb.enabled = v));
        l.add(new Sheet.Action("Minimum bet", () -> "$" + dev.tony.visuals.pay.Payments.money(gb.minimum), () -> minecraft.setScreen(
            new TextEditScreen(this, "Minimum bet (e.g. 1M, 500K)", dev.tony.visuals.pay.Payments.money(gb.minimum), v -> {
                double m = dev.tony.visuals.pay.Payments.parse(v);
                if (m >= 0) gb.minimum = m;
            }))).chevron());
        l.add(new Sheet.Slider("Win pays", 1f, 5f, 0.1f, () -> gb.payout, v -> gb.payout = v,
            v -> String.format(java.util.Locale.ROOT, "%.1f×", v)));
        l.add(new Sheet.Toggle("Stack repeat payments", () -> gb.stack, v -> gb.stack = v));
        l.add(new Sheet.Toggle("[Paper] and [BJ] tags", () -> gb.tags, v -> gb.tags = v));
        l.add(new Sheet.Header("Check it"));
        l.add(new Sheet.Action("Send a test payment to my chat", () -> "", () -> {
            dev.tony.visuals.pay.Payments.handle(net.minecraft.network.chat.Component.literal("TestPlayer paid you $1.1M."));
            onClose();
        }).accent());
        l.add(new Sheet.Info("Last payment found", () -> dev.tony.visuals.pay.Payments.lastMatch().isEmpty() ? "none yet" : dev.tony.visuals.pay.Payments.lastMatch()));
        l.add(new Sheet.Info("Last server message", dev.tony.visuals.pay.Payments::recent));
        l.add(new Sheet.Footer("If a real payment doesn't get buttons, take a screenshot of this page right after it arrives so the exact wording can be matched."));
        l.add(new Sheet.Footer("When someone pays you at least the minimum, the chat line gets [✔] and [✖]. Open chat (T) and click: ✔ = they won (turns lime and shows what to pay back), ✖ = they lost (turns red). Amounts ending in .1 are tagged [Paper], .2 [BJ]. The same person paying the same amount again stacks: \"Name paid you [5] $5M\"; clicking ✔ on a stack lets you pick how many they won."));
        return l;
    }
}
