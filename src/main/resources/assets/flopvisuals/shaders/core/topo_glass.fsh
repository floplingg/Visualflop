#version 330

#moj_import <flopvisuals:topo.glsl>

// Glass ground: a real mirror of the sky ceiling above this exact spot, with Fresnel and lighting.
in vec3 viewPos;
in vec3 worldPos;
in vec2 params;
in vec4 tint;
in vec3 normal;

out vec4 fragColor;

void main() {
    vec3 v = normalize(viewPos);
    vec3 n = normalize(normal);
    vec3 r = reflect(v, n);
    float t = params.x;
    float levels = floor(params.y);
    float brightness = fract(params.y) * 1.25;
    vec3 camera = worldPos - viewPos;

    vec3 base = topo_base(r, brightness, tint.rgb);
    vec3 l = topo_ceiling(worldPos, r, topo_ceiling_y(camera.y), t, levels);
    float lineAmount = clamp((l.x + l.y) * l.z, 0.0, 1.0);
    vec3 lineColour = topo_glow_colour(tint.rgb, brightness, l.x);

    float cosTheta = clamp(abs(dot(v, n)), 0.0, 1.0);
    float fresnel = 0.25 + 0.75 * pow(1.0 - cosTheta, 3.0);
    float strength = tint.a;
    float tintA = strength * (0.28 + 0.42 * fresnel);
    float lineA = strength * lineAmount * (0.75 + 0.25 * fresnel);
    float a = clamp(tintA + lineA * (1.0 - tintA), 0.0, 1.0);
    vec3 colour = (base * 1.6 * tintA + lineColour * 1.15 * lineA) / max(a, 1e-4);
    fragColor = vec4(colour, a);
}
