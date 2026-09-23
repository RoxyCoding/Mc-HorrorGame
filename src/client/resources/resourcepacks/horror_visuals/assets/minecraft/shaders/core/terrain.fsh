#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 worldPos;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// ---- Horror material layer ---------------------------------------------------------------
float horror_hash(ivec3 p) {
    uvec3 q = uvec3(p) * uvec3(1597334673u, 3812015801u, 2798796415u);
    uint h = (q.x ^ q.y ^ q.z) * 1597334673u;
    h ^= h >> 16u;
    return float(h) * (1.0 / 4294967296.0);
}

// Value noise on a lattice that repeats every `period` cells per axis (the 4096-block world wrap
// times that axis' frequency), so the wrapped world position never shows a seam. Returns [-1, 1].
float horror_noise(vec3 p, ivec3 period) {
    vec3 i = floor(p);
    vec3 f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    ivec3 c = ivec3(i);
    ivec3 m = period - 1;
    float n000 = horror_hash((c) & m);
    float n100 = horror_hash((c + ivec3(1, 0, 0)) & m);
    float n010 = horror_hash((c + ivec3(0, 1, 0)) & m);
    float n110 = horror_hash((c + ivec3(1, 1, 0)) & m);
    float n001 = horror_hash((c + ivec3(0, 0, 1)) & m);
    float n101 = horror_hash((c + ivec3(1, 0, 1)) & m);
    float n011 = horror_hash((c + ivec3(0, 1, 1)) & m);
    float n111 = horror_hash((c + ivec3(1, 1, 1)) & m);
    float v = mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
                  mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
    return v * 2.0 - 1.0;
}

// Texture sampling stays vanilla (sharp); only a world-space colour variation is layered on top.
vec4 horror_material(vec4 color, float distance) {
    // Break the 1 m repetition with world-space colour variation: broad patches and fine grain.
    // Keyed by world position only (not by facing or light), so neighbouring blocks match and
    // lighting stays with the shader mod.
    // Fine layers are skipped where fog and distance would hide them anyway.
    float detail = 1.0 - smoothstep(24.0, 40.0, distance);
    float broad = horror_noise(worldPos * 0.25, ivec3(1024));
    float variation = 1.0 + 0.11 * broad;
    if (detail > 0.0) {
        variation += detail * (0.06 * horror_noise(worldPos, ivec3(4096)) + 0.06 * horror_noise(worldPos * 8.0, ivec3(32768)));
    }
    color.rgb *= mix(1.0, variation, color.a);
    return color;
}
// -------------------------------------------------------------------------------------------

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 vanilla = UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize);
    vec4 color = horror_material(vanilla, sphericalVertexDistance) * vertexColor;
    // Translucent surfaces (water, stained glass) carry saturated biome tints: mute them.
    if (color.a < 0.99) {
        color.rgb = mix(vec3(dot(color.rgb, vec3(0.299, 0.587, 0.114))), color.rgb, 0.45);
    }
    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
