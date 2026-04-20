package com.kumagai.composeshaders

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.kumagai.composeshaders.NativeVisualEngine.drawSurface
import com.kumagai.composeshaders.NativeVisualEngine.releaseSurface
import com.kumagai.composeshaders.NativeVisualEngine.setSurface

object NativeVisualEngine {
    init {
        System.loadLibrary("myapplication")
    }

    external fun setSurface(surface: Surface?)
    external fun drawSurface(color: Int)
    external fun releaseSurface()
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