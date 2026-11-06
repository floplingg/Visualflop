#version 330

// Shared Flop Visuals topographic field: domain-warped value noise and anti-aliased contour lines.

float topo_hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float topo_noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(topo_hash(i), topo_hash(i + vec3(1, 0, 0)), f.x),
                   mix(topo_hash(i + vec3(0, 1, 0)), topo_hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(topo_hash(i + vec3(0, 0, 1)), topo_hash(i + vec3(1, 0, 1)), f.x),
                   mix(topo_hash(i + vec3(0, 1, 1)), topo_hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float topo_fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * topo_noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

// Height of the organic field at p, slowly drifting with time t (seconds).
float topo_height(vec3 p, float t) {
    p += vec3(t * 0.004, t * 0.011, -t * 0.006);
    // Gentle, low-frequency warp: smooth organic curves instead of jagged lightning.
    vec3 q = p * 0.6;
    vec3 warp = vec3(topo_noise(q + vec3(3.1, 0.0, 0.0)), topo_noise(q + vec3(0.0, 7.7, 0.0)), topo_noise(q + vec3(0.0, 0.0, 1.3)));
    return topo_fbm(p * 0.75 + warp * 0.8);
}

// x = crisp line (about 1.5 screen pixels wide), y = soft glow. Every fourth line is brighter.
vec2 topo_lines(float h, float levels, float t) {
    float v = h * levels - t * 0.035;
    float f = fract(v);
    float dist = min(f, 1.0 - f);
    float px = max(fwidth(v), 1e-5);
    float pixels = dist / px;
    float line = 1.0 - smoothstep(0.5, 1.6, pixels);
    float glow = exp(-pixels / 5.0) * 0.45;
    float index = mod(floor(v + 0.5), 4.0) < 0.5 ? 1.0 : 0.6;
    return vec2(line * index, glow * index);
}

// Sky colour in direction d (normalized). accent = theme colour, brightness 0..1.25. Returns rgb and line amount.
vec4 topo_sky(vec3 d, float t, float levels, float brightness, vec3 accent) {
    vec3 zenith = accent * 0.16 + vec3(0.02);
    vec3 horizon = accent * 0.42 + vec3(0.04);
    vec3 base = mix(horizon, zenith, smoothstep(0.0, 0.75, d.y));
    base = d.y < 0.0 ? horizon * mix(1.0, 0.55, smoothstep(0.0, -0.4, d.y)) : base;
    base *= brightness;
    float h = topo_height(d * 1.5, t);
    vec2 l = topo_lines(h, levels, t);
    float fade = smoothstep(-0.06, 0.16, d.y);
    float amount = clamp((l.x + l.y) * fade, 0.0, 1.0);
    return vec4(base, amount);
}

vec3 topo_line_colour(vec3 accent, float brightness) {
    return mix(accent, vec3(1.0), 0.2) * (0.85 + 0.6 * brightness);
}
