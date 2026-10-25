#version 330 core

// Night-sky fill: drifting twinkling stars inside the silhouette, with a soft rim.
uniform sampler2D texSampler;
uniform vec2 oneTexel;
uniform vec4 color;
uniform float radius;
uniform float time;

in vec2 v_uv;
layout (location = 0) out vec4 FragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
    if (texture(texSampler, v_uv).r <= 0.0) { FragColor = vec4(0.0); return; }
    vec2 px = v_uv / oneTexel;
    float cell = max(10.0, radius * 4.0);
    vec2 p = (px + vec2(time * 6.0, time * 2.0)) / cell;
    vec2 id = floor(p);
    vec2 f = fract(p) - 0.5;
    float stars = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(float(x), float(y));
            float h = hash(id + o);
            vec2 c = o + vec2(hash(id + o + 3.1), hash(id + o + 7.7)) - 0.5;
            float d = length(f - c);
            float tw = 0.5 + 0.5 * sin(time * (2.0 + h * 4.0) + h * 30.0);
            stars += smoothstep(0.12 * (0.5 + h), 0.0, d) * tw * step(0.45, h);
        }
    }
    float rim = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = 6.28318530718 * float(i) / 8.0;
        rim = max(rim, texture(texSampler, v_uv + vec2(cos(a), sin(a)) * 2.0 * oneTexel).r > 0.0 ? 0.0 : 1.0);
    }
    vec3 sky = mix(vec3(0.02, 0.03, 0.10), color.rgb * 0.35, 0.35);
    vec3 c = sky + vec3(1.0, 0.97, 0.9) * stars + color.rgb * rim;
    FragColor = vec4(min(c, vec3(1.0)), 0.92);
}
