package com.kumagai.composeshaders.shimmer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope

fun DrawScope.drawComposeShimmer(
    state: ShimmerState,
    offsetX: Float,
    offsetY: Float,
    color: Color,
    highlightColor: Color
) {
    val progress = state.animationProgress.value
    
    // We define the shimmer movement in a global space (e.g. 0 to 2400px)
    val totalRange = 2400f
    val shimmerWidth = 800f
    
    val currentGlobalPos = progress * totalRange - 400.0f
    
    // Convert global center to local center using non-state coordinates from state
    val localCenterX = currentGlobalPos - offsetX - (offsetY * 0.4f)
    
    val startX = localCenterX - shimmerWidth / 2
    val endX = localCenterX + shimmerWidth / 2
    
    val brush = Brush.linearGradient(
        colors = listOf(color, highlightColor, color),
        start = Offset(startX, 0f),
        end = Offset(endX, shimmerWidth * 0.4f),
        tileMode = TileMode.Clamp
    )
    
    drawRect(brush = brush)
}
