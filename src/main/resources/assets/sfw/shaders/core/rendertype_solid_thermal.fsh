#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform float gameTime;
uniform float biomeTemp;
uniform float ambientTemp;
uniform float weatherMod;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 lightmapCoord;
in vec3 worldNormal;
in vec3 worldPos;
in float blockLight;
in float skyLight;

out vec4 fragColor;

void main() {
    vec4 thermalData = texture(Sampler0, texCoord0);
    float baseTemp   = thermalData.r;
    float emissivity = thermalData.g;
    float matType    = thermalData.b;

    if (emissivity < 0.02) {
        baseTemp   = 0.12;
        emissivity = 0.90;
        matType    = 0.0;
    }

    float sunAngle = (gameTime - 0.25) * 2.0 * 3.14159265;
    vec3 sunDir = normalize(vec3(0.0, cos(sunAngle), sin(sunAngle)));
    float lambert = max(dot(normalize(worldNormal), sunDir), 0.0);

    bool isMetal = (matType > 0.4 && matType < 0.6);

    float solarCoeff = isMetal ? 0.45 : 0.18;
    float solarHeat = lambert * skyLight * weatherMod * solarCoeff;

    float dayFactor = max(0.0, cos((gameTime - 0.25) * 2.0 * 3.14159265));
    float metalRetained = isMetal ? dayFactor * 0.18 : 0.0;

    float blockHeat = pow(blockLight, 1.5) * 0.65;

    float T_surface = mix(baseTemp, 1.0, blockHeat);
    T_surface = T_surface + solarHeat + metalRetained;

    float upFacing = max(normalize(worldNormal).y, 0.0);
    float roofFactor = smoothstep(0.25, 0.35, baseTemp);
    float roofCool = upFacing * upFacing * 0.22 * roofFactor;
    T_surface = T_surface - roofCool;

    float envReflection = ambientTemp;
    float T_apparent = mix(envReflection, T_surface, emissivity);
    T_apparent = clamp(T_apparent, 0.0, 1.0);

    fragColor = vec4(T_apparent, T_apparent, T_apparent, 1.0);
}
