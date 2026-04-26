package com.kumagai.composeshaders.blur

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt


class BlurState {
    var isCapturing = false
    var internalBitmap: Bitmap? = null
    var lastUpdateTime: Long = 0
    var tick by mutableLongStateOf(0L) // Used to force draw updates in Compose
}



/**
 * Drawing logic and visual effects common to all implementations.
 * Resolves the "sticker" look with a 360 mask and rim light.
 * The [drawBlur] parameter allows each implementation to define how the blurred background is drawn.
 */
fun Modifier.applyCommonBlurEffects(
    layoutCoords: LayoutCoordinates?,
    blurState: BlurState,
    overlayColor: Color,
    onPositioned: (LayoutCoordinates) -> Unit,
    drawBlur: (android.graphics.Canvas, Float, Float) -> Unit
): Modifier = this.onGloballyPositioned { onPositioned(it) }
    .graphicsLayer { alpha = if (blurState.isCapturing) 0f else 1f }
    .drawBehind {
        val bitmap = blurState.internalBitmap ?: return@drawBehind
        val _tick = blurState.tick

        val w = size.width
        val h = size.height

        drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas
            
            // 1. Isolated layer for blurred background + mask
            val checkpoint = nativeCanvas.saveLayer(0f, 0f, w, h, null)
            
            // 2. Draws the blurred background (specific to each implementation)
            drawBlur(nativeCanvas, w, h)

            // 3. Draw Tint, Rim Light, and Stroke inside the layer so they are masked
            // Fill tint (Overlay Color)
            val tintPaint = android.graphics.Paint().apply { 
                color = overlayColor.toArgb() 
                style = android.graphics.Paint.Style.FILL
            }
            nativeCanvas.drawRect(0f, 0f, w, h, tintPaint)

            // Top Rim Light (Simulates light hitting the top edge)
            val rimPaint = android.graphics.Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h * 0.2f,
                    intArrayOf(Color.White.copy(alpha = 0.25f).toArgb(), 0x00FFFFFF),
                    null, Shader.TileMode.CLAMP)
            }
            nativeCanvas.drawRect(0f, 0f, w, h, rimPaint)

            // Physical edge (Stroke) with fade to avoid sharp cut at the base
            val strokePaint = android.graphics.Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h,
                    intArrayOf(Color.White.copy(alpha = 0.35f).toArgb(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.85f), Shader.TileMode.CLAMP)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.dp.toPx()
            }
            nativeCanvas.drawRect(0f, 0f, w, h, strokePaint)

            // 4. 360° Vignette Mask (Solves the "sticker" look)
            val maskPaint = android.graphics.Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            
            // Vertical: Smoothens Top and Base (Progressive fade)
            maskPaint.shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(-0x1, -0x1, -0x1, 0x00FFFFFF),
                floatArrayOf(0f, 0.15f, 0.85f, 1f), Shader.TileMode.CLAMP)
            nativeCanvas.drawRect(0f, 0f, w, h, maskPaint)

            // Horizontal: Smoothens Sides (Side integration)
//            maskPaint.shader = LinearGradient(0f, 0f, w, 0f,
//                intArrayOf(0x00FFFFFF, -0x1, -0x1, 0x00FFFFFF),
//                floatArrayOf(0f, 0.05f, 0.95f, 1f), Shader.TileMode.CLAMP)
//            nativeCanvas.drawRect(0f, 0f, w, h, maskPaint)
            
            nativeCanvas.restoreToCount(checkpoint)
        }
    }