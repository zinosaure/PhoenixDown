package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.catalog.SmbConfigForm
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials as SmbCredentials
import com.swordfish.lemuroid.lib.storage.source.SourceType
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
            viewModel = viewModel
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
private fun ConsolesSettings(
    indexingInProgress: Boolean,
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
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
    val customSources = remember(allSources) { allSources.filter { it.type != SourceType.ARCHIVE_ORG } }

    var pendingDeleteSource by remember { mutableStateOf<RomSource?>(null) }
    var editingSmbSource by remember { mutableStateOf<RomSource?>(null) }
    var showAddSmbDialog by remember { mutableStateOf(false) }
    var editingLocalSourceId by remember { mutableStateOf<String?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

    // SAF picker — ajouter dossier local
    val addLocalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { Log.w("SettingsScreen", "Permission non persistable pour $uri") }
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Jeux"
            viewModel.addSource(RomSource.local(name, uri.toString()))
        }
    }

    // SAF picker — modifier dossier local existant
    val editLocalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val sourceId = editingLocalSourceId
        if (uri != null && sourceId != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { Log.w("SettingsScreen", "Permission non persistable pour $uri") }
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Jeux"
            customSources.firstOrNull { it.id == sourceId }?.let { viewModel.updateSource(it.copy(name = name, path = uri.toString())) }
                ?: viewModel.addSource(RomSource.local(name, uri.toString()))
        }
        editingLocalSourceId = null
    }

    // Dialog ajout SMB
    if (showAddSmbDialog) {
        Dialog(onDismissRequest = { showAddSmbDialog = false }) {
            Card {
                SmbConfigForm(
                    onDismiss = { showAddSmbDialog = false },
                    onBack = { showAddSmbDialog = false },
                    editSource = null,
                    onSave = { name, server, path, credentials ->
                        viewModel.addSource(RomSource.smb(name, server, path, credentials))
                        showAddSmbDialog = false
                    },
                )
            }
        }
    }

    // Dialog édition SMB
    if (editingSmbSource != null) {
        Dialog(onDismissRequest = { editingSmbSource = null }) {
            Card {
                SmbConfigForm(
                    onDismiss = { editingSmbSource = null },
                    onBack = { editingSmbSource = null },
                    editSource = editingSmbSource,
                    onSave = { name, server, path, credentials ->
                        editingSmbSource?.let { src ->
                            viewModel.updateSource(src.copy(name = name, path = "smb://$server$path", credentials = credentials))
                        }
                        editingSmbSource = null
                    },
                )
            }
        }
    }

    // Confirmation suppression
    if (pendingDeleteSource != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteSource = null },
            title = { Text("Supprimer le dossier") },
            text = { Text("Retirer « ${pendingDeleteSource?.name} » de la bibliothèque ?") },
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

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.roms)) }) {
        // En-tête bibliothèque virtuelle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "Bibliothèque de jeux",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            // Bouton + avec menu déroulant
            IconButton(
                onClick = { showAddMenu = true },
                enabled = !indexingInProgress,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un dossier")
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Dossier local")
                            }
                        },
                        onClick = { showAddMenu = false; addLocalLauncher.launch(null) },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Partage SMB (réseau)")
                            }
                        },
                        onClick = { showAddMenu = false; showAddSmbDialog = true },
                    )
                }
            }
        }

        HorizontalDivider()

        // Liste des chemins dans la bibliothèque
        if (customSources.isEmpty()) {
            Text(
                text = "Aucun dossier configuré. Appuyez sur + pour en ajouter un.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        } else {
            customSources.forEach { source ->
                LibraryPathRow(
                    source = source,
                    onEdit = {
                        when (source.type) {
                            SourceType.LOCAL -> { editingLocalSourceId = source.id; editLocalLauncher.launch(null) }
                            SourceType.SMB -> editingSmbSource = source
                            else -> Unit
                        }
                    },
                    onDelete = { pendingDeleteSource = source },
                    enabled = !indexingInProgress,
                )
            }
        }

        HorizontalDivider()

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
            .padding(start = 24.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (source.type == SourceType.LOCAL) Icons.Default.Folder else Icons.Default.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
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
        // Icônes ✎ et 🗑 serrées ensemble
        Row {
            IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", modifier = Modifier.size(18.dp))
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 24.dp))
}


