# Continuum Explorer — Full UI, Server Hub, Suite, Responsive Layout & CI/CD Upgrade Plan

## 1. Eliminate Window Margins & Fix Text Clipping Across All Pages
- **Root Cause Identified**: In `FileContent.kt`, `LazyVerticalGrid` applies `.padding(horizontal = 32.dp)` (or `16.dp`), `.padding(end = 16.dp)`, and `contentPadding = PaddingValues(16.dp)`, while `FileExplorer.kt` adds outer container padding. On tablet freeform/split windows, this consumes over `112.dp` of horizontal space and clips the left and right edges of `DetailsHeader` and `FileDetailsView` rows (e.g., clipping `"Name"` to `"ne ^"` and `"Size"` to `"Siz"`).
- **Fixes**:
  - Remove the excessive outer `.padding(horizontal = 32.dp)` and redundant `contentPadding` from `FileContent.kt` and `FileExplorer.kt`, replacing them with clean edge-to-edge `6.dp`–`8.dp` responsive padding so the file table and all pages use the full window width.
  - Make `DetailsHeader.kt` and `FileDetailsView` in `FileView.kt` dynamically size column widths (`NAME`, `DATE`, `TYPE`, `SIZE`) relative to available window width while keeping horizontal scroll synchronized and aligned so text never clips at the left or right borders.

## 2. Full-Screen Toggle (Hiding the Tab Bar)
- **Global Full-Screen State**: Add an `isFullScreenMode` state in `MainActivity.kt` / `FileExplorerState` that hides the top `TabBar` row when enabled, allowing the active page or editor to occupy 100% of the window height.
- **Accessible Controls**:
  - Add a Full-Screen toggle icon button (`Fullscreen` / `FullscreenExit`) in `TopBar.kt`, `ViewOnlyFileViewer.kt` (separate-tab file preview), `VibeAreaScreen.kt` ("My suite"), `FtpServerSettingsPanel.kt`, `MarkorTextEngine.kt`, and `NativeImageViewerEditor.kt`.
  - Support `BackHandler` or floating/header exit button when in Full-Screen mode so users can always restore the tab bar with a single tap.

## 3. Restore List / Grid / Details / Gallery View Switcher & Column Count Slider
- **Quick View Switcher in `TopBar.kt`**:
  - Add a dedicated View Mode button in `TopBar.kt` (next to Search and Options) that opens a compact popover/menu allowing instant switching between:
    - **Details** (multi-column table)
    - **Content / List** (compact single-column list)
    - **Grid** (icon/thumbnail cards)
    - **Gallery** (square media tiles)
  - Include an interactive **Column Count / Grid Size Slider** (`2` to `8` columns on Grid/Gallery, or dynamic item size scaling) directly inside the View Mode popover, persisted per-folder via `FolderConfigs`.

## 4. Fix Sidebar Internal Storage Capacity Text (`NavigationPane.kt`)
- **Responsive Capacity Label**: Update `NavStorageItem` and `NavNetworkItem` in `NavigationPane.kt` so the `"XX GB free of XX GB"` text uses `maxLines = 1`, `TextOverflow.Ellipsis`, adaptive font sizing (`11.sp`), and compact horizontal padding so it never wraps awkwardly or breaks in narrow sidebar widths.

## 5. Redesign "My Suite" (`VibeAreaScreen.kt`) with File Creation & Editable File Selection
- **Minimalist Workspace UI**: Redesign `VibeAreaScreen.kt` with a clean, consistent header (Menu, Title, New File button, Open File button, Fullscreen toggle, View toggle, Search) and streamlined studio cards.
- **Create New File by Extension**:
  - Add a **"New File"** dialog in "My Suite" where the user enters a file name with any extension (e.g., `notes.md`, `tasks.todo.txt`, `data.csv`, `script.kt`, `config.json`, `doc.org`, `readme.adoc`, `log.txt`) and selects the target directory (defaulting to Documents or current folder).
  - Automatically create the file on disk and open it immediately in the matching specialized Markor editor layout (Markdown preview/editor, interactive CSV grid, interactive Todo.txt checklist, syntax-highlighted code editor, or plain text).
- **Select & Edit Existing Supported Files**:
  - Add an **"Open File to Edit"** picker in "My Suite" that filters and allows selecting only editable file types (Text/Markdown/Code/CSV/Todo/JSON/XML for Text Studio; PNG/JPG/WEBP for Image Studio; files < 2 MB for Base64 Studio) while gracefully blocking unsupported non-editable formats such as Video or APK files with a clear notice.

## 6. Redesign FTP/HTTP & Self-Hosted Website Server Hub (`FtpServerSettingsPanel.kt` & `ContinuumHubService.kt`)
- **Minimalist Segmented Mode Toggle**:
  - Redesign `FtpServerSettingsPanel.kt` around a clean top Segmented Button toggle switching between two dedicated workflows:
    1. **FTP & File Sharing Mode**: Select specific files or folders into the virtual share bucket (or share full storage), configure FTP port, anonymous/authenticated access, and web upload permissions, and start/stop the server with live IP/Port/QR and one-tap copy.
    2. **Self-Hosted Website Mode (Local Web Repo Hosting)**: Select either a specific `.html` file or a website directory (such as a static web repository downloaded from GitHub containing `index.html`, CSS, JS, images, and sub-routes).
