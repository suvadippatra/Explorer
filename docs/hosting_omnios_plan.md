# Hosting Hub & OmniOS Casting Architecture

Unify server operations under a renamed **Hosting** hub featuring an Ultra-Clean Tonal Flat Material 3 desktop workspace with three mutually exclusive modes (**FTP Hosting**, **HTTP Hosting**, and **Casting** powered by OmniOS). The design integrates all My Suite mini-apps into OmniOS under strict Host Admin governance, delivers five casting topologies plus a DeX-style App-to-App Receiver Mode with two-way QR pairing and mandatory host approval, and seamlessly bridges SyncPlay and host-approved file downloads.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The decisions below reflect your confirmed choices and establish the core constraints for execution. Please review and approve before implementation begins.

- **Confirmed — Mutually Exclusive Hosting Modes**: Only **one** hosting mode runs at a time (**FTP Hosting**, **HTTP Hosting**, or **Casting / OmniOS**). Starting any mode automatically shuts down the previously active mode to guarantee clean port ownership and zero state conflicts.
- **Confirmed — Clean Branding**: All UI headings, sidebar items, notifications, and desktop chrome use **Hosting** and **OmniOS** (no "v2" suffix anywhere in user-facing copy).
- **Confirmed — Visual Language**: The 3-tab Hosting page adopts the **Ultra-Clean Tonal Flat Material 3** desktop layout (single `48dp` command bar, segmented mode selector, borderless `surfaceContainer` hierarchy, responsive 2-pane desktop splits folding cleanly on mobile, minimalist headings with zero verbose sub-headings).
- **Confirmed — SyncPlay & File Access in Casting**: SyncPlay triggered from File Manager opens immediately via **FTP Hosting**, and can also be launched/controlled from either **FTP Hosting** or **Casting** using the exact same frame-accurate engine. Inside OmniOS, active SyncPlay media opens in a **full-screen window that can be minimized but cannot be closed** by receivers. Casting also shares host-selected files/folders so receivers can browse and download them directly inside OmniOS.
- **Confirmed — App-to-App Discovery, Two-Way QR & Host Approval**: Casting displays the device IP, auto-discovers peers over LAN via NSD/mDNS alongside manual IP entry, supports **two-way QR pairing** (either Host or Receiver can show or scan a QR popup), and enforces a **Host Approval Gate** before initializing any remote session. Built-in **Receiver Mode** renders OmniOS edge-to-edge across the receiver's available display area (fullscreen or desktop window) with full touch/pointer input.
- **Default — QR Scanner Engine**: On-device camera QR scanning uses Android's zero-cloud CameraX/ML Kit barcode pipeline (already aligned with existing ML Kit setup in the project) with manual IP/code fallback when camera permission is declined.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms the existing server panel into a cohesive **Hosting** control center where users cleanly switch between sharing files via FTP, serving a static website via HTTP, or projecting an interactive **OmniOS** desktop across browsers, wired displays, wireless displays, the built-in screen, or another device running this app on the same Wi-Fi network.
- **Target Audience / Context**: Power users on Android phones, tablets, and Android Desktop / DeX setups who want a single sideloaded hub to transfer files, host local web projects, watch synchronized media with friends, or turn any screen or secondary Android device into a full desktop workspace driven by their primary device.
- **Key Value**:
  - Eliminates conflicting switches and port collisions via strict single-active-mode orchestration.
  - Protects host authority: all OmniOS window state, app availability, touch permissions, and file sandboxing live strictly on the host device.
  - Extends My Suite mini-apps (Markdown Studio, Code Studio, Table Studio, Photo Studio, File Converter, File Manager) directly onto remote screens without installing anything on client devices.

---

## 2. User Experience & Visual Design

### Navigation & Renaming
- Sidebar item, page command bar title, Settings quick-link, and active tab label are renamed from `FTP/HTTP Server` / `Server Hub` to **Hosting**.

