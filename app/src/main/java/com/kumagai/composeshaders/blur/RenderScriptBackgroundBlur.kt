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
 * IMPLEMENTAÇÃO 2: RENDER SCRIPT (Acelerado por Hardware)
 * Usa o ScriptIntrinsicBlur do Android. Extremamente rápido em Android 8-11.
 * Ideal para o Snapdragon 450 por delegar o peso para a GPU.
 */
fun Modifier.renderScriptBackgroundBlur(
    blurRadius: Float = 10f, // RenderScript aceita até 25f
    downsample: Float = 2f,
    overlayColor: Color = Color.White.copy(alpha = 0.20f)
): Modifier = this.composed {
    val context = LocalContext.current
    val view = LocalView.current
    val blurState = remember { BlurState() }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val bitmapPaint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }

    // Gerenciador do Ciclo de Vida do RenderScript
    val rsManager = remember(context) { RenderScriptManager(context) }

    DisposableEffect(view) {
        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            val coords = layoutCoords
            if (coords != null && coords.isAttached && !blurState.isCapturing) {
                val now = System.currentTimeMillis()
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

                    // Executa o desfoque via RenderScript
                    rsManager.blur(bitmap, blurRadius)
                    blurState.lastUpdateTime = now
                }
            }
            true
        }

        view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            blurState.internalBitmap?.recycle()
            rsManager.destroy()
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

/**
 * Gerenciador de ciclo de vida do RenderScript.
 */
private class RenderScriptManager(context: Context) {
    private val rs = RenderScript.create(context)
    private val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    private var input: Allocation? = null
    private var output: Allocation? = null

    fun blur(bitmap: Bitmap, radius: Float) {
        if (input == null || input?.type?.x != bitmap.width || input?.type?.y != bitmap.height) {
            input?.destroy()
            output?.destroy()
            input = Allocation.createFromBitmap(rs, bitmap)
            output = Allocation.createTyped(rs, input?.type)
        }

        input?.copyFrom(bitmap)
        blurScript.setRadius(radius.coerceIn(0f, 25f))
        blurScript.setInput(input)
        blurScript.forEach(output)
        output?.copyTo(bitmap)
    }

    fun destroy() {
        input?.destroy()
        output?.destroy()
        blurScript.destroy()
        rs.destroy()
    }
}
