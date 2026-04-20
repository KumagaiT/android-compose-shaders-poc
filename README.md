# Android Compose Shaders PoC

A proof-of-concept Android application demonstrating the integration of custom shader effects as animated backgrounds in Jetpack Compose. Supports both AGSL shaders (Android 13+/API 33+) and a high-performance OpenGL ES NDK fallback for older Android versions (API 24+).

---

## 🚀 Overview

This repository evaluates two approaches to render a dynamic "smoke" effect as a background in Jetpack Compose:

- **Modern (API 33+):** Uses [`RuntimeShader`](https://developer.android.com/reference/android/graphics/RuntimeShader) (AGSL) for hardware-accelerated effects directly via Compose Graphics.
- **Legacy Fallback (API 24+):** Native C++ (JNI/NDK) hardware-accelerated rendering via **OpenGL ES 2.0** for high-performance animation on older devices.

The effect uses fractal noise (FBM) with octave rotation to create an organic, grid-free smoke look.

---

## 🏗️ Architecture

```
android-compose-shaders-poc/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── cpp/
│           │   └── native-lib.cpp         // OpenGL ES 2.0 NDK implementation
│           └── java/com/kumagai/composeshaders/
│               ├── MainActivity.kt        // Entry, branches on API level
│               ├── NativeSmokeBackground.kt // AGSL shader (API 33+)
│               ├── NativeCompatSmokeBackground.kt // OpenGL ES fallback (API 24+)
│               ├── Shaders.kt             // AGSL shader code
│               └── FpsCounter.kt          // Simple FPS meter
├── build.gradle.kts
└── settings.gradle.kts
```

---

## ✨ Features

- **Animated Smoke Shader**: Real-time, animated GPU-based effect across all supported versions.
- **Hardware Acceleration**: Both AGSL and NDK versions run entirely on the GPU.
- **Seamless Compose Integration**: Wrapped in easy-to-use Composable functions.
- **Legacy Compatibility**: High-performance OpenGL ES fallback for API 24+, avoiding CPU bottlenecks.
- **Performance Meter**: Overlay FPS counter to verify smoothness.
- **Grid-Free Noise**: Uses coordinate rotation between octaves to ensure an organic look.

---

## 📱 Usage

The application automatically selects the best renderer based on the device's API level:

```kotlin
// In your Composable
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    NativeSmokeBackground(isAnimated = true) // Modern AGSL
} else {
    NativeCompatSmokeBackground(isAnimated = true) // Legacy OpenGL ES
}
```

Both versions support customization:
```kotlin
NativeCompatSmokeBackground(
    smokeColor = Color.Cyan,
    isAnimated = true
)
```

---

## 🛠️ Build & Run

**Requirements:**
- Android Studio (Giraffe or newer)
- Android SDK 24+
- CMake/NDK (for OpenGL ES fallback)
- Kotlin 1.9+ / Jetpack Compose BOM 2026.03.01

**Steps:**
1. Clone: `git clone https://github.com/KumagaiT/android-compose-shaders-poc.git`
2. Open in Android Studio.
3. Run on device/emulator.

---

## 🧑‍💻 How it Works

### 🟣 AGSL Shader (API 33+)
- Uses `RuntimeShader` to compile AGSL at runtime.
- Integrated directly into the Compose `Canvas` via `drawPaint`.
- Leverages the modern Android graphics pipeline.

### 🔵 OpenGL ES Fallback (API 24–32)
- Uses `GLSurfaceView` to manage a dedicated rendering thread.
- Native C++ code in `native-lib.cpp` implements the same FBM logic using GLSL.
- Extremely low overhead as it bypasses the CPU for pixel processing.
- Thread-safe synchronization between Compose state and GL rendering.

---

## ⚡ Performance

- **API 33+**: Native GPU speed, optimized for the Compose lifecycle.
- **Older devices**: Hardware-accelerated via OpenGL ES, typically maintaining 60fps+ even on legacy hardware due to dedicated GPU rendering.

---

## 🪲 Known limitations

- `NativeCompatSmokeBackground` uses a `SurfaceView` variant, which sits on a separate window layer (standard `SurfaceView` behavior).
- Coordination of multiple overlapping shaders on older devices may require careful Z-order management.
- Interaction effects (touch) not yet implemented.

---

## 👤 Author

Created by [KumagaiT](https://github.com/KumagaiT)
