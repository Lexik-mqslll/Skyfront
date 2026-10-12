#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in vec4 rawColor;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    bool hot = rawColor.r > 0.980 && rawColor.g < 0.004 && rawColor.b > 0.02;
    bool mottled = rawColor.r < 0.996;

    if (hot) {
        float alpha = tex.a * rawColor.a * ColorModulator.a;
        float coverage = smoothstep(0.015, 0.22, alpha);
        if (coverage < max(0.02, hash(floor(gl_FragCoord.xy)))) {
            discard;
        }
        float heat = rawColor.b;
        float texLuma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
        float t;
        if (mottled) {
            float density = smoothstep(0.05, 0.75, alpha);
            float eddies = valueNoise(texCoord0 * 420.0) * 0.6 + valueNoise(texCoord0 * 1100.0) * 0.4;
            float warmth = mix(0.62, 1.0, density) * mix(0.72, 1.08, eddies) * mix(0.90, 1.04, texLuma);
            t = clamp(heat * warmth, 0.0, 1.0);
            fragColor = vec4(t, t, t * 0.80, 1.0);
            return;
        } else {
            t = clamp(heat * mix(0.84, 1.0, texLuma), 0.0, 1.0);
        }
        fragColor = vec4(t, t, t * 0.90, 1.0);
        return;
    }

    vec4 color = tex * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
