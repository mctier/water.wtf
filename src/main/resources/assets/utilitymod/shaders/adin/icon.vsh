#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec2 texCoord;
out vec4 vertexColor;
flat out vec3 backdrop;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color * ColorModulator;
    backdrop = vec3(UV1.x, UV1.y, UV2.x) / 255.0;
}
