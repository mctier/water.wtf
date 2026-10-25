#version 330

uniform sampler2D InSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform OutlineConfig {
    float Width;
    vec4 Color;
    float Fill;
    float Glow;
    float Scaled;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 blurred = texture(InSampler, texCoord);
    float halo = blurred.a * (1.0 - texture(MaskSampler, texCoord).a);
    if (halo <= 0.002) discard;
    float shaped = clamp(halo * 2.0, 0.0, 1.0);
    shaped *= shaped;
    float strength = (1.0 - exp(-Glow)) * shaped;
    vec3 rgb = Color.a > 0.0 ? Color.rgb : blurred.rgb / max(blurred.a, 0.001);
    fragColor = vec4(rgb * strength, strength);
}
