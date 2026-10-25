#version 330 core
// Straight copy of the main framebuffer into the mipmapped blur source.
uniform sampler2D textureIn;
in vec2 v_uv;
layout (location = 0) out vec4 FragColor;
void main() { FragColor = vec4(texture(textureIn, v_uv).rgb, 1.0); }
