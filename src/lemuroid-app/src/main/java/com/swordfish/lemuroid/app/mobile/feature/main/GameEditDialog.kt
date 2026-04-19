package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Dialog for editing game metadata
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameEditDialog(
    game: Game,
    onDismiss: () -> Unit,
    onSave: (Game) -> Unit
) {
    var title by remember { mutableStateOf(game.title) }
    var systemId by remember { mutableStateOf(game.systemId) }
    var developer by remember { mutableStateOf(game.developer ?: "") }
    var expanded by remember { mutableStateOf(false) }
    
    // Available systems from SystemID enum
    val availableSystems = remember {
        SystemID.values().map { sysId ->
            sysId.dbname to sysId.dbname.uppercase().replace("_", " ")
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.game_edit_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.game_edit_field_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // System dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = availableSystems.find { it.first == systemId }?.second 
                            ?: systemId.uppercase().replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.game_edit_field_system)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableSystems.forEach { (sysDbName, sysDisplayName) ->
                            DropdownMenuItem(
                                text = { Text(sysDisplayName) },
                                onClick = {
                                    systemId = sysDbName
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Developer
                OutlinedTextField(
                    value = developer,
                    onValueChange = { developer = it },
                    label = { Text(stringResource(R.string.game_edit_field_developer)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // File info (read-only)
                Text(
                    text = "${stringResource(R.string.game_edit_file_label)} ${game.fileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updatedGame = game.copy(
                                title = title,
                                systemId = systemId,
                                developer = developer.ifBlank { null }
                            )
                            onSave(updatedGame)
                            onDismiss()
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(stringResource(R.string.game_edit_save))
                    }
                }
            }
        }
    }
}
