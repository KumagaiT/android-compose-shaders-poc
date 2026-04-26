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
    var tick by mutableLongStateOf(0L) // Usado para forçar a atualização do desenho no Compose
}



/**
 * Lógica de desenho e efeitos visuais comum a todas as implementações.
 * Resolve o efeito de "adesivo colado" com uma máscara 360 e rim light.
 * O parâmetro [drawBlur] permite que cada implementação defina como o fundo borrado é desenhado.
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
            
            // 1. Camada isolada para o fundo borrado + máscara
            val checkpoint = nativeCanvas.saveLayer(0f, 0f, w, h, null)
            
            // 2. Desenha o fundo borrado (específico de cada implementação)
            drawBlur(nativeCanvas, w, h)

            // 3. Desenha Tint, Rim Light e Borda dentro da camada para que sofram a máscara
            // Tinta de preenchimento (Overlay Color)
            val tintPaint = android.graphics.Paint().apply { 
                color = overlayColor.toArgb() 
                style = android.graphics.Paint.Style.FILL
            }
            nativeCanvas.drawRect(0f, 0f, w, h, tintPaint)

            // Rim Light superior (simula incidência de luz no topo)
            val rimPaint = android.graphics.Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h * 0.2f,
                    intArrayOf(Color.White.copy(alpha = 0.25f).toArgb(), 0x00FFFFFF),
                    null, Shader.TileMode.CLAMP)
            }
            nativeCanvas.drawRect(0f, 0f, w, h, rimPaint)

            // Borda física (Stroke) com fade para não cortar seco na base
            val strokePaint = android.graphics.Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h,
                    intArrayOf(Color.White.copy(alpha = 0.35f).toArgb(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.85f), Shader.TileMode.CLAMP)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.dp.toPx()
            }
            nativeCanvas.drawRect(0f, 0f, w, h, strokePaint)

            // 4. Máscara de Vinheta 360 (Resolve o aspecto de adesivo)
            val maskPaint = android.graphics.Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            
            // Vertical: Suaviza Topo e Base (Fade progressivo)
            maskPaint.shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(-0x1, -0x1, 0x00FFFFFF),
                floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
            nativeCanvas.drawRect(0f, 0f, w, h, maskPaint)

            // Horizontal: Suaviza Laterais (Integração lateral)
//            maskPaint.shader = LinearGradient(0f, 0f, w, 0f,
//                intArrayOf(0x00FFFFFF, -0x1, -0x1, 0x00FFFFFF),
//                floatArrayOf(0f, 0.08f, 0.92f, 1f), Shader.TileMode.CLAMP)
//            nativeCanvas.drawRect(0f, 0f, w, h, maskPaint)
            
            nativeCanvas.restoreToCount(checkpoint)
        }
    }