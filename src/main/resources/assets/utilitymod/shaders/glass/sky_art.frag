#version 330 core
// Ambience sky: replaces open-sky pixels (depth == far plane) with an animated procedural sky
// (Daylight clouds / Aurora / Nebula / Sunset haze / Storm), stars, and an optional image pinned
// to a direction in the sky (gnomonic projection, stays put as you turn).
uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform sampler2D uImage;
uniform mat4 uInvViewProj;
uniform vec3 uCenter;
uniform vec3 uRight;
uniform vec3 uUp;
uniform float uTanHalf;
uniform float uAspect;
uniform float uOpacity;
uniform float uHasImage;
uniform int uMode;          // 0 daylight 1 aurora 2 nebula 3 sunset 4 storm
uniform vec3 uColA;         // palette (theme or natural)
uniform vec3 uColB;
uniform float uIntensity;
uniform float uSoftness;
uniform float uStars;
uniform float uGlow;
uniform int uOctaves;       // quality
uniform float uTime;        // already scaled by speed
uniform float uDayLight;
uniform float uDome;        // 1 = image wraps the whole upper sky    // 0 night .. 1 day (vanilla sky brightness)
in vec2 v_uv;
layout (location = 0) out vec4 FragColor;

float hash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float noise(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 8; i++) {
        if (i >= uOctaves) break;
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

vec3 stars(vec3 dir) {
    vec3 p = dir * 180.0;
    vec3 id = floor(p);
    float h = hash(id);
    if (h < 0.985) return vec3(0.0);
    vec3 f = fract(p) - 0.5;
    float d = length(f);
    float tw = 0.6 + 0.4 * sin(uTime * 3.0 + h * 80.0);
    float s = smoothstep(0.18, 0.0, d) * tw;
    return mix(vec3(0.8, 0.85, 1.0), vec3(1.0, 0.85, 0.7), fract(h * 37.0)) * s * 1.6;
}

vec4 art(vec2 uv) {
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) return vec4(0.0);
    vec4 c = texture(uImage, uv);
    vec2 e = min(uv, 1.0 - uv);
    return vec4(c.rgb, c.a * smoothstep(0.0, 0.06, min(e.x, e.y)));
}

vec3 sky(vec3 dir) {
    float h = dir.y;
    float up = clamp(h, 0.0, 1.0);
    vec2 pl = dir.xz / max(0.08, h + 0.25);      // cloud plane projection
    float soft = mix(0.05, 0.6, uSoftness);
    float t = uTime;
    vec3 col;
    if (uMode == 0) {               // daylight clouds
        vec3 base = mix(vec3(0.62, 0.78, 0.95), vec3(0.18, 0.42, 0.85), pow(up, 0.6));
        float c = fbm(vec3(pl * 1.2 + vec2(t * 0.05, 0.0), t * 0.02));
        float cl = smoothstep(0.45 - soft * 0.3, 0.75, c) * smoothstep(-0.02, 0.15, h);
        col = mix(base, vec3(1.0) * (0.85 + 0.15 * c), cl * 0.9);
        col = mix(col, uColA, 0.12);
    } else if (uMode == 1) {        // aurora
        vec3 base = mix(vec3(0.02, 0.05, 0.08), vec3(0.0, 0.01, 0.04), up);
        col = base;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float wave = fbm(vec3(pl.x * 0.35 + fi * 3.0, t * 0.08 + fi, pl.y * 0.12));
            float band = exp(-pow((pl.y * 0.25 + wave * 1.4 - 0.4 - fi * 0.35) / (0.12 + soft * 0.4), 2.0));
            float curtain = fbm(vec3(pl.x * 2.5, pl.y * 0.4 - t * 0.3, fi)) ;
            vec3 c = mix(uColA, uColB, clamp(up * 1.6 + fi * 0.2, 0.0, 1.0));
            col += c * band * (0.4 + curtain) * smoothstep(0.0, 0.2, h) * 0.9;
        }
    } else if (uMode == 2) {        // nebula
        vec3 p = dir * 2.0 + vec3(0.0, 0.0, t * 0.03);
        float n1 = fbm(p * 1.3), n2 = fbm(p * 2.7 + n1 * 2.0);
        col = vec3(0.01, 0.01, 0.03);
        col += uColA * pow(n1, 2.0 + (1.0 - uSoftness) * 2.0) * 1.8;
        col += uColB * pow(n2, 3.0) * 1.6;
        col += vec3(1.0, 0.9, 0.95) * pow(max(n2 - 0.55, 0.0) * 2.2, 3.0);
    } else if (uMode == 3) {        // sunset haze
        float hz = exp(-max(h, 0.0) * 4.0);
        col = mix(vec3(0.1, 0.08, 0.25), uColB, hz);
        col = mix(col, uColA, exp(-abs(h) * 14.0) * 0.9);
        float c = fbm(vec3(pl * 0.8 + vec2(t * 0.03, 0.0), 0.0));
        float cl = smoothstep(0.5 - soft * 0.3, 0.8, c) * smoothstep(0.0, 0.1, h);
        col = mix(col, mix(uColA, vec3(0.25, 0.12, 0.2), up) * 1.1, cl * 0.75);
    } else {                        // storm
        vec3 base = mix(vec3(0.22, 0.24, 0.27), vec3(0.07, 0.08, 0.1), up);
        float c = fbm(vec3(pl * 0.9 + vec2(t * 0.15, t * 0.05), t * 0.05));
        col = base * (0.6 + c * 0.8);
        float flash = step(0.985, fract(sin(floor(t * 1.5) * 91.7) * 4375.0)) * fract(t * 1.5);
        col += vec3(0.7, 0.75, 0.9) * flash * (1.0 - fract(t * 1.5)) * c;
        col = mix(col, uColA * 0.4, 0.1);
    }
    // lower sky: a dramatic gradient (deep blue-violet -> glowing horizon) with lit cloud banks;
    // the chosen effect owns the top of the dome and fades into it
    {
        vec3 deep = mix(vec3(0.05, 0.06, 0.16), uColB * 0.35, 0.4);
        vec3 glowH = mix(vec3(1.0, 0.55, 0.3), uColA, 0.35);
        float hz = exp(-max(h, 0.0) * 6.0);
        vec3 dram = mix(deep, glowH, hz);
        float cb = fbm(vec3(pl * 0.6 + vec2(t * 0.02, 0.0), 3.0));
        float bank = smoothstep(0.45, 0.8, cb) * smoothstep(0.0, 0.08, h) * (1.0 - smoothstep(0.25, 0.45, h));
        vec3 lit = mix(vec3(0.12, 0.08, 0.16), glowH * 1.2, pow(1.0 - clamp(h * 3.0, 0.0, 1.0), 2.0));
        dram = mix(dram, lit, bank * 0.8);
        float top = smoothstep(0.18, 0.55, h);
        col = mix(dram, col, top);
    }
    if (uMode != 0) col += stars(dir) * uStars * smoothstep(-0.05, 0.2, h) * (uMode == 4 ? 0.2 : 1.0);
    // horizon fade into the fog colour so it meets the terrain cleanly
    return col * uIntensity;
}

