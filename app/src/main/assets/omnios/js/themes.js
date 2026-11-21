/* ==========================================================================
   OmniOS Theme & OS Control Engine
   Full OS-level controls: OS Theme, Resolution Scale, Text Size, Bundled Fonts,
   Accent Color, Glass Blur, Night Light, Taskbar Pinning, Auto-Hide, User Profile
   ========================================================================== */

window.OmniOS = window.OmniOS || {};

(function (OmniOS) {
  const ACTIVE_USER_KEY = 'omnios_active_user_v1';

  const OS_META = {
    macos: {
      id: 'macos',
      name: 'macOS Sequoia',
      panelTitle: 'Control Center',
      notifTitle: 'Notification Center',
      defaultAccent: '#0a84ff',
      brandSvg: '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M15.97 6.33c.64-.78 1.08-1.86.96-2.94-.93.04-2.06.62-2.72 1.4-.58.68-1.1 1.78-.96 2.84 1.04.08 2.09-.52 2.72-1.3z"/></svg>',
      brandLabel: '',
      defaultWallpaper: 'macos',
      launcherSvg: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><rect x="3" y="3" width="5" height="5" rx="1.4"/><rect x="9.5" y="3" width="5" height="5" rx="1.4"/><rect x="16" y="3" width="5" height="5" rx="1.4"/><rect x="3" y="9.5" width="5" height="5" rx="1.4"/><rect x="9.5" y="9.5" width="5" height="5" rx="1.4"/><rect x="16" y="9.5" width="5" height="5" rx="1.4"/><rect x="3" y="16" width="5" height="5" rx="1.4"/><rect x="9.5" y="16" width="5" height="5" rx="1.4"/><rect x="16" y="16" width="5" height="5" rx="1.4"/></svg>'
    },
    windows: {
      id: 'windows',
      name: 'Windows 11',
      panelTitle: 'Quick Settings',
      notifTitle: 'Notifications',
      defaultAccent: '#0078d4',
      brandSvg: '',
      brandLabel: 'Start',
      defaultWallpaper: 'windows',
      launcherSvg: '<svg viewBox="0 0 24 24" width="20" height="20" fill="#38bdf8"><rect x="3" y="3" width="8.5" height="8.5" rx="1.2"/><rect x="12.5" y="3" width="8.5" height="8.5" rx="1.2"/><rect x="3" y="12.5" width="8.5" height="8.5" rx="1.2"/><rect x="12.5" y="12.5" width="8.5" height="8.5" rx="1.2"/></svg>'
    },
    linux: {
      id: 'linux',
      name: 'Linux GNOME',
      panelTitle: 'System Settings',
      notifTitle: 'Events & Notifications',
      defaultAccent: '#e95420',
      brandSvg: '<svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><circle cx="12" cy="12" r="5"/></svg>',
      brandLabel: 'Activities',
      defaultWallpaper: 'linux',
      launcherSvg: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><circle cx="6" cy="6" r="2"/><circle cx="12" cy="6" r="2"/><circle cx="18" cy="6" r="2"/><circle cx="6" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="18" cy="12" r="2"/><circle cx="6" cy="18" r="2"/><circle cx="12" cy="18" r="2"/><circle cx="18" cy="18" r="2"/></svg>'
    },
    dex: {
      id: 'dex',
      name: 'Android DeX',
      panelTitle: 'Quick Panel',
      notifTitle: 'Notification Shade',
      defaultAccent: '#0ea5e9',
      brandSvg: '',
      brandLabel: 'DeX',
      defaultWallpaper: 'dex',
      launcherSvg: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><circle cx="6" cy="6" r="2.1"/><circle cx="12" cy="6" r="2.1"/><circle cx="18" cy="6" r="2.1"/><circle cx="6" cy="12" r="2.1"/><circle cx="12" cy="12" r="2.1"/><circle cx="18" cy="12" r="2.1"/><circle cx="6" cy="18" r="2.1"/><circle cx="12" cy="18" r="2.1"/><circle cx="18" cy="18" r="2.1"/></svg>'
    }
  };

  const WALLPAPERS = [
    { id: 'macos',   name: 'Sequoia Aurora', src: 'assets/wallpapers/macos.jpg' },
    { id: 'windows', name: 'Fluent Bloom',   src: 'assets/wallpapers/windows.jpg' },
    { id: 'linux',   name: 'Ubuntu Silk',    src: 'assets/wallpapers/linux.jpg' },
    { id: 'dex',     name: 'One UI Glass',   src: 'assets/wallpapers/dex.jpg' }
  ];

  const FONTS = [
    { id: 'os-default', name: 'OS Default (Auto)' },
    { id: 'inter',      name: 'Inter (Bundled Offline)' },
    { id: 'outfit',     name: 'Outfit (Bundled Offline)' },
    { id: 'ubuntu',     name: 'Ubuntu' },
    { id: 'jakarta',    name: 'Plus Jakarta Sans' },
    { id: 'firacode',   name: 'Fira Code Mono (Bundled)' },
    { id: 'system',     name: 'System Native' }
  ];

  const VIEWPORT_RATIOS = [
    { id: 'auto', label: 'Full Window (Responsive)' },
    { id: '16-9', label: '16:9 Widescreen Desktop' },
    { id: '16-10', label: '16:10 Laptop Display' },
    { id: '4-3', label: '4:3 Tablet / Classic' },
    { id: '9-16', label: '9:16 Mobile View' }
  ];

  const RESOLUTIONS = [
    { id: 'native', label: 'Auto (Native)', zoom: 1.0 },
    { id: 'compact', label: 'High Density (85%)', zoom: 0.85 },
    { id: 'hd', label: 'Standard 1080p (100%)', zoom: 1.0 },
    { id: 'comfortable', label: 'Comfortable (110%)', zoom: 1.1 },
    { id: 'large', label: 'Large Display (125%)', zoom: 1.25 }
  ];

  const ANIMATION_PRESETS = [
    { id: 'macos-scale', name: 'macOS Dock Scale & Genie', desc: 'Scales up & down smoothly with dock icon anchor' },
    { id: 'zoom-pop', name: 'Center Zoom & Pop', desc: 'Expands from screen center with subtle pop overshoot' },
    { id: 'fluent-glide', name: 'Windows 11 Fluent Glide', desc: 'Smooth upward translate and acrylic fade' },
    { id: 'gnome-adwaita', name: 'GNOME Adwaita Minimal', desc: 'Clean, snappy 160ms scale and opacity fade' },
    { id: 'slide-drop', name: 'Slide & Drop', desc: 'Cascades down smoothly from top panel' },
    { id: 'elastic-spring', name: 'Bouncy Elastic Spring', desc: 'Playful bouncy spring physics overshoot' }
  ];

  const EASING_PRESETS = [
    { id: 'fluid-spring', name: 'Fluid Spring', css: 'cubic-bezier(0.16, 1, 0.3, 1)' },
    { id: 'smooth-ease', name: 'Natural Smooth', css: 'cubic-bezier(0.25, 0.8, 0.25, 1)' },
    { id: 'elastic-bounce', name: 'Elastic Bounce', css: 'cubic-bezier(0.34, 1.45, 0.64, 1)' },
    { id: 'snappy-expo', name: 'Snappy Exponential', css: 'cubic-bezier(0.05, 0.9, 0.1, 1)' },
    { id: 'linear', name: 'Linear Uniform', css: 'linear' }
  ];

  const DURATION_PRESETS = [
    { ms: 150, label: 'Ultra Fast (150ms)' },
    { ms: 240, label: 'Snappy (240ms)' },
    { ms: 320, label: 'Balanced (320ms)' },
    { ms: 450, label: 'Smooth (450ms)' },
    { ms: 650, label: 'Cinematic (650ms)' }
  ];

  const cfgSettings = (window.OmniOSConfig && window.OmniOSConfig.settings) || {};
  const cfgPinned = (window.OmniOSConfig && window.OmniOSConfig.pinnedApps) || ['files', 'webapps', 'notes', 'tasks', 'calculator', 'terminal', 'monitor', 'settings'];

  const DEFAULT_PREFS = Object.assign({
    username: 'user',
    serverUrl: '',
    os: 'macos',
    mode: 'dark',
    wallpaper: 'macos',
    customWallpaper: '',
    autoWallpaper: true,
    font: 'os-default',
    fontSize: 14,
    resolution: 'native',
    desktopRatio: 'auto',
    accent: '#0a84ff',
    blur: 24,
    nightLight: false,
    autoHideTaskbar: false,
    showDesktopIcons: true,
    pinnedApps: cfgPinned,
    motion: 'full',
    animDuration: 320,
    animPreset: 'macos-scale',
    animEasing: 'fluid-spring',
    dockBounce: true,
    lowMemoryMode: false,
    sound: false,
    wifi: true,
    keepFullscreen: false,
    brightness: 100,
    volume: 70
  }, cfgSettings);

  function getActiveUsername() {
    try { return localStorage.getItem(ACTIVE_USER_KEY) || 'user'; } catch (_) { return 'user'; }
  }

  function prefsKey(u) {
    return 'omnios_prefs_' + (u || 'user').toLowerCase().replace(/[^a-z0-9_-]/g, '_');
  }

  function loadPrefs(username) {
    const base = Object.assign({}, DEFAULT_PREFS, { username });
    try {
      const saved = JSON.parse(localStorage.getItem(prefsKey(username)) || 'null');
      if (saved && typeof saved === 'object') return Object.assign(base, saved, { username });
    } catch (_) {}
    return base;
  }

  let prefs = loadPrefs(getActiveUsername());
  let audioCtx = null;

  // Listen for real online/offline events
  window.addEventListener('online', () => applyToDOM(false));
  window.addEventListener('offline', () => applyToDOM(false));
  document.addEventListener('fullscreenchange', () => applyToDOM(false));

  function playTone(freq = 520, duration = 0.06) {
    if (!prefs.sound || prefs.volume === 0) return;
    try {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      if (!audioCtx) audioCtx = new AC();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, audioCtx.currentTime);
      gain.gain.setValueAtTime(0.035 * (prefs.volume / 100), audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + duration);
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start();
      osc.stop(audioCtx.currentTime + duration);
    } catch (_) {}
  }

  function savePrefs() {
    try {
      localStorage.setItem(ACTIVE_USER_KEY, prefs.username);
      localStorage.setItem(prefsKey(prefs.username), JSON.stringify(prefs));
    } catch (_) {}
  }

  function hexToRgba(hex, alpha) {
    const clean = (hex || '#0a84ff').replace('#', '');
    if (clean.length !== 6) return `rgba(10,132,255,${alpha})`;
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    return `rgba(${r},${g},${b},${alpha})`;
  }

  function swapWallpaper() {
    const img = document.getElementById('wallpaper-img');
    if (!img) return;
    let targetSrc = '';
    if (prefs.wallpaper === 'custom' && prefs.customWallpaper) {
      targetSrc = prefs.customWallpaper;
    } else {
      const wp = WALLPAPERS.find((w) => w.id === prefs.wallpaper) || WALLPAPERS[0];
      targetSrc = wp.src;
    }
    if (img.dataset.currentSrc === targetSrc) return;
    img.classList.add('swapping');
    setTimeout(() => {
      img.src = targetSrc;
      img.dataset.currentSrc = targetSrc;
      img.classList.remove('swapping');
    }, 140);
  }

  function applyToDOM(announce = false) {
    const html = document.documentElement;
    html.setAttribute('data-os', prefs.os);
    html.setAttribute('data-mode', prefs.mode);
    html.setAttribute('data-wallpaper', prefs.wallpaper);
    html.setAttribute('data-motion', prefs.motion);
    html.setAttribute('data-font', prefs.font || 'os-default');
    html.setAttribute('data-desktop-icons', String(prefs.showDesktopIcons !== false));
    html.setAttribute('data-autohide', String(Boolean(prefs.autoHideTaskbar)));
    html.setAttribute('data-low-memory', String(Boolean(prefs.lowMemoryMode)));
    html.setAttribute('data-desktop-ratio', prefs.desktopRatio || 'auto');
    html.setAttribute('data-anim-preset', prefs.animPreset || 'macos-scale');
    html.setAttribute('data-dock-bounce', String(Boolean(prefs.dockBounce !== false)));

    const easingMap = {
      'fluid-spring': 'cubic-bezier(0.16, 1, 0.3, 1)',
      'smooth-ease': 'cubic-bezier(0.25, 0.8, 0.25, 1)',
      'elastic-bounce': 'cubic-bezier(0.34, 1.45, 0.64, 1)',
      'snappy-expo': 'cubic-bezier(0.05, 0.9, 0.1, 1)',
      'linear': 'linear'
    };
    const easingVal = easingMap[prefs.animEasing] || easingMap['fluid-spring'];
    const durVal = (prefs.lowMemoryMode ? 140 : (prefs.animDuration || 320)) + 'ms';

    html.style.setProperty('--anim-duration', durVal);
    html.style.setProperty('--anim-easing', easingVal);

    html.style.setProperty('--ui-font-size', (prefs.fontSize || 14) + 'px');
    html.style.setProperty('--blur-strength', (prefs.lowMemoryMode ? 0 : (prefs.blur ?? 24)) + 'px');
    html.style.setProperty('--accent', prefs.accent || '#0a84ff');
    html.style.setProperty('--accent-hover', prefs.accent || '#0a84ff');
    html.style.setProperty('--accent-soft', hexToRgba(prefs.accent || '#0a84ff', 0.2));

    const resObj = RESOLUTIONS.find((r) => r.id === prefs.resolution) || RESOLUTIONS[0];
    document.body.style.zoom = String(resObj.zoom);

    swapWallpaper();

    const wpLayer = document.getElementById('wallpaper-layer');
    if (wpLayer) wpLayer.style.filter = `brightness(${prefs.brightness}%)`;

    document.getElementById('night-light-overlay')?.classList.toggle('hidden', !prefs.nightLight);

    const meta = OS_META[prefs.os] || OS_META.macos;
    const brandIcon = document.getElementById('top-brand-icon');
    const brandLabel = document.getElementById('top-brand-label');
    const launcherIcon = document.getElementById('launcher-icon');
    const launcherBadge = document.getElementById('launcher-os-badge');
    const ccTitle = document.getElementById('cc-panel-title');
    const ncTitle = document.getElementById('nc-title');
    const luName = document.getElementById('launcher-username');
    const luAv = document.getElementById('launcher-avatar');

    if (brandIcon) brandIcon.innerHTML = meta.brandSvg;
    if (brandLabel) brandLabel.textContent = meta.brandLabel;
    if (launcherIcon) launcherIcon.innerHTML = meta.launcherSvg;
    if (launcherBadge) launcherBadge.textContent = meta.name;
    if (ccTitle) ccTitle.textContent = meta.panelTitle;
    if (ncTitle) ncTitle.textContent = meta.notifTitle;
    if (luName) luName.textContent = prefs.username;
    if (luAv) luAv.textContent = (prefs.username || 'U').slice(0, 2).toUpperCase();

    const syncToggle = (id, active, subId, text) => {
      const btn = document.getElementById(id);
      const sub = document.getElementById(subId);
      if (btn) btn.classList.toggle('active', active);
      if (sub) sub.textContent = text;
    };

    const isOnline = Boolean(prefs.wifi && (typeof navigator.onLine === 'boolean' ? navigator.onLine : true));
    syncToggle('cc-wifi-toggle', isOnline, 'cc-wifi-text', isOnline ? 'Online' : 'Offline');
    syncToggle('cc-mode-toggle', prefs.mode === 'dark', 'cc-mode-text', prefs.mode === 'dark' ? 'Dark' : 'Light');
    syncToggle('cc-night-toggle', prefs.nightLight, 'cc-night-text', prefs.nightLight ? 'Warm' : 'Off');
    syncToggle('cc-motion-toggle', prefs.motion === 'full', 'cc-motion-text', prefs.motion === 'full' ? 'Fluid' : 'Reduced');
    syncToggle('cc-sound-toggle', prefs.sound, 'cc-sound-text', prefs.sound ? 'On' : 'Muted');
    const isFs = Boolean(document.fullscreenElement);
    syncToggle('cc-fullscreen-toggle', isFs, 'cc-fs-text', isFs ? 'On' : 'Off');

    const hiddenFsBtn = document.getElementById('hidden-fullscreen-btn');
    if (hiddenFsBtn) {
      hiddenFsBtn.classList.toggle('active', isFs || prefs.keepFullscreen);
      hiddenFsBtn.textContent = (isFs || prefs.keepFullscreen) ? '⛶ Fullscreen Active' : '⛶ Keep in Full Screen';
    }

    const brIn = document.getElementById('cc-brightness');
    const brVal = document.getElementById('cc-brightness-val');
    if (brIn) brIn.value = prefs.brightness;
    if (brVal) brVal.textContent = prefs.brightness + '%';

    const volIn = document.getElementById('cc-volume');
    const volVal = document.getElementById('cc-volume-val');
    if (volIn) volIn.value = prefs.volume;
    if (volVal) volVal.textContent = prefs.volume + '%';

    if (announce && OmniOS.notify) {
      OmniOS.notify('Theme Updated', `Active theme set to ${meta.name}.`);
    }
    window.dispatchEvent(new CustomEvent('omnios:themechange', { detail: { ...prefs } }));
  }

  OmniOS.themes = {
    OS_META,
    WALLPAPERS,
    FONTS,
    RESOLUTIONS,
    VIEWPORT_RATIOS,
    getPrefs() { return { ...prefs, pinnedApps: [...(prefs.pinnedApps || [])] }; },
    playTone,

    setOS(osId, announce = true) {
      if (!OS_META[osId]) return;
      prefs.os = osId;
      prefs.accent = OS_META[osId].defaultAccent;
      if (prefs.autoWallpaper) prefs.wallpaper = OS_META[osId].defaultWallpaper;
      savePrefs();
      playTone(640, 0.07);
      applyToDOM(announce);
    },

    update(patch) {
      prefs = Object.assign({}, prefs, patch);
      savePrefs();
      applyToDOM(false);
    },

    togglePin(appId) {
      const list = new Set(prefs.pinnedApps || []);
      const app = (OmniOS.apps && OmniOS.apps.catalog) ? OmniOS.apps.catalog.find((a) => a.id === appId) : null;
      const appName = app ? app.name : appId;
      if (list.has(appId)) {
        list.delete(appId);
        const isRunning = OmniOS.wm && OmniOS.wm.isAppRunning(appId);
        if (OmniOS.toast) {
          OmniOS.toast(isRunning ? `🚫 "${appName}" unpinned (will hide when closed)` : `🚫 "${appName}" unpinned from taskbar`);
        }
      } else {
        list.add(appId);
        if (OmniOS.toast) OmniOS.toast(`📌 "${appName}" pinned to taskbar`);
      }
      prefs.pinnedApps = Array.from(list);
      savePrefs();
      applyToDOM(false);
    },

    isPinned(appId) {
      return (prefs.pinnedApps || []).includes(appId);
    },

    setWallpaper(wpId, customDataUrl = '') {
      if (wpId === 'custom' && customDataUrl) {
        prefs.wallpaper = 'custom';
        prefs.customWallpaper = customDataUrl;
        prefs.autoWallpaper = false;
      } else if (WALLPAPERS.some((w) => w.id === wpId)) {
        prefs.wallpaper = wpId;
        prefs.autoWallpaper = false;
      }
      savePrefs();
      applyToDOM(false);
    },

    switchUser(username) {
      const clean = (username || '').trim().replace(/[^a-zA-Z0-9_-]/g, '') || 'user';
      prefs = loadPrefs(clean);
      savePrefs();
      if (OmniOS.vfs) OmniOS.vfs.reloadForUser();
      applyToDOM(false);
      if (OmniOS.notify) OmniOS.notify('Profile Switched', `Active user workspace: ${clean}`);
    },

    toggleMode() { this.update({ mode: prefs.mode === 'dark' ? 'light' : 'dark' }); },

    toggleLowMemory() {
      this.update({ lowMemoryMode: !prefs.lowMemoryMode });
    },

    togglePref(key) {
      if (key === 'motion') this.update({ motion: prefs.motion === 'full' ? 'reduced' : 'full' });
      else if (key === 'lowMemoryMode') this.toggleLowMemory();
      else this.update({ [key]: !prefs[key] });
      if (key === 'sound' && prefs.sound) playTone(700, 0.06);
    },

    toggleFullscreen(keep = null) {
      if (keep !== null) prefs.keepFullscreen = Boolean(keep);
      else prefs.keepFullscreen = !prefs.keepFullscreen;
      try {
        if (!document.fullscreenElement) {
          document.documentElement.requestFullscreen?.().catch(() => {});
        } else {
          document.exitFullscreen?.().catch(() => {});
        }
      } catch (_) {}
      savePrefs();
      setTimeout(() => applyToDOM(false), 120);
    },

    ANIMATION_PRESETS,
    EASING_PRESETS,
    DURATION_PRESETS,

    setAnimPreset(preset) {
      if (ANIMATION_PRESETS.some((a) => a.id === preset)) {
        this.update({ animPreset: preset });
      }
    },

    setAnimDuration(ms) {
      const val = Math.max(100, Math.min(800, Number(ms) || 320));
      this.update({ animDuration: val });
    },

    setAnimEasing(easing) {
      if (EASING_PRESETS.some((e) => e.id === easing)) {
        this.update({ animEasing: easing });
      }
    },

    setDockBounce(enabled) {
      this.update({ dockBounce: Boolean(enabled) });
    },

    init() {
      applyToDOM(false);
      if (prefs.keepFullscreen && !document.fullscreenElement) {
        const onFirstInteract = () => {
          if (prefs.keepFullscreen && !document.fullscreenElement) {
            document.documentElement.requestFullscreen?.().catch(() => {});
          }
          window.removeEventListener('click', onFirstInteract);
        };
        window.addEventListener('click', onFirstInteract, { once: true });
      }
    }
  };
})(window.OmniOS);
