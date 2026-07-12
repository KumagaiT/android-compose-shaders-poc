package com.kumagai.composeshaders.smoke

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun NativeSmokeBackground(
    modifier: Modifier = Modifier,
    smokeColor: Color = Color(0xFFFFD0E0),
    isAnimated: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .smoke(
                implementation = SmokeImplementation.AGSL,
                smokeColor = smokeColor,
                isAnimated = isAnimated
            )
    )
}
