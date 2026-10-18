#version 330

uniform sampler2D InSampler;

layout(std140) uniform BrightConfig {
    vec4 Params; // x = threshold (0..1)
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float m = max(max(c.r, c.g), c.b);
    // The picture is 8-bit, so a torch and a bright sky both top out near 1.0. To keep daylight (bright sky, snow) from
    // glowing everywhere, the effect fades out when the whole picture is bright, and only shows in dark scenes.
    float avg = 0.0;
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            vec3 s = texture(InSampler, (vec2(x, y) + 0.5) / 4.0).rgb;
            avg += dot(s, vec3(0.299, 0.587, 0.114));
        }
    }
    avg /= 16.0;
    float gate = 1.0 - smoothstep(0.14, 0.30, avg);
    float w = smoothstep(Params.x, Params.x + 0.25, m) * gate;
    fragColor = vec4(c * w, 1.0);
}
