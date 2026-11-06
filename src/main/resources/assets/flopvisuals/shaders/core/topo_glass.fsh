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
    vec3 colour = sky.rgb + lineColour * sky.a;

    float cosTheta = clamp(abs(dot(v, n)), 0.0, 1.0);
    float fresnel = 0.35 + 0.65 * pow(1.0 - cosTheta, 3.0);
    float a = clamp(tint.a * mix(0.75, 1.0, fresnel), 0.0, 1.0);
    fragColor = vec4(colour, a);
}
