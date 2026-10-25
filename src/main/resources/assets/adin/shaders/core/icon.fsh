#version 330

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;
flat in vec3 backdrop;
out vec4 fragColor;

const int TAPS = 4;

vec3 toLinear(vec3 color) {
    return mix(color / 12.92, pow((color + 0.055) / 1.055, vec3(2.4)), step(0.04045, color));
}

vec3 toSrgb(vec3 color) {
    return mix(color * 12.92, 1.055 * pow(color, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, color));
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
            float distance = textureLod(Sampler0, texCoord + stepX * offset.x + stepY * offset.y, 0.0).r - 0.5;
            sum += clamp(distance * pixelRange * float(TAPS) + 0.5, 0.0, 1.0);
        }
    }
    return sum / float(TAPS * TAPS);
}

void main() {
    float alpha = coverage() * vertexColor.a;
    vec3 blended = toSrgb(mix(toLinear(backdrop), toLinear(vertexColor.rgb), alpha));
    fragColor = vec4(max(blended - backdrop * (1.0 - alpha), 0.0), alpha);
}
