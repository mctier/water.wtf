#version 330

const int ITERATIONS = 17;
const float FORMUPARAM = 0.530;
const int VOLSTEPS = 18;
const float STEPSIZE = 0.100;
const float ZOOM = 0.800;
const float TILE = 0.850;
const float SPEED = 0.010;
const float BRIGHTNESS = 0.0015;
const float DARKMATTER = 0.300;
const float DISTFADING = 0.760;
const float SATURATION = 0.800;

vec3 starNest(vec2 uv, vec3 base, float time) {
    uv *= 0.35;
    vec3 dir = vec3(uv * ZOOM, 1.0);
    float t = time * SPEED + 0.25;
    float a1 = 0.5;
    float a2 = 0.8;
    mat2 rot1 = mat2(cos(a1), sin(a1), -sin(a1), cos(a1));
    mat2 rot2 = mat2(cos(a2), sin(a2), -sin(a2), cos(a2));
    dir.xz *= rot1;
    dir.xy *= rot2;
    vec3 from = vec3(1.0, 0.5, 0.5) + vec3(t * 2.0, t, -2.0);
    from.xz *= rot1;
    from.xy *= rot2;
    float s = 0.1;
    float fade = 1.0;
    vec3 v = vec3(0.0);
    for (int r = 0; r < VOLSTEPS; r++) {
        vec3 p = from + s * dir * 0.5;
        p = abs(vec3(TILE) - mod(p, vec3(TILE * 2.0)));
        float pa = 0.0;
        float a = 0.0;
        for (int i = 0; i < ITERATIONS; i++) {
            p = abs(p) / dot(p, p) - FORMUPARAM;
            a += abs(length(p) - pa);
            pa = length(p);
        }
        float dm = max(0.0, DARKMATTER - a * a * 0.001);
        a *= a * a;
        if (r > 3) fade *= 1.0 - dm;
        v += fade;
        v += vec3(s, s * s, s * s * s * s) * a * BRIGHTNESS * fade;
        fade *= DISTFADING;
        s += STEPSIZE;
    }
    v = mix(vec3(length(v)), v, SATURATION);
    return v * 0.01 * (0.3 + base * 1.4);
}
