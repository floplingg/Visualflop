#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec3 viewPos;
out vec3 worldPos;
out vec2 params;
out vec4 tint;
out vec3 normal;
flat out int flags;

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
