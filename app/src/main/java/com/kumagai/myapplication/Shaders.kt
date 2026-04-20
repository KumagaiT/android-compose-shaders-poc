package com.kumagai.myapplication

const val SMOKE_SHADER_CODE = """
uniform float2 uResolution;
uniform float uTime;
layout(color) uniform float4 uSmokeColor;

// Hash "Hash Without Sine" - Extremamente estável para evitar artefatos de grade
float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    // Interpolação quintic para suavidade máxima
    float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(hash(i + float2(0.0,0.0)), hash(i + float2(1.0,0.0)), u.x),
               mix(hash(i + float2(0.0,1.0)), hash(i + float2(1.0,1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    // Usamos um deslocamento pequeno e multiplicadores não inteiros para quebrar o padrão
    for (int i = 0; i < 5; ++i) {
        float velocity = 0.1 + float(i) * 0.02;
        v += a * noise(p + uTime * velocity);
        p = p * 2.1 + 7.31; 
        a *= 0.48;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution.xy;
    float aspect = uResolution.x / uResolution.y;
    float2 p = uv;
    p.x *= aspect;

    // Intensidade da fumaça com escala inicial maior
    float intensity = fbm(p * 2.5);
    
    // Contraste refinado
    intensity = smoothstep(0.1, 0.9, intensity);

    // Vinheta circular corrigida pelo aspect ratio
    float dist = length(p - float2(0.5 * aspect, 0.5));
    float vignette = smoothstep(0.8, 0.2, dist);
    intensity *= vignette;

    return half4(uSmokeColor.rgb * intensity, intensity * uSmokeColor.a);
}
"""