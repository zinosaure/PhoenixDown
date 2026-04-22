package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.catalog.ConnectionTestState
import com.swordfish.lemuroid.app.mobile.feature.catalog.ConnectionTestStatus
import com.swordfish.lemuroid.app.mobile.feature.catalog.NetworkClient
import com.swordfish.lemuroid.app.mobile.feature.catalog.SmbClient
import com.swordfish.lemuroid.app.mobile.feature.catalog.SmbConfigForm
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.logs.LogViewerActivity
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
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.storage.source.NetworkProtocol
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SmbLoginProfile
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials as SmbCredentials
import com.swordfish.lemuroid.lib.storage.source.SourceType
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider

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
            viewModel = viewModel,
            indexingInProgress = indexingInProgress,
            scanInProgress = scanInProgress,
        )
        GeneralSettings()
        InputSettings(navController = navController)
        MetadataSettings(
            viewModel = viewModel,
        )
        ConsolesSettings(
            indexingInProgress = indexingInProgress,
            isSaveSyncSupported = state.isSaveSyncSupported,
            navController = navController,
        )
    }
}

@Composable
private fun MetadataSettings(
    viewModel: SettingsViewModel,
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
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTheGamesDbApiKey(tempApiKey)
                        showApiKeyDialog = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_metadata)) },
    ) {
        Text(
            text = stringResource(R.string.settings_thegamesdb_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )

        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_thegamesdb_apikey)) },
            subtitle = {
                Text(
                    text = if (apiKey.isNotEmpty()) stringResource(R.string.settings_thegamesdb_configured) else stringResource(R.string.settings_thegamesdb_not_configured),
                )
            },
            onClick = { showApiKeyDialog = true },
        )
    }
}

@Composable
private fun ConsolesSettings(
    indexingInProgress: Boolean,
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
    val context = LocalContext.current

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_consoles)) },
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
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_log_viewer)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_log_viewer))
            },
            onClick = { context.startActivity(Intent(context, LogViewerActivity::class.java)) },
        )
    }
}

