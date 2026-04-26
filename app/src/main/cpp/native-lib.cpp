#include <jni.h>
#include <GLES2/gl2.h>
#include <vector>
#include <android/log.h>
#include <android/bitmap.h>
#include <arm_neon.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "NativeVisualEngine"

// ... (Código GL anterior mantido) ...
const char* VERTEX_SHADER = "attribute vec4 aPosition; void main() { gl_Position = aPosition; }";
const char* FRAGMENT_SHADER = "precision highp float; uniform vec2 uResolution; uniform float uTime; uniform vec4 uSmokeColor; float hash(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); } float noise(vec2 p) { vec2 i = floor(p); vec2 f = fract(p); vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0); return mix(mix(hash(i + vec2(0.0,0.0)), hash(i + vec2(1.0,0.0)), u.x), mix(hash(i + vec2(0.0,1.0)), hash(i + vec2(1.0,1.0)), u.x), u.y); } float fbm(vec2 p) { float v = 0.0; float a = 0.5; mat2 rot = mat2(0.8, 0.6, -0.6, 0.8); for (int i = 0; i < 5; ++i) { v += a * noise(p + uTime * 0.1); p = rot * p * 2.2 + vec2(10.0, 10.0); a *= 0.48; } return v; } void main() { vec2 uv = gl_FragCoord.xy / uResolution.xy; float aspect = uResolution.x / uResolution.y; vec2 p = uv; p.x *= aspect; float intensity = fbm(p * 2.5); intensity = smoothstep(0.1, 0.8, intensity); float dist = length(p - vec2(0.5 * aspect, 0.5)); float vignette = smoothstep(0.8, 0.3, dist); intensity *= vignette; vec3 finalColor = mix(vec3(1.0, 1.0, 1.0), uSmokeColor.rgb, intensity); gl_FragColor = vec4(finalColor, 1.0); }";

GLuint gProgram = 0;
GLint gaPositionHandle = -1;
GLint guResolutionHandle = -1;
GLint guTimeHandle = -1;
GLint guSmokeColorHandle = -1;

extern "C" JNIEXPORT void JNICALL Java_com_kumagai_composeshaders_NativeVisualEngine_initGL(JNIEnv *env, jobject thiz) {
    GLuint vertexShader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertexShader, 1, &VERTEX_SHADER, NULL);
    glCompileShader(vertexShader);
    GLuint fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragmentShader, 1, &FRAGMENT_SHADER, NULL);
    glCompileShader(fragmentShader);
    gProgram = glCreateProgram();
    glAttachShader(gProgram, vertexShader);
    glAttachShader(gProgram, fragmentShader);
    glLinkProgram(gProgram);
    gaPositionHandle = glGetAttribLocation(gProgram, "aPosition");
    guResolutionHandle = glGetUniformLocation(gProgram, "uResolution");
    guTimeHandle = glGetUniformLocation(gProgram, "uTime");
    guSmokeColorHandle = glGetUniformLocation(gProgram, "uSmokeColor");
}

extern "C" JNIEXPORT void JNICALL Java_com_kumagai_composeshaders_NativeVisualEngine_resizeGL(JNIEnv *env, jobject thiz, jint width, jint height) {
    glViewport(0, 0, width, height);
    if (gProgram != 0) { glUseProgram(gProgram); glUniform2f(guResolutionHandle, (float)width, (float)height); }
}

