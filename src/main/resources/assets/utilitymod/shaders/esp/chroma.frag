#version 330 core

// Animated rainbow sweep: rim outline + tinted body, hue flowing diagonally across the screen.
uniform sampler2D texSampler;
uniform vec2 oneTexel;
uniform float radius;
uniform float fill;
uniform float time;

in vec2 v_uv;
layout (location = 0) out vec4 FragColor;

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

void main() {
    vec3 col = hsv2rgb(vec3(fract((v_uv.x + v_uv.y) * 1.5 - time * 0.35), 0.75, 1.0));
    bool inside = texture(texSampler, v_uv).r > 0.0;
    float w = max(1.0, radius * 0.25);
    float near = 0.0;
    for (int i = 0; i < 12; i++) {
        float a = 6.28318530718 * float(i) / 12.0;
        float v = texture(texSampler, v_uv + vec2(cos(a), sin(a)) * w * oneTexel).r > 0.0 ? 1.0 : 0.0;
        near = max(near, inside ? 1.0 - v : v);
    }
    if (inside) {
        FragColor = vec4(col, max(clamp(fill, 0.0, 1.0), near));
    } else {
        FragColor = vec4(col, near);
    }
}
