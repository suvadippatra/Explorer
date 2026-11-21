# My Suite & File Viewer Desktop Redesign Plan (Material 3 Tonal Flat)

A complete desktop-first UI rebuild of **My Suite**, its individual **Studio Homepages**, the **File Converter**, all **four active Studio Editors**, and every **File Viewer** using an **Ultra-Clean Tonal Flat Material 3** design language. This plan consolidates every visual, structural, and ergonomic defect identified across recent reviews into one unified, borderless, multi-pane desktop architecture.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All core visual and behavioral preferences below have been confirmed. Please review the consolidated plan and approve to begin implementation, or request revisions before any code is changed.

- **Visual Theme (Confirmed)**: **Ultra-Clean Tonal Flat Material 3** — zero card outline borders (`OutlinedCard` eliminated), zero wireframe cages; spatial hierarchy is communicated strictly through Material 3 `surfaceContainer` tonal steps so Light, Dark, Dynamic Color, and user-selected fonts work natively.
- **Full Redesign Scope (Confirmed)**: Includes **My Suite Home**, **all Studio Homepages**, **File Converter**, **all 4 active Studio Editors** (Markdown, Code, CSV Table, Photo), and **all File Viewers** (Image, PDF, Text/HTML, Archive, Audio, Video, Unsupported).
- **Studio Click Flow (Confirmed)**: Clicking a Studio tile in My Suite continues to open that studio's dedicated **Homepage**, now redesigned in the desktop tonal flat layout.
- **Single-Row Header Rule Across Editors & File Viewers (Confirmed)**: Eliminate stacked double/triple detail rows at the top of every Editing page and every File Viewer page. Every editor and viewer gets **one single 48dp Command Bar** on top, with metadata moved to a slim optional bottom status strip or an on-demand Info popover.
- **2-Second Floating Hover Preview (Confirmed)**: On My Suite Home and Studio Homepages, hovering the mouse pointer over a file name/tile for **2 seconds** displays a floating Material 3 tonal preview card over the file list and keeps it visible until the pointer moves away (with long-press fallback on touch-only screens).

---

## 1. Overview & Core Concept

### Consolidated Defect Audit (What Is Being Fixed)

Every defect raised across our review is mapped to a concrete architectural fix:

| Defect Category | Current Problem on Desktop / DeX | Redesign Resolution |
| :--- | :--- | :--- |
| **1. Border Fatigue** | Nearly every studio card, recent row, action box, converter step, and batch item uses 1dp outlined borders, creating nested 3–4 layer wireframe cages. | Pure **M3 Tonal Flat** surface nesting (`surfaceContainerLowest` → `surfaceContainerLow` → `surface` → `surfaceContainer` → `surfaceContainerHigh`). Zero decorative borders. |
| **2. Stretched Phone Layouts** | My Suite Home, Studio Homepages, and all 3 File Converter tabs use single-column `LazyColumn` stacks; sliders, radio rows, and buttons stretch 900+ dp wide; 5 studios in 3 columns leave an awkward empty hole. | True **Desktop Multi-Pane Canonical Layouts** (`>= 840dp`), balanced 5-tile horizontal studio dock, 2-pane converter workbenches, and desktop data tables/grids. |
| **3. Stacked Multi-Row Headers (Editors & Viewers)** | Opening any Editor or File Viewer stacks a Tab Bar + Details Row + Extra Details/Format Row + Search Row (eating 150–200dp vertically) and duplicates icons like Undo/Redo or file metadata. | Strict **Single 48dp Command Bar** rule across every editor and every file viewer. Extra details live in a quiet 24dp bottom status bar or an on-demand Info popover; Find/Replace floats as a compact corner overlay. |
| **4. Redundant Actions & Sub-Headings** | Studio Homepages repeat `New File` and `Open` in both the top bar and two giant hero cards filled with multi-line instructional text. | Minimalist 1–2 word headings only; zero descriptive sub-headings on cards/buttons; hero cards replaced by a clean left Template Rail + right visual Recent Grid. |
| **5. Wasted Space & Passive File Lists** | Clicking a file blindly opens it; wide monitors have no quick preview, no wrapping format grids (trapped in horizontal scrollers), and bottom phone tool strips in Photo Studio. | **2-second floating hover preview** over file entries, wrapping `FlowRow` format matrices in File Converter, and Left Rail + Center Canvas + Right Inspector in Photo Studio. |

