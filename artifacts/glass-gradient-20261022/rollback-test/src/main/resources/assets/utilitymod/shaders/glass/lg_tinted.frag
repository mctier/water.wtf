#version 330 core
/*
 * Liquid Glass (Tinted) - ported from Jacquesqwq/LiquidGlassShader liquidGlassV2Tinted.frag
 * (MIT, (c) 2026 Jack) to GLSL 330. Changes: rounded-rect SDF in pixel space instead of a
 * stretched superellipse, mip-level blur (V3 approach), global alpha.
 */
uniform sampler2D uBlurTex;
uniform vec2 uQuadSize;
uniform float uRadius;
uniform float uBlurLod;
uniform float uNoise;
uniform float uRefractionPower;
uniform vec3 uTintColor;
uniform float uTintStrength;
uniform float uChromaStrength;
uniform float uDarkness;
uniform float uAlpha;

in vec2 v_uv;
in vec2 vMidPointNDC;
in vec2 vLocalOffsetNDC;
layout (location = 0) out vec4 FragColor;

const float M_E = 2.718281828459045;

float f(float x) { return 1.0 - 2.3 * pow(5.2 * M_E, -6.9 * x - 0.7); }
float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }

void main() {
    vec2 half_ = uQuadSize * 0.5;
    float sd = sdRoundBox((v_uv - 0.5) * uQuadSize, half_, min(uRadius, min(half_.x, half_.y)));
    float edge = 1.0 - smoothstep(-0.75, 0.75, sd);
    if (edge <= 0.0) discard;

    // Material sampling.
    float dist = clamp(-sd / max(1.0, min(half_.x, half_.y)), 0.0, 1.0);
    float fresnel = pow(1.0 - dist, 3.0);
    float fd = f(dist);
    float refraction = sign(fd) * pow(abs(fd), uRefractionPower);
    vec2 sampleUV = clamp((vMidPointNDC + vLocalOffsetNDC * refraction) * 0.5 + vec2(0.5), vec2(0.001), vec2(0.999));

    // Subtle chromatic softness.
    vec2 chromaDir = normalize(vLocalOffsetNDC + 0.00001);
    vec2 chromaOffset = chromaDir * fresnel * uChromaStrength;
    vec4 color = vec4(
            textureLod(uBlurTex, sampleUV + chromaOffset, uBlurLod).r,
            textureLod(uBlurTex, sampleUV, uBlurLod).g,
            textureLod(uBlurTex, sampleUV - chromaOffset, uBlurLod).b,
            1.0);

    // Low-frequency material diffusion.
    float micro = sin(gl_FragCoord.x * 0.015 + gl_FragCoord.y * 0.008) * sin(gl_FragCoord.y * 0.012 - gl_FragCoord.x * 0.006);
    color.rgb += micro * uNoise * 0.015;

    // Material shaping.
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    color.rgb += (micro - 0.5) * uNoise * 0.22;
    color.rgb = mix(vec3(luma), color.rgb, mix(0.45, 0.75, luma));
    vec3 adaptiveTintColor = mix(uTintColor, vec3(0.08, 0.09, 0.11), uDarkness);
    color.rgb = mix(color.rgb, adaptiveTintColor, uTintStrength * (1.0 - luma * 0.5));
    color.rgb += uTintColor * fresnel * mix(0.12, 0.06, luma);
    color.rgb *= 0.96 + smoothstep(0.0, 1.0, dist) * 0.04;
    color.rgb *= mix(1.08, 0.98, luma) * mix(1.0, 0.82, uDarkness);

    // Optical edge falloff.
    float opticalEdge = pow(edge, 1.35);
    FragColor = vec4(color.rgb, opticalEdge * uAlpha);
}
