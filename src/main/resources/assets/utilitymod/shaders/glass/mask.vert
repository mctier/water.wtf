#version 330 core
// Block ESP shader mode: camera-relative cube vertices -> clip space.
layout (location = 0) in vec3 a_pos;
uniform mat4 uMVP;
void main() { gl_Position = uMVP * vec4(a_pos, 1.0); }
