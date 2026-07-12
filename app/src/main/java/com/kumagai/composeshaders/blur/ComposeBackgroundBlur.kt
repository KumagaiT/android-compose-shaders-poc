package com.kumagai.composeshaders.blur

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/**
 * IMPLEMENTATION 3: MODERN COMPOSE (API 31+)
 * 
 * Uses RenderNode and RenderEffect for a native blur on the RenderThread.
 */
fun Modifier.modernBackgroundBlur(
    blurRadius: Float = 10f,
    downsample: Float = 2f,
    overlayColor: Color = Color.White.copy(alpha = 0.20f)
): Modifier = this.backgroundBlur(
    implementation = BlurImplementation.Modern,
    blurRadius = blurRadius,
    downsample = downsample,
    overlayColor = overlayColor
)
