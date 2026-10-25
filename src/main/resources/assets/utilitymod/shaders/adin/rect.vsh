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
flat out vec2 rectSize;
flat out float cornerRadius;
flat out int shape;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    localPosition = UV0;
    rectSize = vec2(UV2);
    cornerRadius = float(UV1.x);
    shape = UV1.y;
}
