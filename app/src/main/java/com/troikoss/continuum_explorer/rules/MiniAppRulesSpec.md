# Explorer — Mini-App, Windowing, Tab Lifecycle & Sidebar Rules Specification

This document and its companion Kotlin modules in `com.troikoss.continuum_explorer.rules` define the architectural rules for:
1. Adding new Mini-Apps to **My Suite**.
2. Launching files, previews, and editors across **DeX/Desktop**, **Tablet**, and **Mobile**.
3. Managing **Tab & Back-Stack Lifecycles** with zero memory leaks.
4. Enforcing a **Single Unified Sidebar** with no duplicate overlapping drawers.

---

## 1. Rules Files Directory

All workspace and mini-app rules live in `app/src/main/java/com/troikoss/continuum_explorer/rules/`:
- `MiniAppRulesSpec.md` (this specification file)
- `WorkspaceRulesConfig.kt` (declarative enums and rule models)
- `MiniAppRegistry.kt` (central registry of active and archived My Suite mini-apps, including `file_converter`, `text_editor`, `image_studio`, `csv_editor`, and archived `base64_codec`)
- `WorkspaceWindowController.kt` (runtime controller for tabs, floating panels, and DeX multi-window intents)

---

## 2. Rule 1 — How New Mini-Apps in "My Suite" Must Behave

Every Mini-App registered in `MiniAppRegistry.kt` MUST adhere to the following contract:
1. **Direct Launch (No Intermediate Landing Screen)**:
   - Clicking a Mini-App card in **My Suite** or clicking **Edit** / **Convert with File Converter** on a supported file MUST open the functional workspace immediately (either with the target file(s) loaded, the VERT File Converter workbench, an active scratchpad/new document, or an immediate file selector for image tools)—never an extra static mid-page.
2. **Recent Activity Recording (`SuiteRecentDatabase`)**:
   - Whenever a Mini-App creates, edits, or converts a file (including `FileConverterDispatcher`), it MUST call `SuiteRecentDatabase.recordFileActivity(context, file, toolId, toolTitle, SuiteActivityType.CREATED / EDITED)`.
   - The **My Suite -> Recent Edited & Created** section reads exclusively from `SuiteRecentDatabase` and never from device-wide `MediaStore`.
3. **Deterministic Memory & Resource Teardown**:
   - Every Mini-App composable and conversion engine must release heavy allocations (`Bitmap`, `PdfRenderer`, `PdfDocument`, `MediaCodec`, `MediaExtractor`, `MediaMuxer`, `ExoPlayer`, `WebView`, temporary cache directories) inside `finally` blocks or `DisposableEffect(Unit) { onDispose { ... } }`.
4. **Archiving Deprecated Mini-Apps**:
   - Mini-Apps that are retired from active UI (such as the Base64 Codec) must be set to `isActiveInUi = false` and moved under `com.troikoss.continuum_explorer.archive.<feature>` so the main UI stays lean while preserving source code for future reuse.

---

## 3. Rule 2 — Tab, Floating Panel & Multi-Window Creation (`WorkspaceWindowMode`)

Users can select their preferred windowing behavior in **Settings -> Editors, Previewers & Handlers -> Workspace & Windowing Mode**, with **Option 1** as the default:

1. **Option 1 (`DEX_WINDOWS_TABLET_MOBILE_TABS` — Default)**:
   - **DeX / Desktop Freeform (`screenWidthDp > 1000` or desktop windowing)**: Mini-Apps, Server Hub, and standalone editors can spawn in a separate OS multi-instance window (`FLAG_ACTIVITY_NEW_DOCUMENT` / `FLAG_ACTIVITY_MULTIPLE_TASK`) or animated tab with full return-to-origin tracking.
   - **Tablet & Mobile**: Opens in an animated in-app tab (`AnimatedContent` slide/fade transition) that remembers its `originTabIndex`.
2. **Option 2 (`ANIMATED_TABS_ALL_SCREENS`)**:
   - All screens (DeX, Tablet, Mobile) open Mini-Apps and file editors as animated in-app tabs.
3. **Option 3 (`FLOATING_PANELS_DEX_TABLET_TABS_MOBILE`)**:
   - **DeX & Tablet (`screenWidthDp >= 600`)**: Opens Mini-Apps and file editors inside a draggable, minimizable, resizable floating window (`FloatingMiniAppHost`) over the active workspace.
   - **Mobile**: Opens as an animated in-app tab.

---

## 4. Rule 3 — File Opening, Previewing & Editing Policy (`FileOpenPolicyEngine`)

When a user interacts with a file in Explorer:
1. **Editable Files (`txt`, `md`, `json`, `xml`, `html`, `css`, `js`, `ts`, `kt`, `java`, `py`, `sh`, `ini`, `yaml`, `yml`, `org`, `adoc`, `csv`, `tsv`, `jpg`, `jpeg`, `png`, `webp`, `bmp`)**:
   - **Single Click / Open**: Opens in **Preview Mode** (`ViewOnlyFileViewer`) with a prominent **Edit** button in the top bar.
   - **Three-Dot Menu -> "Edit"**: Opens **directly in the Editor UI** (`openInEditMode = true`) without showing the read-only preview first.
2. **Playable / Viewable Non-Editable Files (`mp4`, `mkv`, `webm`, `mp3`, `wav`, `flac`, `ogg`, `m4a`, `pdf`, `zip`, `tar`, `gz`, `7z`, `rar`, `jar`)**:
   - Opens in the dedicated media player, PDF reader, or archive browser—never raw binary text.
3. **Android Package Files (`.apk`)**:
   - Never opened as a ZIP archive folder tree. Opens the **APK Package Info & Installer Sheet** (`ApkInstallerSheet`) showing app icon, package name, version, SDK targets, and **Install with Package Installer**.
4. **Unsupported / Binary Files**:
   - Never opened as raw binary text. Opens the **Non-Editable File Info Sheet** (`NonEditableFileInfoSheet`) showing file metadata and a **Choose Preferred App to View** button.

---

## 5. Rule 4 — Unified Sidebar Appearance & Collapse Rules

1. **Mobile (`ScreenSize.SMALL`)**:
   - Uses a single `ModalNavigationDrawer`. Top bars show a single `Menu` icon to open the drawer.
2. **Tablet & DeX (`ScreenSize.MEDIUM` / `ScreenSize.LARGE`)**:
   - Uses a single collapsible `PermanentDrawerSheet` controlled by `appState.isSidebarCollapsed`.
   - The `ModalNavigationDrawer` gestures and scrim are disabled on Tablet & DeX so a second sidebar can never appear over the permanent sidebar.
   - The collapse button (`ArrowBack` / `MenuOpen`) inside `NavigationPane` collapses `PermanentDrawerSheet`. When collapsed, a single expand button in the top bar restores it.
   - Subpages (`My Suite`, `FTP/HTTP Server`, `External FTP`) hide their duplicate hamburger menu button whenever the permanent sidebar is already expanded on Tablet/DeX.
