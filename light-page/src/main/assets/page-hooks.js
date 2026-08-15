(() => {
  if (window.__lightToolInstalled) {
    window.__lightToolRefresh && window.__lightToolRefresh();
    return;
  }
  window.__lightToolInstalled = true;

  const ensureStyle = (id, css) => {
    let s = document.getElementById(id);
    if (!s) {
      s = document.createElement("style");
      s.id = id;
      (document.head || document.documentElement).appendChild(s);
    }
    s.textContent = css;
  };

  window.__lightToolRefresh = () => {
    ensureStyle("__light_base_theme", __BASE_CSS__);
  };

  window.__lightToolRefresh();
})();
