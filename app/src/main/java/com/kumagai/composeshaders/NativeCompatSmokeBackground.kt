package com.kumagai.composeshaders

import android.opengl.GLSurfaceView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

object NativeVisualEngine {
    init {
        System.loadLibrary("compose_shaders")
    }

    external fun initGL()
    external fun resizeGL(width: Int, height: Int)
    external fun renderGL(color: Int, time: Float)
}

// Renderer customizável para facilitar a atualização de propriedades de forma thread-safe
private class SmokeRenderer : GLSurfaceView.Renderer {
    var smokeColorInt: Int = 0
    var timeValue: Float = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        NativeVisualEngine.initGL()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        NativeVisualEngine.resizeGL(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        NativeVisualEngine.renderGL(smokeColorInt, timeValue)
    }
}

@Composable
fun NativeCompatSmokeBackground(
    modifier: Modifier = Modifier,
    smokeColor: Color = Color(0xFFFFD0E0),
    isAnimated: Boolean = true
) {
    // 1. Gerencia a animação do tempo se solicitado
    val time = if (isAnimated) {
        val infiniteTransition = rememberInfiniteTransition(label = "SmokeTime")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = infiniteRepeatable(
                animation = tween(100000, easing = LinearEasing)
            ),
            label = "TimeValue",
        ).value
    } else {
        0f
    }

    // 2. Lembra da instância do renderer para podermos atualizar as propriedades
    val smokeRenderer = remember { SmokeRenderer() }

    // 3. Usa o AndroidView para hospedar o GLSurfaceView
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(smokeRenderer)
                
                // RENDERMODE_CONTINUOUSLY para animação fluida (GPU sempre rodando)
                renderMode = if (isAnimated) {
                    GLSurfaceView.RENDERMODE_CONTINUOUSLY
                } else {
                    GLSurfaceView.RENDERMODE_WHEN_DIRTY
                }
            }
        },
        update = { view ->
            // Sincroniza as cores e o tempo com o renderer na Thread de Renderização
            smokeRenderer.smokeColorInt = smokeColor.toArgb()
            smokeRenderer.timeValue = time
            
            // Se não estiver em modo contínuo, solicita um novo frame manualmente
            if (!isAnimated) {
                view.requestRender()
            }
        }
    )
}
