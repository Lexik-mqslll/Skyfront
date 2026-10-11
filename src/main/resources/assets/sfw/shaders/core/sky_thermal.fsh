#version 150

out vec4 fragColor;

void main() {
    float T_sky = 0.02;
    fragColor = vec4(T_sky, T_sky, T_sky, 1.0);
}
