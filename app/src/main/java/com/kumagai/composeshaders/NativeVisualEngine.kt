package com.kumagai.composeshaders

object NativeVisualEngine {
    init {
        System.loadLibrary("compose_shaders")
    }

    external fun initGL()
    external fun resizeGL(width: Int, height: Int)
    external fun renderGL(color: Int, time: Float)
    external fun blurBitmap(bitmap: android.graphics.Bitmap, radius: Int)
}
