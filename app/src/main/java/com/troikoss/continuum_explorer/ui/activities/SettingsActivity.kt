package com.troikoss.continuum_explorer.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.managers.AppFontFamily
import com.troikoss.continuum_explorer.managers.DefaultAppHandler
import com.troikoss.continuum_explorer.managers.DeleteBehavior
import com.troikoss.continuum_explorer.managers.DetailsMode
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.managers.ThemeMode
import com.troikoss.continuum_explorer.managers.TouchDragBehavior
import com.troikoss.continuum_explorer.model.ViewMode
import com.troikoss.continuum_explorer.model.FileColumnType
import com.troikoss.continuum_explorer.model.SortOrder

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileExplorerTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val deleteBehavior = SettingsManager.deleteBehavior.value
    val touchDragBehavior = SettingsManager.touchDragBehavior.value
    val isDefaultArchiveViewerEnabled = SettingsManager.isDefaultArchiveViewerEnabled.value
    val themeMode = SettingsManager.themeMode.value
    val language = SettingsManager.language.value
    val detailsMode = SettingsManager.detailsMode.value
    val isCommandBarVisible = SettingsManager.isCommandBarVisible.value
    val showHiddenFiles = SettingsManager.showHiddenFiles.value
    val iconTouchSelection = SettingsManager.iconTouchSelection.value
    val defaultViewMode = SettingsManager.defaultViewMode.value
    val isColorfulBarsEnabled = SettingsManager.isColorfulBarsEnabled.value
    val foldersFirstEnabled = SettingsManager.foldersFirst.value
    val groupingEnabled = SettingsManager.isGroupingEnabled.value
    val defaultSortColumnType = SettingsManager.defaultSortColumnType.value
    val defaultSortOrder = SettingsManager.defaultSortOrder.value
    val defaultHeaderDate = SettingsManager.defaultHeaderDate.value
    val defaultHeaderSize = SettingsManager.defaultHeaderSize.value
    val defaultHeaderType = SettingsManager.defaultHeaderType.value
    val defaultGridZoom = SettingsManager.defaultGridZoom.value
    val isHoverPreviewEnabled = SettingsManager.isHoverPreviewEnabled.value
    val defaultHandlerPdf = SettingsManager.defaultHandlerPdf.value
    val defaultHandlerImage = SettingsManager.defaultHandlerImage.value
    val defaultHandlerText = SettingsManager.defaultHandlerText.value
    val defaultHandlerMedia = SettingsManager.defaultHandlerMedia.value
    val defaultHandlerOffice = SettingsManager.defaultHandlerOffice.value

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTouchDragDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDefaultAppsDialog by remember { mutableStateOf(false) }
    var showResetDefaultsDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    var showDefaultFolderOptions by remember { mutableStateOf(false) }

    if (showDefaultFolderOptions) {
        DefaultFolderOptionsPage(onBack = { showDefaultFolderOptions = false })
        return
    }

    var explanationDialogText by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (explanationDialogText != null) {
        AlertDialog(
            onDismissRequest = { explanationDialogText = null },
            title = { Text(explanationDialogText!!.first, fontWeight = FontWeight.Bold) },
            text = { Text(explanationDialogText!!.second, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { explanationDialogText = null }) {
                    Text("Got it")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 600

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedCategoryForMobile by remember { mutableStateOf<Int?>(null) }

    data class SettingsMasterCategory(
        val id: Int,
        val title: String,
        val subtitle: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    val masterCategories = remember(context) {
        listOf(
            SettingsMasterCategory(0, "General", "Defaults & language", Icons.Default.Tune),
            SettingsMasterCategory(1, context.getString(R.string.settings_appearance), "Themes, fonts & bars", Icons.Default.Palette),
            SettingsMasterCategory(2, "Editors & Handlers", "Windows & file viewers", Icons.Default.Apps),
            SettingsMasterCategory(3, "Storage & Files", "Delete, drag & archives", Icons.Default.Folder),
            SettingsMasterCategory(4, "Hosting", "FTP, HTTP & Casting", Icons.Default.Router),
            SettingsMasterCategory(5, "Diagnostics", "Memory, shortcuts & about", Icons.Default.Info),
            SettingsMasterCategory(6, "Help Guide", "Quick reference & credits", Icons.Default.HelpOutline)
        )
    }

    BackHandler(enabled = !isLargeScreen && selectedCategoryForMobile != null) {
        selectedCategoryForMobile = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (!isLargeScreen && selectedCategoryForMobile != null) masterCategories[selectedCategoryForMobile!!].title else stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isLargeScreen && selectedCategoryForMobile != null) {
                                selectedCategoryForMobile = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            selectedCategoryIndex = 6
                            selectedCategoryForMobile = 6
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp).height(34.dp)
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Help", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Help Guide", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            @Composable
            fun RenderCategoryContent(catIndex: Int) {
                when (catIndex) {
                    0 -> {
                        val langText = when (language) {
                            "tr" -> stringResource(R.string.settings_language_turkish)
                            "en" -> stringResource(R.string.settings_language_english)
                            "fr" -> stringResource(R.string.settings_language_french)
                            "pt" -> stringResource(R.string.settings_language_portuguese)
                            "es" -> stringResource(R.string.settings_language_spanish)
                            else -> stringResource(R.string.settings_language_system)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PixelCategoryCard(
                                title = "General & Folder Defaults",
                                icon = Icons.Default.Tune
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDefaultFolderOptions = true }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.settings_default_folder_options), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Sidebar Edge Swipe", style = MaterialTheme.typography.bodyMedium)
                                        IconButton(
                                            onClick = {
                                                explanationDialogText = Pair(
                                                    "Sidebar Edge Swipe",
                                                    "Swipe inwards from the left edge of the screen to smoothly reveal the navigation pane."
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Switch(
                                        checked = SettingsManager.isSidebarSwipeEnabled.value,
                                        onCheckedChange = { SettingsManager.setSidebarSwipeEnabled(context, it) }
                                    )
                                }
                            }

                            PixelCategoryCard(
                                title = "Language & Localization",
                                icon = Icons.Default.Translate
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLanguageDialog = true }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(langText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        val currentFont by SettingsManager.appFontFamily
                        PixelCategoryCard(
                            title = stringResource(R.string.settings_appearance),
                            icon = Icons.Default.Palette
                        ) {
                            Text(
                                text = stringResource(R.string.settings_theme),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SegmentedButton(
                                    selected = themeMode == ThemeMode.SYSTEM,
                                    onClick = { SettingsManager.setThemeMode(context, ThemeMode.SYSTEM) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                                ) {
                                    Text(stringResource(R.string.settings_theme_system), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = themeMode == ThemeMode.LIGHT,
                                    onClick = { SettingsManager.setThemeMode(context, ThemeMode.LIGHT) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                                ) {
                                    Text(stringResource(R.string.settings_theme_light), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = themeMode == ThemeMode.DARK,
                                    onClick = { SettingsManager.setThemeMode(context, ThemeMode.DARK) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                                ) {
                                    Text(stringResource(R.string.settings_theme_dark), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = themeMode == ThemeMode.MATERIAL_UI,
                                    onClick = { SettingsManager.setThemeMode(context, ThemeMode.MATERIAL_UI) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                                ) {
                                    Text("Material UI", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Text(
                                text = "App Font",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppFontFamily.entries.forEachIndexed { index, fontOption ->
                                    SegmentedButton(
                                        selected = currentFont == fontOption,
                                        onClick = { SettingsManager.setAppFontFamily(context, fontOption) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AppFontFamily.entries.size)
                                    ) {
                                        Text(
                                            text = when (fontOption) {
                                                AppFontFamily.SYSTEM_DEFAULT -> "Default"
                                                AppFontFamily.PLUS_JAKARTA_SANS -> "Jakarta Sans"
                                                AppFontFamily.JETBRAINS_MONO -> "JetBrains"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.settings_colorful_bars), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = {
                                            explanationDialogText = Pair(
                                                "Colorful Toolbars",
                                                "Tints the top app bar and command bar with the theme accent color."
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = isColorfulBarsEnabled,
                                    onCheckedChange = { SettingsManager.setColorfulBarsEnabled(context, it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.settings_show_command_bar), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = {
                                            explanationDialogText = Pair(
                                                "Command Bar",
                                                "Displays the quick action toolbar with Cut, Copy, Paste, Delete, and Rename shortcuts."
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = isCommandBarVisible,
                                    onCheckedChange = { SettingsManager.setCommandBarVisible(context, it) }
                                )
                            }

                            val detailsText = when (detailsMode) {
                                DetailsMode.OFF -> stringResource(R.string.settings_details_hidden)
                                DetailsMode.PANE -> stringResource(R.string.settings_details_pane)
                                DetailsMode.BAR -> stringResource(R.string.settings_details_bar)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDetailsDialog = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.settings_details_large), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = {
                                            explanationDialogText = Pair(
                                                "Details Mode",
                                                "Configures how file metadata and preview details are presented on large displays."
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(detailsText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    2 -> {
                        val currentWorkspaceMode by SettingsManager.workspaceWindowMode
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PixelCategoryCard(
                                title = "Window Mode",
                                icon = Icons.Default.Layers
                            ) {
                                com.troikoss.continuum_explorer.rules.WorkspaceWindowMode.entries.forEach { mode ->
                                    val isSelected = currentWorkspaceMode == mode
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                SettingsManager.setWorkspaceWindowMode(context, mode)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { SettingsManager.setWorkspaceWindowMode(context, mode) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = mode.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            PixelCategoryCard(
                                title = "Editors & Handlers",
                                icon = Icons.Default.Apps
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Desktop Hover Preview", style = MaterialTheme.typography.bodyMedium)
                                        IconButton(
                                            onClick = {
                                                explanationDialogText = Pair(
                                                    "Desktop Hover Preview",
                                                    "Instantly previews PDFs, archives, images, documents, and code in the right split preview pane when hovering or selecting a file."
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Switch(
                                        checked = isHoverPreviewEnabled,
                                        onCheckedChange = { SettingsManager.setHoverPreviewEnabled(context, it) }
                                    )
                                }

                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDefaultAppsDialog = true }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Default File Handlers", style = MaterialTheme.typography.bodyMedium)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    3 -> {
                        PixelCategoryCard(
                            title = stringResource(R.string.settings_file_ops),
                            icon = Icons.Default.Folder
                        ) {
                            val delText = when (deleteBehavior) {
                                DeleteBehavior.ASK -> stringResource(R.string.settings_del_ask)
                                DeleteBehavior.RECYCLE -> stringResource(R.string.settings_del_recycle)
                                DeleteBehavior.PERMANENT -> stringResource(R.string.settings_del_permanent)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDeleteDialog = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.settings_deletion_behavior), style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(delText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            val dragText = when (touchDragBehavior) {
                                TouchDragBehavior.ASK -> stringResource(R.string.settings_touch_drag_ask)
                                TouchDragBehavior.COPY -> stringResource(R.string.settings_touch_drag_copy)
                                TouchDragBehavior.MOVE -> stringResource(R.string.settings_touch_drag_move)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTouchDragDialog = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.settings_touch_drag), style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(dragText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.settings_internal_archive), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = {
                                            explanationDialogText = Pair(
                                                "Internal Archive Viewer",
                                                "Opens ZIP, JAR, APK, TAR, 7Z, and RAR archives directly inside the explorer with search and one-tap folder extraction."
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = isDefaultArchiveViewerEnabled,
                                    onCheckedChange = { SettingsManager.setDefaultArchiveViewerEnabled(context, it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Batch Renaming Tool", style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = {
                                            explanationDialogText = Pair(
                                                "Batch Renaming Tool",
                                                "Enables multi-file renaming with prefix, suffix, and sequential numbering patterns."
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = SettingsManager.isBatchRenameEnabled.value,
                                    onCheckedChange = { SettingsManager.setBatchRenameEnabled(context, it) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.settings_icon_selection), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = {
                                            explanationDialogText = Pair(
                                                "Icon Touch Selection",
                                                "Allows selecting files and folders by tapping directly on their leading icon."
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = iconTouchSelection,
                                    onCheckedChange = { SettingsManager.setIconTouchSelection(context, it) }
                                )
                            }
                        }
                    }
                    4 -> {
                        val hubState by com.troikoss.continuum_explorer.server.ServerManager.state.collectAsState()
                        val ftpAnon by SettingsManager.isFtpAnonymousAllowed
                        val ftpAllowUpload by SettingsManager.ftpAllowUpload
                        val ftpAllowMiniApps by SettingsManager.ftpAllowMiniApps
                        val httpRequireAuth by SettingsManager.httpWebsiteRequireAuth
                        val ftpPortVal by SettingsManager.ftpPort

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PixelCategoryCard(
                                title = "Hosting & Modes",
                                icon = Icons.Default.Router
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Exclusive FTP Hosting Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("FTP Hosting (:$ftpPortVal)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            if (hubState.isFtpRunning) {
                                                Text(
                                                    "ftp://${hubState.ipAddress}:${hubState.ftp.port}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = hubState.isFtpRunning,
                                            onCheckedChange = { enable ->
                                                if (enable) com.troikoss.continuum_explorer.server.ServerManager.startFtpServer(context)
                                                else com.troikoss.continuum_explorer.server.ServerManager.stopFtpServer(context)
                                            }
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    // Exclusive HTTP Hosting Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("HTTP Hosting (:8080)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            if (hubState.isHttpRunning) {
                                                Text(
                                                    "http://${hubState.ipAddress}:${hubState.http.port}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = hubState.isHttpRunning,
                                            onCheckedChange = { enable ->
                                                if (enable) com.troikoss.continuum_explorer.server.ServerManager.startHttpServer(context)
                                                else com.troikoss.continuum_explorer.server.ServerManager.stopHttpServer(context)
                                            }
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Button(
                                        onClick = {
                                            com.troikoss.continuum_explorer.utils.GlobalEvents.requestOpenLibraryItem(
                                                com.troikoss.continuum_explorer.model.LibraryItem.FtpServer
                                            )
                                            val intent = Intent(context, MainActivity::class.java).apply {
                                                putExtra("isFtpServer", true)
                                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            }
                                            context.startActivity(intent)
                                            (context as? ComponentActivity)?.finish()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Open Hosting Dashboard", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            PixelCategoryCard(
                                title = "Network Security & Sandbox",
                                icon = Icons.Default.Tune
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Require PIN on Untrusted Wi-Fi (HTTP)", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = httpRequireAuth,
                                            onCheckedChange = { SettingsManager.setHttpWebsiteSecurity(context, it) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("FTP Anonymous Read Access", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = ftpAnon,
                                            onCheckedChange = { SettingsManager.setFtpAnonymousAllowed(context, it) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Allow Authenticated File Uploads", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = ftpAllowUpload,
                                            onCheckedChange = { SettingsManager.setFtpAllowUpload(context, it) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Isolated Browser Mini-Apps Sandbox", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = ftpAllowMiniApps,
                                            onCheckedChange = { SettingsManager.setFtpAllowMiniApps(context, it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    5 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PixelCategoryCard(
                                title = "Diagnostics & Memory",
                                icon = Icons.Default.Tune
                            ) {
                                DiagnosticsMemoryPanel(context = context)
                            }

                            PixelCategoryCard(
                                title = "System & About",
                                icon = Icons.Default.Info
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showShortcutsDialog = true }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.shortcuts_title), style = MaterialTheme.typography.bodyMedium)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAboutDialog = true }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.bodyMedium)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                OutlinedButton(
                                    onClick = { showResetDefaultsDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.settings_reset_folder_options))
                                }

                                OutlinedButton(
                                    onClick = { showResetAllDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.settings_reset_all))
                                }
                            }
                        }
                    }
                    else -> {
                        HelpDocumentationSection()
                    }
                }
            }

            if (isLargeScreen) {
                // Material 3 Master-Detail Split-Pane for Wide Screens (Tablets / Foldables / Desktops)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Master Category Panel
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Text(
                                    text = "Settings Categories",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            items(masterCategories.size) { idx ->
                                val cat = masterCategories[idx]
                                val isSelected = selectedCategoryIndex == idx
                                Surface(
                                    selected = isSelected,
                                    onClick = { selectedCategoryIndex = idx },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    tonalElevation = if (isSelected) 4.dp else 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cat.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = cat.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    // Right Detail Panel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
                            RenderCategoryContent(catIndex = selectedCategoryIndex)
                        }
                    }
                }
            } else {
                // Mobile View (<600dp)
                if (selectedCategoryForMobile == null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(masterCategories.size) { idx ->
                            val cat = masterCategories[idx]
                            Surface(
                                onClick = { selectedCategoryForMobile = idx },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = cat.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        RenderCategoryContent(catIndex = selectedCategoryForMobile!!)
                    }
                }
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(R.string.settings_choose_del_behavior)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_del_ask),
                                selected = deleteBehavior == DeleteBehavior.ASK,
                                onClick = { 
                                    SettingsManager.setDeleteBehavior(context, DeleteBehavior.ASK)
                                    showDeleteDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_del_recycle),
                                selected = deleteBehavior == DeleteBehavior.RECYCLE,
                                onClick = { 
                                    SettingsManager.setDeleteBehavior(context, DeleteBehavior.RECYCLE)
                                    showDeleteDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_del_permanent),
                                selected = deleteBehavior == DeleteBehavior.PERMANENT,
                                onClick = { 
                                    SettingsManager.setDeleteBehavior(context, DeleteBehavior.PERMANENT)
                                    showDeleteDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showTouchDragDialog) {
                AlertDialog(
                    onDismissRequest = { showTouchDragDialog = false },
                    title = { Text(stringResource(R.string.settings_touch_drag_title)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_touch_drag_ask),
                                selected = touchDragBehavior == TouchDragBehavior.ASK,
                                onClick = {
                                    SettingsManager.setTouchDragBehavior(context, TouchDragBehavior.ASK)
                                    showTouchDragDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_touch_drag_copy),
                                selected = touchDragBehavior == TouchDragBehavior.COPY,
                                onClick = {
                                    SettingsManager.setTouchDragBehavior(context, TouchDragBehavior.COPY)
                                    showTouchDragDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_touch_drag_move),
                                selected = touchDragBehavior == TouchDragBehavior.MOVE,
                                onClick = {
                                    SettingsManager.setTouchDragBehavior(context, TouchDragBehavior.MOVE)
                                    showTouchDragDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTouchDragDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text(stringResource(R.string.settings_choose_theme)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_theme_system),
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.SYSTEM)
                                    showThemeDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_theme_light),
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.LIGHT)
                                    showThemeDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_theme_dark),
                                selected = themeMode == ThemeMode.DARK,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.DARK)
                                    showThemeDialog = false
                                }
                            )
                            OptionItem(
                                label = "Material UI",
                                selected = themeMode == ThemeMode.MATERIAL_UI,
                                onClick = {
                                    SettingsManager.setThemeMode(context, ThemeMode.MATERIAL_UI)
                                    showThemeDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showLanguageDialog) {
                AlertDialog(
                    onDismissRequest = { showLanguageDialog = false },
                    title = { Text(stringResource(R.string.settings_choose_language)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_language_system),
                                selected = language == "system",
                                onClick = {
                                    SettingsManager.setLanguage(context, "system")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_english),
                                selected = language == "en",
                                onClick = {
                                    SettingsManager.setLanguage(context, "en")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_french),
                                selected = language == "fr",
                                onClick = {
                                    SettingsManager.setLanguage(context, "fr")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_portuguese),
                                selected = language == "pt",
                                onClick = {
                                    SettingsManager.setLanguage(context, "pt")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_spanish),
                                selected = language == "es",
                                onClick = {
                                    SettingsManager.setLanguage(context, "es")
                                    showLanguageDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_language_turkish),
                                selected = language == "tr",
                                onClick = {
                                    SettingsManager.setLanguage(context, "tr")
                                    showLanguageDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLanguageDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showDetailsDialog) {
                AlertDialog(
                    onDismissRequest = { showDetailsDialog = false },
                    title = { Text(stringResource(R.string.settings_choose_details_mode)) },
                    text = {
                        Column {
                            OptionItem(
                                label = stringResource(R.string.settings_details_hidden),
                                selected = detailsMode == DetailsMode.OFF,
                                onClick = {
                                    SettingsManager.setDetailsMode(context, DetailsMode.OFF)
                                    showDetailsDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_details_pane),
                                selected = detailsMode == DetailsMode.PANE,
                                onClick = {
                                    SettingsManager.setDetailsMode(context, DetailsMode.PANE)
                                    showDetailsDialog = false
                                }
                            )
                            OptionItem(
                                label = stringResource(R.string.settings_details_bar),
                                selected = detailsMode == DetailsMode.BAR,
                                onClick = {
                                    SettingsManager.setDetailsMode(context, DetailsMode.BAR)
                                    showDetailsDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDetailsDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showDefaultAppsDialog) {
                AlertDialog(
                    onDismissRequest = { showDefaultAppsDialog = false },
                    title = { Text("Default Apps for File Types") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                "Configure your favorite default handler. In-App Universal Previewer opens files directly in the workspace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // PDF Files
                            Text("PDF Documents", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            OptionItem(
                                label = "In-App Previewer (PDFBox/Viewer)",
                                selected = defaultHandlerPdf == DefaultAppHandler.INTERNAL_PREVIEWER,
                                onClick = { SettingsManager.setDefaultHandlerPdf(context, DefaultAppHandler.INTERNAL_PREVIEWER) }
                            )
                            OptionItem(
                                label = "External System App",
                                selected = defaultHandlerPdf == DefaultAppHandler.EXTERNAL_APP,
                                onClick = { SettingsManager.setDefaultHandlerPdf(context, DefaultAppHandler.EXTERNAL_APP) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Images
                            Text("Images (PNG, JPG, SVG, GIF)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            OptionItem(
                                label = "In-App Previewer (Coil/PhotoEditor)",
                                selected = defaultHandlerImage == DefaultAppHandler.INTERNAL_PREVIEWER,
                                onClick = { SettingsManager.setDefaultHandlerImage(context, DefaultAppHandler.INTERNAL_PREVIEWER) }
                            )
                            OptionItem(
                                label = "External System App",
                                selected = defaultHandlerImage == DefaultAppHandler.EXTERNAL_APP,
                                onClick = { SettingsManager.setDefaultHandlerImage(context, DefaultAppHandler.EXTERNAL_APP) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Text & Code & Markdown
                            Text("Text, Code & Markdown", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            OptionItem(
                                label = "In-App Previewer (CodeEditor/Markwon)",
                                selected = defaultHandlerText == DefaultAppHandler.INTERNAL_PREVIEWER,
                                onClick = { SettingsManager.setDefaultHandlerText(context, DefaultAppHandler.INTERNAL_PREVIEWER) }
                            )
                            OptionItem(
                                label = "External System App",
                                selected = defaultHandlerText == DefaultAppHandler.EXTERNAL_APP,
                                onClick = { SettingsManager.setDefaultHandlerText(context, DefaultAppHandler.EXTERNAL_APP) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Audio & Video
                            Text("Audio & Video Media", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            OptionItem(
                                label = "In-App Previewer (Media3 ExoPlayer)",
                                selected = defaultHandlerMedia == DefaultAppHandler.INTERNAL_PREVIEWER,
                                onClick = { SettingsManager.setDefaultHandlerMedia(context, DefaultAppHandler.INTERNAL_PREVIEWER) }
                            )
                            OptionItem(
                                label = "External System App",
                                selected = defaultHandlerMedia == DefaultAppHandler.EXTERNAL_APP,
                                onClick = { SettingsManager.setDefaultHandlerMedia(context, DefaultAppHandler.EXTERNAL_APP) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Office Documents
                            Text("Office Documents (Word, Excel, PPT)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            OptionItem(
                                label = "In-App Previewer (Apache POI)",
                                selected = defaultHandlerOffice == DefaultAppHandler.INTERNAL_PREVIEWER,
                                onClick = { SettingsManager.setDefaultHandlerOffice(context, DefaultAppHandler.INTERNAL_PREVIEWER) }
                            )
                            OptionItem(
                                label = "External System App",
                                selected = defaultHandlerOffice == DefaultAppHandler.EXTERNAL_APP,
                                onClick = { SettingsManager.setDefaultHandlerOffice(context, DefaultAppHandler.EXTERNAL_APP) }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDefaultAppsDialog = false }) {
                            Text("Done")
                        }
                    }
                )
            }

            if (showShortcutsDialog) {
                LaunchedEffect(Unit) {
                    FileOperationsManager.showShortcuts()
                    val intent = Intent(context, PopUpActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    showShortcutsDialog = false
                }
            }


            if (showAboutDialog) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

                val context = LocalContext.current
                val appIcon = remember {
                    context.packageManager.getApplicationIcon(context.packageName)
                }

                val version = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "1.0"
                }

                AlertDialog(
                    onDismissRequest = { showAboutDialog = false },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = rememberAsyncImagePainter(appIcon),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )
                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("v$version • GPLv3", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Desktop-class file manager & offline suite. Forked from Continuum Explorer with integrated studios adapted from Markor and PhotoEditor.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Continuum (GPLv3)",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                        ),
                                        modifier = Modifier.clickable {
                                            uriHandler.openUri("https://github.com/troikoss/Continuum-Explorer")
                                        }
                                    )
                                    Text(
                                        text = "Markor (Apache)",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                        ),
                                        modifier = Modifier.clickable {
                                            uriHandler.openUri("https://github.com/gsantner/markor")
                                        }
                                    )
                                    Text(
                                        text = "PhotoEditor (MIT)",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                        ),
                                        modifier = Modifier.clickable {
                                            uriHandler.openUri("https://github.com/burhanrashid52/PhotoEditor")
                                        }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAboutDialog = false }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ShortcutCategory(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun ShortcutItem(keys: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = keys, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(text = action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultFolderOptionsPage(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val defaultViewMode = SettingsManager.defaultViewMode.value
    val defaultSortColumnType = SettingsManager.defaultSortColumnType.value
    val defaultSortOrder = SettingsManager.defaultSortOrder.value
    val showHiddenFiles = SettingsManager.showHiddenFiles.value
    val foldersFirstEnabled = SettingsManager.foldersFirst.value
    val groupingEnabled = SettingsManager.isGroupingEnabled.value
    val defaultHeaderDate = SettingsManager.defaultHeaderDate.value
    val defaultHeaderSize = SettingsManager.defaultHeaderSize.value
    val defaultHeaderType = SettingsManager.defaultHeaderType.value
    val defaultGridZoom = SettingsManager.defaultGridZoom.value

    var showResetDefaultsDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    if (showResetDefaultsDialog) {
        AlertDialog(
            onDismissRequest = { showResetDefaultsDialog = false },
            title = { Text(stringResource(R.string.settings_reset_folder_options_title)) },
            text = { Text(stringResource(R.string.settings_reset_folder_options_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager.resetDefaultFolderOptions(context)
                    showResetDefaultsDialog = false
                }) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDefaultsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text(stringResource(R.string.settings_reset_all_title)) },
            text = { Text(stringResource(R.string.settings_reset_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager.resetAllFolderOverrides(context)
                    showResetAllDialog = false
                }) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_default_folder_options)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.settings_default_view_mode),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            OptionItem(
                label = stringResource(R.string.menu_details),
                selected = defaultViewMode == ViewMode.DETAILS,
                onClick = { SettingsManager.setDefaultViewMode(context, ViewMode.DETAILS) }
            )
            OptionItem(
                label = stringResource(R.string.menu_content),
                selected = defaultViewMode == ViewMode.CONTENT,
                onClick = { SettingsManager.setDefaultViewMode(context, ViewMode.CONTENT) }
            )
            OptionItem(
                label = stringResource(R.string.menu_grid),
                selected = defaultViewMode == ViewMode.GRID,
                onClick = { SettingsManager.setDefaultViewMode(context, ViewMode.GRID) }
            )
            OptionItem(
                label = stringResource(R.string.menu_gallery),
                selected = defaultViewMode == ViewMode.GALLERY,
                onClick = { SettingsManager.setDefaultViewMode(context, ViewMode.GALLERY) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                stringResource(R.string.menu_sort),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                stringResource(R.string.settings_sort_by),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            OptionItem(
                label = stringResource(R.string.menu_by_name),
                selected = defaultSortColumnType == FileColumnType.NAME,
                onClick = { SettingsManager.setDefaultSortColumnType(context, FileColumnType.NAME) }
            )
            OptionItem(
                label = stringResource(R.string.menu_by_date),
                selected = defaultSortColumnType == FileColumnType.DATE,
                onClick = { SettingsManager.setDefaultSortColumnType(context, FileColumnType.DATE) }
            )
            OptionItem(
                label = stringResource(R.string.menu_by_size),
                selected = defaultSortColumnType == FileColumnType.SIZE,
                onClick = { SettingsManager.setDefaultSortColumnType(context, FileColumnType.SIZE) }
            )
            OptionItem(
                label = stringResource(R.string.menu_by_type),
                selected = defaultSortColumnType == FileColumnType.TYPE,
                onClick = { SettingsManager.setDefaultSortColumnType(context, FileColumnType.TYPE) }
            )
            Text(
                stringResource(R.string.settings_sort_order),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            OptionItem(
                label = stringResource(R.string.sort_ascending),
                selected = defaultSortOrder == SortOrder.Ascending,
                onClick = { SettingsManager.setDefaultSortOrder(context, SortOrder.Ascending) }
            )
            OptionItem(
                label = stringResource(R.string.sort_descending),
                selected = defaultSortOrder == SortOrder.Descending,
                onClick = { SettingsManager.setDefaultSortOrder(context, SortOrder.Descending) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                stringResource(R.string.settings_details_headers),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.details_header_date),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = defaultHeaderDate,
                    onCheckedChange = { SettingsManager.setDefaultHeaderDate(context, it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.details_header_size),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = defaultHeaderSize,
                    onCheckedChange = { SettingsManager.setDefaultHeaderSize(context, it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.details_header_type),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = defaultHeaderType,
                    onCheckedChange = { SettingsManager.setDefaultHeaderType(context, it) }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                stringResource(R.string.settings_default_zoom),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "${defaultGridZoom}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = defaultGridZoom.toFloat(),
                onValueChange = { SettingsManager.setDefaultGridZoom(context, it.toInt()) },
                valueRange = 60f..300f,
                steps = 47,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_show_hidden_files),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showHiddenFiles,
                    onCheckedChange = { SettingsManager.setShowHiddenFiles(context, it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.menu_folders_first),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = foldersFirstEnabled,
                    onCheckedChange = { SettingsManager.setFoldersFirst(context, it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.menu_group),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = groupingEnabled,
                    onCheckedChange = { SettingsManager.setGroupingEnabled(context, it) }
                )
            }
        }
    }
}

@Composable
fun OptionItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun DiagnosticsMemoryPanel(context: Context) {
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager }
    val memoryInfo = remember { android.app.ActivityManager.MemoryInfo() }

    var totalRamMb by remember {
        activityManager.getMemoryInfo(memoryInfo)
        mutableLongStateOf((memoryInfo.totalMem / (1024 * 1024)).coerceAtLeast(1L))
    }
    var freeRamMb by remember {
        mutableLongStateOf((memoryInfo.availMem / (1024 * 1024)).coerceAtLeast(1L))
    }
    var appUsedMb by remember {
        val runtime = Runtime.getRuntime()
        mutableLongStateOf(((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).coerceAtLeast(1L))
    }
    var isLowMemory by remember { mutableStateOf(memoryInfo.lowMemory) }

    fun refreshStats() {
        activityManager.getMemoryInfo(memoryInfo)
        totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        freeRamMb = memoryInfo.availMem / (1024 * 1024)
        isLowMemory = memoryInfo.lowMemory

        val runtime = Runtime.getRuntime()
        appUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000L)
            refreshStats()
        }
    }

    val usedRamMb = (totalRamMb - freeRamMb).coerceAtLeast(0L)
    val usedRatio = if (totalRamMb > 0) usedRamMb.toFloat() / totalRamMb.toFloat() else 0f

    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Real-Time RAM Usage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isLowMemory) "⚠️ Low Memory" else "Healthy",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLowMemory) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = { usedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (usedRatio > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Device RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalRamMb MB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Used / Free", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$usedRamMb MB / $freeRamMb MB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("App RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$appUsedMb MB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val freedBytes = com.troikoss.continuum_explorer.utils.TempCleanupManager.cleanTempFiles(context)
                        val freedMb = freedBytes / (1024f * 1024f)
                        refreshStats()
                        Toast.makeText(context, String.format("Cleared cache! Freed %.2f MB", freedMb), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
                ) {
                    Text("Clear Cache", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open settings: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
                ) {
                    Text("System Apps", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PixelCategoryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContainerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}
