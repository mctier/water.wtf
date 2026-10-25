#version 330 core

// Liquid glass over the entity silhouette: refracts the scene behind it along the
// silhouette's edge normal, with chromatic split, light frost, tint and rim/specular light.
uniform sampler2D texSampler;   // silhouette mask (r > 0 inside)
uniform sampler2D sceneSampler; // main framebuffer colour
uniform vec2 oneTexel;
uniform vec4 color;             // tint
uniform float radius;           // refraction strength (px)
uniform float fill;             // tint amount 0..1
uniform float time;

in vec2 v_uv;
layout (location = 0) out vec4 FragColor;

float maskAt(vec2 uv) {
    return texture(texSampler, uv).r > 0.0 ? 1.0 : 0.0;
}

void main() {
    if (maskAt(v_uv) <= 0.0) {
        FragColor = vec4(0.0);
        return;
    }

    // Edge normal (points out of the shape) and depth-into-shape from a radial probe.
    float probe = max(3.0, radius * 0.6);
    vec2 grad = vec2(0.0);
    float covered = 0.0;
    const int ANGLES = 16;
    const int STEPS = 4;
    for (int i = 0; i < ANGLES; i++) {
        float a = 6.28318530718 * float(i) / float(ANGLES);
        vec2 dir = vec2(cos(a), sin(a));
        for (int k = 1; k <= STEPS; k++) {
            float r = probe * float(k) / float(STEPS);
            float v = maskAt(v_uv + dir * r * oneTexel);
            grad += dir * (1.0 - v) / float(k);
            covered += v;
        }
    }
    vec2 n = length(grad) > 1e-4 ? normalize(grad) : vec2(0.0);
    float depth = covered / float(ANGLES * STEPS); // 1 deep inside, ~0.5 at the edge
    float edge = clamp((1.0 - depth) * 2.0, 0.0, 1.0);

    // Lens-like bend: strongest near the rim.
    vec2 offset = -n * radius * pow(edge, 1.5) * oneTexel;
    float wobble = sin(time * 1.3 + v_uv.y * 40.0) * 0.4;
    offset += vec2(wobble, 0.0) * oneTexel;

    // Chromatic split + 3x3 frost.
    vec3 c = vec3(0.0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 o = vec2(float(x), float(y)) * oneTexel * 1.5;
            c.r += texture(sceneSampler, v_uv + offset * 1.10 + o).r;
            c.g += texture(sceneSampler, v_uv + offset + o).g;
            c.b += texture(sceneSampler, v_uv + offset * 0.90 + o).b;
        }
    }
    c /= 9.0;

    // Tint, then light: rim glow and a top-left specular lobe.
    c = mix(c, c * color.rgb + color.rgb * 0.15, clamp(fill, 0.0, 1.0));
    float rim = smoothstep(0.35, 1.0, edge);
    float spec = pow(max(0.0, dot(n, normalize(vec2(-0.6, 0.8)))), 4.0) * rim;
    c += vec3(rim * 0.16 + spec * 0.55);
    c *= 1.04;

    FragColor = vec4(min(c, vec3(1.0)), 1.0);
}
