#version 330 core
/*
 * "Lens" liquid glass (own implementation): sharp backdrop, refraction concentrated in a
 * lens band along the edge (direction = rounded-rect SDF normal), chromatic split,
 * slow ripple, tinted rim, corner glint and a diagonal sheen. No frosted blur.
 */
uniform sampler2D uBlurTex;
uniform vec2 uQuadSize;     // GUI px
uniform vec2 uScreen;       // GUI px
uniform float uRadius;
uniform float uRefractPx;   // GUI px
uniform float uChromaPx;    // GUI px
uniform float uRipple;
uniform float uHighlight;
uniform vec3 uTintColor;
uniform float uTintStrength;
uniform float uTime;
uniform float uAlpha;

in vec2 v_uv;
in vec2 vMidPointNDC;
in vec2 vLocalOffsetNDC;
layout (location = 0) out vec4 FragColor;

float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }

void main() {
    vec2 half_ = uQuadSize * 0.5;
    vec2 local = (v_uv - 0.5) * uQuadSize;           // GUI px, y down
    float r = min(uRadius, min(half_.x, half_.y));
    float sd = sdRoundBox(local, half_, r);
    float mask = 1.0 - smoothstep(-0.75, 0.75, sd);
    if (mask <= 0.001) discard;

    // outward normal from the SDF gradient (analytic central difference)
    vec2 e = vec2(0.5, 0.0);
    vec2 g = vec2(sdRoundBox(local + e.xy, half_, r) - sdRoundBox(local - e.xy, half_, r),
                  sdRoundBox(local + e.yx, half_, r) - sdRoundBox(local - e.yx, half_, r));
    vec2 n = length(g) > 1e-5 ? normalize(g) : vec2(0.0, -1.0);   // GUI space (y down)
    vec2 tangent = vec2(-n.y, n.x);

    float inside = max(-sd, 0.0);
    float lensWidth = max(min(uQuadSize.x, uQuadSize.y) * 0.36, 1.0);
    float lens = pow(clamp(1.0 - smoothstep(0.0, lensWidth, inside), 0.0, 1.0), 1.35);

    float ripple = sin(local.y * 0.09 + uTime * 1.65) * cos(local.x * 0.075 - uTime * 1.18) * uRipple;
    vec2 bendPx = (n * (0.92 + ripple * 0.18) + tangent * ripple * 0.22) * uRefractPx * lens;

    // sample position in GL-oriented screen UV (GUI y down -> GL y up)
    vec2 baseUV = (vMidPointNDC + vLocalOffsetNDC) * 0.5 + 0.5;
    vec2 toUV = vec2(1.0 / uScreen.x, -1.0 / uScreen.y);
    vec2 uv = clamp(baseUV - bendPx * toUV, 0.001, 0.999);
    vec2 chroma = n * uChromaPx * lens * toUV;
    vec3 col = vec3(texture(uBlurTex, clamp(uv - chroma, 0.001, 0.999)).r,
                    texture(uBlurTex, uv).g,
                    texture(uBlurTex, clamp(uv + chroma, 0.001, 0.999)).b);

    col = mix(col, uTintColor, clamp(uTintStrength, 0.0, 1.0));
    float rim = 1.0 - smoothstep(0.0, 1.6, inside);
    float innerGlow = 1.0 - smoothstep(0.0, max(9.0, lensWidth * 0.17), inside);
    float facing = max(dot(vec2(n.x, -n.y), normalize(vec2(-0.45, 0.9))), 0.0);
    float glint = pow(facing, 5.0) * innerGlow * uHighlight;
    vec2 nl = v_uv;
    float sheen = smoothstep(0.78, 1.0, 1.0 - abs(nl.x - nl.y - 0.10))
                * smoothstep(0.0, 0.18, nl.x) * smoothstep(1.0, 0.72, nl.x) * 0.11 * uHighlight;
    col += vec3(glint * 0.42) + uTintColor * rim * 0.30 + vec3(rim * 0.10 + innerGlow * 0.035 + sheen);

    float alpha = mask * clamp(uAlpha + rim * 0.2 + glint * 0.13, 0.0, 1.0) * uAlpha;
    FragColor = vec4(clamp(col, 0.0, 1.0), alpha);
}
