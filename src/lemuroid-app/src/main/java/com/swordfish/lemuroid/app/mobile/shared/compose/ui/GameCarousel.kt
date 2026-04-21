package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlin.math.absoluteValue
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalRomSources
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.SourceBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.resolveSourceName

/**
 * 3D Carousel for games with perspective effect
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCarousel(
    games: List<Game>,
    modifier: Modifier = Modifier,
    onGameSelected: (Game) -> Unit = {},
    onGameClick: (Game) -> Unit = {},
    onGameLongClick: (Game) -> Unit = {},
    initialPage: Int = 0
) {
    if (games.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No games found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }
    
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, games.size - 1),
        pageCount = { games.size }
    )
    
    // Notify when page changes
    LaunchedEffect(pagerState.currentPage) {
        if (games.isNotEmpty()) {
            onGameSelected(games[pagerState.currentPage])
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.08f))
        
        // Simple HorizontalPager with fixed values that work well
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = (-80).dp,
            beyondViewportPageCount = 2
        ) { page ->
            GameCarouselCard(
                game = games[page],
                pagerState = pagerState,
                page = page,
                onClick = { onGameClick(games[page]) },
                onLongClick = { onGameLongClick(games[page]) }
            )
        }
        
        // Game info
        Spacer(modifier = Modifier.height(20.dp))
        GameInfoSection(
            game = games.getOrNull(pagerState.currentPage),
            modifier = Modifier.weight(0.40f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCarouselCard(
    game: Game,
    pagerState: PagerState,
    page: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Calculate offset from center
    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
    val absoluteOffset = pageOffset.absoluteValue
    
    // Scale: center = 1.0, sides smaller (subtle effect)
    val scale by animateFloatAsState(
        targetValue = 1f - (absoluteOffset * 0.15f).coerceIn(0f, 0.25f),
        animationSpec = tween(200),
        label = "card_scale"
    )
    
    // Alpha: center = 1.0, sides dimmer (subtle effect)
    val alpha by animateFloatAsState(
        targetValue = 1f - (absoluteOffset * 0.3f).coerceIn(0f, 0.4f),
        animationSpec = tween(200),
        label = "card_alpha"
    )
    
    Card(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .fillMaxHeight()
            .aspectRatio(0.7f)
            .shadow(
                elevation = if (absoluteOffset < 0.5f) 16.dp else 6.dp,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(game.coverFrontUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val sources = LocalRomSources.current
            val badge = remember(game.fileUri, sources) { resolveSourceName(game.fileUri, sources) }
            if (badge != null) {
                SourceBadge(
                    text = badge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun GameInfoSection(
    game: Game?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (game != null) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // System badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = game.systemId.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Year
                game.year?.let { year ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = year.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                // Genre
                game.genre?.let { genre ->
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Description
            game.description?.let { desc ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Developer/Publisher info row
            if (game.developer != null || game.publisher != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = listOfNotNull(game.developer, game.publisher).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
