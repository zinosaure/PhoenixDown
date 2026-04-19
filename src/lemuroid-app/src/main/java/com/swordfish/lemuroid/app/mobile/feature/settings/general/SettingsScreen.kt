package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    val state =
        viewModel.uiState
            .collectAsState(SettingsViewModel.State())
            .value

    val scanInProgress =
        viewModel.directoryScanInProgress
            .collectAsState(false)
            .value

    val indexingInProgress =
        viewModel.indexingInProgress
            .collectAsState(false)
            .value

    LemuroidSettingsPage(modifier = modifier) {
        RomsSettings(
            state = state,
            onChangeFolder = { viewModel.changeLocalStorageFolder() },
            indexingInProgress = indexingInProgress,
            scanInProgress = scanInProgress,
        )
        GeneralSettings()
        InputSettings(navController = navController)
        MiscSettings(
            indexingInProgress = indexingInProgress,
            isSaveSyncSupported = state.isSaveSyncSupported,
            navController = navController,
        )
        MetadataSettings(
            viewModel = viewModel
        )
    }
}

@Composable
private fun MetadataSettings(
    viewModel: SettingsViewModel
) {
    val apiKey = viewModel.theGamesDbApiKey.collectAsState().value
    
    var showApiKeyDialog by remember { mutableStateOf(false) }

    if (showApiKeyDialog) {
        var tempApiKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text(stringResource(R.string.metadata_dialog_apikey_title)) },
            text = {
                OutlinedTextField(
                    value = tempApiKey,
                    onValueChange = { tempApiKey = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTheGamesDbApiKey(tempApiKey)
                        showApiKeyDialog = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_metadata)) },
    ) {
        Text(
            text = stringResource(R.string.settings_thegamesdb_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_thegamesdb_apikey)) },
            subtitle = { 
                Text(
                    text = if (apiKey.isNotEmpty()) stringResource(R.string.settings_thegamesdb_configured) else stringResource(R.string.settings_thegamesdb_not_configured)
                ) 
            },
            onClick = { showApiKeyDialog = true },
        )
    }
}


@Composable
private fun MiscSettings(
    indexingInProgress: Boolean,
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_misc)) },
    ) {
        if (isSaveSyncSupported) {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = {
                    Text(text = stringResource(id = R.string.settings_description_save_sync))
                },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
            )
        }
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_open_cores_selection)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_open_cores_selection))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_display_bios_info)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_display_bios_info))
            },
            enabled = !indexingInProgress,
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_BIOS) },
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_manual)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_manual))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_MANUAL) },
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_advanced_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_advanced_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ADVANCED) },
        )

    }
}

@Composable
private fun InputSettings(navController: NavController) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_input)) },
    ) {
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_haptic_feedback_mode,
                    "press",
                    stringListResource(R.array.pref_key_haptic_feedback_mode_values),
                ),
            title = {
                Text(text = stringResource(id = R.string.settings_title_enable_touch_feedback))
            },
            items = stringListResource(R.array.pref_key_haptic_feedback_mode_display_names),
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_gamepad_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_gamepad_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_INPUT_DEVICES) },
        )
    }
}

@Composable
private fun GeneralSettings() {
    val hdMode = booleanPreferenceState(R.string.pref_key_hd_mode, false)
    val immersiveMode = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false)

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_general)) },
    ) {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_autosave)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_autosave)) },
        )
        LemuroidSettingsSwitch(
            state = immersiveMode,
            title = { Text(text = stringResource(id = R.string.settings_title_immersive_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_immersive_mode)) },
        )
        LemuroidSettingsSwitch(
            state = hdMode,
            title = { Text(text = stringResource(id = R.string.settings_title_hd_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_mode)) },
        )
        LemuroidSettingsSlider(
            enabled = hdMode.value,
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_hd_mode_quality),
                    default = 2,
                ),
            steps = 1,
            valueRange = 0f..2f,
            title = { Text(text = stringResource(R.string.settings_title_hd_quality)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_quality)) },
        )
        LemuroidSettingsList(
            enabled = !hdMode.value,
            state =
                indexPreferenceState(
                    R.string.pref_key_shader_filter,
                    "auto",
                    stringListResource(R.array.pref_key_shader_filter_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.display_filter)) },
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
    }
}

