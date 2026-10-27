#version 330

uniform sampler2D Sampler0;

in vec2 localUv;
in vec4 shapeParameters;
out vec4 fragColor;

void main() {
    // Dimensions are measured in physical framebuffer pixels, so GUI scale cannot enlarge blur.
    vec2 size = 1.0 / max(vec2(length(dFdx(localUv)), length(dFdy(localUv))), vec2(0.00001));
    float radius = min(size.x, size.y) * shapeParameters.r;
    vec2 q = abs(localUv - 0.5) * size - (size * 0.5 - vec2(radius));
    float distanceToEdge = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = 1.0 - smoothstep(-0.7, 0.7, distanceToEdge);
    if (coverage <= 0.0) discard;

    // The copied render target uses the same backend coordinate system as gl_FragCoord.
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy * texel;
    const float weights[7] = float[7](1.0, 6.0, 15.0, 20.0, 15.0, 6.0, 1.0);
    vec3 blurred = vec3(0.0);
    // Only capsule pixels run this kernel; the full-screen snapshot is a GPU copy, never a full-screen blur.
    for (int row = -3; row <= 3; row++) {
        for (int col = -3; col <= 3; col++) {
            blurred += texture(Sampler0, uv + vec2(float(col), float(row)) * texel * 3.5).rgb *
                weights[row + 3] * weights[col + 3];
        }
    }
    // One soft physical-pixel edge replaces staircase CPU row outlines. Tint belongs to the UI layer.
    float edge = 1.0 - smoothstep(0.35, 1.0, abs(distanceToEdge + 0.7));
    vec3 surface = mix(blurred / 4096.0, vec3(0.88, 0.94, 1.0), edge * 0.18);
    fragColor = vec4(surface, coverage * shapeParameters.a);
}
