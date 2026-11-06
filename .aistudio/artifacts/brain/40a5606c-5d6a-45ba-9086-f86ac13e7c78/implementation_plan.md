# Continuum Explorer (Explorer) — UX, Windowing Rules, Server & Modular Architecture Plan

## 1. Executive Summary & Architectural Goals

This plan addresses all UX, navigation, sidebar, file-handling, server-hosting, and modularity improvements requested:

1. **Logical File Opening, Previewing & Direct Editing Flow**:
   - **Editable Files (Text, Code, Markdown, CSV, JSON, XML, Images)**:
     - **Single-Tap / Open in Explorer**: Opens the clean **File Preview** tab (`ViewOnlyFileViewer`) with a prominent **Edit** button in the top action bar.
     - **Three-Dot Menu / Context Menu ("Edit") or Opening from My Suite**: Bypasses all intermediate preview/landing screens and opens **directly inside the Editor UI** (`MarkorTextEngine`, `CsvTableEditor`, or `PhotoEditorStudio`).
     - **Launching Editors in My Suite**: Clicking an editor card in My Suite immediately opens a fresh untitled document or file creation/picker action directly inside the editor—eliminating the redundant middle landing screen.
   - **Playable / Viewable Non-Editable Files (Video, Audio, PDF, Archives like `.zip`/`.tar`)**:
     - Opens directly in the rich media/document/archive viewer (never raw binary text).
   - **APK Files (`.apk`)**:
     - Never opened as a ZIP folder structure. Tapping an `.apk` displays a dedicated **APK Package Info Sheet** (showing app icon, label, package name, version name/code, target/min SDK, file size, and SHA-256) with a primary **Install with Package Installer** button (`Intent.ACTION_VIEW` with `application/vnd.android.package-archive` via `FileProvider`). No "Browse Archive" button is shown.
   - **Unsupported / Binary Non-Editable Files**:
     - Never dumped as raw binary garbage. Shows a clean **File Info & Open-With Sheet** displaying file properties and a **Choose Preferred App to View** (`Intent.createChooser`) button.

2. **Leak-Free Tab & Back-Stack Lifecycle**:
   - Every tab stores an explicit `originTabIndex: Int?` and `returnFolderState` when spawned by opening/editing a file or launching a My Suite tool.
   - Pressing **Back**, **Save & Close**, or **Close** inside a temporary preview/editor tab cleanly disposes buffers/Bitmaps/ExoPlayer instances, closes the temporary tab, and restores the exact originating tab and scroll position—never dumping the user onto the root `/storage/emulated/0` homepage.
   - Navigating folders inside a tab pushes onto that tab's `backStack` so system Back steps backward one folder at a time before returning to the parent tab.

3. **Unified Single Sidebar & Responsive Navigation (Mobile, Tablet, DeX)**:
   - **Mobile (`ScreenSize.SMALL`)**: Uses a single `ModalNavigationDrawer` opened by the TopBar menu icon.
   - **Tablet & DeX (`ScreenSize.MEDIUM` / `LARGE`)**: Uses a single collapsible `PermanentDrawerSheet` / Rail controlled by `isSidebarCollapsed` state.
   - **Eliminate Duplicate Sidebars & Broken Back Buttons**:
     - When the Tablet/DeX sidebar is visible, the redundant hamburger `Menu` button in subpages (`My Suite`, `FTP/HTTP Server`, `External FTP`, `TopBar`) is either hidden or wired directly to toggle `isSidebarCollapsed`—never opening a second `ModalNavigationDrawer` on top of the permanent sidebar.
     - The collapse button (`ArrowBack` / `MenuOpen`) inside `NavigationPane` is wired to `onToggleSidebar` so it actually collapses the sidebar on Tablet/DeX.

