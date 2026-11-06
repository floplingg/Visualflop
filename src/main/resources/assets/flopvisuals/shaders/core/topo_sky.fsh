#version 330

#moj_import <flopvisuals:topo.glsl>

// Flop Visuals animated topographic sky. Lines live on a world-anchored ceiling high above you.
in vec3 skyDir;
in vec2 params;
in vec4 tint;
in vec3 camPos;

out vec4 fragColor;

void main() {
    vec3 d = normalize(skyDir);
    float t = params.x;
    float levels = floor(params.y);
    float brightness = fract(params.y) * 1.25;
    vec3 base = topo_base(d, brightness, tint.rgb);
    vec3 l = topo_ceiling(camPos, d, topo_ceiling_y(camPos.y), t, levels);
    float amount = clamp((l.x + l.y) * l.z, 0.0, 1.0);
    vec3 lineColour = topo_glow_colour(tint.rgb, brightness, l.x);
    float cover = tint.a;
    float a = clamp(cover + amount * (1.0 - cover), 0.0, 1.0);
    vec3 colour = (base * cover + lineColour * amount) / max(a, 1e-4);
    fragColor = vec4(colour, a);
}
