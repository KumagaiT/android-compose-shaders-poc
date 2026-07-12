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
    internal val animationProgress: State<Float>,
    activeCountState: MutableState<Int> = mutableIntStateOf(0)
) {
    internal var activeCount by activeCountState
}

/**
 * CompositionLocal for ShimmerState to allow subcomponents to use the same shimmer animation
 * without explicit prop drilling.
 */
val LocalShimmerState = compositionLocalOf<ShimmerState?> { null }

@Composable
fun rememberShimmerState(
    durationMillis: Int = 1500,
    easing: Easing = LinearEasing
): ShimmerState {
    val activeCount = remember { mutableIntStateOf(0) }
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerTransition")
    
    // Use targetValue as a way to "pause" or "resume" based on activeCount
    // On many systems, if targetValue == initialValue, the animation doesn't run.
    // However, to be sure we save power, we can use a conditional animation or
    // just let it run if activeCount > 0.
    val progress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (activeCount.intValue > 0) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = easing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerProgress"
    )
    
    return remember { ShimmerState(progress, activeCount) }
}

/**
 * Base modifier for shimmer effects.
 * Tracks global position to allow the "window" effect.
 */
fun Modifier.shimmer(
    state: ShimmerState? = null,
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

@Composable
fun Modifier.shimmer(
    loading: Boolean = true,
    color: Color = Color.LightGray.copy(alpha = 0.3f),
    highlightColor: Color = Color.White.copy(alpha = 0.7f),
    implementation: ShimmerImplementation = ShimmerImplementation.Compose
): Modifier {
    val state = LocalShimmerState.current ?: rememberShimmerState()
    return this.shimmer(
        state = state,
        loading = loading,
        color = color,
        highlightColor = highlightColor,
        implementation = implementation
    )
}

private data class ShimmerElement(
    val state: ShimmerState?,
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
    state: ShimmerState?,
    loading: Boolean,
    var color: Color,
    var highlightColor: Color,
    var implementation: ShimmerImplementation
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    var state: ShimmerState? = state
        set(value) {
            if (field != value) {
                if (isAttached && loading) {
                    field?.activeCount = (field?.activeCount ?: 0) - 1
                    value?.activeCount = (value.activeCount) + 1
                }
                field = value
                updateShader()
            }
        }

    var loading: Boolean = loading
        set(value) {
            if (field != value) {
                if (isAttached) {
                    if (value) {
                        state?.activeCount = (state?.activeCount ?: 0) + 1
                    } else {
                        state?.activeCount = (state?.activeCount ?: 0) - 1
                    }
                }
                field = value
            }
        }

    private var shader: RuntimeShader? = null
    private var globalX: Float = 0f
    private var globalY: Float = 0f

    override fun onAttach() {
        if (loading) {
            state?.activeCount = (state?.activeCount ?: 0) + 1
        }
    }

    override fun onDetach() {
        if (loading) {
            state?.activeCount = (state?.activeCount ?: 0) - 1
        }
    }

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
            globalX = offset.x
            globalY = offset.y
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val currentState = state
        if (loading && currentState != null) {
            when (implementation) {
                ShimmerImplementation.Compose -> {
                    drawComposeShimmer(currentState, globalX, globalY, color, highlightColor)
                }
                ShimmerImplementation.AGSL, ShimmerImplementation.Native -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                        drawAgslShimmer(shader!!, currentState, globalX, globalY, color, highlightColor)
                    } else {
                        drawComposeShimmer(currentState, globalX, globalY, color, highlightColor)
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
