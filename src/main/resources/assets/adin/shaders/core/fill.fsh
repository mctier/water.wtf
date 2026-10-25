#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 relativePosition;
in vec3 worldNormal;
in vec3 viewPosition;
in vec3 viewNormal;
flat in ivec2 packedA;
flat in ivec2 packedB;
out vec4 fragColor;

const float SECONDS_PER_DAY = 1200.0;
const float ORIGIN_UNITS = 512.0;
const float ORIGIN_WRAP = 65536.0 / ORIGIN_UNITS;
const float SCALE_UNITS = 256.0;
const float TRIPLANAR_SHARPNESS = 8.0;
const float TRIPLANAR_CUTOFF = 0.01;

vec3 gradientColor(vec3 base) {
    int encoded = packedA.x & 0xFFFF;
    vec3 end = vec3(float(encoded & 0xFF), float((encoded >> 8) & 0xFF), float(packedA.y & 0xFF)) / 255.0;
    float span = float(packedB.y - packedB.x);
    float t = span > 0.0 ? clamp((float(packedB.y) - gl_FragCoord.y) / span, 0.0, 1.0) : 0.0;
    return mix(base, end, t);
}

vec3 localPosition() {
    vec3 origin = vec3(float(packedA.x), float(packedA.y), float(packedB.x)) / ORIGIN_UNITS;
    float scale = max(float(packedB.y & 0xFFFF) / SCALE_UNITS, 0.001);
    vec3 offset = mod(relativePosition - origin + ORIGIN_WRAP * 0.5, ORIGIN_WRAP) - ORIGIN_WRAP * 0.5;
#if HAND
    offset = mat3(ModelViewMat) * offset;
#endif
    return offset / scale;
}

#moj_import <adin:effect/noise.glsl>
#moj_import <adin:effect/galaxy.glsl>
#moj_import <adin:effect/metallic.glsl>
#moj_import <adin:effect/aurora.glsl>
#moj_import <adin:effect/ocean.glsl>
#moj_import <adin:effect/lava.glsl>
#moj_import <adin:effect/sky.glsl>

vec3 pattern(vec2 uv, vec3 base, float time) {
#if EFFECT == 1
    return starNest(uv, base, time);
#elif EFFECT == 3
    return auroras(uv, base, time);
#elif EFFECT == 4
    return ocean(uv, base, time);
#elif EFFECT == 5
    return lava(uv, base, time);
#else
    return sky(uv, base, time);
#endif
}

vec3 triplanar(vec3 base, float time) {
    vec3 local = localPosition();
#if HAND
    vec3 normal = viewNormal;
#else
    vec3 normal = worldNormal;
#endif
    vec3 weight = pow(abs(normal), vec3(TRIPLANAR_SHARPNESS)) + 1e-6;
    weight /= weight.x + weight.y + weight.z;
    vec3 color = vec3(0.0);
    float used = 0.0;
    if (weight.x > TRIPLANAR_CUTOFF) { color += weight.x * pattern(local.zy, base, time); used += weight.x; }
    if (weight.y > TRIPLANAR_CUTOFF) { color += weight.y * pattern(local.xz, base, time); used += weight.y; }
    if (weight.z > TRIPLANAR_CUTOFF) { color += weight.z * pattern(local.xy, base, time); used += weight.z; }
    return color / used;
}

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    vec3 base = vertexColor.rgb;
    float time = GameTime * SECONDS_PER_DAY;
#if EFFECT == 0
    vec3 color = gradientColor(base);
#elif EFFECT == 2
    vec3 color = metallic(base, time);
#else
    vec3 color = triplanar(base, time);
#endif
    fragColor = vec4(clamp(color, 0.0, 1.0), vertexColor.a);
}
