package com.kumagai.composeshaders.blur

import android.graphics.Bitmap
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import com.kumagai.composeshaders.NativeVisualEngine
import kotlin.math.roundToInt



/**
 * IMPLEMENTATION 1: NDK (C++ / NEON SIMD)
 * Focused on full memory control and CPU optimization.
 */
fun Modifier.legacyBackgroundBlur(
    blurRadius: Float = 10f,
    downsample: Float = 2f,
    overlayColor: Color = Color.White.copy(alpha = 0.20f)
): Modifier = this.backgroundBlur(
    implementation = BlurImplementation.Legacy,
    blurRadius = blurRadius,
    downsample = downsample,
    overlayColor = overlayColor
)
