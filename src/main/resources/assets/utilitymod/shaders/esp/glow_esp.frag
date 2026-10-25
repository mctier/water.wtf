#version 330 core

// Soft glow: exponential halo outside the silhouette, translucent fill inside.
uniform sampler2D texSampler;
uniform vec2 oneTexel;
uniform vec4 color;
uniform float radius;
uniform float fill;
uniform int quality;

in vec2 v_uv;
layout (location = 0) out vec4 FragColor;

void main() {
    if (texture(texSampler, v_uv).r > 0.0) {
        FragColor = vec4(color.rgb, clamp(fill, 0.0, 1.0));
        return;
    }
    float maxR = max(1.0, radius);
    int angles = 8 + quality * 2;
    int steps = 3 + quality;
    float best = 1e9;
    for (int a = 0; a < angles; a++) {
        float ang = 6.28318530718 * float(a) / float(angles);
        vec2 dir = vec2(cos(ang), sin(ang));
        for (int s = 1; s <= steps; s++) {
            float d = maxR * float(s) / float(steps);
            if (d >= best) break;
            if (texture(texSampler, v_uv + dir * d * oneTexel).r > 0.0) {
                best = d;
                break;
            }
        }
    }
    if (best >= 1e8) {
        FragColor = vec4(0.0);
        return;
    }
    float glow = exp(-best / maxR * 3.0);
    FragColor = vec4(color.rgb, glow * color.a);
}
