#version 330

uniform sampler2D InSampler;
uniform sampler2D PositionSampler; // view-space position of each terrain pixel (alpha 0 = not recorded)
uniform sampler2D AlbedoSampler;   // unlit colour of the terrain, written by the mod's terrain shader (alpha 0 = not recorded)

layout(std140) uniform SpotConfig {
    vec4 Params;   // x = tan(fov/2) (70 degrees = 0.7002), y = strength (multiplier), z = range in blocks
    vec4 Cone;     // x = cos(inner half angle), y = cos(outer half angle)
    vec4 Origin;   // lamp position in view space: right, up, forward (blocks)
    vec4 Dir;      // where the lamp points in view space (it lags behind the camera; the mod picks the matching file)
    vec4 Haze;     // x = haze strength
    vec4 Core;     // narrow bright core: x = cos(inner), y = cos(outer), z = core strength, w = weight of the wide cone
};

in vec2 texCoord;
out vec4 fragColor;

// Spotlight from the depth buffer (first person). Depth is reverse-Z: linear distance ~ 0.05 / depth.
// Brightness the lit area is brought up to (0..1) and the most the picture may be multiplied by. Tune these.
const float EXPOSURE_TARGET = 0.38;
const float MAX_GAIN = 60.0;
// How strongly the beam lights surfaces whose real (unlit) colour is known.
const float BEAM_GAIN = 1.5;

float coneAt(vec3 pos, vec3 dir) {
    vec3 V = pos - Origin.xyz;
    float dist = length(V);
    float c = smoothstep(Cone.y, Cone.x, dot(V / max(dist, 1.0e-4), dir));
    float f = clamp(1.0 - dist / Params.z, 0.0, 1.0);
    return c * pow(f, 1.2);
}

// the narrow bright core of the Found Footage style (strength 0 in the other style)
float coreAt(vec3 pos, vec3 dir) {
    if (Core.z <= 0.0) return 0.0;
    vec3 V = pos - Origin.xyz;
    float dist = length(V);
    float c = smoothstep(Core.y, Core.x, dot(V / max(dist, 1.0e-4), dir));
    float f = clamp(1.0 - dist / Params.z, 0.0, 1.0);
    return c * pow(f, 1.2);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec4 alb = texture(AlbedoSampler, texCoord);
    vec4 pv = texture(PositionSampler, texCoord);
    bool terrain = alb.a > 0.5 && pv.a > 0.5;

    vec2 size = vec2(textureSize(InSampler, 0));
    float aspect = size.x / size.y;
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 ray = vec3(ndc.x * aspect * Params.x, ndc.y * Params.x, 1.0);   // view ray, z = 1 (forward)
    vec3 dir = normalize(Dir.xyz);

    // The terrain shader wrote where each pixel is (Minecraft view space looks down -z; this shader uses +z forward).
    vec3 P = vec3(pv.x, pv.y, -pv.z);
    float zEnd = terrain ? min(P.z, Params.z) : Params.z;

    // light haze: march along the view ray and add up the light that is in the air
    float haze = 0.0;
    const int STEPS = 16;
    float jitter = hash(gl_FragCoord.xy);
    float ds = max(zEnd, 0.0) / float(STEPS);
    for (int i = 0; i < STEPS; i++) {
        float s = (float(i) + jitter) * ds;
        haze += coneAt(ray * s, dir) * ds;
    }
    vec3 warm = vec3(1.0, 0.92, 0.72);
    vec3 hazeCol = warm * haze * Haze.x;

    if (!terrain) { fragColor = vec4(scene.rgb + hazeCol, 1.0); return; }

    vec3 L = normalize(P - Origin.xyz);
    float k = coneAt(P, dir) * Core.w + coreAt(P, dir) * Core.z;
    // surfaces facing the lamp get more light
    vec3 N = normalize(cross(dFdx(P), dFdy(P)));
    k *= mix(0.35, 1.0, clamp(abs(dot(N, L)), 0.0, 1.0));

    // light the real (unlit) texture colour: works even when the game draws the scene pure black
    vec3 lit = scene.rgb + alb.rgb * mix(vec3(1.0), warm, 0.6) * (k * BEAM_GAIN * Params.y);
    fragColor = vec4(lit + hazeCol, 1.0);
}
