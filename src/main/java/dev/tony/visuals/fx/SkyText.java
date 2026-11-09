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

    /** One rendered line: text in the top half of the image, glow in the bottom half. Sizes in image pixels. */
    record Line(Identifier id, int w, int h, int pad, float ascent, RenderType normal, RenderType through) {}

    // ------------------------------------------------------------ fonts

    private record Face(ByteBuffer data, STBTTFontinfo info) {}

    private static final Map<String, Face> FACES = new HashMap<>();

    private static Face face(String file) {
        return FACES.computeIfAbsent(file, f -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(Identifier.fromNamespaceAndPath("flopvisuals", "font/" + f)).open()) {
                byte[] bytes = in.readAllBytes();
                ByteBuffer buf = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();   // kept for the font's lifetime
                STBTTFontinfo info = STBTTFontinfo.malloc();
                if (!STBTruetype.stbtt_InitFont(info, buf)) return null;
                return new Face(buf, info);
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

    private static Line build(String text, String[] files, float glowSize) {
        Face[] faces = new Face[files.length];
        for (int i = 0; i < files.length; i++) faces[i] = face(files[i]);
        if (faces[0] == null) return null;

        int[] cps = text.codePoints().toArray();
        Face[] use = new Face[cps.length];
        float[] scale = new float[faces.length];
        for (int i = 0; i < faces.length; i++) if (faces[i] != null) scale[i] = STBTruetype.stbtt_ScaleForPixelHeight(faces[i].info(), EM);
        int[] which = new int[cps.length];
        for (int i = 0; i < cps.length; i++) {
            int found = -1;
            for (int f = 0; f < faces.length && found < 0; f++) {
                if (faces[f] != null && (cps[i] == ' ' || STBTruetype.stbtt_FindGlyphIndex(faces[f].info(), cps[i]) != 0)) found = f;
            }
            if (found < 0) return null;   // e.g. Chinese or Korean: fall back to Minecraft's font
            which[i] = found;
            use[i] = faces[found];
        }

        float ascent, descent;
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer a = st.mallocInt(1), d = st.mallocInt(1), g = st.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(faces[0].info(), a, d, g);
            ascent = a.get(0) * scale[0];
            descent = -d.get(0) * scale[0];
        }

        // Pen positions.
        float[] xs = new float[cps.length];
        float pen = 0;
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer adv = st.mallocInt(1), lsb = st.mallocInt(1);
            for (int i = 0; i < cps.length; i++) {
                xs[i] = pen;
                STBTruetype.stbtt_GetCodepointHMetrics(use[i].info(), cps[i], adv, lsb);
                pen += adv.get(0) * scale[which[i]];
                if (i + 1 < cps.length && which[i + 1] == which[i]) {
                    pen += STBTruetype.stbtt_GetCodepointKernAdvance(use[i].info(), cps[i], cps[i + 1]) * scale[which[i]];
                }
            }
        }
        int pad = Math.round(14 + 26 * glowSize);
        int w = (int) Math.ceil(pen) + 2 * pad, h = (int) Math.ceil(ascent + descent) + 2 * pad;
        if (w > 8192) return null;
        float[] mask = new float[w * h];
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer x0 = st.mallocInt(1), y0 = st.mallocInt(1), x1 = st.mallocInt(1), y1 = st.mallocInt(1);
            for (int i = 0; i < cps.length; i++) {
                if (cps[i] == ' ') continue;
                float sc = scale[which[i]];
                float fx = pad + xs[i];
                float shift = fx - (float) Math.floor(fx);
                STBTruetype.stbtt_GetCodepointBitmapBoxSubpixel(use[i].info(), cps[i], sc, sc, shift, 0, x0, y0, x1, y1);
                int gw = x1.get(0) - x0.get(0), gh = y1.get(0) - y0.get(0);
                if (gw <= 0 || gh <= 0) continue;
                ByteBuffer bmp = MemoryUtil.memAlloc(gw * gh);
                try {
                    STBTruetype.stbtt_MakeCodepointBitmapSubpixel(use[i].info(), bmp, gw, gh, gw, sc, sc, shift, 0, cps[i]);
                    int ox = (int) Math.floor(fx) + x0.get(0), oy = Math.round(pad + ascent) + y0.get(0);
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
        // Glow: the letters blurred wide and soft (three box blurs ≈ a gaussian).
        float[] glow = mask.clone();
        int r = Math.max(2, Math.round(9 * glowSize));
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(glow, w, h, r, true);
            boxBlur(glow, w, h, r, false);
        }
        float peak = 0;
        for (float v : glow) peak = Math.max(peak, v);
        float boost = peak > 0 ? Math.min(3f, 0.85f / peak) : 1;

        NativeImage img = new NativeImage(w, h * 2, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = Math.round(Math.min(1, mask[y * w + x]) * 255);
                img.setPixel(x, y, (a << 24) | 0xFFFFFF);
                int b = Math.round(Math.min(1, glow[y * w + x] * boost) * 255);
                img.setPixel(x, y + h, (b << 24) | 0xFFFFFF);
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
        return new Line(id, w, h, pad, ascent, normal, through);
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
        VertexConsumer vc = source.getBuffer(l.normal());
        if (glowRgb >= 0 && glowAlpha > 0.003f) quad(vc, m, x0, y0, x1, y1, 0.5f, 1f, glowRgb, glowAlpha);
        quad(vc, m, x0, y0, x1, y1, 0f, 0.5f, rgb, alpha);
        quad(source.getBuffer(l.through()), m, x0, y0, x1, y1, 0f, 0.5f, rgb, alpha * 0.22f);
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
