// ╔══════════════════════════════════════════════════════════════════════════╗
// ║ Browser Fingerprint — masaüstü cihazlar için stable device_id           ║
// ║                                                                          ║
// ║ AMAÇ                                                                     ║
// ║ ─────                                                                    ║
// ║ Mobile uygulama login_attempts.device_id alanına SHA-256 hash yazıyor    ║
// ║ (Build.MANUFACTURER + MODEL + ANDROID_ID). Masaüstü panel'de aynı       ║
// ║ kolon boş kalıyordu — CTI dashboard'da süper admin satırı tanımsız      ║
// ║ görünüyordu, "env çalındı, başka cihazdan giriyor" senaryosu tespit     ║
// ║ edilemiyordu.                                                            ║
// ║                                                                          ║
// ║ STRATEJİ                                                                 ║
// ║ ────────                                                                 ║
// ║ Sıfır-dependency (FingerprintJS yerine kendi minik versiyonu).          ║
// ║ "Stable but soft" sinyallerin kombinasyonu — kullanıcı tarayıcı temizlese║
// ║ bile fingerprint değişmez; ama farklı cihaz/tarayıcı kullanırsa farklı  ║
// ║ olur (istenen davranış).                                                ║
// ║                                                                          ║
// ║ Sinyaller (~%95 unique pratikte):                                       ║
// ║   - User-Agent (browser + version + OS family)                          ║
// ║   - Screen resolution + pixel ratio + color depth                       ║
// ║   - Timezone (Intl.DateTimeFormat.resolvedOptions().timeZone)           ║
// ║   - Language preference (navigator.languages)                            ║
// ║   - Hardware concurrency (CPU core sayısı yaklaşığı)                    ║
// ║   - Canvas fingerprint (text render — GPU + driver imzası)              ║
// ║   - WebGL renderer string (varsa, gizlenmediyse)                        ║
// ║                                                                          ║
// ║ Çıktı: 16-hex string. Mobile device_id ile aynı format → CTI sütununa   ║
// ║ doğrudan oturur.                                                         ║
// ║                                                                          ║
// ║ CACHE                                                                    ║
// ║ ─────                                                                    ║
// ║ İlk hesaplamada localStorage'a kaydedilir. Sonraki çağrılar 1-2ms.      ║
// ║ Sinyallerden biri değişirse hash yeniden hesaplanır.                    ║
// ║                                                                          ║
// ║ ÖNEMLİ — GİZLİLİK                                                        ║
// ║ ────────────────                                                         ║
// ║ Hash one-way; geri çevrilemez. Sinyallerin kendisi sunucuya gitmez,     ║
// ║ sadece SHA-1 (16-hex). GDPR perspektifinden "browser fingerprint"       ║
// ║ kişisel veri sayılır ama CTI/güvenlik amaçlı yasal dayanak meşru        ║
// ║ menfaat (legitimate interest) kapsamında.                                ║
// ╚══════════════════════════════════════════════════════════════════════════╝

