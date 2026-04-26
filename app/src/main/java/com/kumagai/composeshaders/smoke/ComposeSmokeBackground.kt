package com.kumagai.composeshaders.smoke

import android.graphics.Rect
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap

@Composable
fun ComposeSmokeBackground(
    modifier: Modifier = Modifier,
    smokeColor: Color = Color(0xFFFF00E0),
    isAnimated: Boolean = true
) {
    val resolutionScale = 0.35f

    val time = if (isAnimated) {
        val infiniteTransition = rememberInfiniteTransition(label = "SmokeTime")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = infiniteRepeatable(animation = tween(100000, easing = LinearEasing)),
            label = "TimeValue",
        ).value
    } else {
        0f
    }

    val smokeColorArgb = smokeColor.toArgb()
    val rS = (smokeColorArgb shr 16 and 0xFF)
    val gS = (smokeColorArgb shr 8 and 0xFF)
    val bS = (smokeColorArgb and 0xFF)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = (constraints.maxWidth * resolutionScale).toInt().coerceAtLeast(1)
        val height = (constraints.maxHeight * resolutionScale).toInt().coerceAtLeast(1)

        val bitmap = remember(width, height) {
            createBitmap(width, height)
        }
        val pixels = remember(width, height) { IntArray(width * height) }
        val dstRect = remember(constraints.maxWidth, constraints.maxHeight) {
            Rect(0, 0, constraints.maxWidth, constraints.maxHeight)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentTime = time
            val aspect = width.toFloat() / height.toFloat()

            // Direções de movimento diferentes para cada oitava (Quebra a linearidade)
            val velX = floatArrayOf(0.12f, -0.08f)
            val velY = floatArrayOf(0.08f, 0.15f)

            for (y in 0 until height) {
                val py = y.toFloat() / height
                val rowOffset = y * width
                for (x in 0 until width) {
                    val px = (x.toFloat() / width) * aspect

                    var v = 0.0f
                    var a = 0.5f
                    
                    // Coordenadas iniciais
                    var tx = px * 2.5f
                    var ty = py * 2.5f

                    for (i in 0 until 2) {
                        // Aplica movimento específico da oitava ANTES do ruído
                        val noiseX = tx + currentTime * velX[i]
                        val noiseY = ty + currentTime * velY[i]
                        
                        val ix = floor(noiseX).toInt()
                        val iy = floor(noiseY).toInt()
                        val fx = noiseX - floor(noiseX)
                        val fy = noiseY - floor(noiseY)

                        val ux = fx * fx * (3.0f - 2.0f * fx)
                        val uy = fy * fy * (3.0f - 2.0f * fy)

                        // Hash Inline
                        val n00 = ((ix * 1619 + iy * 31337).let { n -> (n xor (n ushr 16)) * 0x45d9f3b }.let { n -> (n xor (n ushr 16)) and 0x7FFFFFFF }) / 2147483647f
                        val n10 = (((ix + 1) * 1619 + iy * 31337).let { n -> (n xor (n ushr 16)) * 0x45d9f3b }.let { n -> (n xor (n ushr 16)) and 0x7FFFFFFF }) / 2147483647f
                        val n01 = ((ix * 1619 + (iy + 1) * 31337).let { n -> (n xor (n ushr 16)) * 0x45d9f3b }.let { n -> (n xor (n ushr 16)) and 0x7FFFFFFF }) / 2147483647f
                        val n11 = (((ix + 1) * 1619 + (iy + 1) * 31337).let { n -> (n xor (n ushr 16)) * 0x45d9f3b }.let { n -> (n xor (n ushr 16)) and 0x7FFFFFFF }) / 2147483647f

                        v += a * (n00 + ux * (n10 - n00) + uy * (n01 - n00 + ux * (n00 - n10 + n11 - n01)))

                        // Rotação e Escala para a próxima oitava (Scrambling)
                        val rx = 0.8f * tx + 0.6f * ty
                        val ry = -0.6f * tx + 0.8f * ty
                        tx = rx * 2.2f + 7.3f
                        ty = ry * 2.2f + 7.3f
                        a *= 0.48f
                    }

                    val dist = sqrt((px - 0.5f * aspect) * (px - 0.5f * aspect) + (py - 0.5f) * (py - 0.5f))
                    val vignette = (1.0f - ((dist - 0.2f) / 0.6f).coerceIn(0f, 1f)).let { it * it * (3f - 2f * it) }
                    val intensity = (v * vignette).coerceIn(0f, 1f)

                    val r = (255 * (1f - intensity) + rS * intensity).toInt()
                    val g = (255 * (1f - intensity) + gS * intensity).toInt()
                    val b = (255 * (1f - intensity) + bS * intensity).toInt()

                    pixels[rowOffset + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(bitmap, null, dstRect, null)
            }
        }
    }
}
