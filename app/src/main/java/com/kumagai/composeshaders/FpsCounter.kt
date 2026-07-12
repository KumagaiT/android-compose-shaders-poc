package com.kumagai.composeshaders

import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun FpsCounter(modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }
    var frameTimeMs by remember { mutableDoubleStateOf(0.0) }

    DisposableEffect(Unit) {
        var lastFrameTimeNanos = 0L
        var frameCount = 0
        var lastUpdateNanos = 0L

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameTimeNanos != 0L) {
                    val frameDiff = frameTimeNanos - lastFrameTimeNanos
                    frameTimeMs = frameDiff / 1_000_000.0
                }

                frameCount++
                if (lastUpdateNanos == 0L) {
                    lastUpdateNanos = frameTimeNanos
                } else {
                    val elapsed = frameTimeNanos - lastUpdateNanos
                    if (elapsed >= 500_000_000L) { // Update average FPS every 500ms
                        fps = (frameCount * 1_000_000_000L / elapsed).toInt()
                        frameCount = 0
                        lastUpdateNanos = frameTimeNanos
                    }
                }

                lastFrameTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        Choreographer.getInstance().postFrameCallback(callback)

        onDispose {
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    Text(
        text = "FPS: $fps (${String.format(Locale.US, "%.1f", frameTimeMs)}ms)",
        color = Color.Black,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
