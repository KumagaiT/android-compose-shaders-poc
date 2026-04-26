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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/**
 * IMPLEMENTAÇÃO 3: MODERN COMPOSE (API 31+)
 * 
 * Melhora a transição e atualização do efeito. Adicionamos um "tick" de estado
 * para garantir que o Compose re-renderize o conteúdo borrado em tempo real.
 */
fun Modifier.modernBackgroundBlur(
    blurRadius: Float = 16f,
    downsample: Float = 2f,
    overlayColor: Color = Color.White.copy(alpha = 0.12f)
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
                    // O pulo do gato: incrementamos o tick para notificar o Compose
                    // de que o bitmap interno mudou e ele precisa re-desenhar.
                    blurState.tick++
                }
            }
            true
        }
        
        view.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(listener)
            blurState.internalBitmap?.recycle()
            blurState.internalBitmap = null
        }
    }

    this.onGloballyPositioned { layoutCoords = it }
        .graphicsLayer {
            alpha = if (blurState.isCapturing) 0f else 1f
        }
        .drawWithContent {
            // Ler o tick aqui cria uma dependência de estado no Compose
            val _unusedTick = blurState.tick
            val bitmap = blurState.internalBitmap ?: run { drawContent(); return@drawWithContent }

            val w = size.width
            val h = size.height

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRenderNode != null) {
                    blurRenderNode.setPosition(0, 0, w.toInt(), h.toInt())

                    val blurEffect = RenderEffect.createBlurEffect(
                        blurRadius, blurRadius, Shader.TileMode.CLAMP
                    )
                    blurRenderNode.setRenderEffect(blurEffect)

                    val recordingCanvas = blurRenderNode.beginRecording()
                    recordingCanvas.drawBitmap(bitmap, null, Rect(0, 0, w.toInt(), h.toInt()), null)
                    blurRenderNode.endRecording()

                    nativeCanvas.drawRenderNode(blurRenderNode)
                } else {
                    nativeCanvas.drawBitmap(bitmap, null, Rect(0, 0, w.toInt(), h.toInt()), null)
                }

                // Ajuste de Transição: Suavizando o "Adesivo Chapado"
                // Aumentamos o gradiente para uma transição mais longa e orgânica
                val checkpoint = nativeCanvas.saveLayer(0f, 0f, w, h, null)
                val maskPaint = android.graphics.Paint().apply {
                    shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, h,
                        intArrayOf(-0x1, -0x1, 0x00FFFFFF), // Cor sólida até 50%, depois fade
                        floatArrayOf(0f, 0.4f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                }
                nativeCanvas.drawRect(0f, 0f, w, h, maskPaint)
                nativeCanvas.restoreToCount(checkpoint)
            }

            // Glass Polish (Tint leve e Rim Light no topo)
            drawRect(color = overlayColor)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.15f),
                    0.15f to Color.Transparent
                )
            )

            drawContent()
        }
}
