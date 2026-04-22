package com.swordfish.lemuroid.app.mobile.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.lib.storage.source.NetworkProtocol
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SmbLoginProfile
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials as SmbCredentials
import com.swordfish.lemuroid.lib.storage.source.SourceType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import kotlinx.coroutines.launch

/**
 * Dialog to add a new source (Local or SMB)
 */
@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAddLocal: () -> Unit,
    onAddSmb: (name: String, server: String, share: String, path: String, credentials: SmbCredentials?) -> Unit
) {
    var showSmbForm by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (!showSmbForm) {
                // Source type selection
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.sources_add_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Local folder button
                        SourceTypeButton(
                            icon = Icons.Default.Folder,
                            label = stringResource(R.string.sources_add_local),
                            onClick = {
                                onAddLocal()
                                onDismiss()
                            }
                        )
                        
                        // SMB/NAS button
                        SourceTypeButton(
                            icon = Icons.Default.Dns,
                            label = stringResource(R.string.sources_add_network),
                            onClick = { showSmbForm = true }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.sources_cancel))
                    }
                }
            } else {
                // SMB configuration form
                SmbConfigForm(
                    onDismiss = onDismiss,
                    onSave = { name, server, path, credentials ->
                        onAddSmb(name, server, "", path, credentials)
                        onDismiss()
                    },
                    onBack = { showSmbForm = false },
                    editSource = null
                )
            }
        }
    }
}

