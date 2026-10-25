#version 330

#moj_import <adin:rect_coverage.glsl>

#define MODE_SOLID 0
#define MODE_HUE_BAR 1
#define MODE_SATURATION_VALUE 2

in vec4 vertexColor;
in vec2 localPosition;
flat in vec2 rectSize;
flat in float cornerRadius;
flat in int shape;

out vec4 fragColor;

#if RECT_MODE == MODE_HUE_BAR
vec3 hueToRgb(float hue) {
    vec3 p = abs(fract(hue + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return clamp(p - 1.0, 0.0, 1.0);
}
#endif

void main() {
    float coverage = rectCoverage(localPosition, rectSize, cornerRadius, shape);
#if RECT_MODE == MODE_HUE_BAR
    vec3 rgb = hueToRgb(clamp(localPosition.x / rectSize.x, 0.0, 1.0));
#elif RECT_MODE == MODE_SATURATION_VALUE
    float saturation = clamp(localPosition.x / rectSize.x, 0.0, 1.0);
    float value = 1.0 - clamp(localPosition.y / rectSize.y, 0.0, 1.0);
    vec3 rgb = value * mix(vec3(1.0), vertexColor.rgb, saturation);
#else
    vec3 rgb = vertexColor.rgb;
#endif
    fragColor = vec4(rgb, vertexColor.a * coverage);
}
