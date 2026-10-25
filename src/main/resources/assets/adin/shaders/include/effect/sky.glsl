#version 330

vec3 sky(vec2 uv, vec3 base, float time) {
    vec2 p = uv * 1.2 + vec2(time * 0.03, time * 0.005);
    float clouds = smoothstep(0.35, 0.8, fbm(p * 1.5 + fbm(p * 0.7) * 0.5));
    float vertical = clamp(localPosition().y / 2.0, 0.0, 1.0);
    vec3 gradient = mix(base * 0.55, base, vertical);
    return mix(gradient, vec3(1.0), clouds * 0.85);
}
