package com.kumagai.composeshaders

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                var selectedColor by remember { mutableStateOf(Color(0xFFFF00E0)) }
                
                val colors = listOf(
                    Color(0xFFFF00E0), // Magenta
                    Color(0xFF00E5FF), // Cyan
                    Color(0xFFFFB300), // Orange
                    Color(0xFF00FF00), // Green
                    Color(0xFFFFD0E0)  // White
                )

                Scaffold(
                    bottomBar = {
                        Column {
                            // Seletor de Cores
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
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Text("Native") },
                                    label = { Text("GLES") },
                                    selected = false,
                                    onClick = { navController.navigate("native_compat") }
                                )
                                NavigationBarItem(
                                    icon = { Text("AGSL") },
                                    label = { Text("GPU") },
                                    selected = false,
                                    onClick = { navController.navigate("native_agsl") }
                                )
                                NavigationBarItem(
                                    icon = { Text("Compose") },
                                    label = { Text("CPU") },
                                    selected = false,
                                    onClick = { navController.navigate("compose_cpu") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "native_compat",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("native_compat") {
                            Box(modifier = Modifier.fillMaxSize()) {
                                NativeCompatSmokeBackground(smokeColor = selectedColor, isAnimated = true)
                                InfoOverlay("Native GLES (NDK + GPU)", Color.Green)
                            }
                        }
                        composable("native_agsl") {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    NativeSmokeBackground(smokeColor = selectedColor, isAnimated = true)
                                    InfoOverlay("Native AGSL (Android 13+ GPU)", Color.Cyan)
                                } else {
                                    Text("AGSL requer Android 13+", Modifier.align(Alignment.Center))
                                }
                            }
                        }
                        composable("compose_cpu") {
                            Box(modifier = Modifier.fillMaxSize()) {
                                ComposeSmokeBackground(smokeColor = selectedColor, isAnimated = true)
                                InfoOverlay("Compose CPU (Pixel Loop)", Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoOverlay(label: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
