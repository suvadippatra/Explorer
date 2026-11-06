# VERT-Inspired Native File Converter Mini-App ("File Converter" in My Suite)

A local-first, privacy-respecting native Android **File Converter** Mini-App inside **My Suite**, modeled after the `VERT-sh/VERT` user experience and engineered in 100% native Kotlin and Jetpack Compose for zero cloud limits, instant responsiveness on Android 8 through Android 15, and zero APK bloat.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All four core product decisions have been confirmed from your answers:

- **Confirmed Decision 1 — All 4 Conversion Categories Supported Locally**:
  1. **Images**: `PNG`, `JPG/JPEG`, `WEBP`, `BMP`, `PDF` (single or multi-image to PDF), `ICO` (multi-resolution Windows/Web icon header), and `SVG` rasterization.
  2. **Documents, Markup & Data Tables**: `MD` (Markdown), `HTML` (styled standalone document), `PDF` (rendered multi-page PDF), `TXT`, `JSON`, `CSV`, `TSV`, `XML`, `YAML`, and `DOCX/XLSX` extraction/conversion.
  3. **Audio & Video**: `MP4`, `WEBM`, `MKV`, `MP3`/`M4A`/`AAC` (audio extraction from video or audio transcode via `MediaExtractor`/`MediaMuxer`/`MediaCodec`), `WAV` (uncompressed PCM RIFF WAV synthesis), and video frame/GIF thumbnail extraction.
  4. **Archives & Packages**: Convert and repack between `ZIP` (including password-protected AES ZIP via Zip4j), `TAR`, `TAR.GZ / TGZ`, and `GZ`, plus one-tap archive extraction.
- **Confirmed Decision 2 — Special VERT-Style Unified Landing Page**:
  - Features a single cohesive screen with **Category Filter Pills** (*All, Images, Documents & Data, Audio & Video, Archives*), a prominent **Interactive Dropzone & Multi-File Picker**, a **Global "Convert All To" Bar**, and a **Live Batch Queue** with adaptive 1-column (Mobile) and 2-column (Tablet/DeX) workspace layouts.
- **Confirmed Decision 3 — Global "Convert All To" + Per-File Output & Tuning Controls**:
  - Users can apply a global target format to all compatible queued files at once OR customize each file's target format and tuning parameters individually (Image quality %, resize dimensions, strip EXIF, PDF page size, Audio bitrate/sample rate, Video audio-track strip/trim, Archive compression level & optional ZIP password).
- **Confirmed Decision 4 — Output Destination & My Suite Recent Tracking**:
  - Users can toggle between saving in a dedicated `Converted` subfolder or alongside the original file (or pick a custom folder), and every converted output file is automatically recorded in `SuiteRecentDatabase` so it immediately appears in **My Suite -> Recent Edited & Created Files**.

---

## 1. Overview & Core Concept

- **What It Does**: Brings the signature workflow of **VERT (`VERT-sh/VERT`)** — local-first, no file-size limits, batch-capable multi-format conversion — natively into Continuum Explorer's **My Suite** and Explorer context menus.
- **Target Audience / Persona**: Power users on Mobile, Tablet, and Samsung DeX/Desktop who need to transcode images, convert Markdown/CSV/JSON/XML/HTML/PDF documents, extract audio tracks from videos into WAV/M4A/MP3, or repackage archives without uploading sensitive files to external servers.
- **Key Value**:
  - **100% On-Device Execution**: Uses Android's hardware-accelerated media, graphics, PDF, and archive engines on background coroutines (`Dispatchers.Default` / `Dispatchers.IO`).
  - **Seamless Explorer Integration**: Launchable both from its **My Suite** card and directly from any file's three-dot context menu in Explorer (*"Convert File..."*), respecting the user's configured Workspace Windowing Mode (Separate DeX Window, Animated In-App Tab with Origin-Tab return, or Floating Resizable Panel).

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Launching File Converter**:
   - **From My Suite**: Tap the **File Converter** Mini-App card in *My Suite* to open the special VERT-inspired landing page directly (no extra middle screen).
   - **From File Explorer Context Menu**: Tap *"Convert with File Converter"* in any file's three-dot menu to open the File Converter pre-loaded with the selected file(s).
2. **Adding Files to the Batch Queue**:
   - Tap the **VERT Interactive Dropzone** (*"Drop or Select Files to Convert — Local-First • Unlimited Size"*) to pick one or multiple files via the in-app multi-file selector or quick-add recent files.
   - Each queued item is automatically classified into **Image**, **Document/Data**, **Audio/Video**, or **Archive**, displaying its source format badge, file size, and compatible target format dropdown.
3. **Global vs. Per-File Format & Tuning**:
   - When multiple files of the same category are queued, the **Global "Convert All To" Bar** lets the user switch all items to a target format (e.g., `WEBP`, `PDF`, `CSV`, `WAV`, `ZIP`) in one tap.
   - Tapping the **Tune (Sliders)** icon on any queued item (or the global settings drawer) expands format-specific tuning controls:
     - *Images*: Quality slider (`10%–100%`), Max Width/Height scale (`Original`, `1920px`, `1280px`, `512px`, `Custom`), Background fill color for transparent PNG -> JPG, and Strip EXIF metadata toggle.
     - *Documents & Data*: Page size (`A4`, `Letter`, `Fit Content`) for PDF output, Pretty-Print indentation for JSON/XML/YAML, Delimiter selector (`,` / `;` / `\t`) for CSV/TSV, and Dark/Light CSS theme for HTML output.
     - *Audio & Video*: Sample rate (`44.1 kHz`, `48 kHz`), Channels (`Stereo`, `Mono`), Video-to-Audio demuxing (`M4A/AAC`, `WAV`), or container remuxing (`MP4`).
     - *Archives*: Compression level (`Fastest`, `Normal`, `Maximum`) and optional AES-256 password protection for `.zip` output.
