package com.kumagai.composeshaders

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
    smokeColor: Color = Color(0xFFFFD0E0), // Cor única da fumaça (clara)
    isAnimated: Boolean = false
) {
    // 1. Carrega o shader AGSL
    val shader = remember { RuntimeShader(SMOKE_SHADER_CODE) }

    // 2. Gerencia a animação do tempo se solicitado
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

    // 3. Usa o Canvas do Compose para desenhar o efeito nativo
    Canvas(modifier = modifier.fillMaxSize()) {
        // Configura os uniforms apenas quando mudam
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uTime", time)
        shader.setColorUniform("uSmokeColor", smokeColor.toArgb())

        // Aplica o shader como um RenderEffect (executado nativamente na GPU)
        drawContext.canvas.nativeCanvas.drawPaint(Paint().apply {
            setShader(shader)
        })
    }
}