### Key User Flows
1. **Switching Hosting Modes (Exclusive Toggle)**:
   - The user opens **Hosting** and selects one of three tabs in the top `48dp` segmented bar: **FTP Hosting** | **HTTP Hosting** | **Casting**.
   - Tapping the mode power switch starts that mode and automatically halts any other active hosting mode, updating the subtle status dot in the sidebar and command bar.
2. **FTP Hosting Flow**:
   - Configure port, credentials/anonymous toggle, shared scope (selected items vs. full storage), uploads folder, and launch/control **SyncPlay Party** with mandatory PIN and live peer status.
3. **HTTP Hosting Flow**:
   - Pick an unzipped website folder or single HTML file, configure optional access PIN, toggle server on, copy/QR the URL, and inspect via the live side-by-side preview pane on desktop.
4. **Casting (Host Role) Flow**:
   - Displays the live device IP, active OmniOS status, and a clean 5-mode selector:
     - **Web Cast**: Starts OmniOS HTTP/WebSocket host and offers copyable URL + QR popup.
     - **Wired Cast**: Detects external USB-C DisplayPort / HDMI displays for direct secondary-screen projection and offers USB/ADB reverse quick-setup.
     - **Wireless Cast**: Launches system Miracast / Wi-Fi Display pairing and configures smart fullscreen suppression.
     - **Built-In Screen**: Immediately launches an interactive fullscreen OmniOS desktop right on the current device connected to the local host engine.
     - **Same App (LAN)**: Advertises host via NSD, listens for incoming receiver join requests, allows scanning a Receiver's QR code to push an invite, or showing Host QR for a receiver to scan.
   - **Host Approval Banner/Modal**: Whenever a remote device requests to connect, the host sees `Device Name • IP` with **Approve** and **Deny** actions. Unapproved clients remain locked on a waiting barrier and receive zero state or file access.
   - **Host Admin Console**: Right inspector pane (desktop) / collapsible section (mobile) controlling:
     - **App Whitelist**: Toggle each My Suite mini-app (`File Manager`, `Markdown Studio`, `Code Studio`, `Table Studio`, `Photo Studio`, `File Converter`) and built-in OmniOS apps (`Video`, `Music`, `PDF`, `SyncPlay`, `Terminal`, `System Monitor`, `Settings`).
     - **Shared Files Access**: Pick specific files or folders receivers are allowed to browse and download inside OmniOS.
     - **Governance & Calibration**: Touch Input Gate toggle, Audio Output Route (`Receiver` | `Host` | `Both`), Software Display Brightness slider (`15%–100%`), Desktop Style (`DeX` | `macOS` | `Windows` | `Linux`), Dark/Light toggle, and Settings visibility controls.
   - **SyncPlay in Casting**: Picking a media file starts SyncPlay and pushes a locked fullscreen SyncPlay window into OmniOS (receivers can minimize it to the dock/taskbar to multitask, but cannot close it until the host ends the session).
5. **Casting (Receiver Role) Flow**:
   - Inside the **Casting** tab, users can switch the role pill from **Host** to **Receiver**.
   - Receiver Mode shows the device's own IP, offers **Show My QR** (so a Host can scan it) or **Scan Host QR**, lists auto-discovered LAN OmniOS hosts via NSD, and provides a manual IP input.
   - After tapping a host or scanning a QR, Receiver enters a clean `Waiting for Host Approval` state.
   - Once approved by the Host, Receiver opens an immersive **OmniOS Display Surface** that fills 100% of the available screen/window width and height (just like Samsung DeX), forwarding responsive touch, mouse, and keyboard interactions back to the Host.

