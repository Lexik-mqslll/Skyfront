#version 150

uniform sampler2D Sampler0;
uniform float biomeTemp;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float alpha = texture(Sampler0, texCoord0).a * vertexColor.a;
    if (alpha < 0.1) {
        discard;
    }

    float T_cloud = 0.08 + biomeTemp * 0.03;
    fragColor = vec4(T_cloud, T_cloud, T_cloud, alpha);
}