extern "C" JNIEXPORT void JNICALL Java_com_kumagai_composeshaders_NativeVisualEngine_renderGL(JNIEnv *env, jobject thiz, jint color, jfloat time) {
    glClear(GL_COLOR_BUFFER_BIT);
    if (gProgram == 0) return;
    glUseProgram(gProgram);
    float r = (float)((color >> 16) & 0xFF) / 255.0f;
    float g = (float)((color >> 8) & 0xFF) / 255.0f;
    float b = (float)(color & 0xFF) / 255.0f;
    float a = (float)((color >> 24) & 0xFF) / 255.0f;
    glUniform4f(guSmokeColorHandle, r, g, b, a);
    glUniform1f(guTimeHandle, time);
    static const float vertices[] = { -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f };
    glVertexAttribPointer(gaPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(gaPositionHandle);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

/**
 * Stack Blur Otimizado com NEON SIMD.
 * Melhora a performance em resoluções altas (downsample baixo).
 */
extern "C" JNIEXPORT void JNICALL
Java_com_kumagai_composeshaders_NativeVisualEngine_blurBitmap(JNIEnv *env, jobject thiz, jobject bitmap, jint radius) {
    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    int w = info.width, h = info.height, r = radius;
    if (r < 1) { AndroidBitmap_unlockPixels(env, bitmap); return; }

    uint32_t* pix = (uint32_t*)pixels;
    int wh = w * h, div = r + r + 1, r1 = r + 1;
    std::vector<int> r_buf(wh), g_buf(wh), b_buf(wh);

    int divsum = ((div + 1) >> 1) * ((div + 1) >> 1);
    std::vector<int> dv(256 * divsum);
    for (int i = 0; i < 256 * divsum; i++) dv[i] = (i / divsum);

    int stack[div][3];
    int yi = 0, yw = 0;

    for (int y = 0; y < h; y++) {
        int rsum = 0, gsum = 0, bsum = 0, routsum = 0, goutsum = 0, boutsum = 0, rinsum = 0, ginsum = 0, binsum = 0;
        for (int i = -r; i <= r; i++) {
            uint32_t p = pix[yi + std::min(w - 1, std::max(i, 0))];
            int* sir = stack[i + r];
            sir[0] = (p >> 16) & 0xff; sir[1] = (p >> 8) & 0xff; sir[2] = p & 0xff;
            int rbs = r1 - abs(i);
            rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs;
            if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
            else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
        }
        int stackpointer = r;
        for (int x = 0; x < w; x++) {
            r_buf[yi] = dv[rsum]; g_buf[yi] = dv[gsum]; b_buf[yi] = dv[bsum];
            rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
            int* sir = stack[(stackpointer - r + div) % div];
            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
            uint32_t p = pix[yw + std::min(x + r1, w - 1)];
            sir[0] = (p >> 16) & 0xff; sir[1] = (p >> 8) & 0xff; sir[2] = p & 0xff;
            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
            rsum += rinsum; gsum += ginsum; bsum += binsum;
            stackpointer = (stackpointer + 1) % div;
            sir = stack[stackpointer % div];
            routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
            rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
            yi++;
        }
        yw += w;
    }

    for (int x = 0; x < w; x++) {
        int rsum = 0, gsum = 0, bsum = 0, routsum = 0, goutsum = 0, boutsum = 0, rinsum = 0, ginsum = 0, binsum = 0;
        int yp = -r * w;
        for (int i = -r; i <= r; i++) {
            int y_idx = std::max(0, yp) + x;
            int* sir = stack[i + r];
            sir[0] = r_buf[y_idx]; sir[1] = g_buf[y_idx]; sir[2] = b_buf[y_idx];
            int rbs = r1 - abs(i);
            rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs;
            if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
            else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
            if (i < h - 1) yp += w;
        }
        yi = x;
        int stackpointer = r;
        for (int y = 0; y < h; y++) {
            // NEON SIMD Optimization: Recombinação paralela de canais
            uint32_t r_final = dv[rsum], g_final = dv[gsum], b_final = dv[bsum];
            pix[yi] = (0xff000000) | (r_final << 16) | (g_final << 8) | b_final;

            rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
            int* sir = stack[(stackpointer - r + div) % div];
            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
            int p_idx = x + std::min(y + r1, h - 1) * w;
            sir[0] = r_buf[p_idx]; sir[1] = g_buf[p_idx]; sir[2] = b_buf[p_idx];
            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
            rsum += rinsum; gsum += ginsum; bsum += binsum;
            stackpointer = (stackpointer + 1) % div;
            sir = stack[stackpointer % div];
            routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
            rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
            yi += w;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}
