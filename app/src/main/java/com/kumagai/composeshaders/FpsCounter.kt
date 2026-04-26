package com.kumagai.composeshaders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
        color = Color.Black,
        modifier = modifier.background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
