package dev.tony.visuals.wallpaper;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns any wallpaper into a 3840 × 2160 master: cropped to 16:9 around the centre, enlarged in gentle steps with
 * bicubic filtering (much cleaner than the GPU's stretch), lightly de-blocked and sharpened.
 * The result is cached as a PNG so it's only done once per picture.
 *
 * Note: enlarging can't invent detail a small picture never had; it makes it smooth and crisp instead of blurry and blocky.
 */
final class Upscale4K {
    private Upscale4K() {}

    static final int W = 3840, H = 2160;

    static BufferedImage master(Path source, Path cacheDir) throws Exception {
        String name = source.getFileName().toString();
        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        Path cached = cacheDir.resolve(base + ".png");
        if (Files.exists(cached) && Files.getLastModifiedTime(cached).compareTo(Files.getLastModifiedTime(source)) >= 0) {
            BufferedImage img = ImageIO.read(cached.toFile());
            if (img != null && img.getWidth() == W && img.getHeight() == H) return img;
        }
        BufferedImage src = ImageIO.read(source.toFile());
        if (src == null) throw new IllegalArgumentException("not a supported image (use PNG or JPG)");
        BufferedImage out = convert(src);
        try {
            Files.createDirectories(cacheDir);
            ImageIO.write(out, "png", cached.toFile());
        } catch (Exception ignored) {
            // Read-only folder: still show it, just don't cache.
        }
        return out;
    }

    static BufferedImage convert(BufferedImage src) {
        // Crop to 16:9 around the centre so nothing gets stretched.
        int w = src.getWidth(), h = src.getHeight();
        int cw = w, ch = Math.round(w * 9f / 16f);
        if (ch > h) {
            ch = h;
            cw = Math.round(h * 16f / 9f);
        }
        BufferedImage cur = copy(src.getSubimage((w - cw) / 2, (h - ch) / 2, cw, ch));
        boolean enlarging = cw < W;
        if (enlarging) cur = deblock(cur);   // soften JPEG blocks before they get magnified
        // Change size in steps of at most 2x for clean edges either way.
        while (cur.getWidth() != W) {
            int nw = cur.getWidth() < W ? Math.min(W, cur.getWidth() * 2) : Math.max(W, cur.getWidth() / 2);
            int nh = Math.round(nw * 9f / 16f);
            cur = resize(cur, nw, nh);
        }
        if (cur.getHeight() != H) cur = resize(cur, W, H);
        if (enlarging) cur = sharpen(cur, Math.min(0.9f, 0.35f + 0.25f * (W / (float) cw - 1)));
        return cur;
    }

    static BufferedImage resize(BufferedImage in, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(in, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage copy(BufferedImage in) {
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(in, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Edge-preserving smoothing: averages only with neighbours of similar colour, which hides JPEG blocks and noise. */
    private static BufferedImage deblock(BufferedImage in) {
        int w = in.getWidth(), h = in.getHeight();
        int[] px = in.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
                float sr = 0, sg = 0, sb = 0, sw = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = Math.max(0, Math.min(h - 1, y + dy));
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.max(0, Math.min(w - 1, x + dx));
                        int n = px[yy * w + xx];
                        int nr = (n >> 16) & 255, ng = (n >> 8) & 255, nb = n & 255;
                        int diff = Math.abs(nr - cr) + Math.abs(ng - cg) + Math.abs(nb - cb);
                        float wt = (float) Math.exp(-diff * diff / 1800.0) * (dx == 0 && dy == 0 ? 1f : 0.6f);
                        sr += nr * wt;
                        sg += ng * wt;
                        sb += nb * wt;
                        sw += wt;
                    }
                }
                out[y * w + x] = 0xFF000000 | (Math.round(sr / sw) << 16) | (Math.round(sg / sw) << 8) | Math.round(sb / sw);
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, out, 0, w);
        return img;
    }

    /** Unsharp mask (blur radius ~2 px at 4K) so the enlarged picture looks crisp rather than soft. */
    private static BufferedImage sharpen(BufferedImage in, float amount) {
        int w = in.getWidth(), h = in.getHeight();
        int[] px = in.getRGB(0, 0, w, h, null, 0, w);
        float[] k = {0.06f, 0.24f, 0.4f, 0.24f, 0.06f};
        int[] tmp = new int[px.length];
        // Separable blur: horizontal then vertical, per channel packed back into ints.
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float r = 0, g = 0, b = 0;
                for (int i = -2; i <= 2; i++) {
                    int c = px[row + Math.max(0, Math.min(w - 1, x + i))];
                    float kk = k[i + 2];
                    r += ((c >> 16) & 255) * kk;
                    g += ((c >> 8) & 255) * kk;
                    b += (c & 255) * kk;
                }
                tmp[row + x] = (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b);
            }
        }
        int[] out = new int[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float r = 0, g = 0, b = 0;
                for (int i = -2; i <= 2; i++) {
                    int c = tmp[Math.max(0, Math.min(h - 1, y + i)) * w + x];
                    float kk = k[i + 2];
                    r += ((c >> 16) & 255) * kk;
                    g += ((c >> 8) & 255) * kk;
                    b += (c & 255) * kk;
                }
                int o = px[y * w + x];
                int or = (o >> 16) & 255, og = (o >> 8) & 255, ob = o & 255;
                int nr = clamp(Math.round(or + (or - r) * amount));
                int ng = clamp(Math.round(og + (og - g) * amount));
                int nb = clamp(Math.round(ob + (ob - b) * amount));
                out[y * w + x] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, out, 0, w);
        return img;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