### Target Experience & Key Value
- **Target Persona**: Power users running the app on Android Desktop / Samsung DeX, tablets with keyboard & mouse, and handheld phones (via automatic responsive folding).
- **Key Value**: Transforms My Suite and every file viewer/editor from stretched mobile screens into a calm, dense, borderless Material 3 desktop workstation where 85%+ of the screen belongs to actual content and tools sit where mouse and keyboard users expect them.

---

## 2. User Experience & Visual Design

### Visual Identity & Ultra-Clean Tonal Flat Material 3 System

- **Aesthetic Direction**: Calm, utilitarian, ChromeOS / Google Workspace–grade Material 3 Desktop minimalism. Crisp spatial rhythm, zero visual noise, strong figure-ground separation via tone rather than lines.
- **Tonal Surface Hierarchy (Strictly Material 3 Color Tokens)**:
  - **Window Backdrop / Side Rails**: `surfaceContainerLow`
  - **Primary Workspace Canvas**: `surface` (or `surfaceContainerLowest` for document/table sheets)
  - **Interactive Tiles / Studio Cards / Toolbar**: `surfaceContainer`
  - **Hovered / Focused Interactive Controls**: `surfaceContainerHigh` → `surfaceContainerHighest`
  - **Floating 2-Second Hover Preview Card**: `surfaceContainerHighest` with soft ambient shadow (`shadowElevation = 8.dp`) and zero outline stroke
  - **Active Selection / Primary CTA**: `primaryContainer` / `onPrimaryContainer` for tonal selection pills; `primary` / `onPrimary` reserved strictly for the single main action per screen
  - **Dividers**: Hairline `outlineVariant` (`0.5.dp`, 35% opacity) used *only* between major split panes—never around cards or list items.
- **Typography & Copy Discipline**:
  - Inherits the user's selected app font family (Normal, Bold, Premium, or custom installed font) and theme (Light / Dark / Auto) seamlessly.
  - Headings are 1–2 words (`My Suite`, `Converter`, `Presets`, `Batch`, `Templates`, `Recent`). Zero multi-line sub-headings or instructional paragraphs inside UI cards.
- **Mouse, Keyboard & Touch Ergonomics**:
  - Subtle tonal hover fill transitions (`120ms`) on every clickable tile, table row, and toolbar icon.
  - Minimum `48.dp` accessible hit targets on touch layouts; crisp `36–40.dp` compact density on desktop pointer layouts with generous padding.
  - Adaptive breakpoints: **Expanded Desktop (`>= 840.dp`)** → full multi-pane; **Medium Tablet (`600–839.dp`)** → compact 2-pane; **Compact Mobile (`< 600.dp`)** → clean single-pane with segmented switches / bottom sheets.

---

### Screen-by-Screen Desktop Blueprints

#### Screen 1: My Suite Home (Full-Width Tonal Hub + 2s Hover Preview)
- **Balanced 5-Tile Studio Strip**: All 5 studios sit cleanly in one full-width tonal row on desktop (2×3 compact grid on narrow windows)—no awkward empty hole.
- **2-Second Floating Hover Preview**:
  - Moving the cursor over any recent file starts a **2000ms debounce timer**.
  - At 2.0s, a borderless `surfaceContainerHighest` floating preview card fades in smoothly (`150ms`) anchored directly over/beside the hovered file name, showing:
    - **Images**: Crisp downsampled thumbnail + resolution (`W×H`).
    - **Markdown / Code / Text**: Monospace/formatted first ~8 lines snippet.
    - **CSV / TSV**: Compact 4×4 mini table preview.
    - **Footer Strip**: File size + compact parent folder path.
  - The preview remains displayed **until the mouse pointer moves off the file entry** (immediately cancels timer or dismisses overlay on pointer exit/movement). On touch-only screens, a long-press invokes the exact same preview popover.

