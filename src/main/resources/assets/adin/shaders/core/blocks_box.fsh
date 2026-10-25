#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <adin:block_esp.glsl>

flat in int kind;
flat in vec3 boxLow;
flat in vec3 boxHigh;
flat in vec4 segment;
in vec3 relative;

out vec4 fragColor;

void main() {
    if (kind == 2) {
        beginRay(gl_FragCoord.xy);
        vec4 color = shadeBox(boxLow, boxHigh);
        if (color.a <= 0.0) discard;
        fragColor = color;
        return;
    }
    if (kind == 0) {
        fragColor = vec4(FillColor.rgb * FillColor.a, FillColor.a);
        if (FillColor.a <= 0.0) discard;
        return;
    }
    vec2 a = segment.xy;
    vec2 b = segment.zw;
    vec2 ab = b - a;
    float s = clamp(dot(gl_FragCoord.xy - a, ab) / max(dot(ab, ab), 1e-8), 0.0, 1.0);
    float pixels = distance(gl_FragCoord.xy, a + ab * s);
    float coverage = clamp(LineColor.a * 0.5 + 0.5 - pixels, 0.0, 1.0);
    if (coverage <= 0.0) discard;
    float fog = total_fog_value(length(relative), max(length(relative.xz), abs(relative.y)), FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd);
    vec3 lineRgb = mix(LineColor.rgb, FogColor.rgb, fog * FogColor.a);
    fragColor = vec4(lineRgb * coverage, coverage);
}
