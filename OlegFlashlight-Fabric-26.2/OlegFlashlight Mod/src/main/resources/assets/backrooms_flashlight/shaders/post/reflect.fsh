#version 330

uniform sampler2D InSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D PositionSampler; // view-space position of each recorded pixel (alpha 0 = not recorded, 1 = solid, 2 = water/translucent)

layout(std140) uniform ReflectConfig {
    vec4 Up;     // world "up" in view space (x right, y up, z forward) = (0, cos pitch, -sin pitch)
    vec4 Params; // x = tan(fov/2), y = floor reflectivity (0 = off), z = max ray length in blocks, w = water reflectivity (0 = off)
    vec4 Cam;    // x, y = camera world x, z; z = camera yaw (radians); w = time in seconds
};

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

// water height at a world position (x, z): a few moving ripples
float waves(vec2 w, float t) {
    return vnoise(w * 1.7 + vec2(t * 0.45, t * 0.30)) * 0.6
         + vnoise(w * 3.9 - vec2(t * 0.60, -t * 0.50)) * 0.3
         + vnoise(w * 7.1 + vec2(-t * 0.8, t * 0.9)) * 0.05;
}

// Screen-space reflection on upward-facing surfaces (floors, water): march a ray along the mirrored view direction
// and look the colour up where it meets recorded geometry.
void main() {
    vec4 scene = texture(InSampler, texCoord);
    bool floorOn = Params.y > 0.0;
    bool waterOn = Params.w > 0.0;
    if (!floorOn && !waterOn) { fragColor = vec4(scene.rgb, 1.0); return; }
    vec4 pv = texture(PositionSampler, texCoord);
    if (pv.a < 0.5) { fragColor = vec4(scene.rgb, 1.0); return; }
    bool water = pv.a > 1.5;
    if (water ? !waterOn : !floorOn) { fragColor = vec4(scene.rgb, 1.0); return; }

    vec3 P = vec3(pv.x, pv.y, -pv.z);
    vec3 N = normalize(cross(dFdx(P), dFdy(P)));
    if (dot(N, P) > 0.0) N = -N;                       // face the camera
    float up = dot(N, normalize(Up.xyz));
    if (up < 0.92) { fragColor = vec4(scene.rgb, 1.0); return; }   // only flat, upward-facing surfaces
    float floorMask = 1.0;
    if (!water) {
        // grass, leaves, dirt and other coloured / rough ground do not mirror: only grey, smooth-looking surfaces (stone, snow, concrete)
        vec3 al = texture(AlbedoSampler, texCoord).rgb;
        float mx = max(max(al.r, al.g), al.b);
        float sat = (mx - min(min(al.r, al.g), al.b)) / max(mx, 0.001);
        floorMask = 1.0 - smoothstep(0.12, 0.30, sat);
        if (floorMask <= 0.0) { fragColor = vec4(scene.rgb, 1.0); return; }
    }

    vec3 V = normalize(P);
    if (water) {
        // ripples: perturb the normal with the slope of a moving height field in world space
        float cp = Up.y, sp = -Up.z;                    // cos / sin of the camera pitch
        float cy = cos(Cam.z), sy = sin(Cam.z);
        vec2 fh = vec2(-sy, cy);                        // horizontal forward (world x, z)
        vec2 rh = vec2(-cy, -sy);                       // horizontal right
        vec2 off = rh * P.x + fh * (P.z * cp + P.y * sp);
        vec2 w = Cam.xy + off;
        float e = 0.06;
        float h0 = waves(w, Cam.w);
        float gx = (waves(w + vec2(e, 0.0), Cam.w) - h0) / e;
        float gz = (waves(w + vec2(0.0, e), Cam.w) - h0) / e;
        vec2 g = vec2(gx, gz) * 0.075 / (1.0 + 0.12 * P.z);   // wave steepness, calmer with distance (no grain far away)
        float vx = dot(g, rh);
        float fz = dot(g, fh);
        N = normalize(N - vec3(vx, fz * sp, fz * cp));
    }
    vec3 R = reflect(V, N);
    if (R.z < 0.02) { fragColor = vec4(scene.rgb, 1.0); return; }  // reflects back at the camera: not on screen

    vec2 size = vec2(textureSize(InSampler, 0));
    float aspect = size.x / size.y;
    float T = Params.x;

    float stepLen = 0.14;
    float travelled = 0.0;
    vec3 pos = P + N * 0.03;
    vec3 prev = pos;
    vec3 hit = vec3(0.0);
    float found = 0.0;
    bool didHit = false;
    for (int i = 0; i < 64; i++) {
        prev = pos;
        pos += R * stepLen;
        travelled += stepLen;
        stepLen *= 1.06;
        if (pos.z <= 0.05 || travelled > Params.z) break;
        vec2 uv = vec2(pos.x / (pos.z * aspect * T), pos.y / (pos.z * T)) * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;
        vec4 sp2 = texture(PositionSampler, uv);
        if (sp2.a < 0.5) continue;
        float diff = pos.z + sp2.z;                    // > 0: the ray is behind the surface seen at this pixel (sp2.z is -depth)
        if (diff > 0.0 && diff < max(0.4, 0.14 * pos.z)) {
            // refine the hit point (removes the stepping pattern)
            vec3 lo = prev;
            vec3 hi = pos;
            for (int j = 0; j < 5; j++) {
                vec3 mid = 0.5 * (lo + hi);
                vec2 muv = vec2(mid.x / (mid.z * aspect * T), mid.y / (mid.z * T)) * 0.5 + 0.5;
                vec4 mp = texture(PositionSampler, muv);
                if (mp.a > 0.5 && mid.z + mp.z > 0.0) hi = mid; else lo = mid;
            }
            uv = vec2(hi.x / (hi.z * aspect * T), hi.y / (hi.z * T)) * 0.5 + 0.5;
            vec2 edge = smoothstep(0.0, 0.1, uv) * (1.0 - smoothstep(0.9, 1.0, uv));
            found = edge.x * edge.y * (1.0 - 0.6 * travelled / Params.z);
            hit = texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
            didHit = true;
            break;
        }
    }
    // stronger at grazing angles (fresnel); water is much more mirror-like than a floor
    float cosT = clamp(dot(-V, N), 0.0, 1.0);
    float fres = pow(1.0 - cosT, 3.0);
    float k;
    if (water) {
        // where the ray hits nothing (or leaves the screen) the water mirrors the sky: look up the sky pixel in the mirrored direction
        vec3 far = P + R * 400.0;
        vec2 suv = vec2(far.x / (max(far.z, 0.05) * aspect * T), far.y / (max(far.z, 0.05) * T)) * 0.5 + 0.5;
        suv = clamp(suv, vec2(0.02, 0.02), vec2(0.98, 0.98));
        vec4 sk = texture(PositionSampler, suv);
        if (sk.a > 0.5) suv.y = 0.985;                   // that spot is ground: use the sky at the top of the screen instead
        vec3 sky = texture(InSampler, vec2(suv.x, min(suv.y, 0.985))).rgb;
        vec3 col = mix(sky * 0.6, hit, didHit ? found : 0.0) * vec3(0.82, 0.9, 1.0);   // a bit darker and bluer than the sky: water is not a perfect mirror
        k = clamp((0.30 + 0.50 * fres) * Params.w, 0.0, 0.8);
        fragColor = vec4(mix(scene.rgb, col, k), 1.0);
        return;
    }
    k = clamp(found * (0.2 + 0.8 * fres) * Params.y * floorMask, 0.0, 1.0);
    fragColor = vec4(mix(scene.rgb, hit, k), 1.0);
}
