#version 330

#moj_import <flopvisuals:topo.glsl>
#moj_import <flopvisuals:styles.glsl>

// Animated menu wallpaper in any sky look. Colour and look come from a 2x1 texture,
// time from the vertex colour (24-bit counter in 1/100 s steps).
uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec3 accent = texelFetch(Sampler0, ivec2(0, 0), 0).rgb;
    int style = int(floor(texelFetch(Sampler0, ivec2(1, 0), 0).r * 255.0 / 40.0 + 0.5));
    vec3 c = floor(vertexColor.rgb * 255.0 + 0.5);
    float t = (c.r * 65536.0 + c.g * 256.0 + c.b) / 100.0;
    float aspect = abs(dFdy(texCoord0.y)) / max(abs(dFdx(texCoord0.x)), 1e-6);
    vec2 uv = texCoord0 - 0.5;

    if (style != 0) {
        // Look slightly upwards at the sky dome through the screen.
        vec3 d = normalize(vec3(uv.x * aspect * 1.15, 0.32 - uv.y * 1.05, 1.0));
        vec3 colour = fv_sky(style, d, t, accent, 0.62);
        float vignette = smoothstep(1.15, 0.2, length(uv * vec2(aspect * 0.75, 1.0)));
        fragColor = vec4(colour * mix(0.75, 1.0, vignette) + fv_dither(gl_FragCoord.xy), 1.0);
        return;
    }

    vec3 p = vec3(uv.x * aspect, uv.y, 0.0) * 1.25 + vec3(0.0, 0.0, 4.0);
    float h = topo_height(p, t);
    vec2 l = topo_lines(h, 6.0, t);

    float vignette = smoothstep(1.05, 0.15, length(uv * vec2(aspect * 0.8, 1.0)));
    vec3 base = mix(accent * 0.035 + vec3(0.006), accent * 0.20 + vec3(0.015), vignette);
    vec3 lineColour = mix(accent, vec3(1.0), 0.4 * l.x);
    vec3 colour = base + lineColour * clamp(l.x * 0.85 + l.y * 0.6, 0.0, 1.0) * mix(0.5, 0.95, vignette);
    fragColor = vec4(colour + fv_dither(gl_FragCoord.xy), 1.0);
}
