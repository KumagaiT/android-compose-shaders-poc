package com.kumagai.composeshaders.shimmer

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun DrawScope.drawAgslShimmer(
    shader: RuntimeShader,
    state: ShimmerState,
    offsetX: Float,
    offsetY: Float,
    color: Color,
    highlightColor: Color
) {
    shader.setFloatUniform("uResolution", size.width, size.height)
    shader.setFloatUniform("uOffset", offsetX, offsetY)
    shader.setFloatUniform("uProgress", state.animationProgress.value)
    shader.setColorUniform("uColor", color.toArgb())
    shader.setColorUniform("uHighlightColor", highlightColor.toArgb())
    
    drawRect(brush = ShaderBrush(shader))
}
