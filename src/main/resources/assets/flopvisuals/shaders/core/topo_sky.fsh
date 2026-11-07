#version 330

#moj_import <flopvisuals:topo.glsl>
#moj_import <flopvisuals:styles.glsl>

// Flop Visuals animated sky: topographic lines, galaxy, aether, nocturne or amethyst.
in vec3 skyDir;
in vec2 params;
in vec4 tint;
in vec3 camPos;
flat in int style;

out vec4 fragColor;

void main() {
    vec3 d = normalize(skyDir);
    float t = params.x;
    float levels = floor(params.y);
    float brightness = fract(params.y) * 1.25;
    float cover = tint.a;
    if (style != 0) {
        vec3 colour = fv_sky(style, d, t, tint.rgb, fract(params.y));
        fragColor = vec4(colour, cover);
        return;
    }
    vec3 base = topo_base(d, brightness, tint.rgb);
    vec3 l = topo_dir(d, t, levels);
    float amount = clamp((l.x + l.y) * l.z, 0.0, 1.0);
    vec3 lineColour = topo_glow_colour(tint.rgb, brightness, l.x);
    float a = clamp(cover + amount * (1.0 - cover), 0.0, 1.0);
    vec3 colour = (base * cover + lineColour * amount) / max(a, 1e-4);
    fragColor = vec4(colour, a);
}
