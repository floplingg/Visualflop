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
    private static int accentFor = -1;
    private static String loaded;
    private static int imgW, imgH;

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
            s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .sorted().forEach(f -> out.add(f.getFileName().toString()));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void reload() {
        loaded = null;
    }

    /** Draws the wallpaper. Returns false to fall back to the vanilla panorama. */
    public static boolean draw(GuiGraphics g, int w, int h) {
        Config.Wallpaper cfg = Config.get().wallpaper;
        switch (cfg.mode) {
            case VANILLA -> { return false; }
            case IMAGE -> {
                if (!drawImage(g, w, h, cfg.image)) drawTopo(g, w, h);
            }
            default -> drawTopo(g, w, h);
        }
        if (cfg.dim > 0) g.fill(0, 0, w, h, Math.round(cfg.dim * 2.55f) << 24);
        return true;
    }

    private static void drawTopo(GuiGraphics g, int w, int h) {
        int accent = Config.theme().accent() & 0xFFFFFF;
        if (accentTex == null) {
            accentTex = new DynamicTexture(() -> "flopvisuals wallpaper accent", new NativeImage(1, 1, false));
            Minecraft.getInstance().getTextureManager().register(ACCENT_TEX, accentTex);
        }
        if (accent != accentFor) {
            accentFor = accent;
            accentTex.getPixels().setPixel(0, 0, 0xFF000000 | accent);
            accentTex.upload();
        }
        // Time travels in the vertex colour (24 bits, 1/20 s steps), the accent in the 1x1 texture.
        long ticks = ((System.nanoTime() - START) / 50_000_000L) & 0xFFFFFF;
        int timeColour = 0xFF000000 | (int) ticks;
        g.blit(PIPELINE, ACCENT_TEX, 0, 0, 0f, 0f, w, h, w, h, timeColour);
    }

    private static boolean drawImage(GuiGraphics g, int w, int h, String name) {
        if (name == null || name.isEmpty()) return false;
        if (!name.equals(loaded)) {
            loaded = name;
            imgW = imgH = 0;
            Path f = folder().resolve(name);
            try (InputStream in = Files.newInputStream(f)) {
                NativeImage img = NativeImage.read(in);
                imgW = img.getWidth();
                imgH = img.getHeight();
                Minecraft.getInstance().getTextureManager().register(IMAGE_TEX, new DynamicTexture(() -> "flopvisuals wallpaper " + name, img));
            } catch (Exception e) {
                return false;
            }
        }
        if (imgW <= 0) return false;
        // Cover: fill the screen, crop the overflow, keep the centre.
        float scale = Math.max(w / (float) imgW, h / (float) imgH);
        int uw = Math.round(w / scale), vh = Math.round(h / scale);
        float u = (imgW - uw) / 2f, v = (imgH - vh) / 2f;
        g.blit(RenderPipelines.GUI_TEXTURED, IMAGE_TEX, 0, 0, u, v, w, h, uw, vh, imgW, imgH);
        return true;
    }
}
