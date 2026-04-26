package com.kumagai.composeshaders

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kumagai.composeshaders.blur.legacyBackgroundBlur
import com.kumagai.composeshaders.blur.modernBackgroundBlur
import com.kumagai.composeshaders.blur.renderScriptBackgroundBlur
import com.kumagai.composeshaders.smoke.ComposeSmokeBackground
import com.kumagai.composeshaders.smoke.NativeCompatSmokeBackground
import com.kumagai.composeshaders.smoke.NativeSmokeBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "smoke"

                var selectedColor by remember { mutableStateOf(Color(0xFFFF00E0)) }
                
                // States for implementations of each screen
                var smokeImpl by remember { mutableIntStateOf(0) }
                var blurImpl by remember { mutableIntStateOf(0) }

                val colors = listOf(
                    Color(0xFFFF00E0), // Magenta
                    Color(0xFF00E5FF), // Cyan
                    Color(0xFFFFB300), // Orange
                    Color(0xFFFFD0E0), // Light pink
                    Color(0xFFFFFFFF)  // White
                )

                Scaffold(
                    bottomBar = {
                        Surface(tonalElevation = 3.dp) {
                            Column {
                                // 1. Color Selector
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    colors.forEach { color ->
                                        val isSelected = selectedColor == color
                                        Surface(
                                            onClick = { selectedColor = color },
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = color,
                                            modifier = Modifier
                                                .size(if (isSelected) 40.dp else 32.dp)
                                                .padding(4.dp),
                                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color.Black) else null,
                                            content = {}
                                        )
                                    }
                                }

                                // 2. Implementation Selector (Contextual)
                                val options = if (currentRoute == "smoke") {
                                    listOf("GLES (Native)", "AGSL (13+)", "CPU (Pixel)")
                                } else {
                                    listOf("NDK (CPU)", "RS (GPU)", "Modern (31+)")
                                }
                                val selectedIndex = if (currentRoute == "smoke") smokeImpl else blurImpl
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    options.forEachIndexed { index, label ->
                                        FilterChip(
                                            selected = selectedIndex == index,
                                            onClick = {
                                                if (currentRoute == "smoke") smokeImpl = index else blurImpl = index
                                            },
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }

                                // 3. Main Navigation
                                NavigationBar(modifier = Modifier.height(64.dp)) {
                                    NavigationBarItem(
                                        icon = { Text("💨", style = MaterialTheme.typography.titleLarge) },
                                        label = { Text("Smoke") },
                                        selected = currentRoute == "smoke",
                                        onClick = { 
                                            if (currentRoute != "smoke") {
                                                navController.navigate("smoke") {
                                                    popUpTo("smoke") { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                    NavigationBarItem(
                                        icon = { Text("❄️", style = MaterialTheme.typography.titleLarge) },
                                        label = { Text("Blur") },
                                        selected = currentRoute == "blur",
                                        onClick = { 
                                            if (currentRoute != "blur") {
                                                navController.navigate("blur") {
                                                    popUpTo("smoke") { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "smoke",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("smoke") {
                            SmokeSection(selectedColor, smokeImpl)
                        }
                        composable("blur") {
                            BlurSection(selectedColor, blurImpl)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmokeSection(selectedColor: Color, implementationIndex: Int) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (implementationIndex) {
            0 -> NativeCompatSmokeBackground(smokeColor = selectedColor, isAnimated = true)
            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    NativeSmokeBackground(smokeColor = selectedColor, isAnimated = true)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("AGSL requires Android 13+")
                    }
                }
            }
            2 -> ComposeSmokeBackground(smokeColor = selectedColor, isAnimated = true)
        }
        
        val (label, infoColor) = when (implementationIndex) {
            0 -> "Native GLES (NDK + GPU)" to Color.Green
            1 -> "Native AGSL (Android 13+ GPU)" to Color.Cyan
            else -> "Compose CPU (Pixel Loop)" to Color.Red
        }
        InfoOverlay(label, infoColor)
    }
}

@Composable
fun BlurSection(selectedColor: Color, implementationIndex: Int) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background content to be blurred
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 100.dp)) {
            items(30) { i ->
                Card(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (i % 2 == 0) Color(0xFFF8F8F8) else Color(0xFFE8E8E8)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Post #$i", style = MaterialTheme.typography.titleMedium)
                        
                        val infoText = when (implementationIndex) {
                            0 -> """
                                1. NDK StackBlur (CPU + SIMD)
                                • How it works: Processing is done entirely on the CPU. It uses mathematical algorithms (StackBlur) written in C++ to manipulate Bitmap pixels. It uses NEON (SIMD) instructions to parallelize calculations.
                                • Advantage: Compatible with any Android version.
                                • Disadvantage: It is the heaviest option. Depending on resolution, it can cause FPS drops by overloading the main processor, which is not optimized for graphics like the GPU.
                            """.trimIndent()
                            1 -> """
                                2. RenderScript (GPU Legacy)
                                • How it works: A Google API (now deprecated) that automatically sends processing to the GPU or DSP. It uses a system-optimized image processing "Script".
                                • Advantage: Much faster than NDK, as it leverages graphics hardware without requiring complex GLES code.
                                • Disadvantage: Deprecated since Android 12. On recent Android versions, it runs via software emulation, losing much of its original performance benefit.
                            """.trimIndent()
                            else -> """
                                3. Modern Blur / RenderNode (Native GPU - API 31+)
                                • How it works: Uses new Android 12+ APIs (RenderEffect). Blurring occurs directly in the UI rendering pipeline (Hardware Renderer). In Compose, it is applied via Modifier.graphicsLayer { renderEffect = ... }.
                                • Advantage: The most performant and efficient solution. The system applies blur in real-time while drawing the screen, without needing to extract Bitmaps or manage buffers manually.
                                • Disadvantage: Only works on devices running Android 12 or higher.
                            """.trimIndent()
                        }
                        
                        Text(infoText, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                             Box(modifier = Modifier.size(40.dp).background(Color.Red.copy(0.7f), MaterialTheme.shapes.small))
                             Spacer(modifier = Modifier.width(8.dp))
                             Box(modifier = Modifier.size(40.dp).background(Color.Blue.copy(0.7f), MaterialTheme.shapes.small))
                             Spacer(modifier = Modifier.width(8.dp))
                             Box(modifier = Modifier.size(40.dp).background(Color.Green.copy(0.7f), MaterialTheme.shapes.small))
                        }
                    }
                }
            }
        }

        // "Glass" Window (Frosty Window)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth().padding(top = 100.dp)
                .height(150.dp)
                .then(
                    when (implementationIndex) {
                        0 -> Modifier.legacyBackgroundBlur(overlayColor = selectedColor.copy(alpha = 0.15f))
                        1 -> Modifier.renderScriptBackgroundBlur(overlayColor = selectedColor.copy(alpha = 0.15f))
                        else -> Modifier.modernBackgroundBlur(overlayColor = selectedColor.copy(alpha = 0.15f))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Glassmorphism",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Black
                )
                Text(
                    "Hardware Accelerated Blur",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.6f)
                )
            }
        }

        val (label, infoColor) = when (implementationIndex) {
            0 -> "NDK StackBlur (CPU SIMD)" to Color(0xFFFFD700)
            1 -> "RenderScript (GPU Legacy)" to Color(0xFF448AFF)
            else -> "RenderNode (API 31+ GPU)" to Color(0xFFE040FB)
        }
        InfoOverlay(label, infoColor)
    }
}

@Composable
fun InfoOverlay(label: String, color: Color) {
    Box(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Surface(color = color.copy(alpha = 0.8f), shape = MaterialTheme.shapes.small) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Black
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FpsCounter()
        }
    }
}
