#version 330 core
/*
 * Liquid Glass - ported from nnnxxxx11/liquidglass BatchRenderer2D.glsl LiquidGlass()
 * (MIT, (c) 2026 Sepehr Kalanaki; see LICENSE-LiquidGlass-Kalanaki.txt) to GLSL 330.
 * Superellipse SDF in quad space; scene sampled at p * f(dist)^power, which goes
 * negative near the rim (odd power) -> the inverted, magnified lens edge. Changes:
 * sign-safe pow, clamped lookups instead of magenta, rim light + edge AA + alpha.
 */
uniform sampler2D uBlurTex;
uniform vec2 uQuadSize;
uniform vec2 uHalfNdc;      // panel half-size in NDC
uniform float uPowerFactor;  // superellipse n
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
uniform vec2 uScreen;        // GUI px
uniform float uTime;
uniform float uMotion;       // liquid motion strength
uniform float uFlowSpeed;

in vec2 v_uv;
in vec2 vMidPointNDC;
in vec2 vLocalOffsetNDC;
layout (location = 0) out vec4 FragColor;

const float M_E = 2.718281828459045;

float sdSuperellipse(vec2 p, float n, float r) {
    vec2 a = abs(p);
    float num = pow(a.x, n) + pow(a.y, n) - pow(r, n);
    float den = n * sqrt(pow(a.x, 2.0 * n - 2.0) + pow(a.y, 2.0 * n - 2.0)) + 0.00001;
    return num / den;
}
float f(float x) { return 1.0 - 2.3 * pow(5.2 * M_E, -6.9 * x - 0.7); }
float spow(float x, float e) { return sign(x) * pow(abs(x), e); }
// Smooth flowing field: two layers of domain-warped waves (a liquid film over the glass).
vec2 flow(vec2 q, float t) {
    vec2 w = vec2(sin(q.y * 1.7 + t * 1.10) + sin(q.x * 1.3 - t * 0.70 + q.y * 0.6),
                  cos(q.x * 1.5 - t * 0.90) + sin(q.y * 1.1 + t * 1.30 - q.x * 0.4));
    q += w * 0.55;
    return vec2(sin(q.y * 2.3 - t * 1.6) + 0.5 * sin(q.x * 3.1 + t * 2.1),
                cos(q.x * 2.1 + t * 1.4) + 0.5 * cos(q.y * 2.9 - t * 1.8)) * 0.5;
}
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    vec2 p = (v_uv - 0.5) * 2.0;
    float t = uTime * uFlowSpeed;
    // wobbling surface: the rim breathes like a droplet held by surface tension
    float ang = atan(p.y, p.x);
    float wobble = (sin(ang * 3.0 + t * 1.3) * 0.6 + sin(ang * 5.0 - t * 1.7) * 0.4) * 0.012 * uMotion;
    float d = sdSuperellipse(p, max(2.0, uPowerFactor), 1.0 + wobble);
    // AA over ~1 screen px
    float px = 2.0 / max(1.0, min(uQuadSize.x, uQuadSize.y));
    float mask = 1.0 - smoothstep(-px, px, d);
    if (mask <= 0.0) discard;
    float dist = max(-d, 0.0);

    vec2 sampleP = p * spow(f(dist), uFPower);
    // quad-local [-1,1] (y down) -> screen NDC (y up)
    vec2 coord = (vMidPointNDC + vec2(sampleP.x, -sampleP.y) * uHalfNdc) * 0.5 + 0.5;

    // moving liquid: flow displacement (px, stronger through the body than at the rim)
    vec2 qp = v_uv * uQuadSize * 0.045;
    vec2 fl = flow(qp, t);
    float body = smoothstep(0.0, 0.25, dist);
    vec2 dispPx = fl * uMotion * (3.0 + 3.0 * body);
    vec2 toUV = vec2(1.0 / uScreen.x, -1.0 / uScreen.y);
    vec2 disp = dispPx * toUV;
    // chromatic split along the flow, like light through moving water
    vec2 ca = disp * 0.35;
    vec2 c0 = clamp(coord + disp, vec2(0.001), vec2(0.999));
    vec3 color = vec3(texture(uBlurTex, clamp(c0 + ca, vec2(0.001), vec2(0.999))).r,
                      texture(uBlurTex, c0).g,
                      texture(uBlurTex, clamp(c0 - ca, vec2(0.001), vec2(0.999))).b) + vec3(rand(gl_FragCoord.xy * 1e-3) - 0.5) * uNoise;
    float glow = sin(atan(p.y, p.x) - 0.5);
    float mul = glow * uGlowWeight * smoothstep(uGlowEdge0, uGlowEdge1, dist) + 1.0 + uGlowBias;
    color *= mul;
    // glass colour: absorption tint through the body (multiplicative, like coloured glass),
    // a slight wash of the tint, and a thicker blue-green edge where the light path is longest
    vec3 tint = uTintColor;
    float edgeT = 1.0 - smoothstep(0.0, 0.35, dist);              // 1 at the rim
    vec3 absorb = mix(vec3(1.0), tint, clamp(uTintStrength * (1.4 + edgeT), 0.0, 1.0));
    color *= absorb;
    color = mix(color, tint, uTintStrength * 0.35);
    vec3 edgeColor = mix(tint, vec3(0.55, 0.85, 0.95), 0.5);       // glassy cyan at the edges
    color = mix(color, color * edgeColor * 1.15, edgeT * uTintStrength * 1.6);
    color += edgeColor * pow(edgeT, 2.0) * 0.12 * uTintStrength * 4.0;
    // caustics: bright moving filaments where the liquid focuses light
    vec2 fl2 = flow(qp * 1.7 + 3.1, t * 1.2);
    float caustic = pow(1.0 - abs(sin((fl2.x + fl2.y) * 3.14159)), 10.0);
    color += vec3(caustic * 0.10 * uMotion * body);
    // specular sheen that slides with the surface normal of the flow
    float spec = pow(max(dot(normalize(vec3(-fl, 2.5)), normalize(vec3(-0.4, 0.6, 1.0))), 0.0), 40.0);
    color += vec3(spec * 0.25 * uMotion * uHighlight);
    float rim = 1.0 - smoothstep(0.0, px * 3.0, dist);
    color += mix(vec3(1.0), edgeColor, 0.35) * rim * 0.35 * uHighlight;
    FragColor = vec4(clamp(color, 0.0, 1.0), mask * uAlpha);
}