### Visual Identity & Theme
- **Aesthetic Direction**: Ultra-Clean Tonal Flat Material 3 — calm, spacious, borderless desktop utility matching the redesigned My Suite workspace.
- **Color Palette & Tokens**: Pure `MaterialTheme.colorScheme` semantic tokens (`surface`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `primary`, `secondaryContainer`, `errorContainer`) so Day, Night, Auto, and Dynamic themes adapt seamlessly with zero hardcoded colors or heavy card outlines.
- **Typography & Density**: Clean single-line titles and compact `11sp–13sp` monospace badges for IPs, ports, and metrics; zero decorative icons before titles and zero multi-line explanatory sub-headings under buttons (detailed descriptions belong in the Settings Help page).
- **Layout Structure**:
  - Single `48dp` `SingleRowCommandBar` (`☰` Sidebar Toggle, `Hosting` title, `FTP Hosting | HTTP Hosting | Casting` M3 `SingleChoiceSegmentedButtonRow`, QR/Logs/Fullscreen quick actions).
  - Desktop (`>= 740dp`): Balanced 2-pane tonal workbench (Left primary controls & mode cards; Right live preview / Admin Governance / Peer Approval inspector separated by a `1dp` hairline divider).
  - Mobile (`< 740dp`): Clean single-column vertical flow with sticky segmented switcher and compact bottom-sheet modals for QR display/scanning.
- **Interactive Feedback & Motion**: Instant ripple feedback on all `>= 48dp` targets, smooth `AnimatedVisibility` reveals for approval banners and status strips, clear empty states for NSD scanning, and explicit disconnect/waiting barriers when a host stops or denies access.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Single-Active-Mode State Machine**
  - *Chosen Approach*: Upgrade the central server coordinator to track an explicit `ActiveHostingMode` (`NONE`, `FTP`, `HTTP`, `CASTING`). Starting one mode gracefully stops any other running mode before binding sockets.
  - *Why*: Eliminates port conflicts on `:8080`, guarantees predictable battery/thermal behavior, and removes confusing half-running states (such as SyncPlay active while FTP reports off).
  - *Alternatives Considered*: Running all three servers concurrently on separate ports (rejected per user preference for strict separation).

- **Decision 2 — Native WebClient SDUI + My Suite Mini-App Bridge inside OmniOS**
  - *Chosen Approach*: Bundle the extracted OmniOS SDUI client assets directly in the app and extend the host State Engine and SDUI client renderer with dedicated app windows for our My Suite mini-apps (`Markdown Studio`, `Code Studio`, `Table Studio`, `Photo Studio`, `File Converter`, `File Manager`) backed by host REST/WebSocket APIs executing directly on the Android device.
  - *Why*: All heavy file editing, format conversion, and storage sandboxing execute authoritatively on the host device while any browser, wired/wireless screen, or App-to-App receiver renders a crisp, responsive desktop UI with zero extra installs.
  - *Alternatives Considered*: Pure pixel-stream-only mirroring (rejected because SDUI gives native sharpness at any receiver resolution, instant DeX-style scaling across arbitrary window widths, and granular admin app/file control).

- **Decision 3 — Two-Way QR Pairing + Mandatory Host Approval Gate**
  - *Chosen Approach*: Encode compact JSON pairing payloads (`role`, `ip`, `port`, `deviceName`, `nonce`) in QR codes. Whether a Receiver scans the Host's QR, the Host scans a Receiver's QR, or a Receiver selects a Host from NSD discovery, the Host creates a `PendingConnectionRequest` that requires explicit Host approval before issuing a session token.
  - *Why*: Prevents unauthorized users on shared or public Wi-Fi networks from silently viewing the host's desktop or downloading shared files.

- **Decision 4 — Built-In & App-to-App Fullscreen OmniOS Surface**
  - *Chosen Approach*: Render the Receiver / Built-In Screen OmniOS surface inside a dedicated full-bleed Compose/WebView container with hardware acceleration, viewport-fitting responsive scaling, software luminance calibration, suppressed redundant fullscreen chrome, and low-latency binary touch telemetry forwarding.
  - *Why*: Immediately fills 100% of the receiver's screen or freeform desktop window (DeX behavior) with working touch, trackpad, and keyboard input across phones, tablets, and desktop monitors.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### System Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     HOSTING HUB UI (Ultra-Clean Tonal M3)                    │
