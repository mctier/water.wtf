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

float distanceScale(vec3 rgb) {
    if (Scaled <= 0.5) return 1.0;
    ivec3 bits = ivec3(round(rgb * 255.0)) & 3;
    return 1.0 - float(bits.r * 16 + bits.g * 4 + bits.b) / 63.0;
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 at = ivec2(gl_FragCoord.xy);
    int taps = int(ceil(Width));
    float nearest = -1.0;
    float width = Width;
    float bestRatio = 0.0;
    vec3 color = vec3(0.0);
    for (int dy = -taps; dy <= taps; dy++) {
        int y = at.y + dy;
        if (y < 0 || y >= size.y) continue;
        vec4 row = texelFetch(InSampler, ivec2(at.x, y), 0);
        if (row.a <= 0.0) continue;
        float dx = (1.0 - row.a) * (Width + 1.0);
        float away = sqrt(dx * dx + float(dy * dy));
        float scaled = max(Width * distanceScale(row.rgb), 1.0);
        float ratio = (away - 0.5) / scaled;
        if (nearest < 0.0 || ratio < bestRatio) {
            nearest = away;
            width = scaled;
            color = row.rgb;
            bestRatio = ratio;
        }
    }
    if (nearest < 0.0 || nearest > width + 0.5) discard;
    vec3 rgb = Color.a > 0.0 ? Color.rgb : color;
    if (nearest < 0.5) {
        if (Fill <= 0.0) discard;
        fragColor = vec4(rgb, Fill);
        return;
    }
    rgb = mix(rgb, vec3(1.0), min(Glow, 3.0) * 0.15);
    fragColor = vec4(rgb, clamp(width + 0.5 - nearest, 0.0, 1.0));
}
