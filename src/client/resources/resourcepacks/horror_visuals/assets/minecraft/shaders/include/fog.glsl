#ifndef MINECRAFT_FOG_GLSL
#define MINECRAFT_FOG_GLSL

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }

    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd) {
    return max(linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd), linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));
}

// Horror haze: a close, desaturated atmosphere layered under vanilla's distance fog.
// Shared by terrain, entities, particles, clouds and the sky, so everything fades together.
const float HORROR_HAZE_DISTANCE = 44.0; // metres at which the haze covers ~63%
const float HORROR_HAZE_MAX = 0.94;

vec3 horror_haze_color(vec3 fogColor) {
    float luma = dot(fogColor, vec3(0.299, 0.587, 0.114));
    // Keep a little of the biome/time tint (lava stays warm, water stays blue), lose the rest.
    return mix(vec3(luma), fogColor, 0.3) * vec3(0.8, 0.84, 0.88);
}

float horror_haze_value(float sphericalVertexDistance) {
    return HORROR_HAZE_MAX * (1.0 - exp(-pow(sphericalVertexDistance / HORROR_HAZE_DISTANCE, 1.5)));
}

vec4 apply_fog(vec4 inColor, float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd, vec4 fogColor) {
    float fogValue = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, environmentalStart, environmantalEnd, renderDistanceStart, renderDistanceEnd);
    vec3 haze = horror_haze_color(fogColor.rgb);
    vec3 color = mix(inColor.rgb, haze, horror_haze_value(sphericalVertexDistance) * fogColor.a);
    return vec4(mix(color, haze, fogValue * fogColor.a), inColor.a);
}

float fog_spherical_distance(vec3 pos) {
    return length(pos);
}

float fog_cylindrical_distance(vec3 pos) {
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}

#endif
