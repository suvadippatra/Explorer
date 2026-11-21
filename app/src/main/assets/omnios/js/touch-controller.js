/**
 * OmniOS - Touch & Pointer Controller
 * Handles high-frequency touch interactions on wireless displays, touchscreens, and web clients.
 * Provides immediate local tactile feedback and compact binary dispatching.
 */

window.OmniOS = window.OmniOS || {};

(function (OmniOS) {
  'use strict';

  class TouchController {
    constructor() {
      this.container = null;
      this.sendCallback = null;
      this.touchEnabled = true;
      this.activePointers = new Map();
      this.rafPending = false;
      this.pendingMoves = new Map();
      this.feedbackLayer = null;
      this.hudToast = null;

      this.onPointerDown = this.handlePointerDown.bind(this);
      this.onPointerMove = this.handlePointerMove.bind(this);
      this.onPointerUp = this.handlePointerUp.bind(this);
      this.onPointerCancel = this.handlePointerCancel.bind(this);
    }

    init(containerEl, sendCallback) {
      this.container = containerEl || document.body;
      this.sendCallback = sendCallback;

      // Create tactile feedback layer (for ultra-fast local visual echo)
      this.createFeedbackLayer();

      // Attach pointer events to container
      this.container.addEventListener('pointerdown', this.onPointerDown, { passive: false });
      window.addEventListener('pointermove', this.onPointerMove, { passive: false });
      window.addEventListener('pointerup', this.onPointerUp, { passive: false });
      window.addEventListener('pointercancel', this.onPointerCancel, { passive: false });

      // Prevent default pull-to-refresh or gesture bounce
      this.container.addEventListener('touchstart', (e) => {
        if (e.touches.length > 1) e.preventDefault();
      }, { passive: false });
    }

    createFeedbackLayer() {
      let layer = document.getElementById('touch-feedback-layer');
      if (!layer) {
        layer = document.createElement('div');
        layer.id = 'touch-feedback-layer';
        layer.style.cssText = `
          position: fixed;
          top: 0; left: 0; width: 100vw; height: 100vh;
          pointer-events: none;
          z-index: 99999;
          overflow: hidden;
        `;
        document.body.appendChild(layer);
      }
      this.feedbackLayer = layer;

      // HUD Toast for when Admin disables touch
      let toast = document.getElementById('touch-disabled-hud');
      if (!toast) {
        toast = document.createElement('div');
        toast.id = 'touch-disabled-hud';
        toast.style.cssText = `
          position: fixed;
          top: 18px;
          left: 50%;
          transform: translateX(-50%) translateY(-100px);
          background: rgba(220, 38, 38, 0.92);
          backdrop-filter: blur(12px);
          -webkit-backdrop-filter: blur(12px);
          color: #ffffff;
          padding: 8px 18px;
          border-radius: 9999px;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
          font-size: 13px;
          font-weight: 600;
          letter-spacing: 0.3px;
          box-shadow: 0 10px 25px -5px rgba(220, 38, 38, 0.5), 0 8px 10px -6px rgba(0, 0, 0, 0.2);
          pointer-events: none;
          z-index: 100000;
          transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.3s ease;
          opacity: 0;
          display: flex;
          align-items: center;
          gap: 8px;
        `;
        toast.innerHTML = `
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"></line>
          </svg>
          <span>Touch Input Disabled by Host Admin</span>
        `;
        document.body.appendChild(toast);
      }
      this.hudToast = toast;
    }

    setTouchEnabled(enabled) {
      this.touchEnabled = Boolean(enabled);
      if (!this.touchEnabled) {
        this.showDisabledHud();
        document.body.classList.add('touch-disabled-mode');
      } else {
        this.hideDisabledHud();
        document.body.classList.remove('touch-disabled-mode');
      }
    }

    showDisabledHud() {
      if (this.hudToast) {
        this.hudToast.style.opacity = '1';
        this.hudToast.style.transform = 'translateX(-50%) translateY(0)';
      }
    }

    hideDisabledHud() {
      if (this.hudToast) {
        this.hudToast.style.opacity = '0';
        this.hudToast.style.transform = 'translateX(-50%) translateY(-100px)';
      }
    }

    showTouchRipple(clientX, clientY) {
      if (!this.feedbackLayer) return;
      const ripple = document.createElement('div');
      ripple.className = 'touch-tactile-ripple';
      ripple.style.cssText = `
        position: absolute;
        left: ${clientX}px;
        top: ${clientY}px;
        width: 14px;
        height: 14px;
        margin-left: -7px;
        margin-top: -7px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.6);
        box-shadow: 0 0 16px rgba(0, 122, 255, 0.8), 0 0 28px rgba(0, 122, 255, 0.4);
        pointer-events: none;
        transform: scale(1);
        opacity: 0.95;
        transition: transform 0.45s cubic-bezier(0.1, 0.8, 0.2, 1), opacity 0.45s ease-out;
      `;
      this.feedbackLayer.appendChild(ripple);

      requestAnimationFrame(() => {
        ripple.style.transform = 'scale(4.5)';
        ripple.style.opacity = '0';
      });

      setTimeout(() => {
        if (ripple.parentNode) ripple.parentNode.removeChild(ripple);
      }, 500);
    }

    getNormalizedCoordinates(clientX, clientY) {
      const rect = this.container.getBoundingClientRect();
      const normX = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
      const normY = Math.max(0, Math.min(1, (clientY - rect.top) / rect.height));
      return { normX, normY };
    }

    handlePointerDown(e) {
      if (!this.touchEnabled) {
        this.showDisabledHud();
        setTimeout(() => this.hideDisabledHud(), 1800);
        return;
      }

      this.activePointers.set(e.pointerId, {
        startX: e.clientX,
        startY: e.clientY,
        lastX: e.clientX,
        lastY: e.clientY,
        time: Date.now()
      });

      this.showTouchRipple(e.clientX, e.clientY);

      const { normX, normY } = this.getNormalizedCoordinates(e.clientX, e.clientY);

      if (this.sendCallback) {
        // Send compact binary touch packet
        const buffer = OmniOSProtocol.encodeBinaryTouch(
          OmniOSProtocol.OPCODES.TOUCH_DOWN,
          e.pointerId,
          normX,
          normY,
          Date.now()
        );
        this.sendCallback(buffer);
      }
    }

    handlePointerMove(e) {
      if (!this.touchEnabled || !this.activePointers.has(e.pointerId)) return;

      const ptr = this.activePointers.get(e.pointerId);
      ptr.lastX = e.clientX;
      ptr.lastY = e.clientY;

      const { normX, normY } = this.getNormalizedCoordinates(e.clientX, e.clientY);

      this.pendingMoves.set(e.pointerId, {
        normX,
        normY,
        time: Date.now()
      });

      // Frame-rate throttle dispatch using requestAnimationFrame
      if (!this.rafPending) {
        this.rafPending = true;
        requestAnimationFrame(() => {
          this.flushPendingMoves();
          this.rafPending = false;
        });
      }
    }

    flushPendingMoves() {
      if (!this.sendCallback || !this.touchEnabled) {
        this.pendingMoves.clear();
        return;
      }

      for (const [pointerId, data] of this.pendingMoves.entries()) {
        const buffer = OmniOSProtocol.encodeBinaryTouch(
          OmniOSProtocol.OPCODES.TOUCH_MOVE,
          pointerId,
          data.normX,
          data.normY,
          data.time
        );
        this.sendCallback(buffer);
      }
      this.pendingMoves.clear();
    }

    handlePointerUp(e) {
      if (!this.activePointers.has(e.pointerId)) return;
      this.activePointers.delete(e.pointerId);
      this.pendingMoves.delete(e.pointerId);

      if (!this.touchEnabled) return;

      const { normX, normY } = this.getNormalizedCoordinates(e.clientX, e.clientY);

      if (this.sendCallback) {
        const buffer = OmniOSProtocol.encodeBinaryTouch(
          OmniOSProtocol.OPCODES.TOUCH_UP,
          e.pointerId,
          normX,
          normY,
          Date.now()
        );
        this.sendCallback(buffer);
      }
    }

    handlePointerCancel(e) {
      if (!this.activePointers.has(e.pointerId)) return;
      this.activePointers.delete(e.pointerId);
      this.pendingMoves.delete(e.pointerId);

      if (this.sendCallback) {
        const buffer = OmniOSProtocol.encodeBinaryTouch(
          OmniOSProtocol.OPCODES.TOUCH_CANCEL,
          e.pointerId,
          0,
          0,
          Date.now()
        );
        this.sendCallback(buffer);
      }
    }

    destroy() {
      if (this.container) {
        this.container.removeEventListener('pointerdown', this.onPointerDown);
      }
      window.removeEventListener('pointermove', this.onPointerMove);
      window.removeEventListener('pointerup', this.onPointerUp);
      window.removeEventListener('pointercancel', this.onPointerCancel);
      if (this.feedbackLayer && this.feedbackLayer.parentNode) {
        this.feedbackLayer.parentNode.removeChild(this.feedbackLayer);
      }
    }
  }

  OmniOS.TouchController = TouchController;
})(window.OmniOS);
