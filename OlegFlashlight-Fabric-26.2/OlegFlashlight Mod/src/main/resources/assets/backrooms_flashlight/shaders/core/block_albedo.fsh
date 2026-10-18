#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPosition;
in vec4 unlitColor;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 albedoOut;
layout(location = 2) out vec4 positionOut;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    vec4 bfUnlit = texture(Sampler0, texCoord0) * unlitColor * ColorModulator;
    albedoOut = vec4(bfUnlit.rgb, 1.0);
    positionOut = vec4(viewPosition, 1.0);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
