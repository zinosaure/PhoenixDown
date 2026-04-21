package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesScreen
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesViewModel
import com.swordfish.lemuroid.app.mobile.feature.games.GamesScreen
import com.swordfish.lemuroid.app.mobile.feature.games.GamesViewModel
import com.swordfish.lemuroid.app.mobile.feature.catalog.CatalogScreen
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomDownloader
import com.swordfish.lemuroid.app.mobile.feature.home.HomeScreen
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.search.SearchScreen
import com.swordfish.lemuroid.app.mobile.feature.search.SearchViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsScreen
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.BackgroundWithOverlay
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.DeleteGamesConfirmationDialog
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.ext.feature.review.ReviewManager
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Provides
import de.charlex.compose.material3.HtmlText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var gameMetadataProvider: com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider

    @Inject
    lateinit var romDownloader: RomDownloader

    private val reviewManager = ReviewManager()

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        if (com.swordfish.lemuroid.app.tv.shared.TVHelper.isTV(this)) {
            val intent = Intent(this, com.swordfish.lemuroid.app.tv.main.MainTVActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        GlobalScope.safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        setContent {
            val navController = rememberNavController()
            
            // Verificar si el disclaimer fue aceptado
            val sharedPrefs = SharedPreferencesHelper.getSharedPreferences(applicationContext)
            val disclaimerAccepted = remember { 
                mutableStateOf(sharedPrefs.getBoolean("disclaimer_accepted", false)) 
            }
            
            if (!disclaimerAccepted.value) {
                // Mostrar pantalla de disclaimer
                AppTheme {
                    com.swordfish.lemuroid.app.mobile.feature.disclaimer.DisclaimerScreen(
                        onAccept = {
                            sharedPrefs.edit().putBoolean("disclaimer_accepted", true).apply()
                            disclaimerAccepted.value = true
                        }
                    )
                }
            } else {
                MainScreen(navController)
            }
        }
    }
    
    private val requestPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                ensureDefaultFolderConfigured()
            }
        }

    override fun onResume() {
        super.onResume()

        // V10 FIX: Fallback for broken SAF on some devices (Android 10/11)
        ensureLegacyStoragePermissionsIfNeeded()
        // If we have "All Files Access", ensure the default folder is set immediately
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()) {
            ensureDefaultFolderConfigured()
        }
    }
    
    private fun ensureDefaultFolderConfigured() {
        val repo = com.swordfish.lemuroid.lib.storage.source.SourceRepository(applicationContext)
        // Only add the default path if there are no LOCAL sources at all
        if (repo.getCustomSources().none { it.type == com.swordfish.lemuroid.lib.storage.source.SourceType.LOCAL }) {
            val defaultFile = java.io.File(android.os.Environment.getExternalStorageDirectory(), "EmulAI_Roms")
            if (!defaultFile.exists()) defaultFile.mkdirs()
            val uri = android.net.Uri.fromFile(defaultFile).toString()
            repo.upsertByPath(com.swordfish.lemuroid.lib.storage.source.RomSource.local("EmulAI Roms", uri))
            com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
        }
    }

    private fun ensureLegacyStoragePermissionsIfNeeded() {
        // 1. If SAF is ALREADY configured -> Do nothing
        if (SharedPreferencesHelper.getSAFUri(applicationContext) != null) return
        
        // 2. If SAF is SUPPORTED (standard device) -> Do nothing (let user use picker)
        if (com.swordfish.lemuroid.app.tv.shared.TVHelper.isSAFSupported(this)) {
            return 
        }

        // 3. Fallback Logic
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = android.net.Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent()
                        intent.action = android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                        startActivity(intent)
                    } catch (e2: Exception) {
                        // ignore
                    }
                }
            }
        } else {
            // Android 10 or below (Legacy)
            if (!isLegacyPermissionGranted()) {
                requestLegacyStoragePermission()
            } else {
                 ensureDefaultFolderConfigured()
            }
        }
    }

    private fun requestLegacyStoragePermission() {
        requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun isLegacyPermissionGranted(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    override fun onDestroy() = super.onDestroy()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val currentRoute =
                currentDestination?.route
                    ?.let { MainRoute.findByRoute(it) }
                    ?: MainRoute.HOME

            val infoDialogDisplayed =
                remember {
                    mutableStateOf(false)
                }

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }
            
            // Handle navigation from TV interface
            LaunchedEffect(Unit) {
                val navigateTo = intent.getStringExtra("navigate_to")
                if (navigateTo == "catalog") {
                    navController.navigate(MainRoute.CATALOG.route) {
                        popUpTo(MainRoute.HOME.route) { inclusive = false }
                    }
                }
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }
            
            // State for game edit dialog
            var gameToEdit by remember { mutableStateOf<Game?>(null) }
            
            // State for delete confirmation dialog
            var gameToDelete by remember { mutableStateOf<Game?>(null) }
            
            // View mode toggle state (Carousel -> Grid -> List -> Carousel)
            var viewMode by remember { mutableStateOf(HomeViewMode.CAROUSEL) }
            
            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val onGameClick = { game: Game ->
                gameInteractor.onGamePlay(game)
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            val onHelpPressed = {
                navController.navigateToRoute(MainRoute.SETTINGS_ABOUT)
            }

            val baseUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value
            
            // Enrich UiState with view mode callbacks
            val mainUIState = baseUIState.copy(
                viewMode = viewMode,
                onToggleView = { 
                    viewMode = when (viewMode) {
                        HomeViewMode.CAROUSEL -> HomeViewMode.GRID
                        HomeViewMode.GRID -> HomeViewMode.LIST
                        HomeViewMode.LIST -> HomeViewMode.CAROUSEL
                    }
                },
            )

            BackgroundWithOverlay {
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    topBar = {
                    MainTopBar(
                        currentRoute = currentRoute,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                        onUpdateQueryString = { mainViewModel.changeQueryString(it) },
                    )
                },
                bottomBar = { MainNavigationBar(currentRoute, navController) },
            ) { padding ->
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    startDestination = MainRoute.HOME.route,
                ) {
                    composable(MainRoute.HOME) {
                        // State for mass deletion
                        var gamesToDelete by remember { mutableStateOf<List<Game>>(emptyList()) }
                        
                        HomeScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        HomeViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            coresSelection,
                                        ),
                                ),
                            viewMode = viewMode,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onOpenCoreSelection = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
                            onDeleteGames = { games -> gamesToDelete = games },
                        )
                        
                        // Mass deletion confirmation dialog
                        if (gamesToDelete.isNotEmpty()) {
                            DeleteGamesConfirmationDialog(
                                games = gamesToDelete,
                                onConfirm = {
                                    gameInteractor.deleteGames(gamesToDelete)
                                    gamesToDelete = emptyList()
                                },
                                onDismiss = { gamesToDelete = emptyList() }
                            )
                        }
                    }
                    composable(MainRoute.FAVORITES) {
                        FavoritesScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = FavoritesViewModel.Factory(retrogradeDb),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                        )
                    }
                    composable(MainRoute.SEARCH) {
                        SearchScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = SearchViewModel.Factory(retrogradeDb),
                                ),
                            searchQuery = mainUIState.searchQuery,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                            onResetSearchQuery = { mainViewModel.changeQueryString("") },
                        )
                    }
                    composable(MainRoute.SYSTEMS) {
                        MetaSystemsScreen(
                            modifier = Modifier.padding(padding),
                            navController = navController,
                            viewModel =
                                viewModel(
                                    factory =
                                        MetaSystemsViewModel.Factory(
                                            retrogradeDb,
                                            applicationContext,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.CATALOG) {
                        CatalogScreen(
                            modifier = Modifier.padding(padding),
                            gameMetadataProvider = gameMetadataProvider,
                            romDownloader = romDownloader
                        )
                    }
                    composable(MainRoute.SYSTEM_GAMES) { entry ->
                        val metaSystemId = entry.arguments?.getString("metaSystemId")
                        GamesScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        GamesViewModel.Factory(
                                            retrogradeDb,
                                            MetaSystemID.valueOf(metaSystemId!!),
                                        ),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                        )
                    }
                    composable(MainRoute.SETTINGS) {
                        SettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                            saveSyncManager,
                                            FlowSharedPreferences(
                                                SharedPreferencesHelper.getSharedPreferences(
                                                    applicationContext,
                                                ),
                                            ),
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_ADVANCED) {
                        AdvancedSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        AdvancedSettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_BIOS) {
                        BiosScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = BiosSettingsViewModel.Factory(biosManager),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_CORES_SELECTION) {
                        CoresSelectionScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        CoresSelectionViewModel.Factory(
                                            applicationContext,
                                            coresSelection,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                        InputDevicesSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        InputDevicesSettingsViewModel.Factory(
                                            applicationContext,
                                            inputDeviceManager,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_SAVE_SYNC) {
                        SaveSyncSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SaveSyncSettingsViewModel.Factory(
                                            application,
                                            saveSyncManager,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_MANUAL) {
                        com.swordfish.lemuroid.app.mobile.feature.settings.manual.ManualScreen(
                            modifier = Modifier.padding(padding),
                        )
                    }
                    composable(MainRoute.SETTINGS_ABOUT) {
                        com.swordfish.lemuroid.app.mobile.feature.settings.about.AboutScreen(
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }

            MainGameContextActions(
                selectedGameState = selectedGameState,
                shortcutSupported = gameInteractor.supportShortcuts(),
                onGamePlay = { gameInteractor.onGamePlay(it) },
                onGameRestart = { gameInteractor.onGameRestart(it) },
                onFavoriteToggle = { game: Game, isFavorite: Boolean ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
                onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
                onEdit = { game -> gameToEdit = game },
                onDelete = { game -> gameToDelete = game },
                onChangeSystem = { game -> gameToEdit = game },
            )
            
            // Game edit dialog
            gameToEdit?.let { game ->
                GameEditDialog(
                    game = game,
                    onDismiss = { gameToEdit = null },
                    onSave = { updatedGame ->
                        gameInteractor.updateGame(updatedGame)
                        gameToEdit = null
                    }
                )
            }
            
            // Delete confirmation dialog
            gameToDelete?.let { game ->
                DeleteGamesConfirmationDialog(
                    games = listOf(game),
                    onConfirm = {
                        gameInteractor.deleteGame(game)
                        gameToDelete = null
                    },
                    onDismiss = { gameToDelete = null }
                )
            }

            if (infoDialogDisplayed.value) {
                val message =
                    remember {
                        val systemFolders =
                            SystemID.values()
                                .joinToString(", ") { "<i>${it.dbname}</i>" }

                        getString(R.string.lemuroid_help_content)
                            .replace("\$SYSTEMS", systemFolders)
                    }

                AlertDialog(
                    title = {
                        Text(text = "Phoenix Down v${com.swordfish.lemuroid.BuildConfig.VERSION_NAME}")
                    },
                    text = {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                        ) {
                            HtmlText(text = message)
                        }
                    },
                    onDismissRequest = { infoDialogDisplayed.value = false },
                    confirmButton = { },
                )
            }
            }  // end BackgroundWithOverlay
        }
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
                        true,
                        this@MainActivity,
                        resultCode,
                        data,
                    )
                }
            }
        }
    }

    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                lemuroidLibrary: com.swordfish.lemuroid.lib.library.LemuroidLibrary,
            ) = GameInteractor(activity, retrogradeDb, false, shortcutsGenerator, gameLauncher, lemuroidLibrary)
        }
    }
}
