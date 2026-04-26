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
    blurRadius: Int = 10,
    downsample: Float = 2f,
    overlayColor: Color = Color.White.copy(alpha = 0.20f)
): Modifier = this.composed {
    val view = LocalView.current
    val blurState = remember { BlurState() }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val bitmapPaint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }

    DisposableEffect(view) {
        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            val coords = layoutCoords
            if (coords != null && coords.isAttached && !blurState.isCapturing) {
                val now = System.currentTimeMillis()
                // Throttling: it is possible to increase the value from 16 for scenarios where devices are less performant, but it causes a `lag` effect
                if (now - blurState.lastUpdateTime < 16) return@OnPreDrawListener true

                val width = coords.size.width.toFloat()
                val height = coords.size.height.toFloat()
                
                if (width > 0 && height > 0) {
                    val bw = (width / downsample).roundToInt().coerceAtLeast(1)
                    val bh = (height / downsample).roundToInt().coerceAtLeast(1)
                    
                    val bitmap = if (blurState.internalBitmap?.width != bw || blurState.internalBitmap?.height != bh) {
                        blurState.internalBitmap?.recycle()
                        Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { blurState.internalBitmap = it }
                    } else blurState.internalBitmap!!

                    val canvas = android.graphics.Canvas(bitmap)
                    val pos = coords.localToWindow(Offset.Zero)
                    
                    canvas.save()
                    canvas.scale(1f / downsample, 1f / downsample)
                    canvas.translate(-pos.x, -pos.y)
                    canvas.clipRect(pos.x, pos.y, pos.x + width, pos.y + height)
                    
                    try {
                        blurState.isCapturing = true
                        view.rootView.draw(canvas)
                    } catch (e: Exception) {
                    } finally {
                        blurState.isCapturing = false
                    }
                    canvas.restore()
                    
                    NativeVisualEngine.blurBitmap(bitmap, blurRadius)
                    blurState.lastUpdateTime = now
                }
            }
            true
        }
        
        view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            blurState.internalBitmap?.recycle()
        }
    }

    applyCommonBlurEffects(
        layoutCoords = layoutCoords,
        blurState = blurState,
        overlayColor = overlayColor,
        onPositioned = { layoutCoords = it }
    ) { canvas, w, h ->
        canvas.drawBitmap(blurState.internalBitmap!!, null, android.graphics.Rect(0, 0, w.roundToInt(), h.roundToInt()), bitmapPaint)
    }
}
