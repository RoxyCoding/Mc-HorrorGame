#ifndef HORROR_DEPTH_GLSL
#define HORROR_DEPTH_GLSL

// Minecraft 26.3 renders with reversed-Z: 1 at the 0.05 m near plane, 0 at the far plane/sky.
const float HORROR_NEAR = 0.05;
const float HORROR_FAR = 1024.0;

float horror_linear_depth(float depth) {
    return HORROR_NEAR * HORROR_FAR / (depth * (HORROR_FAR - HORROR_NEAR) + HORROR_NEAR);
}

bool horror_is_sky(float depth) {
    return depth <= 0.0;
}

#endif
