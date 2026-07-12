package com.kumagai.composeshaders.blur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.*
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class BlurImplementation {
    Legacy,       // NDK StackBlur
    RenderScript, // Legacy RS GPU
    Modern        // API 31+ RenderNode
}

@Stable
class BlurState {
    var isCapturing = false
    var internalBitmap: Bitmap? = null
    var lastUpdateTime: Long = 0
    var tick by mutableLongStateOf(0L)
}

/**
 * Unified blur modifier using Modifier.Node for better performance.
 */
fun Modifier.backgroundBlur(
    implementation: BlurImplementation,
    blurRadius: Float = 10f,
    downsample: Float = 2f,
    overlayColor: Color = Color.White.copy(alpha = 0.20f)
): Modifier = this then BlurElement(
    implementation = implementation,
    blurRadius = blurRadius,
    downsample = downsample,
    overlayColor = overlayColor
)

private data class BlurElement(
    val implementation: BlurImplementation,
    val blurRadius: Float,
    val downsample: Float,
    val overlayColor: Color
) : ModifierNodeElement<BlurNode>() {
    override fun create(): BlurNode = BlurNode(
        implementation = implementation,
        blurRadius = blurRadius,
        downsample = downsample,
        overlayColor = overlayColor
    )

    override fun update(node: BlurNode) {
        node.implementation = implementation
        node.blurRadius = blurRadius
        node.downsample = downsample
        node.overlayColor = overlayColor
        node.updateEngines()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "backgroundBlur"
        properties["implementation"] = implementation
        properties["blurRadius"] = blurRadius
        properties["downsample"] = downsample
        properties["overlayColor"] = overlayColor
    }
}

