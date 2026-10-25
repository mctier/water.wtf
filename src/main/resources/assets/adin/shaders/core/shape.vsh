#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec4 vertexColor;
out vec2 localPosition;
flat out vec2 shapeSize;
flat out float borderWidth;

const float FIXED_POINT = 8.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    localPosition = UV0;
    shapeSize = vec2(UV2) / FIXED_POINT;
    borderWidth = float(UV1.x) / FIXED_POINT;
}
