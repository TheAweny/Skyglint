#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 localUv;
out vec4 shapeParameters;
flat out float extraParameter;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Top/bottom occupy separate UV Y bands. Their fractional payload stays continuous,
    // while recovered 0/1 local coordinates keep blur and contour derivatives unchanged.
    float localY = floor(UV0.y * 0.5);
    extraParameter = UV0.y - localY * 2.0;
    localUv = vec2(UV0.x, localY);
    shapeParameters = Color;
}
