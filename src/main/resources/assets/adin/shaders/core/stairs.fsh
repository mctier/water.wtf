#version 330

in vec4 vertexColor;
in vec2 localPosition;
flat in vec2 rowSize;
flat in float cornerRadius;
flat in int edges;
flat in vec2 neighbours;

out vec4 fragColor;

const int FLUSH_INNER = 1;
const int FLUSH_TOP = 2;
const float FAR = 4096.0;

float box(vec2 position, vec2 low, vec2 high, vec4 radii) {
    vec2 center = (low + high) * 0.5;
    vec2 offset = position - center;
    float radius = offset.x > 0.0 ? (offset.y > 0.0 ? radii.z : radii.y) : (offset.y > 0.0 ? radii.w : radii.x);
    vec2 q = abs(offset) - (high - low) * 0.5 + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float stepRadius(float wider, float narrower) {
    return min(cornerRadius, floor(max(wider - narrower, 0.0) * 0.5));
}

float fillet(vec2 position, float x, float y, float radius, float direction, float reach) {
    vec2 low = vec2(0.0, direction > 0.0 ? y - reach : y - radius);
    vec2 high = vec2(x + radius, direction > 0.0 ? y + radius : y + reach);
    vec2 center = vec2(x + radius, y + direction * radius);
    return max(box(position, low, high, vec4(0.0)), radius - length(position - center));
}

float stairsDistance(vec2 position) {
    float width = rowSize.x;
    float height = rowSize.y;
    float above = neighbours.x;
    float below = neighbours.y;
    bool hasAbove = above >= 0.0;
    bool hasBelow = below >= 0.0;
    bool flushInner = (edges & FLUSH_INNER) != 0;
    bool flushTop = (edges & FLUSH_TOP) != 0;
    float middle = height * 0.5;
    vec4 radii = vec4(
            hasAbove || flushTop || flushInner ? 0.0 : cornerRadius,
            hasAbove ? stepRadius(width, above) : (flushTop ? 0.0 : cornerRadius),
            hasBelow ? stepRadius(width, below) : cornerRadius,
            hasBelow || flushInner ? 0.0 : cornerRadius);
    float nearest = box(position, vec2(0.0), rowSize, min(radii, vec4(middle)));
    if (hasAbove) {
        nearest = min(nearest, box(position, vec2(0.0, -FAR), vec2(min(width, above), middle), vec4(0.0)));
        nearest = min(nearest, box(position, vec2(0.0, -FAR), vec2(above, 0.0),
                vec4(0.0, 0.0, min(stepRadius(above, width), middle), 0.0)));
        float inward = min(stepRadius(above, width), height);
        if (inward > 0.0) nearest = min(nearest, fillet(position, width, 0.0, inward, 1.0, FAR));
        float outward = min(stepRadius(width, above), height);
        if (outward > 0.0) nearest = min(nearest, fillet(position, above, 0.0, outward, -1.0, height + (hasBelow ? FAR : 0.0)));
    }
    if (hasBelow) {
        nearest = min(nearest, box(position, vec2(0.0, middle), vec2(min(width, below), height + FAR), vec4(0.0)));
        nearest = min(nearest, box(position, vec2(0.0, height), vec2(below, height + FAR),
                vec4(0.0, min(stepRadius(below, width), middle), 0.0, 0.0)));
        float inward = min(stepRadius(below, width), height);
        if (inward > 0.0) nearest = min(nearest, fillet(position, width, height, inward, -1.0, FAR));
        float outward = min(stepRadius(width, below), height);
        if (outward > 0.0) nearest = min(nearest, fillet(position, below, height, outward, 1.0, height + (hasAbove ? FAR : 0.0)));
    }
    return nearest;
}

float coverage(vec2 position) {
    vec2 dx = dFdx(position);
    vec2 dy = dFdy(position);
    float distanceToEdge = stairsDistance(position);
    float filterWidth = max(2.0 * length(vec2(dFdx(distanceToEdge), dFdy(distanceToEdge))), 0.0001);
    float footprint = max(length(dx) + length(dy), 0.0001) + filterWidth;
    if (abs(distanceToEdge) >= footprint) return distanceToEdge < 0.0 ? 1.0 : 0.0;
    float sum = 0.0;
    for (int y = 0; y < 4; ++y) {
        for (int x = 0; x < 4; ++x) {
            vec2 offset = (float(x) - 1.5) * 0.25 * dx + (float(y) - 1.5) * 0.25 * dy;
            float t = clamp(0.5 - 0.5 * stairsDistance(position + offset) / filterWidth, 0.0, 1.0);
            sum += t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        }
    }
    return sum * 0.0625;
}

void main() {
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage(localPosition));
}
