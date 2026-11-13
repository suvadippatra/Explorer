package com.troikoss.continuum_explorer.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.troikoss.continuum_explorer.managers.SettingsManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.ui.activities.SettingsActivity
import com.troikoss.continuum_explorer.model.NavLocation
import com.troikoss.continuum_explorer.model.ScreenSize
import com.troikoss.continuum_explorer.model.LibraryItem
import com.troikoss.continuum_explorer.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Standardized Top Bar for the explorer. 
 * Shows navigation controls, breadcrumbs, and a toggleable address bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    onMenuClick: () -> Unit,
    appState: FileExplorerState
) {

    val context = LocalContext.current
    val resources = LocalResources.current

    var optionsMenuExpanded by remember { mutableStateOf(false) }
    var addressBar by remember { mutableStateOf(false) }
    var historyMenuExpanded by remember { mutableStateOf(false) }

    // Search related state
    var searchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var searchOptionsMenuExpanded by remember { mutableStateOf(false) }
    var searchSubfolders by remember { mutableStateOf(false) }
    var searchKindMenuExpanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Sync UI searchBar state to global state
    LaunchedEffect(searchBar) {
        appState.isSearchUIActive = searchBar
    }

    // Logic to determine what to show in the address bar text field
    val currentPathString = remember(appState.currentPath, appState.currentSafUri, appState.currentArchiveFile, appState.currentArchiveUri, appState.currentArchivePath, appState.currentNetworkId) {
        if (appState.currentNetworkId != null) {
            appState.currentNetworkId ?: ""
        } else if (appState.currentArchiveFile != null) {
            val base = appState.currentArchiveFile?.absolutePath ?: ""
            if (appState.currentArchivePath.isEmpty()) base else "$base/${appState.currentArchivePath}"
        } else if (appState.currentArchiveUri != null) {
            val base = appState.currentArchiveUri.toString()
            if (appState.currentArchivePath.isEmpty()) base else "$base/${appState.currentArchivePath}"
        } else if (appState.currentPath != null) {
            appState.currentPath?.absolutePath ?: ""
        } else if (appState.currentSafUri != null) {
            appState.currentSafUri.toString()
        } else {
            ""
        }
    }

    // Breadcrumbs logic for File paths (including local Archives)
    val allSegments = remember(appState.currentPath, appState.currentArchiveFile, appState.currentArchivePath) {
        val fullPath = if (appState.currentArchiveFile != null) {
            val base = appState.currentArchiveFile?.absolutePath ?: ""
            val inner = appState.currentArchivePath
            if (inner.isEmpty()) base else "$base/$inner"
        } else {
            appState.currentPath?.absolutePath ?: ""
        }
        fullPath.split("/").filter { it.isNotEmpty() }
    }
    
    val zeroIndex = allSegments.indexOf("0")
    val visibleSegments = if (zeroIndex != -1) {
        allSegments.subList(zeroIndex, allSegments.size)
    } else {
        allSegments
    }

    var textPathValue by remember(currentPathString) {
        mutableStateOf(TextFieldValue(currentPathString))
    }

    LaunchedEffect(addressBar) {
        if (addressBar) {
            focusRequester.requestFocus()
            textPathValue = textPathValue.copy(
                selection = TextRange(0, textPathValue.text.length)
            )
        }
    }

    LaunchedEffect(searchBar) {
        if (searchBar) {
            focusRequester.requestFocus()
            searchQuery = searchQuery.copy(
                selection = TextRange(0, searchQuery.text.length)
            )
        }
    }

    val breadcrumbScrollState = rememberScrollState()

    // Auto-scroll to end when path changes (includes network navigation)
    LaunchedEffect(currentPathString, appState.currentNetworkId) {
        breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
    }

    // Debounced Cancel button logic
    var showCancelButton by remember { mutableStateOf(false) }
    LaunchedEffect(appState.isLoading) {
        if (appState.isLoading) {
            delay(400) // Match the 400ms delay of the spinner
            showCancelButton = true
        } else {
            showCancelButton = false
        }
    }

    val topBarBorderColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = topBarBorderColor,
                    start = Offset(0f, size.height - strokeWidth / 2),
                    end = Offset(size.width, size.height - strokeWidth / 2),
                    strokeWidth = strokeWidth
                )
            },
        color = if (SettingsManager.isColorfulBarsEnabled.value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row (modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = CenterVertically) {
            val isWideCollapsed by SettingsManager.isWideSidebarCollapsed
            if (appState.getScreenSize() == ScreenSize.SMALL || isWideCollapsed) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = if (isWideCollapsed) "Expand Navigation Sidebar" else stringResource(R.string.menu)
                    )
                }
            }
            
            // NAVIGATION GROUP (Back/Forward/History)
            val isSmall = appState.getScreenSize() == ScreenSize.SMALL
            if (!isSmall || !searchBar) Row(verticalAlignment = CenterVertically) {
                IconButton(
                    onClick = {
                        if (appState.backStack.isNotEmpty()) {
                            appState.goBack()
                        } else if (appState.canGoUp) {
                            appState.goUp()
                        }
                    },
                    enabled = appState.backStack.isNotEmpty() || appState.canGoUp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(20.dp),
                        tint = if (appState.backStack.isNotEmpty() || appState.canGoUp) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = { appState.goForward() },
                    enabled = appState.forwardStack.isNotEmpty(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.forward),
                        modifier = Modifier.size(20.dp),
                        tint = if (appState.forwardStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // UNIFIED HISTORY DROPDOWN (MOVE TO RIGHT OF FORWARD)
                if (appState.getScreenSize() != ScreenSize.SMALL){
                    Box {
                        IconButton(
                            onClick = { historyMenuExpanded = true },
                            enabled = appState.backStack.isNotEmpty() || appState.forwardStack.isNotEmpty(),
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.history),
                                modifier = Modifier.size(16.dp),
                                tint = if (appState.backStack.isNotEmpty() || appState.forwardStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        DropdownMenu(
                            expanded = historyMenuExpanded,
                            onDismissRequest = { historyMenuExpanded = false }
                        ) {
                            // Forward History (Latest at top)
                            appState.forwardStack.take(5)
                                .forEachIndexed { reversedIndex, location ->
                                    val unifiedIndex =
                                        appState.backStack.size + 1 + (appState.forwardStack.size - 1 - reversedIndex)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                appState.getLocationName(location),
                                                maxLines = 1
                                            )
                                        },
                                        onClick = {
                                            appState.jumpToHistory(unifiedIndex)
                                            historyMenuExpanded = false
                                        }
                                    )
                                }

                            // Current Location (with Checkmark)
                            val currentNav = NavLocation(
                                path = appState.currentPath,
                                uri = appState.currentSafUri,
                                archiveFile = appState.currentArchiveFile,
                                archiveUri = appState.currentArchiveUri,
                                archivePath = appState.currentArchivePath,
                                safStack = ArrayList(appState.safStack),
                                networkConnectionId = appState.currentNetworkConnectionId,
                                networkPath = appState.currentNetworkId
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        appState.getLocationName(currentNav),
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { historyMenuExpanded = false }
                            )

                            // Back History (Most recent just below current)
                            appState.backStack.asReversed().take(5)
                                .forEachIndexed { reversedIndex, location ->
                                    val originalIndex = appState.backStack.size - 1 - reversedIndex
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                appState.getLocationName(location),
                                                maxLines = 1
                                            )
                                        },
                                        onClick = {
                                            appState.jumpToHistory(originalIndex)
                                            historyMenuExpanded = false
                                        }
                                    )
                                }
                        }
                    }
                }
            }

            // UP BUTTON

            if (appState.getScreenSize() != ScreenSize.SMALL){
                IconButton(
                    onClick = { appState.goUp() },
                    enabled = appState.canGoUp
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.up),
                        tint = if (appState.canGoUp) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // REFRESH / CANCEL BUTTON
                if (showCancelButton) {
                    IconButton(onClick = { appState.cancelLoading() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                } else {
                    // Show Refresh button if not loading, or if loading hasn't reached the 400ms mark yet
                    IconButton(onClick = { if (!appState.isLoading) appState.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.menu_refresh))
                    }
                }
            }

            // Breadcrumbs / Address Bar Area
            Surface(
                modifier = Modifier
                    .padding(start = 6.dp, end = 2.dp)
                    .height(40.dp)
                    .weight(1f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        if (!addressBar && !searchBar) addressBar = true
                    },
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(20.dp)
            ) {
                if (searchBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalAlignment = CenterVertically
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    down = FocusRequester.Cancel
                                    up = FocusRequester.Cancel
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
                                        true // Consume Up/Down keys to prevent focus from leaving the search bar
                                    } else {
                                        false
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    appState.performSearch(searchQuery.text, searchSubfolders)
                                }
                            ),
                            decorationBox = { innerTextField ->
                                if (searchQuery.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_hint),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Box {
                            IconButton(onClick = { searchOptionsMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.dialog_search_options))
                            }

                            DropdownMenu(
                                expanded = searchOptionsMenuExpanded,
                                onDismissRequest = { 
                                    searchOptionsMenuExpanded = false
                                    searchKindMenuExpanded = false 
                                }
                            ) {
                                if (searchKindMenuExpanded) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.back)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                        onClick = { searchKindMenuExpanded = false }
                                    )
                                    HorizontalDivider()
                                    val kinds = listOf("folder", "document", "image", "video", "audio", "archive")
                                    val kindResIds = mapOf(
                                        "folder" to R.string.kind_folder,
                                        "document" to R.string.kind_document,
                                        "image" to R.string.kind_image,
                                        "video" to R.string.kind_video,
                                        "audio" to R.string.kind_audio,
                                        "archive" to R.string.kind_archive
                                    )
                                    kinds.forEach { kind ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(kindResIds[kind]!!)) },
                                            onClick = {
                                                searchQuery = TextFieldValue(searchQuery.text + "kind:$kind ")
                                                searchOptionsMenuExpanded = false
                                                searchKindMenuExpanded = false
                                                focusRequester.requestFocus()
                                            }
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.search_subfolders)) },
                                        trailingIcon = {
                                            Checkbox(checked = searchSubfolders, onCheckedChange = null)
                                        },
                                        onClick = { searchSubfolders = !searchSubfolders }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.search_kind)) },
                                        onClick = { searchKindMenuExpanded = true },
                                        trailingIcon = { Icon(Icons.Default.ChevronRight, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.search_and)) },
                                        onClick = {
                                            searchQuery = TextFieldValue(searchQuery.text + " AND ")
                                            searchOptionsMenuExpanded = false
                                            focusRequester.requestFocus()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.search_or)) },
                                        onClick = {
                                            searchQuery = TextFieldValue(searchQuery.text + " OR ")
                                            searchOptionsMenuExpanded = false
                                            focusRequester.requestFocus()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.search_not)) },
                                        onClick = {
                                            searchQuery = TextFieldValue(searchQuery.text + " NOT ")
                                            searchOptionsMenuExpanded = false
                                            focusRequester.requestFocus()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.search_ext)) },
                                        onClick = {
                                            searchQuery = TextFieldValue(searchQuery.text + "*.zip ")
                                            searchOptionsMenuExpanded = false
                                            focusRequester.requestFocus()
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            searchBar = false
                            focusManager.clearFocus()
                            if (appState.isSearchMode) {
                                appState.refresh() // Reset to normal view if search was closed
                            }
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (!addressBar) {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val isLargeScreen = appState.getScreenSize() != ScreenSize.SMALL || isLandscape

                    val rawExactPath = remember(currentPathString, appState.currentPath, appState.libraryItem) {
                        when {
                            appState.currentPath != null -> appState.currentPath!!.absolutePath
                            currentPathString.isNotBlank() -> currentPathString
                            appState.libraryItem == LibraryItem.Gallery -> "Gallery"
                            appState.libraryItem == LibraryItem.RecycleBin -> "Recycle Bin"
                            appState.libraryItem == LibraryItem.Recent -> "Recent"
                            appState.libraryItem == LibraryItem.VibeArea -> "My Suite"
                            else -> "/storage/emulated/0"
                        }
                    }

                    val displayedPath = remember(rawExactPath) {
                        val clean = rawExactPath.removePrefix("/")
                        if (clean.startsWith("storage/emulated/0")) {
                            val rel = clean.removePrefix("storage/emulated/0").removePrefix("/")
                            if (rel.isEmpty()) {
                                "storage/emulated/0"
                            } else {
                                "0/$rel"
                            }
                        } else {
                            rawExactPath
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                val clickEditPath = if (rawExactPath.startsWith("/storage/emulated/0")) {
                                    val rel = rawExactPath.removePrefix("/storage/emulated/0").removePrefix("/")
                                    if (rel.isEmpty()) "storage/emulated/0" else "0/$rel"
                                } else if (rawExactPath.startsWith("storage/emulated/0")) {
                                    val rel = rawExactPath.removePrefix("storage/emulated/0").removePrefix("/")
                                    if (rel.isEmpty()) "storage/emulated/0" else "0/$rel"
                                } else {
                                    rawExactPath
                                }
                                textPathValue = TextFieldValue(
                                    text = clickEditPath,
                                    selection = androidx.compose.ui.text.TextRange(clickEditPath.length)
                                )
                                addressBar = true
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = displayedPath,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = CenterVertically
                    ) {
                        BasicTextField(
                            value = textPathValue,
                            onValueChange = { textPathValue = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            textPathValue = textPathValue.copy(
                                                selection = androidx.compose.ui.text.TextRange(0, textPathValue.text.length)
                                            )
                                        }
                                    )
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val input = textPathValue.text.trim()
                                    val resolvedInput = if (input == "0" || input == "storage/emulated/0") {
                                        "/storage/emulated/0"
                                    } else if (input.startsWith("0/")) {
                                        "/storage/emulated/0/" + input.removePrefix("0/")
                                    } else if (input.startsWith("storage/")) {
                                        "/$input"
                                    } else {
                                        input
                                    }
                                    if (resolvedInput.startsWith("/") || resolvedInput.startsWith("content://")) {
                                        if (resolvedInput.startsWith("/")) {
                                            val newFile = File(resolvedInput)
                                            if (newFile.exists()) {
                                                if (newFile.isDirectory) {
                                                    appState.navigateTo(newFile, null)
                                                    addressBar = false
                                                    focusManager.clearFocus()
                                                } else if (ZipUtils.isArchive(newFile.toUniversal())) {
                                                    appState.navigateTo(null, null, archiveFile = newFile, archivePath = "")
                                                    addressBar = false
                                                    focusManager.clearFocus()
                                                } else {
                                                    Toast.makeText(context, resources.getString(R.string.msg_not_dir_archive), Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, resources.getString(R.string.msg_file_not_found), Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            addressBar = false
                                            focusManager.clearFocus()
                                        }
                                    } else {
                                        Toast.makeText(context, resources.getString(R.string.msg_invalid_path), Toast.LENGTH_SHORT).show()
                                        textPathValue = TextFieldValue(currentPathString)
                                    }
                                }
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = {
                    if (searchBar) {
                        appState.performSearch(searchQuery.text, searchSubfolders)
                    } else {
                        searchBar = true
                        addressBar = false
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), modifier = Modifier.size(20.dp))
            }

            // QUICK VIEW MODE & COLUMN COUNT SWITCHER
            var viewMenuExpanded by remember { mutableStateOf(false) }
            val activeViewMode = appState.folderConfigs.viewMode
            val currentStorageKey = appState.getCurrentStorageKey()

            if (appState.libraryItem == LibraryItem.Gallery || activeViewMode == com.troikoss.continuum_explorer.model.ViewMode.GALLERY) {
                IconButton(
                    onClick = { appState.is3dGalleryWallEnabled = !appState.is3dGalleryWallEnabled },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = "Toggle 3D Picture Wall",
                        modifier = Modifier.size(20.dp),
                        tint = if (appState.is3dGalleryWallEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { viewMenuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    val viewIcon = when (activeViewMode) {
                        com.troikoss.continuum_explorer.model.ViewMode.DETAILS -> Icons.Default.TableRows
                        com.troikoss.continuum_explorer.model.ViewMode.CONTENT -> Icons.Default.ViewList
                        com.troikoss.continuum_explorer.model.ViewMode.GRID -> Icons.Default.GridView
                        com.troikoss.continuum_explorer.model.ViewMode.GALLERY -> Icons.Default.Collections
                    }
                    Icon(
                        imageVector = viewIcon,
                        contentDescription = "Change View Layout & Columns",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = viewMenuExpanded,
                    onDismissRequest = { viewMenuExpanded = false },
                    modifier = Modifier.width(250.dp)
                ) {
                    Text(
                        text = "Layout Mode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    val viewModes = listOf(
                        Triple(com.troikoss.continuum_explorer.model.ViewMode.DETAILS, "Details (Table)", Icons.Default.TableRows),
                        Triple(com.troikoss.continuum_explorer.model.ViewMode.CONTENT, "List (Compact)", Icons.Default.ViewList),
                        Triple(com.troikoss.continuum_explorer.model.ViewMode.GRID, "Grid (Cards)", Icons.Default.GridView),
                        Triple(com.troikoss.continuum_explorer.model.ViewMode.GALLERY, "Gallery (Tiles)", Icons.Default.Collections)
                    )

                    viewModes.forEach { (mode, label, icon) ->
                        val isSelected = activeViewMode == mode && !appState.is3dGalleryWallEnabled
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            onClick = {
                                appState.is3dGalleryWallEnabled = false
                                appState.folderConfigs.updateViewMode(mode, currentStorageKey)
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "3D Picture Wall (Touch)",
                                fontWeight = if (appState.is3dGalleryWallEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (appState.is3dGalleryWallEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ViewInAr,
                                contentDescription = null,
                                tint = if (appState.is3dGalleryWallEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (appState.is3dGalleryWallEnabled) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = {
                            appState.folderConfigs.updateViewMode(com.troikoss.continuum_explorer.model.ViewMode.GALLERY, currentStorageKey)
                            appState.is3dGalleryWallEnabled = !appState.is3dGalleryWallEnabled
                            viewMenuExpanded = false
                        }
                    )

                    if (activeViewMode == com.troikoss.continuum_explorer.model.ViewMode.GRID ||
                        activeViewMode == com.troikoss.continuum_explorer.model.ViewMode.GALLERY
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                verticalAlignment = CenterVertically
                            ) {
                                Text("Grid Columns", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${appState.folderConfigs.gridColumnCount} cols",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = appState.folderConfigs.gridColumnCount.toFloat(),
                                onValueChange = {
                                    appState.folderConfigs.updateGridColumnCount(it.toInt(), currentStorageKey)
                                },
                                valueRange = 2f..6f,
                                steps = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                verticalAlignment = CenterVertically
                            ) {
                                Text("Icon / Tile Size", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${appState.folderConfigs.gridItemSize}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = appState.folderConfigs.gridItemSize.toFloat(),
                                onValueChange = {
                                    appState.folderConfigs.updateGridSize(it.toInt(), currentStorageKey)
                                },
                                valueRange = 60f..200f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text("Folders First") },
                        trailingIcon = {
                            Checkbox(
                                checked = appState.folderConfigs.foldersFirst,
                                onCheckedChange = null
                            )
                        },
                        onClick = {
                            appState.folderConfigs.toggleFoldersFirst(currentStorageKey) { appState.refresh() }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Group Items") },
                        trailingIcon = {
                            Checkbox(
                                checked = appState.folderConfigs.isGroupingEnabled,
                                onCheckedChange = null
                            )
                        },
                        onClick = {
                            appState.folderConfigs.toggleGrouping(currentStorageKey) { appState.refresh() }
                        }
                    )
                }
            }

            // QUICK PREVIEW PANE TOGGLE (Homepage Quick Preview Feature)
            IconButton(
                onClick = {
                    if (appState.isPreviewPaneVisible) {
                        appState.closePreview()
                    } else {
                        val candidate = appState.selectionManager.selectedItems.firstOrNull { !it.isDirectory }
                            ?: appState.files.firstOrNull { !it.isDirectory }
                        if (candidate != null) {
                            appState.previewFile = candidate
                        }
                        appState.isPreviewPaneVisible = true
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = if (appState.isPreviewPaneVisible) "Close Quick Preview Pane" else "Open Quick Preview Pane",
                    modifier = Modifier.size(20.dp),
                    tint = if (appState.isPreviewPaneVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // FULL-SCREEN TOGGLE (Hides/Shows Top TabBar)
            val isFullScreen by SettingsManager.isFullScreenTabBarHidden
            IconButton(
                onClick = { SettingsManager.toggleFullScreenTabBar() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullScreen) "Exit Full Screen (Show Tabs)" else "Full Screen (Hide Tabs)",
                    modifier = Modifier.size(20.dp),
                    tint = if (isFullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            Box {
                IconButton(onClick = { optionsMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options), modifier = Modifier.size(20.dp))
                }

                DropdownMenu(
                    expanded = optionsMenuExpanded,
                    onDismissRequest = { optionsMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isFullScreen) "Show Tab Bar" else "Full Screen (Hide Tab Bar)") },
                        onClick = {
                            optionsMenuExpanded = false
                            SettingsManager.toggleFullScreenTabBar()
                        },
                        leadingIcon = {
                            Icon(
                                if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = null
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(if (SettingsManager.showHiddenFiles.value) "Hide Hidden Files" else "Show Hidden Files") },
                        onClick = {
                            optionsMenuExpanded = false
                            appState.folderConfigs.toggleShowHiddenFiles(currentStorageKey) { appState.refresh() }
                        },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        onClick = {
                            optionsMenuExpanded = false
                            val intent = Intent(context, SettingsActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                    )
                }
            }
        }
    }
}