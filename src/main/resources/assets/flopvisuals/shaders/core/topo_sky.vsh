#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(location = 0) out vec3 skyDir;
layout(location = 1) out vec2 params;
layout(location = 2) out vec4 tint;
layout(location = 3) out vec3 camPos;
layout(location = 4) flat out int style;

// Camera position packed into the integer vertex attributes (same as topo_decode_camera).
vec3 decodeCamera(ivec2 uv1, ivec2 uv2) {
    float fx = float((uv2.y >> 7) & 127) / 127.0;
    float fz = float(uv2.y & 127) / 127.0;
    return vec3(float(uv1.x) + fx, float(uv2.x) / 8.0, float(uv1.y) + fz);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Pin the sky to the far plane: it only fills pixels nothing else has drawn on.
    // Pin to the far plane. 26.3 uses reversed depth (far = 0, set by a projection whose [2][2] is >= 0);
    // the classic layout has far = 1. Either way the sky only fills pixels nothing else has drawn on.
    gl_Position.z = gl_Position.w * (ProjMat[2][2] >= 0.0 ? 1.0e-6 : 0.999999);
    skyDir = Position;
    params = UV0;
    tint = Color;
    camPos = decodeCamera(UV1, UV2);
    style = int(floor(Normal.x * 10.0 + 0.5));
}
