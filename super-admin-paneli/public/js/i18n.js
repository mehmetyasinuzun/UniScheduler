// ─────────────────────────────────────────────────────────────────────────
// UniScheduler Panel — i18n (TR + EN)
//
// JSON dictionary'leri public/i18n/{tr,en}.json olarak gelir. Window
// genelinde `t(key, vars?)` ve `setLang(lang)` API'sini açar; sayfa
// load'unda kayıtlı dil tercihini (localStorage `panelLang`) okur, yoksa
// tarayıcı diline göre seçer. `data-i18n="key"` attribute'lu her DOM
// elementinin textContent'ini, `data-i18n-attr="attr|key"` ile placeholder/
// title/aria-label gibi attribute'ları çevirir.
//
// Mustache benzeri `{var}` placeholder'ları destekler — `t('sec.freeze_confirm',
// {user: 'admin'})` gibi.
// ─────────────────────────────────────────────────────────────────────────
(function () {
  const STORAGE_KEY = 'panelLang';
  const FALLBACK = 'tr';

  let dict = {};
  let currentLang = FALLBACK;

  // Tarayıcı varsayılanı — Chrome `tr-TR`, Firefox `tr` döner.
  function detectDefault() {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'tr' || saved === 'en') return saved;
    const nav = (navigator.language || navigator.userLanguage || '').toLowerCase();
    return nav.startsWith('tr') ? 'tr' : 'en';
  }

  // Nokta-yollu key okuyucu: t('common.save') → dict.common.save
  function lookup(key) {
    const parts = key.split('.');
    let node = dict;
    for (const p of parts) {
      if (node == null || typeof node !== 'object') return null;
      node = node[p];
    }
    return (typeof node === 'string') ? node : null;
  }

  // `{var}` placeholder'larını doldurur.
  function interpolate(s, vars) {
    if (!vars) return s;
    return s.replace(/\{(\w+)\}/g, (_m, name) => {
      return (name in vars) ? String(vars[name]) : `{${name}}`;
    });
  }

  function t(key, vars) {
    const raw = lookup(key);
    if (raw == null) return key; // dev'de eksik key görünsün
    return interpolate(raw, vars);
  }

  function applyTranslations(root) {
    const scope = root || document;
    // textContent çevirileri
    scope.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.getAttribute('data-i18n');
      const html = el.getAttribute('data-i18n-html') === '1';
      const txt = t(key);
      if (html) el.innerHTML = txt;
      else el.textContent = txt;
    });
    // attribute çevirileri — `data-i18n-attr="placeholder|key,title|key2"`
    scope.querySelectorAll('[data-i18n-attr]').forEach(el => {
      const spec = el.getAttribute('data-i18n-attr');
      spec.split(',').forEach(pair => {
        const [attr, key] = pair.split('|').map(s => s.trim());
        if (attr && key) el.setAttribute(attr, t(key));
      });
    });
    // html lang attribute
    document.documentElement.lang = currentLang;
  }

  async function loadDict(lang) {
    const res = await fetch(`i18n/${lang}.json`, { cache: 'no-store' });
    if (!res.ok) throw new Error(`Failed to load i18n/${lang}.json`);
    dict = await res.json();
  }

  async function setLang(lang) {
    if (lang !== 'tr' && lang !== 'en') return;
    try {
      await loadDict(lang);
      currentLang = lang;
      localStorage.setItem(STORAGE_KEY, lang);
      applyTranslations(document);
      // Listener'lar için event
      window.dispatchEvent(new CustomEvent('panel:lang-changed', { detail: { lang } }));
    } catch (e) {
      console.warn('i18n setLang failed', e);
    }
  }

  // Sayfa açılışında başlangıç dilini yükle.
  document.addEventListener('DOMContentLoaded', async () => {
    const initial = detectDefault();
    await setLang(initial);
  });

  // Window'a açıkça expose et — diğer scriptler kullanabilsin.
  window.t = t;
  window.setLang = setLang;
  window.getLang = () => currentLang;
  window.applyTranslations = applyTranslations;
})();
