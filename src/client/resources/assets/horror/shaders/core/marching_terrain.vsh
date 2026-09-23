#version 330
#extension GL_ARB_separate_shader_objects : require

// Marching cubes terrain surface. Vertices are section-local block coordinates.

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in ivec2 UV2;
layout(location = 3) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec3 vertexNormal;

void main() {
    // ModelOffset: section origin minus the camera block, whole blocks. CameraOffset: the camera's
    // fraction. The same split as vanilla terrain, so neighbouring sections line up exactly.
    vec3 pos = Position + ModelOffset + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    vertexNormal = Normal;
}
