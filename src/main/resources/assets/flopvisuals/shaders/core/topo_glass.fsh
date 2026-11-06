#version 330

#moj_import <flopvisuals:topo.glsl>

// Glass ground: mirrors the topographic sky with a Fresnel falloff.
in vec3 viewPos;
in vec2 params;
in vec4 tint;
in vec3 normal;

out vec4 fragColor;

void main() {
    vec3 v = normalize(viewPos);
    vec3 n = normalize(normal);
    vec3 r = reflect(v, n);                             // mirror the view ray off the surface
    float t = params.x;
    float levels = floor(params.y);
    float brightness = fract(params.y) * 1.25;
    vec4 sky = topo_sky(r, t, levels, brightness, tint.rgb);
    vec3 lineColour = topo_line_colour(tint.rgb, brightness);

    // Glass: the ground stays visible underneath; the mirrored sky tints it and the
    // glowing contour lines sit on top. Grazing angles reflect more (Fresnel).
    float cosTheta = clamp(abs(dot(v, n)), 0.0, 1.0);
    float fresnel = 0.25 + 0.75 * pow(1.0 - cosTheta, 3.0);
    float strength = tint.a;
    float tintA = strength * (0.28 + 0.42 * fresnel);
    float lineA = strength * sky.a * (0.75 + 0.25 * fresnel);
    float a = clamp(tintA + lineA * (1.0 - tintA), 0.0, 1.0);
    vec3 colour = (sky.rgb * 1.6 * tintA + lineColour * 1.15 * lineA) / max(a, 1e-4);
    fragColor = vec4(colour, a);
}
