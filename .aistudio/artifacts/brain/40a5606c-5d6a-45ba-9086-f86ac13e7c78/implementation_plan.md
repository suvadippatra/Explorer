# Continuum Explorer Architecture Audit, Markor Editor Overhaul & 60fps Performance Optimization

Refactor the application into modular UI and business-logic engines, rebuild the text and Markdown editor using the Markor reference architecture within the prescribed Title Bar and Heading hierarchy, repair all non-editable file previewers (Video, Audio, PDF, Archive, and Right-Hand Split Preview), and eliminate cold-start, tab-switching, and grid-scrolling bottlenecks.

## User Review & Critical Decisions

> [!IMPORTANT]
> All core architectural choices below incorporate your confirmed preferences from the clarification phase and your UI layout rules:

- **Markor Editor Feature Scope (Confirmed)**: Full integration of Markor's multi-format engine inside your **Title Bar (Tab Bar)** + **Heading (Action Bar)** structure, including:
  - Live syntax highlighting and format-specific action bars (Markdown, Todo.txt, CSV, Org-mode, AsciiDoc, WikiText, Key-Value/Code).
  - Local asset-backed WebView preview rendering with **KaTeX** (math equations), **Prism.js** (syntax highlighting with line numbers), **Mermaid.js** (diagrams), **AsciiDoctor**, and **Org-mode** bundles.
  - Interactive **Todo.txt** task filtering (by priority, `@context`, `+project`, completion toggle) and **CSV** spreadsheet grid editing.
- **File Previewer Overhaul (Confirmed)**: Repair UI and core playback/rendering functionality across all non-editable previewers:
  - **Video & Audio Players**: Responsive Jetpack Media3 ExoPlayer controllers, scrubbing timeline, playback speed, background lifecycle cleanup, and zero black-screen surface initialization.
  - **PDF Viewer**: Non-blocking background page rendering with `Mutex`-synchronized `PdfRenderer`, smooth pinch-to-zoom, page indicator, and text search/OCR integration.
  - **Archive Viewer**: Direct in-tab archive browsing (ZIP, TAR, 7Z, RAR) with one-tap extraction, archive breadcrumb navigation, and entry previewing.
  - **Right-Hand Split Preview Pane**: Responsive tablet/desktop side preview panel with asynchronous metadata loading, thumbnail/snippet rendering, and quick file actions.
- **Modularization of Monolithic UI & Logic (Confirmed)**: Split oversized combined UI/logic classes (such as the 1,300+ line `ViewOnlyFileViewer`, 1,680+ line `SettingsActivity`, and `FileExplorerState`) into dedicated state controllers/engines and lightweight, stateless Jetpack Compose UI views.
- **App Speed, Cold-Start & 60fps Scrolling**: Defer heavy storage volume queries and recent-file checks off the main thread during cold start, stabilize `LazyVerticalGrid` item keys and layout math for 60fps scrolling, and make tab switching instantaneous on single tap.
- **Help Page Synchronization**: Automatically update the in-app Help & Documentation section to reflect every architectural improvement, Markor editor capability, and previewer control.

---

## 1. Overview & Core Concept

### What It Does
1. **Prescribed Window & Heading Hierarchy**: Every screen and workspace strictly follows the two-tier top hierarchy—a fixed top **Title Bar (Tab Bar)** for workspace/document tabs, and a flush **Heading (Action Bar)** directly underneath with zero middle gaps containing navigation (Menu/Back), Page Title, Search toggle, and context actions.
2. **Markor-Powered Universal Document & Code Engine**: Replaces the buggy text editor with a modular Markor architecture supporting Markdown, Todo.txt, CSV, Org-mode, AsciiDoc, and source code—complete with smart list continuation, regex syntax highlighting, customizable format action bar, and offline local-asset WebView rendering (KaTeX, Prism, Mermaid).
3. **Rock-Solid File Previewers**: Dedicated, leak-free preview engines for Video, Audio, PDF, Archives, and Images—both in full-tab view and in the desktop/tablet Right-Hand Split Preview Pane.
4. **High-Velocity Explorer Core**: Non-blocking startup, zero-lag tab switching, and 60fps grid/list scrolling with clean separation between state controllers and Compose UI components.

---

## 2. User Experience & Visual Design

