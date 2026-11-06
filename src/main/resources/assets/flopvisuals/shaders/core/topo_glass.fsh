#version 330

#moj_import <flopvisuals:topo.glsl>

// Glass ground: a mirror of the sky ceiling above this exact spot (anchored to the world).
// material 0 = solid ground: soft polished-stone sheen.  material 1 = water: clear mirror with ripples.
in vec3 viewPos;
in vec3 worldPos;
in vec2 params;
in vec4 tint;
in vec3 normal;
in float material;

out vec4 fragColor;

void main() {
    bool water = material > 0.5;
    vec3 v = normalize(viewPos);
    vec3 n = normalize(normal);
    float t = params.x;
    float levels = floor(params.y);
    float brightness = fract(params.y) * 1.25;
    vec3 camera = worldPos - viewPos;

    if (water) {
        // Gentle animated ripples: two drifting noise layers tilt the surface normal a little.
        vec2 p = worldPos.xz * 0.55;
        float e = 0.35;
        float h0 = topo_noise(vec3(p, t * 0.45)) + 0.5 * topo_noise(vec3(p * 2.3 + 7.0, t * 0.8));
        float hx = topo_noise(vec3(p + vec2(e, 0.0), t * 0.45)) + 0.5 * topo_noise(vec3((p + vec2(e, 0.0)) * 2.3 + 7.0, t * 0.8));
        float hz = topo_noise(vec3(p + vec2(0.0, e), t * 0.45)) + 0.5 * topo_noise(vec3((p + vec2(0.0, e)) * 2.3 + 7.0, t * 0.8));
        n = normalize(n + vec3(h0 - hx, 0.0, h0 - hz) * 0.18);
    }
    vec3 r = reflect(v, n);

    // Ground lines are spaced out wider than the sky's; water mirrors them a little tighter.
    float lv = max(3.0, floor(levels * (water ? 0.75 : 0.6)));
    vec3 base = topo_base(r, brightness, tint.rgb);
    vec3 l = topo_ceiling(worldPos, r, topo_ceiling_y(camera.y), t, lv, water ? 420.0 : 560.0);
    float lineAmount = clamp((l.x + l.y) * l.z, 0.0, 1.0);
    vec3 lineColour = topo_glow_colour(tint.rgb, brightness, l.x);

    float cosTheta = clamp(abs(dot(v, n)), 0.0, 1.0);
    float strength = tint.a;
    float tintA, lineA;
    if (water) {
        float fresnel = 0.08 + 0.92 * pow(1.0 - cosTheta, 5.0);      // water: clear looking down, mirror at grazing angles
        tintA = strength * (0.30 + 0.55 * fresnel);
        lineA = strength * lineAmount * (0.55 + 0.45 * fresnel);
        base = mix(base, base * vec3(0.75, 0.95, 1.05), 0.5);
    } else {
        float fresnel = 0.20 + 0.80 * pow(1.0 - cosTheta, 3.0);      // polished stone: subtle sheen
        tintA = strength * (0.16 + 0.34 * fresnel);
        lineA = strength * lineAmount * (0.45 + 0.30 * fresnel);
    }
    float a = clamp(tintA + lineA * (1.0 - tintA), 0.0, 1.0);
    vec3 colour = (base * 1.6 * tintA + lineColour * 1.15 * lineA) / max(a, 1e-4);
    fragColor = vec4(colour, a);
}
