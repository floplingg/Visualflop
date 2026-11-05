#version 330

// Flop Visuals: animated glowing topographic contour sky.
// Drawn per pixel, so lines are razor sharp at any resolution.
// params.x = time (seconds), params.y = line count + brightness (fraction part)
// tint.rgb = accent colour, tint.a = how much of the vanilla sky to cover

in vec3 skyDir;
in vec2 params;
in vec4 tint;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

void main() {
    vec3 d = normalize(skyDir);
    float t = params.x;
    float levels = floor(params.y);
    float brightness = fract(params.y) * 1.25;
    vec3 accent = tint.rgb;

    // Base sky: dark accent at the top, brighter towards the horizon.
    vec3 zenith = accent * 0.07 + vec3(0.008);
    vec3 horizon = accent * 0.30 + vec3(0.02);
    vec3 base = mix(horizon, zenith, smoothstep(0.0, 0.75, d.y));
    base = d.y < 0.0 ? horizon * mix(1.0, 0.55, smoothstep(0.0, -0.4, d.y)) : base;
    base *= brightness;

    // Organic height field: domain-warped noise on the sky sphere, slowly drifting.
    vec3 p = d * 1.9 + vec3(t * 0.004, t * 0.011, -t * 0.006);
    vec3 warp = vec3(fbm(p + vec3(3.1, 0.0, 0.0)), fbm(p + vec3(0.0, 7.7, 0.0)), fbm(p + vec3(0.0, 0.0, 1.3)));
    float h = fbm(p * 0.85 + warp * 1.35);

    // Contour lines. The levels themselves slide slowly so the lines breathe.
    float v = h * levels - t * 0.035;
    float f = fract(v);
    float dist = min(f, 1.0 - f);
    float px = max(fwidth(v), 1e-5);
    float pixels = dist / px;                                  // distance to the line in screen pixels
    float line = 1.0 - smoothstep(0.55, 1.65, pixels);         // crisp ~1.5 px line
    float glow = exp(-pixels / 7.0) * 0.55 + exp(-dist * 10.0) * 0.10;

    // Every fourth line is an index line, a bit brighter, like a real topo map.
    float index = mod(floor(v + 0.5), 4.0) < 0.5 ? 1.0 : 0.6;
    float fade = smoothstep(-0.06, 0.16, d.y);                 // fade into the horizon
    float lineAmount = clamp((line * index + glow * index) * fade, 0.0, 1.0);

    vec3 lineColour = mix(accent, vec3(1.0), 0.25 * line) * (0.85 + 0.6 * brightness);
    float coverA = tint.a;
    float a = clamp(coverA + lineAmount * (1.0 - coverA), 0.0, 1.0);
    vec3 colour = (base * coverA + lineColour * lineAmount) / max(a, 1e-4);

    fragColor = vec4(colour, a);
}
