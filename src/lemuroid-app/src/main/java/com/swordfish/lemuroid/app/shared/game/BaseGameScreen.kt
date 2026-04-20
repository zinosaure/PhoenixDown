package com.swordfish.lemuroid.app.shared.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView

@Composable
fun BaseGameScreen(
    viewModel: BaseGameScreenViewModel,
    gameScreen: @Composable (BaseGameScreenViewModel) -> Unit,
) {
    val gameState =
        viewModel.getGameState()
            .collectAsState(GameViewModelRetroGameView.GameState.Uninitialized)
            .value

    val isGameReady =
        gameState is GameViewModelRetroGameView.GameState.Loaded ||
            gameState is GameViewModelRetroGameView.GameState.Ready

    if (isGameReady) {
        gameScreen(viewModel)
    } else {
        val loadingInfo = gameState as? GameViewModelRetroGameView.GameState.Loading
        var animatedProgress by remember(loadingInfo?.message, loadingInfo?.progressStart, loadingInfo?.progressMax) {
            mutableIntStateOf(loadingInfo?.progressStart ?: 0)
        }

        LaunchedEffect(loadingInfo?.message, loadingInfo?.progressStart, loadingInfo?.progressMax) {
            val start = loadingInfo?.progressStart ?: return@LaunchedEffect
            val max = loadingInfo.progressMax
            val visualMax = if (max >= 100) 100 else (max - 1).coerceAtLeast(start)
            val loopingFloor = (start + 2).coerceAtMost(visualMax)
            if (animatedProgress < start || animatedProgress > visualMax) {
                animatedProgress = start
            }

            while (true) {
                delay(320)
                if (animatedProgress >= visualMax) {
                    // Long-running operations can stay in one state for a while.
                    // Loop within the stage range so progress keeps moving.
                    animatedProgress = loopingFloor
                    continue
                }

                val remaining = visualMax - animatedProgress
                val step = when {
                    remaining > 20 -> 3
                    remaining > 8 -> 2
                    else -> 1
                }
                animatedProgress = (animatedProgress + step).coerceAtMost(visualMax)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = loadingInfo?.title.orEmpty(),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )

                AnimatedVisibility(!loadingInfo?.subtitle.isNullOrBlank()) {
                    Text(
                        text = loadingInfo?.subtitle.orEmpty(),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                LinearProgressIndicator(
                    progress = { (animatedProgress.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "$animatedProgress%",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge,
                )

                AnimatedVisibility(loadingInfo != null) {
                    Text(
                        text = loadingInfo?.message.orEmpty(),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
