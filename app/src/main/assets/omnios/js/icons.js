/* ==========================================================================
   OmniOS Multi-OS Bespoke Vector Icon Packs & Developer Customization Guide
   4 distinct vector icon sets: macOS, Windows 11, Linux (Ubuntu), Android DeX
   ==========================================================================

   DEVELOPER & ADMIN ICON CUSTOMIZATION INSTRUCTIONS:
   -------------------------------------------------
   You can customize or replace any application or system icon in 3 ways:

   METHOD 1: EDITING THE SVG STRINGS DIRECTLY IN THIS FILE
   --------------------------------------------------------
   Every icon is an SVG string wrapped by `svgWrap(innerSvg, size)`.
   The default viewport is 48x48 (`viewBox="0 0 48 48"`).
   
   Example (Replacing with custom SVG vector):
   ```javascript
   files: () => svgWrap(`
     <defs>
       <linearGradient id="my_grad" x1="0" y1="0" x2="48" y2="48">
         <stop stop-color="#FF007A"/><stop offset="1" stop-color="#7928CA"/>
       </linearGradient>
     </defs>
     <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#my_grad)"/>
     <path d="M14 24L22 32L36 16" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round"/>
   `)
   ```

   METHOD 2: USING RASTER IMAGES (PNG / JPEG / WebP / Base64)
   ----------------------------------------------------------
   If you have a PNG image or photo, you can embed it inside an SVG `<image>` tag:
   
   A) Using a local file path:
   ```javascript
   files: () => svgWrap(`
     <rect x="2" y="2" width="44" height="44" rx="11" fill="#1C1C1E"/>
     <image href="assets/icons/my_custom_files.png" x="4" y="4" width="40" height="40" preserveAspectRatio="xMidYMid meet"/>
   `)
   ```

   B) Using a Base64 Data URI (Self-contained, zero external files):
   ```javascript
   files: () => svgWrap(`
     <image href="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAA..." width="48" height="48"/>
   `)
   ```

   METHOD 3: DYNAMIC RUNTIME OVERRIDES VIA HOST ADMIN CONSOLE
   ----------------------------------------------------------
   The host administrator can push icon overrides via the Admin REST API:
   `POST /api/admin/icons` with `{ icons: { "files": "<svg...>...</svg>", "system_logo": "..." } }`
   Or in JavaScript:
   `OmniOS.icons.setCustomIcon("files", "<svg>...</svg>")`
   The system will automatically use the custom override without editing code!

   SYSTEM LOGOS (Apple, Windows, Ubuntu, DeX):
   -------------------------------------------
   Each theme includes a `system_logo` entry representing the OS brand logo in the topbar.
   ========================================================================== */

window.OmniOS = window.OmniOS || {};

