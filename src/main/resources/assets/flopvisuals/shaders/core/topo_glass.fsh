#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <flopvisuals:topo.glsl>
#moj_import <flopvisuals:styles.glsl>

// Glass ground.
//  - Real reflections: marches the reflected ray through a copy of the frame drawn so far (colour + depth),
//    so blocks, players and mobs above the floor show up mirrored. Where the ray leaves the screen it falls back to the sky.
//  - The sky's pattern is set into the floor itself, anchored to the world, so it stays where it is as you move.
// flags: bit 0 water, bits 1-3 sky style, bit 4 reflections on. Light shade travels in the normal's length.
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;

in vec3 viewPos;
in vec3 worldPos;
in vec2 params;
in vec4 tint;
in vec3 normal;
flat in int flags;

out vec4 fragColor;

float sceneViewZ(vec2 uv) {
    float d = texture(SceneDepth, uv).r;
    if (d >= 0.99999) return -1e6;   // sky: infinitely far
    float ndc = d * 2.0 - 1.0;
    return -ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

vec2 toScreen(vec3 v, out float w) {
    vec4 c = ProjMat * vec4(v, 1.0);
    w = c.w;
    return c.xy / c.w * 0.5 + 0.5;
}

// Screen-space reflection in view space. Returns colour and how much to trust it.
// When the ray first slips behind something on screen, the crossing is refined and only accepted if the ray
// really meets that surface there. Passing behind a thin thing in front (a petal, a hand, a player's edge) is
// rejected and the march goes on, so small objects reflect as their true shape instead of long smears.
vec4 traceReflection(vec3 origin, vec3 dir) {
    if (dir.z > 0.35) return vec4(0.0);   // pointing back towards the camera: nothing reliable on screen
    float stepLen = 0.1;
    vec3 pos = origin;
    float travelled = 0.0;
    for (int i = 0; i < 64; i++) {
        vec3 prev = pos;
        pos += dir * stepLen;
        travelled += stepLen;
        float w;
        vec2 uv = toScreen(pos, w);
        if (w <= 0.05 || uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) break;
        float behind = sceneViewZ(uv) - pos.z;   // > 0: the ray is behind what's drawn there
        if (behind > 0.0) {
            vec3 a = prev, b = pos;
            for (int k = 0; k < 6; k++) {
                vec3 m = (a + b) * 0.5;
                float wm;
                vec2 um = toScreen(m, wm);
                if (sceneViewZ(um) - m.z > 0.0) b = m; else a = m;
            }
            float wb;
            vec2 hit = toScreen(b, wb);
            float gap = sceneViewZ(hit) - b.z;
            if (gap < 0.12 + travelled * 0.012) {
                vec2 e = smoothstep(vec2(0.0), vec2(0.06), hit) * smoothstep(vec2(0.0), vec2(0.06), 1.0 - hit);
                float trust = e.x * e.y * (1.0 - smoothstep(40.0, 70.0, travelled)) * (1.0 - smoothstep(0.0, 0.35, dir.z));
                return vec4(texture(SceneColor, hit).rgb, trust);
            }
        }
        stepLen = min(stepLen * 1.07, 0.6);
    }
    return vec4(0.0);
}

void main() {
    bool water = (flags & 1) != 0;
    int style = (flags >> 1) & 7;
    bool reflections = (flags & 16) != 0;
    float shade = clamp(length(normal), 0.1, 1.0);
    vec3 n = normalize(normal);
    vec3 v = normalize(viewPos);
    float t = params.x;
    float levels = floor(params.y);
    float bright = fract(params.y);
    vec3 colour = tint.rgb;
    float strength = tint.a;

    if (water) {
        // Gentle animated ripples tilt the surface normal a little.
        vec2 p = worldPos.xz * 0.55;
        float e = 0.35;
        float h0 = topo_noise(vec3(p, t * 0.45)) + 0.5 * topo_noise(vec3(p * 2.3 + 7.0, t * 0.8));
        float hx = topo_noise(vec3(p + vec2(e, 0.0), t * 0.45)) + 0.5 * topo_noise(vec3((p + vec2(e, 0.0)) * 2.3 + 7.0, t * 0.8));
        float hz = topo_noise(vec3(p + vec2(0.0, e), t * 0.45)) + 0.5 * topo_noise(vec3((p + vec2(0.0, e)) * 2.3 + 7.0, t * 0.8));
        n = normalize(n + vec3(h0 - hx, 0.0, h0 - hz) * 0.18);
    }
    vec3 r = reflect(v, n);

    if (water) {
        // Water keeps its original look: a rippling mirror of the glowing sky lines overhead.
        vec3 camera = worldPos - viewPos;
        float brightness = bright * 1.25;
        vec3 tintC = colour * shade;
        float lv = max(3.0, floor(levels * 0.75));
        vec3 base = topo_base(r, brightness, tintC);
        vec3 l = topo_ceiling(worldPos, r, topo_ceiling_y(camera.y), t, lv, 420.0);
        float lineAmount = clamp((l.x + l.y) * l.z, 0.0, 1.0);
        vec3 lineColour = topo_glow_colour(tintC, brightness, l.x);
        float cosT = clamp(abs(dot(v, n)), 0.0, 1.0);
        float fresnelW = 0.08 + 0.92 * pow(1.0 - cosT, 5.0);
        float tintA = strength * (0.30 + 0.55 * fresnelW);
        float lineA = strength * lineAmount * (0.55 + 0.45 * fresnelW);
        base = mix(base, base * vec3(0.75, 0.95, 1.05), 0.5);
        float aw = clamp(tintA + lineA * (1.0 - tintA), 0.0, 1.0);
        fragColor = vec4((base * 1.6 * tintA + lineColour * 1.15 * lineA) / max(aw, 1e-4), aw);
        return;
    }

    // What the floor mirrors: real geometry when the ray finds it on screen, otherwise the sky's glow.
    vec3 sky = fv_sky_base(style, r, colour, bright) * 1.15;
    vec3 mirrored = sky;
    if (reflections) {
        vec3 pv = (ModelViewMat * vec4(viewPos, 1.0)).xyz;
        vec3 nv = normalize(mat3(ModelViewMat) * n);
        vec3 rv = normalize(reflect(normalize(pv), nv));
        vec4 hit = traceReflection(pv + nv * 0.03, rv);
        mirrored = mix(sky, hit.rgb, hit.a);
    }
    mirrored *= mix(0.35, 1.0, shade);

    // The sky's pattern, set into the floor where it is.
    vec4 pattern = fv_ground_pattern(style, worldPos.xz, t, colour, bright * 1.25, levels);
    pattern.a *= mix(0.25, 1.0, shade) * smoothstep(0.5, 0.9, abs(n.y));   // floors only, not the sides of steps

    float cosTheta = clamp(abs(dot(v, n)), 0.0, 1.0);
    float mirrorA;
    if (water) {
        float fresnel = 0.08 + 0.92 * pow(1.0 - cosTheta, 5.0);
        mirrorA = strength * (0.35 + 0.6 * fresnel);
    } else {
        // Polished glass: a clear mirror of what's above, stronger at grazing angles.
        float fresnel = 0.35 + 0.65 * pow(1.0 - cosTheta, 3.0);
        mirrorA = strength * fresnel;
    }
    float lineA = clamp(pattern.a * strength, 0.0, 1.0);
    float a = clamp(mirrorA + lineA * (1.0 - mirrorA), 0.0, 1.0);
    vec3 outColour = (mirrored * mirrorA + pattern.rgb * 1.1 * lineA) / max(a, 1e-4);
    fragColor = vec4(outColour, a);
}
