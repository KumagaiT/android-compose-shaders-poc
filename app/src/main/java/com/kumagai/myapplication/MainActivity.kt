package com.kumagai.myapplication

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kumagai.myapplication.NativeVisualEngine.drawSurface
import com.kumagai.myapplication.NativeVisualEngine.releaseSurface
import com.kumagai.myapplication.NativeVisualEngine.setSurface
import kotlinx.coroutines.isActive

object NativeVisualEngine {
    init {
        System.loadLibrary("myapplication")
    }

    external fun setSurface(surface: Surface?)
    external fun drawSurface(color: Int)
    external fun releaseSurface()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    NativeSmokeBackground(isAnimated = true)
                } else {
                    NativeCompatSmokeBackground()
                }

                FpsCounter(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun FpsCounter(modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastUpdate by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                frameCount++
                if (lastUpdate == 0L) {
                    lastUpdate = nanos
                } else {
                    val elapsed = nanos - lastUpdate
                    if (elapsed >= 500_000_000L) {
                        fps = (frameCount * 1_000_000_000L / elapsed).toInt()
                        frameCount = 0
                        lastUpdate = nanos
                    }
                }
            }
        }
    }

    Text(
        text = "FPS: $fps",
        color = Color.Green,
        modifier = modifier
    )
}

@Composable
fun NativeCompatSmokeBackground(
    modifier: Modifier = Modifier,
    smokeColor: Color = Color(0xFFFFD0E0)
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        setSurface(holder.surface)
                        // Desenha apenas uma vez ao criar a surface
                        drawSurface(smokeColor.toArgb())
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        // Redesenha se o tamanho mudar (ex: rotação)
                        drawSurface(smokeColor.toArgb())
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        releaseSurface()
                    }
                })
            }
        }
    )
}

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
