#version 330 core
// Copies the world depth (before the hand pass clears it) into a colour texture for Ambience.
uniform sampler2D uDepth;
in vec2 v_uv;
layout (location = 0) out vec4 FragColor;
void main() { FragColor = vec4(texture(uDepth, v_uv).r, 0.0, 0.0, 1.0); }
