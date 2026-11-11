# Explorer

Most Android file managers are built strictly for tapping on a phone screen. They work fine when you just want to move a photo or two, but the moment you plug into an external monitor, open Samsung DeX, or pair a keyboard and mouse on a tablet, they start getting in the way.

**Explorer** is built to bridge that gap. It feels completely natural with one thumb on a phone, and scales into a true multi-tab, desktop-class workspace when you have a larger screen, mouse, and keyboard. Beyond managing files, it packs a built-in workspace suite (**My Suite**) so you can resize exam photos to exact kilobyte limits, compress PDFs, edit Markdown or code, inspect spreadsheets, listen to music, and share files or your live screen over Wi-Fi—all locally on your device, with zero ads or cloud uploads.

## About & Licenses

**Explorer** is a desktop-grade Android file manager and offline workspace suite. It is licensed under the **GNU General Public License v3.0 (GPLv3)**.

This software is forked and developed from open-source foundations:
- **Base File Explorer Engine:** Forked from [Continuum Explorer](https://github.com/troikoss/Continuum-Explorer) by Troikoss (licensed under **GPLv3**).
- **Text, Markdown & Document Editors:** Adapted and inspired by [Markor](https://github.com/gsantner/markor) by Gregor Santner (licensed under **Apache License 2.0**).
- **Image Editing & Annotation Canvas:** Adapted and inspired by [PhotoEditor](https://github.com/burhanrashid52/PhotoEditor) by Burhanuddin Rashid (licensed under **MIT License**).
- **Exact-KB Squoosh Resizer & Dynamic VERT Converter:** Architectural concepts modeled after [Squoosh](https://github.com/GoogleChromeLabs/squoosh) (Apache-2.0) and [VERT](https://github.com/VERT-sh/VERT) (AGPL-3.0).

All original copyright notices, third-party licenses, and attributions are preserved in full compliance with GNU GPLv3 Section 5 and Apache 2.0 Section 4. See the `/LICENSE` file for the complete GPLv3 text.

## What You Can Do With It

### Desktop-Grade File Management (Mobile, Tablet & DeX)
- **Browser-Style Tabs & Separate Windows**: Keep multiple folders open in tabs at the top, drag files onto a tab to move or copy them, or pop workspaces out into standalone windows on DeX and tablets.
- **Full Mouse & Keyboard Support**: Right-click context menus, hover states, marquee drag-selection, middle-click to open in a new tab, and familiar desktop shortcuts (`Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `F2`, `Del`, `Alt+Enter`).
- **Flexible Views**: Switch per-folder between Details (with sortable columns), Grid (with live pinch/ctrl-scroll zoom from 60% to 300%), Content, and Gallery views.
- **Safe File Operations**: A built-in Recycle Bin per storage volume and a 50-step Undo/Redo history stack so accidental moves, renames, or deletions are easy to reverse.
- **Archives Without the Hassle**: Browse inside `.zip`, `.7z`, `.rar`, and `.tar` archives just like regular folders, or pack files into standard or AES-256 password-protected archives.
- **Storage Analyzer & USB OTG**: Visualize what is eating up your storage with an interactive treemap, and plug in flash drives or SD cards seamlessly.

### My Suite — 5 Built-In Local Studios
Instead of jumping between five different apps, **My Suite** gives you dedicated, minimalist workspaces that run 100% offline:
1. **File Converter (Squoosh Exact-KB Resizer & VERT Engine)**:
   - **Quick Presets for Forms & Exams**: Resize passport photos (`20–50 KB`, `200×230 px`) or signatures (`10–20 KB`, `140×60 px` with white-paper contrast cleanup) to hit strict online form upload limits, or dial in any exact target KB with a live Before/After preview.
   - **PDF Size Compressor**: Shrink heavy scanned PDFs down to email- or portal-friendly sizes (`100`, `150`, or `200 DPI`).
   - **Single & Batch Format Converter**: Pick any image, document, spreadsheet, audio/video clip, or archive and convert it to compatible formats—or combine multiple photos into a single multi-page PDF.
2. **Markor Text & Markdown Studio**: Write and preview `.md`, `.txt`, and `.todo.txt` notes with live formatting toolbars and split-screen preview on wider displays.
3. **Code Studio**: Edit source files (`.kt`, `.java`, `.py`, `.js`, `.ts`, `.html`, `.css`, `.json`, `.xml`, `.yaml`, `.sh`) with line numbers, syntax highlighting, and a quick symbol bar.
4. **CSV & Table Studio**: Open `.csv` and `.tsv` files in a clean spreadsheet grid with sticky headers, one-tap column sorting, row filtering, and cell editing.
5. **PhotoEditor Image Studio**: Crop, rotate, annotate with brush/text/shapes, apply filters, or start from a blank canvas.

### Media, Documents & Local Wi-Fi Sharing
- **Desktop-Class PDF Viewer**: Read PDFs in continuous scroll, single-page, or side-by-side 2-page desktop spread mode, complete with page jump, text search, night/sepia reading modes, thumbnail strip, and direct printing.
- **3-Mode Music Player**: Tap any audio track to play it in a bottom mini-bar while you keep browsing, shrink it into a draggable floating Picture-in-Picture card over your files, or expand it into a full-screen Spotify-style player with album-art color gradients and folder queue playback.
- **Wi-Fi FTP/HTTP Server & Live Screen Sharing**: Host selected folders—or even start a zero-file session—to access your device from any laptop or phone browser on your Wi-Fi network. Includes live upload progress, device battery/storage telemetry, optional MJPEG/WebSocket live screen sharing with adjustable FPS and quality, and remote browser access to the built-in Mini-Apps.
- **Native FTP/HTTP Client**: Connect to other FTP or HTTP servers on your network using a native file browser with inline previews, or flip to the built-in web browser with one tap.

## Building From Source

1. Clone this repository and open it in **Android Studio** (Ladybug or newer recommended).
2. Let Gradle sync the project dependencies (targets Android 8.0 API 26 through Android 15 API 35).
3. Run the `app` configuration on any Android phone, tablet, foldable, or desktop emulator, or build a debug APK with:
   ```bash
   ./gradlew assembleDebug
   ```
