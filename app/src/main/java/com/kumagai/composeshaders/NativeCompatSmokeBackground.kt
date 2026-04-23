package com.kumagai.composeshaders

import android.opengl.GLSurfaceView
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

@Composable
fun NativeCompatSmokeBackground(
    modifier: Modifier = Modifier,
    smokeColor: Color = Color(0xFFFF00E0),
    isAnimated: Boolean = true
) {
    // Mantemos o renderer em um remember para evitar recriação
    val smokeRenderer = remember { SmokeRenderer() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            GLSurfaceView(context).apply {
                // Configuração para GLES 2.0 (mais compatível)
                setEGLContextClientVersion(2)

                // Otimização: solicita um config sem depth ou stencil se não for usar (economiza banda)
                setEGLConfigChooser(8, 8, 8, 8, 0, 0)

                setRenderer(smokeRenderer)

                // Preservar o contexto ajuda na performance de troca de apps
                preserveEGLContextOnPause = true

                renderMode = if (isAnimated) {
                    GLSurfaceView.RENDERMODE_CONTINUOUSLY
                } else {
                    GLSurfaceView.RENDERMODE_WHEN_DIRTY
                }
            }
        },
        update = { view ->
            // Atualiza apenas os valores atômicos no renderer
            smokeRenderer.smokeColorInt = smokeColor.toArgb()
            smokeRenderer.isAnimated = isAnimated

            val targetMode = if (isAnimated) {
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }

            if (view.renderMode != targetMode) {
                view.renderMode = targetMode
            }

            if (!isAnimated) {
                view.requestRender()
            }
        }
    )
}

private class SmokeRenderer : GLSurfaceView.Renderer {
    @Volatile var smokeColorInt: Int = 0
    @Volatile var isAnimated: Boolean = true

    private var startTime: Long = -1
    private var lastPausedTime: Float = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        NativeVisualEngine.initGL()
        startTime = System.currentTimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        NativeVisualEngine.resizeGL(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val currentTime = if (isAnimated) {
            if (startTime == -1L) startTime = System.currentTimeMillis()
            (System.currentTimeMillis() - startTime) / 1000f
        } else {
            if (startTime != -1L) {
                lastPausedTime = (System.currentTimeMillis() - startTime) / 1000f
                startTime = -1L
            }
            lastPausedTime
        }

        NativeVisualEngine.renderGL(smokeColorInt, currentTime)
    }
}
