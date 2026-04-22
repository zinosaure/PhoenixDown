package com.swordfish.lemuroid.app.mobile.feature.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalRomSources
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.source.SourceRepository

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
    multiSelectEnabled: Boolean = false,
) {
    val games = viewModel.games.collectAsLazyPagingItems()
    val context = LocalContext.current
    val sources by remember { SourceRepository(context).sourcesFlow() }.collectAsState(emptyList())

    // Multi-select state (only meaningful when multiSelectEnabled = true)
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedGames by remember { mutableStateOf(mapOf<Int, Game>()) }
    var showAssignDialog by remember { mutableStateOf(false) }

    if (games.itemCount == 0 && !isSelectionMode) {
        LemuroidEmptyView()
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Selection action bar — shown when in multi-select mode
        if (isSelectionMode && multiSelectEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        isSelectionMode = false
                        selectedGames = emptyMap()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                    Text(
                        text = stringResource(R.string.multiselect_selected_count, selectedGames.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(
                        onClick = { showAssignDialog = true },
                        enabled = selectedGames.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.assign_platform_button))
                    }
                }
            }
        }

        CompositionLocalProvider(LocalRomSources provides sources) {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                columns = GridCells.Adaptive(144.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
                    val game = games[index] ?: return@items
                    val isSelected = selectedGames.containsKey(game.id)

                    Box {
                        LemuroidGameCard(
                            modifier = Modifier.animateItem(),
                            game = game,
                            onClick = {
                                if (isSelectionMode && multiSelectEnabled) {
                                    selectedGames = if (isSelected) {
                                        selectedGames - game.id
                                    } else {
                                        selectedGames + (game.id to game)
                                    }
                                    if (selectedGames.isEmpty()) isSelectionMode = false
                                } else {
                                    onGameClick(game)
                                }
                            },
                            onLongClick = {
                                if (multiSelectEnabled) {
                                    isSelectionMode = true
                                    selectedGames = selectedGames + (game.id to game)
                                } else {
                                    onGameLongClick(game)
                                }
                            },
                            onFavoriteToggle = if (isSelectionMode && multiSelectEnabled) null
                                              else { isFav -> onGameFavoriteToggle(game, isFav) },
                        )
                        if (isSelectionMode && multiSelectEnabled) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedGames = if (checked) {
                                        selectedGames + (game.id to game)
                                    } else {
                                        selectedGames - game.id
                                    }
                                    if (selectedGames.isEmpty()) isSelectionMode = false
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Assign platform dialog
    if (showAssignDialog) {
        AssignPlatformDialog(
            selectedCount = selectedGames.size,
            onDismiss = { showAssignDialog = false },
            onAssign = { systemId ->
                viewModel.bulkUpdateSystem(selectedGames.values.toList(), systemId)
                isSelectionMode = false
                selectedGames = emptyMap()
                showAssignDialog = false
            },
        )
    }
}
