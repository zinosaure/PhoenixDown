package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.DynamicGameBackground
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GameCarousel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameTexts
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.app.mobile.feature.main.HomeViewMode

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    viewMode: HomeViewMode = HomeViewMode.CAROUSEL,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
    onDeleteGames: (List<Game>) -> Unit = {},
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                viewModel.updatePermissions(applicationContext)
            }
            else -> { }
        }
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (!isGranted) {
                context.displayDetailsSettingsScreen()
            }
        }

    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState())
    
    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedGames by remember { mutableStateOf(setOf<Game>()) }
    
    // Sound manager
    val soundManager = remember { CarouselSoundManager(applicationContext) }
    
    // Selected game for background (in carousel mode)
    var selectedGame by remember { mutableStateOf<Game?>(null) }
    
    // Track page changes for sound
    var lastPage by remember { mutableStateOf(0) }
    
    DisposableEffect(Unit) {
        onDispose {
            soundManager.release()
        }
    }
    
    HomeScreenContent(
        modifier = modifier,
        state = state.value,
        viewMode = viewMode,
        isSelectionMode = isSelectionMode,
        selectedGames = selectedGames,
        selectedGame = selectedGame,
        onGameClicked = { game ->
            if (isSelectionMode) {
                selectedGames = if (game in selectedGames) {
                    selectedGames - game
                } else {
                    selectedGames + game
                }
            } else {
                soundManager.playSelect()
                onGameClick(game)
            }
        },
        onGameLongClick = onGameLongClick,
        onGameSelected = { game ->
            if (game != selectedGame) {
                selectedGame = game
                soundManager.playSwipe()
            }
        },
        onOpenCoreSelection = onOpenCoreSelection,
        onEnableNotificationsClicked = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return@HomeScreenContent
            }
            permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onEnableMicrophoneClicked = { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onSetDirectoryClicked = { viewModel.changeLocalStorageFolder(context) },
        onToggleSelectionMode = {
            isSelectionMode = !isSelectionMode
            if (!isSelectionMode) {
                selectedGames = emptySet()
            }
        },
        onConfirmDelete = {
            soundManager.playDelete()
            onDeleteGames(selectedGames.toList())
            selectedGames = emptySet()
            isSelectionMode = false
        },
        onCancelSelection = {
            selectedGames = emptySet()
            isSelectionMode = false
        },
    )
}

@Composable
private fun HomeScreenContent(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    viewMode: HomeViewMode,
    isSelectionMode: Boolean,
    selectedGames: Set<Game>,
    selectedGame: Game?,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameSelected: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onEnableMicrophoneClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    // Combine all games for carousel view
    val allGames = remember(state.recentGames, state.favoritesGames, state.discoveryGames) {
        (state.recentGames + state.favoritesGames + state.discoveryGames).distinctBy { it.id }
    }
    val hasGames = allGames.isNotEmpty()
    
    Box(modifier = modifier.fillMaxSize()) {
        // Dynamic background (only in carousel mode)
        if (viewMode == HomeViewMode.CAROUSEL && hasGames) {
            DynamicGameBackground(
                game = selectedGame ?: allGames.firstOrNull(),
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Content
        Crossfade(
            targetState = viewMode,
            label = "view_mode_crossfade"
        ) { currentViewMode ->
            when {
                currentViewMode == HomeViewMode.CAROUSEL && hasGames && !isSelectionMode -> {
                    // Carousel View (3D Coverflow)
                    CarouselViewContent(
                        games = allGames,
                        onGameClick = onGameClicked,
                        onGameLongClick = onGameLongClick,
                        onGameSelected = onGameSelected,
                    )
                }
                currentViewMode == HomeViewMode.LIST && hasGames && !isSelectionMode -> {
                    // List View (vertical with thumbnails)
                    ThumbnailListViewContent(
                        games = allGames,
                        onGameClick = onGameClicked,
                        onGameLongClick = onGameLongClick,
                    )
                }
                else -> {
                    // Grid View (original horizontal scrolling)
                    ListViewContent(
                        state = state,
                        isSelectionMode = isSelectionMode,
                        selectedGames = selectedGames,
                        onGameClicked = onGameClicked,
                        onGameLongClick = onGameLongClick,
                        onOpenCoreSelection = onOpenCoreSelection,
                        onEnableNotificationsClicked = onEnableNotificationsClicked,
                        onEnableMicrophoneClicked = onEnableMicrophoneClicked,
                        onSetDirectoryClicked = onSetDirectoryClicked,
                        onConfirmDelete = onConfirmDelete,
                        onCancelSelection = onCancelSelection,
                    )
                }
            }
        }
        
        // FAB for entering selection mode (show in both views when has games)
        AnimatedVisibility(
            visible = !isSelectionMode && hasGames,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = onToggleSelectionMode,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_mode_title)
                )
            }
        }
    }
}

