/**
 * OmniOS - Server-Driven UI (SDUI) Client Engine
 * Lightweight rendering client: all application governance, window authority,
 * file sandboxing, and SyncPlay clocks execute strictly on the Android Host.
 */

window.OmniOS = window.OmniOS || {};

(function (OmniOS) {
  'use strict';

  const OMNIOS_APP_CATALOG = [
    { id: 'files', name: 'File Manager', desktop: true },
    { id: 'markdown_studio', name: 'Markdown Studio', desktop: true },
    { id: 'code_studio', name: 'Code Studio', desktop: true },
    { id: 'table_studio', name: 'Table Studio', desktop: true },
    { id: 'photo_studio', name: 'Photo Studio', desktop: true },
    { id: 'file_converter', name: 'File Converter', desktop: true },
    { id: 'syncplay', name: 'SyncPlay', desktop: true },
    { id: 'video', name: 'Video Player', desktop: false },
    { id: 'music', name: 'Music', desktop: false },
    { id: 'pdf', name: 'PDF Reader', desktop: false },
    { id: 'terminal', name: 'Terminal', desktop: false },
    { id: 'sysmon', name: 'System Monitor', desktop: true },
    { id: 'settings', name: 'Settings', desktop: true }
  ];

  class SDUIClient {
    constructor() {
      this.ws = null;
      this.connected = false;
      this.reconnectAttempts = 0;
      this.maxReconnectDelay = 5000;
      this.heartbeatTimer = null;
      this.lastRtt = 0;
      this.syncPlaySockets = new Map();

      this.serverState = {
        windows: {},
        activeApp: null,
        notifications: [],
        quickPanel: { wifi: true, bluetooth: true, volume: 80, brightness: 100, darkMode: true },
        adminConfig: {
          touchInputEnabled: true,
          allowedApps: OMNIOS_APP_CATALOG.map(a => a.id),
          settingsVisibility: { appearance: true, animation: true, sound: true, storage: true, host: true },
          theme: 'dex',
          audioRoute: 'receiver',
          brightness: 100,
          darkMode: true,
          animationDuration: 300
        },
        systemStats: { cpu: 12, ram: '2.4 / 8.0 GB', temp: 35, battery: 95 },
        syncPlay: null
      };

      this.touchController = null;
      this.dom = {
        desktop: null,
        windowContainer: null,
        dock: null,
        topbar: null,
        quickPanel: null,
        notificationCenter: null,
        disconnectBarrier: null,
        rttBadge: null
      };

      this.windowDragState = null;
    }

    init() {
      this.cacheDom();
      this.detectEnvironment();
      this.initTouch();
      this.initEventListeners();
      this.updateTopbarLogo();
      this.applyBrightness(this.serverState.quickPanel.brightness || 100);
      this.applyDarkMode(this.serverState.quickPanel.darkMode !== false);
      this.connect();
    }

    detectEnvironment() {
      const params = new URLSearchParams(window.location.search);
      const isReceiver = params.get('mode') === 'receiver' ||
                         params.get('receiver') === 'true' ||
                         params.get('cast') === 'true' ||
                         window.AndroidReceiverBridge !== undefined ||
                         window.matchMedia('(display-mode: standalone)').matches;

      if (isReceiver) {
        document.body.classList.add('receiver-mode');
      }

      document.addEventListener('fullscreenchange', () => {
        document.body.classList.toggle('fullscreen-active', Boolean(document.fullscreenElement));
      });
    }

    updateTopbarLogo() {
      const trigger = document.getElementById('os-menu-trigger');
      if (trigger && OmniOS.icons && typeof OmniOS.icons.getSystemLogo === 'function') {
        const theme = this.serverState.adminConfig.theme || 'dex';
        trigger.innerHTML = OmniOS.icons.getSystemLogo(theme, 18);
      }
    }

    applyBrightness(val) {
      const b = Math.max(15, Math.min(100, val || 100));
      this.serverState.quickPanel.brightness = b;
      document.documentElement.style.setProperty('--display-brightness', b);
      if (this.dom.desktop) {
        this.dom.desktop.style.filter = `brightness(${b / 100})`;
      }
    }

    applyDarkMode(enabled) {
      const isDark = Boolean(enabled);
      this.serverState.quickPanel.darkMode = isDark;
      document.documentElement.setAttribute('data-mode', isDark ? 'dark' : 'light');
      document.body.classList.toggle('dark-mode', isDark);
      document.body.classList.toggle('light-mode', !isDark);
    }

    cacheDom() {
      this.dom.desktop = document.getElementById('desktop');
      this.dom.windowContainer = document.getElementById('window-container');
      this.dom.dock = document.getElementById('dock');
      this.dom.topbar = document.getElementById('topbar');
      this.dom.quickPanel = document.getElementById('quick-panel');
      this.dom.notificationCenter = document.getElementById('notification-center');
      this.dom.disconnectBarrier = document.getElementById('omnios-disconnect-barrier');
      this.dom.rttBadge = document.getElementById('rtt-status-badge');
    }

    initTouch() {
      if (OmniOS.TouchController) {
        this.touchController = new OmniOS.TouchController();
        this.touchController.init(document.getElementById('omnios-touch-plane'), (data) => {
          this.sendRaw(data);
        });
      }
    }

    initEventListeners() {
      window.addEventListener('pointermove', (e) => this.handleWindowPointerMove(e));
      window.addEventListener('pointerup', (e) => this.handleWindowPointerUp(e));
      this.startClock();

      document.getElementById('quick-panel-trigger')?.addEventListener('click', (e) => {
        e.stopPropagation();
        this.toggleQuickPanel();
      });

      document.getElementById('notif-center-trigger')?.addEventListener('click', (e) => {
        e.stopPropagation();
        this.toggleNotificationCenter();
      });

      this.dom.desktop?.addEventListener('click', () => this.closePopups());

      document.getElementById('topbar-fullscreen-btn')?.addEventListener('click', () => {
        if (!document.fullscreenElement) {
          document.documentElement.requestFullscreen?.().catch(() => {});
        } else {
          document.exitFullscreen?.().catch(() => {});
        }
      });
    }

    startClock() {
      const clockEl = document.getElementById('topbar-clock');
      const update = () => {
        if (!clockEl) return;
        const now = new Date();
        clockEl.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      };
      update();
      setInterval(update, 1000);
    }

    /* ==========================================================================
       WebSocket Connection & Reconnection Management
       ========================================================================== */

    connect() {
      const params = new URLSearchParams(window.location.search);
      const token = params.get('token') || '';
      const deviceName = params.get('name') || '';
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = window.location.host || 'localhost:8080';
      const qs = new URLSearchParams();
      if (token) qs.set('token', token);
      if (deviceName) qs.set('name', deviceName);
      const querySuffix = qs.toString() ? `?${qs.toString()}` : '';
      const wsUrl = `${protocol}//${host}/ws/sdui${querySuffix}`;

      this.showDisconnectBarrier(true, 'Connecting to OmniOS Host...');

      try {
        this.ws = new WebSocket(wsUrl);
        this.ws.binaryType = 'arraybuffer';

        this.ws.onopen = () => {
          this.connected = true;
          this.reconnectAttempts = 0;
          this.startHeartbeat();
          this.send(OmniOSProtocol.OPCODES.SYNC_REQUEST, { clientTime: Date.now() });
        };

        this.ws.onmessage = (event) => {
          const msg = OmniOSProtocol.unpack(event.data);
          this.handleServerMessage(msg);
        };

        this.ws.onclose = () => this.handleDisconnect();
        this.ws.onerror = () => this.handleDisconnect();
      } catch (_) {
        this.handleDisconnect();
      }
    }

    handleDisconnect() {
      this.connected = false;
      this.stopHeartbeat();
      const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), this.maxReconnectDelay);
      this.showDisconnectBarrier(true, `Reconnecting to OmniOS Host in ${(delay / 1000).toFixed(1)}s...`);
      this.reconnectAttempts++;
      setTimeout(() => {
        if (!this.connected) this.connect();
      }, delay);
    }

    showDisconnectBarrier(show, message = 'Connection Lost') {
      if (!this.dom.disconnectBarrier) return;
      const statusText = document.getElementById('disconnect-status-text');
      if (statusText) statusText.textContent = message;

      if (show) {
        this.dom.disconnectBarrier.classList.remove('hidden');
        document.body.classList.add('server-disconnected');
      } else {
        this.dom.disconnectBarrier.classList.add('hidden');
        document.body.classList.remove('server-disconnected');
      }
    }

    startHeartbeat() {
      this.stopHeartbeat();
      this.heartbeatTimer = setInterval(() => {
        if (this.connected && this.ws && this.ws.readyState === WebSocket.OPEN) {
          this.send(OmniOSProtocol.OPCODES.HEARTBEAT, { ts: Date.now() });
        }
      }, 3000);
    }

    stopHeartbeat() {
      if (this.heartbeatTimer) {
        clearInterval(this.heartbeatTimer);
        this.heartbeatTimer = null;
      }
    }

    send(opcode, payload) {
      if (!this.connected || !this.ws || this.ws.readyState !== WebSocket.OPEN) return;
      this.ws.send(OmniOSProtocol.packJson(opcode, payload));
    }

    sendRaw(buffer) {
      if (!this.connected || !this.ws || this.ws.readyState !== WebSocket.OPEN) return;
      this.ws.send(buffer);
    }

    /* ==========================================================================
       Message Dispatcher & Differential Patch Reconciler
       ========================================================================== */

    handleServerMessage(msg) {
      if (!msg) return;

      switch (msg.op) {
        case OmniOSProtocol.OPCODES.FULL_SYNC:
          this.showDisconnectBarrier(false);
          this.applyFullSync(msg.state);
          break;

        case OmniOSProtocol.OPCODES.STATE_DELTA:
          this.applyStateDelta(msg.delta);
          break;

        case OmniOSProtocol.OPCODES.ADMIN_CONFIG:
          this.applyAdminConfig(msg.config);
          break;

        case OmniOSProtocol.OPCODES.DISCONNECT_BARRIER:
          this.showDisconnectBarrier(true, msg.message || 'Waiting for Host Approval...');
          break;

        case OmniOSProtocol.OPCODES.HEARTBEAT_ACK:
          this.lastRtt = Math.max(0, Date.now() - (msg.ts || Date.now()));
          this.updateRttBadge();
          break;

        default:
          break;
      }
    }

    updateRttBadge() {
      if (!this.dom.rttBadge) return;
      const quality = this.lastRtt < 25 ? 'optimal' : this.lastRtt < 70 ? 'good' : 'fair';
      this.dom.rttBadge.innerHTML = `<span class="rtt-dot ${quality}"></span><span>RTT: ${this.lastRtt}ms</span>`;
    }

    applyFullSync(state) {
      this.serverState = { ...this.serverState, ...state };
      if (state.adminConfig) {
        this.applyAdminConfig(state.adminConfig);
      }
      this.renderDock();
      this.renderWindows();
      this.renderNotifications();
      this.renderQuickPanel();
      this.renderDesktopShortcuts();
    }

    applyStateDelta(delta) {
      if (!Array.isArray(delta)) return;

      for (const patch of delta) {
        const { type, entity, id, data } = patch;
        if (entity === 'window') {
          if (type === 'add' || type === 'update') {
            this.serverState.windows[id] = { ...(this.serverState.windows[id] || {}), ...data };
            this.patchWindowDom(id, this.serverState.windows[id]);
          } else if (type === 'remove') {
            delete this.serverState.windows[id];
            this.removeWindowDom(id);
          }
        } else if (entity === 'notification') {
          if (type === 'add') {
            this.serverState.notifications.unshift(data);
            this.renderNotifications();
            this.showNotificationToast(data);
          } else if (type === 'remove') {
            this.serverState.notifications = this.serverState.notifications.filter(n => n.id !== id);
            this.renderNotifications();
          }
        } else if (entity === 'quickPanel') {
          this.serverState.quickPanel = { ...this.serverState.quickPanel, ...data };
          this.renderQuickPanel();
        } else if (entity === 'systemStats') {
          this.serverState.systemStats = { ...this.serverState.systemStats, ...data };
          this.updateSystemMonitorView();
        }
      }

      this.updateDockRunningIndicators();
    }

    applyAdminConfig(config) {
      this.serverState.adminConfig = { ...this.serverState.adminConfig, ...config };

      if (this.touchController) {
        this.touchController.setTouchEnabled(this.serverState.adminConfig.touchInputEnabled);
      }

      if (config.customIcons && typeof config.customIcons === 'object') {
        for (const [id, svg] of Object.entries(config.customIcons)) {
          OmniOS.icons?.setCustomIcon?.(id, svg);
        }
      }

      if (this.serverState.adminConfig.theme) {
        const theme = this.serverState.adminConfig.theme;
        document.documentElement.setAttribute('data-os', theme);
        document.documentElement.setAttribute('data-theme', theme);
        document.body.setAttribute('data-theme', theme);
        this.updateTopbarLogo();
      }

      if (this.serverState.adminConfig.animationDuration !== undefined) {
        document.documentElement.style.setProperty('--os-anim-duration', `${this.serverState.adminConfig.animationDuration}ms`);
      }

      if (this.serverState.adminConfig.brightness !== undefined) {
        this.applyBrightness(this.serverState.adminConfig.brightness);
      }

      if (this.serverState.adminConfig.darkMode !== undefined) {
        this.applyDarkMode(this.serverState.adminConfig.darkMode);
      }

      this.renderDock();
      this.renderDesktopShortcuts();
      this.patchOpenSettingsWindow();
    }

    /* ==========================================================================
       Window Management & Differential DOM Patching
       ========================================================================== */

    renderWindows() {
      if (!this.dom.windowContainer) return;
      const currentWinIds = new Set(Object.keys(this.serverState.windows));

      this.dom.windowContainer.querySelectorAll('.os-window').forEach(el => {
        const winId = el.getAttribute('data-win-id');
        if (!currentWinIds.has(winId)) {
          this.cleanupWindowResources(winId);
          el.remove();
        }
      });

      for (const [winId, win] of Object.entries(this.serverState.windows)) {
        this.patchWindowDom(winId, win);
      }
    }

    patchWindowDom(winId, win) {
      if (!this.dom.windowContainer) return;
      let winEl = this.dom.windowContainer.querySelector(`[data-win-id="${winId}"]`);

      if (!winEl) {
        winEl = this.createWindowShell(winId, win);
        this.dom.windowContainer.appendChild(winEl);
      }

      winEl.style.left = `${win.x}px`;
      winEl.style.top = `${win.y}px`;
      winEl.style.width = `${win.width}px`;
      winEl.style.height = `${win.height}px`;
      winEl.style.zIndex = win.zIndex || 10;

      winEl.classList.toggle('minimized', Boolean(win.isMinimized));
      winEl.classList.toggle('maximized', Boolean(win.isMaximized));
      winEl.classList.toggle('active', Boolean(win.isFocused));

      // Lock close button if host marked window non-closable (e.g., active SyncPlay)
      const closeBtn = winEl.querySelector('.win-close');
      if (closeBtn) {
        const isClosable = win.closable !== false;
        closeBtn.style.display = isClosable ? '' : 'none';
        closeBtn.disabled = !isClosable;
      }

      const titleText = winEl.querySelector('.window-title-text');
      if (titleText && win.title) titleText.textContent = win.title;

      const bodyEl = winEl.querySelector('.window-content');
      if (bodyEl && (!bodyEl.hasChildNodes() || win.needsContentRefresh)) {
        this.renderAppContent(win.appId, winId, bodyEl, win.appData || {});
        win.needsContentRefresh = false;
      }
    }

    createWindowShell(winId, win) {
      const el = document.createElement('div');
      el.className = 'os-window glass-card';
      el.setAttribute('data-win-id', winId);
      el.setAttribute('data-app-id', win.appId);
      const isClosable = win.closable !== false;

      el.innerHTML = `
        <div class="window-titlebar">
          <div class="window-controls">
            <button class="win-btn win-close" title="Close" ${isClosable ? '' : 'style="display:none" disabled'}></button>
            <button class="win-btn win-min" title="Minimize"></button>
            <button class="win-btn win-max" title="Maximize"></button>
          </div>
          <div class="window-title">
            <span class="window-icon">${this.getAppIconSvg(win.appId, 16)}</span>
            <span class="window-title-text">${this.escapeHtml(win.title || win.appId)}</span>
          </div>
          <div class="window-actions-extra">${isClosable ? '' : '<span style="font-size:10px;opacity:0.75;padding:2px 8px;border-radius:999px;background:rgba(239,68,68,0.22);color:#fca5a5;">Host Locked</span>'}</div>
        </div>
        <div class="window-content"></div>
        <div class="window-resize-handle"></div>
      `;

      el.addEventListener('pointerdown', (e) => {
        e.stopPropagation();
        this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'focus', winId });
      });

      el.querySelector('.win-close')?.addEventListener('click', (e) => {
        e.stopPropagation();
        if (this.serverState.windows[winId]?.closable === false) return;
        this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'close', winId });
      });

      el.querySelector('.win-min')?.addEventListener('click', (e) => {
        e.stopPropagation();
        this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'minimize', winId });
      });

      el.querySelector('.win-max')?.addEventListener('click', (e) => {
        e.stopPropagation();
        this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'maximize', winId });
      });

      const titlebar = el.querySelector('.window-titlebar');
      titlebar?.addEventListener('pointerdown', (e) => {
        if (e.target.closest('.win-btn')) return;
        if (el.classList.contains('maximized')) return;
        e.preventDefault();
        this.startWindowDrag(e, winId, el, 'move');
      });

      el.querySelector('.window-resize-handle')?.addEventListener('pointerdown', (e) => {
        if (el.classList.contains('maximized')) return;
        e.preventDefault();
        e.stopPropagation();
        this.startWindowDrag(e, winId, el, 'resize');
      });

      return el;
    }

    cleanupWindowResources(winId) {
      const spSocket = this.syncPlaySockets.get(winId);
      if (spSocket) {
        try { spSocket.close(); } catch (_) {}
        this.syncPlaySockets.delete(winId);
      }
    }

    removeWindowDom(winId) {
      this.cleanupWindowResources(winId);
      const el = this.dom.windowContainer?.querySelector(`[data-win-id="${winId}"]`);
      if (el) {
        el.classList.add('closing');
        setTimeout(() => el.remove(), 220);
      }
    }

    startWindowDrag(e, winId, winEl, mode) {
      const rect = winEl.getBoundingClientRect();
      this.windowDragState = {
        winId, winEl, mode,
        startX: e.clientX, startY: e.clientY,
        initialX: rect.left, initialY: rect.top,
        initialW: rect.width, initialH: rect.height,
        lastSent: 0
      };
      winEl.classList.add('dragging');
    }

    handleWindowPointerMove(e) {
      if (!this.windowDragState) return;
      const s = this.windowDragState;
      const dx = e.clientX - s.startX;
      const dy = e.clientY - s.startY;
      const now = Date.now();

      if (s.mode === 'move') {
        const newX = Math.max(0, s.initialX + dx);
        const newY = Math.max(30, s.initialY + dy);
        s.winEl.style.left = `${newX}px`;
        s.winEl.style.top = `${newY}px`;
        if (now - s.lastSent > 33) {
          s.lastSent = now;
          this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'move', winId: s.winId, x: Math.round(newX), y: Math.round(newY) });
        }
      } else if (s.mode === 'resize') {
        const newW = Math.max(320, s.initialW + dx);
        const newH = Math.max(220, s.initialH + dy);
        s.winEl.style.width = `${newW}px`;
        s.winEl.style.height = `${newH}px`;
        if (now - s.lastSent > 33) {
          s.lastSent = now;
          this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'resize', winId: s.winId, width: Math.round(newW), height: Math.round(newH) });
        }
      }
    }

    handleWindowPointerUp() {
      if (!this.windowDragState) return;
      const s = this.windowDragState;
      s.winEl.classList.remove('dragging');
      const rect = s.winEl.getBoundingClientRect();
      this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, {
        action: s.mode === 'move' ? 'move' : 'resize',
        winId: s.winId,
        x: Math.round(rect.left),
        y: Math.round(rect.top),
        width: Math.round(rect.width),
        height: Math.round(rect.height)
      });
      this.windowDragState = null;
    }

    /* ==========================================================================
       Application Component Renderer (My Suite Mini-Apps + OmniOS Core Apps)
       ========================================================================== */

    renderAppContent(appId, winId, container, appData = {}) {
      container.innerHTML = '';

      switch (appId) {
        case 'files': this.renderFileManagerApp(winId, container, appData); break;
        case 'markdown_studio': this.renderMarkdownStudioApp(winId, container, appData); break;
        case 'code_studio': this.renderCodeStudioApp(winId, container, appData); break;
        case 'table_studio': this.renderTableStudioApp(winId, container, appData); break;
        case 'photo_studio': this.renderPhotoStudioApp(winId, container, appData); break;
        case 'file_converter': this.renderFileConverterApp(winId, container, appData); break;
        case 'syncplay': this.renderSyncPlayApp(winId, container, appData); break;
        case 'video': this.renderVideoPlayerApp(winId, container, appData); break;
        case 'music': this.renderAudioPlayerApp(winId, container, appData); break;
        case 'pdf': this.renderPdfViewerApp(winId, container, appData); break;
        case 'terminal': this.renderTerminalApp(winId, container, appData); break;
        case 'sysmon': this.renderSystemMonitorApp(winId, container, appData); break;
        case 'settings': this.renderSettingsApp(winId, container, appData); break;
        default:
          container.innerHTML = `<div class="p-6 text-center text-muted">Application unavailable: ${this.escapeHtml(appId)}</div>`;
      }
    }

    // 1. File Manager (Live Host-Shared Sandbox + Download + Open in My Suite)
    renderFileManagerApp(winId, container, appData) {
      let currentPath = appData.currentPath || '';
      let parentPath = '';

      container.innerHTML = `
        <div class="file-manager-root" style="display:flex;flex-direction:column;height:100%;">
          <div class="fm-toolbar" style="display:flex;align-items:center;gap:8px;padding:10px 12px;border-bottom:1px solid rgba(255,255,255,0.08);">
            <button class="fm-btn fm-back os-btn small">‹ Back</button>
            <div class="fm-path-bar" style="flex:1;font-size:12px;opacity:0.85;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">Shared Files</div>
            <button class="fm-btn fm-refresh os-btn small">↻ Refresh</button>
          </div>
          <div class="fm-grid" style="flex:1;overflow:auto;padding:12px;"></div>
        </div>
      `;

      const pathBar = container.querySelector('.fm-path-bar');
      const grid = container.querySelector('.fm-grid');
      const backBtn = container.querySelector('.fm-back');
      const refreshBtn = container.querySelector('.fm-refresh');

      const loadDirectory = async (dirPath) => {
        grid.innerHTML = `<div style="padding:24px;text-align:center;opacity:0.7;font-size:13px;">Loading host-shared files...</div>`;
        try {
          const url = dirPath ? `/api/files/list?path=${encodeURIComponent(dirPath)}` : '/api/files/list';
          const res = await fetch(url);
          const data = await res.json();
          currentPath = data.currentPath || '';
          parentPath = data.parentPath || '';
          pathBar.textContent = data.displayPath || 'Shared Files';
          backBtn.disabled = !currentPath;

          const files = data.files || [];
          if (files.length === 0) {
            grid.innerHTML = `<div style="padding:36px;text-align:center;opacity:0.65;font-size:13px;">No files shared yet. Select files or folders on the Host Casting tab to enable browsing and downloads.</div>`;
            return;
          }

          grid.innerHTML = files.map(f => `
            <div class="fm-item" data-path="${this.escapeAttr(f.path)}" data-type="${f.type}" data-ext="${this.escapeAttr(f.ext || '')}" style="position:relative;">
              <div class="fm-icon">${f.type === 'folder' ? '📁' : this.getFileTypeIcon(f.ext)}</div>
              <div class="fm-name" title="${this.escapeAttr(f.name)}">${this.escapeHtml(f.name)}</div>
              <div class="fm-size">${this.escapeHtml(f.size || '')}</div>
              ${f.type === 'file' && f.downloadUrl ? `<a class="os-btn small fm-dl-btn" href="${this.escapeAttr(f.downloadUrl)}" download="${this.escapeAttr(f.name)}" style="margin-top:6px;display:inline-block;text-decoration:none;font-size:11px;padding:3px 8px;">Download</a>` : ''}
            </div>
          `).join('');

          grid.querySelectorAll('.fm-dl-btn').forEach(a => a.addEventListener('click', (e) => e.stopPropagation()));
          grid.querySelectorAll('.fm-item').forEach(item => {
            item.addEventListener('dblclick', () => {
              const p = item.getAttribute('data-path');
              const t = item.getAttribute('data-type');
              if (t === 'folder') {
                loadDirectory(p);
              } else {
                this.send(OmniOSProtocol.OPCODES.APP_ACTION, {
                  winId,
                  appId: 'files',
                  action: 'open_shared_file',
                  path: p
                });
              }
            });
          });
        } catch (err) {
          grid.innerHTML = `<div style="padding:24px;text-align:center;color:#f87171;font-size:13px;">Could not load host files.</div>`;
        }
      };

      backBtn.addEventListener('click', () => loadDirectory(parentPath));
      refreshBtn.addEventListener('click', () => loadDirectory(currentPath));
      loadDirectory(currentPath);
    }

    getFileTypeIcon(ext) {
      if (['mp4', 'mkv', 'webm', 'mov'].includes(ext)) return '🎬';
      if (['mp3', 'wav', 'flac', 'm4a', 'ogg'].includes(ext)) return '🎵';
      if (['png', 'jpg', 'jpeg', 'webp', 'gif'].includes(ext)) return '🖼️';
      if (ext === 'pdf') return '📄';
      if (['csv', 'tsv'].includes(ext)) return '📊';
      if (['kt', 'java', 'js', 'ts', 'py', 'json', 'html', 'css', 'xml'].includes(ext)) return '💻';
      if (['md', 'txt'].includes(ext)) return '📝';
      return '📄';
    }

    // 2. My Suite — Markdown Studio
    renderMarkdownStudioApp(winId, container, appData) {
      let filePath = appData.filePath || '';
      let fileName = appData.fileName || 'note.md';
      const initialContent = appData.content || '# Markdown Studio\n\nWrite Markdown on any display and save directly to the Android host.\n\n- Live split preview\n- Host file sync\n';

      container.innerHTML = `
        <div style="display:flex;flex-direction:column;height:100%;">
          <div style="display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid rgba(255,255,255,0.08);">
            <input class="md-filename" value="${this.escapeAttr(fileName)}" style="background:rgba(255,255,255,0.07);border:1px solid rgba(255,255,255,0.12);color:inherit;border-radius:6px;padding:4px 8px;font-size:12px;width:180px;" />
            <button class="os-btn small primary md-save-btn">Save to Host</button>
            <span class="md-status" style="font-size:11px;opacity:0.75;margin-left:auto;"></span>
          </div>
          <div style="flex:1;display:grid;grid-template-columns:1fr 1fr;min-height:0;">
            <textarea class="md-editor" style="width:100%;height:100%;resize:none;padding:14px;background:rgba(0,0,0,0.22);color:inherit;border:none;border-right:1px solid rgba(255,255,255,0.08);font-family:monospace;font-size:13px;outline:none;">${this.escapeHtml(initialContent)}</textarea>
            <div class="md-preview" style="padding:16px;overflow:auto;font-size:14px;line-height:1.6;"></div>
          </div>
        </div>
      `;

      const editor = container.querySelector('.md-editor');
      const preview = container.querySelector('.md-preview');
      const nameInput = container.querySelector('.md-filename');
      const statusEl = container.querySelector('.md-status');

      const renderMd = (text) => {
        const safe = this.escapeHtml(text)
          .replace(/^### (.*)$/gm, '<h3>$1</h3>')
          .replace(/^## (.*)$/gm, '<h2>$1</h2>')
          .replace(/^# (.*)$/gm, '<h1>$1</h1>')
          .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
          .replace(/\*(.+?)\*/g, '<em>$1</em>')
          .replace(/`([^`]+)`/g, '<code style="background:rgba(255,255,255,0.12);padding:1px 5px;border-radius:4px;">$1</code>')
          .replace(/^- (.*)$/gm, '<li>$1</li>')
          .replace(/\n/g, '<br/>');
        preview.innerHTML = safe;
      };

      renderMd(editor.value);
      editor.addEventListener('input', () => renderMd(editor.value));

      container.querySelector('.md-save-btn').addEventListener('click', async () => {
        statusEl.textContent = 'Saving...';
        try {
          const res = await fetch('/api/suite/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filePath, fileName: nameInput.value, content: editor.value, tool: 'markdown_studio' })
          });
          const data = await res.json();
          if (data.success) {
            filePath = data.filePath;
            fileName = data.fileName;
            statusEl.textContent = `Saved (${data.fileName})`;
          } else {
            statusEl.textContent = data.error || 'Save failed';
          }
        } catch (_) {
          statusEl.textContent = 'Host unreachable';
        }
      });
    }

    // 3. My Suite — Code Studio
    renderCodeStudioApp(winId, container, appData) {
      let filePath = appData.filePath || '';
      let fileName = appData.fileName || 'script.kt';
      const lang = (appData.language || fileName.split('.').pop() || 'txt').toUpperCase();
      const initialCode = appData.content || '// Code Studio — OmniOS Host Bridge\nfun main() {\n    println("Running from Android Host")\n}\n';

      container.innerHTML = `
        <div style="display:flex;flex-direction:column;height:100%;">
          <div style="display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid rgba(255,255,255,0.08);">
            <input class="code-filename" value="${this.escapeAttr(fileName)}" style="background:rgba(255,255,255,0.07);border:1px solid rgba(255,255,255,0.12);color:inherit;border-radius:6px;padding:4px 8px;font-size:12px;width:190px;" />
            <span style="font-size:11px;padding:2px 8px;border-radius:6px;background:rgba(14,165,233,0.2);color:#7dd3fc;">${this.escapeHtml(lang)}</span>
            <button class="os-btn small primary code-save-btn">Save</button>
            <span class="code-status" style="font-size:11px;opacity:0.75;margin-left:auto;"></span>
          </div>
          <div style="flex:1;display:flex;min-height:0;background:rgba(0,0,0,0.3);">
            <pre class="code-gutter" style="margin:0;padding:12px 10px;text-align:right;opacity:0.45;font-family:monospace;font-size:12px;line-height:1.5;user-select:none;overflow:hidden;"></pre>
            <textarea class="code-editor" spellcheck="false" style="flex:1;resize:none;padding:12px;background:transparent;color:#e2e8f0;border:none;font-family:monospace;font-size:12px;line-height:1.5;outline:none;white-space:pre;overflow:auto;">${this.escapeHtml(initialCode)}</textarea>
          </div>
        </div>
      `;

      const editor = container.querySelector('.code-editor');
      const gutter = container.querySelector('.code-gutter');
      const nameInput = container.querySelector('.code-filename');
      const statusEl = container.querySelector('.code-status');

      const syncLines = () => {
        const count = Math.max(1, editor.value.split('\n').length);
        gutter.textContent = Array.from({ length: count }, (_, i) => i + 1).join('\n');
      };
      syncLines();
      editor.addEventListener('input', syncLines);
      editor.addEventListener('scroll', () => { gutter.scrollTop = editor.scrollTop; });

      container.querySelector('.code-save-btn').addEventListener('click', async () => {
        statusEl.textContent = 'Saving...';
        try {
          const res = await fetch('/api/suite/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filePath, fileName: nameInput.value, content: editor.value, tool: 'code_studio' })
          });
          const data = await res.json();
          if (data.success) {
            filePath = data.filePath;
            statusEl.textContent = `Saved (${data.fileName})`;
          } else {
            statusEl.textContent = data.error || 'Save failed';
          }
        } catch (_) {
          statusEl.textContent = 'Host unreachable';
        }
      });
    }

    // 4. My Suite — Table Studio (CSV/TSV Interactive Grid)
    renderTableStudioApp(winId, container, appData) {
      let filePath = appData.filePath || '';
      let fileName = appData.fileName || 'sheet.csv';
      const raw = appData.content || 'Column A,Column B,Column C\nItem 1,100,Active\nItem 2,240,Ready';
      const rows = raw.trim().split(/\r?\n/).map(line => line.split(','));

      container.innerHTML = `
        <div style="display:flex;flex-direction:column;height:100%;">
          <div style="display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid rgba(255,255,255,0.08);">
            <input class="tbl-filename" value="${this.escapeAttr(fileName)}" style="background:rgba(255,255,255,0.07);border:1px solid rgba(255,255,255,0.12);color:inherit;border-radius:6px;padding:4px 8px;font-size:12px;width:160px;" />
            <button class="os-btn small tbl-add-row">+ Row</button>
            <button class="os-btn small tbl-add-col">+ Col</button>
            <button class="os-btn small primary tbl-save-btn">Save CSV</button>
            <span class="tbl-status" style="font-size:11px;opacity:0.75;margin-left:auto;"></span>
          </div>
          <div class="tbl-scroll" style="flex:1;overflow:auto;padding:12px;"></div>
        </div>
      `;

      const scrollEl = container.querySelector('.tbl-scroll');
      const nameInput = container.querySelector('.tbl-filename');
      const statusEl = container.querySelector('.tbl-status');

      const drawTable = () => {
        const colCount = Math.max(1, ...rows.map(r => r.length));
        const tableHtml = `
          <table style="width:100%;border-collapse:collapse;font-size:12px;">
            ${rows.map((r, rIdx) => `
              <tr>
                ${Array.from({ length: colCount }, (_, cIdx) => {
                  const val = r[cIdx] ?? '';
                  const tag = rIdx === 0 ? 'th' : 'td';
                  return `<${tag} contenteditable="true" data-r="${rIdx}" data-c="${cIdx}" style="border:1px solid rgba(255,255,255,0.12);padding:6px 8px;min-width:90px;background:${rIdx === 0 ? 'rgba(255,255,255,0.08)' : 'transparent'};outline:none;">${this.escapeHtml(val)}</${tag}>`;
                }).join('')}
              </tr>
            `).join('')}
          </table>
        `;
        scrollEl.innerHTML = tableHtml;
        scrollEl.querySelectorAll('[contenteditable]').forEach(cell => {
          cell.addEventListener('input', () => {
            const r = parseInt(cell.getAttribute('data-r'), 10);
            const c = parseInt(cell.getAttribute('data-c'), 10);
            while (rows[r].length <= c) rows[r].push('');
            rows[r][c] = cell.textContent;
          });
        });
      };

      drawTable();
      container.querySelector('.tbl-add-row').addEventListener('click', () => {
        const cols = Math.max(1, rows[0]?.length || 3);
        rows.push(Array(cols).fill(''));
        drawTable();
      });
      container.querySelector('.tbl-add-col').addEventListener('click', () => {
        rows.forEach((r, idx) => r.push(idx === 0 ? `Col ${r.length + 1}` : ''));
        drawTable();
      });
      container.querySelector('.tbl-save-btn').addEventListener('click', async () => {
        statusEl.textContent = 'Saving...';
        const csv = rows.map(r => r.join(',')).join('\n');
        try {
          const res = await fetch('/api/suite/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filePath, fileName: nameInput.value, content: csv, tool: 'table_studio' })
          });
          const data = await res.json();
          if (data.success) {
            filePath = data.filePath;
            statusEl.textContent = `Saved (${data.fileName})`;
          } else {
            statusEl.textContent = data.error || 'Save failed';
          }
        } catch (_) {
          statusEl.textContent = 'Host unreachable';
        }
      });
    }

    // 5. My Suite — Photo Studio
    renderPhotoStudioApp(winId, container, appData) {
      const imgUrl = appData.imageUrl || '';
      const fileName = appData.fileName || 'photo.png';
      let brightness = 100, contrast = 100, saturate = 100, rotation = 0;

      container.innerHTML = `
        <div style="display:flex;flex-direction:column;height:100%;">
          <div style="display:flex;align-items:center;gap:10px;padding:8px 12px;border-bottom:1px solid rgba(255,255,255,0.08);flex-wrap:wrap;">
            <span style="font-size:12px;font-weight:600;">${this.escapeHtml(fileName)}</span>
            <label style="font-size:11px;">Bright <input type="range" class="ph-bright" min="30" max="180" value="100" style="width:70px;vertical-align:middle;"></label>
            <label style="font-size:11px;">Contrast <input type="range" class="ph-contrast" min="40" max="180" value="100" style="width:70px;vertical-align:middle;"></label>
            <label style="font-size:11px;">Sat <input type="range" class="ph-sat" min="0" max="200" value="100" style="width:70px;vertical-align:middle;"></label>
            <button class="os-btn small ph-rotate">Rotate 90°</button>
            <button class="os-btn small primary ph-export" style="margin-left:auto;">Export PNG</button>
          </div>
          <div style="flex:1;display:flex;align-items:center;justify-content:center;overflow:auto;background:rgba(0,0,0,0.35);padding:16px;">
            ${imgUrl ? `<img class="ph-img" src="${this.escapeAttr(imgUrl)}" crossorigin="anonymous" style="max-width:100%;max-height:100%;border-radius:8px;transition:transform 0.2s ease;" />` :
              `<div style="opacity:0.65;font-size:13px;text-align:center;">Open any shared image from File Manager to edit in Photo Studio.</div>`}
          </div>
        </div>
      `;

      const img = container.querySelector('.ph-img');
      const applyFilter = () => {
        if (!img) return;
        img.style.filter = `brightness(${brightness}%) contrast(${contrast}%) saturate(${saturate}%)`;
        img.style.transform = `rotate(${rotation}deg)`;
      };

      container.querySelector('.ph-bright')?.addEventListener('input', (e) => { brightness = e.target.value; applyFilter(); });
      container.querySelector('.ph-contrast')?.addEventListener('input', (e) => { contrast = e.target.value; applyFilter(); });
      container.querySelector('.ph-sat')?.addEventListener('input', (e) => { saturate = e.target.value; applyFilter(); });
      container.querySelector('.ph-rotate')?.addEventListener('click', () => { rotation = (rotation + 90) % 360; applyFilter(); });
      container.querySelector('.ph-export')?.addEventListener('click', () => {
        if (!img || !img.naturalWidth) return;
        const canvas = document.createElement('canvas');
        const swap = rotation % 180 !== 0;
        canvas.width = swap ? img.naturalHeight : img.naturalWidth;
        canvas.height = swap ? img.naturalWidth : img.naturalHeight;
        const ctx = canvas.getContext('2d');
        ctx.filter = `brightness(${brightness}%) contrast(${contrast}%) saturate(${saturate}%)`;
        ctx.translate(canvas.width / 2, canvas.height / 2);
        ctx.rotate((rotation * Math.PI) / 180);
        ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2);
        const a = document.createElement('a');
        a.href = canvas.toDataURL('image/png');
        a.download = `edited_${fileName.replace(/\.[^.]+$/, '')}.png`;
        a.click();
      });
    }

    // 6. My Suite — File Converter (Real Host Native Conversion Engine)
    renderFileConverterApp(winId, container, appData) {
      container.innerHTML = `
        <div style="display:flex;flex-direction:column;height:100%;padding:16px;gap:12px;">
          <div style="font-weight:700;font-size:14px;">Host File Converter</div>
          <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
            <select class="conv-file-select" style="flex:1;min-width:200px;background:rgba(255,255,255,0.08);color:inherit;border:1px solid rgba(255,255,255,0.15);border-radius:8px;padding:8px;font-size:12px;"></select>
            <select class="conv-target-select" style="width:130px;background:rgba(255,255,255,0.08);color:inherit;border:1px solid rgba(255,255,255,0.15);border-radius:8px;padding:8px;font-size:12px;"></select>
            <button class="os-btn primary conv-run-btn">Convert</button>
          </div>
          <div class="conv-result glass-card" style="padding:14px;font-size:12px;opacity:0.85;">Select a host-shared file and target format to convert directly on the Android host.</div>
        </div>
      `;

      const fileSel = container.querySelector('.conv-file-select');
      const targetSel = container.querySelector('.conv-target-select');
      const runBtn = container.querySelector('.conv-run-btn');
      const resultEl = container.querySelector('.conv-result');

      const loadTargets = async (path) => {
        targetSel.innerHTML = '<option value="">...</option>';
        if (!path) return;
        try {
          const res = await fetch(`/api/suite/converter/targets?path=${encodeURIComponent(path)}`);
          const data = await res.json();
          const targets = data.targets || [];
          targetSel.innerHTML = targets.length
            ? targets.map(t => `<option value="${this.escapeAttr(t)}" ${t === data.defaultTarget ? 'selected' : ''}>.${this.escapeHtml(t.toUpperCase())}</option>`).join('')
            : '<option value="">Unsupported</option>';
        } catch (_) {
          targetSel.innerHTML = '<option value="">Error</option>';
        }
      };

      const loadFiles = async () => {
        try {
          const res = await fetch('/api/files/list');
          const data = await res.json();
          const list = (data.files || []).filter(f => f.type === 'file');
          if (appData.filePath && !list.some(f => f.path === appData.filePath)) {
            list.unshift({ name: appData.fileName || 'Selected File', path: appData.filePath, type: 'file' });
          }
          if (list.length === 0) {
            fileSel.innerHTML = '<option value="">No shared files available</option>';
            return;
          }
          fileSel.innerHTML = list.map(f => `<option value="${this.escapeAttr(f.path)}" ${f.path === appData.filePath ? 'selected' : ''}>${this.escapeHtml(f.name)}</option>`).join('');
          loadTargets(fileSel.value);
        } catch (_) {}
      };

      fileSel.addEventListener('change', () => loadTargets(fileSel.value));
      runBtn.addEventListener('click', async () => {
        const path = fileSel.value;
        const targetFormat = targetSel.value;
        if (!path || !targetFormat) return;
        resultEl.textContent = 'Converting on Android host...';
        try {
          const res = await fetch('/api/suite/converter/run', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path, targetFormat })
          });
          const data = await res.json();
          if (data.success) {
            resultEl.innerHTML = `Converted: <strong>${this.escapeHtml(data.outputName)}</strong> — <a href="${this.escapeAttr(data.downloadUrl)}" download="${this.escapeAttr(data.outputName)}" class="os-btn small primary" style="margin-left:8px;text-decoration:none;">Download Result</a>`;
          } else {
            resultEl.textContent = `Conversion failed: ${data.error || 'Unknown error'}`;
          }
        } catch (_) {
          resultEl.textContent = 'Conversion request failed.';
        }
      });

      loadFiles();
    }

    // 7. SyncPlay Frame-Accurate Synchronized Media Stage (Locked Fullscreen, Minimizable, Non-Closable)
    renderSyncPlayApp(winId, container) {
      this.cleanupWindowResources(winId);
      container.innerHTML = `
        <div class="syncplay-stage" style="display:flex;flex-direction:column;height:100%;background:#090b10;color:#f8fafc;">
          <div style="display:flex;align-items:center;gap:10px;padding:8px 14px;background:rgba(255,255,255,0.04);border-bottom:1px solid rgba(255,255,255,0.08);">
            <span class="sp-title" style="font-weight:700;font-size:13px;">SyncPlay</span>
            <span class="sp-sync-badge" style="font-size:11px;padding:2px 8px;border-radius:999px;background:rgba(16,185,129,0.2);color:#6ee7b7;">Connecting...</span>
            <div class="sp-auth-box" style="margin-left:auto;display:none;gap:6px;align-items:center;">
              <input class="sp-pin-input" placeholder="6-digit PIN" style="width:110px;padding:4px 8px;border-radius:6px;background:rgba(255,255,255,0.1);border:1px solid rgba(255,255,255,0.18);color:#fff;font-size:12px;" />
              <button class="os-btn small primary sp-auth-btn">Unlock</button>
            </div>
          </div>
          <div class="sp-media-box" style="flex:1;display:flex;align-items:center;justify-content:center;position:relative;overflow:hidden;">
            <video class="sp-video" playsinline style="width:100%;height:100%;object-fit:contain;display:none;"></video>
            <audio class="sp-audio" style="display:none;"></audio>
            <div class="sp-idle" style="text-align:center;opacity:0.75;padding:24px;">Waiting for Host SyncPlay media...</div>
          </div>
        </div>
      `;

      const titleEl = container.querySelector('.sp-title');
      const badgeEl = container.querySelector('.sp-sync-badge');
      const authBox = container.querySelector('.sp-auth-box');
      const pinInput = container.querySelector('.sp-pin-input');
      const authBtn = container.querySelector('.sp-auth-btn');
      const videoEl = container.querySelector('.sp-video');
      const audioEl = container.querySelector('.sp-audio');
      const idleEl = container.querySelector('.sp-idle');

      const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = window.location.host || 'localhost:8080';
      const spWs = new WebSocket(`${proto}//${host}/ws/syncplay`);
      this.syncPlaySockets.set(winId, spWs);

      let clockOffsetMs = 0;
      let loadedStreamUrl = '';
      let pingTimer = null;

      const getActiveMedia = (isVideo) => (isVideo ? videoEl : audioEl);

      const applySyncFrame = (st) => {
        if (!st || !st.isActive) {
          idleEl.style.display = 'block';
          videoEl.style.display = 'none';
          videoEl.pause();
          audioEl.pause();
          badgeEl.textContent = 'Idle';
          return;
        }

        if (st.authenticated === false) {
          authBox.style.display = 'flex';
          badgeEl.textContent = 'PIN Required';
          idleEl.style.display = 'block';
          idleEl.textContent = 'Enter the Host SyncPlay PIN above to synchronize playback.';
          return;
        }

        authBox.style.display = 'none';
        titleEl.textContent = st.mediaFileName || 'SyncPlay';
        const mediaEl = getActiveMedia(st.isVideo);
        const otherEl = getActiveMedia(!st.isVideo);
        otherEl.pause();
        otherEl.style.display = 'none';

        if (st.streamUrl && loadedStreamUrl !== st.streamUrl) {
          loadedStreamUrl = st.streamUrl;
          mediaEl.src = st.streamUrl;
          mediaEl.load();
        }

        if (st.isVideo) {
          idleEl.style.display = 'none';
          videoEl.style.display = 'block';
        } else {
          videoEl.style.display = 'none';
          idleEl.style.display = 'block';
          idleEl.innerHTML = `<div style="font-size:40px;margin-bottom:8px;">🎵</div><div style="font-weight:700;font-size:16px;">${this.escapeHtml(st.mediaFileName)}</div>`;
        }

        // Apply audio routing policy (mute receiver if Host-only audio selected)
        const audioRoute = this.serverState.adminConfig.audioRoute || 'receiver';
        mediaEl.muted = audioRoute === 'host';
        mediaEl.playbackRate = st.playbackSpeed || 1.0;

        const estServerNow = Date.now() + clockOffsetMs;
        const waitMs = Math.max(0, (st.executeAtServerEpochMs || 0) - estServerNow);
        const targetSec = Math.max(0, (st.livePositionMs || 0) / 1000);

        const execute = () => {
          if (Math.abs((mediaEl.currentTime || 0) - targetSec) > 0.18) {
            try { mediaEl.currentTime = targetSec; } catch (_) {}
          }
          if (st.isPlaying) {
            mediaEl.play().catch(() => {});
            badgeEl.textContent = `Synced Playing • ${(st.playbackSpeed || 1).toFixed(2)}x`;
          } else {
            mediaEl.pause();
            badgeEl.textContent = 'Synced Paused';
          }
        };

        if (waitMs > 8 && waitMs < 1500) {
          try { mediaEl.currentTime = Math.max(0, (st.basePositionMs || 0) / 1000); } catch (_) {}
          setTimeout(execute, waitMs);
        } else {
          execute();
        }
      };

      spWs.onopen = () => {
        badgeEl.textContent = 'Connected';
        pingTimer = setInterval(() => {
          if (spWs.readyState === WebSocket.OPEN) {
            spWs.send(JSON.stringify({ type: 'PING', clientSendMs: Date.now(), rttMs: this.lastRtt, clockOffsetMs, bufferReady: true }));
          }
        }, 2500);
        spWs.send(JSON.stringify({ type: 'PING', clientSendMs: Date.now(), bufferReady: true }));
      };

      spWs.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data);
          if (msg.type === 'PONG') {
            const t4 = Date.now();
            const rtt = Math.max(0, (t4 - msg.clientSendMs) - ((msg.serverSendMs || 0) - (msg.serverRecvMs || 0)));
            clockOffsetMs = Math.round(((msg.serverRecvMs - msg.clientSendMs) + (msg.serverSendMs - t4)) / 2);
            this.lastRtt = rtt;
          } else if (msg.type === 'AUTH_RESULT') {
            badgeEl.textContent = msg.authenticated ? 'Unlocked' : 'Invalid PIN';
          } else if (msg.isActive !== undefined) {
            applySyncFrame(msg);
          }
        } catch (_) {}
      };

      spWs.onclose = () => {
        if (pingTimer) clearInterval(pingTimer);
      };

      authBtn.addEventListener('click', () => {
        if (spWs.readyState === WebSocket.OPEN) {
          spWs.send(JSON.stringify({ type: 'AUTH', passcode: pinInput.value.trim(), name: 'OmniOS Display' }));
        }
      });
    }

    // 8. Video Player
    renderVideoPlayerApp(winId, container, appData) {
      const src = appData.videoSrc || '';
      const muted = (this.serverState.adminConfig.audioRoute || 'receiver') === 'host' ? 'muted' : '';
      container.innerHTML = `
        <div class="media-player-root video-theme">
          <div class="media-screen">
            ${src ? `<video src="${this.escapeAttr(src)}" controls autoplay ${muted} class="media-video-element"></video>` : `
              <div class="empty-media-state">
                <div class="empty-icon">🎬</div>
                <div class="empty-title">OmniOS Video Player</div>
                <div class="empty-desc">Open any shared video from File Manager</div>
              </div>
            `}
          </div>
        </div>
      `;
    }

    // 9. Audio Player
    renderAudioPlayerApp(winId, container, appData) {
      const track = appData.currentTrack || { title: 'OmniOS Audio', artist: 'Host Shared Media' };
      const src = appData.audioSrc || '';
      container.innerHTML = `
        <div class="media-player-root audio-theme p-6" style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;gap:16px;">
          <div class="audio-disc-animation"><div class="vinyl-disc"></div></div>
          <div class="audio-info text-center">
            <h3 class="text-lg font-bold">${this.escapeHtml(track.title)}</h3>
            <p class="text-sm text-muted">${this.escapeHtml(track.artist)}</p>
          </div>
          ${src ? `<audio src="${this.escapeAttr(src)}" controls autoplay style="width:90%;max-width:420px;"></audio>` : '<div class="text-xs text-muted">Open a shared audio file from File Manager.</div>'}
        </div>
      `;
    }

    // 10. PDF Viewer
    renderPdfViewerApp(winId, container, appData) {
      const pdfUrl = appData.pdfUrl || '';
      container.innerHTML = pdfUrl
        ? `<iframe src="${this.escapeAttr(pdfUrl)}" style="width:100%;height:100%;border:none;background:#1e293b;"></iframe>`
        : `<div class="p-6 text-center text-muted">Open any shared PDF from File Manager to view it here.</div>`;
    }

    // 11. Terminal
    renderTerminalApp(winId, container) {
      container.innerHTML = `
        <div class="terminal-root font-mono p-4 bg-black text-green-400 h-full flex flex-col">
          <div class="terminal-output flex-1 overflow-y-auto mb-2 text-xs leading-5">
            <div>OmniOS Host Terminal</div>
            <div>Type 'help' for status commands.</div>
          </div>
          <div class="terminal-input-line flex items-center gap-2">
            <span class="text-blue-400">host@omnios:~$</span>
            <input type="text" class="terminal-input flex-1 bg-transparent border-none text-green-400 outline-none text-xs font-mono" />
          </div>
        </div>
      `;

      const input = container.querySelector('.terminal-input');
      const output = container.querySelector('.terminal-output');
      input?.addEventListener('keydown', (e) => {
        if (e.key !== 'Enter') return;
        const cmd = input.value.trim();
        input.value = '';
        if (!cmd) return;
        const line = document.createElement('div');
        line.textContent = `host@omnios:~$ ${cmd}`;
        output.appendChild(line);

        const res = document.createElement('div');
        res.className = 'text-gray-300';
        if (cmd === 'help') {
          res.textContent = 'Commands: status, apps, stats, clear';
        } else if (cmd === 'status') {
          res.textContent = `OmniOS Online | Theme: ${this.serverState.adminConfig.theme} | Touch: ${this.serverState.adminConfig.touchInputEnabled ? 'ON' : 'LOCKED'} | Audio: ${this.serverState.adminConfig.audioRoute}`;
        } else if (cmd === 'apps') {
          res.textContent = `Allowed Apps: ${(this.serverState.adminConfig.allowedApps || []).join(', ')}`;
        } else if (cmd === 'stats') {
          const s = this.serverState.systemStats;
          res.textContent = `RAM: ${s.ram} | Temp: ${s.temp}°C | Battery: ${s.battery}%`;
        } else if (cmd === 'clear') {
          output.innerHTML = '';
          return;
        } else {
          res.textContent = `Unknown command: ${cmd}`;
        }
        output.appendChild(res);
        output.scrollTop = output.scrollHeight;
      });
    }

    // 12. System Monitor
    renderSystemMonitorApp(winId, container) {
      const stats = this.serverState.systemStats;
      container.innerHTML = `
        <div class="sysmon-root p-6">
          <h3 class="text-md font-bold mb-4">Host Diagnostics</h3>
          <div class="sysmon-grid grid grid-cols-2 gap-4">
            <div class="glass-card p-4"><div class="text-xs text-muted">Memory Load</div><div class="text-2xl font-bold sysmon-cpu">${stats.cpu}%</div></div>
            <div class="glass-card p-4"><div class="text-xs text-muted">RAM Allocated</div><div class="text-2xl font-bold sysmon-ram">${stats.ram}</div></div>
            <div class="glass-card p-4"><div class="text-xs text-muted">Device Temp</div><div class="text-2xl font-bold sysmon-temp">${stats.temp}°C</div></div>
            <div class="glass-card p-4"><div class="text-xs text-muted">Battery</div><div class="text-2xl font-bold sysmon-battery">${stats.battery}%</div></div>
          </div>
          <div class="mt-6 glass-card p-4">
            <div class="flex items-center justify-between text-sm">
              <span>Touch Gate</span>
              <span class="font-bold ${this.serverState.adminConfig.touchInputEnabled ? 'text-green-400' : 'text-red-400'}">
                ${this.serverState.adminConfig.touchInputEnabled ? 'ACTIVE' : 'LOCKED BY HOST'}
              </span>
            </div>
          </div>
        </div>
      `;
    }

    updateSystemMonitorView() {
      const win = this.dom.windowContainer?.querySelector('[data-app-id="sysmon"]');
      if (!win) return;
      const s = this.serverState.systemStats;
      const set = (sel, val) => { const el = win.querySelector(sel); if (el) el.textContent = val; };
      set('.sysmon-cpu', `${s.cpu}%`);
      set('.sysmon-ram', s.ram);
      set('.sysmon-temp', `${s.temp}°C`);
      set('.sysmon-battery', `${s.battery}%`);
    }

    // 13. Settings App (Host Admin Filtered)
    renderSettingsApp(winId, container, appData = {}) {
      const vis = this.serverState.adminConfig.settingsVisibility || {};
      const config = this.serverState.adminConfig;
      const activeTab = appData.activeTab || 'appearance';
      const audioRoute = config.audioRoute || 'receiver';

      container.innerHTML = `
        <div class="settings-root p-6">
          <div class="settings-nav mb-6 flex gap-3 border-b pb-3">
            ${vis.appearance ? `<button class="settings-tab-btn ${activeTab === 'appearance' ? 'active' : ''}" data-tab="appearance">Appearance</button>` : ''}
            ${vis.animation ? `<button class="settings-tab-btn ${activeTab === 'animation' ? 'active' : ''}" data-tab="animation">Animations</button>` : ''}
            ${vis.sound ? `<button class="settings-tab-btn ${activeTab === 'sound' ? 'active' : ''}" data-tab="sound">Sound</button>` : ''}
            ${vis.host ? `<button class="settings-tab-btn ${activeTab === 'host' ? 'active' : ''}" data-tab="host">Host Policy</button>` : ''}
          </div>
          <div class="settings-pane">
            <div class="settings-tab-content ${activeTab === 'appearance' ? 'block' : 'hidden'}" id="tab-appearance">
              ${vis.appearance ? `
                <div class="settings-section">
                  <h4 class="font-bold mb-3">Desktop Theme</h4>
                  <div class="theme-picker flex gap-3 mb-5">
                    ${['dex', 'macos', 'windows', 'linux'].map(t => `<button class="os-btn ${config.theme === t ? 'primary' : ''} theme-select-btn" data-theme="${t}">${t.toUpperCase()}</button>`).join('')}
                  </div>
                  <h4 class="font-bold mb-2">Display Brightness</h4>
                  <div class="flex items-center gap-4 mb-4">
                    <input type="range" min="15" max="100" value="${this.serverState.quickPanel.brightness || 100}" class="settings-brightness-slider" />
                    <span class="settings-brightness-val text-xs font-mono">${this.serverState.quickPanel.brightness || 100}%</span>
                  </div>
                  <div class="flex items-center justify-between p-3 glass-card mb-4">
                    <span class="text-sm font-semibold">Theme Polarity</span>
                    <button class="os-btn small toggle-darkmode-btn">${this.serverState.quickPanel.darkMode ? 'Dark' : 'Light'}</button>
                  </div>
                </div>
              ` : '<div class="text-sm text-muted">Managed by Host Admin.</div>'}
            </div>

            <div class="settings-tab-content ${activeTab === 'animation' ? 'block' : 'hidden'}" id="tab-animation">
              ${vis.animation ? `
                <div class="settings-section">
                  <h4 class="font-bold mb-3">Animation Duration</h4>
                  <div class="flex items-center gap-4">
                    <input type="range" min="100" max="800" step="50" value="${config.animationDuration || 300}" class="anim-duration-slider" />
                    <span class="anim-duration-val">${config.animationDuration || 300}ms</span>
                  </div>
                </div>
              ` : '<div class="text-sm text-muted">Managed by Host Admin.</div>'}
            </div>

            <div class="settings-tab-content ${activeTab === 'sound' ? 'block' : 'hidden'}" id="tab-sound">
              ${vis.sound ? `
                <div class="settings-section">
                  <h4 class="font-bold mb-3">Audio Output Route</h4>
                  <div class="audio-route-group mb-5">
                    <button class="audio-route-btn ${audioRoute === 'receiver' ? 'active' : ''}" data-audio="receiver">Receiver</button>
                    <button class="audio-route-btn ${audioRoute === 'host' ? 'active' : ''}" data-audio="host">Host</button>
                    <button class="audio-route-btn ${audioRoute === 'both' ? 'active' : ''}" data-audio="both">Both</button>
                  </div>
                </div>
              ` : '<div class="text-sm text-muted">Managed by Host Admin.</div>'}
            </div>

            <div class="settings-tab-content ${activeTab === 'host' ? 'block' : 'hidden'}" id="tab-host">
              ${vis.host ? `
                <div class="settings-section flex flex-col gap-2">
                  <div class="flex items-center justify-between p-3 glass-card"><span class="text-xs">Touch Input</span><span class="badge ${config.touchInputEnabled ? 'badge-success' : 'badge-danger'}">${config.touchInputEnabled ? 'Allowed' : 'Locked'}</span></div>
                  <div class="flex items-center justify-between p-3 glass-card"><span class="text-xs">Audio Target</span><span class="badge badge-success">${(config.audioRoute || 'receiver').toUpperCase()}</span></div>
                </div>
              ` : ''}
            </div>
          </div>
        </div>
      `;

      container.querySelectorAll('.settings-tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          const tab = btn.getAttribute('data-tab');
          container.querySelectorAll('.settings-tab-btn').forEach(b => b.classList.remove('active'));
          btn.classList.add('active');
          container.querySelectorAll('.settings-tab-content').forEach(c => { c.classList.add('hidden'); c.classList.remove('block'); });
          const target = container.querySelector(`#tab-${tab}`);
          if (target) { target.classList.remove('hidden'); target.classList.add('block'); }
        });
      });

      container.querySelectorAll('.theme-select-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          this.send(OmniOSProtocol.OPCODES.ADMIN_COMMAND, { cmd: 'set_theme', theme: btn.getAttribute('data-theme') });
        });
      });

      container.querySelector('.toggle-darkmode-btn')?.addEventListener('click', () => {
        const next = !this.serverState.quickPanel.darkMode;
        this.applyDarkMode(next);
        this.send(OmniOSProtocol.OPCODES.ADMIN_COMMAND, { cmd: 'set_dark_mode', darkMode: next });
      });

      const bSlider = container.querySelector('.settings-brightness-slider');
      const bVal = container.querySelector('.settings-brightness-val');
      if (bSlider) {
        bSlider.addEventListener('input', (e) => {
          const v = parseInt(e.target.value, 10);
          if (bVal) bVal.textContent = `${v}%`;
          this.applyBrightness(v);
        });
        bSlider.addEventListener('change', (e) => {
          this.send(OmniOSProtocol.OPCODES.ADMIN_COMMAND, { cmd: 'set_brightness', brightness: parseInt(e.target.value, 10) });
        });
      }

      const animSlider = container.querySelector('.anim-duration-slider');
      const animVal = container.querySelector('.anim-duration-val');
      if (animSlider) {
        animSlider.addEventListener('input', (e) => { if (animVal) animVal.textContent = `${e.target.value}ms`; });
        animSlider.addEventListener('change', (e) => {
          this.send(OmniOSProtocol.OPCODES.ADMIN_COMMAND, { cmd: 'set_animation_duration', duration: parseInt(e.target.value, 10) });
        });
      }

      container.querySelectorAll('.audio-route-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          this.send(OmniOSProtocol.OPCODES.ADMIN_COMMAND, { cmd: 'set_audio_route', route: btn.getAttribute('data-audio') });
        });
      });
    }

    patchOpenSettingsWindow() {
      const settingsWin = this.dom.windowContainer?.querySelector('[data-app-id="settings"]');
      if (settingsWin) {
        const bodyEl = settingsWin.querySelector('.window-content');
        if (bodyEl) this.renderSettingsApp(settingsWin.getAttribute('data-win-id'), bodyEl, {});
      }
    }

    /* ==========================================================================
       Dock & Desktop Icons Rendering (Admin Whitelist Filtered)
       ========================================================================== */

    renderDock() {
      if (!this.dom.dock) return;
      const allowedApps = this.serverState.adminConfig.allowedApps || [];
      const runningAppIds = new Set(Object.values(this.serverState.windows).map(w => w.appId));

      // Show admin-selected apps plus any active host-locked window (e.g., SyncPlay)
      const filtered = OMNIOS_APP_CATALOG.filter(app => allowedApps.includes(app.id) || runningAppIds.has(app.id));

      this.dom.dock.innerHTML = filtered.map(app => `
        <div class="dock-item" data-app-id="${app.id}" title="${this.escapeAttr(app.name)}">
          <div class="dock-icon-wrapper">${this.getAppIconSvg(app.id, 42)}</div>
          <div class="dock-dot"></div>
        </div>
      `).join('');

      this.dom.dock.querySelectorAll('.dock-item').forEach(item => {
        item.addEventListener('click', () => this.handleDockAppClick(item.getAttribute('data-app-id')));
      });

      this.updateDockRunningIndicators();
    }

    handleDockAppClick(appId) {
      const existing = Object.entries(this.serverState.windows).find(([, w]) => w.appId === appId);
      if (existing) {
        const [winId, w] = existing;
        if (w.isMinimized) {
          this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'restore', winId });
        } else if (w.isFocused) {
          this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'minimize', winId });
        } else {
          this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'focus', winId });
        }
      } else {
        this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'open', appId });
      }
    }

    updateDockRunningIndicators() {
      if (!this.dom.dock) return;
      const runningApps = new Set(Object.values(this.serverState.windows).map(w => w.appId));
      this.dom.dock.querySelectorAll('.dock-item').forEach(item => {
        item.classList.toggle('running', runningApps.has(item.getAttribute('data-app-id')));
      });
    }

    renderDesktopShortcuts() {
      const desktopIconsContainer = document.getElementById('desktop-icons');
      if (!desktopIconsContainer) return;

      const allowedApps = this.serverState.adminConfig.allowedApps || [];
      const filtered = OMNIOS_APP_CATALOG.filter(app => app.desktop && allowedApps.includes(app.id));

      desktopIconsContainer.innerHTML = filtered.map(app => `
        <div class="desktop-shortcut" data-app-id="${app.id}">
          <div class="shortcut-icon">${this.getAppIconSvg(app.id, 46)}</div>
          <div class="shortcut-label">${this.escapeHtml(app.name)}</div>
        </div>
      `).join('');

      desktopIconsContainer.querySelectorAll('.desktop-shortcut').forEach(sc => {
        sc.addEventListener('dblclick', () => {
          this.send(OmniOSProtocol.OPCODES.WINDOW_ACTION, { action: 'open', appId: sc.getAttribute('data-app-id') });
        });
      });
    }

    /* ==========================================================================
       Quick Panel & Notification Center
       ========================================================================== */

    renderQuickPanel() {
      if (!this.dom.quickPanel) return;
      const qp = this.serverState.quickPanel;
      const audioRoute = this.serverState.adminConfig.audioRoute || 'receiver';

      this.dom.quickPanel.innerHTML = `
        <div class="quick-panel-card glass-panel p-5">
          <div class="grid grid-cols-2 gap-3 mb-4">
            <button class="qp-tile ${qp.wifi ? 'active' : ''}" data-feature="wifi"><span>📶</span><span class="text-xs font-semibold">Wi-Fi</span></button>
            <button class="qp-tile ${qp.bluetooth ? 'active' : ''}" data-feature="bluetooth"><span>ᛒ</span><span class="text-xs font-semibold">Bluetooth</span></button>
            <button class="qp-tile ${qp.darkMode ? 'active' : ''}" data-feature="darkMode"><span>🌙</span><span class="text-xs font-semibold">Dark Mode</span></button>
            <button class="qp-tile ${this.serverState.adminConfig.touchInputEnabled ? 'active' : 'disabled'}" data-feature="touch"><span>👆</span><span class="text-xs font-semibold">Touch</span></button>
          </div>
          <div class="mb-4">
            <label class="text-xs font-semibold text-muted block mb-1.5">Audio Route</label>
            <div class="audio-route-group">
              <button class="audio-route-btn ${audioRoute === 'receiver' ? 'active' : ''}" data-audio="receiver">Receiver</button>
              <button class="audio-route-btn ${audioRoute === 'host' ? 'active' : ''}" data-audio="host">Host</button>
              <button class="audio-route-btn ${audioRoute === 'both' ? 'active' : ''}" data-audio="both">Both</button>
            </div>
          </div>
          <div class="slider-group mb-3">
            <div class="flex justify-between items-center mb-1">
              <label class="text-xs font-semibold text-muted">Display Brightness</label>
              <span class="text-2xs font-mono text-muted qp-brightness-val">${qp.brightness || 100}%</span>
            </div>
            <input type="range" min="15" max="100" value="${qp.brightness || 100}" class="qp-slider" data-param="brightness" />
          </div>
        </div>
      `;

      this.dom.quickPanel.querySelectorAll('.qp-tile').forEach(tile => {
        tile.addEventListener('click', () => {
          const feat = tile.getAttribute('data-feature');
          if (feat === 'darkMode') {
            const nextMode = !this.serverState.quickPanel.darkMode;
            this.applyDarkMode(nextMode);
            this.send(OmniOSProtocol.OPCODES.ADMIN_COMMAND, { cmd: 'set_dark_mode', darkMode: nextMode });
          } else if (feat !== 'touch') {
            this.send(OmniOSProtocol.OPCODES.APP_ACTION, { appId: 'system', action: 'toggle_quick_panel', feature: feat });
          }
        });
      });

      this.dom.quickPanel.querySelectorAll('.audio-route-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          this.send(OmniOSProtocol.OPCODES.ADMIN_COMMAND, { cmd: 'set_audio_route', route: btn.getAttribute('data-audio') });
        });
      });

      const bSlider = this.dom.quickPanel.querySelector('[data-param="brightness"]');
      const bVal = this.dom.quickPanel.querySelector('.qp-brightness-val');
      if (bSlider) {
        bSlider.addEventListener('input', (e) => {
          const val = parseInt(e.target.value, 10);
          if (bVal) bVal.textContent = `${val}%`;
          this.applyBrightness(val);
        });
        bSlider.addEventListener('change', (e) => {
          this.send(OmniOSProtocol.OPCODES.ADMIN_COMMAND, { cmd: 'set_brightness', brightness: parseInt(e.target.value, 10) });
        });
      }
    }

    toggleQuickPanel() {
      this.dom.quickPanel?.classList.toggle('open');
      this.dom.notificationCenter?.classList.remove('open');
    }

    renderNotifications() {
      if (!this.dom.notificationCenter) return;
      const notifs = this.serverState.notifications || [];
      this.dom.notificationCenter.innerHTML = `
        <div class="notif-center-root glass-panel p-4">
          <div class="flex items-center justify-between mb-4 pb-2 border-b">
            <span class="font-bold text-sm">Notifications (${notifs.length})</span>
            <button class="text-xs text-muted clear-notifs-btn">Clear</button>
          </div>
          <div class="notif-list flex flex-col gap-3">
            ${notifs.length === 0 ? '<div class="text-center text-xs text-muted py-6">No notifications</div>' : ''}
            ${notifs.map(n => `
              <div class="notif-card glass-card p-3" data-id="${this.escapeAttr(n.id)}">
                <div class="flex items-center justify-between mb-1">
                  <span class="font-semibold text-xs">${this.escapeHtml(n.title)}</span>
                  <span class="text-2xs text-muted">${this.escapeHtml(n.time || 'now')}</span>
                </div>
                <div class="text-xs text-muted">${this.escapeHtml(n.body)}</div>
                <button class="notif-dismiss-btn" data-id="${this.escapeAttr(n.id)}">✕</button>
              </div>
            `).join('')}
          </div>
        </div>
      `;

      this.dom.notificationCenter.querySelector('.clear-notifs-btn')?.addEventListener('click', () => {
        this.send(OmniOSProtocol.OPCODES.APP_ACTION, { appId: 'system', action: 'clear_notifications' });
      });

      this.dom.notificationCenter.querySelectorAll('.notif-dismiss-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          this.send(OmniOSProtocol.OPCODES.APP_ACTION, { appId: 'system', action: 'dismiss_notification', id: btn.getAttribute('data-id') });
        });
      });
    }

    toggleNotificationCenter() {
      this.dom.notificationCenter?.classList.toggle('open');
      this.dom.quickPanel?.classList.remove('open');
    }

    closePopups() {
      this.dom.quickPanel?.classList.remove('open');
      this.dom.notificationCenter?.classList.remove('open');
    }

    showNotificationToast(notif) {
      const toast = document.createElement('div');
      toast.className = 'notif-toast glass-card';
      toast.innerHTML = `<div class="font-bold text-xs">${this.escapeHtml(notif.title)}</div><div class="text-xs text-muted">${this.escapeHtml(notif.body)}</div>`;
      document.body.appendChild(toast);
      setTimeout(() => toast.classList.add('visible'), 10);
      setTimeout(() => {
        toast.classList.remove('visible');
        setTimeout(() => toast.remove(), 300);
      }, 3600);
    }

    getAppIconSvg(appId, size = 32) {
      const theme = this.serverState.adminConfig.theme || 'dex';
      if (OmniOS.icons && typeof OmniOS.icons.get === 'function') {
        return OmniOS.icons.get(appId, theme, size);
      }
      return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="currentColor"><rect width="24" height="24" rx="6" fill="#007AFF"/></svg>`;
    }

    escapeHtml(str) {
      return String(str ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
    }

    escapeAttr(str) {
      return this.escapeHtml(str).replace(/'/g, '&#39;');
    }
  }

  OmniOS.SDUIClient = SDUIClient;

  document.addEventListener('DOMContentLoaded', () => {
    const client = new SDUIClient();
    window.OmniOSClientInstance = client;
    client.init();
  });
})(window.OmniOS);