@Composable
private fun SourceTypeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.size(120.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbConfigForm(
    onDismiss: () -> Unit,
    onSave: (name: String, server: String, path: String, credentials: SmbCredentials?) -> Unit,
    onBack: () -> Unit,
    editSource: RomSource?,
    showDisplayNameField: Boolean = true,
    fixedDisplayName: String? = null,
    savedProfiles: List<SmbLoginProfile> = emptyList(),
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(fixedDisplayName ?: editSource?.name ?: "") }
    var path by remember { mutableStateOf("/") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var showSavedProfilesDialog by remember { mutableStateOf(false) }
    var showPathBrowserDialog by remember { mutableStateOf(false) }
    var connectionTestState by remember { mutableStateOf<ConnectionTestState>(ConnectionTestState.Idle) }
    var testMessageDialog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val smbClient = remember { SmbClient() }
    val networkClient = remember { NetworkClient(smbClient) }
    val selectedProfile = savedProfiles.firstOrNull { it.id == selectedProfileId }

    fun applyProfile(profile: SmbLoginProfile) {
        selectedProfileId = profile.id
        connectionTestState = ConnectionTestState.Idle
    }

    LaunchedEffect(editSource) {
        editSource?.path?.let { smbPath ->
            val withoutScheme = smbPath.removePrefix("smb://")
            val slashIndex = withoutScheme.indexOf('/')
            if (slashIndex > 0) {
                val serverPart = withoutScheme.substring(0, slashIndex)
                path = withoutScheme.substring(slashIndex).ifBlank { "/" }
                val matchedProfile = savedProfiles.firstOrNull {
                    it.server.equals(serverPart, ignoreCase = true) &&
                        it.username == (editSource.credentials?.username ?: "") &&
                        it.password == (editSource.credentials?.password ?: "")
                }
                selectedProfileId = matchedProfile?.id
            } else {
                path = "/"
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = if (editSource != null) stringResource(R.string.sources_network_edit_title) else stringResource(R.string.sources_network_connection_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        if (showDisplayNameField) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.sources_smb_display_name)) },
                placeholder = { Text(stringResource(R.string.sources_smb_display_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (savedProfiles.isEmpty()) {
            Text(
                text = stringResource(R.string.sources_network_profile_required),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedButton(
                onClick = { showSavedProfilesDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = selectedProfile?.name ?: stringResource(R.string.sources_smb_saved_profile_pick),
                    )
                    selectedProfile?.let { profile ->
                        val protocolName = context.getString(
                            when (profile.protocol) {
                                NetworkProtocol.SMB -> R.string.network_protocol_smb
                                NetworkProtocol.SFTP -> R.string.network_protocol_sftp
                                NetworkProtocol.WEBDAV -> R.string.network_protocol_webdav
                            },
                        )
                        Text(
                            text = "$protocolName • ${profile.server}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val canBrowsePath = selectedProfile != null && selectedProfile.protocol == NetworkProtocol.SMB

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canBrowsePath) {
                    showPathBrowserDialog = true
                },
        ) {
            OutlinedTextField(
                value = path,
                onValueChange = {},
                label = { Text(stringResource(R.string.sources_smb_path)) },
                placeholder = { Text(stringResource(R.string.sources_network_path_default)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = true,
                trailingIcon = {
                    IconButton(
                        enabled = canBrowsePath,
                        onClick = { showPathBrowserDialog = true },
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                    }
                },
                supportingText = {
                    Text(
                        if (selectedProfile?.protocol == NetworkProtocol.SMB) {
                            stringResource(R.string.sources_network_path_hint)
                        } else {
                            stringResource(R.string.sources_network_path_hint_smb_only)
                        },
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                val profile = selectedProfile ?: return@OutlinedButton
                connectionTestState = ConnectionTestState.Testing
                scope.launch {
                    val credentials = profile.toCredentials()
                    val result = networkClient.testConnection(
                        protocol = profile.protocol,
                        server = profile.server,
                        path = path,
                        credentials = credentials,
                    )
                    connectionTestState = if (result.isSuccess) {
                        ConnectionTestState.Success(context.getString(R.string.sources_smb_connection_success))
                    } else {
                        ConnectionTestState.Error(
                            toFriendlyNetworkError(
                                result.exceptionOrNull()?.message,
                                profile.protocol,
                                context.getString(R.string.sources_network_test_unknown_error),
                            ),
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
            enabled = selectedProfile != null && path.removePrefix("/").isNotBlank() && connectionTestState !is ConnectionTestState.Testing,
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

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(5f),
            ) {
                Text(stringResource(R.string.sources_cancel), maxLines = 1)
            }
            Button(
                onClick = {
                    val profile = selectedProfile ?: return@Button
                    val displayName = (fixedDisplayName ?: name).ifBlank { "${profile.name} $path" }
                    val normalizedPath = if (path.startsWith("/")) path else "/$path"
                    onSave(displayName, profile.server, normalizedPath, profile.toCredentials())
                },
                modifier = Modifier.weight(7f),
                enabled = selectedProfile != null && path.removePrefix("/").isNotBlank() && (!showDisplayNameField || name.isNotBlank()),
            ) {
                Text(stringResource(R.string.sources_finish), maxLines = 1)
            }
        }
    }

    if (showSavedProfilesDialog) {
        AlertDialog(
            onDismissRequest = { showSavedProfilesDialog = false },
            title = { Text(stringResource(R.string.sources_smb_saved_profile_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedProfiles.forEach { profile ->
                        TextButton(
                            onClick = {
                                applyProfile(profile)
                                showSavedProfilesDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val protocolName = stringResource(
                                when (profile.protocol) {
                                    NetworkProtocol.SMB -> R.string.network_protocol_smb
                                    NetworkProtocol.SFTP -> R.string.network_protocol_sftp
                                    NetworkProtocol.WEBDAV -> R.string.network_protocol_webdav
                                },
                            )
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(text = profile.name)
                                Text(
                                    text = if (profile.username.isNotBlank()) {
                                        "$protocolName • ${profile.server} • ${profile.username}"
                                    } else {
                                        "$protocolName • ${profile.server}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSavedProfilesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showPathBrowserDialog && selectedProfile != null) {
        SmbPathBrowserDialog(
            profile = selectedProfile,
            initialPath = path,
            networkClient = networkClient,
            onDismiss = { showPathBrowserDialog = false },
            onSelect = {
                path = it
                connectionTestState = ConnectionTestState.Idle
                showPathBrowserDialog = false
            },
        )
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
private fun SmbLoginProfile.toCredentials(): SmbCredentials? =
    if (username.isNotBlank()) {
        SmbCredentials(username, password)
    } else {
        null
    }

private fun normalizePath(path: String): String {
    val value = path.trim()
    if (value.isBlank()) return "/"
    return if (value.startsWith('/')) value else "/$value"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionTestStatus(
    testState: ConnectionTestState,
    modifier: Modifier = Modifier,
) {
    when (testState) {
        is ConnectionTestState.Testing -> CircularProgressIndicator(modifier = modifier.size(24.dp), strokeWidth = 2.dp)
        is ConnectionTestState.Success -> {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(testState.message) } },
                state = rememberTooltipState(),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        is ConnectionTestState.Error -> {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(testState.message) } },
                state = rememberTooltipState(),
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
        ConnectionTestState.Idle -> Spacer(modifier = modifier.size(24.dp))
    }
}

@Composable
private fun SmbPathBrowserDialog(
    profile: SmbLoginProfile,
    initialPath: String,
    networkClient: NetworkClient,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val initialSegments = initialPath.removePrefix("/").split('/').filter { it.isNotBlank() }
    var shareName by remember(profile.id, initialPath) { mutableStateOf(initialSegments.firstOrNull() ?: "") }
    var currentPath by remember(profile.id, initialPath) {
        mutableStateOf(initialPath.takeIf { it.startsWith("/") && it.length > 1 } ?: "/")
    }
    var entries by remember(profile.id, currentPath) { mutableStateOf<List<SmbDirectoryEntry>>(emptyList()) }
    var isLoading by remember(profile.id, currentPath) { mutableStateOf(true) }
    var loadError by remember(profile.id, currentPath) { mutableStateOf<String?>(null) }
    var autoResetToRootDone by remember(profile.id) { mutableStateOf(false) }

    fun normalizePath(path: String): String {
        val value = path.trim()
        if (value.isBlank()) return "/"
        return if (value.startsWith('/')) value else "/$value"
    }

    fun splitPath(path: String): Pair<String?, String> {
        val segments = path.removePrefix("/").split('/').filter { it.isNotBlank() }
        val share = segments.firstOrNull()
        val subPath = segments.drop(1).joinToString("/")
        return share to subPath
    }

    fun parentPath(path: String): String {
        val segments = path.removePrefix("/").split('/').filter { it.isNotBlank() }
        return when {
            segments.isEmpty() -> "/"
            segments.size == 1 -> "/"
            else -> "/" + segments.dropLast(1).joinToString("/")
        }
    }

    LaunchedEffect(profile.id, currentPath) {
        isLoading = true
        loadError = null
        val result = networkClient.listDirectories(
            protocol = profile.protocol,
            server = profile.server,
            path = normalizePath(currentPath),
            credentials = profile.toCredentials(),
        )

        result.onSuccess {
            entries = it
            isLoading = false
        }.onFailure {
            entries = emptyList()
            loadError = toFriendlyNetworkError(it.message, profile.protocol, "Erreur inconnue")
            isLoading = false

            val raw = it.message?.lowercase().orEmpty()
            if (!autoResetToRootDone && currentPath != "/" &&
                (raw.contains("status_bad_network_name") || raw.contains("bad_network_name"))) {
                autoResetToRootDone = true
                currentPath = "/"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sources_network_browse_path)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = normalizePath(currentPath),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (currentPath == "/") {
                    if (profile.protocol == NetworkProtocol.SMB) {
                        OutlinedTextField(
                            value = shareName,
                            onValueChange = { shareName = it.trim() },
                            label = { Text(stringResource(R.string.sources_network_share_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedButton(
                            onClick = { currentPath = "/${shareName.trim().removePrefix("/")}" },
                            enabled = shareName.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.sources_network_open_share))
                        }
                    }
                }
                if (currentPath != "/") {
                    TextButton(onClick = { currentPath = parentPath(currentPath) }) {
                        Text(stringResource(R.string.sources_network_go_up))
                    }
                }
                when {
                    isLoading -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.sources_network_loading))
                        }
                    }
                    loadError != null -> Text(
                        text = stringResource(R.string.sources_network_browse_error, loadError ?: ""),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        entries.forEach { entry ->
                            TextButton(
                                onClick = { currentPath = entry.path },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Text(entry.name)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelect(normalizePath(currentPath)) }, enabled = currentPath != "/" && loadError == null) {
                Text(stringResource(R.string.sources_choose_path))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sources_cancel))
            }
        },
    )
}


/**
 * Dialog to manage existing sources (edit/delete)
 */
@Composable
fun ManageSourcesDialog(
    sources: List<RomSource>,
    onDismiss: () -> Unit,
    onEdit: (RomSource) -> Unit,
    onDelete: (RomSource) -> Unit
) {
    var editingSource by remember { mutableStateOf<RomSource?>(null) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (editingSource != null && editingSource!!.type == SourceType.SMB) {
                // Show edit form for SMB
                SmbConfigForm(
                    onDismiss = { editingSource = null },
                    onSave = { name, server, path, credentials ->
                        val updatedSource = editingSource!!.copy(
                            name = name,
                            path = "smb://$server$path",
                            credentials = credentials
                        )
                        onEdit(updatedSource)
                        editingSource = null
                    },
                    onBack = { editingSource = null },
                    editSource = editingSource
                )
            } else {
                // Show sources list
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sources_manage_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (sources.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sources_no_custom),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        sources.forEach { source ->
                            SourceItem(
                                source = source,
                                onEdit = { 
                                    if (source.type == SourceType.SMB) {
                                        editingSource = source
                                    }
                                    // Local sources can't be edited (path is fixed)
                                },
                                onDelete = { onDelete(source) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItem(
    source: RomSource,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (source.type) {
                    SourceType.LOCAL -> Icons.Default.Folder
                    SourceType.SMB -> Icons.Default.Dns
                    SourceType.ARCHIVE_ORG -> Icons.Default.Cloud
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = source.path,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (source.type != SourceType.ARCHIVE_ORG) {
                // Only show edit button for SMB sources (Local paths can't be edited)
                if (source.type == SourceType.SMB) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.sources_edit))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.sources_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * State for SMB connection testing
 */
sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}
