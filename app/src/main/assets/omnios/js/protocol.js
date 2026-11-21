/**
 * OmniOS - Compact Bidirectional SDUI & Touch Protocol
 * Ultra-compact serialization for low-latency casting and server-driven UI.
 * Compatible with Browser, Node.js, and Android Kotlin/Ktor server.
 */

(function (root, factory) {
  if (typeof exports === 'object' && typeof module !== 'undefined') {
    module.exports = factory();
  } else if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else {
    root.OmniOSProtocol = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const OPCODES = {
    // Touch & Pointer Telemetry (Compact Binary / JSON)
    TOUCH_DOWN: 0x01,
    TOUCH_MOVE: 0x02,
    TOUCH_UP: 0x03,
    TOUCH_CANCEL: 0x04,
    KEY_EVENT: 0x05,

    // Window & SDUI Interaction
    WINDOW_ACTION: 0x10,
    APP_ACTION: 0x11,

    // Server-Driven State Updates
    STATE_DELTA: 0x20,
    FULL_SYNC: 0x21,
    SYNC_REQUEST: 0x22,

    // Admin Host Policy & Control
    ADMIN_CONFIG: 0x30,
    ADMIN_COMMAND: 0x31,

    // Keepalive & Telemetry
    HEARTBEAT: 0x40,
    HEARTBEAT_ACK: 0x41,

    // Connection Control
    DISCONNECT_BARRIER: 0x50,
    SESSION_AUTH: 0x60,
    SESSION_AUTH_OK: 0x61,
    SESSION_AUTH_FAIL: 0x62
  };

  const OP_NAMES = {};
  for (const [k, v] of Object.entries(OPCODES)) {
    OP_NAMES[v] = k;
  }

  /**
   * Encodes a touch event into an ultra-compact 10-byte binary ArrayBuffer:
   * [0]: Opcode (1B)
   * [1]: Pointer ID (1B, 0-255)
   * [2..3]: Normalized X (2B uint16, 0..65535)
   * [4..5]: Normalized Y (2B uint16, 0..65535)
   * [6..9]: Timestamp in ms (4B uint32)
   */
  function encodeBinaryTouch(opcode, pointerId, normX, normY, timestampMs) {
    const buffer = new ArrayBuffer(10);
    const view = new DataView(buffer);
    view.setUint8(0, opcode & 0xff);
    view.setUint8(1, (pointerId || 0) & 0xff);
    // Clamp to 0.0 .. 1.0
    const clampedX = Math.max(0, Math.min(1, normX || 0));
    const clampedY = Math.max(0, Math.min(1, normY || 0));
    view.setUint16(2, Math.round(clampedX * 65535), false); // Big endian
    view.setUint16(4, Math.round(clampedY * 65535), false);
    const ts = (timestampMs || Date.now()) & 0xffffffff;
    view.setUint32(6, ts, false);
    return buffer;
  }

  /**
   * Decodes a 10-byte binary touch ArrayBuffer
   */
  function decodeBinaryTouch(buffer) {
    let view;
    if (buffer instanceof ArrayBuffer) {
      view = new DataView(buffer);
    } else if (buffer.buffer instanceof ArrayBuffer) {
      view = new DataView(buffer.buffer, buffer.byteOffset, buffer.byteLength);
    } else {
      return null;
    }
    if (view.byteLength < 10) return null;

    const opcode = view.getUint8(0);
    const pointerId = view.getUint8(1);
    const rawX = view.getUint16(2, false);
    const rawY = view.getUint16(4, false);
    const timestampMs = view.getUint32(6, false);

    return {
      op: opcode,
      pointerId,
      normX: rawX / 65535.0,
      normY: rawY / 65535.0,
      timestamp: timestampMs
    };
  }

  /**
   * Packs structured JSON message
   */
  function packJson(op, payload) {
    return JSON.stringify({ op, ...payload });
  }

  /**
   * Unpacks message from string or buffer
   */
  function unpack(data) {
    if (typeof data === 'string') {
      try {
        return JSON.parse(data);
      } catch (e) {
        return { op: -1, error: 'JSON parse error', raw: data };
      }
    }
    if (data instanceof ArrayBuffer || (data && data.buffer instanceof ArrayBuffer)) {
      const touch = decodeBinaryTouch(data);
      if (touch) return touch;
      // If not 10-byte touch, try decoding as UTF-8 JSON
      try {
        const text = new TextDecoder().decode(data);
        return JSON.parse(text);
      } catch (e) {
        return { op: -1, error: 'Binary decode error' };
      }
    }
    return data;
  }

  return {
    OPCODES,
    OP_NAMES,
    encodeBinaryTouch,
    decodeBinaryTouch,
    packJson,
    unpack
  };
});
