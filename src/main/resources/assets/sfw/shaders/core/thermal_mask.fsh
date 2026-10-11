#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);

    if (texColor.a * vertexColor.a * ColorModulator.a < 0.1) {
        discard;
    }

    float texLuma = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));
    float material = mix(0.85, 1.0, smoothstep(0.20, 0.75, texLuma));

    float temp = clamp(vertexColor.r * material, 0.0, 1.0);
    fragColor = vec4(temp, temp, temp, 1.0);
}
