#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <adin:block_esp.glsl>

uniform isamplerBuffer Origins;

in uint Block;

flat out int kind;
flat out vec3 boxLow;
flat out vec3 boxHigh;
flat out vec4 segment;
out vec3 relative;

const float TINY_PIXELS = 10.0;
const int QUAD_BASE = 56;
const vec4 HIDDEN = vec4(2.0, 2.0, 0.0, 1.0);
const int[24] EDGES = int[](0, 1, 2, 3, 4, 5, 6, 7, 0, 2, 1, 3, 4, 6, 5, 7, 0, 4, 1, 5, 2, 6, 3, 7);

vec3 corner(vec3 low, vec3 high, int index) {
    return mix(low, high, vec3(index & 1, (index >> 1) & 1, (index >> 2) & 1));
}

vec4 project(vec3 position) {
    return ViewProj * vec4(position - CameraFraction.xyz, 1.0);
}

void main() {
    int slot = int(Block >> 12u);
    int index = int(Block & 4095u);
    ivec3 local = ivec3(index & 15, index >> 8, (index >> 4) & 15);
    vec3 cell = vec3(texelFetch(Origins, slot).xyz * 16 + local - CameraBlock.xyz);
    vec3 low;
    vec3 high;
    blockBox(texelFetch(Occupancy, slot * SLOT_WORDS + SHAPE_WORD).r, index, low, high);
    low += cell;
    high += cell;
    boxLow = low;
    boxHigh = high;
    segment = vec4(0.0);
    relative = vec3(0.0);
    int vertex = gl_VertexID;
    float depth = dot((low + high) * 0.5 - CameraFraction.xyz, Forward.xyz);
    bool forced = vertex >= QUAD_BASE;
    if (forced || (depth > 2.0 && length(high - low) < TINY_PIXELS * depth * Forward.w)) {
        kind = 2;
        if (!forced && (vertex < 8 || vertex >= 12)) {
            gl_Position = HIDDEN;
            return;
        }
        vec2 lowest = vec2(1e9);
        vec2 highest = vec2(-1e9);
        for (int i = 0; i < 8; i++) {
            vec4 clip = project(corner(low, high, i));
            vec2 ndc = clip.xy / clip.w;
            lowest = min(lowest, ndc);
            highest = max(highest, ndc);
        }
        vec2 pad = (LineColor.a * 0.5 + 1.0) * 2.0 / Screen.xy;
        lowest -= pad;
        highest += pad;
        int quad = forced ? vertex - QUAD_BASE : vertex - 8;
        gl_Position = vec4(quad == 0 || quad == 3 ? lowest.x : highest.x, quad < 2 ? lowest.y : highest.y, 0.0, 1.0);
        return;
    }
    if (vertex < 8) {
        kind = 0;
        relative = corner(low, high, vertex) - CameraFraction.xyz;
        gl_Position = ViewProj * vec4(relative, 1.0);
        return;
    }
    kind = 1;
    int edge = (vertex - 8) >> 2;
    int side = (vertex - 8) & 3;
    vec3 a = corner(low, high, EDGES[edge * 2]);
    vec3 b = corner(low, high, EDGES[edge * 2 + 1]);
    vec4 clipA = project(a);
    vec4 clipB = project(b);
    float near = 1e-3;
    if (clipA.w < near && clipB.w < near) {
        gl_Position = HIDDEN;
        return;
    }
    if (clipA.w < near) {
        float k = (near - clipA.w) / (clipB.w - clipA.w);
        a = mix(a, b, k);
        clipA = mix(clipA, clipB, k);
    } else if (clipB.w < near) {
        float k = (near - clipB.w) / (clipA.w - clipB.w);
        b = mix(b, a, k);
        clipB = mix(clipB, clipA, k);
    }
    vec2 pixelA = (clipA.xy / clipA.w * 0.5 + 0.5) * Screen.xy;
    vec2 pixelB = (clipB.xy / clipB.w * 0.5 + 0.5) * Screen.xy;
    vec2 along = pixelB - pixelA;
    float length = max(length(along), 1e-4);
    along /= length;
    vec2 across = vec2(-along.y, along.x);
    float reach = LineColor.a * 0.5 + 1.0;
    bool end = side >= 2;
    vec2 pixel = (end ? pixelB : pixelA) + along * (end ? reach : -reach) + across * (side == 0 || side == 3 ? -reach : reach);
    vec4 clip = end ? clipB : clipA;
    segment = vec4(pixelA, pixelB);
    relative = end ? b - CameraFraction.xyz : a - CameraFraction.xyz;
    gl_Position = vec4((pixel / Screen.xy * 2.0 - 1.0) * clip.w, clip.z, clip.w);
}
