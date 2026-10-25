#version 330

uniform sampler2D InSampler;

layout(std140) uniform KawaseConfig {
    vec2 Offset;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 step = Offset / vec2(textureSize(InSampler, 0));
    vec2 flip = vec2(step.x, -step.y);
    fragColor = (texture(InSampler, texCoord + step) + texture(InSampler, texCoord - step)
            + texture(InSampler, texCoord + flip) + texture(InSampler, texCoord - flip)) * 0.25;
}
