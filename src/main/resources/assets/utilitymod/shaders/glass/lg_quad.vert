#version 330 core

// Panel quad in GUI pixels. Screen-space NDC (GL orientation, y up) drives refraction
// sampling of the scene copy; the output is written y-flipped so the GUI samples the
// glass layer texture upright.
layout (location = 0) in vec2 a_pos;   // GUI px
layout (location = 1) in vec2 a_uv;    // 0..1 across the panel

uniform vec2 uScreen;  // GUI px size
uniform vec2 uMid;     // panel centre, GUI px

out vec2 v_uv;
out vec2 vMidPointNDC;
out vec2 vLocalOffsetNDC;

vec2 toNdc(vec2 px) { return vec2(px.x / uScreen.x * 2.0 - 1.0, 1.0 - px.y / uScreen.y * 2.0); }

void main() {
    vec2 ndc = toNdc(a_pos);
    vMidPointNDC = toNdc(uMid);
    vLocalOffsetNDC = ndc - vMidPointNDC;
    v_uv = a_uv;
    gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);
}
