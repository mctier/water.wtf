#version 330 core
/*
 * Liquid Glass - after nnnxxxx11/liquidglass BatchRenderer2D.glsl LiquidGlass()
 * (MIT, (c) 2026 Sepehr Kalanaki; see LICENSE-LiquidGlass-Kalanaki.txt), GLSL 330.
 *
 * The original bends the scene with p * f(dist)^power in normalized quad space, which on
 * long / tall panels turns into a pill shape and a panel-wide flip. Here the same f() profile
 * is applied in pixels inside a fixed lens band along a rounded-rect SDF (the panel's real
 * shape and radius): the rim magnifies/inverts like the reference, the middle stays clear.
 * Plus: sign-safe pow, clamped lookups, edge AA, gentle liquid motion, rim light, tint.
 */
uniform sampler2D uBlurTex;
uniform vec2 uQuadSize;      // GUI px
uniform vec2 uScreen;        // GUI px
uniform float uRadius;       // corner radius, GUI px
uniform float uFPower;       // refraction exponent (u_fPower)
uniform float uNoise;
uniform float uGlowWeight;
uniform float uGlowBias;
uniform float uGlowEdge0;
uniform float uGlowEdge1;
uniform vec3 uTintColor;
uniform float uTintStrength;
uniform float uHighlight;
uniform float uAlpha;
uniform float uTime;
uniform float uMotion;       // liquid motion strength
uniform float uFlowSpeed;

in vec2 v_uv;
in vec2 vMidPointNDC;
in vec2 vLocalOffsetNDC;
layout (location = 0) out vec4 FragColor;

const float M_E = 2.718281828459045;

float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float f(float x) { return 1.0 - 2.3 * pow(5.2 * M_E, -6.9 * x - 0.7); }
float spow(float x, float e) { return sign(x) * pow(abs(x), e); }
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }

// smooth flowing field (two layers of domain-warped waves)
vec2 flow(vec2 q, float t) {
    vec2 w = vec2(sin(q.y * 1.7 + t * 1.10) + sin(q.x * 1.3 - t * 0.70 + q.y * 0.6),
                  cos(q.x * 1.5 - t * 0.90) + sin(q.y * 1.1 + t * 1.30 - q.x * 0.4));
    q += w * 0.55;
    return vec2(sin(q.y * 2.3 - t * 1.6) + 0.5 * sin(q.x * 3.1 + t * 2.1),
                cos(q.x * 2.1 + t * 1.4) + 0.5 * cos(q.y * 2.9 - t * 1.8)) * 0.5;
}

void main() {
    vec2 half_ = uQuadSize * 0.5;
    vec2 local = (v_uv - 0.5) * uQuadSize;                 // GUI px, y down
    float r = min(uRadius, min(half_.x, half_.y));
    float t = uTime * uFlowSpeed;

    // panel shape with a slight surface-tension wobble on the rim (<= ~0.8 px)
    float ang = atan(local.y, local.x);
    float wobble = (sin(ang * 3.0 + t * 1.3) * 0.6 + sin(ang * 5.0 - t * 1.7) * 0.4) * 0.8 * uMotion;
    float sd = sdRoundBox(local, half_, r) + wobble;
    float mask = 1.0 - smoothstep(-0.75, 0.75, sd);
    if (mask <= 0.0) discard;
    float inside = max(-sd, 0.0);                           // px from the rim

    // inward normal from the SDF gradient
    vec2 e = vec2(0.5, 0.0);
    vec2 g = vec2(sdRoundBox(local + e.xy, half_, r) - sdRoundBox(local - e.xy, half_, r),
                  sdRoundBox(local + e.yx, half_, r) - sdRoundBox(local - e.yx, half_, r));
    vec2 n = length(g) > 1e-5 ? normalize(g) : vec2(0.0, -1.0);

    // lens band: f() profile over a fixed px band; k ~ 1 in the middle (no bend), small/negative at the rim
    float band = clamp(min(half_.x, half_.y) * 0.45, 6.0, 28.0);
    float d01 = clamp(inside / band, 0.0, 1.0);
    float k = spow(f(d01), max(0.25, uFPower));
    vec2 bendPx = -n * (1.0 - clamp(k, -1.5, 1.0)) * band * 0.85;   // pull the sample inward -> magnified rim

    // gentle moving liquid, stronger in the body than on the rim
    vec2 qp = local * 0.045;
    vec2 fl = flow(qp, t);
    float body = smoothstep(0.0, 1.0, d01);
    vec2 flowPx = fl * uMotion * (1.0 + 2.0 * body);

    vec2 toUV = vec2(1.0 / uScreen.x, -1.0 / uScreen.y);   // GUI px (y down) -> GL uv (y up)
    vec2 base = (vMidPointNDC + vLocalOffsetNDC) * 0.5 + 0.5;
    vec2 uv = base + (bendPx + flowPx) * toUV;
    // light chromatic split only inside the lens band
    vec2 ca = n * (1.0 - d01) * 0.9 * toUV;
    vec3 color = vec3(texture(uBlurTex, clamp(uv - ca, vec2(0.001), vec2(0.999))).r,
                      texture(uBlurTex, clamp(uv, vec2(0.001), vec2(0.999))).g,
                      texture(uBlurTex, clamp(uv + ca, vec2(0.001), vec2(0.999))).b);
    color += vec3(rand(gl_FragCoord.xy * 1e-3) - 0.5) * uNoise * 0.5;

    // directional rim glow (original Glow(), limited to the band)
    vec2 p = local / max(half_, vec2(1.0));
    float glow = sin(atan(p.y, p.x) - 0.5);
    // Edge0/Edge1 shape how far in the glow reaches (order-safe, unlike smoothstep with reversed edges)
    float reach = clamp(max(abs(uGlowEdge0 - uGlowEdge1), 0.02) * 6.0, 0.05, 1.0);
    float glowMask = 1.0 - clamp(d01 / reach, 0.0, 1.0);
    color *= 1.0 + glow * uGlowWeight * glowMask + uGlowBias;

    // glass colour: absorption through the body, a thicker tint toward the rim
    vec3 tint = uTintColor;
    float edgeT = 1.0 - d01;
    color *= mix(vec3(1.0), tint, clamp(uTintStrength * (1.2 + edgeT * 0.8), 0.0, 1.0));
    color = mix(color, tint, uTintStrength * 0.3);

    // soft caustic shimmer (subtle) + sliding sheen
    vec2 fl2 = flow(qp * 1.7 + 3.1, t * 1.2);
    float caustic = pow(1.0 - abs(sin((fl2.x + fl2.y) * 3.14159)), 18.0);
    color += vec3(caustic * 0.035 * uMotion * body);
    float spec = pow(max(dot(normalize(vec3(-fl, 3.0)), normalize(vec3(-0.4, 0.6, 1.0))), 0.0), 60.0);
    color += vec3(spec * 0.12 * uMotion * uHighlight);

    // crisp rim light, brightest on the top-left
    float rim = 1.0 - smoothstep(0.0, 1.6, inside);
    float lit = 0.55 + 0.45 * max(dot(-n, normalize(vec2(-0.6, -0.8))), 0.0);
    color += mix(vec3(1.0), tint, 0.3) * rim * 0.32 * uHighlight * lit;

    FragColor = vec4(clamp(color, 0.0, 1.0), mask * uAlpha);
}
