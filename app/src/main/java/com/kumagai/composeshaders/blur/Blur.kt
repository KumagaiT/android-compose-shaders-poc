package com.kumagai.composeshaders.blur

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
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
import kotlin.math.roundToInt


class BlurState {
    var isCapturing = false
    var internalBitmap: Bitmap? = null
    var lastUpdateTime: Long = 0
    var tick by mutableLongStateOf(0L) // Usado para forçar a atualização do desenho no Compose
}



/**
 * Lógica de desenho e efeitos visuais comum às duas implementações.
 */
fun Modifier.applyCommonBlurEffects(
    layoutCoords: LayoutCoordinates?,
    blurState: BlurState,
    bitmapPaint: android.graphics.Paint,
    overlayColor: Color,
    onPositioned: (LayoutCoordinates) -> Unit
): Modifier = this.onGloballyPositioned { onPositioned(it) }
    .graphicsLayer { alpha = if (blurState.isCapturing) 0f else 1f }
    .drawBehind {
        val bitmap = blurState.internalBitmap ?: return@drawBehind
        // Acessar o tick garante que o Compose re-execute este bloco quando o bitmap mudar
        val _tick = blurState.tick

        val w = size.width
        val h = size.height

        drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas
            val checkpoint = nativeCanvas.saveLayer(0f, 0f, w, h, null)

            val dstRect = Rect(0, 0, w.roundToInt(), h.roundToInt())
            nativeCanvas.drawBitmap(bitmap, null, dstRect, bitmapPaint)

            // Máscara de Transparência (Fusão Suave)
            val maskPaint = android.graphics.Paint().apply {
                shader = LinearGradient(
                    0f, h * 0.65f, 0f, h,
                    -0x1, 0x00FFFFFF, Shader.TileMode.CLAMP
                )
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            nativeCanvas.drawRect(0f, 0f, w, h, maskPaint)
            nativeCanvas.restoreToCount(checkpoint)

            // Vidro e Brilho de Borda
            drawRect(color = overlayColor)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.12f),
                    0.25f to Color.Transparent
                )
            )
        }
    }