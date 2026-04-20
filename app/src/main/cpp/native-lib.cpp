#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <mutex>
#include <cmath>
#include <algorithm>

// Variável global para manter a referência enquanto a surface existir
static ANativeWindow* g_nativeWindow = nullptr;
static std::mutex g_windowMutex; // Protege o acesso global

static float g_time = 0.0f;

// Helpers matemáticos equivalentes ao GLSL
inline float fract(float x) { return x - std::floor(x); }
inline float mix(float a, float b, float t) { return a + t * (b - a); }
inline float step(float edge, float x) { return x < edge ? 0.0f : 1.0f; }

// Hash "Hash Without Sine" para C++
float hash(float x, float y) {
    float p3x = fract(x * 0.1031f);
    float p3y = fract(y * 0.1031f);
    float p3z = fract(x * 0.1031f);

    float dotVal = p3x * (p3y + 33.33f) + p3y * (p3z + 33.33f) + p3z * (p3x + 33.33f);
    p3x += dotVal;
    p3y += dotVal;
    p3z += dotVal;
    return fract((p3x + p3y) * p3z);
}

float noise(float x, float y) {
    float ix = std::floor(x);
    float iy = std::floor(y);
    float fx = fract(x);
    float fy = fract(y);

    // Interpolação quintic para suavidade máxima
    float ux = fx * fx * fx * (fx * (fx * 6.0f - 15.0f) + 10.0f);
    float uy = fy * fy * fy * (fy * (fy * 6.0f - 15.0f) + 10.0f);

    return mix(mix(hash(ix, iy), hash(ix + 1.0f, iy), ux),
               mix(hash(ix, iy + 1.0f), hash(ix + 1.0f, iy + 1.0f), ux), uy);
}

float fbm(float x, float y, float time) {
    float v = 0.0f;
    float a = 0.5f;
    for (int i = 0; i < 4; ++i) {
        float velocity = 0.1f + (float)i * 0.02f;
        v += a * noise(x + time * velocity, y + time * velocity);
        x = x * 2.1f + 7.31f;
        y = y * 2.1f + 7.31f;
        a *= 0.4f; // Reduzido para suavizar (menos detalhes "picados")
    }
    return v;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kumagai_myapplication_NativeVisualEngine_drawSurface(JNIEnv *env, jobject thiz, jint color) {
    std::lock_guard<std::mutex> lock(g_windowMutex);
    if (g_nativeWindow == nullptr) return;

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(g_nativeWindow, &buffer, NULL) == 0) {
        if (buffer.bits == nullptr || buffer.format != WINDOW_FORMAT_RGBA_8888) {
            ANativeWindow_unlockAndPost(g_nativeWindow);
            return;
        }

        // Versão estática: não incrementamos o g_time
        uint32_t* pixelPtr = (uint32_t*)buffer.bits;

        // Extrai as cores do ARGB enviado pelo Kotlin
        const float smokeR = (float)((color >> 16) & 0xFF);
        const float smokeG = (float)((color >> 8) & 0xFF);
        const float smokeB = (float)(color & 0xFF);

        for (int y = 0; y < buffer.height; ++y) {
            uint32_t* row = pixelPtr + (y * buffer.stride);
            float uvY = (float)y / buffer.height;

            for (int x = 0; x < buffer.width; ++x) {
                float uvX = (float)x / buffer.width;

                // FBM Estático (tempo fixo em 0.0)
                // Escala reduzida (1.5f) para manchas maiores e mais suaves
                float intensity = fbm(uvX * 1.5f, uvY * 1.5f, 0.0f);

                // Transição ultra suave: Range de 0.0 a 1.0 para evitar bordas
                intensity = std::max(0.0f, std::min(1.0f, (intensity - 0.05f) / 0.9f));

                // Interpolação: Branco Puro -> Cor da Mancha
                uint32_t r = (uint32_t)(255.0f - (255.0f - smokeR) * intensity);
                uint32_t g = (uint32_t)(255.0f - (255.0f - smokeG) * intensity);
                uint32_t b = (uint32_t)(255.0f - (255.0f - smokeB) * intensity);

                // Alpha 255 (Opaco)
                row[x] = (0xFF << 24) | (b << 16) | (g << 8) | r;
            }
        }
        ANativeWindow_unlockAndPost(g_nativeWindow);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kumagai_myapplication_NativeVisualEngine_setSurface(JNIEnv *env, jobject thiz, jobject surface) {
    std::lock_guard<std::mutex> lock(g_windowMutex);
    if (g_nativeWindow != nullptr) {
        ANativeWindow_release(g_nativeWindow);
        g_nativeWindow = nullptr;
    }

    if (surface != nullptr) {
        g_nativeWindow = ANativeWindow_fromSurface(env, surface);

        // PERFORMANCE: Reduzimos a resolução interna do buffer.
        // Renderizar em 1/4 da resolução (4x menor em cada eixo) melhora a performance em 16x.
        // O hardware do Android fará o upscale suave automaticamente.
        int32_t width = ANativeWindow_getWidth(g_nativeWindow);
        int32_t height = ANativeWindow_getHeight(g_nativeWindow);
        if (width > 0 && height > 0) {
            ANativeWindow_setBuffersGeometry(g_nativeWindow, width / 4, height / 4, WINDOW_FORMAT_RGBA_8888);
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kumagai_myapplication_NativeVisualEngine_releaseSurface(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_windowMutex);
    if (g_nativeWindow != nullptr) {
        ANativeWindow_release(g_nativeWindow);
        g_nativeWindow = nullptr;
    }
}
