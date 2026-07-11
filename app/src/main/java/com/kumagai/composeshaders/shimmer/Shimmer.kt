package com.kumagai.composeshaders.shimmer

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.*
import android.graphics.RuntimeShader
import android.os.Build

internal const val SHIMMER_SHADER_CODE = """
    uniform float2 uResolution;
    uniform float2 uOffset;
    uniform float uProgress;
    layout(color) uniform float4 uColor;
    layout(color) uniform float4 uHighlightColor;

    half4 main(float2 fragCoord) {
        float2 globalCoord = fragCoord + uOffset;
        
        float totalRange = 2400.0;
        float shimmerWidth = 800.0;
        float currentPos = uProgress * totalRange - 400.0;
        
        // Softer diagonal line: x + y*0.4
        float d = (globalCoord.x + globalCoord.y * 0.4) - currentPos;
        
        // Use a smoother distribution (Gaussian-like) instead of linear smoothstep
        float normalizedDist = abs(d) / (shimmerWidth * 0.5);
        float intensity = exp(-3.0 * normalizedDist * normalizedDist);
        
        intensity = clamp(intensity, 0.0, 1.0);
        
        return mix(uColor, uHighlightColor, intensity);
    }
"""

/**
 * Shared state for shimmer animation to ensure all "windows" are synchronized.
 */
@Stable
class ShimmerState(
    internal val animationProgress: State<Float>
) {
    // Non-state variables to avoid recomposition while scrolling
    internal var globalX: Float = 0f
    internal var globalY: Float = 0f
}

@Composable
fun rememberShimmerState(
    durationMillis: Int = 1500,
    easing: Easing = LinearEasing
): ShimmerState {
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerTransition")
    val progress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = easing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerProgress"
    )
    return remember { ShimmerState(progress) }
}

/**
 * Base modifier for shimmer effects.
 * Tracks global position to allow the "window" effect.
 */
fun Modifier.shimmer(
    state: ShimmerState,
    loading: Boolean = true,
    color: Color = Color.LightGray.copy(alpha = 0.3f),
    highlightColor: Color = Color.White.copy(alpha = 0.7f),
    implementation: ShimmerImplementation = ShimmerImplementation.Compose
): Modifier = this then ShimmerElement(
    state = state,
    loading = loading,
    color = color,
    highlightColor = highlightColor,
    implementation = implementation
)

private data class ShimmerElement(
    val state: ShimmerState,
    val loading: Boolean,
    val color: Color,
    val highlightColor: Color,
    val implementation: ShimmerImplementation
) : ModifierNodeElement<ShimmerNode>() {
    override fun create(): ShimmerNode = ShimmerNode(
        state = state,
        loading = loading,
        color = color,
        highlightColor = highlightColor,
        implementation = implementation
    )

    override fun update(node: ShimmerNode) {
        node.state = state
        node.loading = loading
        node.color = color
        node.highlightColor = highlightColor
        node.implementation = implementation
        node.updateShader()
        node.invalidateDraw()
    }
}

private class ShimmerNode(
    var state: ShimmerState,
    var loading: Boolean,
    var color: Color,
    var highlightColor: Color,
    var implementation: ShimmerImplementation
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var shader: RuntimeShader? = null

    init {
        updateShader()
    }

    fun updateShader() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shader == null && (implementation == ShimmerImplementation.AGSL || implementation == ShimmerImplementation.Native)) {
                shader = RuntimeShader(SHIMMER_SHADER_CODE)
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            val offset = coordinates.localToRoot(Offset.Zero)
            state.globalX = offset.x
            state.globalY = offset.y
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        if (loading) {
            when (implementation) {
                ShimmerImplementation.Compose -> {
                    drawComposeShimmer(state, color, highlightColor)
                }
                ShimmerImplementation.AGSL, ShimmerImplementation.Native -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                        drawAgslShimmer(shader!!, state, color, highlightColor)
                    } else {
                        drawComposeShimmer(state, color, highlightColor)
                    }
                }
            }
        }
        drawContent()
    }
}

enum class ShimmerImplementation {
    Compose, // Standard Brush-based
    AGSL,    // Android 13+ RuntimeShader
    Native   // NDK/GLES (similar to Smoke effect fallback)
}
