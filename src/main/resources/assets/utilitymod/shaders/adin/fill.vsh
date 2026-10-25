#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 relativePosition;
out vec3 worldNormal;
out vec3 viewPosition;
out vec3 viewNormal;
flat out ivec2 packedA;
flat out ivec2 packedB;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vertexColor = Color;
    texCoord0 = UV0;
    relativePosition = Position;
    worldNormal = Normal;
    viewPosition = view.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    packedA = UV1;
    packedB = UV2;
}
