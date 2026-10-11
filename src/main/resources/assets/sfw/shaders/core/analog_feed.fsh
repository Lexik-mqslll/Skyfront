#version 150

uniform sampler2D Sampler0;

uniform float Time;
uniform float Interference;
uniform float Burst;
uniform float Stripes;
uniform vec4 FeedRect;
uniform float StaticOnly;
uniform float Stage;
uniform float SyncShift;
uniform float SyncRoll;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec2 toFeed(vec2 uv) {
    return (uv - FeedRect.xy) / (FeedRect.zw - FeedRect.xy);
}

vec2 fromFeed(vec2 f) {
    return FeedRect.xy + f * (FeedRect.zw - FeedRect.xy);
}

float valueNoise(vec2 f, vec2 cells, float seed) {
    vec2 p = f * cells;
    vec2 i = floor(p);
    vec2 t = p - i;
    t = t * t * (3.0 - 2.0 * t);
    float a = hash(i + seed);
    float b = hash(i + vec2(1.0, 0.0) + seed);
    float c = hash(i + vec2(0.0, 1.0) + seed);
    float d = hash(i + vec2(1.0, 1.0) + seed);
    return mix(mix(a, b, t.x), mix(c, d, t.x), t.y);
}

vec3 speck(vec2 cell, float frame, float colour) {
    float r = hash(cell + frame * 1.37);
    float pushed = r < 0.5 ? r * r * 2.0 : 1.0 - (1.0 - r) * (1.0 - r) * 2.0;
    float l = mix(r, pushed, 0.5);
    vec3 c = vec3(l);
    vec3 hue = vec3(hash(cell + frame * 2.13), hash(cell + frame * 3.31), hash(cell + frame * 4.77));
    hue = mix(hue, vec3(hue.x, hue.y * 0.7, hue.x), 0.5);
    return mix(c, c * (0.5 + 1.0 * hue), colour * 0.45);
}

float lostStatic(vec2 f, float frame) {
    float along = valueNoise(f, vec2(80.0, 192.0), frame * 1.37);
    float across = valueNoise(f, vec2(240.0, 64.0), frame * 2.91);
    float pick = hash(floor(f * vec2(120.0, 96.0)) + frame * 4.13);
    float dashes = pick < 0.55 ? along : across;
    float fine = hash(floor(f * vec2(240.0, 192.0)) + frame * 5.7);
    float n = clamp((dashes * 0.75 + fine * 0.25 - 0.5) * 1.2 + 0.5, 0.0, 1.0);
    return pow(n, 1.5);
}

void main() {
    vec2 uv = texCoord;
    vec2 f = toFeed(uv);
    if (f.x < 0.0 || f.x > 1.0 || f.y < 0.0 || f.y > 1.0) {
        fragColor = texture(Sampler0, uv);
        return;
    }

    float frame = mod(floor(Time * 50.0), 2048.0);
    float line = floor(f.y * 288.0);
    vec2 cell = floor(f * vec2(480.0, 288.0));
    float level = clamp(Interference + Burst, 0.0, 1.0);
    float colourOn = 1.0 - 0.6 * smoothstep(0.75, 1.0, level);

    if (StaticOnly > 0.5) {
        vec3 under = texture(Sampler0, uv).rgb;
        fragColor = vec4(mix(under, vec3(lostStatic(f, frame)), 0.95), 1.0);
        return;
    }

    if (Stage > 0.5) {
        float thin = floor(line / 2.0);
        float thick = floor(line / 6.0);
        bool band = hash(vec2(thin, frame + 11.0)) < Stripes * 0.05
                || hash(vec2(thick, frame + 29.0)) < Stripes * 0.02;
        float drag = band ? (hash(vec2(thick, frame + 5.0)) - 0.5) * 0.03 : 0.0;

        vec3 c;
        float lineVisible = 0.86;
        if (SyncShift > 0.0 || SyncRoll > 0.0) {
            float t = SyncShift > 0.0 ? fract(f.x + SyncShift) : f.x * lineVisible;
            float v = SyncRoll > 0.0 ? fract(f.y + SyncRoll) : f.y * 0.92;
            if (t >= lineVisible) {
                float into = (t - lineVisible) / (1.0 - lineVisible);
                c = vec3(into < 0.35 ? 0.015 : 0.10);
                c += (hash(cell + frame) - 0.5) * 0.05;
            } else if (v >= 0.92) {
                c = vec3(0.02);
            } else {
                c = texture(Sampler0, fromFeed(vec2(fract(t / lineVisible + drag), v / 0.92))).rgb;
            }
        } else {
            c = texture(Sampler0, fromFeed(vec2(fract(f.x + drag), f.y))).rgb;
        }

        if (band) {
            float streak = hash(vec2(floor(f.x * 90.0 + hash(vec2(line, frame)) * 7.0), line + frame * 3.1));
            float s = streak < 0.5 ? streak * streak * 2.0 : 1.0 - (1.0 - streak) * (1.0 - streak) * 2.0;
            c = mix(c, vec3(s), 0.7);
        }

        float density = 0.55 * pow(level, 1.6);
        if (hash(cell * 1.03 + frame * 0.71) < density) {
            c = mix(c, speck(cell, frame, colourOn), 0.85);
        }

        fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
        return;
    }

    float jitter = (hash(vec2(line, frame)) - 0.5) * 0.004 * level * level;
    vec2 sf = vec2(f.x + jitter, f.y);
    vec2 s = fromFeed(sf);
    float px = (FeedRect.z - FeedRect.x) / 720.0;

    vec3 a = texture(Sampler0, s).rgb;
    vec3 narrow = (a * 2.0 + texture(Sampler0, s + vec2(px, 0.0)).rgb
            + texture(Sampler0, s - vec2(px, 0.0)).rgb) / 4.0;
    vec2 cs = s + vec2(2.0 * px, 0.0);
    vec3 wide = (texture(Sampler0, cs - vec2(4.0 * px, 0.0)).rgb
            + texture(Sampler0, cs - vec2(2.0 * px, 0.0)).rgb
            + texture(Sampler0, cs).rgb
            + texture(Sampler0, cs + vec2(2.0 * px, 0.0)).rgb
            + texture(Sampler0, cs + vec2(4.0 * px, 0.0)).rgb) / 5.0;
    float y = luma(narrow);
    vec3 chroma = wide - vec3(luma(wide));
    vec3 col = vec3(y) + chroma * 0.6 * colourOn;

    col = mix(vec3(0.06), col, 0.92);

    col += (hash(cell + frame) - 0.5) * (0.012 + 0.12 * level);

    col *= 1.0 - 0.12 * level * hash(vec2(frame, 3.0));

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
