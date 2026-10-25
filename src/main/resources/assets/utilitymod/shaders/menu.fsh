#version 150
uniform float Time;
uniform vec4 ColorModulator;
in vec4 vertexColor;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 base = vertexColor * ColorModulator;
    float pulse = 0.5 + 0.5 * sin(Time * 2.0 + texCoord.x * 10.0);
    vec3 tint = mix(base.rgb, vec3(0.2, 0.8, 0.4), pulse * 0.15);
    fragColor = vec4(tint, base.a);
}
