package dev.tony.visuals.wallpaper;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import dev.tony.visuals.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Menu wallpapers: an animated topographic background (GPU shader) or your own PNG images. */
public final class Wallpapers {
    private Wallpapers() {}

    public static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("flopvisuals", "pipeline/topo_wall"))
            .withVertexShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/topo_wall"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/topo_wall"))
            .build());

    private static final Identifier ACCENT_TEX = Identifier.fromNamespaceAndPath("flopvisuals", "wallpaper/accent");
    private static final Identifier IMAGE_TEX = Identifier.fromNamespaceAndPath("flopvisuals", "wallpaper/image");
    private static final long START = System.nanoTime();
    private static DynamicTexture accentTex;
    private static int accentFor = -1, styleFor = -1;
    private static String loaded;
    private static int imgW, imgH;
    /** What happened with the chosen image, shown in the menu. */
    private static String status = "";
    private static final String[] TYPES = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};

    public static void init() {}

    public static Path folder() {
        Path p = Config.DIR.resolve("wallpapers");
        try {
            Files.createDirectories(p);
        } catch (Exception ignored) {
        }
        return p;
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder())) {
            s.filter(f -> isImage(f.getFileName().toString()))
                .sorted().forEach(f -> out.add(f.getFileName().toString()));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String t : TYPES) if (n.endsWith(t)) return true;
        return false;
    }

    public static void reload() {
        loaded = null;
        status = "";
    }

    public static String status() {
        return status;
    }

    /** Draws the wallpaper. Returns false to fall back to the vanilla panorama. */
    public static boolean draw(GuiGraphics g, int w, int h) {
        Config.Wallpaper cfg = Config.get().wallpaper;
        switch (cfg.mode) {
            case VANILLA -> { return false; }
            case IMAGE -> {
                if (!drawImage(g, w, h, cfg.image)) drawTopo(g, w, h, 0);
            }
            default -> drawTopo(g, w, h, cfg.mode.ordinal());
        }
        if (cfg.dim > 0) g.fill(0, 0, w, h, Math.round(cfg.dim * 2.55f) << 24);
        return true;
    }

    private static void drawTopo(GuiGraphics g, int w, int h, int style) {
        Config.SkyStyle look = Config.SkyStyle.values()[Math.max(0, Math.min(Config.SkyStyle.values().length - 1, style))];
        int accent = dev.tony.visuals.sky.SkyStyles.colour(look, Config.get().wallpaper.colour) & 0xFFFFFF;
        if (accentTex == null) {
            // Pixel 0: colour. Pixel 1: style in red.
            accentTex = new DynamicTexture(() -> "flopvisuals wallpaper accent", new NativeImage(2, 1, false));
            Minecraft.getInstance().getTextureManager().register(ACCENT_TEX, accentTex);
        }
        if (accent != accentFor || style != styleFor) {
            accentFor = accent;
            styleFor = style;
            accentTex.getPixels().setPixel(0, 0, 0xFF000000 | accent);
            accentTex.getPixels().setPixel(1, 0, 0xFF000000 | ((style * 40) << 16));
            accentTex.upload();
        }
        // Time travels in the vertex colour (24 bits, 1/100 s steps, so the motion is smooth).
        long ticks = ((System.nanoTime() - START) / 10_000_000L) & 0xFFFFFF;
        int timeColour = 0xFF000000 | (int) ticks;
        g.blit(PIPELINE, ACCENT_TEX, 0, 0, 0f, 0f, w, h, w, h, timeColour);
    }

    /** Loads the image if it isn't already. Returns true when it's ready to draw. */
    public static boolean load(String name) {
        if (name == null || name.isEmpty()) return false;
        if (!name.equals(loaded)) {
            loaded = name;
            imgW = imgH = 0;
            Path f = folder().resolve(name);
            try {
                NativeImage img = readImage(f);
                imgW = img.getWidth();
                imgH = img.getHeight();
                // Smooth filtering (Minecraft's default is blocky nearest-pixel sampling).
                DynamicTexture tex = new DynamicTexture(() -> "flopvisuals wallpaper " + name, img) {
                    {
                        this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                            .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
                    }
                };
                Minecraft.getInstance().getTextureManager().register(IMAGE_TEX, tex);
                status = "Showing " + name + " (" + imgW + " × " + imgH + ")";
            } catch (Throwable e) {
                imgW = imgH = 0;
                status = "Couldn't open " + name + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                org.slf4j.LoggerFactory.getLogger("flopvisuals").warn("Wallpaper {} failed to load", name, e);
                return false;
            }
        }
        return imgW > 0;
    }

    /** PNGs load directly; JPG, BMP and GIF are converted to PNG first. Huge photos are scaled down to 4K. */
    private static NativeImage readImage(Path f) throws Exception {
        byte[] bytes = Files.readAllBytes(f);
        boolean png = bytes.length > 8 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        try {
            return convert(bytes);
        } catch (Throwable e) {
            if (!png) throw e;
            return NativeImage.read(bytes);   // no Java imaging available: load the PNG as it is
        }
    }

    private static NativeImage convert(byte[] bytes) throws Exception {
        java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (src == null) throw new IllegalArgumentException("not a supported image (use PNG or JPG)");
        // Scale to cover the window (never up), halving in steps for a clean, sharp result.
        var win = Minecraft.getInstance().getWindow();
        int sw = Math.max(640, win.getWidth()), sh = Math.max(360, win.getHeight());
        int w = src.getWidth(), h = src.getHeight();
        float k = Math.min(1f, Math.max(sw / (float) w, sh / (float) h));
        int nw = Math.max(1, Math.round(w * k)), nh = Math.max(1, Math.round(h * k));
        java.awt.image.BufferedImage cur = src;
        int cw = w, ch = h;
        while (cw / 2 >= nw && ch / 2 >= nh) {
            cw /= 2;
            ch /= 2;
            cur = scale(cur, cw, ch);
        }
        java.awt.image.BufferedImage rgb = scale(cur, nw, nh);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(rgb, "png", out);
        return NativeImage.read(out.toByteArray());
    }

    private static java.awt.image.BufferedImage scale(java.awt.image.BufferedImage in, int w, int h) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D gr = out.createGraphics();
        gr.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gr.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        gr.drawImage(in, 0, 0, w, h, null);
        gr.dispose();
        return out;
    }

    private static boolean drawImage(GuiGraphics g, int w, int h, String name) {
        if (!load(name)) return false;
        // Cover: fill the screen, crop the overflow, keep the centre.
        float scale = Math.max(w / (float) imgW, h / (float) imgH);
        int uw = Math.round(w / scale), vh = Math.round(h / scale);
        float u = (imgW - uw) / 2f, v = (imgH - vh) / 2f;
        g.blit(RenderPipelines.GUI_TEXTURED, IMAGE_TEX, 0, 0, u, v, w, h, uw, vh, imgW, imgH);
        return true;
    }
}
