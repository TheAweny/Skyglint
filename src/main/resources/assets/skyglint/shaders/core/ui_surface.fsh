#version 330

in vec2 localUv;
in vec4 shapeParameters;
flat in float extraParameter;
out vec4 fragColor;

void main() {
    vec2 size = 1.0 / max(vec2(length(dFdx(localUv)), length(dFdy(localUv))), vec2(0.00001));
    float radius = min(size.x, size.y) * extraParameter;
    vec2 q = abs(localUv - 0.5) * size - (size * 0.5 - vec2(radius));
    float distanceToEdge = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = 1.0 - smoothstep(-0.7, 0.7, distanceToEdge);
    if (coverage <= 0.0) discard;
    fragColor = vec4(shapeParameters.rgb, shapeParameters.a * coverage);
}