(function (OmniOS) {
  'use strict';

  // Dynamic icon override storage (populated via Admin upload or local override)
  const customIconRegistry = new Map();

  function svgWrap(inner, size = 44) {
    return `<svg class="os-svg-icon" viewBox="0 0 48 48" width="${size}" height="${size}" fill="none" xmlns="http://www.w3.org/2000/svg">${inner}</svg>`;
  }

  // ---------- 1. macOS Sequoia Icon Pack (Glossy Apple Squircles) ----------
  const MACOS_ICONS = {
    // OS Brand Logo (Apple silhouette)
    system_logo: () => svgWrap(`
      <path d="M28.8 11.2C29.8 9.9 30.5 8.2 30.3 6.4C28.8 6.5 27.0 7.4 25.9 8.7C24.9 9.8 24.1 11.6 24.4 13.4C26.0 13.5 27.7 12.5 28.8 11.2Z" fill="currentColor"/>
      <path d="M30.2 20.8C30.2 16.7 33.6 14.8 33.7 14.7C31.8 11.9 28.8 11.5 27.8 11.4C25.3 11.1 22.8 12.9 21.5 12.9C20.2 12.9 18.2 11.4 16.3 11.4C13.8 11.4 11.4 12.9 10.1 15.2C7.5 19.8 9.5 26.6 12.0 30.2C13.2 32.0 14.6 33.9 16.6 33.8C18.5 33.7 19.2 32.6 21.5 32.6C23.7 32.6 24.4 33.8 26.4 33.8C28.4 33.7 29.7 32.0 30.9 30.2C32.3 28.1 32.9 26.1 33.0 26.0C32.9 25.9 30.2 24.9 30.2 20.8Z" fill="currentColor"/>
    `, 20),

    files: () => svgWrap(`
      <defs>
        <linearGradient id="m_f1" x1="0" y1="0" x2="0" y2="48"><stop stop-color="#5AC8FA"/><stop offset="1" stop-color="#007AFF"/></linearGradient>
        <linearGradient id="m_f2" x1="0" y1="0" x2="0" y2="48"><stop stop-color="#E5F4FF"/><stop offset="1" stop-color="#B8DDFB"/></linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#m_f1)"/>
      <path d="M24 2H35C41.075 2 46 6.925 46 13V35C46 41.075 41.075 46 35 46H22C25 35 26 24 22 24H25C24 14 23 7 24 2Z" fill="url(#m_f2)"/>
      <rect x="14" y="15" width="3" height="6" rx="1.5" fill="#1D3557"/>
      <rect x="31" y="15" width="3" height="6" rx="1.5" fill="#1D3557"/>
      <path d="M13 30C17 35 31 35 35 30" stroke="#1D3557" stroke-width="2.6" stroke-linecap="round"/>
      <rect x="2.5" y="2.5" width="43" height="43" rx="10.5" stroke="rgba(255,255,255,0.35)"/>
    `),
    webapps: () => svgWrap(`
      <defs>
        <linearGradient id="m_s1" x1="0" y1="0" x2="0" y2="48"><stop stop-color="#64D2FF"/><stop offset="1" stop-color="#0066FF"/></linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="#F8FAFC"/>
      <circle cx="24" cy="24" r="17.5" fill="url(#m_s1)"/>
      <circle cx="24" cy="24" r="15" stroke="rgba(255,255,255,0.45)" stroke-dasharray="1.5 3.5" stroke-width="1.5"/>
      <polygon points="24,24 33,14 27,27" fill="#FF3B30"/>
      <polygon points="24,24 15,34 21,21" fill="#FFFFFF"/>
      <circle cx="24" cy="24" r="2" fill="#E2E8F0"/>
      <rect x="2.5" y="2.5" width="43" height="43" rx="10.5" stroke="rgba(255,255,255,0.5)"/>
    `),
    notes: () => svgWrap(`
      <defs>
        <linearGradient id="m_n1" x1="0" y1="0" x2="0" y2="14"><stop stop-color="#FFD60A"/><stop offset="1" stop-color="#FF9F0A"/></linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="#FFFFFF"/>
      <path d="M2 13C2 6.925 6.925 2 13 2H35C41.075 2 46 6.925 46 13V15H2V13Z" fill="url(#m_n1)"/>
      <line x1="8" y1="22" x2="40" y2="22" stroke="#E2E8F0" stroke-width="1.6"/>
      <line x1="8" y1="29" x2="40" y2="29" stroke="#E2E8F0" stroke-width="1.6"/>
      <line x1="8" y1="36" x2="32" y2="36" stroke="#E2E8F0" stroke-width="1.6"/>
      <line x1="8" y1="18.5" x2="40" y2="18.5" stroke="#CBD5E1" stroke-dasharray="2 2" stroke-width="1"/>
      <rect x="2.5" y="2.5" width="43" height="43" rx="10.5" stroke="rgba(0,0,0,0.08)"/>
    `),
    tasks: () => svgWrap(`
      <rect x="2" y="2" width="44" height="44" rx="11" fill="#FFFFFF"/>
      <circle cx="13" cy="14" r="3.2" fill="#007AFF"/>
      <rect x="20" y="12.5" width="19" height="3" rx="1.5" fill="#CBD5E1"/>
      <circle cx="13" cy="24" r="3.2" fill="#FF3B30"/>
      <rect x="20" y="22.5" width="16" height="3" rx="1.5" fill="#CBD5E1"/>
      <circle cx="13" cy="34" r="3.2" fill="#34C759"/>
      <rect x="20" y="32.5" width="19" height="3" rx="1.5" fill="#CBD5E1"/>
      <rect x="2.5" y="2.5" width="43" height="43" rx="10.5" stroke="rgba(0,0,0,0.08)"/>
    `),
    calculator: () => svgWrap(`
      <defs>
        <linearGradient id="m_c1" x1="0" y1="0" x2="0" y2="48"><stop stop-color="#3A3A3C"/><stop offset="1" stop-color="#1C1C1E"/></linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#m_c1)"/>
      <rect x="8" y="8" width="32" height="9" rx="3" fill="#2C2C2E"/>
      <circle cx="13" cy="23" r="3.2" fill="#636366"/><circle cx="22" cy="23" r="3.2" fill="#636366"/><circle cx="33" cy="23" r="3.4" fill="#FF9F0A"/>
      <circle cx="13" cy="32" r="3.2" fill="#636366"/><circle cx="22" cy="32" r="3.2" fill="#636366"/><circle cx="33" cy="32" r="3.4" fill="#FF9F0A"/>
      <rect x="10" y="38.5" width="15" height="3.5" rx="1.75" fill="#636366"/><circle cx="33" cy="40" r="3.2" fill="#FF9F0A"/>
      <rect x="2.5" y="2.5" width="43" height="43" rx="10.5" stroke="rgba(255,255,255,0.2)"/>
    `),
    terminal: () => svgWrap(`
      <defs>
        <linearGradient id="m_t1" x1="0" y1="0" x2="0" y2="48"><stop stop-color="#2C2C2E"/><stop offset="1" stop-color="#0D0D0F"/></linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#m_t1)"/>
      <path d="M12 18L19 23.5L12 29" stroke="#30D158" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
      <line x1="22" y1="29" x2="34" y2="29" stroke="#F2F2F7" stroke-width="3" stroke-linecap="round"/>
    `),
    monitor: () => svgWrap(`
      <defs>
        <linearGradient id="m_m1" x1="0" y1="0" x2="0" y2="48"><stop stop-color="#1E293B"/><stop offset="1" stop-color="#0B1120"/></linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#m_m1)"/>
      <path d="M6 26H14L18 14L24 35L29 21L33 26H42" stroke="#30D158" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
      <rect x="2.5" y="2.5" width="43" height="43" rx="10.5" stroke="rgba(255,255,255,0.18)"/>
    `),
    settings: () => svgWrap(`
      <defs>
        <linearGradient id="m_g1" x1="0" y1="0" x2="0" y2="48"><stop stop-color="#D1D5DB"/><stop offset="1" stop-color="#6B7280"/></linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#m_g1)"/>
      <circle cx="24" cy="24" r="11.5" stroke="#1F2937" stroke-width="4" stroke-dasharray="5 3.2"/>
      <circle cx="24" cy="24" r="6.5" fill="#F3F4F6" stroke="#374151" stroke-width="2"/>
      <circle cx="24" cy="24" r="2.5" fill="#1F2937"/>
      <rect x="2.5" y="2.5" width="43" height="43" rx="10.5" stroke="rgba(255,255,255,0.4)"/>
    `)
  };

  // ---------- 2. Windows 11 Fluent Icon Pack ----------
  const WINDOWS_ICONS = {
    // OS Brand Logo (Windows 11 4-Square Blue Tiles)
    system_logo: () => svgWrap(`
      <rect x="6" y="6" width="16" height="16" rx="2" fill="#0078D4"/>
      <rect x="26" y="6" width="16" height="16" rx="2" fill="#0078D4"/>
      <rect x="6" y="26" width="16" height="16" rx="2" fill="#0078D4"/>
      <rect x="26" y="26" width="16" height="16" rx="2" fill="#0078D4"/>
    `, 20),

    files: () => svgWrap(`
      <defs>
        <linearGradient id="w_f1" x1="0" y1="8" x2="44" y2="40"><stop stop-color="#FCE166"/><stop offset="1" stop-color="#E5A21B"/></linearGradient>
        <linearGradient id="w_f2" x1="0" y1="16" x2="44" y2="42"><stop stop-color="#FFDF6B"/><stop offset="1" stop-color="#F2B729"/></linearGradient>
      </defs>
      <path d="M5 12C5 9.8 6.8 8 9 8H19L23 12H39C41.2 12 43 13.8 43 16V37C43 39.2 41.2 41 39 41H9C6.8 41 5 39.2 5 37V12Z" fill="url(#w_f1)"/>
      <rect x="5" y="16" width="38" height="24" rx="3.5" fill="url(#w_f2)"/>
      <rect x="15" y="34" width="18" height="7" rx="2" fill="#0078D4"/>
    `),
    webapps: () => svgWrap(`
      <defs>
        <linearGradient id="w_e1" x1="6" y1="6" x2="42" y2="42"><stop stop-color="#38BDF8"/><stop offset="0.55" stop-color="#0078D4"/><stop offset="1" stop-color="#0EA5E9"/></linearGradient>
        <linearGradient id="w_e2" x1="10" y1="22" x2="38" y2="42"><stop stop-color="#34D399"/><stop offset="1" stop-color="#0284C7"/></linearGradient>
      </defs>
      <circle cx="24" cy="24" r="19" fill="url(#w_e1)"/>
      <path d="M9 28C13 38 30 43 38 34C32 37 22 34 21 26C20 19 28 15 34 19C31 11 19 9 12 16C8 20 7 24 9 28Z" fill="url(#w_e2)"/>
      <circle cx="25" cy="24" r="6" fill="#0B192C" fill-opacity="0.35"/>
    `),
    notes: () => svgWrap(`
      <defs>
        <linearGradient id="w_n1" x1="8" y1="6" x2="40" y2="42"><stop stop-color="#60CDFF"/><stop offset="1" stop-color="#0078D4"/></linearGradient>
      </defs>
      <rect x="9" y="6" width="30" height="36" rx="4" fill="#F8FAFC"/>
      <path d="M9 10C9 7.8 10.8 6 13 6H35C37.2 6 39 7.8 39 10V15H9V10Z" fill="url(#w_n1)"/>
      <rect x="14" y="21" width="20" height="2.4" rx="1.2" fill="#94A3B8"/>
      <rect x="14" y="27" width="16" height="2.4" rx="1.2" fill="#94A3B8"/>
      <rect x="14" y="33" width="12" height="2.4" rx="1.2" fill="#CBD5E1"/>
    `),
    tasks: () => svgWrap(`
      <defs>
        <linearGradient id="w_td1" x1="6" y1="12" x2="42" y2="38"><stop stop-color="#60CDFF"/><stop offset="1" stop-color="#005FB8"/></linearGradient>
      </defs>
      <path d="M8 25L19 36L41 14" stroke="url(#w_td1)" stroke-width="7.5" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M8 25L19 36" stroke="#38BDF8" stroke-width="7.5" stroke-linecap="round"/>
    `),
    calculator: () => svgWrap(`
      <defs>
        <linearGradient id="w_c1" x1="8" y1="4" x2="40" y2="44"><stop stop-color="#475569"/><stop offset="1" stop-color="#1E293B"/></linearGradient>
      </defs>
      <rect x="8" y="4" width="32" height="40" rx="5" fill="url(#w_c1)"/>
      <rect x="12" y="9" width="24" height="8" rx="2" fill="#94A3B8"/>
      <rect x="12" y="21" width="6.5" height="5.5" rx="1.5" fill="#CBD5E1"/>
      <rect x="20.7" y="21" width="6.5" height="5.5" rx="1.5" fill="#CBD5E1"/>
      <rect x="29.5" y="21" width="6.5" height="5.5" rx="1.5" fill="#60CDFF"/>
      <rect x="12" y="29" width="6.5" height="5.5" rx="1.5" fill="#CBD5E1"/>
      <rect x="20.7" y="29" width="6.5" height="5.5" rx="1.5" fill="#CBD5E1"/>
      <rect x="29.5" y="29" width="6.5" height="11" rx="1.5" fill="#0078D4"/>
      <rect x="12" y="36" width="15.2" height="4" rx="1.5" fill="#CBD5E1"/>
    `),
    terminal: () => svgWrap(`
      <rect x="4" y="7" width="40" height="34" rx="5" fill="#1E242E"/>
      <path d="M4 12C4 9.2 6.2 7 9 7H39C41.8 7 44 9.2 44 12V15H4V12Z" fill="#334155"/>
      <rect x="8" y="9.5" width="8" height="3" rx="1" fill="#60CDFF"/>
      <rect x="18" y="9.5" width="7" height="3" rx="1" fill="#64748B"/>
      <path d="M12 22L19 27L12 32" stroke="#F8FAFC" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
      <line x1="22" y1="32" x2="32" y2="32" stroke="#60CDFF" stroke-width="2.8" stroke-linecap="round"/>
    `),
    monitor: () => svgWrap(`
      <rect x="5" y="7" width="38" height="34" rx="5" fill="#0F172A" stroke="#38BDF8" stroke-width="1.6"/>
      <path d="M8 29H15L19 16L25 33L30 21L34 29H40" stroke="#38BDF8" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
    `),
    settings: () => svgWrap(`
      <defs>
        <linearGradient id="w_g1" x1="6" y1="6" x2="42" y2="42"><stop stop-color="#94A3B8"/><stop offset="1" stop-color="#475569"/></linearGradient>
      </defs>
      <circle cx="24" cy="24" r="14" stroke="url(#w_g1)" stroke-width="6.5" stroke-dasharray="6.5 4.5"/>
      <circle cx="24" cy="24" r="9.5" fill="#E2E8F0"/>
      <circle cx="24" cy="24" r="5" fill="#0078D4"/>
    `)
  };

  // ---------- 3. Linux Ubuntu / GNOME Yaru Icon Pack ----------
  const LINUX_ICONS = {
    // OS Brand Logo (Ubuntu Circle of Friends)
    system_logo: () => svgWrap(`
      <circle cx="24" cy="24" r="20" fill="#E95420"/>
      <circle cx="24" cy="24" r="13" stroke="#FFFFFF" stroke-width="3" fill="none"/>
      <circle cx="9" cy="24" r="3.5" fill="#FFFFFF"/>
      <circle cx="31.5" cy="11" r="3.5" fill="#FFFFFF"/>
      <circle cx="31.5" cy="37" r="3.5" fill="#FFFFFF"/>
    `, 20),

    files: () => svgWrap(`
      <defs>
        <linearGradient id="l_f1" x1="4" y1="8" x2="44" y2="42"><stop stop-color="#F97316"/><stop offset="1" stop-color="#C2410C"/></linearGradient>
      </defs>
      <path d="M5 13C5 10.2 7.2 8 10 8H19L23 12H38C40.8 12 43 14.2 43 17V36C43 38.8 40.8 41 38 41H10C7.2 41 5 38.8 5 36V13Z" fill="url(#l_f1)"/>
      <rect x="5" y="17" width="38" height="23" rx="3" fill="#EA580C"/>
      <rect x="19" y="25" width="10" height="3" rx="1.5" fill="rgba(255,255,255,0.75)"/>
    `),
    webapps: () => svgWrap(`
      <circle cx="24" cy="24" r="20" fill="#1D4ED8"/>
      <circle cx="24" cy="24" r="15" stroke="#93C5FD" stroke-width="1.6"/>
      <ellipse cx="24" cy="24" rx="7.5" ry="15" stroke="#93C5FD" stroke-width="1.5"/>
      <line x1="9" y1="24" x2="39" y2="24" stroke="#93C5FD" stroke-width="1.5"/>
      <polygon points="24,24 32,16 26,26" fill="#F97316"/>
    `),
    notes: () => svgWrap(`
      <rect x="8" y="5" width="32" height="38" rx="4" fill="#FAF7F2"/>
      <path d="M29 5H36V18L32.5 15L29 18V5Z" fill="#E95420"/>
      <rect x="13" y="22" width="22" height="2.4" rx="1.2" fill="#64748B"/>
      <rect x="13" y="28" width="18" height="2.4" rx="1.2" fill="#94A3B8"/>
      <rect x="13" y="34" width="14" height="2.4" rx="1.2" fill="#CBD5E1"/>
    `),
    tasks: () => svgWrap(`
      <rect x="5" y="5" width="38" height="38" rx="9" fill="#16A34A"/>
      <path d="M14 24.5L21 31.5L35 17" stroke="#FFFFFF" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
    `),
    calculator: () => svgWrap(`
      <rect x="7" y="5" width="34" height="38" rx="6" fill="#2D2D32"/>
      <rect x="11" y="9" width="26" height="9" rx="2.5" fill="#14532D"/>
      <circle cx="15" cy="25" r="2.8" fill="#A1A1AA"/><circle cx="24" cy="25" r="2.8" fill="#A1A1AA"/><circle cx="33" cy="25" r="2.8" fill="#E95420"/>
      <circle cx="15" cy="34" r="2.8" fill="#A1A1AA"/><circle cx="24" cy="34" r="2.8" fill="#A1A1AA"/><circle cx="33" cy="34" r="2.8" fill="#E95420"/>
    `),
    terminal: () => svgWrap(`
      <rect x="4" y="6" width="40" height="36" rx="6" fill="#300A24" stroke="#77216F" stroke-width="2"/>
      <path d="M12 18L19 23.5L12 29" stroke="#E95420" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      <line x1="22" y1="29" x2="33" y2="29" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round"/>
    `),
    monitor: () => svgWrap(`
      <rect x="4" y="6" width="40" height="36" rx="6" fill="#242428"/>
      <path d="M8 27H15L19 15L24 33L29 21L33 27H40" stroke="#22C55E" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
    `),
    settings: () => svgWrap(`
      <rect x="5" y="5" width="38" height="38" rx="9" fill="#3F3F46"/>
      <circle cx="24" cy="24" r="10.5" stroke="#E4E4E7" stroke-width="4" stroke-dasharray="5 3.3"/>
      <circle cx="24" cy="24" r="4.5" fill="#E95420"/>
    `)
  };

  // ---------- 4. Android DeX (Samsung One UI Superellipse Squircles) ----------
  const SQUIRCLE = 'M24 2C8.5 2 2 8.5 2 24C2 39.5 8.5 46 24 46C39.5 46 46 39.5 46 24C46 8.5 39.5 2 24 2Z';
  const DEX_ICONS = {
    // OS Brand Logo (Samsung DeX 9-Dot Superellipse)
    system_logo: () => svgWrap(`
      <defs><linearGradient id="d_logo_g" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#2563EB"/><stop offset="1" stop-color="#1D4ED8"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_logo_g)"/>
      <circle cx="15" cy="15" r="3.2" fill="#FFFFFF"/>
      <circle cx="24" cy="15" r="3.2" fill="#FFFFFF"/>
      <circle cx="33" cy="15" r="3.2" fill="#FFFFFF"/>
      <circle cx="15" cy="24" r="3.2" fill="#FFFFFF"/>
      <circle cx="24" cy="24" r="3.2" fill="#FFFFFF"/>
      <circle cx="33" cy="24" r="3.2" fill="#FFFFFF"/>
      <circle cx="15" cy="33" r="3.2" fill="#FFFFFF"/>
      <circle cx="24" cy="33" r="3.2" fill="#FFFFFF"/>
      <circle cx="33" cy="33" r="3.2" fill="#FFFFFF"/>
    `, 20),

    files: () => svgWrap(`
      <defs><linearGradient id="d_f" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#FFB703"/><stop offset="1" stop-color="#F57C00"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_f)"/>
      <path d="M13 17C13 15.3 14.3 14 16 14H22L25 17H32C33.7 17 35 18.3 35 20V31C35 32.7 33.7 34 32 34H16C14.3 34 13 32.7 13 31V17Z" fill="#FFFFFF"/>
    `),
    webapps: () => svgWrap(`
      <defs><linearGradient id="d_w" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#7C4DFF"/><stop offset="1" stop-color="#4F46E5"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_w)"/>
      <circle cx="24" cy="24" r="8.5" fill="#FFFFFF"/>
      <ellipse cx="24" cy="24" rx="16.5" ry="5.5" transform="rotate(-24 24 24)" stroke="#FFFFFF" stroke-width="2.6"/>
    `),
    notes: () => svgWrap(`
      <defs><linearGradient id="d_n" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#FF6B4A"/><stop offset="1" stop-color="#E11D48"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_n)"/>
      <rect x="14" y="13" width="20" height="22" rx="4" fill="#FFFFFF"/>
      <line x1="18" y1="20" x2="30" y2="20" stroke="#E11D48" stroke-width="2.2" stroke-linecap="round"/>
      <line x1="18" y1="26" x2="26" y2="26" stroke="#E11D48" stroke-width="2.2" stroke-linecap="round"/>
    `),
    tasks: () => svgWrap(`
      <defs><linearGradient id="d_t" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#14B8A6"/><stop offset="1" stop-color="#0284C7"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_t)"/>
      <path d="M15 24.5L21.5 31L34 18" stroke="#FFFFFF" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
    `),
    calculator: () => svgWrap(`
      <defs><linearGradient id="d_c" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#22C55E"/><stop offset="1" stop-color="#15803D"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_c)"/>
      <path d="M16 14V22M12 18H20" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round"/>
      <line x1="28" y1="18" x2="36" y2="18" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round"/>
      <path d="M13 28L19 34M19 28L13 34" stroke="#FFFFFF" stroke-width="2.6" stroke-linecap="round"/>
      <path d="M28 29.5H36M28 33.5H36" stroke="#FFFFFF" stroke-width="2.6" stroke-linecap="round"/>
    `),
    terminal: () => svgWrap(`
      <defs><linearGradient id="d_tm" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#334155"/><stop offset="1" stop-color="#0F172A"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_tm)"/>
      <path d="M14 18L21 24L14 30" stroke="#38BDF8" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      <line x1="23" y1="30" x2="34" y2="30" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round"/>
    `),
    monitor: () => svgWrap(`
      <defs><linearGradient id="d_m" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#06B6D4"/><stop offset="1" stop-color="#2563EB"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_m)"/>
      <path d="M10 25H17L21 15L26 33L30 22L33 25H38" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
    `),
    settings: () => svgWrap(`
      <defs><linearGradient id="d_s" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#3B82F6"/><stop offset="1" stop-color="#1D4ED8"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_s)"/>
      <circle cx="24" cy="24" r="9.5" stroke="#FFFFFF" stroke-width="3.6" stroke-dasharray="5.2 4.7"/>
      <circle cx="24" cy="24" r="4.2" fill="#FFFFFF"/>
    `),
    custom: () => svgWrap(`
      <defs><linearGradient id="d_cust" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#8B5CF6"/><stop offset="1" stop-color="#6366F1"/></linearGradient></defs>
      <path d="${SQUIRCLE}" fill="url(#d_cust)"/>
      <rect x="12" y="12" width="24" height="24" rx="6" fill="rgba(255,255,255,0.18)" stroke="#FFFFFF" stroke-width="2"/>
      <path d="M19 21L15 25L19 29M29 21L33 25L29 29" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
    `)
  };

  MACOS_ICONS.custom = () => svgWrap(`
    <defs><linearGradient id="m_cust" x1="0" y1="0" x2="0" y2="48"><stop stop-color="#A855F7"/><stop offset="1" stop-color="#6366F1"/></linearGradient></defs>
    <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#m_cust)"/>
    <rect x="11" y="11" width="26" height="26" rx="7" fill="rgba(255,255,255,0.15)" stroke="rgba(255,255,255,0.45)" stroke-width="1.8"/>
    <path d="M19 20L15 24L19 28M29 20L33 24L29 28" stroke="#FFFFFF" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>
    <rect x="2.5" y="2.5" width="43" height="43" rx="10.5" stroke="rgba(255,255,255,0.3)"/>
  `);

  WINDOWS_ICONS.custom = () => svgWrap(`
    <defs><linearGradient id="w_cust" x1="6" y1="6" x2="42" y2="42"><stop stop-color="#C084FC"/><stop offset="1" stop-color="#4F46E5"/></linearGradient></defs>
    <rect x="5" y="6" width="38" height="36" rx="5" fill="url(#w_cust)"/>
    <path d="M18 20L13 24L18 28M30 20L35 24L30 28" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
    <line x1="26" y1="18" x2="22" y2="30" stroke="#E2E8F0" stroke-width="2.4" stroke-linecap="round"/>
  `);

  LINUX_ICONS.custom = () => svgWrap(`
    <rect x="5" y="5" width="38" height="38" rx="9" fill="#7C3AED"/>
    <path d="M18 20L13 24L18 28M30 20L35 24L30 28" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
    <line x1="26" y1="18" x2="22" y2="30" stroke="#DDD6FE" stroke-width="2.5" stroke-linecap="round"/>
  `);

  // ---------- 5. My Suite Mini-Apps & OmniOS Media/SyncPlay Shared Bespoke Icons ----------
  const SUITE_SHARED_ICONS = {
    markdown_studio: () => svgWrap(`
      <defs><linearGradient id="s_md" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#6366F1"/><stop offset="1" stop-color="#4338CA"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_md)"/>
      <path d="M11 31V17L17 24L23 17V31" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M31 17V29M31 29L27 25M31 29L35 25" stroke="#C7D2FE" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
    `),
    code_studio: () => svgWrap(`
      <defs><linearGradient id="s_code" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#0EA5E9"/><stop offset="1" stop-color="#1D4ED8"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_code)"/>
      <path d="M17 17L10 24L17 31M31 17L38 24L31 31" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
      <line x1="26" y1="15" x2="22" y2="33" stroke="#BAE6FD" stroke-width="2.6" stroke-linecap="round"/>
    `),
    table_studio: () => svgWrap(`
      <defs><linearGradient id="s_tbl" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#10B981"/><stop offset="1" stop-color="#047857"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_tbl)"/>
      <rect x="10" y="12" width="28" height="24" rx="4" stroke="#FFFFFF" stroke-width="2.4"/>
      <line x1="10" y1="19" x2="38" y2="19" stroke="#FFFFFF" stroke-width="2.2"/>
      <line x1="10" y1="27" x2="38" y2="27" stroke="#A7F3D0" stroke-width="1.8"/>
      <line x1="20" y1="12" x2="20" y2="36" stroke="#FFFFFF" stroke-width="2.2"/>
      <line x1="29" y1="12" x2="29" y2="36" stroke="#A7F3D0" stroke-width="1.8"/>
    `),
    photo_studio: () => svgWrap(`
      <defs><linearGradient id="s_pho" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#EC4899"/><stop offset="1" stop-color="#9333EA"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_pho)"/>
      <rect x="10" y="12" width="28" height="24" rx="5" fill="rgba(255,255,255,0.16)" stroke="#FFFFFF" stroke-width="2.2"/>
      <circle cx="18" cy="19" r="2.6" fill="#FDE047"/>
      <path d="M12 33L21 23L27 29L31 25L37 33" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
    `),
    file_converter: () => svgWrap(`
      <defs><linearGradient id="s_conv" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#F59E0B"/><stop offset="1" stop-color="#EA580C"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_conv)"/>
      <path d="M14 20A10 10 0 0 1 32 16M32 16H26M32 16V22" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M34 28A10 10 0 0 1 16 32M16 32H22M16 32V26" stroke="#FEF3C7" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
    `),
    syncplay: () => svgWrap(`
      <defs><linearGradient id="s_sp" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#EF4444"/><stop offset="1" stop-color="#B91C1C"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_sp)"/>
      <circle cx="24" cy="24" r="14" stroke="rgba(255,255,255,0.45)" stroke-width="2"/>
      <polygon points="20,16 33,24 20,32" fill="#FFFFFF"/>
    `),
    video: () => svgWrap(`
      <defs><linearGradient id="s_vid" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#3B82F6"/><stop offset="1" stop-color="#1E40AF"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_vid)"/>
      <rect x="10" y="14" width="20" height="20" rx="4" fill="#FFFFFF"/>
      <polygon points="31,20 39,15 39,33 31,28" fill="#BFDBFE"/>
    `),
    music: () => svgWrap(`
      <defs><linearGradient id="s_mus" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#F43F5E"/><stop offset="1" stop-color="#BE123C"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_mus)"/>
      <path d="M19 32V15L34 12V29" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="16" cy="32" r="3.5" fill="#FFFFFF"/>
      <circle cx="31" cy="29" r="3.5" fill="#FFFFFF"/>
    `),
    pdf: () => svgWrap(`
      <defs><linearGradient id="s_pdf" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#DC2626"/><stop offset="1" stop-color="#991B1B"/></linearGradient></defs>
      <rect x="2" y="2" width="44" height="44" rx="11" fill="url(#s_pdf)"/>
      <path d="M15 11H27L34 18V37H15V11Z" fill="#FFFFFF"/>
      <path d="M27 11V18H34" fill="#FCA5A5"/>
      <rect x="18" y="24" width="12" height="2.6" rx="1.3" fill="#DC2626"/>
      <rect x="18" y="29" width="8" height="2.6" rx="1.3" fill="#DC2626"/>
    `),
    sysmon: () => MACOS_ICONS.monitor()
  };

  [MACOS_ICONS, WINDOWS_ICONS, LINUX_ICONS, DEX_ICONS].forEach(pack => {
    Object.assign(pack, SUITE_SHARED_ICONS);
    pack.sysmon = pack.monitor || SUITE_SHARED_ICONS.sysmon;
  });

  const PACKS = {
    macos: MACOS_ICONS,
    windows: WINDOWS_ICONS,
    linux: LINUX_ICONS,
    dex: DEX_ICONS
  };

  OmniOS.icons = {
    /**
     * Injects or overrides an icon at runtime (used by Admin Uploads or custom JS).
     * @param {string} id - App ID (e.g. 'files', 'video') or 'system_logo'
     * @param {string} svgOrHtml - Full SVG string or inner SVG markup
     */
    setCustomIcon(id, svgOrHtml) {
      if (!id || !svgOrHtml) return;
      customIconRegistry.set(id, svgOrHtml);
    },

    /**
     * Clears custom icon overrides.
     */
    clearCustomIcons() {
      customIconRegistry.clear();
    },

    /**
     * Returns the SVG icon for a given application ID and theme.
     */
    get(appId, osId, size = 44) {
      // 1. Check custom admin override first
      if (customIconRegistry.has(appId)) {
        let html = customIconRegistry.get(appId);
        if (!html.startsWith('<svg')) {
          html = svgWrap(html, size);
        }
        return size === 44 ? html : html.replace(/width="\d+" height="\d+"/, `width="${size}" height="${size}"`);
      }

      // 2. Fetch from active theme pack
      const os = osId || document.documentElement.getAttribute('data-os') || 'macos';
      const pack = PACKS[os] || MACOS_ICONS;
      const fn = pack[appId] || (appId && appId.startsWith('app_') ? pack.custom : null) || pack.files;
      const html = fn();
      return size === 44 ? html : html.replace(/width="\d+" height="\d+"/, `width="${size}" height="${size}"`);
    },

    /**
     * Returns the branded system OS logo (Apple, Windows, Ubuntu, DeX) for the topbar/menu.
     */
    getSystemLogo(osId, size = 20) {
      if (customIconRegistry.has('system_logo')) {
        let html = customIconRegistry.get('system_logo');
        if (!html.startsWith('<svg')) {
          html = svgWrap(html, size);
        }
        return html;
      }

      const os = osId || document.documentElement.getAttribute('data-os') || 'macos';
      const pack = PACKS[os] || MACOS_ICONS;
      const fn = pack.system_logo || MACOS_ICONS.system_logo;
      const html = fn();
      return size === 20 ? html : html.replace(/width="\d+" height="\d+"/, `width="${size}" height="${size}"`);
    },

    ui: {
      folder: '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M3 6a2 2 0 0 1 2-2h4.6a2 2 0 0 1 1.4.6l1.4 1.4H19a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6z"/></svg>',
      file: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>',
      code: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>',
      image: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/></svg>',
      video: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="4" width="15" height="16" rx="2"/><path d="m17 9 5-3v12l-5-3z"/><polygon points="7 10 7 14 11 12" fill="currentColor"/></svg>',
      audio: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>',
      pdf: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 15v-4h2a1.5 1.5 0 0 1 0 3H9"/></svg>',
      trash: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>',
      globe: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>',
      aspect: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 15h3M7 15v-3M17 9h-3M17 9v3"/></svg>'
    }
  };
})(window.OmniOS);
