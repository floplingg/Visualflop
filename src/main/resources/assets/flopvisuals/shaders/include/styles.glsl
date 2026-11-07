// Flop Visuals sky styles. Needs topo.glsl imported first.
// 0 Topographic, 1 Galaxy, 2 Aether, 3 Nocturne, 4 Amethyst.

float fv_hash1(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float fv_fbm(vec3 p, int octaves) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) break;
        v += a * topo_noise(p);
        p = p * 2.02 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

vec3 fv_hue(vec3 c, float shift) {
    // Rotate the hue of c around the grey axis.
    const vec3 k = vec3(0.57735);
    float a = shift * 6.2831853;
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}

// Anti-aliased thin line where v crosses zero: x = core, y = glow.
vec2 fv_line(float v, float glowWidth) {
    float px = max(fwidth(v), 1e-5);
    float pixels = abs(v) / px;
    float core = 1.0 - smoothstep(0.4, 1.4, pixels);
    float glow = exp(-pixels / glowWidth) * 0.55 + exp(-pixels / (glowWidth * 4.0)) * 0.25;
    return vec2(core, glow);
}

// ------------------------------------------------------------ Galaxy

vec3 fv_stars(vec3 d, float t, float density, float scale) {
    vec3 p = d * scale;
    vec3 id = floor(p);
    vec3 f = fract(p) - 0.5;
    float h = fv_hash1(id);
    if (h < 1.0 - density) return vec3(0.0);
    vec3 off = vec3(fv_hash1(id + 1.3), fv_hash1(id + 2.7), fv_hash1(id + 4.1)) - 0.5;
    vec3 q = f - off * 0.6;
    float r2 = dot(q, q);
    float tw = 0.6 + 0.4 * sin(t * (1.5 + h * 3.0) + h * 40.0);
    float big = step(0.997, h);
    float core = exp(-r2 * mix(900.0, 260.0, big));
    float cross = big * (exp(-abs(q.x) * 90.0 - q.y * q.y * 900.0) + exp(-abs(q.y) * 90.0 - q.x * q.x * 900.0)) * 0.6;
    vec3 col = mix(vec3(0.85, 0.9, 1.0), vec3(1.0, 0.9, 0.95), fv_hash1(id + 7.0));
    return col * (core + cross) * tw;
}

