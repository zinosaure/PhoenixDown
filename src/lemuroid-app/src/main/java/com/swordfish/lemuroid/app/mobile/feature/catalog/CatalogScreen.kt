package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import kotlinx.coroutines.launch

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    modifier: Modifier = Modifier,
    gameMetadataProvider: com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider,
    romDownloader: RomDownloader,
    viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory(LocalContext.current.applicationContext, gameMetadataProvider, romDownloader))
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    
    // Source management
    val sourceManager = remember { SourceManager(context) }
    var sources by remember { mutableStateOf(sourceManager.getSources()) }
    var selectedSourceType by remember { mutableStateOf<SourceType?>(null) } // null = all
    
    // Dialog states
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showManageSourcesDialog by remember { mutableStateOf(false) }
    
    // State for reloading external files
    var shouldReloadLocalFiles by remember { mutableStateOf(true) }
    
    // SAF Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Take persistable permission
            context.contentResolver.takePersistableUriPermission(
                selectedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            // Get folder name
            val folderName = DocumentFile.fromTreeUri(context, selectedUri)?.name ?: "Local Folder"
            
            // Add source
            val newSource = RomSource.local(folderName, selectedUri.toString())
            sourceManager.addSource(newSource)
            sources = sourceManager.getSources()
            
            // Trigger reload of local files
            shouldReloadLocalFiles = true
        }
    }
    
    // States for Local/SMB files
    var localFiles by remember { mutableStateOf<List<LocalFile>>(emptyList()) }
    var smbFiles by remember { mutableStateOf<List<Pair<RomSource, SmbFile>>>(emptyList()) }
    var isLoadingExternalFiles by remember { mutableStateOf(false) }
    
    // SMB download states
    var smbDownloadsInProgress by remember { mutableStateOf<Set<String>>(emptySet()) }
    var smbDownloadedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    val romDownloader = viewModel.romDownloader
    
    // Load files from Local and SMB sources
    val localScanner = remember { LocalFolderScanner(context) }
    val smbClient = remember { SmbClient() }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(sources, shouldReloadLocalFiles) {
        if (shouldReloadLocalFiles) {
            isLoadingExternalFiles = true
            
            // Load local files
            val allLocalFiles = mutableListOf<LocalFile>()
            sources.filter { it.type == SourceType.LOCAL }.forEach { source ->
                try {
                    val uri = Uri.parse(source.path)
                    val result = localScanner.scanFolder(uri)
                    result.onSuccess { files ->
                        allLocalFiles.addAll(files)
                    }
                } catch (e: Exception) {
                    // Log error but continue
                }
            }
            localFiles = allLocalFiles
            
            // Load SMB files
            val allSmbFiles = mutableListOf<Pair<RomSource, SmbFile>>()
            sources.filter { it.type == SourceType.SMB }.forEach { source ->
                try {
                    // Parse SMB path: smb://server/sharename/optional/subpath
                    // El path ya incluye el sharename como primera parte
                    val smbPath = source.path.removePrefix("smb://")
                    val slashIndex = smbPath.indexOf('/')
                    if (slashIndex <= 0) {
                        Log.w("CatalogScreen", "Invalid SMB path: ${source.path}")
                        return@forEach
                    }
                    
                    val server = smbPath.substring(0, slashIndex)
                    val fullPath = smbPath.substring(slashIndex) // /sharename/subpath
                    
                    // Extraer sharename (primera parte del path) y subpath (resto)
                    val pathParts = fullPath.removePrefix("/").split("/", limit = 2)
                    val share = pathParts.getOrNull(0) ?: return@forEach
                    val subPath = if (pathParts.size > 1) pathParts[1] else ""
                    
                    Log.d("CatalogScreen", "SMB: server=$server, share=$share, subPath=$subPath")
                    
                    val result = smbClient.listFiles(server, share, subPath, source.credentials)
                    result.onSuccess { files ->
                        Log.d("CatalogScreen", "SMB found ${files.size} files")
                        files.forEach { file ->
                            allSmbFiles.add(source to file)
                        }
                    }.onFailure { e ->
                        Log.e("CatalogScreen", "SMB error: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e("CatalogScreen", "SMB exception: ${e.message}", e)
                }
            }
            smbFiles = allSmbFiles
            
            isLoadingExternalFiles = false
            shouldReloadLocalFiles = false
        }
    }
    
    // Derived: should show only Archive.org packs or filter by source type
    val showArchiveOrgContent = selectedSourceType == null || selectedSourceType == SourceType.ARCHIVE_ORG
    val showLocalContent = selectedSourceType == null || selectedSourceType == SourceType.LOCAL
    val showSmbContent = selectedSourceType == null || selectedSourceType == SourceType.SMB
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sources row with Add button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source type chips
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // All sources
                    item {
                        FilterChip(
                            selected = selectedSourceType == null,
                            onClick = { selectedSourceType = null },
                            label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    // Cloud (Archive.org)
                    item {
                        FilterChip(
                            selected = selectedSourceType == SourceType.ARCHIVE_ORG,
                            onClick = { selectedSourceType = SourceType.ARCHIVE_ORG },
                            leadingIcon = { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(16.dp)) },
                            label = { Text(stringResource(R.string.catalog_filter_cloud), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    // Local
                    if (sources.any { it.type == SourceType.LOCAL }) {
                        item {
                            FilterChip(
                                selected = selectedSourceType == SourceType.LOCAL,
                                onClick = { selectedSourceType = SourceType.LOCAL },
                                leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.catalog_filter_local), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    // SMB
                    if (sources.any { it.type == SourceType.SMB }) {
                        item {
                            FilterChip(
                                selected = selectedSourceType == SourceType.SMB,
                                onClick = { selectedSourceType = SourceType.SMB },
                                leadingIcon = { Icon(Icons.Default.Dns, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.catalog_filter_smb), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                
                // Add Source button
                IconButton(onClick = { showAddSourceDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.sources_add_title), tint = MaterialTheme.colorScheme.onSurface)
                }
                
                // Manage Sources button (if custom sources exist)
                if (sources.size > 1) {
                    IconButton(onClick = { showManageSourcesDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.sources_manage_title), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            
            // Chips de sistemas
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(viewModel.availableSystems) { (id, name) ->
                    FilterChip(
                        selected = uiState.selectedSystem == id,
                        onClick = { viewModel.selectSystem(id) },
                        label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            
            // Barra de búsqueda + filtro región
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { 
                        focusManager.clearFocus()
                        viewModel.searchPacks()  // Execute search when user presses Enter
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent {
                            if (it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                                true
                            } else {
                                false
                            }
                        }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Dropdown de región
                var regionExpanded by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = uiState.selectedRegion.isNotEmpty(),
                        onClick = { regionExpanded = true },
                        label = { 
                            Text(
                                viewModel.availableRegions.find { it.first == uiState.selectedRegion }?.second?.take(6) ?: "🌍",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                    DropdownMenu(expanded = regionExpanded, onDismissRequest = { regionExpanded = false }) {
                        viewModel.availableRegions.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.setRegionFilter(id)
                                    regionExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            // Info de resultados + ordenación + botón descargas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.filteredPacks.size} de ${uiState.totalResults} paquetes",
                    style = MaterialTheme.typography.bodySmall
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ordenación
                    var sortExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { sortExpanded = true }) {
                            Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                viewModel.sortOptions.find { it.first == uiState.sortOption }?.second ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            viewModel.sortOptions.forEach { (option, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        sortExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Botón descargas
                    val activeDownloads = downloads.count { 
                        it.value.status == RomDownloader.DownloadStatus.DOWNLOADING || 
                        it.value.status == RomDownloader.DownloadStatus.PENDING 
                    }
                    BadgedBox(
                        badge = { if (activeDownloads > 0) Badge { Text("$activeDownloads") } }
                    ) {
                        IconButton(onClick = { viewModel.toggleDownloadsPanel() }) {
                            Icon(Icons.Default.Download, stringResource(R.string.catalog_downloads), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            // Contenido principal - filtrado por tipo de fuente
            val hasLocalOrSmbContent = localFiles.isNotEmpty() || smbFiles.isNotEmpty()
            val isLoadingAny = uiState.isLoading || isLoadingExternalFiles
            
            when {
                isLoadingAny -> LoadingContent()
                uiState.error != null && showArchiveOrgContent -> ErrorContent(uiState.error!!) { viewModel.searchPacks() }
                else -> {
                    // Show combined content based on filter
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Archive.org packs (only if showing cloud content)
                        if (showArchiveOrgContent && uiState.filteredPacks.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.catalog_section_archive_org),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            items(uiState.filteredPacks) { pack ->
                                PackCard(
                                    pack = pack,
                                    onClick = { viewModel.onPackSelected(pack) }
                                )
                            }
                            if (uiState.hasMorePages) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (uiState.isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        } else {
                                            TextButton(onClick = { viewModel.loadMorePacks() }) {
                                                Text(stringResource(R.string.catalog_load_more))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Local files (only if showing local content)
                        if (showLocalContent && localFiles.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.catalog_section_local_files),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            items(localFiles) { file ->
                                LocalFileCard(
                                    file = file,
                                    onPlay = {
                                        // La ROM local ya está en el dispositivo, se puede lanzar directamente
                                        // Esto se integrará con el sistema de biblioteca existente
                                    }
                                )
                            }
                        }
                        
                        // SMB files (only if showing SMB content)
                        if (showSmbContent && smbFiles.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.catalog_section_smb_nas),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            // Filter SMB files by search query
                            val filteredSmbFiles = if (uiState.searchQuery.isNotEmpty()) {
                                smbFiles.filter { (_, file) -> 
                                    file.name.contains(uiState.searchQuery, ignoreCase = true)
                                }
                            } else {
                                smbFiles
                            }
                            items(filteredSmbFiles) { (source, file) ->
                                val isDownloading = file.path in smbDownloadsInProgress
                                val isDownloaded = file.path in smbDownloadedFiles || 
                                    romDownloader.isFileInRomsDir(file.name)
                                
                                SmbFileCard(
                                    file = file,
                                    sourceName = source.name,
                                    isDownloading = isDownloading,
                                    isDownloaded = isDownloaded,
                                    onDownload = {
                                        if (!isDownloading) {
                                            smbDownloadsInProgress = smbDownloadsInProgress + file.path
                                            coroutineScope.launch {
                                                try {
                                                    romDownloader.downloadFromSmbSource(file, source)
                                                    smbDownloadedFiles = smbDownloadedFiles + file.path
                                                } catch (e: Exception) {
                                                    Log.e("CatalogScreen", "Download failed", e)
                                                } finally {
                                                    smbDownloadsInProgress = smbDownloadsInProgress - file.path
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        // Empty state
                        if ((selectedSourceType == null && uiState.filteredPacks.isEmpty() && localFiles.isEmpty() && smbFiles.isEmpty()) ||
                            (selectedSourceType == SourceType.ARCHIVE_ORG && uiState.filteredPacks.isEmpty()) ||
                            (selectedSourceType == SourceType.LOCAL && localFiles.isEmpty()) ||
                            (selectedSourceType == SourceType.SMB && smbFiles.isEmpty())) {
                            item {
                                EmptyContent()
                            }
                        }
                    }
                }
            }
        }
        
        // Panel de descargas (fullscreen overlay)
        if (uiState.showDownloadsPanel) {
            DownloadsPanel(
                downloads = downloads.values.toList(),
                onDismiss = { viewModel.toggleDownloadsPanel() },
                onCancelDownload = { viewModel.cancelDownload(it) },
                onClearCompleted = { viewModel.clearCompletedDownloads() }
            )
        }
    }
    
    // Dialog de detalles del paquete
    uiState.selectedPack?.let { pack ->
        PackDetailsDialog(
            pack = pack,
            files = uiState.downloadableFiles,
            isLoadingFiles = uiState.isLoadingFiles,
            isFileDownloaded = { fileName -> 
                val downloadId = "${pack.id}_${fileName}"
                val isCompletedInMemory = downloads[downloadId]?.status == RomDownloader.DownloadStatus.COMPLETED
                isCompletedInMemory || viewModel.isFileDownloaded(pack, fileName) 
            },
            isFileDownloading = { fileName ->
                val downloadId = "${pack.id}_${fileName}"
                val status = downloads[downloadId]?.status
                status == RomDownloader.DownloadStatus.PENDING || 
                status == RomDownloader.DownloadStatus.DOWNLOADING || 
                status == RomDownloader.DownloadStatus.PROCESSING
            },
            onDismiss = { viewModel.clearSelectedPack() },
            onDownloadFile = { file -> viewModel.startDownload(pack, file) },
            onDownloadAll = { viewModel.downloadAllFiles(pack, uiState.downloadableFiles) }
        )
    }
    
    // Add Source Dialog
    if (showAddSourceDialog) {
        AddSourceDialog(
            onDismiss = { showAddSourceDialog = false },
            onAddLocal = {
                folderPickerLauncher.launch(null)
                showAddSourceDialog = false
            },
            onAddSmb = { name, server, _, path, credentials ->
                val newSource = RomSource.smb(name, server, path, credentials)
                sourceManager.addSource(newSource)
                sources = sourceManager.getSources()
                shouldReloadLocalFiles = true
            }
        )
    }
    
    // Manage Sources Dialog
    if (showManageSourcesDialog) {
        ManageSourcesDialog(
            sources = sources.filter { it.type != SourceType.ARCHIVE_ORG },
            onDismiss = { showManageSourcesDialog = false },
            onEdit = { updatedSource ->
                sourceManager.updateSource(updatedSource)
                sources = sourceManager.getSources()
                shouldReloadLocalFiles = true
            },
            onDelete = { source ->
                sourceManager.removeSource(source.id)
                sources = sourceManager.getSources()
                shouldReloadLocalFiles = true
            }
        )
    }

    // CRITICAL ERROR DIALOG: PATH NOT CONFIGURED
    if (uiState.error == "NO_ROM_PATH_CONFIGURED") {
        AlertDialog(
            onDismissRequest = { viewModel.searchPacks() }, // Clear error when closing
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Path not configured") 
                }
            },
            text = { 
                Text("To download games, you need to choose a folder where they will be saved.") 
            },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.searchPacks() // Clear error
                        // Navigate directly to the folder picker
                        val intent = Intent(context, com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher::class.java)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.searchPacks() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ERROR DIALOG: NOT ENOUGH SPACE
    uiState.storageCheckResult?.let { storageCheck ->
        if (!storageCheck.hasEnoughSpace) {
            AlertDialog(
                onDismissRequest = { viewModel.clearStorageError() },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Not enough space") 
                    }
                },
                text = { 
                    Column {
                        Text(
                            "The package you are trying to download is too large.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Info table
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Download size:", modifier = Modifier.weight(1f))
                            Text(storageCheck.requiredFormatted, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Available space:", modifier = Modifier.weight(1f))
                            Text(storageCheck.availableFormatted, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Additional space required:", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            Text(storageCheck.shortageFormatted, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Free up space on your device or select fewer files to download.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearStorageError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    // Friendly message for Archive.org server errors
    val displayMessage = when {
        error.contains("503") -> "Archive.org is down or under maintenance right now. Try again later."
        error.contains("502") -> "Archive.org is experiencing issues. Try again later."
        error.contains("500") -> "Archive.org has an internal error. Try again later."
        error.contains("certificate", ignoreCase = true) || error.contains("SSL", ignoreCase = true) -> 
            "SSL certificate error. Check that your device date and time are correct."
        error.contains("timeout", ignoreCase = true) -> "The connection to Archive.org timed out. Check your internet connection."
        error.contains("Unable to resolve host", ignoreCase = true) -> "No internet connection. Check your connection."
        else -> error
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(displayMessage, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.catalog_no_packs_found), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PacksList(
    packs: List<ArchiveOrgClient.RomPack>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onPackClick: (ArchiveOrgClient.RomPack) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(packs, key = { it.id }) { pack ->
            PackCard(pack = pack, onClick = { onPackClick(pack) })
        }
        
        // Load more button
        if (hasMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        Button(onClick = onLoadMore) {
                            Icon(Icons.Default.ExpandMore, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.catalog_load_more_packs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackCard(
    pack: ArchiveOrgClient.RomPack,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pack.flag, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${pack.systemId.uppercase()} • ${pack.sizeFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "⬇️ ${pack.downloads}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            pack.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc.take(120) + if (desc.length > 120) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PackDetailsDialog(
    pack: ArchiveOrgClient.RomPack,
    files: List<ArchiveOrgClient.DownloadableFile>,
    isLoadingFiles: Boolean,
    isFileDownloaded: (String) -> Boolean,
    isFileDownloading: (String) -> Boolean,
    onDismiss: () -> Unit,
    onDownloadFile: (ArchiveOrgClient.DownloadableFile) -> Unit,
    onDownloadAll: () -> Unit
) {
    // State for file search inside the package
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter files by search
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(pack.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${pack.flag} ${pack.systemId.uppercase()} • ${pack.sizeFormatted} • ⬇️ ${pack.downloads}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(min = 200.dp, max = 400.dp)
            ) {
                // Description (now scrollable)
                pack.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    item {
                        Text(desc, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                
                // Search field (only if there are more than 10 files)
                if (files.size > 10 && !isLoadingFiles) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search ROM...", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                    }
                }
                
                // Files header + download all button
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Show filtered count when search is active
                        val countText = if (searchQuery.isNotBlank() && filteredFiles.size != files.size) {
                            "${filteredFiles.size} of ${files.size} files"
                        } else {
                            stringResource(R.string.catalog_files_count, files.size)
                        }
                        Text(countText, style = MaterialTheme.typography.labelMedium)
                        if (files.isNotEmpty() && !isLoadingFiles) {
                            FilledTonalButton(
                                onClick = onDownloadAll,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.catalog_download_all), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Loading/empty/files states
                when {
                    isLoadingFiles -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    files.isEmpty() -> {
                        item {
                            Text(stringResource(R.string.catalog_no_files_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    filteredFiles.isEmpty() -> {
                        item {
                            Text("No ROMs found for \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        // File list (already inside LazyColumn)
                        items(filteredFiles, key = { it.name }) { file ->
                            val downloaded = isFileDownloaded(file.name)
                            val downloading = isFileDownloading(file.name)
                            FileItem(
                                file = file,
                                isDownloaded = downloaded,
                                isDownloading = downloading,
                                onDownload = { onDownloadFile(file) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FileItem(
    file: ArchiveOrgClient.DownloadableFile,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDownloaded) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (isDownloaded) "${file.sizeFormatted} • Already downloaded" else file.sizeFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDownloaded) {
            Icon(
                Icons.Default.CheckCircle,
                "Downloaded",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else if (isDownloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Download, "Download", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun DownloadsPanel(
    downloads: List<RomDownloader.DownloadInfo>,
    onDismiss: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onClearCompleted: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.catalog_downloads_title, downloads.size), style = MaterialTheme.typography.titleLarge)
                Row {
                    if (downloads.any { it.status in listOf(RomDownloader.DownloadStatus.COMPLETED, RomDownloader.DownloadStatus.ERROR) }) {
                        TextButton(onClick = onClearCompleted) { Text(stringResource(R.string.catalog_downloads_clear)) }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }
            }
            
            HorizontalDivider()
            
            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.catalog_no_downloads), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloads, key = { it.id }) { download ->
                        DownloadItem(download = download, onCancel = { onCancelDownload(download.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(
    download: RomDownloader.DownloadInfo,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = download.gameTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                when (download.status) {
                    RomDownloader.DownloadStatus.PROCESSING,
                    RomDownloader.DownloadStatus.DOWNLOADING,
                    RomDownloader.DownloadStatus.PENDING -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    }
                    RomDownloader.DownloadStatus.COMPLETED -> {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    RomDownloader.DownloadStatus.ERROR -> {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                    }
                    RomDownloader.DownloadStatus.CANCELLED -> {
                        Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            
            if (download.status == RomDownloader.DownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { download.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(download.sizeText, style = MaterialTheme.typography.labelSmall)
                    Text(download.progressText, style = MaterialTheme.typography.labelSmall)
                }
            } else if (download.status == RomDownloader.DownloadStatus.ERROR) {
                Text(
                    text = download.error ?: "Error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun LocalFileCard(
    file: LocalFile,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flag or system icon
            Text(
                text = file.flag,
                fontSize = 24.sp,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.cleanName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (file.system != null) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(file.systemDisplay, fontSize = 10.sp) },
                            modifier = Modifier.height(20.dp)
                        )
                    }
                    Text(
                        text = "${file.sizeFormatted} • ${file.extension.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SmbFileCard(
    file: SmbFile,
    sourceName: String,
    isDownloading: Boolean = false,
    isDownloaded: Boolean = false,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDownloading, onClick = onDownload)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flag or system icon
            Text(
                text = file.flag,
                fontSize = 24.sp,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.cleanName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (file.system != null) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(file.systemDisplay, fontSize = 10.sp) },
                            modifier = Modifier.height(20.dp)
                        )
                    }
                    Text(
                        text = "${file.sizeFormatted} • $sourceName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Icon based on state
            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                isDownloaded -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "In Library",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
