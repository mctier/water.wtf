#version 330

mat2 mm2(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, s, -s, c);
}

const mat2 M2 = mat2(0.95534, 0.29552, -0.29552, 0.95534);

float tri(float x) {
    return clamp(abs(fract(x) - 0.5), 0.01, 0.49);
}

vec2 tri2(vec2 p) {
    return vec2(tri(p.x) + tri(p.y), tri(p.y + tri(p.x)));
}

float triNoise2d(vec2 p, mat2 spin) {
    float z = 1.8;
    float z2 = 2.5;
    float rz = 0.0;
    p *= mm2(p.x * 0.06);
    vec2 bp = p;
    for (float i = 0.0; i < 5.0; i++) {
        vec2 dg = tri2(bp * 1.85) * 0.75;
        dg *= spin;
        p -= dg / z2;
        bp *= 1.3;
        z2 *= 0.45;
        z *= 0.42;
        p *= 1.21 + (rz - 1.0) * 0.02;
        rz += tri(p.x + tri(p.y)) * z;
        p *= -M2;
    }
    return clamp(1.0 / pow(rz * 29.0, 1.3), 0.0, 0.55);
}

float hash21(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

const float AURORA_STEPS = 50.0;
const float AURORA_STRIDE = 2.0;

vec4 aurora(vec3 ro, vec3 rd, float time) {
    vec4 col = vec4(0.0);
    vec4 avgCol = vec4(0.0);
    mat2 spin = mm2(time * 0.06);
    float dither = 0.006 * hash21(gl_FragCoord.xy);
    for (float i = 0.0; i < AURORA_STEPS; i += AURORA_STRIDE) {
        float of = dither * smoothstep(0.0, 15.0, i);
        float pt = ((0.8 + pow(i, 1.4) * 0.002) - ro.y) / (rd.y * 2.0 + 0.4);
        pt -= of;
        vec3 bpos = ro + pt * rd;
        vec2 p = bpos.zx;
        float rzt = triNoise2d(p, spin);
        vec4 col2 = vec4(0.0, 0.0, 0.0, rzt);
        col2.rgb = (sin(1.0 - vec3(2.15, -0.5, 1.2) + i * 0.043) * 0.5 + 0.5) * rzt;
        avgCol = mix(avgCol, col2, 1.0 - pow(0.5, AURORA_STRIDE));
        col += avgCol * exp2(-i * 0.065 - 2.5) * smoothstep(0.0, 5.0, i) * AURORA_STRIDE;
    }
    col *= clamp(rd.y * 15.0 + 0.4, 0.0, 1.0);
    return col * 1.8;
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(443.8975, 397.2973, 491.1871));
    p += dot(p.zxy, p.yxz + 19.27);
    return fract(vec3(p.x * p.y, p.z * p.x, p.y * p.z));
}

vec3 stars(vec3 p) {
    vec3 c = vec3(0.0);
    float res = 1200.0;
    for (float i = 0.0; i < 4.0; i++) {
        vec3 q = fract(p * (0.15 * res)) - 0.5;
        vec3 id = floor(p * (0.15 * res));
        vec2 rn = hash33(id).xy;
        float c2 = 1.0 - smoothstep(0.0, 0.6, length(q));
        c2 *= step(rn.x, 0.0005 + i * i * 0.001);
        c += c2 * (mix(vec3(1.0, 0.49, 0.1), vec3(0.75, 0.9, 1.0), rn.y) * 0.1 + 0.9);
        p *= 1.3;
    }
    return c * c * 0.8;
}

vec3 auroras(vec2 uv, vec3 base, float time) {
    vec2 p = uv * 0.3;
    vec3 ro = vec3(0.0, 0.0, -6.7);
    vec3 rd = normalize(vec3(p, 1.3));
    rd.xz *= mm2(sin(time * 0.05) * 0.2);
    rd.y = abs(rd.y) + 0.05;
    float fade = smoothstep(0.0, 0.01, abs(rd.y)) * 0.1 + 0.9;
    vec4 aur = smoothstep(0.0, 1.5, aurora(ro, rd, time)) * fade;
    vec3 col = base * 0.2 + stars(rd);
    return col * (1.0 - aur.a) + aur.rgb * (0.4 + base * 1.2);
}
