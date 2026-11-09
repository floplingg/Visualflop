#version 330

// Frosted glass: a wide, smooth blur of the game behind a rounded box.
// texCoord runs 0..1 across the box; red = corner radius in pixels / 255, green = blur strength.
uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec2 size = 1.0 / max(abs(vec2(dFdx(texCoord.x), dFdy(texCoord.y))), vec2(1e-5));
    vec2 p = texCoord * size;
    float r = vertexColor.r * 255.0;
    vec2 q = abs(p - size * 0.5) - (size * 0.5 - r);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float mask = clamp(0.5 - d, 0.0, 1.0);
    if (mask <= 0.0) discard;

    vec2 screen = vec2(textureSize(Sampler0, 0));
    float strength = vertexColor.g;
    float radius = (6.0 + 22.0 * strength) * screen.y / 1080.0;
    // 32 taps on a golden-angle spiral, turned a little per pixel so it reads smooth instead of banded.
    float turn = ign(gl_FragCoord.xy) * 6.2831853;
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < 32; i++) {
        float f = (float(i) + 0.5) / 32.0;
        float a = float(i) * 2.3999632 + turn;
        vec2 o = vec2(cos(a), sin(a)) * sqrt(f) * radius;
        float wgt = 1.0 - f * 0.6;
        sum += texture(Sampler0, (gl_FragCoord.xy + o) / screen).rgb * wgt;
        total += wgt;
    }
    vec3 col = sum / total;
    // A touch more colour, then the dark glass tint from the preview.
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(l), col, 1.25);
    col = mix(col, vec3(14.0, 16.0, 30.0) / 255.0, 0.42);
    fragColor = vec4(clamp(col, 0.0, 1.0), mask);
}
