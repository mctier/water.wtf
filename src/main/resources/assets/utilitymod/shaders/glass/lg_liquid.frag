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
uniform float uCardMaterial;

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
    float sd = sdRoundBox(local, half_, r) + (uCardMaterial > 0.5 ? 0.0 : wobble);
    float mask = 1.0 - smoothstep(-0.75, 0.75, sd);
    if (mask <= 0.0) discard;
    float inside = max(-sd, 0.0);                           // px from the rim

    // inward normal from the SDF gradient
    vec2 e = vec2(0.5, 0.0);
    vec2 g = vec2(sdRoundBox(local + e.xy, half_, r) - sdRoundBox(local - e.xy, half_, r),
                  sdRoundBox(local + e.yx, half_, r) - sdRoundBox(local - e.yx, half_, r));
    vec2 n = length(g) > 1e-5 ? normalize(g) : vec2(0.0, -1.0);

    // lens band: f() profile over a fixed px band; k ~ 1 in the middle (no bend), small/negative at the rim
    float band = uCardMaterial > 0.5 ? clamp(min(half_.x, half_.y) * 0.5, 8.0, 18.0)
                                     : clamp(min(half_.x, half_.y) * 0.45, 6.0, 28.0);
    float d01 = clamp(inside / band, 0.0, 1.0);
    float k = spow(f(d01), max(0.25, uFPower));
    vec2 bendPx = -n * (1.0 - clamp(k, -1.5, 1.0)) * band * (uCardMaterial > 0.5 ? 0.85 : 0.85);   // pull the sample inward -> magnified rim

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

    vec3 liquid = color;   // the dropdown GUI's liquid-glass result, reused for the card rim
    if (uCardMaterial > 0.5) {
        // Reference card material: clear refracting liquid rim, saturated magenta body.
        vec3 scene = vec3(texture(uBlurTex, clamp(uv - ca, vec2(0.001), vec2(0.999))).r,
                          texture(uBlurTex, clamp(uv, vec2(0.001), vec2(0.999))).g,
                          texture(uBlurTex, clamp(uv + ca, vec2(0.001), vec2(0.999))).b);
        // LegacyPort Glow.java: three slowly drifting ambient orbs over the dark window
        // base, laid out in its 1100x700 window space. Colours derive from the theme tint.
        vec2 P = v_uv * vec2(1100.0, 700.0);
        float T = uTime;
        vec3 base = uTintColor * 0.16;
        vec3 c1 = uTintColor;                                               // centre
        vec3 c2 = min(uTintColor * vec3(1.35, 0.55, 1.15) + 0.04, vec3(1.0)); // lower-left, hotter
        vec3 c3 = min(uTintColor * 0.6 + vec3(0.25, 0.22, 0.32), vec3(1.0));  // upper-right, paler
        float pulse1 = (sin(T * 0.18 * 6.2831855) * 0.5 + 0.5) * 0.1 + 0.9;
        float pulse2 = ((sin(T * 0.22 * 6.2831855 + 1.3) * 0.5 + 0.5) * 0.1 + 0.9) * 1.5;
        float pulse3 = (sin(T * 0.2 * 6.2831855 + 2.7) * 0.5 + 0.5) * 0.1 + 0.9;
        vec2 o1 = vec2(sin(T * 0.11) * 12.0 + 550.0, sin(T * 0.13 + 1.2) * 8.0 + 280.0);
        vec2 o2 = vec2(sin(T * 0.09 + 2.4) * 18.0 + 120.0, sin(T * 0.15) * 10.0 + 504.0);
        vec2 o3 = vec2(sin(T * 0.1 + 0.7) * 14.0 + 1140.0, sin(T * 0.12 + 1.9) * 9.0 - 50.0);
        float a1 = min(0.95, 0.55 * pulse1) * pow(clamp(1.0 - length(P - o1) / 520.0, 0.0, 1.0), 1.4);
        float a2 = min(0.95, 0.50 * pulse2) * pow(clamp(1.0 - length(P - o2) / 460.0, 0.0, 1.0), 1.4);
        float a3 = min(0.95, 0.45 * pulse3) * pow(clamp(1.0 - length(P - o3) / 310.0, 0.0, 1.0), 1.4);
        vec3 pigment = base;
        pigment = mix(pigment, c1, a1);
        pigment = mix(pigment, c2, a2);
        pigment = mix(pigment, c3, a3);
        // body: the world shows through, multiplied into the magenta like stained glass
        vec3 stained = scene * (pigment * 1.6 + 0.08);
        vec3 body = mix(stained, pigment, 0.55);
        // rim (the lens band): mostly clear, magnified scene with a light pink cast
        float rimW = 1.0 - smoothstep(0.55, 1.0, d01);
        vec3 rimCol = liquid;
        color = mix(body, rimCol, rimW);
    }

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
    if (uCardMaterial > 0.5) {
        // bevel: bright outer lip, soft inner shadow line, specular along the band
        float lip = 1.0 - smoothstep(0.0, 2.2, inside);
        float inner = smoothstep(2.0, 3.5, inside) * (1.0 - smoothstep(3.5, 6.0, inside));
        float sheen = pow(1.0 - d01, 3.0);
        color += vec3(1.0, 0.92, 0.98) * lip * 0.45 * lit;
        color *= 1.0 - inner * 0.18;
        color += mix(vec3(1.0), tint, 0.4) * sheen * 0.10 * lit;
    }

    FragColor = vec4(clamp(color, 0.0, 1.0), mask * uAlpha);
}