### Key User Flows & Layout Hierarchy

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ TITLE BAR (TAB BAR): [ 📁 Internal Storage x ] [ 📝 README.md x ] [ + ]      │
├──────────────────────────────────────────────────────────────────────────────┤
│ HEADING (ACTION BAR): [ ☰ / ← ]  Page / Document Title   [ 🔍 ] [ 👁️ ] [ ⋮ ] │
├────────────────────┬────────────────────────────────────┬────────────────────┤
│ NAVIGATION PANE    │ MAIN WORKSPACE / EDITOR / VIEWER   │ RIGHT PREVIEW PANE │
│ (Tablet/Desktop    │                                    │ (Tablet/Desktop)   │
│  or Modal Drawer)  │ • 60fps File Grid / Details List   │ • High-Res Preview │
│                    │ • Markor Editor (Edit/Preview/Split│ • Media Metadata   │
│ • Internal Storage │   with KaTeX, Prism & Mermaid)     │ • Quick Actions    │
│ • SD / USB Volumes │ • Todo.txt Interactive Task List   │   (Open, Edit,     │
│ • My Suite         │ • CSV Interactive Data Grid        │    Share, Move,    │
│ • Archive Mounts   │ • ExoPlayer Video / Audio Surface  │    Recycle Bin)    │
│ • Network / FTP    │ • Async Zoomable PDF Renderer      │                    │
├────────────────────┴────────────────────────────────────┴────────────────────┤
│ CONTEXT / FORMAT BAR: [ B ] [ I ] [ H1 ] [ - [ ] ] [ Code ] [ Table ] [ TOC ]│
└──────────────────────────────────────────────────────────────────────────────┘
```

### Visual Identity & Theme
- **Aesthetic Direction**: Utilitarian precision with Material Design 3 adaptive surfaces—clean, high-contrast workspaces with zero wasted vertical space or unwanted gaps between the Title Bar and Heading Bar.
- **Color Palette & Mood**: Dynamic M3 semantic tokens (`surface`, `surfaceContainerLow`, `surfaceContainerHigh`, `primaryContainer`, `onSurface`) with optional colorful toolbar tinting and synchronized dark/light CSS themes for the Markor WebView previewer.
- **Typography & Hierarchy**: Crisp sans-serif UI labels paired with bundled monospace/editorial fonts (`Source Code Pro`, `Roboto Slab`, `Open Sans`, `Liberation Mono`) for code and document editing.
- **Interactive Feedback & Motion**: Instantaneous single-tap tab activation, smooth spring-based drawer/panel reveals under 250ms, and non-blocking loading indicators for large files and archives.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Strict Separation of State Engines and Compose UI**
  - *Chosen Approach*: Extract media playback management, PDF bitmap caching, archive parsing, Markor text formatting, and settings state into dedicated controllers/engines outside the `@Composable` functions.
  - *Why*: Eliminates unnecessary recompositions on every frame or keystroke, fixes UI freezes/black screens, and makes individual modules small and maintainable.
  - *Alternatives Considered*: Keeping state inline inside `remember` blocks inside monolithic UI files—rejected because it caused sluggish UI updates and 1,300+ line files.

- **Decision 2: Local Asset-Backed WebView + Incremental Highlighting for Markor Editor**
  - *Chosen Approach*: Load `file:///android_asset/` resources (`katex`, `prism`, `mermaid`, `asciidoc`, `orgmode`) directly inside the preview WebView while using debounced span/annotated-string highlighting and smart list continuation in the editor.
  - *Why*: Delivers authentic Markor rendering for math formulas, syntax-highlighted code blocks, and Mermaid diagrams completely offline without blocking keystrokes.
  - *Alternatives Considered*: Naive regex HTML string replacement—rejected because it fails on complex Markdown tables, LaTeX math, Mermaid diagrams, and AsciiDoc/Org files.

- **Decision 3: Cold-Start & Grid Scroll Optimization**
  - *Chosen Approach*: Defer storage volume discovery, SAF root resolution, and recent file verification to background coroutines (`Dispatchers.IO`) after the first frame renders, and use stable `providerId` item keys with precomputed display models in `LazyVerticalGrid`.
  - *Why*: Guarantees instant cold start without a black screen and maintains 60fps scrolling even in directories with thousands of files.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        FileExplorer (Top-Level Shell)                        │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ Title Bar (TabBar) ──► Instant single-tap tab switching & state cache  │  │
