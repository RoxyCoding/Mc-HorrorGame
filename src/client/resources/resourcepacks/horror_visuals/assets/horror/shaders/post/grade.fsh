#version 330
#extension GL_ARB_separate_shader_objects : require

#include <horror:depth.glsl>

// Final image: overcast sky, colour grading and grain. Lighting, shadows and vignetting are left
// to a separate shader mod. Pixels are read 1:1 so the image stays as sharp as vanilla.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;

    float depth = texture(DepthSampler, texCoord).r;
    if (horror_is_sky(depth)) {
        // Sun, moon, stars and the sunrise band are drawn without fog: sink them into the overcast.
        float l = dot(color, LUMA);
        color = mix(vec3(l), color, 0.3);
    }

    // Grade: desaturated, cool shadows, slightly warm highlights, filmic contrast.
    float luma = dot(color, LUMA);
    color = mix(vec3(luma), color, 0.6);
    color *= mix(vec3(0.9, 0.98, 1.04), vec3(1.03, 1.0, 0.95), smoothstep(0.15, 0.8, luma));
    vec3 curved = color * color * (3.0 - 2.0 * color);
    color = mix(color, curved, 0.35);
    color = max(color - 0.012, 0.0) * 1.03;

    // Fine grain, strongest in the shadows; also hides banding in dark gradients.
    float grain = hash12(gl_FragCoord.xy) - 0.5;
    color += grain * 0.035 * (1.0 - 0.7 * dot(color, LUMA));

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
