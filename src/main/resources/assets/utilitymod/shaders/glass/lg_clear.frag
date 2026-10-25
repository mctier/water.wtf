#version 330 core
/*
 * Liquid Glass (Clear) - ported from Jacquesqwq/LiquidGlassShader liquidGlassV2Clear.frag
 * (MIT, (c) 2026 Jack) to GLSL 330. Changes: rounded-rect SDF in pixel space instead of a
 * stretched superellipse, mip-level blur (V3 approach), global alpha.
 */
uniform sampler2D uBlurTex;
uniform vec2 uQuadSize;     // GUI px
uniform float uRadius;      // corner radius, GUI px
uniform float uBlurLod;
uniform float uNoise;
uniform float uRefractionPower;
uniform float uGlowWeight;
uniform float uGlowBias;
uniform float uGlowEdge0;
uniform float uGlowEdge1;
uniform float uAlpha;

in vec2 v_uv;
in vec2 vMidPointNDC;
in vec2 vLocalOffsetNDC;
layout (location = 0) out vec4 FragColor;

const float M_E = 2.718281828459045;

float f(float x) { return 1.0 - 2.3 * pow(5.2 * M_E, -6.9 * x - 0.7); }
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float Glow(vec2 uv) { vec2 g = uv * 2.0 - 1.0; return sin(atan(g.y, g.x) - 0.5); }
bool OutOfBounds(vec2 uv) { return max(uv.x, uv.y) > 1.0 || min(uv.x, uv.y) < 0.0; }

void main() {
    vec2 half_ = uQuadSize * 0.5;
    float sd = sdRoundBox((v_uv - 0.5) * uQuadSize, half_, min(uRadius, min(half_.x, half_.y)));
    float edge = 1.0 - smoothstep(-0.75, 0.75, sd);
    if (edge <= 0.0) discard;
    float dist = clamp(-sd / max(1.0, min(half_.x, half_.y)), 0.0, 1.0);

    float fd = f(dist);
    float refraction = sign(fd) * pow(abs(fd), uRefractionPower);
    vec2 sampleUV = (vMidPointNDC + vLocalOffsetNDC * refraction) * 0.5 + vec2(0.5);
    sampleUV = clamp(sampleUV, vec2(0.001), vec2(0.999));

    vec4 color = textureLod(uBlurTex, sampleUV, uBlurLod);
    color.rgb += vec3((rand(gl_FragCoord.xy * 1e-3) - 0.5) * uNoise);
    float glowMask = smoothstep(uGlowEdge0, uGlowEdge1, dist);
    color.rgb *= Glow(v_uv) * uGlowWeight * glowMask + 1.0 + uGlowBias;

    float a = edge * uAlpha;
    FragColor = vec4(color.rgb, a);
}
