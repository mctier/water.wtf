#version 330

float waves(vec2 p, float time) {
    float height = 0.0;
    height += sin(p.x * 1.6 + time * 1.1) * 0.35;
    height += sin((p.x * 0.7 + p.y * 1.9) - time * 0.8) * 0.25;
    height += fbm(p * 1.8 + vec2(time * 0.25, -time * 0.15)) * 0.9;
    return height;
}

vec3 ocean(vec2 uv, vec3 base, float time) {
    vec2 p = uv * 2.5;
    float e = 0.02;
    float height = waves(p, time);
    vec3 normal = normalize(vec3(waves(p - vec2(e, 0.0), time) - waves(p + vec2(e, 0.0), time),
            waves(p - vec2(0.0, e), time) - waves(p + vec2(0.0, e), time), e * 6.0));
    vec3 light = normalize(vec3(0.3, 0.8, 0.6));
    float diffuse = clamp(dot(normal, light), 0.0, 1.0);
    float specular = pow(clamp(dot(normal, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0, 1.0), 48.0);
    float foam = smoothstep(0.75, 1.1, height + fbm(p * 6.0 + time * 0.5) * 0.3);
    vec3 deep = base * 0.25;
    vec3 water = mix(deep, base, 0.35 + diffuse * 0.65);
    return mix(water, vec3(0.9, 0.97, 1.0), foam * 0.7) + specular * 0.8;
}
