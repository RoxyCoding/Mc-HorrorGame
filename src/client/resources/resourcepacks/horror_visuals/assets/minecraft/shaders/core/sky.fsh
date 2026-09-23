#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = apply_fog(ColorModulator, sphericalVertexDistance, cylindricalVertexDistance, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);
    // Overcast: the sky dome takes the same haze colour as distant terrain, so the horizon and
    // the sky above it read as one cloud layer instead of a blue dome over a white wall.
    fragColor.rgb = mix(fragColor.rgb, horror_haze_color(FogColor.rgb), 0.85);
}