4. **Workspace, Windowing & Mini-App Rules System (User-Selectable in Settings)**:
   - Configurable in **Settings -> Workspace & Windowing Mode** with three modes (defaulting to Option 1):
     1. **Mode 1 (Default)**: *Separate Multi-Instance Windows on DeX/Desktop (`FLAG_ACTIVITY_NEW_DOCUMENT` / `FLAG_ACTIVITY_MULTIPLE_TASK` + Freeform bounds), Animated In-App Tabs on Tablet & Mobile*.
     2. **Mode 2**: *Animated In-App Tabs across DeX, Tablet, and Mobile*.
     3. **Mode 3**: *Floating Resizable Desktop Panels on DeX and Tablet, Animated Tabs on Mobile*.
   - **Exact Rules File Locations**:
     - `app/src/main/java/com/troikoss/continuum_explorer/rules/MiniAppRulesSpec.md` — Human-readable architectural specification for adding future Mini-Apps, window/tab creation, sidebar visibility, and UI standards.
     - `app/src/main/java/com/troikoss/continuum_explorer/rules/WorkspaceRulesConfig.kt` — Declarative rules data models (`WindowingPolicy`, `SidebarVisibilityRule`, `MiniAppDescriptor`, `TabAnimationSpec`, `FileOpenResolutionRule`).
     - `app/src/main/java/com/troikoss/continuum_explorer/rules/MiniAppRegistry.kt` — Central registry of My Suite Mini-Apps and file-extension routing rules.
     - `app/src/main/java/com/troikoss/continuum_explorer/rules/WorkspaceWindowController.kt` — Runtime engine executing tab transitions, floating window overlays, or DeX multi-window intents based on the user's selected rule.

5. **My Suite Recent Edited/Created Database**:
   - Replaces the device-wide `MediaStore` query in My Suite with a dedicated persistence store (`SuiteActivityDatabase.kt`) that records **only files created or edited inside My Suite / Explorer Editors**, sorted by last edited timestamp with tool badge, file type, and quick-resume action.

6. **Homepage Quick Preview Bar & Side-Pane Polish**:
   - Makes the Homepage Quick Preview (`WorkspacePreviewPane` / `ViewAreaWindow`) cleanly toggleable from the TopBar eye icon and file context menu, respecting the new APK/binary/media preview rules without cramping narrow windows.

7. **HTTP/FTP Server Hub, Screen Sharing Fix, Responsive Web Portal & Notification Title**:
   - **Notification Title**: Changes the foreground notification title in `ContinuumHubService.kt` from `"Continuum Server Hub"` to `"Explorer"`.
   - **Android 8–15 Screen Mirroring Fix (`ScreenCastManager.kt`)**: Registers `MediaProjection.Callback` before calling `createVirtualDisplay()` (mandatory on Android 14 / API 34+ and Android 15), uses a dedicated background `HandlerThread` for `ImageReader` frame capture, and ensures `ContinuumHubService` starts/attaches the MJPEG stream dynamically even if the HTTP server is already running.
   - **Hideable Screen-Share Player & Responsive Web Portal (`KtorEmbeddedServer.kt` + `WebPortalTemplates.kt`)**:
     - On the FTP/HTTP File Hosting portal, the screen-mirror video player is **hidden by default** behind a clean **"Live Screen Stream (Show/Hide)"** toggle pill in the header (and only shown when screen mirroring is active).
     - Redesigns the hosted web file browser with a responsive grid/table switcher, breadcrumb bar, drag-and-drop upload zone, media preview modal, and adaptive layout for mobile, tablet, and desktop browsers.

8. **Archive Base64 Codec Feature**:
   - Removes Base64 Codec from active UI (`VibeAreaScreen.kt` and `EditorContainer.kt`) and archives its implementation inside `app/src/main/java/com/troikoss/continuum_explorer/archive/base64/ArchivedBase64Codec.kt` for future reference.

9. **Strict Modularity (Separate UI, Functionality/Logic, and Database Files)**:
   - Splits oversized files into focused, fast-compiling modules under `data/db/`, `domain/logic/`, `rules/`, and `ui/` so every file remains concise and easy to maintain.

---

## 2. Where the Rules Files Are Located

The new Mini-App, Tab/Window Creation, Sidebar, and UI Rules system is stored in:

1. **`/app/src/main/java/com/troikoss/continuum_explorer/rules/MiniAppRulesSpec.md`**
   - Complete developer documentation defining how every new Mini-App in **My Suite** must declare its ID, supported extensions, memory cleanup hook, sidebar behavior, and launch mode across Mobile, Tablet, and DeX.