#### Screen 2: Individual Studio Homepages (Markdown, Code, Table, Photo)
- **Studio-Specific Left Template Rail**:
  - *Markdown Studio*: `.md` Note, `todo.txt` List, `.org` Outline, `.txt` Plain Text.
  - *Code Studio*: `.kt`, `.py`, `.html`, `.js`, `.json`, `.css`.
  - *Table Studio*: Blank `.csv`, Budget `.csv`, `.tsv`.
  - *Photo Studio*: Open Existing Image, Blank `1080×1080`, Blank `1920×1080`, Blank `1080×1920`.
- **Inline Naming Bar**: Clicking a template lets you launch in 1 click or edit the filename inline inside the rail without stacked modal dialogs.
- **Right Visual Workspace**: Adaptive tonal grid/table of recent studio files with crisp thumbnails/snippets and the shared **2-second floating hover preview**.

#### Screen 3: File Converter (2-Pane Desktop Workbench Across All 3 Modes)
- **Presets Mode**: Compact 2×2 tonal preset tiles on the left; persistent live Before/After visual comparison canvas on the right.
- **Convert Mode (Single File VERT)**: Left source card with thumbnail + detected format badge; Right wrapping **`FlowRow` Format Matrix** (all target extensions visible at once—zero horizontal scrolling) + quality slider + primary Convert action.
- **Batch Mode**: Left dense tonal **Queue Table** (`File` | `Size` | `Target` | `Status` | `✕`); Right **Batch Inspector** (`Add Files`, `Set All To` format matrix, `Combine to 1 PDF`, `Convert All` with aggregate progress).

#### Screen 4: Unified Single-Row Command Bar for ALL 4 Studio Editors
- **Markdown & Text Studio**: One single 48dp Command Bar, collapsible bottom formatting dock, floating top-right Find & Replace card, 24dp bottom status strip.
- **Code Studio**: One single 48dp Command Bar (`☰`, File Name, HTML `Code | Split | Preview` segmented toggle when editing HTML, Find, Wrap toggle, Font `A-/A+`, Save, `⋮`), floating Find & Replace, 24dp bottom status strip.
- **CSV Table Studio**: One single 48dp Command Bar (`☰`, File Name, `Grid | Raw` toggle, `+ Row`, `+ Col`, inline compact `Filter` field, Save, `⋮`), inline cell bar, flat borderless grid, 24dp bottom status strip.
- **Photo Studio**: One single 48dp Command Bar (`☰`, File Name, Undo/Redo, Compare Original hold-button, Reset, Save/Export, `⋮`), Desktop (`>= 760.dp`) Left 64dp Vertical Tool Rail + Center Full-Bleed Canvas + Right 280dp Tonal Properties Inspector (folds cleanly to bottom bar on `< 760.dp`), 24dp bottom status strip.

#### Screen 5: Unified Single-Row Header for ALL File Viewers
- **Strict Single Top Bar Rule**: Outer File Viewer renders ONE clean 48dp tonal flat bar (`←`, File Name, contextual primary action like `Edit` or `Extract`, `Info` popover toggle, `Share`, `Fullscreen`, `⋮` overflow for Rename / Delete / Open External).
- **All inner redundant top bars/detail banners inside individual viewers are removed** (no double titles, no second row repeating file size/extension/resolution).
- Secondary file metadata moves cleanly into a **24dp bottom status strip** or an **on-demand tonal Info Popover**.
