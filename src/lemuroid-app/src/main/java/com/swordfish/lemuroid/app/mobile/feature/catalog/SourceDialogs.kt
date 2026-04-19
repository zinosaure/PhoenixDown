package com.swordfish.lemuroid.app.mobile.feature.catalog

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                            label = stringResource(R.string.sources_add_smb),
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

@Composable
fun SmbConfigForm(
    onDismiss: () -> Unit,
    onSave: (name: String, server: String, path: String, credentials: SmbCredentials?) -> Unit,
    onBack: () -> Unit,
    editSource: RomSource?
) {
    var name by remember { mutableStateOf(editSource?.name ?: "") }
    var server by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var useAuth by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf(editSource?.credentials?.username ?: "") }
    var password by remember { mutableStateOf(editSource?.credentials?.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    
    // Test connection state
    var connectionTestState by remember { mutableStateOf<ConnectionTestState>(ConnectionTestState.Idle) }
    val scope = rememberCoroutineScope()
    val smbClient = remember { SmbClient() }
    
    // Parse existing SMB path if editing
    LaunchedEffect(editSource) {
        editSource?.path?.let { smbPath ->
            // Parse smb://server/path
            val withoutScheme = smbPath.removePrefix("smb://")
            val slashIndex = withoutScheme.indexOf('/')
            if (slashIndex > 0) {
                server = withoutScheme.substring(0, slashIndex)
                path = withoutScheme.substring(slashIndex)
            } else {
                server = withoutScheme
                path = ""
            }
            useAuth = editSource.credentials != null
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
                text = if (editSource != null) stringResource(R.string.sources_smb_edit_title) else stringResource(R.string.sources_smb_connection_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.sources_smb_display_name)) },
            placeholder = { Text(stringResource(R.string.sources_smb_display_name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text(stringResource(R.string.sources_smb_server)) },
            placeholder = { Text(stringResource(R.string.sources_smb_server_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text(stringResource(R.string.sources_smb_path)) },
            placeholder = { Text(stringResource(R.string.sources_smb_path_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text(stringResource(R.string.sources_smb_path_hint)) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = useAuth,
                onCheckedChange = { useAuth = it }
            )
            Text(stringResource(R.string.sources_smb_use_auth))
        }
        
        if (useAuth) {
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.sources_smb_username_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.sources_smb_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Connection test result
        when (connectionTestState) {
            is ConnectionTestState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.sources_smb_connection_success),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            is ConnectionTestState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.sources_smb_connection_failed, (connectionTestState as ConnectionTestState.Error).message),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is ConnectionTestState.Testing -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sources_smb_testing))
                }
            }
            else -> {}
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    connectionTestState = ConnectionTestState.Testing
                    scope.launch {
                        val credentials = if (useAuth && username.isNotBlank()) {
                            SmbCredentials(username, password)
                        } else null
                        
                        // Parse share name from path
                        val shareName = path.removePrefix("/").split("/").firstOrNull() ?: ""
                        val result = smbClient.testConnection(server, shareName, credentials)
                        connectionTestState = if (result.isSuccess) {
                            ConnectionTestState.Success
                        } else {
                            ConnectionTestState.Error(
                                result.exceptionOrNull()?.message ?: "Unknown error"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = server.isNotBlank() && path.isNotBlank() && connectionTestState !is ConnectionTestState.Testing
            ) {
                Text(stringResource(R.string.sources_smb_test_connection))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.sources_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val displayName = name.ifBlank { server + path }
                        val credentials = if (useAuth && username.isNotBlank()) {
                            SmbCredentials(username, password)
                        } else null
                        onSave(displayName, server, path, credentials)
                    },
                    enabled = server.isNotBlank() && path.isNotBlank()
                ) {
                    Text(stringResource(R.string.sources_save))
                }
            }
        }
    }
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
    object Success : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}
