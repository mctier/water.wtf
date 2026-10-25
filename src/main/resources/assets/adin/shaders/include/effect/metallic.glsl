#version 330

vec3 metallic(vec3 base, float time) {
    vec3 normal = normalize(viewNormal);
    vec3 view = normalize(-viewPosition);
    vec3 light = normalize(vec3(-0.45, 0.70, 0.55));
    vec3 reflected = reflect(-view, normal);
    float ndl = clamp(dot(normal, light) * 0.5 + 0.5, 0.0, 1.0);
    float fresnel = pow(1.0 - clamp(abs(dot(normal, view)), 0.0, 1.0), 4.0);
    float sky = clamp(reflected.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 environment = mix(base * 0.08, mix(base * 0.38, vec3(1.0), 0.72), smoothstep(0.08, 0.92, sky));
    float horizon = exp2(-abs(reflected.y) * 10.0);
    float specular = pow(clamp(dot(normal, normalize(light + view)), 0.0, 1.0), 72.0);
    float sweep = 0.5 + 0.5 * sin(dot(localPosition(), vec3(0.7, 1.3, 0.4)) * 6.0 + time * 0.8);
    return base * (0.10 + ndl * 0.24) + environment * (0.48 + fresnel * 0.42) * (0.8 + 0.2 * sweep)
            + vec3(1.0) * horizon * 0.20 + vec3(1.0) * specular * 0.88;
}
