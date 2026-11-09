#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

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
flat out int style;

// Camera position packed into the integer vertex attributes (same as topo_decode_camera).
vec3 decodeCamera(ivec2 uv1, ivec2 uv2) {
    float fx = float((uv2.y >> 7) & 127) / 127.0;
    float fz = float(uv2.y & 127) / 127.0;
    return vec3(float(uv1.x) + fx, float(uv2.x) / 8.0, float(uv1.y) + fz);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Pin the sky to the far plane: it only fills pixels nothing else has drawn on.
    gl_Position.z = gl_Position.w * 0.999999;
    skyDir = Position;
    params = UV0;
    tint = Color;
    camPos = decodeCamera(UV1, UV2);
    style = int(floor(Normal.x * 10.0 + 0.5));
}
