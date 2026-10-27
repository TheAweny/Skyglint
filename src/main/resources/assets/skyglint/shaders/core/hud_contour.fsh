#version 330

in vec2 localUv;
in vec4 shapeParameters;
flat in float extraParameter;
out vec4 fragColor;

const float PI = 3.141592653589793;

void main() {
    vec2 size = 1.0 / max(vec2(length(dFdx(localUv)), length(dFdy(localUv))), vec2(0.00001));
    vec2 p = (localUv - 0.5) * size;
    // Inset keeps the whole stroke within the submitted quad. These are physical framebuffer pixels.
    float radius = max(min(size.x, size.y) * 0.5 - 1.0, 0.5);
    float straight = max(size.x - min(size.x, size.y), 0.0);
    float halfStraight = straight * 0.5;
    float distanceToLine = length(vec2(p.x - clamp(p.x, -halfStraight, halfStraight), p.y)) - radius;
    float stroke = 1.0 - smoothstep(0.45, 1.15, abs(distanceToLine));
    if (stroke <= 0.0) discard;

    // Arc distance follows the actual stadium (straight edges + semicircles), including while it morphs.
    float arc;
    if (p.x > halfStraight) {
        float angle = atan(p.y, p.x - halfStraight);
        arc = straight + (angle + PI * 0.5) * radius;
    } else if (p.x < -halfStraight) {
        float angle = atan(p.y, p.x + halfStraight);
        if (angle < 0.0) angle += PI * 2.0;
        arc = straight * 2.0 + PI * radius + (angle - PI * 0.5) * radius;
    } else {
        arc = p.y < 0.0 ? p.x + halfStraight : straight + PI * radius + halfStraight - p.x;
    }
    float perimeter = straight * 2.0 + PI * 2.0 * radius;
    float startAtBottom = straight * 1.5 + PI * radius;
    arc = mod(arc - startAtBottom + perimeter, perimeter);
    float gap = max(4.0, perimeter * 0.065);
    float gapCoverage = smoothstep(gap * 0.5, gap * 0.5 + 0.9, arc) *
        (1.0 - smoothstep(perimeter - gap * 0.5 - 0.9, perimeter - gap * 0.5, arc));

    float encoded = extraParameter;
    bool known = encoded >= 0.25;
    float fraction = clamp((encoded - 0.25) * 2.0, 0.0, 1.0);
    float along = clamp((arc - gap * 0.5) / max(perimeter - gap, 0.001), 0.0, 1.0);
    float progress = known && fraction > 0.0 ? 1.0 - smoothstep(fraction - 0.004, fraction + 0.004, along) : 0.0;
    float dash = known ? 1.0 : 1.0 - smoothstep(3.0, 3.8, mod(arc, 7.0));
    float alpha = mix(0.20, 0.97, progress) * stroke * gapCoverage * dash * shapeParameters.a;
    fragColor = vec4(shapeParameters.rgb, alpha);
}
