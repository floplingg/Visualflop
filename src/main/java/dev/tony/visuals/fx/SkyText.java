package dev.tony.visuals.fx;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tony.visuals.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sharp text for the sky: each lyric line is drawn once into a high-resolution image with the real font (smooth
 * edges at any distance) plus a blurred copy for the glow, then shown on one quad in the world.
 */
final class SkyText {
    private SkyText() {}

    static final int EM = 112;

    private static RenderPipeline pipeline(String name, boolean seeThrough) {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("flopvisuals", "pipeline/" + name))
            .withVertexShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/sky_text"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/sky_text"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(seeThrough ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build());
    }

    static final RenderPipeline NORMAL = pipeline("sky_text", false);
    static final RenderPipeline THROUGH = pipeline("sky_text_through", true);

    static void init() {}

    /** One rendered line, three layers stacked: text mask, glow, colour emoji. Sizes in image pixels (one layer). */
    record Line(Identifier id, int w, int h, int pad, float ascent, boolean emoji, RenderType normal, RenderType through) {}

    // ------------------------------------------------------------ fonts

    private record Face(ByteBuffer data, STBTTFontinfo info) {}

    private static final Map<String, java.util.Optional<Face>> FACES = new HashMap<>();

    private static Face load(byte[] bytes) {
        ByteBuffer buf = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();   // kept for the font's lifetime
        int offset = STBTruetype.stbtt_GetFontOffsetForIndex(buf, 0);   // .ttc collections: first font
        STBTTFontinfo info = STBTTFontinfo.malloc();
        if (offset < 0 || !STBTruetype.stbtt_InitFont(info, buf, offset)) {
            MemoryUtil.memFree(buf);
            info.free();
            return null;
        }
        return new Face(buf, info);
    }

    private static Face face(String file) {
        return FACES.computeIfAbsent(file, f -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(Identifier.fromNamespaceAndPath("flopvisuals", "font/" + f)).open()) {
                return java.util.Optional.ofNullable(load(in.readAllBytes()));
            } catch (Exception e) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
    }

    /** A font installed on the computer (for Korean, Japanese and Chinese), or null if it isn't there. */
    private static Face systemFace(String path) {
        return FACES.computeIfAbsent("sys:" + path, f -> {
            try {
                java.nio.file.Path p = java.nio.file.Path.of(path);
                if (!java.nio.file.Files.isRegularFile(p)) return java.util.Optional.empty();
                return java.util.Optional.ofNullable(load(java.nio.file.Files.readAllBytes(p)));
            } catch (Exception e) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
    }

    /** Korean / Japanese / Chinese fonts that come with Windows, macOS and common Linux setups; bold ones first when wanted. */
    private static java.util.List<String> systemFonts(boolean bold) {
        String win = System.getenv().getOrDefault("WINDIR", "C:\\Windows") + "\\Fonts\\";
        java.util.List<String> b = java.util.List.of(win + "malgunbd.ttf", win + "msyhbd.ttc", win + "YuGothB.ttc", win + "meiryob.ttc", win + "msjhbd.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", "/usr/share/fonts/noto-cjk/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Bold.ttc", "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf");
        java.util.List<String> r = java.util.List.of(win + "malgun.ttf", win + "msyh.ttc", win + "YuGothM.ttc", win + "meiryo.ttc", win + "msjh.ttc",
            win + "simhei.ttf", win + "simsun.ttc",
            "/System/Library/Fonts/AppleSDGothicNeo.ttc", "/System/Library/Fonts/PingFang.ttc", "/System/Library/Fonts/Hiragino Sans GB.ttc",
            "/System/Library/Fonts/\u30d2\u30e9\u30ae\u30ce\u89d2\u30b4\u30b7\u30c3\u30af W6.ttc", "/Library/Fonts/Arial Unicode.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc", "/usr/share/fonts/truetype/nanum/NanumGothic.ttf");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (bold) out.addAll(b);
        out.addAll(r);
        if (!bold) out.addAll(b);
        return out;
    }

    // ------------------------------------------------------------ colour emoji (the same Twemoji pictures as the HUD font)

    private record Emoji(int sheet, int col, int row) {}

    private static Map<Integer, Emoji> emojiMap;
    private static final Map<Integer, NativeImage> SHEETS = new HashMap<>();
    private static final int CELL = 64;

    private static Map<Integer, Emoji> emoji() {
        if (emojiMap != null) return emojiMap;
        emojiMap = new HashMap<>();
        try (InputStream in = Minecraft.getInstance().getResourceManager()
            .getResourceOrThrow(Identifier.fromNamespaceAndPath("flopvisuals", "font/emoji.json")).open()) {
            var root = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var providers = root.getAsJsonArray("providers");
            for (int sh = 0; sh < providers.size(); sh++) {
                var rows = providers.get(sh).getAsJsonObject().getAsJsonArray("chars");
                for (int r = 0; r < rows.size(); r++) {
                    int[] cps = rows.get(r).getAsString().codePoints().toArray();
                    for (int c = 0; c < cps.length; c++) if (cps[c] != 0) emojiMap.put(cps[c], new Emoji(sh, c, r));
                }
            }
        } catch (Exception e) {
            // no emoji: they fall back to Minecraft's font
        }
        return emojiMap;
    }

    private static NativeImage sheet(int i) {
        return SHEETS.computeIfAbsent(i, k -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(Identifier.fromNamespaceAndPath("flopvisuals", "textures/font/emoji_" + k + ".png")).open()) {
                return NativeImage.read(in);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Font files for a choice, main first then the extended-Latin part. Null = use Minecraft's own font. */
    static String[] files(Config.SkyFont f) {
        return switch (f) {
            case HEAVY -> new String[]{"manrope-extrabold.ttf", "manrope-extrabold-ext.ttf"};
            case BOLD -> new String[]{"manrope-bold.ttf", "manrope-bold-ext.ttf"};
            case REGULAR -> new String[]{"manrope-medium.ttf", "manrope-medium-ext.ttf"};
            case MONO -> new String[]{"jetbrainsmono-semibold.ttf"};
            case MINECRAFT -> null;
        };
    }

    // ------------------------------------------------------------ cache

    private static final LinkedHashMap<String, Line> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final java.util.Set<String> MISSING = new java.util.HashSet<>();
    private static int counter;

    /** The line as a texture, or null when the font can't draw it (then the caller uses Minecraft's font). */
    static Line get(String text, Config.SkyFont font, float glowSize) {
        String[] files = files(font);
        if (files == null) return null;
        String key = font + "|" + Math.round(glowSize * 10) + "|" + text;
        Line l = CACHE.get(key);
        if (l != null) return l;
        if (MISSING.contains(key)) return null;
        l = build(text, files, glowSize);
        if (l == null) {
            MISSING.add(key);
            return null;
        }
        CACHE.put(key, l);
        while (CACHE.size() > 10) {
            String eldest = CACHE.keySet().iterator().next();
            Line old = CACHE.remove(eldest);
            Minecraft.getInstance().getTextureManager().release(old.id());
        }
        return l;
    }

    private static Line build(String rawText, String[] files, float glowSize) {
        String text = dev.tony.visuals.gui.Kit.clean(rawText);
        java.util.List<Face> faces = new java.util.ArrayList<>();
        for (String f : files) {
            Face fc = face(f);
            if (fc != null) faces.add(fc);
        }
        if (faces.isEmpty()) return null;
        int base = faces.size();
        boolean bold = files[0].contains("bold");
        java.util.List<String> system = systemFonts(bold);
        int sysNext = 0;

        int[] cps = text.codePoints().toArray();
        Face[] use = new Face[cps.length];   // null = colour emoji
        Map<Integer, Emoji> em = emoji();
        for (int i = 0; i < cps.length; i++) {
            int cp = cps[i];
            Face found = null;
            for (Face f : faces) {
                if (cp == ' ' || STBTruetype.stbtt_FindGlyphIndex(f.info(), cp) != 0) {
                    found = f;
                    break;
                }
            }
            if (found == null && em.containsKey(cp)) continue;   // emoji
            // Korean, Japanese, Chinese…: borrow a font installed on the computer.
            while (found == null && sysNext <= system.size()) {
                for (Face f : faces.subList(base, faces.size())) {
                    if (STBTruetype.stbtt_FindGlyphIndex(f.info(), cp) != 0) {
                        found = f;
                        break;
                    }
                }
                if (found != null || sysNext == system.size()) break;
                Face extra = systemFace(system.get(sysNext++));
                if (extra != null) faces.add(extra);
            }
            if (found == null) return null;   // nothing here can draw it: Minecraft's font takes over
            use[i] = found;
        }

        Face main = faces.get(0);
        float mainScale = STBTruetype.stbtt_ScaleForPixelHeight(main.info(), EM);
        float ascent, descent;
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer a = st.mallocInt(1), d = st.mallocInt(1), g = st.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(main.info(), a, d, g);
            ascent = a.get(0) * mainScale;
            descent = -d.get(0) * mainScale;
        }
        float emojiSize = (ascent + descent) * 0.86f;

        // Pen positions. Other fonts are scaled to the same height as the main one.
        float[] xs = new float[cps.length];
        float[] scales = new float[cps.length];
        float pen = 0;
        boolean anyEmoji = false;
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer adv = st.mallocInt(1), lsb = st.mallocInt(1);
            for (int i = 0; i < cps.length; i++) {
                xs[i] = pen;
                if (use[i] == null) {
                    anyEmoji = true;
                    pen += emojiSize + EM * 0.08f;
                    continue;
                }
                float sc = STBTruetype.stbtt_ScaleForPixelHeight(use[i].info(), EM);
                if (use[i] != main) sc *= 0.94f;   // CJK fonts run a little large
                scales[i] = sc;
                STBTruetype.stbtt_GetCodepointHMetrics(use[i].info(), cps[i], adv, lsb);
                pen += adv.get(0) * sc;
                if (i + 1 < cps.length && use[i + 1] == use[i]) {
                    pen += STBTruetype.stbtt_GetCodepointKernAdvance(use[i].info(), cps[i], cps[i + 1]) * sc;
                }
            }
        }
        int pad = Math.round(14 + 26 * glowSize);
        int w = (int) Math.ceil(pen) + 2 * pad, h = (int) Math.ceil(ascent + descent) + 2 * pad;
        if (w > 8192) return null;
        float[] mask = new float[w * h];
        int[] colour = anyEmoji ? new int[w * h] : null;
        int baseline = Math.round(pad + ascent);
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer x0 = st.mallocInt(1), y0 = st.mallocInt(1), x1 = st.mallocInt(1), y1 = st.mallocInt(1);
            for (int i = 0; i < cps.length; i++) {
                if (cps[i] == ' ') continue;
                if (use[i] == null) {
                    paintEmoji(em.get(cps[i]), colour, mask, w, h, Math.round(pad + xs[i]), Math.round(baseline - ascent * 0.86f), Math.round(emojiSize));
                    continue;
                }
                float sc = scales[i];
                float fx = pad + xs[i];
                float shift = fx - (float) Math.floor(fx);
                STBTruetype.stbtt_GetCodepointBitmapBoxSubpixel(use[i].info(), cps[i], sc, sc, shift, 0, x0, y0, x1, y1);
                int gw = x1.get(0) - x0.get(0), gh = y1.get(0) - y0.get(0);
                if (gw <= 0 || gh <= 0) continue;
                ByteBuffer bmp = MemoryUtil.memAlloc(gw * gh);
                try {
                    STBTruetype.stbtt_MakeCodepointBitmapSubpixel(use[i].info(), bmp, gw, gh, gw, sc, sc, shift, 0, cps[i]);
                    int ox = (int) Math.floor(fx) + x0.get(0), oy = baseline + y0.get(0);
                    for (int yy = 0; yy < gh; yy++) {
                        int ty = oy + yy;
                        if (ty < 0 || ty >= h) continue;
                        for (int xx = 0; xx < gw; xx++) {
                            int tx = ox + xx;
                            if (tx < 0 || tx >= w) continue;
                            float v = (bmp.get(yy * gw + xx) & 255) / 255f;
                            int k = ty * w + tx;
                            mask[k] = Math.min(1, mask[k] + v);
                        }
                    }
                } finally {
                    MemoryUtil.memFree(bmp);
                }
            }
        }
        // Glow: the letters (and emoji shapes) blurred wide and soft (three box blurs ≈ a gaussian).
        float[] glow = mask.clone();
        if (colour != null) for (int k = 0; k < glow.length; k++) glow[k] = Math.max(glow[k], ((colour[k] >>> 24) & 255) / 255f);
        int r = Math.max(2, Math.round(9 * glowSize));
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(glow, w, h, r, true);
            boxBlur(glow, w, h, r, false);
        }
        float peak = 0;
        for (float v : glow) peak = Math.max(peak, v);
        float boost = peak > 0 ? Math.min(3f, 0.85f / peak) : 1;

        NativeImage img = new NativeImage(w, h * 3, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int k = y * w + x;
                int a = Math.round(Math.min(1, mask[k]) * 255);
                img.setPixel(x, y, (a << 24) | 0xFFFFFF);
                int b = Math.round(Math.min(1, glow[k] * boost) * 255);
                img.setPixel(x, y + h, (b << 24) | 0xFFFFFF);
                img.setPixel(x, y + 2 * h, colour == null ? 0 : colour[k]);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("flopvisuals", "sky/line_" + (counter++));
        DynamicTexture tex = new DynamicTexture(() -> "flopvisuals sky lyric", img) {
            {
                this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            }
        };
        Minecraft.getInstance().getTextureManager().register(id, tex);
        RenderType normal = RenderType.create("flopvisuals_sky_text", RenderSetup.builder(NORMAL)
            .withTexture("Sampler0", id, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)).createRenderSetup());
        RenderType through = RenderType.create("flopvisuals_sky_text_through", RenderSetup.builder(THROUGH)
            .withTexture("Sampler0", id, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)).createRenderSetup());
        return new Line(id, w, h, pad, ascent, anyEmoji, normal, through);
    }

    /** Copies one emoji, smoothly resized, into the colour layer (ARGB) at (x, y), size s. */
    private static void paintEmoji(Emoji e, int[] colour, float[] mask, int w, int h, int x, int y, int s) {
        NativeImage sheet = sheet(e.sheet());
        if (sheet == null || s <= 0) return;
        float k = (CELL - 4) / (float) s;
        int sx0 = e.col() * CELL + 2, sy0 = e.row() * CELL + 2;
        for (int yy = 0; yy < s; yy++) {
            int ty = y + yy;
            if (ty < 0 || ty >= h) continue;
            for (int xx = 0; xx < s; xx++) {
                int tx = x + xx;
                if (tx < 0 || tx >= w) continue;
                // Bilinear sample.
                float fx = sx0 + (xx + 0.5f) * k - 0.5f, fy = sy0 + (yy + 0.5f) * k - 0.5f;
                int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
                float ax = fx - ix, ay = fy - iy;
                float[] acc = new float[4];
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int px = Math.max(sx0, Math.min(sx0 + CELL - 5, ix + dx)), py = Math.max(sy0, Math.min(sy0 + CELL - 5, iy + dy));
                        int c = sheet.getPixel(px, py);   // ARGB
                        float wgt = (dx == 0 ? 1 - ax : ax) * (dy == 0 ? 1 - ay : ay);
                        float a = ((c >>> 24) & 255) / 255f;
                        acc[0] += a * wgt;
                        acc[1] += ((c >> 16) & 255) * a * wgt;
                        acc[2] += ((c >> 8) & 255) * a * wgt;
                        acc[3] += (c & 255) * a * wgt;
                    }
                }
                if (acc[0] <= 0.002f) continue;
                int a = Math.round(acc[0] * 255);
                int rr = Math.round(acc[1] / acc[0]), gg = Math.round(acc[2] / acc[0]), bb = Math.round(acc[3] / acc[0]);
                colour[ty * w + tx] = (a << 24) | (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
            }
        }
    }

    private static void boxBlur(float[] a, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        float[] row = new float[len];
        float inv = 1f / (2 * r + 1);
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) row[i] = a[horizontal ? l * w + i : i * w + l];
            float sum = 0;
            for (int i = -r; i <= r; i++) sum += row[Math.max(0, Math.min(len - 1, i))];
            for (int i = 0; i < len; i++) {
                a[horizontal ? l * w + i : i * w + l] = sum * inv;
                sum += row[Math.min(len - 1, i + r + 1)] - row[Math.max(0, i - r)];
            }
        }
    }

    // ------------------------------------------------------------ drawing

    /**
     * Draws the line centred on m's origin (m maps image pixels to world space, y down). Glow first, then the text,
     * then a faint copy that shows through walls.
     */
    static void draw(MultiBufferSource source, Line l, Matrix4f m, float dy, int rgb, float alpha, int glowRgb, float glowAlpha) {
        float x0 = -l.w() / 2f, y0 = dy - l.pad() - l.ascent() * 0.5f, x1 = x0 + l.w(), y1 = y0 + l.h();
        float third = 1f / 3f;
        VertexConsumer vc = source.getBuffer(l.normal());
        if (glowRgb >= 0 && glowAlpha > 0.003f) quad(vc, m, x0, y0, x1, y1, third, 2 * third, glowRgb, glowAlpha);
        quad(vc, m, x0, y0, x1, y1, 0f, third, rgb, alpha);
        if (l.emoji()) quad(vc, m, x0, y0, x1, y1, 2 * third, 1f, 0xFFFFFF, alpha);
        quad(source.getBuffer(l.through()), m, x0, y0, x1, y1, 0f, third, rgb, alpha * 0.22f);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float v0, float v1, int rgb, float a) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        Vector3f p = new Vector3f();
        m.transformPosition(x0, y0, 0, p); vc.addVertex(p.x, p.y, p.z).setUv(0, v0).setColor(r, g, b, a);
        m.transformPosition(x0, y1, 0, p); vc.addVertex(p.x, p.y, p.z).setUv(0, v1).setColor(r, g, b, a);
        m.transformPosition(x1, y1, 0, p); vc.addVertex(p.x, p.y, p.z).setUv(1, v1).setColor(r, g, b, a);
        m.transformPosition(x1, y0, 0, p); vc.addVertex(p.x, p.y, p.z).setUv(1, v0).setColor(r, g, b, a);
    }
}
