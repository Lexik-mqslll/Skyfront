#version 150

uniform sampler2D Sampler0;

uniform float entityTemp;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);

    float alpha = texColor.a * vertexColor.a * ColorModulator.a;
    if (alpha < 0.1) {
        discard;
    }

    float T = entityTemp > 0.01 ? entityTemp : 0.92;

    float texLuma = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));
    float material = mix(0.80, 1.0, smoothstep(0.20, 0.75, texLuma));

    float form = dot(vertexColor.rgb, vec3(0.299, 0.587, 0.114));
    float shape = mix(0.84, 1.0, clamp(form, 0.0, 1.0));

    float temp = clamp(T * material * shape, 0.0, 1.0);

    fragColor = vec4(temp, temp, temp * 0.90, 1.0);
}
