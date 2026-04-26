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
 * IMPLEMENTAÇÃO 3: MODERN COMPOSE (API 31+)
 * 
 * Utiliza RenderNode e RenderEffect para um blur nativo na RenderThread.
 */
fun Modifier.modernBackgroundBlur(
    blurRadius: Float = 10f,
    downsample: Float = 2f,
    overlayColor: Color = Color.White.copy(alpha = 0.20f)
): Modifier = this.composed {
    val view = LocalView.current
    val blurState = remember { BlurState() }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    
    val blurRenderNode = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RenderNode("FrostyRenderNode")
        } else null
    }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnPreDrawListener {
            val coords = layoutCoords
            if (coords != null && coords.isAttached && !blurState.isCapturing) {
                val now = System.currentTimeMillis()
                if (now - blurState.lastUpdateTime < 16) return@OnPreDrawListener true

                val width = coords.size.width.toFloat()
                val height = coords.size.height.toFloat()
                
                if (width > 0 && height > 0) {
                    val bw = (width / downsample).roundToInt().coerceAtLeast(1)
                    val bh = (height / downsample).roundToInt().coerceAtLeast(1)

                    if (blurState.internalBitmap?.width != bw || blurState.internalBitmap?.height != bh) {
                        blurState.internalBitmap?.recycle()
                        blurState.internalBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    }

                    val bitmap = blurState.internalBitmap!!
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
                    
                    blurState.lastUpdateTime = now
                    blurState.tick++
                }
            }
            true
        }
        
        view.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(listener)
            blurState.internalBitmap?.recycle()
        }
    }

    applyCommonBlurEffects(
        layoutCoords = layoutCoords,
        blurState = blurState,
        overlayColor = overlayColor,
        onPositioned = { layoutCoords = it }
    ) { canvas, w, h ->
        val bitmap = blurState.internalBitmap ?: return@applyCommonBlurEffects
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRenderNode != null) {
            blurRenderNode.setPosition(0, 0, w.toInt(), h.toInt())
            val effect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
            blurRenderNode.setRenderEffect(effect)

            val recordingCanvas = blurRenderNode.beginRecording()
            recordingCanvas.drawBitmap(bitmap, null, Rect(0, 0, w.toInt(), h.toInt()), null)
            blurRenderNode.endRecording()

            canvas.drawRenderNode(blurRenderNode)
        } else {
            canvas.drawBitmap(bitmap, null, Rect(0, 0, w.roundToInt(), h.roundToInt()), null)
        }
    }
}
