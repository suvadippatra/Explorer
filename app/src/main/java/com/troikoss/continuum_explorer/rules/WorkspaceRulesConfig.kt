package com.troikoss.continuum_explorer.rules

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * User-configurable Workspace & Mini-App Windowing Mode.
 * Stored in SettingsManager and enforced by WorkspaceWindowController.
 */
enum class WorkspaceWindowMode(
    val id: Int,
    val title: String,
    val description: String
) {
    DEX_WINDOWS_TABLET_MOBILE_TABS(
        id = 1,
        title = "1. Separate Windows on DeX, Animated Tabs on Tablet & Mobile (Default)",
        description = "Launches separate multi-instance windows on DeX/Desktop (or clean return-tabs) and smooth animated tabs on Tablet and Mobile."
    ),
    ANIMATED_TABS_ALL_SCREENS(
        id = 2,
        title = "2. Animated In-App Tabs across DeX, Tablet & Mobile",
        description = "Opens all editors, previews, and My Suite mini-apps inside animated in-app tabs with automatic back-to-origin tab restoration."
    ),
    FLOATING_PANELS_DEX_TABLET_TABS_MOBILE(
        id = 3,
        title = "3. Floating Resizable Panels on DeX & Tablet, Tabs on Mobile",
        description = "Opens My Suite mini-apps and editors inside movable, resizable desktop panels on Tablet/DeX and animated tabs on Mobile."
    );

    companion object {
        fun fromId(id: Int): WorkspaceWindowMode {
            return entries.firstOrNull { it.id == id } ?: DEX_WINDOWS_TABLET_MOBILE_TABS
        }
    }
}

/**
 * Defines how the navigation sidebar behaves across screen form factors.
 */
enum class SidebarDisplayRule {
    /** Mobile (< 600dp): Modal drawer only, triggered by hamburger button. */
    MODAL_DRAWER_ONLY,
    /** Tablet & DeX (>= 600dp): Single collapsible rail/sidebar; never opens a second modal drawer on top. */
    COLLAPSIBLE_SINGLE_RAIL
}

/**
 * Defines how a Mini-App in "My Suite" is registered and launched.
 */
data class MiniAppRuleDescriptor(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector,
    val accentColor: Color,
    val supportedExtensions: Set<String>,
    val isActiveInUi: Boolean = true,
    val archivePackagePath: String? = null,
    val requiresInitialFileFromDisk: Boolean = false,
    val defaultNewFileName: String? = null
)
