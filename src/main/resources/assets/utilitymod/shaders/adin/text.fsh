#version 330

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

const int TAPS = 4;
const float GAMMA = 0.6;
const float DARK_LUMA = 0.3;
const float LIGHT_LUMA = 0.6;

float median(vec3 msdf) {
    return max(min(msdf.r, msdf.g), min(max(msdf.r, msdf.g), msdf.b));
}

float coverage() {
    vec2 stepX = dFdx(texCoord);
    vec2 stepY = dFdy(texCoord);
    vec2 unitRange = vec2(MSDF_RANGE) / vec2(textureSize(Sampler0, 0));
    float pixelRange = 0.5 * dot(unitRange, 1.0 / max(abs(stepX) + abs(stepY), vec2(0.000001)));
    float sum = 0.0;
    for (int y = 0; y < TAPS; y++) {
        for (int x = 0; x < TAPS; x++) {
            vec2 offset = (vec2(x, y) + 0.5) / float(TAPS) - 0.5;
            float distance = median(textureLod(Sampler0, texCoord + stepX * offset.x + stepY * offset.y, 0.0).rgb) - 0.5;
            sum += clamp(distance * pixelRange * float(TAPS) + 0.5, 0.0, 1.0);
        }
    }
    return sum / float(TAPS * TAPS);
}

float perceived(float alpha, vec3 color) {
    float onDark = pow(alpha, GAMMA);
    float onLight = 1.0 - pow(1.0 - alpha, GAMMA);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(onLight, onDark, smoothstep(DARK_LUMA, LIGHT_LUMA, luma));
}

void main() {
    fragColor = vec4(vertexColor.rgb, vertexColor.a * perceived(coverage(), vertexColor.rgb));
}
