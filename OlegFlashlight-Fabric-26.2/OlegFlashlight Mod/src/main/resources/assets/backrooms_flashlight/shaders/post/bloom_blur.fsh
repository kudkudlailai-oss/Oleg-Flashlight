#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec4 Params; // xy = direction, z = step size in texels
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 step = Params.xy * Params.z / InSize;
    // 9-tap gaussian using bilinear taps
    vec3 sum = texture(InSampler, texCoord).rgb * 0.2270270;
    sum += texture(InSampler, texCoord + step * 1.3846154).rgb * 0.3162162;
    sum += texture(InSampler, texCoord - step * 1.3846154).rgb * 0.3162162;
    sum += texture(InSampler, texCoord + step * 3.2307692).rgb * 0.0702703;
    sum += texture(InSampler, texCoord - step * 3.2307692).rgb * 0.0702703;
    fragColor = vec4(sum, 1.0);
}
