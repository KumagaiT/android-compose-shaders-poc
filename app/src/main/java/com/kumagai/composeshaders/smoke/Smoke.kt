package com.kumagai.composeshaders.smoke

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.sqrt

enum class SmokeImplementation {
    AGSL,    // Android 13+ GPU
    Compose  // CPU Pixel Loop
}

/**
 * Unified smoke modifier using Modifier.Node.
 */
fun Modifier.smoke(
    implementation: SmokeImplementation,
    smokeColor: Color = Color(0xFFFFD0E0),
    isAnimated: Boolean = true
): Modifier = this then SmokeElement(
    implementation = implementation,
    smokeColor = smokeColor,
    isAnimated = isAnimated
)

private data class SmokeElement(
    val implementation: SmokeImplementation,
    val smokeColor: Color,
    val isAnimated: Boolean
) : ModifierNodeElement<SmokeNode>() {
    override fun create(): SmokeNode = SmokeNode(
        implementation = implementation,
        smokeColor = smokeColor,
        isAnimated = isAnimated
    )

    override fun update(node: SmokeNode) {
        node.implementation = implementation
        node.smokeColor = smokeColor
        node.isAnimated = isAnimated
        node.updateAnimation()
        // No manual invalidateDraw needed here as DrawModifierNode handles auto-invalidation by default
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "smoke"
        properties["implementation"] = implementation
        properties["smokeColor"] = smokeColor
        properties["isAnimated"] = isAnimated
    }
}

internal class SmokeNode(
    var implementation: SmokeImplementation,
    var smokeColor: Color,
    var isAnimated: Boolean
) : Modifier.Node(), DrawModifierNode {

    private var timeState = Animatable(0f)
    private var animationJob: Job? = null
    
    // AGSL
    private var shader: RuntimeShader? = null
    private val shaderPaint = Paint()

    // Compose/CPU
    private var cpuBitmap: Bitmap? = null
    private val resolutionScale = 0.35f
    private var lastWidth = 0
    private var lastHeight = 0
    private var pixels: IntArray? = null

    override fun onAttach() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && implementation == SmokeImplementation.AGSL) {
            shader = RuntimeShader(SMOKE_SHADER_CODE)
        }
        updateAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
        cpuBitmap?.recycle()
        cpuBitmap = null
    }

    fun updateAnimation() {
        animationJob?.cancel()
        if (isAnimated) {
            animationJob = coroutineScope.launch {
                timeState.snapTo(0f)
                timeState.animateTo(
                    targetValue = 200f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(100000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                ) {
                    // Manual invalidation during animation
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        when (implementation) {
            SmokeImplementation.AGSL -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                    drawAgslSmoke()
                } else {
                    drawComposeSmoke()
                }
            }
            SmokeImplementation.Compose -> {
                drawComposeSmoke()
            }
        }
        drawContent()
    }

    private fun ContentDrawScope.drawAgslSmoke() {
        val s = shader ?: return
        s.setFloatUniform("uResolution", size.width, size.height)
        s.setFloatUniform("uTime", timeState.value)
        s.setColorUniform("uSmokeColor", smokeColor.toArgb())

        drawIntoCanvas { canvas ->
            shaderPaint.shader = s
            canvas.nativeCanvas.drawPaint(shaderPaint)
        }
    }

    private fun ContentDrawScope.drawComposeSmoke() {
        val w = (size.width * resolutionScale).toInt().coerceAtLeast(1)
        val h = (size.height * resolutionScale).toInt().coerceAtLeast(1)

        if (cpuBitmap == null || lastWidth != w || lastHeight != h) {
            cpuBitmap?.recycle()
            cpuBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            pixels = IntArray(w * h)
            lastWidth = w
            lastHeight = h
        }

        val bitmap = cpuBitmap ?: return
        val currentPixels = pixels ?: return
        val currentTime = timeState.value
        val aspect = w.toFloat() / h.toFloat()

        val smokeColorArgb = smokeColor.toArgb()
        val rS = (smokeColorArgb shr 16 and 0xFF)
        val gS = (smokeColorArgb shr 8 and 0xFF)
        val bS = (smokeColorArgb and 0xFF)

        // Same movement directions as in ComposeSmokeBackground.kt
        val velX = floatArrayOf(0.12f, -0.08f)
        val velY = floatArrayOf(0.08f, 0.15f)

        for (y in 0 until h) {
            val py = y.toFloat() / h
            val rowOffset = y * w
            for (x in 0 until w) {
                val px = (x.toFloat() / w) * aspect

                var v = 0.0f
                var a = 0.5f
                var tx = px * 2.5f
                var ty = py * 2.5f

                for (i in 0 until 2) {
                    val noiseX = tx + currentTime * velX[i]
                    val noiseY = ty + currentTime * velY[i]
                    val ix = floor(noiseX).toInt()
                    val iy = floor(noiseY).toInt()
                    val fx = noiseX - floor(noiseX)
                    val fy = noiseY - floor(noiseY)
                    val ux = fx * fx * (3.0f - 2.0f * fx)
                    val uy = fy * fy * (3.0f - 2.0f * fy)

                    val n00 = ((ix * 1619 + iy * 31337).let { n -> (n xor (n ushr 16)) * 0x45d9f3b }.let { n -> (n xor (n ushr 16)) and 0x7FFFFFFF }) / 2147483647f
                    val n10 = (((ix + 1) * 1619 + iy * 31337).let { n -> (n xor (n ushr 16)) * 0x45d9f3b }.let { n -> (n xor (n ushr 16)) and 0x7FFFFFFF }) / 2147483647f
                    val n01 = ((ix * 1619 + (iy + 1) * 31337).let { n -> (n xor (n ushr 16)) * 0x45d9f3b }.let { n -> (n xor (n ushr 16)) and 0x7FFFFFFF }) / 2147483647f
                    val n11 = (((ix + 1) * 1619 + (iy + 1) * 31337).let { n -> (n xor (n ushr 16)) * 0x45d9f3b }.let { n -> (n xor (n ushr 16)) and 0x7FFFFFFF }) / 2147483647f

                    v += a * (n00 + ux * (n10 - n00) + uy * (n01 - n00 + ux * (n00 - n10 + n11 - n01)))

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

                currentPixels[rowOffset + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(currentPixels, 0, w, 0, 0, w, h)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(bitmap, null, Rect(0, 0, size.width.toInt(), size.height.toInt()), null)
        }
    }
}