2. **`/app/src/main/java/com/troikoss/continuum_explorer/rules/WorkspaceRulesConfig.kt`**
   - Defines `WorkspaceWindowMode` (`DEX_WINDOWS_MOBILE_TABS`, `ALL_ANIMATED_TABS`, `FLOATING_PANELS_DESKTOP_TABLET`), `SidebarVisibilityMode`, `TabLifecycleRule`, and `FileLaunchRule`.
3. **`/app/src/main/java/com/troikoss/continuum_explorer/rules/MiniAppRegistry.kt`**
   - Central registry mapping active Mini-Apps (Markor Text & Code Studio, PhotoEditor Studio, CSV & Table Studio) and archived Mini-Apps (Base64 Codec) with extension routing and direct-launch behavior.
4. **`/app/src/main/java/com/troikoss/continuum_explorer/rules/WorkspaceWindowController.kt`**
   - Enforces back-stack origin tracking (`originTabId`), animated tab transitions, floating window state (`FloatingPanelState`), and DeX multi-window `Intent` launching.

---

## 3. File-by-File Modular Implementation Breakdown

### A. Database & Persistence Layer (`data/db/`)
- **`/app/src/main/java/com/troikoss/continuum_explorer/data/db/SuiteRecentDatabase.kt` (New)**:
  - Lightweight SQLite / JSON-backed database tracking only files **created or edited** in My Suite or Explorer Editors (`path`, `name`, `extension`, `editorId`, `lastModified`, `actionType = CREATED | EDITED`), with automatic pruning of deleted files and zero memory overhead.

### B. Rules & Domain Logic Layer (`rules/` & `domain/logic/`)
- **`/app/src/main/java/com/troikoss/continuum_explorer/rules/MiniAppRulesSpec.md` (New)**
- **`/app/src/main/java/com/troikoss/continuum_explorer/rules/WorkspaceRulesConfig.kt` (New)**
- **`/app/src/main/java/com/troikoss/continuum_explorer/rules/MiniAppRegistry.kt` (New)**
- **`/app/src/main/java/com/troikoss/continuum_explorer/rules/WorkspaceWindowController.kt` (New)**
- **`/app/src/main/java/com/troikoss/continuum_explorer/domain/logic/FileOpenPolicyEngine.kt` (New)**:
  - Classifies any `UniversalFile` into:
    - `EDITABLE_TEXT` / `EDITABLE_TABLE` / `EDITABLE_IMAGE` -> Single-tap opens Preview with Edit button; "Edit" menu action opens Editor directly; My Suite opens Editor directly.
    - `PLAYABLE_MEDIA_OR_DOC` (Video, Audio, PDF, ZIP/TAR/7Z/RAR excluding APK) -> Opens rich viewer.
    - `APK_PACKAGE` (`.apk`) -> Opens APK Info & Package Installer Sheet (never Archive folder view).
    - `NON_EDITABLE_BINARY` -> Opens File Info & Choose Preferred App sheet (never raw binary text).
- **`/app/src/main/java/com/troikoss/continuum_explorer/domain/logic/ApkMetadataExtractor.kt` (New)**:
  - Uses Android `PackageManager.getPackageArchiveInfo` to extract the APK's application icon, app name, package name, version name, version code, minSdk, targetSdk, and permissions, plus launches the system Package Installer via `FileProvider`.

### C. Archiving Base64 Codec (`archive/base64/`)
- **`/app/src/main/java/com/troikoss/continuum_explorer/archive/base64/ArchivedBase64Codec.kt` (New)**:
  - Preserves the Base64 encoder/decoder logic and UI composable in an archived module removed from active UI navigation.

### D. UI Components & Screens (`ui/`)
- **`/app/src/main/java/com/troikoss/continuum_explorer/ui/dialogs/ApkInstallerSheet.kt` (New)**:
  - Material 3 modal dialog/sheet showing APK icon, app label, package ID, version, SDK levels, file size, and **Install with Package Installer** primary button (no "Browse Archive" button).
