#version 330
#extension GL_ARB_separate_shader_objects : require

#include <flopvisuals:topo.glsl>
#include <flopvisuals:styles.glsl>

// Flop Visuals animated sky: topographic lines, galaxy, aether, nocturne or amethyst.
layout(location = 0) in vec3 skyDir;
layout(location = 1) in vec2 params;
layout(location = 2) in vec4 tint;
layout(location = 3) in vec3 camPos;
layout(location = 4) flat in int style;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 d = normalize(skyDir);
    float t = params.x;
    float levels = floor(params.y);
    float brightness = fract(params.y) * 1.25;
    float cover = tint.a;
    if (style != 0) {
        vec3 colour = fv_sky(style, d, t, tint.rgb, fract(params.y));
        fragColor = vec4(colour + fv_dither(gl_FragCoord.xy), cover);
        return;
    }
    vec3 base = topo_base(d, brightness, tint.rgb);
    vec3 l = topo_dir(d, t, levels);
    float amount = clamp((l.x + l.y) * l.z, 0.0, 1.0);
    vec3 lineColour = topo_glow_colour(tint.rgb, brightness, l.x);
    float a = clamp(cover + amount * (1.0 - cover), 0.0, 1.0);
    vec3 colour = (base * cover + lineColour * amount) / max(a, 1e-4);
    fragColor = vec4(colour + fv_dither(gl_FragCoord.xy), a);
}
