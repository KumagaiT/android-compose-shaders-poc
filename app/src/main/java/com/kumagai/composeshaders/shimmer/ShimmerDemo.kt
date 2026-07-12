package com.kumagai.composeshaders.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerDemo(
    selectedColor: Color,
    implementationIndex: Int
) {
    val shimmerState = rememberShimmerState()
    val implementation = when (implementationIndex) {
        0 -> ShimmerImplementation.Compose
        1 -> ShimmerImplementation.AGSL
        else -> ShimmerImplementation.Native
    }

    val loadingStates = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(20) { add(true) }
        }
    }

    CompositionLocalProvider(LocalShimmerState provides shimmerState) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Shared Shimmer Effect",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 110.dp, bottom = 8.dp)
                )
                Text(
                    "Tap a card to toggle its loading state. All active shimmers are synchronized via CompositionLocal.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            items(loadingStates.size) { index ->
                ShimmerLoadingItem(
                    implementation = implementation,
                    baseColor = selectedColor,
                    loading = loadingStates[index],
                    onClick = { loadingStates[index] = !loadingStates[index] }
                )
            }
        }
    }
}

@Composable
fun ShimmerLoadingItem(
    implementation: ShimmerImplementation,
    baseColor: Color,
    loading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle placeholder
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .then(
                        if (loading) {
                            Modifier.shimmer(
                                implementation = implementation,
                                color = baseColor.copy(alpha = 0.2f),
                                highlightColor = baseColor.copy(alpha = 0.6f)
                            )
                        } else {
                            Modifier.background(baseColor.copy(alpha = 0.1f))
                        }
                    )
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (loading) {
                                Modifier.shimmer(
                                    implementation = implementation,
                                    color = baseColor.copy(alpha = 0.2f),
                                    highlightColor = baseColor.copy(alpha = 0.6f)
                                )
                            } else {
                                Modifier.background(baseColor.copy(alpha = 0.1f))
                            }
                        )
                )

                if (loading) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtitle placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmer(
                                implementation = implementation,
                                color = baseColor.copy(alpha = 0.2f),
                                highlightColor = baseColor.copy(alpha = 0.6f)
                            )
                    )
                } else {
                    Text("Loading complete!", style = MaterialTheme.typography.bodySmall)
                    Text("Tap to restart shimmer", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}