@Composable
private fun InputSettings(navController: NavController) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_system_interaction)) },
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
    viewModel: SettingsViewModel,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
) {
    val context = LocalContext.current
    val allSources by viewModel.sources.collectAsState()
    val smbLoginProfiles by viewModel.smbLoginProfiles.collectAsState()
    val customSources = remember(allSources) { allSources.filter { it.type != SourceType.ARCHIVE_ORG } }

    val saveLocationUri by viewModel.saveLocationUri.collectAsState()
    val downloadSourceId by viewModel.downloadSourceId.collectAsState()

    var pendingDeleteSource by remember { mutableStateOf<RomSource?>(null) }
    var editingSmbSource by remember { mutableStateOf<RomSource?>(null) }
    var showAddSmbDialog by remember { mutableStateOf(false) }
    var editingLocalSourceId by remember { mutableStateOf<String?>(null) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var editingSmbLoginProfile by remember { mutableStateOf<SmbLoginProfile?>(null) }
    var showAddSmbLoginDialog by remember { mutableStateOf(false) }
    var pendingDeleteSmbLoginProfile by remember { mutableStateOf<SmbLoginProfile?>(null) }

    // Dialogs to choose save/download location type (local vs SMB)
    var showSavePickerDialog by remember { mutableStateOf(false) }
    var showDownloadPickerDialog by remember { mutableStateOf(false) }
    var showSaveSmbDialog by remember { mutableStateOf(false) }
    var showDownloadSmbDialog by remember { mutableStateOf(false) }

    // State machine for platform hint
    var pendingSourceForPlatform by remember { mutableStateOf<RomSource?>(null) }
    var pendingSourceIsEdit by remember { mutableStateOf(false) }

    // SAF picker — ajouter dossier local (bibliothèque)
    val addLocalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { Log.w("SettingsScreen", "Permission non persistable pour $uri") }
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Jeux"
            pendingSourceForPlatform = RomSource.local(name, uri.toString())
            pendingSourceIsEdit = false
        }
    }

    // SAF picker — modifier dossier local (bibliothèque)
    val editLocalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val sourceId = editingLocalSourceId
        if (uri != null && sourceId != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { Log.w("SettingsScreen", "Permission non persistable pour $uri") }
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Jeux"
            val existingSource = customSources.firstOrNull { it.id == sourceId }
            pendingSourceForPlatform = if (existingSource != null) {
                existingSource.copy(name = name, path = uri.toString())
            } else {
                RomSource.local(name, uri.toString())
            }
            pendingSourceIsEdit = existingSource != null
        }
        editingLocalSourceId = null
    }

    // SAF picker — emplacement des sauvegardes
    val saveLocationPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure { Log.w("SettingsScreen", "Permission non persistable pour $uri") }
            viewModel.setSaveLocation(uri.toString())
        }
    }

    // SAF picker — emplacement des téléchargements
    val downloadLocationPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure { Log.w("SettingsScreen", "Permission non persistable pour $uri") }
            viewModel.setDownloadSourceId(uri.toString())
        }
    }

    // Platform picker dialog
    if (pendingSourceForPlatform != null) {
        PlatformPickerDialog(
            currentHint = pendingSourceForPlatform!!.platformHint,
            onDismiss = { pendingSourceForPlatform = null },
            onConfirm = { selectedHint ->
                val source = pendingSourceForPlatform!!.copy(platformHint = selectedHint)
                if (pendingSourceIsEdit) viewModel.updateSource(source) else viewModel.addSource(source)
                pendingSourceForPlatform = null
            },
        )
    }

    // Dialog ajout SMB (bibliothèque)
    if (showAddSmbDialog) {
        Dialog(onDismissRequest = { showAddSmbDialog = false }) {
            Card {
                SmbConfigForm(
                    onDismiss = { showAddSmbDialog = false },
                    onBack = { showAddSmbDialog = false },
                    editSource = null,
                    savedProfiles = smbLoginProfiles,
                    onSave = { name, server, path, credentials ->
                        pendingSourceForPlatform = RomSource.smb(name, server, path, credentials)
                        pendingSourceIsEdit = false
                        showAddSmbDialog = false
                    },
                )
            }
        }
    }

    // Dialog édition SMB (bibliothèque)
    if (editingSmbSource != null) {
        Dialog(onDismissRequest = { editingSmbSource = null }) {
            Card {
                SmbConfigForm(
                    onDismiss = { editingSmbSource = null },
                    onBack = { editingSmbSource = null },
                    editSource = editingSmbSource,
                    savedProfiles = smbLoginProfiles,
                    onSave = { name, server, path, credentials ->
                        editingSmbSource?.let { src ->
                            pendingSourceForPlatform = src.copy(name = name, path = "smb://$server$path", credentials = credentials)
                            pendingSourceIsEdit = true
                        }
                        editingSmbSource = null
                    },
                )
            }
        }
    }

    // Dialog choix type source (local vs SMB)
    if (showAddTypeDialog) {
        AlertDialog(
            onDismissRequest = { showAddTypeDialog = false },
            title = { Text(stringResource(R.string.settings_title_add_source)) },
            text = {
                Column {
                    TextButton(
                        onClick = { showAddTypeDialog = false; addLocalLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Text(stringResource(R.string.settings_picker_local_folder))
                        }
                    }
                    TextButton(
                        onClick = { showAddTypeDialog = false; showAddSmbDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Dns, contentDescription = null)
                            Text(stringResource(R.string.settings_picker_smb_server))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddTypeDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // Dialog type de dossier — sauvegardes
    if (showSavePickerDialog) {
        AlertDialog(
            onDismissRequest = { showSavePickerDialog = false },
            title = { Text(stringResource(R.string.settings_title_save_location)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showSavePickerDialog = false
                            saveLocationPickerLauncher.launch(
                                if (saveLocationUri.startsWith("content://")) Uri.parse(saveLocationUri) else null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Text(stringResource(R.string.settings_picker_local_folder))
                        }
                    }
                    TextButton(
                        onClick = { showSavePickerDialog = false; showSaveSmbDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Dns, contentDescription = null)
                            Text(stringResource(R.string.settings_picker_smb_server))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSavePickerDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // Dialog type de dossier — téléchargements
    if (showDownloadPickerDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadPickerDialog = false },
            title = { Text(stringResource(R.string.settings_title_download_location)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showDownloadPickerDialog = false
                            downloadLocationPickerLauncher.launch(
                                if (downloadSourceId.startsWith("content://")) Uri.parse(downloadSourceId) else null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Text(stringResource(R.string.settings_picker_local_folder))
                        }
                    }
                    TextButton(
                        onClick = { showDownloadPickerDialog = false; showDownloadSmbDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Dns, contentDescription = null)
                            Text(stringResource(R.string.settings_picker_smb_server))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDownloadPickerDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // SMB form — emplacement des sauvegardes
    if (showSaveSmbDialog) {
        Dialog(onDismissRequest = { showSaveSmbDialog = false }) {
            Card {
                SmbConfigForm(
                    onDismiss = { showSaveSmbDialog = false },
                    onBack = { showSaveSmbDialog = false },
                    editSource = if (saveLocationUri.startsWith("smb://")) {
                        RomSource(type = SourceType.SMB, name = "Sauvegardes", path = saveLocationUri, id = "_save")
                    } else null,
                    showDisplayNameField = false,
                    fixedDisplayName = "Sauvegardes",
                    savedProfiles = smbLoginProfiles,
                    onSave = { _, server, path, credentials ->
                        val normalizedPath = if (path.startsWith("/")) path else "/$path"
                        viewModel.setSaveLocation(
                            "smb://$server$normalizedPath",
                            credentials?.username ?: "",
                            credentials?.password ?: "",
                        )
                        showSaveSmbDialog = false
                    },
                )
            }
        }
    }

    // SMB form — emplacement des téléchargements
    if (showDownloadSmbDialog) {
        Dialog(onDismissRequest = { showDownloadSmbDialog = false }) {
            Card {
                SmbConfigForm(
                    onDismiss = { showDownloadSmbDialog = false },
                    onBack = { showDownloadSmbDialog = false },
                    editSource = if (downloadSourceId.startsWith("smb://")) {
                        RomSource(type = SourceType.SMB, name = "Téléchargements", path = downloadSourceId, id = "_dl")
                    } else null,
                    showDisplayNameField = false,
                    fixedDisplayName = "Téléchargements",
                    savedProfiles = smbLoginProfiles,
                    onSave = { _, server, path, credentials ->
                        val normalizedPath = if (path.startsWith("/")) path else "/$path"
                        viewModel.setDownloadSourceId(
                            "smb://$server$normalizedPath",
                            credentials?.username ?: "",
                            credentials?.password ?: "",
                        )
                        showDownloadSmbDialog = false
                    },
                )
            }
        }
    }

    // Confirmation suppression
    if (pendingDeleteSource != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteSource = null },
            title = { Text(stringResource(R.string.settings_source_remove_confirm_title)) },
            text = { Text(stringResource(R.string.settings_source_remove_confirm_message, pendingDeleteSource?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = { pendingDeleteSource?.let { viewModel.removeSource(it.id) }; pendingDeleteSource = null }) {
                    Text(stringResource(R.string.game_context_menu_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSource = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showAddSmbLoginDialog || editingSmbLoginProfile != null) {
        val initialProfile = editingSmbLoginProfile
        AlertDialog(
            onDismissRequest = {
                showAddSmbLoginDialog = false
                editingSmbLoginProfile = null
            },
            title = {
                Text(
                    stringResource(
                        if (initialProfile == null) R.string.settings_smb_login_add_title else R.string.settings_smb_login_edit_title,
                    ),
                )
            },
            text = {
                SmbLoginProfileForm(
                    initialProfile = initialProfile,
                    onCancel = {
                        showAddSmbLoginDialog = false
                        editingSmbLoginProfile = null
                    },
                    onSave = { profile ->
                        viewModel.addOrUpdateSmbLoginProfile(profile)
                        showAddSmbLoginDialog = false
                        editingSmbLoginProfile = null
                    },
                )
            },
            confirmButton = {},
            dismissButton = {},
        )
    }

    if (pendingDeleteSmbLoginProfile != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteSmbLoginProfile = null },
            title = { Text(stringResource(R.string.settings_smb_login_remove_title)) },
            text = { Text(stringResource(R.string.settings_smb_login_remove_message, pendingDeleteSmbLoginProfile?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteSmbLoginProfile?.let { viewModel.removeSmbLoginProfile(it.id) }
                    pendingDeleteSmbLoginProfile = null
                }) {
                    Text(stringResource(R.string.game_context_menu_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSmbLoginProfile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ── Card 1 : Bibliothèque de jeux ─────────────────────────────────────
    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.settings_category_library)) }) {
        // ── Liste des chemins ──────────────────────────────────────────
        if (customSources.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_library_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        } else {
            customSources.forEachIndexed { index, source ->
                LibraryPathRow(
                    source = source,
                    onEdit = {
                        when (source.type) {
                            SourceType.LOCAL -> {
                                editingLocalSourceId = source.id
                                editLocalLauncher.launch(
                                    if (source.path.startsWith("content://")) Uri.parse(source.path) else null,
                                )
                            }
                            SourceType.SMB -> editingSmbSource = source
                            else -> Unit
                        }
                    },
                    onDelete = { pendingDeleteSource = source },
                    enabled = !indexingInProgress,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            val spacing = 8.dp
            val availableWidth = maxWidth - spacing
            val addButtonWidth = availableWidth * (4f / 12f)
            val rescanButtonWidth = availableWidth - addButtonWidth

            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                Button(
                    onClick = { showAddTypeDialog = true },
                    enabled = !indexingInProgress,
                    modifier = Modifier.width(addButtonWidth),
                ) {
                    Text("+")
                }

                if (scanInProgress) {
                    Button(
                        onClick = { LibraryIndexScheduler.cancelLibrarySync(context) },
                        modifier = Modifier.width(rescanButtonWidth),
                    ) {
                        Text(stringResource(R.string.stop))
                    }
                } else {
                    Button(
                        onClick = { LibraryIndexScheduler.scheduleLibrarySync(context) },
                        enabled = !indexingInProgress,
                        modifier = Modifier.width(rescanButtonWidth),
                    ) {
                        Text(stringResource(R.string.rescan))
                    }
                }
            }
        }
    }

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.settings_category_smb_logins)) }) {
        if (smbLoginProfiles.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_smb_logins_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        } else {
            smbLoginProfiles.forEachIndexed { index, profile ->
                SmbLoginProfileRow(
                    profile = profile,
                    onEdit = { editingSmbLoginProfile = profile },
                    onDelete = { pendingDeleteSmbLoginProfile = profile },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Button(
                onClick = { showAddSmbLoginDialog = true },
                enabled = !indexingInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_smb_login_add_action))
            }
        }
    }

    // ── Card 2 : Emplacement de stockage ──────────────────────────────────
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_storage_locations)) },
    ) {
        // ── Dossier des sauvegardes ────────────────────────────────────
        val saveDisplayPath = uriToReadablePath(context, saveLocationUri)
            .ifEmpty { stringResource(R.string.settings_save_location_default) }
        StorageLocationRow(
            title = stringResource(R.string.settings_title_save_location),
            subtitle = saveDisplayPath,
            onDelete = if (saveLocationUri.isNotBlank()) { { viewModel.setSaveLocation("") } } else null,
            onClick = {
                when {
                    saveLocationUri.startsWith("smb://") -> showSaveSmbDialog = true
                    saveLocationUri.isNotBlank() -> saveLocationPickerLauncher.launch(Uri.parse(saveLocationUri))
                    else -> showSavePickerDialog = true
                }
            },
        )

        // ── Dossier de téléchargement ──────────────────────────────────
        val downloadDisplayPath = uriToReadablePath(context, downloadSourceId)
            .ifEmpty { stringResource(R.string.settings_download_location_default) }
        StorageLocationRow(
            title = stringResource(R.string.settings_title_download_location),
            subtitle = downloadDisplayPath,
            onDelete = if (downloadSourceId.isNotBlank()) { { viewModel.setDownloadSourceId("") } } else null,
            onClick = {
                when {
                    downloadSourceId.startsWith("smb://") -> showDownloadSmbDialog = true
                    downloadSourceId.isNotBlank() -> downloadLocationPickerLauncher.launch(Uri.parse(downloadSourceId))
                    else -> showDownloadPickerDialog = true
                }
            },
        )
        Text(
            text = stringResource(R.string.settings_download_location_library_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun SmbLoginProfileRow(
    profile: SmbLoginProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val protocolName = stringResource(
        when (profile.protocol) {
            NetworkProtocol.SMB -> R.string.network_protocol_smb
            NetworkProtocol.SFTP -> R.string.network_protocol_sftp
            NetworkProtocol.WEBDAV -> R.string.network_protocol_webdav
        },
    )
    val details = if (profile.username.isNotBlank()) {
        "$protocolName • ${profile.server} • ${profile.username}"
    } else {
        "$protocolName • ${profile.server}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.clickable(onClick = onDelete)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SmbLoginProfileForm(
    initialProfile: SmbLoginProfile?,
    onCancel: () -> Unit,
    onSave: (SmbLoginProfile) -> Unit,
) {
    var name by remember(initialProfile) { mutableStateOf(initialProfile?.name ?: "") }
    var server by remember(initialProfile) { mutableStateOf(initialProfile?.server?.substringBeforeLast(':', initialProfile.server) ?: "") }
    var port by remember(initialProfile) {
        mutableStateOf(
            initialProfile?.server?.substringAfterLast(':', "")
                ?.takeIf { it.toIntOrNull() in 1..65535 }
                ?: "",
        )
    }
    var protocol by remember(initialProfile) { mutableStateOf(initialProfile?.protocol ?: NetworkProtocol.SMB) }
    var isAnonymous by remember(initialProfile) { mutableStateOf(initialProfile?.username?.isBlank() != false) }
    var username by remember(initialProfile) { mutableStateOf(initialProfile?.username ?: "") }
    var password by remember(initialProfile) { mutableStateOf(initialProfile?.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var connectionTestState by remember(initialProfile) { mutableStateOf<ConnectionTestState>(ConnectionTestState.Idle) }
    var testMessageDialog by remember(initialProfile) { mutableStateOf<String?>(null) }
    val networkClient = remember { NetworkClient(SmbClient()) }
    val scope = rememberCoroutineScope()
    val successMessage = stringResource(R.string.sources_smb_connection_success)
    val unknownErrorMessage = stringResource(R.string.sources_network_test_unknown_error)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.sources_smb_display_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Column {
            Text(
                text = stringResource(R.string.network_protocol_label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NetworkProtocol.entries.forEach { proto ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    protocol = proto
                                    connectionTestState = ConnectionTestState.Idle
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RadioButton(
                                modifier = Modifier.size(20.dp),
                                selected = protocol == proto,
                                onClick = {
                                    protocol = proto
                                    connectionTestState = ConnectionTestState.Idle
                                },
                            )
                            Text(
                                text = stringResource(
                                    when (proto) {
                                        NetworkProtocol.SMB -> R.string.network_protocol_smb
                                        NetworkProtocol.SFTP -> R.string.network_protocol_sftp
                                        NetworkProtocol.WEBDAV -> R.string.network_protocol_webdav
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = server,
                onValueChange = {
                    server = it
                    connectionTestState = ConnectionTestState.Idle
                },
                label = { Text(stringResource(R.string.sources_smb_server)) },
                modifier = Modifier.weight(3f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it.filter(Char::isDigit).take(5)
                    connectionTestState = ConnectionTestState.Idle
                },
                label = { Text(stringResource(R.string.sources_smb_port)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.network_login_anonymous),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isAnonymous,
                onCheckedChange = {
                    isAnonymous = it
                    connectionTestState = ConnectionTestState.Idle
                },
            )
        }

        if (!isAnonymous) {
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    connectionTestState = ConnectionTestState.Idle
                },
                label = { Text(stringResource(R.string.sources_smb_username_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    connectionTestState = ConnectionTestState.Idle
                },
                label = { Text(stringResource(R.string.sources_smb_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                        )
                    }
                },
            )
        }

        OutlinedButton(
            onClick = {
                val trimmedServer = server.trim()
                connectionTestState = ConnectionTestState.Testing
                scope.launch {
                    val serverAddress = if (port.isNotBlank()) "$trimmedServer:${port.trim()}" else trimmedServer
                    val credentials = if (!isAnonymous && username.isNotBlank()) {
                        SmbCredentials(username.trim(), password)
                    } else {
                        null
                    }
                    val result = networkClient.testConnection(
                        protocol = protocol,
                        server = serverAddress,
                        path = "/",
                        credentials = credentials,
                    )
                    connectionTestState = if (result.isSuccess) {
                        ConnectionTestState.Success(successMessage)
                    } else {
                        ConnectionTestState.Error(
                            toFriendlyNetworkError(result.exceptionOrNull()?.message, protocol, unknownErrorMessage),
                        )
                    }

                    testMessageDialog = when (val state = connectionTestState) {
                        is ConnectionTestState.Success -> state.message
                        is ConnectionTestState.Error -> state.message
                        else -> null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = server.isNotBlank() && connectionTestState !is ConnectionTestState.Testing,
        ) {
            Text(
                text = if (connectionTestState is ConnectionTestState.Testing) {
                    stringResource(R.string.sources_smb_testing)
                } else {
                    stringResource(R.string.sources_smb_test_connection)
                },
                maxLines = 1,
            )
        }

        if (testMessageDialog != null) {
            AlertDialog(
                onDismissRequest = { testMessageDialog = null },
                title = {
                    Text(
                        if (connectionTestState is ConnectionTestState.Success) {
                            stringResource(R.string.sources_smb_connection_success)
                        } else {
                            stringResource(R.string.sources_network_test_failed_title)
                        },
                    )
                },
                text = { Text(testMessageDialog.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = { testMessageDialog = null }) {
                        Text(stringResource(R.string.ok))
                    }
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(5f),
            ) {
                Text(stringResource(R.string.sources_cancel), maxLines = 1)
            }
            Button(
                onClick = {
                    val trimmedServer = server.trim()
                    val serverAddress = if (port.isNotBlank()) "$trimmedServer:${port.trim()}" else trimmedServer
                    onSave(
                        (initialProfile ?: SmbLoginProfile(name = "", server = "")).copy(
                            name = name.trim().ifBlank { trimmedServer.substringBefore(':').ifBlank { trimmedServer } },
                            server = serverAddress,
                            protocol = protocol,
                            username = if (isAnonymous) "" else username.trim(),
                            password = if (isAnonymous) "" else password,
                        ),
                    )
                },
                modifier = Modifier.weight(7f),
                enabled = name.isNotBlank() && server.isNotBlank() && (isAnonymous || username.isNotBlank()),
            ) {
                Text(stringResource(R.string.sources_save), maxLines = 1)
            }
        }
    }
}

private fun toFriendlyNetworkError(raw: String?, protocol: NetworkProtocol, unknownError: String): String {
    val message = raw?.trim().orEmpty()
    if (message.isBlank()) return unknownError

    val lower = message.lowercase()
    return when {
        lower.contains("requires username/password") || lower.contains("auth fail") || lower.contains("authentication") ->
            "Identifiants invalides ou manquants pour ${protocol.name}."
        lower.contains("missing smb share name") ->
            "Aucun partage SMB n'est sélectionné. Ouvrez le navigateur de dossiers et choisissez d'abord un partage."
        lower.contains("status_bad_network_name") || lower.contains("bad_network_name") ->
            "Partage SMB introuvable. Vérifiez le nom du partage au début du chemin (ex: /games)."
        lower.contains("timeout") ->
            "Connexion expirée. Vérifiez l'adresse serveur, le port et le réseau."
        else -> message
    }
}

/** Custom row for storage location items — identical layout to LibraryPathRow so delete icons align. */
@Composable
private fun StorageLocationRow(
    title: String,
    subtitle: String,
    onDelete: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDelete != null) {
            Box(modifier = Modifier.clickable(onClick = onDelete)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Converts a raw URI string to a human-readable path for display. */
private fun uriToReadablePath(context: android.content.Context, uri: String): String {
    if (uri.isBlank()) return ""
    if (uri.startsWith("smb://")) return uri
    if (uri.startsWith("content://")) {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
            if (docId.contains(":")) {
                val (vol, path) = docId.split(":", limit = 2)
                if (vol == "primary") "/storage/emulated/0/$path" else "/storage/$vol/$path"
            } else docId
        }.getOrElse { Uri.decode(uri) }
    }
    return uri
}

@Composable
private fun LibraryPathRow(
    source: RomSource,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onEdit)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (source.type == SourceType.LOCAL) Icons.Default.Folder else Icons.Default.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row {
            Box(modifier = Modifier.clickable(enabled = enabled, onClick = onDelete)) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Dialog to pick a platform hint for a ROM source (Auto or a specific system). */
@Composable
private fun PlatformPickerDialog(
    currentHint: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val systems = remember {
        GameSystem.all()
            .filter { it.id != SystemID.UNKNOWN }
            .sortedBy { it.libretroFullName }
    }
    var selected by remember { mutableStateOf(currentHint) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_platform_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_platform_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    // Auto row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = null }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == null, onClick = { selected = null })
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(stringResource(R.string.settings_platform_auto), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.settings_platform_auto_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    // One row per system
                    items(systems) { system ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = system.id.dbname }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == system.id.dbname, onClick = { selected = system.id.dbname })
                            Text(
                                text = system.libretroFullName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}