float fv_shooting(vec3 d, float t) {
    float period = 6.5;
    float slot = floor(t / period);
    float prog = fract(t / period);
    float life = 0.16;
    if (prog > life) return 0.0;
    float k = prog / life;
    vec3 rnd = vec3(fv_hash1(vec3(slot, 1.0, 3.0)), fv_hash1(vec3(slot, 5.0, 7.0)), fv_hash1(vec3(slot, 9.0, 2.0)));
    float az = rnd.x * 6.2831853;
    vec3 s = normalize(vec3(cos(az), 0.45 + rnd.y * 0.4, sin(az)));
    vec3 side = normalize(cross(s, vec3(0.0, 1.0, 0.0)));
    vec3 e = normalize(s + side * (0.35 + rnd.z * 0.25) - vec3(0.0, 0.18, 0.0));
    vec3 head = normalize(mix(s, e, k));
    vec3 tail = normalize(mix(s, e, max(0.0, k - 0.35)));
    vec3 ab = head - tail, ap = d - tail;
    float h = clamp(dot(ap, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
    float dist = length(ap - ab * h);
    float fade = sin(k * 3.14159);
    return exp(-dist / 0.0012) * h * h * fade * 1.6;
}

// Ringed planet at a fixed spot in the sky. Returns rgb in .xyz, coverage in .w.
vec4 fv_planet(vec3 d, vec3 c, float t) {
    vec3 P = normalize(vec3(0.62, 0.34, -0.71));
    float w = dot(d, P);
    if (w < 0.9) return vec4(0.0);
    vec3 T = normalize(cross(P, vec3(0.0, 1.0, 0.0)));
    vec3 B = cross(T, P);
    vec2 uv = vec2(dot(d, T), dot(d, B));
    float R = 0.085;
    vec2 q = uv / R;
    // Tilted ring.
    float ca = cos(-0.32), sa = sin(-0.32);
    vec2 rq = vec2(q.x * ca - q.y * sa, q.x * sa + q.y * ca);
    float rr = length(vec2(rq.x, rq.y / 0.26));
    float ringBand = smoothstep(1.32, 1.36, rr) * (1.0 - smoothstep(2.05, 2.12, rr));
    float bands = 0.65 + 0.35 * sin(rr * 38.0) * sin(rr * 11.0 + 1.0);
    float gap = 1.0 - 0.75 * (smoothstep(1.62, 1.64, rr) * (1.0 - smoothstep(1.70, 1.72, rr)));
    vec3 ringCol = mix(c * 1.1 + 0.1, vec3(0.95, 0.9, 1.0), 0.35) * bands * gap;
    float ringA = ringBand * 0.85 * gap;
    // Planet body.
    float r = length(q);
    float px = fwidth(r);
    float body = 1.0 - smoothstep(1.0 - px, 1.0 + px, r);
    vec3 n = vec3(q, sqrt(max(0.0, 1.0 - r * r)));
    vec3 L = normalize(vec3(-0.6, 0.5, 0.65));
    float lit = clamp(dot(n, L), 0.0, 1.0);
    float swirl = fv_fbm(vec3(q * 2.2 + vec2(t * 0.01, 0.0), 3.0) + n * 0.8, 4);
    vec3 surf = mix(c * 0.35, vec3(0.92, 0.9, 0.97), smoothstep(0.38, 0.62, swirl));
    vec3 planet = surf * (0.12 + 0.95 * lit) + c * 0.25 * pow(1.0 - n.z, 3.0);
    // Ring in front of the planet only on its lower half (towards the viewer).
    bool ringFront = rq.y < 0.0;
    vec3 col = vec3(0.0);
    float a = 0.0;
    if (ringFront) {
        col = mix(planet * body, ringCol, ringA);
        a = max(body, ringA);
    } else {
        col = mix(ringCol * ringA, planet, body);
        a = max(body, ringA * (1.0 - body));
    }
    // Two little moons.
    vec2 m1 = q - vec2(-2.7, -0.55), m2 = q - vec2(-2.45, -0.72);
    float moon = (1.0 - smoothstep(0.13, 0.16, length(m1))) + (1.0 - smoothstep(0.08, 0.11, length(m2)));
    col = mix(col, vec3(0.45, 0.42, 0.5) * (0.6 + 0.4 * clamp(-m1.x * 4.0, 0.0, 1.0)), clamp(moon, 0.0, 1.0));
    a = max(a, clamp(moon, 0.0, 1.0));
    return vec4(col, a);
}

vec3 fv_galaxy(vec3 d, float t, vec3 c, float b) {
    float up = clamp(d.y, -1.0, 1.0);
    vec3 zenith = c * 0.10 + vec3(0.01, 0.005, 0.03);
    vec3 horizon = c * 0.42 + vec3(0.03, 0.01, 0.06);
    vec3 col = mix(horizon, zenith, smoothstep(-0.05, 0.7, up));

    // Nebula: soft clouds with bright filaments.
    vec3 p = d * 1.8 + vec3(t * 0.004, 0.0, -t * 0.003);
    vec3 warp = vec3(fv_fbm(p + 3.1, 3), fv_fbm(p + 7.7, 3), fv_fbm(p + 1.3, 3));
    float n = fv_fbm(p * 1.3 + warp * 1.6, 5);
    float neb = smoothstep(0.42, 0.78, n);
    float fil = pow(1.0 - abs(fv_fbm(p * 2.6 + warp * 2.0, 4) * 2.0 - 1.0), 7.0);
    vec3 pink = fv_hue(c, 0.06) * 1.4 + vec3(0.1, 0.0, 0.12);
    col += pink * neb * 0.55 + mix(pink, vec3(1.0), 0.3) * fil * neb * 0.45;

    // Aurora curtains just above the horizon.
    float az = atan(d.z, d.x);
    float rays = topo_noise(vec3(az * 26.0, t * 0.12, 0.0));
    rays = pow(rays, 2.5) * (0.5 + topo_noise(vec3(az * 5.0, t * 0.05, 3.0)));
    float band = smoothstep(-0.02, 0.06, up) * exp(-max(up, 0.0) * 3.2);
    vec3 auroraCol = mix(fv_hue(c, -0.08) * 1.3, c * 1.6 + 0.08, smoothstep(0.0, 0.3, up));
    col += auroraCol * rays * band * 0.9;

    // Stars, shooting stars and the planet.
    float starMask = smoothstep(-0.02, 0.12, up) * (1.0 - neb * 0.6);
    col += fv_stars(d, t, 0.012, 140.0) * starMask * 1.2;
    col += fv_stars(d, t * 1.3, 0.03, 300.0) * starMask * 0.55;
    col += vec3(1.0, 0.95, 1.0) * fv_shooting(d, t);
    vec4 pl = fv_planet(d, c, t);
    col = mix(col, pl.rgb, pl.a);

    // Below the horizon: dark haze.
    col = up < 0.0 ? mix(horizon * 0.8, zenith * 0.6, smoothstep(0.0, -0.4, up)) : col;
    return col * (0.55 + 0.9 * b);
}

// ------------------------------------------------------------ Aether: thin silver light flowing over anthracite

float fv_aether_field(vec3 p, float t) {
    vec3 q = p + vec3(t * 0.006, -t * 0.004, t * 0.005);
    vec3 warp = vec3(topo_noise(q * 0.7 + 2.0), topo_noise(q * 0.7 + 9.0), topo_noise(q * 0.7 + 5.0));
    return fv_fbm(q + warp * 1.4 + vec3(0.0, t * 0.02, 0.0), 4) * 2.0 - 1.0;
}

vec3 fv_aether_colour(float field1, float field2, vec3 c, vec2 shade) {
    vec2 l1 = fv_line(field1, 3.0);
    vec2 l2 = fv_line(field2, 2.0) * 0.55;
    float shadeK = 0.5 + 0.5 * shade.x;
    vec3 base = vec3(0.10, 0.11, 0.13) * mix(0.55, 1.15, shade.y) + c * 0.03;
    vec3 lineCol = mix(c, vec3(1.0), 0.55);
    return base + lineCol * (l1.x * 0.9 + l1.y + l2.x * 0.6 + l2.y) * shadeK;
}

vec3 fv_aether(vec3 d, float t, vec3 c, float b) {
    vec3 p = d * 1.7;
    float f1 = fv_aether_field(p, t);
    float f2 = fv_aether_field(p * 1.9 + 11.0, t * 1.2);
    float shade = fv_fbm(p * 0.8 + 4.0, 3);
    vec3 col = fv_aether_colour(f1, f2, c, vec2(shade, shade));
    float horizon = smoothstep(-0.1, 0.25, d.y);
    return mix(vec3(0.07, 0.075, 0.09), col, horizon) * (0.6 + 0.8 * b);
}

// ------------------------------------------------------------ Nocturne: soft violet clouds with a lilac glow at their edges

vec3 fv_nocturne_colour(float n, vec3 c) {
    float cloud = smoothstep(0.38, 0.78, n);
    float edge = exp(-abs(n - 0.56) * 18.0);
    vec3 base = c * 0.06 + vec3(0.02, 0.015, 0.03);
    vec3 cloudCol = c * 0.32 + vec3(0.02);
    vec3 lilac = mix(c, vec3(1.0), 0.35) * 0.5;
    return base + cloudCol * cloud + lilac * edge * cloud * 0.6;
}

vec3 fv_nocturne(vec3 d, float t, vec3 c, float b) {
    vec3 p = d * 1.4 + vec3(t * 0.006, 0.0, t * 0.004);
    vec3 warp = vec3(fv_fbm(p + 1.0, 3), fv_fbm(p + 6.0, 3), fv_fbm(p + 12.0, 3));
    float n = fv_fbm(p + warp * 1.2 + vec3(0.0, t * 0.01, 0.0), 5);
    vec3 col = fv_nocturne_colour(n, c);
    float horizon = smoothstep(-0.15, 0.2, d.y);
    return mix(c * 0.05 + vec3(0.015, 0.01, 0.02), col, horizon) * (0.6 + 0.9 * b);
}

// ------------------------------------------------------------ Amethyst: deep violet marble with fine veins of light

vec3 fv_amethyst_at(vec3 p, float t, vec3 c) {
    p += vec3(t * 0.003, 0.0, -t * 0.002);
    vec3 q = vec3(fv_fbm(p, 3), fv_fbm(p + vec3(5.2, 1.3, 2.8), 3), fv_fbm(p + vec3(1.7, 9.2, 4.4), 3));
    vec3 r = vec3(fv_fbm(p + 4.0 * q + vec3(1.7, 9.2, 0.0) + t * 0.01, 3),
                  fv_fbm(p + 4.0 * q + vec3(8.3, 2.8, 5.0), 3), 0.0);
    float f = fv_fbm(p + 4.0 * r.xyx, 3);
    vec3 dark = c * 0.06 + vec3(0.02, 0.01, 0.03);
    vec3 mid = c * 0.55;
    vec3 col = mix(dark, mid, smoothstep(0.25, 0.85, f * f * 2.2));
    col = mix(col, dark * 0.5, smoothstep(0.55, 0.9, length(q)) * 0.6);
    // Fine veins along contours of the warped field, only in some places.
    float v = fract(f * 9.0) - 0.5;
    vec2 vein = fv_line(v, 1.2);
    float mask = smoothstep(0.45, 0.62, fv_fbm(p * 2.5 + 3.0, 3));
    col += mix(c, vec3(1.0), 0.6) * (vein.x * 0.7 + vein.y * 0.35) * mask;
    return col;
}

vec3 fv_amethyst(vec3 d, float t, vec3 c, float b) {
    vec3 col = fv_amethyst_at(d * 1.6, t, c);
    float horizon = smoothstep(-0.15, 0.2, d.y);
    return mix(c * 0.05 + vec3(0.01, 0.005, 0.02), col, horizon) * (0.6 + 0.9 * b);
}

// ------------------------------------------------------------ dispatch

// Full sky colour for a style (topographic is handled by the caller, which needs the line alpha).
vec3 fv_sky(int style, vec3 d, float t, vec3 c, float b) {
    if (style == 1) return fv_galaxy(d, t, c, b);
    if (style == 2) return fv_aether(d, t, c, b);
    if (style == 3) return fv_nocturne(d, t, c, b);
    return fv_amethyst(d, t, c, b);
}

// A world-anchored pattern for the glass ground at world position xz. rgb + strength.
vec4 fv_ground_pattern(int style, vec2 xz, float t, vec3 c, float b, float levels) {
    if (style == 0) {
        float h = topo_height(vec3(xz.x, 0.0, xz.y) / 46.0, t);
        vec2 l = topo_lines(h, max(3.0, floor(levels * 0.6)), t);
        float a = clamp(l.x + l.y, 0.0, 1.0);
        return vec4(topo_glow_colour(c, b, l.x), a);
    }
    vec3 p = vec3(xz.x, 0.0, xz.y) / 30.0;
    if (style == 2) {
        float f1 = fv_aether_field(p, t);
        vec2 l = fv_line(f1, 2.5);
        return vec4(mix(c, vec3(1.0), 0.55), clamp(l.x * 0.9 + l.y, 0.0, 1.0));
    }
    if (style == 4) {
        vec3 col = fv_amethyst_at(p * 0.8, t, c);
        return vec4(col * 1.6, 0.55);
    }
    if (style == 3) {
        float n = fv_fbm(p * 0.9 + vec3(t * 0.006, 0.0, 0.0), 4);
        float edge = exp(-abs(n - 0.56) * 14.0) * smoothstep(0.38, 0.7, n);
        return vec4(mix(c, vec3(1.0), 0.35), edge * 0.6);
    }
    // Galaxy: a sprinkle of stars and a faint nebula glow.
    vec3 s = fv_stars(vec3(xz.x, 0.5, xz.y) * 0.4, t, 0.035, 1.0);
    float neb = smoothstep(0.5, 0.8, fv_fbm(p * 0.7, 4));
    vec3 col = s * 1.5 + fv_hue(c, 0.06) * neb * 0.6;
    return vec4(col, clamp(max(max(s.r, s.g), s.b) + neb * 0.4, 0.0, 1.0));
}

// Sky colour seen in a reflection that misses everything on screen: the style's gradient without fine detail.
vec3 fv_sky_base(int style, vec3 d, vec3 c, float b) {
    float up = clamp(d.y, 0.0, 1.0);
    if (style == 2) return vec3(0.09, 0.095, 0.11) * (0.6 + 0.8 * b);
    if (style == 0) return topo_base(d, b * 1.25, c);
    return mix(c * 0.38 + vec3(0.02), c * 0.10 + vec3(0.01), smoothstep(0.0, 0.7, up)) * (0.6 + 0.9 * b);
}