internal class BlurNode(
    var implementation: BlurImplementation,
    var blurRadius: Float,
    var downsample: Float,
    var overlayColor: Color
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private val blurState = BlurState()
    private var layoutCoords: LayoutCoordinates? = null
    
    // Engine specific state
    private var rsManager: RenderScriptManager? = null
    private var blurRenderNode: RenderNode? = null
    private val bitmapPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        val coords = layoutCoords
        val view = currentValueOf(LocalView)
        if (coords != null && coords.isAttached && !blurState.isCapturing) {
            val now = System.currentTimeMillis()
            if (now - blurState.lastUpdateTime < 16) return@OnPreDrawListener true

            val width = coords.size.width.toFloat()
            val height = coords.size.height.toFloat()
            
            if (width > 0 && height > 0) {
                val bw = (width / downsample).roundToInt().coerceAtLeast(1)
                val bh = (height / downsample).roundToInt().coerceAtLeast(1)

                if (blurState.internalBitmap?.width != bw || blurState.internalBitmap?.height != bh) {
                    blurState.internalBitmap?.recycle()
                    blurState.internalBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                }

                val bitmap = blurState.internalBitmap!!
                val canvas = android.graphics.Canvas(bitmap)
                val pos = coords.localToWindow(Offset.Zero)

                canvas.save()
                canvas.scale(1f / downsample, 1f / downsample)
                canvas.translate(-pos.x, -pos.y)
                canvas.clipRect(pos.x, pos.y, pos.x + width, pos.y + height)
                
                try {
                    blurState.isCapturing = true
                    view.rootView.draw(canvas)
                } catch (e: Exception) {
                } finally {
                    blurState.isCapturing = false
                }
                canvas.restore()
                
                // Post-processing blur
                processBlur(bitmap)
                
                blurState.lastUpdateTime = now
                blurState.tick++
                invalidateDraw()
            }
        }
        true
    }

    override fun onAttach() {
        updateEngines()
        val view = currentValueOf(LocalView)
        view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    fun updateEngines() {
        if (isAttached) {
            val view = currentValueOf(LocalView)
            
            // RenderScript
            if (implementation == BlurImplementation.RenderScript) {
                if (rsManager == null) {
                    rsManager = RenderScriptManager(view.context)
                }
            } else {
                rsManager?.destroy()
                rsManager = null
            }

            // Modern RenderNode
            if (implementation == BlurImplementation.Modern && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (blurRenderNode == null) {
                    blurRenderNode = RenderNode("FrostyRenderNode")
                }
            } else {
                blurRenderNode = null
            }
        }
    }

    override fun onDetach() {
        val view = currentValueOf(LocalView)
        view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        blurState.internalBitmap?.recycle()
        blurState.internalBitmap = null
        
        rsManager?.destroy()
        rsManager = null
        blurRenderNode = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        layoutCoords = coordinates
    }

    private fun processBlur(bitmap: Bitmap) {
        when (implementation) {
            BlurImplementation.Legacy -> {
                com.kumagai.composeshaders.NativeVisualEngine.blurBitmap(bitmap, blurRadius.toInt())
            }
            BlurImplementation.RenderScript -> {
                rsManager?.blur(bitmap, blurRadius)
            }
            BlurImplementation.Modern -> {
                // Modern blur is applied during draw using RenderEffect, nothing to do here
            }
        }
    }

    override fun ContentDrawScope.draw() {
        // If capturing, we draw nothing (or content only) to avoid recursive capturing
        if (blurState.isCapturing) {
            drawContent()
            return
        }

        val bitmap = blurState.internalBitmap
        if (bitmap == null) {
            drawContent()
            return
        }

        val w = size.width
        val h = size.height

        drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas
            
            // 1. Isolated layer for blurred background + mask
            val checkpoint = nativeCanvas.saveLayer(0f, 0f, w, h, null)
            
            // 2. Draws the blurred background
            drawBlurredBackground(nativeCanvas, bitmap, w, h)

            // 3. Draw Polish (Tint, Rim Light, Stroke)
            drawPolish(nativeCanvas, w, h)

            // 4. 360° Vignette Mask
            drawVignetteMask(nativeCanvas, w, h)
            
            nativeCanvas.restoreToCount(checkpoint)
        }
        
        drawContent()
    }

    private fun drawBlurredBackground(canvas: android.graphics.Canvas, bitmap: Bitmap, w: Float, h: Float) {
        if (implementation == BlurImplementation.Modern && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val node = blurRenderNode ?: return
            node.setPosition(0, 0, w.toInt(), h.toInt())
            val effect = android.graphics.RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
            node.setRenderEffect(effect)

            val recordingCanvas = node.beginRecording()
            recordingCanvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, w.toInt(), h.toInt()), null)
            node.endRecording()

            canvas.drawRenderNode(node)
        } else {
            canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, w.roundToInt(), h.roundToInt()), bitmapPaint)
        }
    }

    private fun drawPolish(nativeCanvas: android.graphics.Canvas, w: Float, h: Float) {
        // Fill tint
        val tintPaint = android.graphics.Paint().apply { 
            color = overlayColor.toArgb() 
            style = android.graphics.Paint.Style.FILL
        }
        nativeCanvas.drawRect(0f, 0f, w, h, tintPaint)

        // Top Rim Light
        val rimPaint = android.graphics.Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.2f,
                intArrayOf(Color.White.copy(alpha = 0.25f).toArgb(), 0x00FFFFFF),
                null, Shader.TileMode.CLAMP)
        }
        nativeCanvas.drawRect(0f, 0f, w, h, rimPaint)

        // Physical edge (Stroke)
        val strokePaint = android.graphics.Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(Color.White.copy(alpha = 0.35f).toArgb(), 0x00FFFFFF),
                floatArrayOf(0f, 0.85f), Shader.TileMode.CLAMP)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = with(currentValueOf(LocalDensity)) { 1.dp.toPx() }
        }
        nativeCanvas.drawRect(0f, 0f, w, h, strokePaint)
    }

    private fun drawVignetteMask(nativeCanvas: android.graphics.Canvas, w: Float, h: Float) {
        val maskPaint = android.graphics.Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(-0x1, -0x1, -0x1, 0x00FFFFFF),
                floatArrayOf(0f, 0.15f, 0.85f, 1f), Shader.TileMode.CLAMP)
        }
        nativeCanvas.drawRect(0f, 0f, w, h, maskPaint)
    }
}

/**
 * RenderScript lifecycle manager.
 */
internal class RenderScriptManager(context: Context) {
    private val rs = RenderScript.create(context)
    private val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    private var input: Allocation? = null
    private var output: Allocation? = null

    fun blur(bitmap: Bitmap, radius: Float) {
        if (input == null || input?.type?.x != bitmap.width || input?.type?.y != bitmap.height) {
            input?.destroy()
            output?.destroy()
            input = Allocation.createFromBitmap(rs, bitmap)
            output = Allocation.createTyped(rs, input?.type)
        }

        input?.copyFrom(bitmap)
        blurScript.setRadius(radius.coerceIn(0f, 25f))
        blurScript.setInput(input)
        blurScript.forEach(output)
        output?.copyTo(bitmap)
    }

    fun destroy() {
        input?.destroy()
        output?.destroy()
        blurScript.destroy()
        rs.destroy()
    }
}