@Composable
private fun CarouselViewContent(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameSelected: (Game) -> Unit,
) {
    GameCarousel(
        games = games,
        modifier = Modifier.fillMaxSize(),
        onGameSelected = onGameSelected,
        onGameClick = onGameClick,
        onGameLongClick = onGameLongClick,
    )
}

@Composable
private fun ListViewContent(
    state: HomeViewModel.UIState,
    isSelectionMode: Boolean,
    selectedGames: Set<Game>,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onEnableMicrophoneClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedVisibility(state.showNoNotificationPermissionCard) {
            HomeNotification(
                titleId = R.string.home_notification_title,
                messageId = R.string.home_notification_message,
                actionId = R.string.home_notification_action,
                onAction = onEnableNotificationsClicked,
            )
        }
        AnimatedVisibility(state.showNoGamesCard) {
            HomeNotification(
                titleId = R.string.home_empty_title,
                messageId = R.string.home_empty_message,
                actionId = R.string.home_empty_action,
                onAction = onSetDirectoryClicked,
                enabled = !state.indexInProgress,
            )
        }
        AnimatedVisibility(state.showNoMicrophonePermissionCard) {
            HomeNotification(
                titleId = R.string.home_microphone_title,
                messageId = R.string.home_microphone_message,
                actionId = R.string.home_microphone_action,
                onAction = onEnableMicrophoneClicked,
            )
        }
        AnimatedVisibility(state.showDesmumeDeprecatedCard) {
            HomeNotification(
                titleId = R.string.home_notification_desmume_deprecated_title,
                messageId = R.string.home_notification_desmume_deprecated_message,
                actionId = R.string.home_notification_desmume_deprecated_action,
                onAction = onOpenCoreSelection,
            )
        }
        
        // Selection mode header
        AnimatedVisibility(isSelectionMode) {
            SelectionModeHeader(
                selectedCount = selectedGames.size,
                onConfirmDelete = onConfirmDelete,
                onCancel = onCancelSelection,
            )
        }
        
        HomeRowWithSelection(
            title = stringResource(id = R.string.recent),
            games = state.recentGames,
            isSelectionMode = isSelectionMode,
            selectedGames = selectedGames,
            onGameClicked = onGameClicked,
            onGameLongClick = onGameLongClick,
        )
        HomeRowWithSelection(
            title = stringResource(id = R.string.favorites),
            games = state.favoritesGames,
            isSelectionMode = isSelectionMode,
            selectedGames = selectedGames,
            onGameClicked = onGameClicked,
            onGameLongClick = onGameLongClick,
        )
        HomeRowWithSelection(
            title = stringResource(id = R.string.discover),
            games = state.discoveryGames,
            isSelectionMode = isSelectionMode,
            selectedGames = selectedGames,
            onGameClicked = onGameClicked,
            onGameLongClick = onGameLongClick,
        )
    }
}

@Composable
private fun SelectionModeHeader(
    selectedCount: Int,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.delete_selected, selectedCount),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = onConfirmDelete,
                    enabled = selectedCount > 0,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.delete_games_confirm),
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRowWithSelection(
    title: String,
    games: List<Game>,
    isSelectionMode: Boolean,
    selectedGames: Set<Game>,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    if (games.isEmpty()) {
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(games.size, key = { games[it].id }) { index ->
                val game = games[index]
                val isSelected = game in selectedGames
                
                GameCardWithSelection(
                    modifier = Modifier
                        .widthIn(0.dp, 144.dp)
                        .animateItem(),
                    game = game,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onClick = { onGameClicked(game) },
                    onLongClick = { onGameLongClick(game) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCardWithSelection(
    modifier: Modifier = Modifier,
    game: Game,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(modifier = modifier) {
        ElevatedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (!isSelectionMode) onLongClick else null,
                    ),
            ) {
                LemuroidGameImage(game = game)
                LemuroidGameTexts(game = game)
            }
        }
        
        // Checkbox overlay in selection mode
        AnimatedVisibility(
            visible = isSelectionMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeNotification(
    titleId: Int,
    messageId: Int,
    actionId: Int,
    enabled: Boolean = true,
    onAction: () -> Unit = { },
) {
    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(titleId),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(messageId),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onAction,
                enabled = enabled,
            ) {
                Text(stringResource(id = actionId))
            }
        }
    }
}

/**
 * Thumbnail List View - vertical list with small thumbnails
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailListViewContent(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(games.size) { index ->
            val game = games[index]
            ThumbnailListItem(
                game = game,
                onClick = { onGameClick(game) },
                onLongClick = { onGameLongClick(game) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailListItem(
    game: Game,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier.size(56.dp, 72.dp)
        ) {
            LemuroidGameImage(
                modifier = Modifier.fillMaxSize(),
                game = game,
            )
        }
        
        // Game info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(game.systemId.uppercase())
                    game.year?.let { append(" • $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            val details = listOfNotNull(game.genre, game.developer).joinToString(" • ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
