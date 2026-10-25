#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in float LineWidth;

out vec4 vertexColor;
out vec2 localPosition;
flat out vec2 rowSize;
flat out float cornerRadius;
flat out int edges;
flat out vec2 neighbours;

const float PACK = 4096.0;
const float BIAS = 2048.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    localPosition = UV0;
    rowSize = vec2(UV2);
    cornerRadius = float(UV1.x);
    edges = UV1.y;
    neighbours = vec2(floor(LineWidth / PACK), mod(LineWidth, PACK)) - BIAS;
}
