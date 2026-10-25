#version 330 core
// Motion blur blend: history frame drawn over the current one with alpha, and a saturation tweak.
uniform sampler2D textureIn;
uniform float uAlpha;
uniform float uSaturation;
in vec2 v_uv;
layout (location = 0) out vec4 FragColor;
void main() {
    vec3 c = texture(textureIn, v_uv).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, uSaturation);
    FragColor = vec4(clamp(c, 0.0, 1.0), uAlpha);
}
