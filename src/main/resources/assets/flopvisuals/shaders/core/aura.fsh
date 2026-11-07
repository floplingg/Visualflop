#version 330

// Glowing shapes for orbs, trails, halos and the aura. The whole part of U picks the shape (two units each),
// the rest is the position inside the quad. Drawn additively, so overlaps glow brighter.
in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    int shape = int(floor(texCoord.x * 0.5));
    vec2 uv = vec2(texCoord.x - float(shape) * 2.0, texCoord.y);
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    vec3 c = vertexColor.rgb;
    vec3 white = vec3(1.0);
    vec3 col = vec3(0.0);
    float a = 0.0;

    if (shape == 0) {            // orb: hot white core, accent glow
        float core = exp(-r * r * 26.0);
        float glow = exp(-r * 4.2) * (1.0 - smoothstep(0.75, 1.0, r));
        col = mix(c, white, core * 0.9);
        a = core + glow * 0.75;
    } else if (shape == 1) {     // soft glow (trails, aura, wisps)
        float g = exp(-r * r * 4.5) * (1.0 - smoothstep(0.7, 1.0, r));
        col = mix(c, white, g * g * 0.35);
        a = g;
    } else if (shape == 2) {     // flat ring
        float d = abs(r - 0.78);
        float px = max(fwidth(r), 1e-4);
        float line = 1.0 - smoothstep(0.035, 0.035 + px * 1.5, d);
        float glow = exp(-d * 16.0) * 0.55;
        col = mix(c, white, line * 0.55);
        a = (line + glow) * (1.0 - smoothstep(0.9, 1.0, r));
    } else if (shape == 3) {     // crown: five points with jewels, band along the bottom
        float s = abs(fract(uv.x * 5.0) - 0.5) * 2.0;          // 1 at a point, 0 between points
        float top = 0.42 + 0.5 * pow(s, 1.6);
        float d = top - uv.y;
        float px = max(fwidth(uv.y), 1e-4);
        float fill = smoothstep(-px, px, d);
        float edge = exp(-abs(d) * 26.0);
        float band = exp(-abs(uv.y - 0.14) * 40.0) + exp(-uv.y * 30.0) * 0.8;
        vec2 jp = vec2((fract(uv.x * 5.0) - 0.5) * 5.0, uv.y - top - 0.03);
        float jewel = exp(-dot(jp, jp) * 300.0) * 2.0;
        col = mix(c, white, clamp(edge * 0.6 + jewel * 0.8 + band * 0.4, 0.0, 1.0));
        a = fill * 0.28 + edge * 0.9 * fill + band * 0.5 * fill + jewel;
    } else if (shape == 4) {     // sun: rays around a ring, behind your head
        float ang = atan(p.y, p.x);
        float rays = pow(abs(cos(ang * 6.0)), 18.0) * smoothstep(0.42, 0.5, r) * (1.0 - smoothstep(0.7, 1.0, r));
        float ring = exp(-abs(r - 0.42) * 40.0);
        float disc = (1.0 - smoothstep(0.3, 0.44, r)) * 0.18;
        col = mix(c, white, ring * 0.5);
        a = rays * 0.9 + ring + disc;
    } else if (shape == 5) {     // horn: a curved, tapering spike
        float v = uv.y;
        float centre = 0.5 + 0.28 * v * v;                      // curves outwards towards the tip
        float width = 0.3 * (1.0 - v) + 0.02;
        float d = width - abs(uv.x - centre);
        float px = max(fwidth(uv.x), 1e-4);
        float fill = smoothstep(-px, px, d) * step(v, 0.98);
        float edge = exp(-abs(d) * 30.0) * step(v, 0.98);
        col = mix(c * 0.8, white, edge * 0.6);
        a = fill * 0.55 + edge * 0.8;
    } else {                     // sparkle: four-point star
        float star = exp(-abs(p.x) * 9.0 - p.y * p.y * 70.0) + exp(-abs(p.y) * 9.0 - p.x * p.x * 70.0);
        float core = exp(-r * r * 40.0);
        col = mix(c, white, core * 0.8 + star * 0.3);
        a = star + core;
    }
    a *= vertexColor.a;
    if (a < 0.004) discard;
    fragColor = vec4(col, clamp(a, 0.0, 1.0));
}
