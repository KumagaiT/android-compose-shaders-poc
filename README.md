# Android Compose Shaders PoC

A proof-of-concept Android application demonstrating the integration of high-performance custom shader effects and advanced blurring techniques in Jetpack Compose. This project focuses on **Glassmorphism (Frosty Window)** and **Animated Backgrounds**, with special optimizations for legacy hardware like the Snapdragon 450.

---

## 🚀 Overview

This repository evaluates multiple approaches to render complex visual effects in Jetpack Compose, organized into two main categories:

### 1. ❄️ Frosty Window (Glassmorphism)
A high-performance background blur effect with "Soft Edge" (Vignette) and "Rim Light" polish. It implements three engines to guarantee performance across all Android generations:
- **NDK StackBlur (CPU + NEON SIMD):** C++ implementation using SIMD instructions for maximum CPU efficiency. Compatible with all Android versions.
- **RenderScript (GPU Legacy):** Hardware-accelerated blur using the intrinsic API, ideal for Android 8-11.
- **Modern RenderNode (GPU Nativa - API 31+):** Native Android 12+ `RenderEffect` pipeline, the most efficient solution for modern devices.

#### 📖 How to use:
```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(150.dp)
        .modernBackgroundBlur(overlayColor = Color.White.copy(alpha = 0.15f))
) {
    Text("Frosty Window Content")
}
```

### 2. ✨ Shimmer Effect (Synchronized Loading)
A high-performance loading shimmer that synchronizes across multiple views to create a "window" effect:
- **AGSL (API 33+):** GPU-native shimmer using a custom Gaussian distribution shader for a soft, natural glow without hard bevels.
- **Compose Canvas (Compatible):** Optimized `Brush` implementation with global coordinate synchronization for all Android versions.
- **Performance Optimized:** Uses non-recomposing coordinate tracking to maintain high FPS (60+) during fast scrolls, even in complex lists.
- **Window Synchronization:** Every node acts as a "window" to a single global shimmer animation, preventing visual clutter and ensuring consistency.

#### 📖 How to use:
```kotlin
// Recommended: Using CompositionLocal (automatically synchronizes all sub-components)
CompositionLocalProvider(LocalShimmerState provides rememberShimmerState()) {
    Column {
        Box(modifier = Modifier.shimmer()) // Synchronized
        Box(modifier = Modifier.shimmer()) // Synchronized
    }
}
```

### 3. 💨 Smoke Background (GPU Shaders)
Dynamic fractal noise (FBM) backgrounds with organic, grid-free motion:
- **AGSL (API 33+):** Modern `RuntimeShader` implementation integrated into the Compose pipeline.
- **OpenGL ES 2.0 (Native NDK):** JNI/C++ fallback for high-performance animation on devices from API 24+.
- **Compose CPU:** A reference implementation using pixel-looping (for educational/debugging purposes).

#### 📖 How to use:
```kotlin
NativeSmokeBackground(
    smokeColor = Color(0xFFFF00E0),
    isAnimated = true
)
```

---

## ✨ Features

- **Multi-Engine Blur**: Contextual choice between NDK, RenderScript, and RenderNode based on device capabilities.
- **360° Soft-Vignette Masking**: Advanced `saveLayer` masking to eliminate the "sticker" look and blend glass surfaces naturally.
- **Visual Depth Polish**: Integrated "Rim Lighting" and semi-transparent "Physical Strokes" for a premium glass feel.
- **Synchronized Loading placeholders**: Advanced shimmer system with Gaussian distribution for professional loading states.
- **Real-time Performance**: Optimized to maintain 60 FPS scroll even on Snapdragon 450 devices.
- **Interactive UI**: Tabbed navigation to switch between **Smoke**, **Blur**, and **Shimmer** effects with live configuration chips.
- **Live Color Picker**: Global color state that updates both shaders and glass tints in real-time.
- **Synchronized Shimmer**: Unified loading states that follow a single global animation pattern.

---

## 🏗️ Architecture

```
android-compose-shaders-poc/
├── app/
│   ├── src/main/
│   │   ├── cpp/
│   │   │   └── native-lib.cpp         // OES 2.0 Shaders & NDK StackBlur (NEON SIMD)
│   │   └── java/com/kumagai/composeshaders/
│   │       ├── blur/
│   │       │   ├── Blur.kt            // Unified Visual Engine & Shared Logic
│   │       │   ├── LegacyBackgroundBlur.kt // NDK CPU Engine
│   │       │   ├── RenderScriptBackgroundBlur.kt // RS GPU Engine
│   │       │   └── ComposeBackgroundBlur.kt // Modern RenderNode Engine (API 31+)
│   │       ├── shimmer/
│   │       │   ├── Shimmer.kt         // Shared State & Unified Modifier
│   │       │   ├── ComposeShimmer.kt  // Brush-based Engine
│   │       │   └── AgslShimmer.kt     // AGSL-based Engine
│   │       ├── smoke/
│   │       │   ├── NativeSmokeBackground.kt // AGSL implementation
│   │       │   └── NativeCompatSmokeBackground.kt // NDK GLES implementation
│   │       └── MainActivity.kt        // Effects Showcase & Info Cards
```

---

## ⚡ Performance Optimization (Snapdragon 450 Focus)

- **Throttling**: Background capture frequency is throttled to 16ms (60fps target) or higher to preserve CPU for the UI thread.
- **Intelligent Downsampling**: Bitmaps are captured at reduced scales (2x to 4x) to minimize memory footprint and blur complexity.
- **State-Driven Invalidation**: Uses a `tick` state in `BlurState` to force Compose to re-draw only when the background capture actually updates.
- **Anti-Recursion**: Automatic alpha toggling (`graphicsLayer`) during `OnPreDraw` to prevent the blur from capturing its own overlay.

---

## 🛠️ Build & Run

**Requirements:**
- Android Studio Ladybug or newer
- Android SDK 24+ (Min) / 34+ (Target)
- NDK & CMake (for C++/NEON components)
- Kotlin 2.x

**Steps:**
1. Clone: `git clone https://github.com/KumagaiT/android-compose-shaders-poc.git`
2. Open in Android Studio.
3. Build and run on a physical device (recommended for performance testing).

---

## 👤 Author

Created by [KumagaiT](https://github.com/KumagaiT)
