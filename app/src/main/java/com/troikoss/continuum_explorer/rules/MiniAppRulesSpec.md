# Explorer — Mini-App, Windowing, Tab Lifecycle & Sidebar Rules Specification

This document and its companion Kotlin modules in `com.troikoss.continuum_explorer.rules` define the architectural rules for:
1. Adding and organizing Mini-Apps in **My Suite** (5 distinct Studios).
2. Standardizing the **50.dp Flush Top Heading Bar** with a **Triple-Line Menu Button (`Icons.Default.Menu`)** across all subpages and Mini-App editors.
3. Providing a **Minimalist Landing Page** (`MiniAppLandingScreen.kt`) for every Mini-App when opened from My Suite.
4. Launching files, previews, and editors across **DeX/Desktop**, **Tablet**, and **Mobile** (including standalone single-app window chrome on DeX).
5. Managing **Tab & Back-Stack Lifecycles** with zero memory leaks.
6. Enforcing a **Single Unified Sidebar** with no duplicate overlapping drawers.

---

## 1. Rules Files Directory

All workspace and mini-app rules live in `app/src/main/java/com/troikoss/continuum_explorer/rules/`:
- `MiniAppRulesSpec.md` (this specification file)
- `WorkspaceRulesConfig.kt` (declarative enums and rule models)
- `MiniAppRegistry.kt` (central registry of active and archived My Suite mini-apps: `file_converter`, `text_editor` [Markor Text & Markdown Studio], `code_studio` [Code Studio], `csv_editor` [CSV & Table Studio], `image_studio` [PhotoEditor Image Studio], and archived `base64_codec`)
- `WorkspaceWindowController.kt` (runtime controller for tabs, floating panels, and DeX multi-window intents)

---

## 2. Rule 1 — How Mini-Apps in "My Suite" Must Behave

Every Mini-App registered in `MiniAppRegistry.kt` MUST adhere to the following contract:
1. **Minimalist Studio Landing Page (`MiniAppLandingScreen.kt`)**:
   - Clicking a Mini-App card in **My Suite** (without a pre-selected file) opens its minimalist **Landing Page** featuring:
     1. **Create New File** (with extension chips tailored to that Studio),
     2. **Open Existing File to Edit** (using the redesigned universal `InAppFilePickerDialog` with List/Grid thumbnails, sort, and breadcrumbs), and
     3. **Recent Studio Files** filtered from `SuiteRecentDatabase` for that specific Studio.
   - Selecting **Edit** from a file's three-dot menu in Explorer bypasses the Landing Page and opens the file directly in its matching Studio editor.
2. **50.dp Flush Top Heading Bar with Triple-Line Menu Button (`Icons.Default.Menu`)**:
   - Every App Subpage (`My Suite`, `FTP/HTTP Server`, `Add FTP Server`) and every active Mini-App Editor/Workbench (`Markor Studio`, `Code Studio`, `CSV Studio`, `PhotoEditor`, `File Converter`) MUST use a 50.dp flush top heading bar with the **Triple-Line Menu Button (`Icons.Default.Menu`)** on the left (instead of a Close/`X` button) to toggle the unified navigation sidebar, plus the Fullscreen toggle on the right.
3. **5 Separated Studios in My Suite**:
   - `file_converter`: File Converter (`VERT` + `Squoosh` Exact-KB Engine)
   - `text_editor`: Markor Text & Markdown Studio (`.md`, `.txt`, `.todo.txt`, `.org`, `.adoc`, `.log`)
   - `code_studio`: Code Studio (`.kt`, `.java`, `.py`, `.js`, `.ts`, `.html`, `.css`, `.json`, `.xml`, `.yaml`, `.sh`, `.sql`)
   - `csv_editor`: CSV & Table Studio (`.csv`, `.tsv`) — CSV files are routed exclusively here and never into raw text mode.
   - `image_studio`: PhotoEditor Image Studio (`.png`, `.jpg`, `.jpeg`, `.webp`, `.bmp`)
4. **Recent Activity Recording (`SuiteRecentDatabase`)**:
   - Whenever a Mini-App creates, edits, or converts a file, it MUST call `SuiteRecentDatabase.recordFileActivity(...)`.
   - The **My Suite -> Recently Edited & Created** section supports both **List** and **Grid** layouts controlled by the My Suite heading bar toggle.
