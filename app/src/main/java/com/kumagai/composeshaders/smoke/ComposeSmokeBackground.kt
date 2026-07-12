package com.kumagai.composeshaders.smoke

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ComposeSmokeBackground(
    modifier: Modifier = Modifier,
    smokeColor: Color = Color(0xFFFF00E0),
    isAnimated: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .smoke(
                implementation = SmokeImplementation.Compose,
                smokeColor = smokeColor,
                isAnimated = isAnimated
            )
    )
}
