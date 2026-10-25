#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <adin:block_esp.glsl>

uniform usamplerBuffer Lookup;

out vec4 fragColor;

const int MAX_STEPS = 8192;
const float OPAQUE = 0.998;

int slotAt(ivec3 section) {
    ivec3 delta = section - (CameraBlock.xyz >> 4);
    if (abs(delta.x) > Grid.x || abs(delta.z) > Grid.x) return -1;
    int y = section.y - Grid.y;
    if (y < 0 || y >= Grid.z) return -1;
    int width = CameraBlock.w;
    int x = ((section.x % width) + width) % width;
    int z = ((section.z % width) + width) % width;
    return int(texelFetch(Lookup, (y * width + z) * width + x).r) - 1;
}

float exitDistance(vec3 cellMin, float size) {
    vec3 bound = cellMin + step(0.0, rayDirection) * size;
    vec3 exits = (bound - rayOrigin) * inverseDirection;
    return min(exits.x, min(exits.y, exits.z));
}

void main() {
    beginRay(gl_FragCoord.xy);
    vec4 color = vec4(0.0);
    float limit = float(Grid.w);
    ivec3 current = ivec3(2147483647);
    int slot = -1;
    uint brickLow = 0u;
    uint brickHigh = 0u;
    uint shapes = 0u;
    int cachedWord = -1;
    uint bits = 0u;
    float t = 0.0;
    for (int i = 0; i < MAX_STEPS && t < limit; i++) {
        ivec3 local = ivec3(floor(rayOrigin + rayDirection * (t + 1e-3)));
        ivec3 world = CameraBlock.xyz + local;
        ivec3 section = world >> 4;
        if (section != current) {
            current = section;
            slot = slotAt(section);
            cachedWord = -1;
            if (slot >= 0) {
                int base = slot * SLOT_WORDS;
                brickLow = texelFetch(Occupancy, base + BRICK_WORD).r;
                brickHigh = texelFetch(Occupancy, base + BRICK_WORD + 1).r;
                shapes = texelFetch(Occupancy, base + SHAPE_WORD).r;
            }
        }
        if (slot < 0) {
            int y = section.y - Grid.y;
            if ((y < 0 && rayDirection.y <= 0.0) || (y >= Grid.z && rayDirection.y >= 0.0)) break;
            t = exitDistance(vec3((section << 4) - CameraBlock.xyz), 16.0);
            continue;
        }
        ivec3 inner = world & 15;
        ivec3 brick = inner >> 2;
        int brickIndex = (brick.y << 4) | (brick.z << 2) | brick.x;
        if (((brickIndex < 32 ? brickLow : brickHigh) & (1u << uint(brickIndex & 31))) == 0u) {
            t = exitDistance(vec3((world & ~3) - CameraBlock.xyz), 4.0);
            continue;
        }
        int index = (inner.y << 8) | (inner.z << 4) | inner.x;
        int wordIndex = index >> 5;
        if (wordIndex != cachedWord) {
            cachedWord = wordIndex;
            bits = texelFetch(Occupancy, slot * SLOT_WORDS + wordIndex).r;
        }
        if ((bits & (1u << uint(index & 31))) != 0u) {
            vec3 low;
            vec3 high;
            blockBox(shapes, index, low, high);
            color += (1.0 - color.a) * shadeBox(vec3(local) + low, vec3(local) + high);
            if (color.a > OPAQUE) break;
        }
        t = exitDistance(vec3(local), 1.0);
    }
    if (color.a <= 0.0) discard;
    fragColor = color;
}
