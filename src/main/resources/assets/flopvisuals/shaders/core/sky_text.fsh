#version 330

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, texCoord) * vertexColor;
    if (c.a < 0.004) discard;
    fragColor = c;
}
