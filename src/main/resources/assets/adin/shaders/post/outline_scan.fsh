#version 330

uniform sampler2D InSampler;

layout(std140) uniform OutlineConfig {
    float Width;
    vec4 Color;
    float Fill;
    float Glow;
    float Scaled;
};

in vec2 texCoord;

out vec4 fragColor;

vec4 fetch(ivec2 at, ivec2 size) {
    if (at.x < 0 || at.x >= size.x) return vec4(0.0);
    return texelFetch(InSampler, at, 0);
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 at = ivec2(gl_FragCoord.xy);
    int taps = int(ceil(Width));
    for (int d = 0; d <= taps; d++) {
        vec4 left = fetch(at - ivec2(d, 0), size);
        vec4 right = fetch(at + ivec2(d, 0), size);
        vec4 hit = left.a > 0.5 ? left : right;
        if (hit.a > 0.5) {
            fragColor = vec4(hit.rgb, 1.0 - float(d) / (Width + 1.0));
            return;
        }
    }
    fragColor = vec4(0.0);
}