- **Working Server Engine & Responsive Web Portal (`ContinuumHubService.kt`)**:
  - In **Self-Hosted Website Mode**, serve the selected `.html` file or folder root directly at `http://<ip>:8080/` with proper relative asset resolution (`.css`, `.js`, `.svg`, `.png`, `.woff2`, sub-paths) and SPA fallback so downloaded GitHub web repos run seamlessly.
  - In **FTP & File Sharing Mode**, serve a redesigned minimalist Web Portal at `http://<ip>:8080/` featuring a responsive layout switcher for **Mobile**, **Desktop (split-pane table)**, and **Screen Mirroring (live MJPEG stream + side-by-side file browser)**, with working file downloads, multi-file uploads, and live status.

## 7. Separate-Tab File Preview Action Bar & Metadata Editor (`ViewOnlyFileViewer.kt`)
- **Logical Top Action Bar Order**:
  - Standardize the header bar when a file is previewed in a separate tab in the exact logical order:
    1. **File Type Icon & File Name** (with size & format subtitle)
    2. **Edit** button (shown for all editable text, code, markdown, CSV, and image files; routes directly to Markor Text Engine or Image Studio)
    3. **Metadata (View / Edit)** button (opens a comprehensive Metadata & Properties modal)
    4. **Delete** button (with confirmation dialog and automatic tab cleanup)
    5. **Share / Open Externally** button
    6. **Full-Screen Toggle** button
- **Interactive Metadata Modal**:
  - Allow viewing and editing file name & extension inline, viewing/editing EXIF metadata (for JPEG/PNG/WebP images: Artist, Copyright, Description, DateTime, GPS strip option) and file attributes/timestamps, plus one-tap copy for full path, MD5/SHA-256 checksums, and byte size.

## 8. PhotoEditor & Markor Desktop vs. Mobile Responsive Layouts (`NativeImageViewerEditor.kt` & `MarkorTextEngine.kt`)
- **PhotoEditor-Inspired Image Studio (`NativeImageViewerEditor.kt`)**:
  - **Wide / Desktop / Tablet Layout (`maxWidth >= 680.dp`)**: Keep the image canvas centered on the left/main area and dock all PhotoEditor controls, filter presets, adjustment sliders (Brightness, Contrast, Saturation, Warmth), crop/transform tools, and brush/text overlays in a dedicated right-hand control sidebar (`300.dp` width) so sliders never cover or shift the image.
  - **Mobile / Compact Layout**: Preserve the bottom toolbar and compact slider sheet layout inspired by the original PhotoEditor app.
- **Markor-Inspired Text & Markdown Studio (`MarkorTextEngine.kt`)**:
  - Optimize for desktop/tablet screens by supporting a side-by-side **Live Split Editor + WebView Preview** toggle on wide screens while keeping the stable Title Bar, Heading Bar, and bottom/top formatting bar structure intact on mobile.

## 9. Release APK Compression & Performance Optimization (`app/build.gradle.kts` & `proguard-rules.pro`)
- Enable R8 code shrinking (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) for release builds, limit bundled `resConfigs("en")` if appropriate or strip debug symbols, and configure optional ABI split properties (`-PabiTarget`) so architecture-specific APKs are compact and fast.
- Ensure comprehensive `proguard-rules.pro` keep rules for kotlinx.serialization, Apache Commons Compress, SMBJ, SSHJ, BouncyCastle, and Compose so release builds never crash.

## 10. Error-Free GitHub Actions Workflow (`.github/workflows/android-build.yml`)
- Create `.github/workflows/android-build.yml` modeled directly on the user's verified workflow structure (`JDK 21`, `Gradle 9.3.1`, safe keystore decoding/fallback, zero personal information exposed):
  - **Inputs**:
    - `build_target`: `all`, `aab`, `universal-apk`, `arm64-v8a-apk`, `armeabi-v7a-apk`, `x86_64-apk`, `x86-apk`
    - `build_variant`: `release` or `debug`
    - `create_release`: boolean toggle to optionally publish a GitHub Release
    - `release_tag`: optional custom version input; defaults to base version `0.0.1` and automatically increments to `0.0.(x+1)` based on `github.run_number` when left blank.

## 11. Mandatory In-App Help Documentation Update (`HelpDocumentationSection.kt`)
- Update `HelpDocumentationSection.kt` to thoroughly document all new and updated features (Full-Screen Mode, Window Margin fixes, View & Column Switcher, My Suite file creation & editing, FTP/HTTP & Self-Hosted Website Server Hub, Separate-Tab Preview & Metadata Editor, and Desktop/Mobile Editor layouts) and verify zero build or runtime issues via `compile_applet`.
