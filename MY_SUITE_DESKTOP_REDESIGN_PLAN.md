# My Suite & File Viewer Desktop Redesign Plan (Material 3 Tonal Flat)

A complete desktop-first UI rebuild of **My Suite**, its individual **Studio Homepages**, the **File Converter**, all **four active Studio Editors**, and every **File Viewer** using an **Ultra-Clean Tonal Flat Material 3** design language. This plan consolidates every visual, structural, and ergonomic defect identified across recent reviews into one unified, borderless, multi-pane desktop architecture.

---

## Confirmed Core Decisions

- **Visual Theme**: **Ultra-Clean Tonal Flat Material 3** — zero card outline borders (`OutlinedCard` eliminated), zero wireframe cages; spatial hierarchy is communicated strictly through Material 3 `surfaceContainer` tonal steps (`surfaceContainerLowest` → `surfaceContainerLow` → `surface` → `surfaceContainer` → `surfaceContainerHigh` → `surfaceContainerHighest`) so Light, Dark, Dynamic Color, and user-selected fonts work natively.
- **Full Redesign Scope**: Includes **My Suite Home**, **all Studio Homepages**, **File Converter**, **all 4 active Studio Editors** (Markdown, Code, CSV Table, Photo), and **all File Viewers** (Image, PDF, Text/HTML, Archive, Audio, Video, Unsupported).
- **Studio Click Flow**: Clicking a Studio tile in My Suite opens that studio's dedicated **Homepage**, redesigned as a 2-pane Desktop Start Workspace (Left Template Rail + Right Visual Recent Grid/Table).
- **Single-Row Header Rule Across Editors & File Viewers**: Eliminates stacked double/triple detail rows at the top of every Editing page and every File Viewer page. Every editor and viewer gets **one single 48dp Command Bar** on top, with metadata moved to a slim 24dp bottom status strip or an on-demand Info popover, and Find/Replace rendered as a floating top-right tonal overlay card.
- **2-Second Floating Hover Preview**: On My Suite Home and Studio Homepages, hovering the mouse pointer over a file name/tile for **2 seconds** displays a floating Material 3 tonal preview card over the file list and keeps it visible until the pointer moves away (with long-press fallback on touch-only screens).
