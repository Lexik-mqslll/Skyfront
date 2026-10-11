#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float ambientTemp;
uniform float Time;
uniform float UseMask;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

const float SENSOR_BLUR = 2.1;

const float BLOOM_RADIUS = 7.0;

float temperatureOf(vec3 rgb, float mask, out float hotness) {
    float luma = dot(rgb, vec3(0.299, 0.587, 0.114));

    float skyness = smoothstep(0.30, 0.62, luma) * smoothstep(0.0, 0.10, rgb.b - rgb.r);

    float grey = 1.0 - smoothstep(0.004, 0.012, abs(rgb.r - rgb.g));
    float signature = 1.0 - smoothstep(0.012, 0.030, abs((rgb.r - rgb.b) - rgb.r * 0.10));
    float bodyish = grey * signature * smoothstep(0.30, 0.45, luma);

    float fire = smoothstep(0.40, 0.58, rgb.r - rgb.b)
               * smoothstep(0.08, 0.16, rgb.g - rgb.b)
               * (1.0 - smoothstep(0.44, 0.58, rgb.g - rgb.b))
               * smoothstep(0.34, 0.48, luma);

    float masked = UseMask * step(0.02, mask);
    bodyish = max(bodyish, masked);

    float gasSignature = 1.0 - smoothstep(0.012, 0.030, abs((rgb.r - rgb.b) - rgb.r * 0.20));
    float gas = grey * gasSignature * smoothstep(0.20, 0.32, luma) * (1.0 - bodyish);

    hotness = max(max(bodyish, fire), gas * 0.2);

    float cold = ambientTemp + luma * 0.42;

    float temp = mix(cold, 0.04, skyness);
    temp = mix(temp, clamp(luma * 1.06, 0.0, 1.0), bodyish);
    temp = mix(temp, mask, masked);
    temp = mix(temp, clamp(luma * 1.08, 0.0, 1.0), gas);
    temp = mix(temp, 1.0, fire);
    return temp;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));

    float temp = 0.0;
    float weightSum = 0.0;
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec2 offset = vec2(float(x), float(y)) * texel * SENSOR_BLUR;
            float weight = exp(-float(x * x + y * y) * 0.36);
            float ignored;
            temp += temperatureOf(texture(Sampler0, texCoord + offset).rgb,
                    texture(Sampler1, texCoord + offset).r, ignored) * weight;
            weightSum += weight;
        }
    }
    temp /= weightSum;

    float halo = 0.0;
    float haloWeight = 0.0;
    float jitter = hash(floor(gl_FragCoord.xy)) * 6.2831853;
    for (int i = 0; i < 16; i++) {
        float along = (float(i) + 0.5) / 16.0;
        float angle = jitter + along * 6.2831853 * 2.6;
        float radius = BLOOM_RADIUS * sqrt(along);
        vec2 offset = vec2(cos(angle), sin(angle)) * texel * radius;
        float hotness;
        float sampled = temperatureOf(texture(Sampler0, texCoord + offset).rgb,
                texture(Sampler1, texCoord + offset).r, hotness);
        float weight = 1.0 - along;
        halo += hotness * sampled * weight;
        haloWeight += weight;
    }
    halo /= max(haloWeight, 0.001);
    temp = max(temp, halo * 1.35);

    float fixedPattern = (hash(floor(gl_FragCoord.xy)) - 0.5) * 0.026;
    float shimmer = (hash(floor(gl_FragCoord.xy) + vec2(Time * 37.0)) - 0.5) * 0.016;

    float T = clamp(temp + fixedPattern + shimmer, 0.0, 1.0);
    fragColor = vec4(T, T, T, 1.0);
}