│  [ SingleRowCommandBar: ☰ | Hosting | FTP Hosting | HTTP Hosting | Casting ] │
└───────────────┬───────────────────────┬──────────────────────────┬───────────┘
                │                       │                          │
                ▼                       ▼                          ▼
     ┌────────────────────┐  ┌────────────────────┐   ┌────────────────────────┐
     │  Tab 1: FTP Hosting│  │ Tab 2: HTTP Hosting│   │   Tab 3: Casting       │
     │ • Apache FTP :2121 │  │ • Static Site :8080│   │ [Role: Host | Receiver]│
     │ • Shared Scope     │  │ • Folder / HTML    │   │ • 5 Cast Modes         │
     │ • Uploads Folder   │  │ • Optional PIN     │   │ • Admin Governance     │
     │ • SyncPlay Party   │  │ • Live Preview     │   │ • Two-Way QR + NSD     │
     └─────────┬──────────┘  └─────────┬──────────┘   │ • Host Approval Queue  │
               │                       │              │ • Shared Files + Sync  │
               └───────────────────────┼──────────────┴───────────┬────────────┘
                                       ▼                          │
              ┌────────────────────────────────────────────┐      │
              │    EXCLUSIVE SERVER MANAGER (Mutex Lock)   │      │
              │  ActiveHostingMode: NONE | FTP | HTTP |    │      │
              │                     CASTING                │      │
              │  + ContinuumHubService (Foreground Guard,  │      │
              │    WakeLock, WifiLock, Thermal Pacing)     │      │
              └────────────────────────┬───────────────────┘      │
                                       │                          │
        ┌──────────────────────────────┴──────────────┐           │
        ▼                                             ▼           ▼