5. **Deterministic Memory & Resource Teardown**:
   - Every Mini-App composable and conversion engine must release heavy allocations (`Bitmap`, `PdfRenderer`, `PdfDocument`, `MediaCodec`, `MediaExtractor`, `MediaMuxer`, `ExoPlayer`, `WebView`, temporary cache directories) inside `finally` blocks or `DisposableEffect(Unit) { onDispose { ... } }`.

---

## 3. Rule 2 — Tab, Floating Panel & Multi-Window Creation (`WorkspaceWindowMode`)

Users can select their preferred windowing behavior in **Settings -> Editors, Previewers & Handlers -> Workspace & Windowing Mode**, with **Option 1** as the default:

1. **Option 1 (`DEX_WINDOWS_TABLET_MOBILE_TABS` — Default)**:
   - **DeX / Desktop Freeform (`screenWidthDp > 1000` or desktop windowing)**: Mini-Apps, Server Hub, and standalone editors launch in a separate OS multi-instance window (`FLAG_ACTIVITY_NEW_DOCUMENT` / `FLAG_ACTIVITY_MULTIPLE_TASK`) with `isSingleAppWindow = true`.
   - **Separate Desktop / DeX Window Chrome Rule**: When `isSingleAppWindow = true`, the top Explorer `TabBar` is automatically hidden so the Mini-App or file editor looks and behaves like a standalone native desktop app, and pressing Back finishes the standalone window cleanly.
   - **Tablet & Mobile**: Opens in an animated in-app tab (`AnimatedContent` transition) that remembers its `originTabIndex`.
2. **Option 2 (`ANIMATED_TABS_ALL_SCREENS`)**:
   - All screens (DeX, Tablet, Mobile) open Mini-Apps and file editors as animated in-app tabs.
3. **Option 3 (`FLOATING_PANELS_DEX_TABLET_TABS_MOBILE`)**:
   - **DeX & Tablet (`screenWidthDp >= 600`)**: Opens Mini-Apps and file editors inside a draggable, minimizable, resizable floating window (`FloatingMiniAppHost`) over the active workspace.
   - **Mobile**: Opens as an animated in-app tab.

---

## 4. Rule 3 — File Opening, Previewing, CSV, PDF & 3-Mode Audio Policy

When a user interacts with a file in Explorer:
1. **Editable Files**:
   - **Single Click / Open**: Opens in **Preview Mode** (`ViewOnlyFileViewer`) with a prominent **Edit** button in the top bar. For `.csv`/`.tsv` files, renders an interactive spreadsheet table preview (`CsvInteractiveTablePreview`) with sticky headers, row numbers, and search filter.
   - **Three-Dot Menu -> "Edit"**: Opens **directly in the matching Studio Editor UI** (`openInEditMode = true`).
2. **Audio Files (`.mp3`, `.wav`, `.flac`, `.m4a`, `.aac`, `.ogg`, `.opus`) — 3-Mode Music Player**:
   - Tapping an audio file in Explorer starts playback immediately in the **Persistent Bottom Mini-Player Bar** (`SpotifyAudioPlayerOverlay`) without leaving the current folder.
   - Users can switch seamlessly between:
     1. **Persistent Bottom Mini-Player Bar**,
     2. **Draggable Floating PiP Mini-Card** over files, and
     3. **Spotify-Style Fullscreen Music Player** with dynamic artwork-tinted gradient, large album art, seek bar, shuffle, repeat, speed control, and folder playlist queue.
3. **PDF Documents (`.pdf`) — Desktop-Class PDF Viewer**:
   - Renders with a single unified top bar (zero duplicate headers) and a desktop control bar supporting Page Jump (`Page X / Y`), Continuous / Single Page / 2-Page Spread modes, Zoom (`-`/`100%`/`+`), `90°` Rotation, Day / Night Inverted / Sepia modes, Thumbnail strip, PDF text search, Print (`PrintManager`), and Quick Compress PDF.
4. **Android Package Files (`.apk`) & Binary Files**:
   - `.apk` files open `ApkInstallerSheet`; unsupported binary files open `NonEditableFileInfoSheet`.
