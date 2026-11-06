#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <flopvisuals:topo.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec3 skyDir;
out vec2 params;
out vec4 tint;
out vec3 camPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    skyDir = Position;
    params = UV0;
    tint = Color;
    camPos = topo_decode_camera(UV1, UV2);
}