┌──────────────────────────────┐         ┌─────────────────────────────────────┐
│   OMNIOS HOST ENGINE (Ktor)  │         │  DISCOVERY, PAIRING & PROJECTION    │
│ • Static SDUI Assets Serving │         │ • NSD Advertise & Browse (_omnios)  │
│ • /ws/sdui (State Deltas +   │         │ • Two-Way QR Generator & Scanner    │
│   10-Byte Binary Touch)      │         │ • Pending Approval Session Store    │
│ • /api/admin/* (Governance)  │         │ • DisplayManager (Wired / Wireless) │
│ • /api/files/* (Sandbox DL)  │         │ • AudioRoutingManager (Tri-Route)   │
│ • /api/suite/* (Mini-Apps:   │         │ • Built-In Screen & Receiver Mode   │
│   Markdown/Code/CSV/Photo/   │         │   (Full-Bleed Interactive Desktop)  │
│   Converter Bridges)         │         └─────────────────────────────────────┘
│ • SyncPlayManager (Locked    │
│   Minimizable Fullscreen Win)│
└──────────────────────────────┘
```

### Data Model & State
- **Exclusive Hosting State (`ActiveHostingMode`)**: `NONE`, `FTP`, `HTTP`, `CASTING`. Enforced inside a coroutine `Mutex` so transitioning modes cleanly drains and stops the previous engine before binding the new one.
- **OmniOS Admin Configuration (`OmniOSAdminConfig`)**: Persisted locally via SharedPreferences/Settings:
  - `allowedApps`: Set of enabled app IDs spanning My Suite (`files`, `markdown_studio`, `code_studio`, `table_studio`, `photo_studio`, `file_converter`) and OmniOS core apps (`video`, `music`, `pdf`, `syncplay`, `terminal`, `sysmon`, `settings`).
  - `touchInputEnabled`: Boolean gate enforced server-side on incoming binary touch frames.
  - `audioRoute`: `receiver` | `host` | `both`.
  - `brightness`: Integer `15..100` applied via real-time software luminance filter.
  - `themeStyle`: `dex` | `macos` | `windows` | `linux`, plus `darkMode` boolean.
  - `settingsVisibility`: Map of client settings tabs (`appearance`, `animation`, `sound`, `storage`, `host`).
  - `sharedCastPaths`: List of host-approved files/folders exposed for read/download inside OmniOS File Manager.
- **Pairing & Approval Store**:
  - `DiscoveredHost`: `serviceName`, `hostIp`, `port`, `deviceName`.
  - `PendingPairingRequest`: `requestId`, `clientName`, `clientIp`, `direction` (`INCOMING_JOIN` | `QR_INVITE`), `status` (`PENDING` | `APPROVED` | `DENIED`), `sessionToken`.
- **OmniOS Authoritative Window State**:
  - Tracks open windows (`id`, `appId`, `title`, `bounds`, `zIndex`, `minimized`, `maximized`, `closable`).
  - When SyncPlay starts during Casting, the host injects a maximized SyncPlay window with `closable = false, minimizable = true`, synchronized to `SyncPlayManager` timestamps and frames.

### Interactive Component & State Mapping
- **Mode Switcher (`FTP Hosting | HTTP Hosting | Casting`)**: Updates active tab immediately; starting a server mode runs `ServerManager.activateExclusiveMode(...)`, automatically stopping any other mode and updating live status badges across the sidebar and header.
- **Admin App Whitelist Toggles**: Mutating any toggle persists `OmniOSAdminConfig`, emits an `ADMIN_CONFIG` WebSocket push to all connected OmniOS sessions, immediately updating client docks/launchers and closing any newly revoked app windows.
- **Shared Files Picker (Casting)**: Reuses the in-app multi-select file/folder picker to populate `sharedCastPaths`, immediately refreshing the live file listing and download links inside OmniOS File Manager.
- **Two-Way QR & NSD Pairing**:
  - *Host shows QR* -> Receiver scans QR -> Receiver calls `/api/pair/request` -> Host sees Approval Prompt -> Host taps **Approve** -> Receiver receives `sessionToken` and opens full-bleed OmniOS Receiver view.
  - *Receiver shows QR* -> Host scans Receiver's QR (containing Receiver IP + listener port) -> Host sends pre-authorized invite directly to Receiver -> Receiver opens full-bleed OmniOS Receiver view.
  - *NSD Discovery* -> Receiver taps discovered Host card -> Triggers `/api/pair/request` -> Host Approval Prompt -> Session launches.
- **Built-In Screen Cast**: Starts Casting mode on loopback/LAN, auto-authorizes the local device session, and opens the full-bleed OmniOS Desktop view immediately on the current screen.
- **Error & Warning Resolution**: Resolve deprecated API usages, unused variables, and compiler warnings across modified Hosting, OmniOS, and Navigation components, verified with a clean Android build.

---

## 5. Phase-by-Phase Execution Plan

> [!IMPORTANT]
> **Execution Rule**: To guarantee zero truncation, fast focused edits, and a verified working build after every step, implementation is divided into **5 sequential phases**. When you click **Proceed**, **ONLY Phase 1** will be executed and compile-verified. Each subsequent phase will run only when you instruct to proceed to the next phase. A permanent copy of this plan is safely stored at `docs/hosting_omnios_plan.md`.

### Phase 1 — Extract OmniOS Assets, Exclusive Hosting Coordinator & Global "Hosting" Rename *(Runs on First Proceed)*
- Unzip `omnios_v2.zip` web client assets (`index.html`, `css/`, `js/`, `assets/fonts/`, `assets/wallpapers/`) into `app/src/main/assets/omnios/` and strip all `"v2"` branding labels from user-facing strings.
- Upgrade `ServerManager` to enforce strict single-active-mode execution (`ActiveHostingMode`: `NONE`, `FTP`, `HTTP`, `CASTING`) under a coroutine `Mutex` so starting any mode automatically shuts down any other running mode before binding sockets.
- Enhance `ContinuumHubService` with `PARTIAL_WAKE_LOCK` + `WifiLock` lifecycle resilience for long-running Casting/Hosting sessions.
- Rename `"FTP/HTTP Server"` / `"Server Hub"` to **"Hosting"** across the navigation sidebar, file explorer routing, state titles, and Settings links.
- **Gate**: Run `compile_applet` and confirm zero errors before stopping.

### Phase 2 — OmniOS Server-Authoritative Backend, My Suite Mini-Apps Bridge & SyncPlay Window Lock
- Implement `OmniOSAdminConfig` (persisted whitelist of My Suite + core OmniOS apps, touch gate, tri-route audio, software luminance brightness, DE theme, dark mode, settings visibility, and shared download files/folders).
- Implement `OmniOSStateEngine` (authoritative window manager, delta patch emitter, SyncPlay locked fullscreen window injection with `closable = false, minimizable = true`, and live device telemetry).
- Implement `OmniOSHostServer` (Ktor server serving bundled OmniOS assets, `/ws/sdui` state delta + 10-byte binary touch pipeline with admin touch gate, `/api/admin/*`, `/api/files/*` sandbox browser & direct download, `/api/suite/*` live bridge for Markdown Studio, Code Studio, Table Studio, Photo Studio, and File Converter, plus `/api/syncplay/*` integration).
- Extend `assets/omnios/js/sdui-client.js` & `icons.js` to render My Suite mini-apps, enforce admin-selected app filtering, support host-shared file downloads, and lock SyncPlay windows as minimizable-only while active.
- Wire `ActiveHostingMode.CASTING` inside `ServerManager` to launch/stop `OmniOSHostServer`.
- **Gate**: Run `compile_applet` and confirm zero errors before stopping.

### Phase 3 — LAN NSD Auto-Discovery, Two-Way QR Pairing, Host Approval Gate & Projection Subsystems
- Implement `OmniOSDiscoveryManager` (Android `NsdManager` advertising `_omnios._tcp.` when hosting and discovering nearby LAN hosts in Receiver Mode).
- Implement `OmniOSPairingManager` (compact QR pairing JSON encoder/decoder, incoming Host Approval queue with Approve/Deny & session tokens, plus Host-scans-Receiver-QR reverse invite channel).
- Implement `OmniOSDisplayProjectionManager` (USB-C DisplayPort / HDMI external display detection via `DisplayManager` + `Presentation`, plus Android Miracast / Wireless Cast launcher) and `OmniOSAudioRoutingManager` (`receiver` | `host` | `both`).
- Build reusable `QrPairingDialogs.kt` (clean M3 tonal QR popup viewer + on-device camera QR scanner with manual IP/code fallback).
- Declare any needed manifest permissions/components cleanly in `AndroidManifest.xml`.
- **Gate**: Run `compile_applet` and confirm zero errors before stopping.

### Phase 4 — 3-Tab Ultra-Clean Tonal Flat Hosting UI + Built-In Screen & DeX-Style Receiver Surface
- Build modular Ultra-Clean Tonal Flat M3 tab workbenches:
  - `HostingFtpTab.kt` (FTP controls, shared scope, uploads folder, SyncPlay Party controls).
  - `HostingHttpTab.kt` (Website folder / single HTML picker, PIN gate, URL/QR, desktop live preview pane).
  - `HostingCastingTab.kt` (Host / Receiver role switch, live device IP, 5 Cast modes — Web Cast, Wired Cast, Wireless Cast, Built-In Screen, Same App LAN — NSD peer list, Two-Way QR buttons, Host Approval queue banner, shared download files picker, and SyncPlay Casting launch).
  - `HostingAdminInspector.kt` (Right desktop inspector / mobile collapsible panel for App Whitelist checkboxes including all My Suite mini-apps, Touch Gate, Audio Route, Software Brightness, DE Style, Dark Mode, Settings Visibility).
  - `OmniOSFullscreenSurface.kt` (Edge-to-edge full-width DeX-style interactive OmniOS surface used by both **Cast on Built-In Screen Now** and **App-to-App Receiver Mode**, adapting automatically to fullscreen or freeform window mode with touch/pointer/keyboard support and exit/minimize controls).
- Refactor `FtpServerSettingsPanel.kt` into a sleek thin shell using `SingleRowCommandBar` (`☰` | `Hosting` | `FTP Hosting | HTTP Hosting | Casting`) delegating cleanly to the modular tab files.
- **Gate**: Run `compile_applet` and confirm zero errors before stopping.

### Phase 5 — Error & Warning Cleanup, Help Docs Update & Final End-to-End Verification
- Update `HelpDocumentationSection.kt` with concise descriptions for the renamed Hosting hub, 3 tabs, 5 casting modes, Admin Console, Two-Way QR, and Receiver Mode.
- Audit all modified and newly created files to resolve compiler warnings, deprecated Android APIs, and unused imports/variables.
- Verify exclusive mode transitions (FTP -> HTTP -> Casting), File Manager SyncPlay -> FTP immediate launch, Casting SyncPlay locked window, and Receiver full-width rendering.
- **Gate**: Run final `compile_applet` verification confirming a clean build with zero errors.

---

## 6. File-by-File Edit & Creation Table

| Phase | Action | File Path | Responsibility / Summary of Changes |
| :---: | :---: | :--- | :--- |
| **1** | Extract | `app/src/main/assets/omnios/**` | Unzip `omnios_v2.zip` client assets (`index.html`, `css/`, `js/`, `assets/fonts/`, `assets/wallpapers/`) and remove `"v2"` branding strings. |
| **1** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/server/ServerManager.kt` | Add `ActiveHostingMode` (`NONE`, `FTP`, `HTTP`, `CASTING`), exclusive mutex switching (`activateExclusiveMode`), and Casting lifecycle hooks. |
| **1** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/server/ContinuumHubService.kt` | Add `PARTIAL_WAKE_LOCK` + `WifiLock` resilience and update notification labels to **Hosting** / **OmniOS**. |
| **1** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/ui/components/NavigationPane.kt` | Rename sidebar navigation item from `"FTP/HTTP Server"` to `"Hosting"` and show active mode dot. |
| **1** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/ui/FileExplorer.kt` | Update UniversalFile display name / routing labels from `"FTP/HTTP Server"` to `"Hosting"`. |
| **1** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/utils/FileExplorerState.kt` | Rename state title mapping from `"FTP/HTTP Server"` to `"Hosting"`. |
| **1** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/ui/activities/SettingsActivity.kt` | Rename `"Server Hub"` settings category and button to `"Hosting"`. |
| **2** | Create | `app/src/main/java/com/troikoss/continuum_explorer/server/omnios/OmniOSAdminConfig.kt` | Persisted Host Admin model: app whitelist (My Suite + OmniOS core apps), touch gate, audio route, brightness, theme, dark mode, settings visibility, shared file paths. |
| **2** | Create | `app/src/main/java/com/troikoss/continuum_explorer/server/omnios/OmniOSStateEngine.kt` | Server-authoritative window manager, delta patch broadcast, My Suite app registry, live device metrics, and locked minimizable SyncPlay fullscreen window. |
| **2** | Create | `app/src/main/java/com/troikoss/continuum_explorer/server/omnios/OmniOSHostServer.kt` | Embedded Ktor server for Casting mode: OmniOS static assets, `/ws/sdui` (deltas + 10-byte binary touch gate), `/api/admin/*`, `/api/files/*` downloads, `/api/suite/*` mini-app endpoints, `/api/pair/*`, and SyncPlay stream bridge. |
| **2** | Edit | `app/src/main/assets/omnios/js/sdui-client.js` | Add renderers for My Suite mini-apps (`markdown_studio`, `code_studio`, `table_studio`, `photo_studio`, `file_converter`), real host file downloads, locked non-closable SyncPlay fullscreen window, approval barrier, and remove `"v2"` labels. |
| **2** | Edit | `app/src/main/assets/omnios/js/icons.js` | Register crisp vector icons for all My Suite mini-apps and SyncPlay inside OmniOS dock & desktop. |
| **2** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/server/SyncPlayManager.kt` | Emit SyncPlay room state updates into `OmniOSStateEngine` when Casting mode is active so SyncPlay mirrors seamlessly across FTP & Casting. |
| **3** | Create | `app/src/main/java/com/troikoss/continuum_explorer/server/omnios/OmniOSDiscoveryManager.kt` | Android `NsdManager` LAN host advertisement (`_omnios._tcp.`) and receiver auto-discovery state flow. |
| **3** | Create | `app/src/main/java/com/troikoss/continuum_explorer/server/omnios/OmniOSPairingManager.kt` | Two-way QR payload generator/parser, Host Approval pending queue (`Approve` / `Deny`), session token issuance, and Host-to-Receiver QR invite flow. |
| **3** | Create | `app/src/main/java/com/troikoss/continuum_explorer/server/omnios/OmniOSDisplayProjectionManager.kt` | External USB-C DisplayPort / HDMI display detector (`DisplayManager` + secondary `Presentation`) and Wireless Miracast settings launcher. |
| **3** | Create | `app/src/main/java/com/troikoss/continuum_explorer/server/omnios/OmniOSAudioRoutingManager.kt` | Tri-route audio state coordinator (`receiver`, `host`, `both`) synchronized with OmniOS Host & Quick Panel. |
| **3** | Create | `app/src/main/java/com/troikoss/continuum_explorer/ui/components/hosting/QrPairingDialogs.kt` | Reusable M3 tonal QR display popup (for both Host and Receiver) and on-device QR scanner modal with manual IP/code fallback. |
| **3** | Edit | `app/src/main/AndroidManifest.xml` | Add `WAKE_LOCK`, `CHANGE_WIFI_MULTICAST_STATE` (if absent), and `CAMERA` (optional feature) for NSD, wake resilience, and two-way QR scanning. |
| **4** | Create | `app/src/main/java/com/troikoss/continuum_explorer/ui/components/hosting/HostingFtpTab.kt` | Ultra-Clean Tonal Flat M3 desktop/mobile 2-pane FTP Hosting workbench (FTP toggle, credentials, shared scope, uploads, SyncPlay Party). |
| **4** | Create | `app/src/main/java/com/troikoss/continuum_explorer/ui/components/hosting/HostingHttpTab.kt` | Ultra-Clean Tonal Flat M3 desktop/mobile 2-pane HTTP Hosting workbench (folder/HTML picker, PIN gate, live web preview). |
| **4** | Create | `app/src/main/java/com/troikoss/continuum_explorer/ui/components/hosting/HostingCastingTab.kt` | Ultra-Clean Tonal Flat M3 Casting workbench: Host/Receiver role switch, device IP, 5 Cast modes, NSD peer list, Two-Way QR controls, Host Approval banner, shared files download picker, and SyncPlay launch. |
| **4** | Create | `app/src/main/java/com/troikoss/continuum_explorer/ui/components/hosting/HostingAdminInspector.kt` | Modular Host Admin Console pane: My Suite + OmniOS App Whitelist toggles, Touch Gate, Audio Route, Software Brightness, Desktop Theme, Dark Mode, Settings Visibility. |
| **4** | Create | `app/src/main/java/com/troikoss/continuum_explorer/ui/components/hosting/OmniOSFullscreenSurface.kt` | Edge-to-edge full-width DeX-style interactive OmniOS surface for both **Built-In Screen Cast** and **App-to-App Receiver Mode** with touch/pointer/keyboard forwarding and clean exit bar. |
| **4** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/ui/components/FtpServerSettingsPanel.kt` | Refactor into a clean 3-tab `SingleRowCommandBar` (`Hosting` • `FTP Hosting | HTTP Hosting | Casting`) delegating to the modular `ui/components/hosting/*` files. |
| **5** | Edit | `app/src/main/java/com/troikoss/continuum_explorer/ui/activities/HelpDocumentationSection.kt` | Add minimalist button & feature guide entries for **Hosting**, **FTP Hosting**, **HTTP Hosting**, **Casting**, **Admin Console**, **Two-Way QR**, and **Receiver Mode**. |
| **5** | Edit | Modified Hosting, OmniOS & Navigation files | Fix compiler warnings, deprecated calls, and unused symbols; run final `compile_applet` verification. |
