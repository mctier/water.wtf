#version 330

in vec4 vertexColor;
in vec2 localPosition;
flat in vec2 shapeSize;
flat in float borderWidth;

out vec4 fragColor;

float boxCoverage(vec2 position, vec2 size) {
    vec2 footprint = max(fwidth(position), vec2(0.0001));
    vec2 covered = clamp((min(position, size - position) / footprint) + 0.5, 0.0, 1.0);
    return covered.x * covered.y;
}

void main() {
    float coverage = boxCoverage(localPosition, shapeSize);
    if (borderWidth > 0.0) {
        vec2 innerSize = shapeSize - 2.0 * borderWidth;
        if (innerSize.x > 0.0 && innerSize.y > 0.0) {
            coverage -= boxCoverage(localPosition - vec2(borderWidth), innerSize);
        }
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * clamp(coverage, 0.0, 1.0));
}
