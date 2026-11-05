#version 330

#moj_import <flopvisuals:topo.glsl>

// Flop Visuals animated topographic sky. params.x = time, params.y = lines + brightness (fraction).
in vec3 skyDir;
in vec2 params;
in vec4 tint;

out vec4 fragColor;

void main() {
    vec3 d = normalize(skyDir);
    float t = params.x;
    float levels = floor(params.y);
    float brightness = fract(params.y) * 1.25;
    vec4 sky = topo_sky(d, t, levels, brightness, tint.rgb);
    vec3 lineColour = topo_line_colour(tint.rgb, brightness);
    float cover = tint.a;
    float a = clamp(cover + sky.a * (1.0 - cover), 0.0, 1.0);
    vec3 colour = (sky.rgb * cover + lineColour * sky.a) / max(a, 1e-4);
    fragColor = vec4(colour, a);
}
