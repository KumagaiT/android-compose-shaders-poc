#include <jni.h>
#include <GLES2/gl2.h>
#include <vector>
#include <string>
#include <android/log.h>

#define LOG_TAG "NativeVisualEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const char* VERTEX_SHADER =
    "attribute vec4 aPosition;"
    "void main() {"
    "  gl_Position = aPosition;"
    "}";

const char* FRAGMENT_SHADER =
    "precision highp float;"
    "uniform vec2 uResolution;"
    "uniform float uTime;"
    "uniform vec4 uSmokeColor;"

    "float hash(vec2 p) {"
    "    vec3 p3 = fract(vec3(p.xyx) * 0.1031);"
    "    p3 += dot(p3, p3.yzx + 33.33);"
    "    return fract((p3.x + p3.y) * p3.z);"
    "}"

    "float noise(vec2 p) {"
    "    vec2 i = floor(p);"
    "    vec2 f = fract(p);"
    "    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);"
    "    return mix(mix(hash(i + vec2(0.0,0.0)), hash(i + vec2(1.0,0.0)), u.x),"
    "               mix(hash(i + vec2(0.0,1.0)), hash(i + vec2(1.0,1.0)), u.x), u.y);"
    "}"

    "float fbm(vec2 p) {"
    "    float v = 0.0;"
    "    float a = 0.5;"
    "    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);" // Matriz de rotação (~36 graus)
    "    for (int i = 0; i < 5; ++i) {"
    "        v += a * noise(p + uTime * 0.1);"
    "        p = rot * p * 2.2 + vec2(10.0, 10.0);" // Rotaciona e escala
    "        a *= 0.48;"
    "    }"
    "    return v;"
    "}"

    "void main() {"
    "    vec2 uv = gl_FragCoord.xy / uResolution.xy;"
    "    float aspect = uResolution.x / uResolution.y;"
    "    vec2 p = uv;"
    "    p.x *= aspect;"
    "    float intensity = fbm(p * 2.5);"
    "    intensity = smoothstep(0.1, 0.8, intensity);"
    "    float dist = length(p - vec2(0.5 * aspect, 0.5));"
    "    float vignette = smoothstep(0.8, 0.3, dist);"
    "    intensity *= vignette;"
    "    vec3 finalColor = mix(vec3(1.0, 1.0, 1.0), uSmokeColor.rgb, intensity);"
    "    gl_FragColor = vec4(finalColor, 1.0);"
    "}";

GLuint gProgram = 0;
GLint gaPositionHandle = -1;
GLint guResolutionHandle = -1;
GLint guTimeHandle = -1;
GLint guSmokeColorHandle = -1;

void checkGlError(const char* op) {
    for (GLint error = glGetError(); error; error = glGetError()) {
        LOGE("after %s() glError (0x%x)\n", op, error);
    }
}

GLuint loadShader(GLenum type, const char* shaderCode) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &shaderCode, NULL);
    glCompileShader(shader);
    GLint compiled;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen) {
            char* buf = (char*) malloc(infoLen);
            if (buf) {
                glGetShaderInfoLog(shader, infoLen, NULL, buf);
                LOGE("Could not compile shader %d:\n%s\n", type, buf);
                free(buf);
            }
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kumagai_composeshaders_NativeVisualEngine_initGL(JNIEnv *env, jobject thiz) {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

    gProgram = glCreateProgram();
    glAttachShader(gProgram, vertexShader);
    glAttachShader(gProgram, fragmentShader);
    glLinkProgram(gProgram);

    GLint linkStatus;
    glGetProgramiv(gProgram, GL_LINK_STATUS, &linkStatus);
    if (linkStatus != GL_TRUE) {
        LOGE("Could not link program");
    }

    gaPositionHandle = glGetAttribLocation(gProgram, "aPosition");
    guResolutionHandle = glGetUniformLocation(gProgram, "uResolution");
    guTimeHandle = glGetUniformLocation(gProgram, "uTime");
    guSmokeColorHandle = glGetUniformLocation(gProgram, "uSmokeColor");
}

extern "C" JNIEXPORT void JNICALL
Java_com_kumagai_composeshaders_NativeVisualEngine_resizeGL(JNIEnv *env, jobject thiz, jint width, jint height) {
    glViewport(0, 0, width, height);
    if (gProgram != 0) {
        glUseProgram(gProgram);
        glUniform2f(guResolutionHandle, (float)width, (float)height);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_kumagai_composeshaders_NativeVisualEngine_renderGL(JNIEnv *env, jobject thiz, jint color, jfloat time) {
    // Clear com Cinza Claro para depurar: se o fundo for branco, o desenho funcionou.
    // Se for cinza claro, o desenho falhou.
    glClearColor(0.95f, 0.95f, 0.95f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    if (gProgram == 0) return;

    glUseProgram(gProgram);

    float r = (float)((color >> 16) & 0xFF) / 255.0f;
    float g = (float)((color >> 8) & 0xFF) / 255.0f;
    float b = (float)(color & 0xFF) / 255.0f;
    float a = (float)((color >> 24) & 0xFF) / 255.0f;

    glUniform4f(guSmokeColorHandle, r, g, b, a);
    glUniform1f(guTimeHandle, time);

    static const float vertices[] = {
        -1.0f,  1.0f,
        -1.0f, -1.0f,
         1.0f,  1.0f,
         1.0f, -1.0f,
    };

    if (gaPositionHandle != -1) {
        glVertexAttribPointer(gaPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, vertices);
        glEnableVertexAttribArray(gaPositionHandle);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }
}
