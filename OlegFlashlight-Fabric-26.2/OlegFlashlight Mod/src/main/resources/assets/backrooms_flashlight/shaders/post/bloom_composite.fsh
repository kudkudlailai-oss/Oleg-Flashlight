#version 330

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform CompositeConfig {
    vec4 Params; // x = intensity
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 scene = texture(InSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    fragColor = vec4(scene + bloom * Params.x, 1.0);
}