@Composable
private fun RomsSettings(
    state: SettingsViewModel.State,
    onChangeFolder: () -> Unit,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
) {
    val context = LocalContext.current
    var showLibrarySourceDialog by remember { mutableStateOf(false) }
    var connectionTestState by remember { mutableStateOf<com.swordfish.lemuroid.app.shared.library.ConnectionTestState>(
        com.swordfish.lemuroid.app.shared.library.ConnectionTestState.Idle
    ) }
    val scope = rememberCoroutineScope()
    val smbClient = remember { com.swordfish.lemuroid.lib.storage.smb.SmbClient() }

    val currentDirectory = state.currentDirectory
    val emptyDirectory = stringResource(R.string.none)
    
    // Check if SMB is configured
    val prefs = remember { com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.getSharedPreferences(context) }
    val libraryType = prefs.getString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_LIBRARY_TYPE, "local")
    val smbServer = prefs.getString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, null)

    val currentDirectoryName =
        remember(state.currentDirectory, libraryType, smbServer) {
            if (libraryType == "smb" && !smbServer.isNullOrBlank()) {
                "SMB: $smbServer"
            } else {
                runCatching {
                    DocumentFile.fromTreeUri(context, Uri.parse(currentDirectory))?.name
                }.getOrNull() ?: emptyDirectory
            }
        }

    // Show LibrarySourceDialog
    if (showLibrarySourceDialog) {
        com.swordfish.lemuroid.app.shared.library.LibrarySourceDialog(
            onDismiss = { showLibrarySourceDialog = false },
            onLocalSelected = {
                // Save library type as local
                prefs.edit().putString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_LIBRARY_TYPE, "local").apply()
                // Launch folder picker
                onChangeFolder()
            },
            onSmbSelected = { server, path, username, password ->
                // Parse path to extract share name and subpath
                // Path format: /ShareName/Folder/SubFolder
                val pathSegments = path.removePrefix("/").split("/")
                val shareName = pathSegments.firstOrNull() ?: ""
                val subPath = if (pathSegments.size > 1) {
                    "/" + pathSegments.drop(1).joinToString("/")
                } else {
                    ""
                }
                
                // Save SMB configuration
                prefs.edit().apply {
                    putString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_LIBRARY_TYPE, "smb")
                    putString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, server)
                    putString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, shareName)
                    putString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, subPath)
                    putString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, username)
                    putString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, password)
                    apply()
                }
                showLibrarySourceDialog = false
                // Trigger library rescan
                LibraryIndexScheduler.scheduleLibrarySync(context)
            },
            onTestConnection = { server, path, username, password ->
                connectionTestState = com.swordfish.lemuroid.app.shared.library.ConnectionTestState.Testing
                scope.launch {
                    val credentials = if (username != null) {
                        com.swordfish.lemuroid.lib.storage.smb.SmbCredentials(username, password ?: "")
                    } else null
                    
                    // Parse path to get share name
                    val shareName = path.removePrefix("/").split("/").firstOrNull() ?: ""
                    val result = smbClient.testConnection(server, shareName, credentials)
                    connectionTestState = if (result.isSuccess) {
                        com.swordfish.lemuroid.app.shared.library.ConnectionTestState.Success
                    } else {
                        com.swordfish.lemuroid.app.shared.library.ConnectionTestState.Error(
                            result.exceptionOrNull()?.message ?: "Unknown error"
                        )
                    }
                }
            },
            connectionTestResult = connectionTestState
        )
    }

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.roms)) }) {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.directory)) },
            subtitle = { Text(text = currentDirectoryName) },
            onClick = { showLibrarySourceDialog = true },
            enabled = !indexingInProgress,
        )
        if (scanInProgress) {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.stop)) },
                onClick = { LibraryIndexScheduler.cancelLibrarySync(context) },
            )
        } else {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.rescan)) },
                onClick = { LibraryIndexScheduler.scheduleLibrarySync(context) },
                enabled = !indexingInProgress,
            )
        }
    }
}
