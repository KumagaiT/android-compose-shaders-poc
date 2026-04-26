package com.kumagai.composeshaders.smoke

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun NativeSmokeBackground(
    modifier: Modifier = Modifier,
    smokeColor: Color = Color(0xFFFFD0E0), // Single smoke color (light)
    isAnimated: Boolean = false
) {
    // 1. Loads the AGSL shader
    val shader = remember { RuntimeShader(SMOKE_SHADER_CODE) }

    // 2. Manages time animation if requested
    val time = if (isAnimated) {
        val infiniteTransition = rememberInfiniteTransition(label = "SmokeTime")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 200f,
            animationSpec = infiniteRepeatable(animation = tween(100000, easing = LinearEasing)),
            label = "TimeValue",
        ).value
    } else {
        0f
    }

    // 3. Uses Compose Canvas to draw the native effect
    Canvas(modifier = modifier.fillMaxSize()) {
        // Sets uniforms only when they change
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uTime", time)
        shader.setColorUniform("uSmokeColor", smokeColor.toArgb())

        // Applies the shader as a RenderEffect (executed natively on GPU)
        drawContext.canvas.nativeCanvas.drawPaint(Paint().apply {
            setShader(shader)
        })
    }
}
