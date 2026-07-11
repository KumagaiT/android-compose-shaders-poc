package com.kumagai.composeshaders.blur

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.ViewTreeObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/**
 * IMPLEMENTATION 2: RENDER SCRIPT (Hardware Accelerated)
 * Uses Android's ScriptIntrinsicBlur. Extremely fast on Android 8-11.
 * Ideal for Snapdragon 450 by offloading the load to the GPU.
 */
fun Modifier.renderScriptBackgroundBlur(
    blurRadius: Float = 10f,
    downsample: Float = 2f,
    overlayColor: Color = Color.White.copy(alpha = 0.20f)
): Modifier = this.backgroundBlur(
    implementation = BlurImplementation.RenderScript,
    blurRadius = blurRadius,
    downsample = downsample,
    overlayColor = overlayColor
)

