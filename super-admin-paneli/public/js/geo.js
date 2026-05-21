// ─────────────────────────────────────────────────────────────────────────
// GeoIP lookup — ip-api.com batch endpoint (ücretsiz, API key gerekmez).
//
// Rate limit: 45 istek / dakika / IP (cliente). Batch endpoint tek istekle
// 100 IP'ye kadar sorar → CTI sayfası için fazlasıyla yeterli.
//
// Cache: localStorage (`geoCache` key), 7 günlük TTL. Yeniden sorgu yapmaktan
// kaçınırız (rate limit ve performans). Cache miss → batch lookup → sonuçları
// cache'le.
//
// PII: ip-api.com cevabı sadece coğrafi bilgi (ülke, şehir, ASN). Username vb.
// kişisel veri GİTMİYOR — sadece IP gönderiliyor.
//
// API: window.geoLookup(ips: string[]) → Promise<Record<ip, GeoInfo>>
//      window.geoBadge(ip: string) → HTML string (bayrak + ülke kodu)
// ─────────────────────────────────────────────────────────────────────────
(function () {
  const CACHE_KEY = 'geoCache';
  const CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days
  const BATCH_URL = 'http://ip-api.com/batch?fields=status,country,countryCode,city,isp,as,query';
  // NOT: ip-api.com'un ücretsiz endpoint'i sadece HTTP. HTTPS pro paket.
  // Karışık-içerik (mixed content) hatası alırsanız panel'i HTTP'de çalıştırın,
  // veya pro key + HTTPS endpoint kullanın (env'de IPAPI_KEY tanımlı ise sunucu
  // tarafında proxy ekleyebilirsiniz — şimdilik client-side direct).

  function loadCache() {
    try {
      const raw = localStorage.getItem(CACHE_KEY);
      if (!raw) return {};
      const obj = JSON.parse(raw);
      // expired entry'leri filtrele
      const now = Date.now();
      const clean = {};
      for (const k in obj) {
        if (obj[k] && obj[k]._t && now - obj[k]._t < CACHE_TTL_MS) clean[k] = obj[k];
      }
      return clean;
    } catch (_) { return {}; }
  }

  function saveCache(cache) {
    try { localStorage.setItem(CACHE_KEY, JSON.stringify(cache)); } catch (_) {}
  }

  function isPublicIp(ip) {
    if (!ip || typeof ip !== 'string') return false;
    // private / loopback / link-local — sorgu israfı engelle
    if (ip === '127.0.0.1' || ip === '::1') return false;
    if (ip.startsWith('10.')) return false;
    if (ip.startsWith('192.168.')) return false;
    if (ip.startsWith('172.')) {
      const second = parseInt(ip.split('.')[1], 10);
      if (second >= 16 && second <= 31) return false;
    }
    if (ip.startsWith('169.254.')) return false;       // APIPA
    if (ip.startsWith('fc') || ip.startsWith('fd')) return false;   // IPv6 ULA
    if (ip.startsWith('fe80:')) return false;          // IPv6 link-local
    return true;
  }

  let cache = loadCache();

  async function geoLookup(ips) {
    const unique = [...new Set(ips.filter(isPublicIp))];
    const missing = unique.filter(ip => !(ip in cache));

    if (missing.length === 0) return Object.fromEntries(unique.map(ip => [ip, cache[ip]]));

    // ip-api.com batch (POST): max 100 IP per request
    const chunks = [];
    for (let i = 0; i < missing.length; i += 100) chunks.push(missing.slice(i, i + 100));

    for (const chunk of chunks) {
      try {
        const res = await fetch(BATCH_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(chunk)
        });
        if (!res.ok) continue;
        const arr = await res.json();
        const now = Date.now();
        for (const row of arr) {
          if (row && row.query) {
            cache[row.query] = (row.status === 'success')
              ? { country: row.country, countryCode: row.countryCode, city: row.city, isp: row.isp, asn: row.as, _t: now }
              : { error: true, _t: now };
          }
        }
        saveCache(cache);
      } catch (_) {
        // Network hatası: cache miss kalır, kullanıcı IP'yi raw görür
      }
    }
    return Object.fromEntries(unique.map(ip => [ip, cache[ip] || null]));
  }

  // Ülke kodundan bayrak emoji'si — IS04 alpha-2 → Unicode regional indicator.
  function flagFromCC(cc) {
    if (!cc || cc.length !== 2) return '🌐';
    const A = 0x1F1E6;
    const codePoints = [...cc.toUpperCase()].map(c => A + (c.charCodeAt(0) - 65));
    return String.fromCodePoint(...codePoints);
  }

  // Tek IP için badge HTML — escapeHtml lazım, app.js'tekini varsay.
  function geoBadge(ip) {
    if (!ip || ip === '-' || !isPublicIp(ip)) return '';
    const info = cache[ip];
    if (!info || info.error) return '';
    const flag = flagFromCC(info.countryCode);
    const code = (info.countryCode || '').toUpperCase();
    const tip = [info.city, info.country, info.isp ? '(' + info.isp + ')' : null].filter(Boolean).join(', ');
    return ' <span class="geo-badge" title="' + (window.escapeHtml ? window.escapeHtml(tip) : tip) + '">'
        + flag + ' ' + code + '</span>';
  }

  // Cache'i ısıtmak için public API — CTI render edilirken çağır
  // listeyle ısıtma yap, sonra geoBadge() ile her IP'yi süsle.
  window.geoLookup = geoLookup;
  window.geoBadge = geoBadge;
  window.geoFlag = flagFromCC;
  window.geoClearCache = () => { cache = {}; localStorage.removeItem(CACHE_KEY); };
})();