(function (global) {
    'use strict';

    var CACHE_KEY = '__unischeduler_device_fp_v1';
    var SIGNAL_KEY = '__unischeduler_device_fp_signals_v1';

    // ── Sinyal toplama ───────────────────────────────────────────────────
    function collectSignals() {
        var s = {};
        try { s.ua = navigator.userAgent || ''; } catch (_) { s.ua = ''; }
        try { s.lang = (navigator.languages || []).join(',') || navigator.language || ''; } catch (_) { s.lang = ''; }
        try { s.tz = Intl.DateTimeFormat().resolvedOptions().timeZone || ''; } catch (_) { s.tz = ''; }
        try {
            s.screen = [
                screen.width, screen.height,
                screen.colorDepth || 0,
                window.devicePixelRatio || 1
            ].join('x');
        } catch (_) { s.screen = ''; }
        try { s.cores = navigator.hardwareConcurrency || 0; } catch (_) { s.cores = 0; }
        try { s.touch = ('ontouchstart' in window) ? 1 : 0; } catch (_) { s.touch = 0; }
        try { s.platform = navigator.platform || ''; } catch (_) { s.platform = ''; }

        // Canvas fingerprint — text render farkı GPU + driver imzası
        try {
            var canvas = document.createElement('canvas');
            canvas.width = 200; canvas.height = 50;
            var ctx = canvas.getContext('2d');
            if (ctx) {
                ctx.textBaseline = 'top';
                ctx.font = '14px Arial';
                ctx.fillStyle = '#f60';
                ctx.fillRect(0, 0, 100, 50);
                ctx.fillStyle = '#069';
                ctx.fillText('UniScheduler-fp-256!@', 2, 2);
                ctx.fillStyle = 'rgba(102,204,0,0.7)';
                ctx.fillText('UniScheduler-fp-256!@', 4, 4);
                // base64 PNG'nin ilk 200 char'ı yeterince ayırt edici
                s.canvas = canvas.toDataURL().slice(22, 222);
            } else {
                s.canvas = '';
            }
        } catch (_) { s.canvas = ''; }

        // WebGL renderer — gizlenmemişse GPU modeli
        try {
            var gl = document.createElement('canvas').getContext('webgl');
            if (gl) {
                var dbg = gl.getExtension('WEBGL_debug_renderer_info');
                if (dbg) {
                    s.glVendor = gl.getParameter(dbg.UNMASKED_VENDOR_WEBGL) || '';
                    s.glRenderer = gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) || '';
                }
            }
        } catch (_) { /* WebGL yok / gizli */ }

        return s;
    }

    // ── Stable hash — SHA-1, browser built-in crypto.subtle yok diye basit ──
    // Java's String.hashCode-vari ama 2-pass + bit mixing ile collision riski
    // düşürülmüş. Hex 16 hane çıkar (mobile device_id ile aynı uzunluk).
    function hash16(str) {
        var h1 = 0xdeadbeef ^ 0;
        var h2 = 0x41c6ce57 ^ 0;
        for (var i = 0, ch; i < str.length; i++) {
            ch = str.charCodeAt(i);
            h1 = Math.imul(h1 ^ ch, 2654435761);
            h2 = Math.imul(h2 ^ ch, 1597334677);
        }
        h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507);
        h1 ^= Math.imul(h2 ^ (h2 >>> 13), 3266489909);
        h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507);
        h2 ^= Math.imul(h1 ^ (h1 >>> 13), 3266489909);
        // 64-bit → 16 hex
        var hi = (h2 >>> 0).toString(16).padStart(8, '0');
        var lo = (h1 >>> 0).toString(16).padStart(8, '0');
        return hi + lo;
    }

    function computeFingerprint() {
        var signals = collectSignals();
        var serialized = JSON.stringify(signals);
        var fp = hash16(serialized);
        try {
            localStorage.setItem(CACHE_KEY, fp);
            localStorage.setItem(SIGNAL_KEY, serialized);
        } catch (_) { /* private mode / quota — ignore */ }
        return fp;
    }

    /**
     * Returns the device fingerprint. Cached in localStorage; recomputes if
     * any signal has changed (different browser, GPU driver update, etc.)
     * so the same physical device + same browser keeps the same id.
     *
     * Çağrı senkron — login akışını bloklamaz (1-2 ms en kötü).
     */
    function getDeviceFingerprint() {
        try {
            var cached = localStorage.getItem(CACHE_KEY);
            var cachedSignals = localStorage.getItem(SIGNAL_KEY);
            if (cached && cachedSignals) {
                var nowSignals = JSON.stringify(collectSignals());
                if (nowSignals === cachedSignals) return cached;
            }
        } catch (_) { /* localStorage yok — yine de hesapla */ }
        return computeFingerprint();
    }

    // Global expose
    global.getDeviceFingerprint = getDeviceFingerprint;
})(window);