- **`/app/src/main/java/com/troikoss/continuum_explorer/ui/dialogs/NonEditableFileInfoSheet.kt` (New)**:
  - Clean file info sheet for unsupported/binary files with a **Choose Preferred App to View** button.
- **`/app/src/main/java/com/troikoss/continuum_explorer/ui/components/FloatingMiniAppHost.kt` (New)**:
  - Renders draggable, resizable floating windows on Tablet/DeX when Windowing Mode 3 is selected in Settings.
- **`/app/src/main/java/com/troikoss/continuum_explorer/ui/FileExplorer.kt` & `/app/src/main/java/com/troikoss/continuum_explorer/utils/FileExplorerState.kt`**:
  - Adds `originTabIndex` to `ExplorerState` so closing a preview/editor tab returns to the exact source tab and folder scroll state.
  - Unifies the sidebar into a single collapsible rail/drawer on Tablet & DeX (`isSidebarCollapsed`) and single modal drawer on Mobile—eliminating overlapping drawers.
  - Adds animated tab creation/closing transitions.
- **`/app/src/main/java/com/troikoss/continuum_explorer/ui/components/NavigationPane.kt`**:
  - Connects the collapse/hide button on Tablet & DeX to `onToggleSidebar` and hides it when inappropriate.
- **`/app/src/main/java/com/troikoss/continuum_explorer/ui/components/VibeAreaScreen.kt` & `/app/src/main/java/com/troikoss/continuum_explorer/ui/editor/EditorContainer.kt`**:
  - Removes the redundant mid-page before editing; clicking a tool or file opens the editor directly.
  - Connects the "Recent" section in My Suite exclusively to `SuiteRecentDatabase`.
  - Removes Base64 Codec from the My Suite grid.
  - Hides the duplicate hamburger menu button when the Tablet/DeX permanent sidebar is already visible.
- **`/app/src/main/java/com/troikoss/continuum_explorer/ui/components/ViewOnlyFileViewer.kt` & `/app/src/main/java/com/troikoss/continuum_explorer/ui/components/ItemRow.kt` & `ContextMenus.kt`**:
  - Adds a direct **Edit** item with icon to the three-dot menu of all supported editable files (`txt`, `md`, `json`, `xml`, `csv`, `html`, `css`, `js`, `kt`, `py`, `jpg`, `png`, `webp`).
  - Removes `.apk` from `isArchive` in `ViewOnlyFileViewer.kt`.
- **`/app/src/main/java/com/troikoss/continuum_explorer/ui/activities/SettingsScreen.kt` & `/app/src/main/java/com/troikoss/continuum_explorer/utils/SettingsManager.kt`**:
  - Adds the **Workspace & Mini-App Windowing Mode** selector (Options 1, 2, and 3, with Option 1 as default).

### E. Server & Screen Sharing Layer (`server/`)
- **`/app/src/main/java/com/troikoss/continuum_explorer/server/ContinuumHubService.kt`**:
  - Changes notification title to `"Explorer"`.
  - Allows starting/stopping Screen Mirroring dynamically while the HTTP server is already running.
- **`/app/src/main/java/com/troikoss/continuum_explorer/server/ScreenCastManager.kt`**:
  - Registers `MediaProjection.Callback` before `createVirtualDisplay()` for Android 14/15 compatibility and pumps frames on a dedicated background `HandlerThread`.
- **`/app/src/main/java/com/troikoss/continuum_explorer/server/WebPortalTemplates.kt` (New) & `/app/src/main/java/com/troikoss/continuum_explorer/server/KtorEmbeddedServer.kt`**:
  - Extracts and upgrades the web portal HTML/CSS/JS into a responsive, interactive file-hosting UI where the live screen stream player is hidden by default behind a toggle button and scales cleanly across mobile, tablet, and desktop aspect ratios.

### F. Help Documentation (`ui/components/HelpDocumentationSection.kt`)
- Updates the Help page with complete documentation of the new file opening/editing rules, APK installer flow, My Suite Recent Edited database, Workspace Windowing Modes (1/2/3), unified sidebar, and HTTP/FTP web portal + screen mirroring upgrades.