│  ├────────────────────────────────────────────────────────────────────────┤  │
│  │ Flush Heading Bar  ──► Context-aware navigation, search & mode toggles │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
└───────────────┬──────────────────────────────────────────────┬───────────────┘
                │                                              │
                ▼                                              ▼
┌───────────────────────────────────────────┐  ┌───────────────────────────────┐
│        Markor Document & Code Suite       │  │   Dedicated File Previewers   │
├───────────────────────────────────────────┤  ├───────────────────────────────┤
│ • MarkorFormatRegistry                    │  │ • VideoPlayerEngine (Media3)  │
│   (MD, Todo.txt, CSV, Org, AsciiDoc, Code)│  │ • AudioPlayerEngine (Media3)  │
│ • MarkorSyntaxEngine & AutoFormatter      │  │ • PdfRenderController         │
│   (Smart lists, checkboxes, indent, undo) │  │   (Async LruCache + Zoom/OCR) │
│ • MarkorWebPreviewEngine                  │  │ • ArchiveBrowserEngine        │
│   (KaTeX, Prism, Mermaid, Org, AsciiDoc)  │  │   (ZIP/TAR/7Z browse & extract│
│ • TodoTxtTaskController & CsvGridEngine   │  │ • SplitPreviewPaneController  │
└───────────────────────────────────────────┘  └───────────────────────────────┘
```

### Interactive Component & State Mapping
1. **App Speed & Cold-Start Pipeline**:
   - Application launch initializes lightweight in-memory defaults immediately and renders the shell UI on Frame 1.
   - Directory listing, storage volume enumeration, and recent files validation execute asynchronously on `Dispatchers.IO`, streaming updates into state without blocking the main thread.
   - `LazyVerticalGrid` items bind to stable unique keys (`file.providerId`) and memoized formatting strings (file size, date) so scrolling does not trigger per-frame date formatting or allocations.
   - `TabBar` removes blocking scroll animations on tap and switches `selectedTabIndex` synchronously on single click.
2. **Markor Editor & Format Engine**:
   - **Format Detection**: Automatically routes `.md`/`.markdown`, `todo.txt`, `.csv`/`.tsv`, `.org`, `.adoc`, `.zim`/`.txt`, and source code extensions to their specialized Markor handler.
   - **Action Bar & Smart Editing**: Provides format-specific toolbars (headings, bold/italic/strikethrough, checkboxes, code blocks, links/images, tables, date/time insertion, indent/unindent, search & replace, jump to line). Pressing Enter automatically continues bulleted, numbered, and checkbox lists.
   - **Todo.txt Mode**: Parses tasks with priority badges `(A)`–`(Z)`, `@context` chips, `+project` tags, due dates, one-tap completion toggling (`x YYYY-MM-DD`), and filter dialogs.
   - **CSV Grid Mode**: Switches seamlessly between raw delimiter text and an interactive spreadsheet table with cell editing, row/column addition, and alignment.
   - **Live WebView Preview & Split Mode**: Renders Markdown, Org-mode, and AsciiDoc using local assets (`katex.min.js`, `prism.js`, `mermaid.min.js`, `asciidoctor.min.js`, `org-bundle.js`) with synchronized scroll position and Table of Contents (TOC) drawer.
3. **Non-Editable File Previewers (Video, Audio, PDF, Archive, Split Pane)**:
   - **Video & Audio**: Modularized into dedicated player views with lifecycle-aware `ExoPlayer` instances, custom Material 3 transport controls, seek bar, speed selector, and audio waveform/metadata cards.
   - **PDF**: Uses a thread-safe `PdfRenderer` engine with `LruCache<Int, Bitmap>`, rendering pages off the UI thread with smooth pan/zoom gestures and page jump controls.
   - **Archive**: Displays archive contents directly inside the explorer/viewer with folder hierarchy navigation, file size/compression details, and "Extract All" / "Extract Selected" actions.
   - **Right-Hand Split Preview Pane**: Modularized for tablet/desktop layouts to preview images, video/audio metadata, PDF first page, archive summary, and text snippets without slowing down directory navigation.
4. **Settings & Help Page Modularization**:
   - Splits `SettingsActivity` into modular category views and updates the built-in Help & Documentation guide to document all Markor editor tools, previewer controls, and performance enhancements.
