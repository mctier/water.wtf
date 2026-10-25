#version 330

layout(std140) uniform BlockVolume {
    mat4 InvViewProj;
    mat4 ViewProj;
    vec4 FillColor;
    vec4 LineColor;
    vec4 CameraFraction;
    vec4 Forward;
    vec4 Screen;
    ivec4 CameraBlock;
    ivec4 Grid;
};

uniform usamplerBuffer Occupancy;
uniform usamplerBuffer ShapeIds;
uniform samplerBuffer ShapeBounds;

const int SLOT_WORDS = 132;
const int BRICK_WORD = 128;
const int SHAPE_WORD = 130;

vec3 rayOrigin;
vec3 rayDirection;
vec3 inverseDirection;
float pixelScale;

void beginRay(vec2 fragment) {
    vec4 point = InvViewProj * vec4(fragment / Screen.xy * 2.0 - 1.0, 0.5, 1.0);
    rayDirection = normalize(point.xyz / point.w);
    rayOrigin = CameraFraction.xyz;
    inverseDirection = 1.0 / max(abs(rayDirection), vec3(1e-9)) * sign(rayDirection + vec3(1e-30));
    pixelScale = Forward.w * dot(rayDirection, Forward.xyz);
}

void blockBox(uint shapes, int index, out vec3 low, out vec3 high) {
    low = vec3(0.0);
    high = vec3(1.0);
    if (shapes == 0u) return;
    uint id = texelFetch(ShapeIds, int(shapes - 1u) * 4096 + index).r;
    if (id == 0u) return;
    int base = int(id - 1u) * 2;
    low = texelFetch(ShapeBounds, base).xyz;
    high = texelFetch(ShapeBounds, base + 1).xyz;
}

void edge(vec3 start, int axis, float length, inout float coverage, inout float distanceAlong) {
    vec3 toStart = start - rayOrigin;
    float along = rayDirection[axis];
    float denominator = 1.0 - along * along;
    if (denominator < 1e-6) return;
    float projected = dot(toStart, rayDirection);
    float s = clamp((along * projected - toStart[axis]) / denominator, 0.0, length);
    vec3 point = start;
    point[axis] += s;
    float t = dot(point - rayOrigin, rayDirection);
    if (t <= 0.0) return;
    float pixels = distance(rayOrigin + rayDirection * t, point) / (t * pixelScale);
    float value = clamp(LineColor.a * 0.5 + 0.5 - pixels, 0.0, 1.0);
    if (value > coverage) {
        coverage = value;
        distanceAlong = t;
    }
}

vec4 shadeBox(vec3 low, vec3 high) {
    vec3 near = (low - rayOrigin) * inverseDirection;
    vec3 far = (high - rayOrigin) * inverseDirection;
    vec3 entries = min(near, far);
    vec3 exits = max(near, far);
    float enter = max(entries.x, max(entries.y, entries.z));
    float leave = min(exits.x, min(exits.y, exits.z));
    float faces = leave <= 0.0 || enter > leave ? 0.0 : (enter > 0.0 ? 2.0 : 1.0);
    float fill = 1.0 - pow(1.0 - FillColor.a, faces);
    float line = 0.0;
    float lineDistance = 0.0;
    vec3 size = high - low;
    for (int i = 0; i < 4; i++) {
        float a = (i & 1) == 0 ? 0.0 : 1.0;
        float b = (i & 2) == 0 ? 0.0 : 1.0;
        edge(low + vec3(0.0, a * size.y, b * size.z), 0, size.x, line, lineDistance);
        edge(low + vec3(a * size.x, 0.0, b * size.z), 1, size.y, line, lineDistance);
        edge(low + vec3(a * size.x, b * size.y, 0.0), 2, size.z, line, lineDistance);
    }
    if (fill <= 0.0 && line <= 0.0) return vec4(0.0);
    vec3 hit = rayDirection * lineDistance;
    float fog = total_fog_value(lineDistance, max(length(hit.xz), abs(hit.y)), FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd);
    vec3 lineRgb = mix(LineColor.rgb, FogColor.rgb, fog * FogColor.a);
    float fillAlpha = fill * (1.0 - line);
    return vec4(lineRgb * line + FillColor.rgb * fillAlpha, line + fillAlpha);
}
