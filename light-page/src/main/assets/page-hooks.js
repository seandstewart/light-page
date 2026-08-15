(() => {
  if (window.__lightPageThemeInstalled) {
    window.__lightPageThemeRefresh && window.__lightPageThemeRefresh();
    return;
  }
  window.__lightPageThemeInstalled = true;

  __ENSURE_STYLE__;

  let timer = null;
  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(() => window.__lightPageThemeRefresh(), 250);
  };

  ["pushState", "replaceState"].forEach((fn) => {
    const orig = history[fn];
    history[fn] = function (...a) {
      const r = orig.apply(this, a);
      schedule();
      return r;
    };
  });
  addEventListener("popstate", schedule);
  addEventListener("hashchange", schedule);

  new MutationObserver(schedule).observe(document.documentElement, {
    childList: true,
    subtree: true,
  });

  window.__lightPageThemeRefresh = () => {
    ensureStyle("__light_base_theme", __BASE_CSS__);
  };

  window.__lightPageThemeRefresh();
})();
