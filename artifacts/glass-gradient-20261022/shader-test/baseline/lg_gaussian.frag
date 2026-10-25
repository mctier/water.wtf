#version 330 core
// Separable Gaussian (one direction per pass), like LiquidGlassShader V2's blur stage.
uniform sampler2D textureIn;
uniform vec2 texelSize;
uniform vec2 direction;
uniform float radius;
in vec2 v_uv;
layout (location = 0) out vec4 FragColor;
void main() {
    float sigma = max(radius * 0.5, 0.5);
    vec3 sum = texture(textureIn, v_uv).rgb;
    float wsum = 1.0;
    for (int i = 1; i <= 24; i++) {
        float fi = float(i);
        if (fi > radius) break;
        float w = exp(-(fi * fi) / (2.0 * sigma * sigma));
        vec2 o = direction * texelSize * fi;
        sum += (texture(textureIn, v_uv + o).rgb + texture(textureIn, v_uv - o).rgb) * w;
        wsum += 2.0 * w;
    }
    FragColor = vec4(sum / wsum, 1.0);
}
