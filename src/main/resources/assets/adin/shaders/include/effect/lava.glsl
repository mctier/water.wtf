#version 330

vec3 lava(vec2 uv, vec3 base, float time) {
    vec2 p = uv * 2.0;
    float flow = fbm(p * 1.3 + vec2(time * 0.08, -time * 0.18));
    float cracks = abs(sin(p.x * 3.1 + sin(p.y * 4.3 + time * 0.8)) + sin(p.y * 5.2 + flow * 4.0 - time * 1.1) * 0.55);
    float crack = smoothstep(0.38, 0.74, cracks);
    float heat = smoothstep(0.45, 0.98, crack);
    vec3 crust = base * 0.06;
    vec3 hot = base;
    vec3 glow = mix(base, vec3(1.0, 0.85, 0.4), 0.6);
    return mix(crust, hot, crack) + glow * heat * 0.7;
}
