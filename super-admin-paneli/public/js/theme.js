// ─────────────────────────────────────────────────────────────────────────
// UniScheduler Panel — Theme switcher (light / dark / system)
//
// `panelTheme` localStorage key'inde "light" | "dark" | "system" tutar.
// Erken çalışır (DOMContentLoaded öncesi inline init için aşağıdaki IIFE
// hazır), böylece sayfa açılışında "light flash" görünmez.
//
// System modunda matchMedia('(prefers-color-scheme: dark)') dinleyici aktif.
// ─────────────────────────────────────────────────────────────────────────
(function () {
  const STORAGE_KEY = 'panelTheme';
  let mediaQuery = null;

  function effectiveTheme(preference) {
    if (preference === 'light' || preference === 'dark') return preference;
    // system
    return (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
      ? 'dark' : 'light';
  }

  function applyTheme(preference) {
    const eff = effectiveTheme(preference);
    document.documentElement.setAttribute('data-theme', eff);
    // System modunda OS değişikliğini takip et
    if (mediaQuery && mediaQuery.removeEventListener) {
      mediaQuery.removeEventListener('change', onSystemChange);
    }
    if (preference === 'system' && window.matchMedia) {
      mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      if (mediaQuery.addEventListener) {
        mediaQuery.addEventListener('change', onSystemChange);
      }
    }
  }

  function onSystemChange(e) {
    document.documentElement.setAttribute('data-theme', e.matches ? 'dark' : 'light');
  }

  function getPreference() {
    return localStorage.getItem(STORAGE_KEY) || 'system';
  }

  function setTheme(preference) {
    if (preference !== 'light' && preference !== 'dark' && preference !== 'system') return;
    localStorage.setItem(STORAGE_KEY, preference);
    applyTheme(preference);
    window.dispatchEvent(new CustomEvent('panel:theme-changed', {
      detail: { preference, effective: effectiveTheme(preference) }
    }));
  }

  // Sayfa içeriği yüklenirken erken init — body görünmeden tema attribute set olsun.
  applyTheme(getPreference());

  window.getThemePreference = getPreference;
  window.setTheme = setTheme;
  window.effectiveTheme = () => effectiveTheme(getPreference());
})();
