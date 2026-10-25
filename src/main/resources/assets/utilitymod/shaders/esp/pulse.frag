#version 330 core

// Breathing outline: a halo whose reach and brightness pulse over time.
uniform sampler2D texSampler;
uniform vec2 oneTexel;
uniform vec4 color;
uniform float radius;
uniform float fill;
uniform float time;

in vec2 v_uv;
layout (location = 0) out vec4 FragColor;

void main() {
    float beat = 0.5 + 0.5 * sin(time * 4.0);
    if (texture(texSampler, v_uv).r > 0.0) {
        FragColor = vec4(color.rgb, clamp(fill, 0.0, 1.0) * (0.6 + 0.4 * beat));
        return;
    }
    float reach = max(1.0, radius * (0.6 + 0.6 * beat));
    float best = 1e9;
    for (int a = 0; a < 16; a++) {
        float ang = 6.28318530718 * float(a) / 16.0;
        vec2 dir = vec2(cos(ang), sin(ang));
        for (int s = 1; s <= 6; s++) {
            float d = reach * float(s) / 6.0;
            if (d >= best) break;
            if (texture(texSampler, v_uv + dir * d * oneTexel).r > 0.0) { best = d; break; }
        }
    }
    if (best >= 1e8) { FragColor = vec4(0.0); return; }
    float k = 1.0 - best / reach;
    FragColor = vec4(mix(color.rgb, vec3(1.0), k * 0.35 * beat), k * k * (0.55 + 0.45 * beat));
}
