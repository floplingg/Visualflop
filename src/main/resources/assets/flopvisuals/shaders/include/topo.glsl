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

// x = crisp line core, y = glow halo (a tight bloom plus a wide soft one). Every fourth line is brighter.
vec2 topo_lines(float h, float levels, float t) {
    float v = h * levels - t * 0.035;
    float f = fract(v);
    float dist = min(f, 1.0 - f);
    float px = max(fwidth(v), 1e-5);
    float pixels = dist / px;
    float core = 1.0 - smoothstep(0.45, 1.5, pixels);
    float glow = exp(-pixels / 3.0) * 0.55 + exp(-pixels / 12.0) * 0.32;
    float index = mod(floor(v + 0.5), 4.0) < 0.5 ? 1.0 : 0.62;
    return vec2(core * index, glow * index);
}

// Height of the world-anchored sky ceiling the contour lines live on.
float topo_ceiling_y(float camY) {
    return max(420.0, camY + 140.0);
}

// Contour lines where a ray from origin in direction dir meets the sky ceiling.
// Returns (core, glow, fade). Anchored to the world, so the pattern doesn't slide when you move.
vec3 topo_ceiling(vec3 origin, vec3 dir, float ceilingY, float t, float levels) {
    if (dir.y <= 0.0005) return vec3(0.0);
    float k = (ceilingY - origin.y) / dir.y;
    vec2 q = origin.xz + dir.xz * k;
    float h = topo_height(vec3(q.x, 0.0, q.y) / 230.0, t);
    vec2 l = topo_lines(h, levels, t);
    float horizon = smoothstep(0.015, 0.22, dir.y);
    float far = exp(-k / 9000.0);
    return vec3(l, horizon * far);
}

// Sky gradient behind the lines (dark accent overhead, brighter at the horizon).
vec3 topo_base(vec3 d, float brightness, vec3 accent) {
    vec3 zenith = accent * 0.16 + vec3(0.02);
    vec3 horizon = accent * 0.42 + vec3(0.04);
    vec3 base = mix(horizon, zenith, smoothstep(0.0, 0.75, d.y));
    base = d.y < 0.0 ? horizon * mix(1.0, 0.55, smoothstep(0.0, -0.4, d.y)) : base;
    return base * brightness;
}

// Line colour: accent, running white-hot in the core.
vec3 topo_glow_colour(vec3 accent, float brightness, float core) {
    return mix(accent, vec3(1.0), 0.45 * core) * (0.9 + 0.6 * brightness);
}

// Decode the camera position packed into the integer vertex attributes.
vec3 topo_decode_camera(ivec2 uv1, ivec2 uv2) {
    float fx = float((uv2.y >> 7) & 127) / 127.0;
    float fz = float(uv2.y & 127) / 127.0;
    return vec3(float(uv1.x) + fx, float(uv2.x) / 8.0, float(uv1.y) + fz);
}
