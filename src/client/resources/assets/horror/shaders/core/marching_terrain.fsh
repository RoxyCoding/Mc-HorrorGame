#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec3 vertexNormal;

layout(location = 0) out vec4 fragColor;

void main() {
#ifdef WIREFRAME
    // Triangle edges drawn over the shaded surface.
    vec3 color = vec3(0.04);
#else
    // Vanilla's fixed face shading (up 1.0, down 0.5, north/south 0.8, east/west 0.6), blended by the
    // smooth normal per pixel instead of being fixed per block face.
    vec3 n = normalize(vertexNormal);
    vec3 weight = n * n;
    vec3 color = vertexColor.rgb * (weight.x * 0.6 + weight.z * 0.8 + weight.y * (n.y > 0.0 ? 1.0 : 0.5));
#endif
    // ColorModulator.a is the section's fade-in, as for vanilla chunks.
    color = mix(FogColor.rgb, color, ColorModulator.a);
    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
