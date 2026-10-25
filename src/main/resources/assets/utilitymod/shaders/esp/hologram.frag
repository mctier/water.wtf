#version 330 core

// Sci-fi hologram: scanlines, flicker, glitch band and a bright rim.
uniform sampler2D texSampler;
uniform vec2 oneTexel;
uniform vec4 color;
uniform float fill;
uniform float time;

in vec2 v_uv;
layout (location = 0) out vec4 FragColor;

float hash(float n) { return fract(sin(n) * 43758.5453); }

void main() {
    vec2 uv = v_uv;
    float band = step(0.985, hash(floor(time * 12.0) + floor(uv.y * 40.0)));
    uv.x += band * (hash(time) - 0.5) * 0.01;
    if (texture(texSampler, uv).r <= 0.0) { FragColor = vec4(0.0); return; }
    float rim = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = 6.28318530718 * float(i) / 8.0;
        rim = max(rim, texture(texSampler, uv + vec2(cos(a), sin(a)) * 2.0 * oneTexel).r > 0.0 ? 0.0 : 1.0);
    }
    float scan = 0.55 + 0.45 * sin(uv.y / oneTexel.y * 0.9 - time * 8.0);
    float flicker = 0.85 + 0.15 * hash(floor(time * 30.0));
    vec3 c = color.rgb * (0.6 + 0.6 * scan) + vec3(rim * 0.6);
    float a = clamp(max(fill, 0.25) * scan * flicker + rim * 0.8 + band * 0.3, 0.0, 1.0);
    FragColor = vec4(min(c, vec3(1.0)), a);
}
