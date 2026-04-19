package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Dynamic background that shows the selected game's artwork with blur effect
 */
@Composable
fun DynamicGameBackground(
    game: Game?,
    modifier: Modifier = Modifier,
    blurRadius: Int = 25
) {
    val backgroundUrls = remember(game) {
        game?.let { GameArtworkProvider.getBackgroundUrls(it) } ?: emptyList()
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Crossfade between games
        Crossfade(
            targetState = game?.id ?: 0,
            animationSpec = tween(durationMillis = 500),
            label = "background_crossfade"
        ) { gameId ->
            if (game != null && backgroundUrls.isNotEmpty()) {
                BackgroundImage(
                    urls = backgroundUrls,
                    blurRadius = blurRadius
                )
            } else {
                // Default dark background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212))
                )
            }
        }
        
        // Dark overlay gradient for better readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun BackgroundImage(
    urls: List<String>,
    blurRadius: Int
) {
    val context = LocalContext.current
    var currentUrlIndex by remember { mutableStateOf(0) }
    var imageLoaded by remember { mutableStateOf(false) }
    
    // Try loading images in order until one succeeds
    val currentUrl = urls.getOrNull(currentUrlIndex)
    
    if (currentUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(currentUrl)
                .crossfade(true)
                .listener(
                    onError = { _, _ ->
                        // Try next URL if available
                        if (currentUrlIndex < urls.size - 1) {
                            currentUrlIndex++
                        }
                    },
                    onSuccess = { _, _ ->
                        imageLoaded = true
                    }
                )
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius.dp)
        )
    }
    
    // Fallback dark background if no image loaded
    if (!imageLoaded && currentUrlIndex >= urls.size - 1) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E))
        )
    }
}
