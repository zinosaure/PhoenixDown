package com.swordfish.lemuroid.app.mobile.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.storage.source.NetworkLoginProfile
import com.swordfish.lemuroid.lib.storage.source.NetworkProtocol
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials as NetworkCredentials
import com.swordfish.lemuroid.lib.storage.source.SourceType
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Dialog to add a new source (local or network)
 */
@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAddLocal: () -> Unit,
                onAddNetwork: (name: String, selectedProtocol: NetworkProtocol, server: String, share: String, path: String, credentials: NetworkCredentials?, profileId: String) -> Unit
) {
    var showNetworkForm by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (!showNetworkForm) {
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
                        
                        // Network source button
                        SourceTypeButton(
                            icon = Icons.Default.Dns,
                            label = stringResource(R.string.sources_add_network),
                            onClick = { showNetworkForm = true }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.sources_cancel))
                    }
                }
            } else {
                // Network configuration form
                NetworkConfigForm(
                    onDismiss = onDismiss,
                    onSave = { name, selectedProtocol, server, path, credentials, profileId ->
                        val selectedProfileId = profileId ?: return@NetworkConfigForm
                        onAddNetwork(name, selectedProtocol, server, "", path, credentials, selectedProfileId)
                        onDismiss()
                    },
                    onBack = { showNetworkForm = false },
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
fun NetworkConfigForm(
    onDismiss: () -> Unit,
    onSave: (name: String, protocol: NetworkProtocol, server: String, path: String, credentials: NetworkCredentials?, profileId: String?) -> Unit,
    onBack: () -> Unit,
    editSource: RomSource?,
    showDisplayNameField: Boolean = true,
    fixedDisplayName: String? = null,
    savedProfiles: List<NetworkLoginProfile> = emptyList(),
    preferredProfileId: String? = null,
    excludedProfileProtocols: Set<NetworkProtocol> = emptySet(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val pathRequester = remember { FocusRequester() }
    var name by remember { mutableStateOf(fixedDisplayName ?: editSource?.name ?: "") }
    var path by remember { mutableStateOf("/") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var showSavedProfilesDialog by remember { mutableStateOf(false) }
    var connectionTestState by remember { mutableStateOf<ConnectionTestState>(ConnectionTestState.Idle) }
    var testMessageDialog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val smbClient = remember { SmbClient() }
    val networkClient = remember { NetworkClient(smbClient) }
    val availableProfiles = savedProfiles.filter { it.protocol !in excludedProfileProtocols }
    val selectedProfile = availableProfiles.firstOrNull { it.id == selectedProfileId }

    fun applyProfile(profile: NetworkLoginProfile) {
        selectedProfileId = profile.id
        connectionTestState = ConnectionTestState.Idle
    }

    LaunchedEffect(editSource, availableProfiles, preferredProfileId) {
        val preferred = preferredProfileId?.let { id -> availableProfiles.firstOrNull { it.id == id } }
        if (preferred != null) {
            selectedProfileId = preferred.id
        }

        editSource?.path?.let { sourcePath ->
            val parsedUri = runCatching { URI(sourcePath) }.getOrNull()
            if (parsedUri != null) {
                val serverPart = buildString {
                    append(parsedUri.host ?: parsedUri.authority.orEmpty())
                    if (parsedUri.port > 0) append(":${parsedUri.port}")
                }
                path = parsedUri.path?.ifBlank { "/" } ?: "/"

                // Derive expected protocol from URI scheme
                val expectedProtocol = when (parsedUri.scheme?.lowercase()) {
                    "smb" -> NetworkProtocol.SMB
                    "sftp" -> NetworkProtocol.SFTP
                    "davs" -> NetworkProtocol.WEBDAV
                    "dav" -> NetworkProtocol.WEBDAV_HTTP
                    else -> null
                }
                val expectedUsername = editSource.credentials?.username ?: ""
                // Match by protocol + server + username (skip password to be resilient to changes)
                val matchedProfileByUser = availableProfiles.firstOrNull { profile ->
                    (expectedProtocol == null || profile.protocol == expectedProtocol) &&
                        profile.server.equals(serverPart, ignoreCase = true) &&
                        profile.username == expectedUsername
                }
                val matchedProfileByServer = availableProfiles.firstOrNull { profile ->
                    (expectedProtocol == null || profile.protocol == expectedProtocol) &&
                        profile.server.equals(serverPart, ignoreCase = true)
                }
                selectedProfileId = preferred?.id ?: matchedProfileByUser?.id ?: matchedProfileByServer?.id
            } else {
                path = "/"
                if (preferred == null) {
                    selectedProfileId = null
                }
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { requestFocusOrMove(pathRequester, focusManager) }),
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (availableProfiles.isEmpty()) {
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
                        text = selectedProfile?.name ?: stringResource(R.string.sources_network_saved_profile_pick),
                    )
                    selectedProfile?.let { profile ->
                        val protocolName = context.getString(
                            when (profile.protocol) {
                                NetworkProtocol.SMB -> R.string.network_protocol_smb
                                NetworkProtocol.SFTP -> R.string.network_protocol_sftp
                                NetworkProtocol.WEBDAV -> R.string.network_protocol_webdav
                                NetworkProtocol.WEBDAV_HTTP -> R.string.network_protocol_webdav_http
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

        OutlinedTextField(
            value = path,
            onValueChange = {
                path = it
                connectionTestState = ConnectionTestState.Idle
            },
            label = { Text(stringResource(R.string.sources_smb_path)) },
            placeholder = { Text(stringResource(R.string.sources_network_path_default)) },
            modifier = Modifier.fillMaxWidth().focusRequester(pathRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            supportingText = {
                Text(stringResource(R.string.sources_network_path_hint))
            },
        )

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
                    onSave(displayName, profile.protocol, profile.server, normalizedPath, profile.toCredentials(), profile.id)
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
            title = { Text(stringResource(R.string.sources_network_saved_profile_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableProfiles.forEach { profile ->
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
                                    NetworkProtocol.WEBDAV_HTTP -> R.string.network_protocol_webdav_http
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
private fun NetworkLoginProfile.toCredentials(): NetworkCredentials? =
    if (username.isNotBlank()) {
        NetworkCredentials(username, password)
    } else {
        null
    }

private fun normalizePath(path: String): String {
    val value = path.trim()
    if (value.isBlank()) return "/"
    return if (value.startsWith('/')) value else "/$value"
}

private fun requestFocusOrMove(requester: FocusRequester, focusManager: androidx.compose.ui.focus.FocusManager) {
    runCatching { requester.requestFocus() }
        .onFailure { focusManager.moveFocus(FocusDirection.Down) }
}

private fun buildNetworkLocationUri(protocol: NetworkProtocol, server: String, path: String): String {
    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    val scheme = when (protocol) {
        NetworkProtocol.SMB -> "smb"
        NetworkProtocol.SFTP -> "sftp"
        NetworkProtocol.WEBDAV -> "davs"
        NetworkProtocol.WEBDAV_HTTP -> "dav"
    }
    return "$scheme://$server$normalizedPath"
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
private fun NetworkPathBrowserDialog(
    profile: NetworkLoginProfile,
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
    var entries by remember(profile.id, currentPath) { mutableStateOf<List<NetworkDirectoryEntry>>(emptyList()) }
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
                NetworkConfigForm(
                    onDismiss = { editingSource = null },
                    onSave = { name, protocol, server, path, credentials, profileId ->
                        val updatedSource = editingSource!!.copy(
                            name = name,
                            path = buildNetworkLocationUri(protocol, server, path),
                            credentials = credentials,
                            networkProfileId = profileId,
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