void main() {
    vec3 scene = texture(uScene, v_uv).rgb;
    vec4 p = uInvViewProj * vec4(v_uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(p.xyz / p.w);
    vec3 col = uIntensity > 0.0 ? sky(dir) : scene;
    // blend with vanilla near/below the horizon so fog + terrain edges stay seamless
    if (uIntensity > 0.0) {
        // merge into vanilla instead of pasting over it: the bottom of our sky takes on the
        // game's own horizon/fog colour (read from the frame), fading in over a wide band
        float keep = 1.0 - smoothstep(-0.02, 0.32, dir.y);
        keep = keep * keep * (3.0 - 2.0 * keep);
        vec3 horizonTint = mix(col, scene, 0.55);          // pull hue toward vanilla's sky
        col = mix(col, horizonTint, 0.35 * (1.0 - keep));
        col = mix(col, scene, keep);
    }

    if (uHasImage > 0.5 && uDome > 0.5) {
        // background mode: wrap the image around the upper dome (mirrored so there is no seam)
        float az = atan(dir.x, dir.z) / 3.14159265 * 0.5 + 0.5;  // 0..1 around
        float u = abs(fract(az * 2.0) * 2.0 - 1.0);               // mirrored twice around
        float el = asin(clamp(dir.y, -1.0, 1.0)) / 1.5707963;   // 0 horizon .. 1 zenith
        vec2 uv = vec2(u, 1.0 - clamp(el * 1.15 + 0.05, 0.0, 1.0));
        vec4 a = texture(uImage, uv);
        float fade = smoothstep(-0.02, 0.18, dir.y);
        col = mix(col, a.rgb, a.a * uOpacity * fade);
    } else if (uHasImage > 0.5) {
        float facing = dot(dir, uCenter);
        if (facing > 0.05) {
            vec2 g = vec2(dot(dir, uRight), dot(dir, uUp)) / facing;
            vec2 uv = vec2(g.x / (uTanHalf * uAspect), g.y / uTanHalf) * 0.5 + 0.5;
            uv.y = 1.0 - uv.y;
            vec4 a = art(uv);
            vec3 halo = vec3(0.0);
            for (int i = 0; i < 8; i++) {
                vec2 o = vec2(cos(float(i) * 0.785398), sin(float(i) * 0.785398)) * 0.06;
                vec4 s = art(uv + o);
                halo += s.rgb * s.a;
            }
            col += halo / 8.0 * uGlow * 0.6;
            col = mix(col, a.rgb, a.a * uOpacity);
        }
    }
    FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
