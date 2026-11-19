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

layout(location = 0) out vec3 viewPos;
layout(location = 1) out vec3 worldPos;
layout(location = 2) out vec2 params;
layout(location = 3) out vec4 tint;
layout(location = 4) out vec3 normal;
layout(location = 5) flat out int flags;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    viewPos = Position;
    // World position of this vertex (x and z wrapped every 16384 blocks), so the floor pattern stays put.
    worldPos = vec3(float(UV1.x), float(UV2.x) / 8.0, float(UV1.y));
    params = UV0;
    tint = Color;
    normal = Normal;
    flags = UV2.y;
}
