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
uniform float uCardMaterial;
uniform vec2 uScreen;

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

    // Cards use a bounded edge lens, not a panel-wide magnifier.
    float rim = 1.0 - smoothstep(0.0, 28.0, max(-sd, 0.0));
    vec2 pixelUV = vec2(1.0, -1.0) / max(uScreen, vec2(1.0));
    vec2 local = (v_uv - 0.5) * uQuadSize;
    vec2 q = abs(local) - half_ + min(uRadius, min(half_.x, half_.y));
    vec2 normal = q.x > q.y ? vec2(sign(local.x), 0.0) : vec2(0.0, sign(local.y));
    if (q.x > 0.0 && q.y > 0.0) normal = normalize(max(q, vec2(0.0))) * sign(local);
    if (uCardMaterial > 0.5) {
        vec2 baseUV = (vMidPointNDC + vLocalOffsetNDC) * 0.5 + 0.5;
        float lensPixels = 10.0 * clamp(uRefractionPower / 0.32, 0.0, 1.0);
        sampleUV = clamp(baseUV - normal * pixelUV * lensPixels * rim * rim, vec2(0.001), vec2(0.999));
    }

    // Subtle chromatic softness.
    vec2 chromaDir = normalize(vLocalOffsetNDC + 0.00001);
    vec2 chromaOffset = chromaDir * fresnel * uChromaStrength;
    if (uCardMaterial > 0.5) chromaOffset = normal * pixelUV * 0.55 * rim * clamp(uChromaStrength / 0.0008, 0.0, 1.0);
    vec4 color = vec4(
            textureLod(uBlurTex, sampleUV + chromaOffset, uBlurLod).r,
            textureLod(uBlurTex, sampleUV, uBlurLod).g,
            textureLod(uBlurTex, sampleUV - chromaOffset, uBlurLod).b,
            1.0);

    if (uCardMaterial > 0.5) {
        // Panel-space pigment stays rich even against bright sky or pale stone.
        vec3 topPlum = uTintColor * vec3(0.34, 0.32, 0.44);
        vec3 lowerPlum = uTintColor * vec3(0.74, 0.32, 0.81);
        vec3 pigment = mix(topPlum, lowerPlum, smoothstep(0.0, 1.0, v_uv.y));
        // Three overlapping ambient lobes, inspired by LegacyPort Glow.java.
        // Its 1100x700 composition is evaluated analytically: no circle meshes,
        // extra framebuffer passes, or time animation (stable with reduced motion).
        vec2 glowPoint = v_uv * vec2(1100.0, 700.0);
        float centerGlow = exp(-2.2 * dot((glowPoint - vec2(550.0, 280.0)) / 520.0,
                                        (glowPoint - vec2(550.0, 280.0)) / 520.0));
        float lowerGlow = exp(-2.2 * dot((glowPoint - vec2(120.0, 504.0)) / 460.0,
                                       (glowPoint - vec2(120.0, 504.0)) / 460.0));
        float upperGlow = exp(-2.2 * dot((glowPoint - vec2(1140.0, -50.0)) / 310.0,
                                       (glowPoint - vec2(1140.0, -50.0)) / 310.0));
        vec3 centerColor = min(uTintColor * vec3(1.12, 0.55, 1.38), vec3(1.0));
        vec3 lowerColor = min(uTintColor * vec3(1.38, 0.12, 0.96), vec3(1.0));
        vec3 upperColor = min(uTintColor * vec3(1.10, 1.65, 1.72), vec3(1.0));
        pigment = mix(pigment, centerColor, centerGlow * 0.46);
        pigment = mix(pigment, lowerColor, lowerGlow * 0.86);
        pigment = mix(pigment, upperColor, upperGlow * 0.32);
        float darkRight = smoothstep(0.57, 1.0, v_uv.x) * 0.78;
        pigment = mix(pigment, vec3(0.035, 0.012, 0.045), darkRight);
        // Preserve backdrop details without washing the pigment into gray.
        vec3 scene = color.rgb / (vec3(1.0) + color.rgb * 0.35);
        // Clearer bevel reveals the refracted scene; the interior pigment is unchanged.
        float pigmentWeight = mix(0.34, 0.48, darkRight) - rim * 0.20;
        color.rgb = mix(scene * vec3(0.92, 0.68, 0.88), pigment, pigmentWeight);
        float lip = exp(-max(-sd, 0.0) / 1.25);
        float directional = 0.45 + 0.55 * max(0.0, dot(normal, normalize(vec2(-0.6, -0.8))));
        // Soft inner caustic stays inside the same 28px lens band.
        float innerGlow = exp(-max(-sd, 0.0) / 6.0) * rim;
        color.rgb += vec3(0.85, 0.57, 0.83) * directional * (lip * 0.20 + innerGlow * 0.09);
        FragColor = vec4(color.rgb, pow(edge, 1.35) * uAlpha);
        return;
    }

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