4. **Live Conversion Progress & Instant Actions**:
   - Tapping **"Convert All (N)"** runs conversions concurrently with per-item progress bars, speed/elapsed badges, and status indicators (*Queued*, *Converting %*, *Completed*, *Failed*).
   - Completed items expose one-tap **Preview**, **Edit** (if editable), **Share**, and **Open Folder** buttons, and are automatically logged in `SuiteRecentDatabase`.

### Visual Identity & Theme
- **Aesthetic Direction**: VERT-inspired utilitarian precision combined with Material 3 tonal surfaces — crisp monospace format badges (`PNG → WEBP`, `MD → PDF`, `MP4 → WAV`), glowing emerald status indicators for local-first privacy (`100% On-Device • Zero Cloud Upload`), and responsive split panes.
- **Responsive Layout Optimization**:
  - **Mobile (< 600dp)**: Vertical scrollable workspace with compact dropzone header, horizontal category filter chips, sticky bottom *Convert All* action bar, and expandable per-file tuning drawers.
  - **Tablet & DeX (>= 600dp)**: Canonical two-pane desktop workbench — Left pane houses the VERT Dropzone, Category Explorer & Output Destination settings; Right pane houses the live Batch Queue table, Global "Convert All To" bar, and side-by-side tuning inspector.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Pure Native Kotlin Conversion Engines (Zero External Wasm Bloat)**
  - *Chosen Approach*: Implement dedicated, modular Kotlin engines for Images (`Bitmap`/`Canvas`/`PdfDocument`/ICO binary encoder), Documents & Data (`PdfRenderer`/`PdfDocument`/Markdown-to-HTML-to-PDF/JSON-CSV-XML-YAML AST transformers), Audio/Video (`MediaExtractor`/`MediaMuxer`/`MediaCodec` PCM-to-WAV & AAC/MP4 pipelines), and Archives (`Zip4j` + TAR/GZIP stream engines).
  - *Why*: Keeps APK size compact, avoids bundling 60MB+ of FFmpeg/Pandoc binaries, guarantees compatibility across ARM64, ARMv7, x86_64, and x86 on Android 8–15, and runs at native hardware speed.
- **Decision 2 — Architecture Rules & Origin-Tab Memory Compliance**
  - *Chosen Approach*: Register `file_converter` in `MiniAppRegistry.kt` and route it through `WorkspaceWindowController` and `FileExplorerState` with `originTabIndex` tracking.
  - *Why*: Ensures opening File Converter from *My Suite* or Explorer respects the user's chosen Windowing Mode (Mode 1 DeX Window / Mode 2 Animated Tab / Mode 3 Floating Panel) and pressing Back cleanly returns to the originating tab without memory leaks.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   My Suite / Explorer Context Menu                         │
│        (MiniAppRegistry: "file_converter" • WorkspaceWindowController)     │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   FileConverterScreen (Special Landing Page)               │
│  ┌──────────────────────────────────┐  ┌────────────────────────────────┐  │
│  │ Left / Top Pane:                 │  │ Right / Bottom Pane:           │  │
│  │ • Local-First Privacy Hero Badge │  │ • Global "Convert All To" Bar  │  │
│  │ • Category Filter Tabs           │  │ • Interactive Batch Queue List │  │
│  │ • VERT Multi-File Dropzone       │  │ • Per-File Format & Tuning UI  │  │
│  │ • Output Folder Mode Selector    │  │ • Live Progress & Result Bar   │  │
│  └─────────────────┬────────────────┘  └───────────────┬────────────────┘  │
└────────────────────┼───────────────────────────────────┼───────────────────┘
                     │                                   │
                     └─────────────────┬─────────────────┘
                                       ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   FileConverterEngine (Coroutine Dispatcher)               │
├──────────────────┬───────────────────┬──────────────────┬──────────────────┤
│ ImageConverter   │ DocumentConverter │ MediaConverter   │ ArchiveConverter │
│ • PNG/JPG/WEBP   │ • MD <-> HTML/PDF │ • MP4/MKV/WEBM   │ • ZIP (AES/Std)  │
│ • BMP / ICO      │ • CSV <-> JSON    │   -> WAV / M4A   │ • TAR / GZ / TGZ │
│ • SVG -> Raster  │ • XML <-> YAML    │ • Audio Transcode│ • Repack &       │
│ • Images -> PDF  │ • PDF -> Images   │ • Video Frame/GIF│   Extract        │
└──────────────────┴─────────┬─────────┴──────────────────┴──────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────────────────┐
│             Output Storage & SuiteRecentDatabase Integration               │
│  • Saves to "/Converted/" subfolder or original directory                  │
│  • Logs every converted file into SuiteRecentDatabase (My Suite Recent)    │
└────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping
- **Format Compatibility Matrix (`ConversionFormatMatrix`)**: Maps every input extension to its valid target extensions and default tuning parameters so users can never select an invalid conversion pair.
- **Batch Queue State (`ConverterQueueItem`)**: Tracks `id`, `sourceFile`, `category`, `sourceExtension`, `targetExtension`, `tuningConfig`, `status` (`IDLE`, `CONVERTING`, `SUCCESS`, `ERROR`), `progress` (`0f..1f`), `outputFile`, and `errorMessage`.
- **Mandatory Help Page Synchronization**: Updates `HelpDocumentationSection.kt` to document the new **File Converter** Mini-App, supported conversion matrices, tuning parameters, and output rules.
