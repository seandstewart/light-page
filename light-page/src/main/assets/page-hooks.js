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

  let timer = null;
  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(() => window.__lightToolRefresh(), 250);
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

  window.__lightToolRefresh = () => {
    ensureStyle("__light_base_theme", __BASE_CSS__);
  };

  window.__lightToolRefresh();
})();
