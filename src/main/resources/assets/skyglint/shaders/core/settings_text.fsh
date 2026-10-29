#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
flat in vec4 glyphBounds;
out vec4 fragColor;

float coverageAt(vec2 uv) {
    // Atlas packing only reserves a narrow border. A minifying filter must never
    // pick up another character outside this glyph's own original rectangle.
    if (any(lessThan(uv, glyphBounds.xy)) || any(greaterThan(uv, glyphBounds.zw))) return 0.0;
    return texture(Sampler0, uv).r;
}

void main() {
    vec2 dx = dFdx(texCoord0);
    vec2 dy = dFdy(texCoord0);
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    // Approximately one bilinear sample per source texel. At GUI scale 1 the
    // 96px atlas can shrink by ten times: a single bilinear lookup aliases it.
    int nx = int(clamp(ceil(length(dx * atlasSize)), 1.0, 12.0));
    int ny = int(clamp(ceil(length(dy * atlasSize)), 1.0, 12.0));
    float coverage = 0.0;
    for (int y = 0; y < 12; ++y) {
        if (y >= ny) break;
        for (int x = 0; x < 12; ++x) {
            if (x >= nx) break;
            vec2 offset = vec2((float(x) + 0.5) / float(nx), (float(y) + 0.5) / float(ny)) - 0.5;
            coverage += coverageAt(texCoord0 + dx * offset.x + dy * offset.y);
        }
    }
    coverage /= float(nx * ny);
    vec4 tint = vertexColor * ColorModulator;
    // Keep low edge coverage, including during fades. Vanilla's 0.1 alpha cutoff
    // removes these pixels and makes small letters and page motion sparkle.
    fragColor = vec4(tint.rgb, tint.a * coverage);
}
