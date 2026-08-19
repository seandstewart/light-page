(() => {
  if (window.__lightToolInstalled) {
    window.__lightToolRefresh && window.__lightToolRefresh();
    return;
  }
  window.__lightToolInstalled = true;
  window.__lightReaderApplied = false;

  if (typeof window.__lightReaderEnabled === "undefined") window.__lightReaderEnabled = true;
  if (typeof window.__lightReaderForced === "undefined") window.__lightReaderForced = false;
  if (typeof window.__lightCssInjectionEnabled === "undefined") window.__lightCssInjectionEnabled = true;
  if (typeof window.__lightUseDarkTheme === "undefined") window.__lightUseDarkTheme = false;

  __ENSURE_STYLE__;

  const READER_ROOT_ID = "__light_reader_root";
  const HIDE_CLASS = "__light_reader_hidden";
  const MIN_PROSE_LENGTH = 400;
  const MIN_PARAGRAPHS = 3;
  const MAX_ATTEMPTS = 3;
  const RETRY_DELAY_MS = 500;

  let timer = null;
  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(() => window.__lightToolRefresh(), 500);
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

  const pathContains = (terms) => {
    const path = location.pathname.toLowerCase();
    return terms.some((t) => path.includes(t));
  };

  const titleContains = (terms) => {
    const title = document.title.toLowerCase();
    return terms.some((t) => title.includes(t));
  };

  const isAuthPage = () =>
    pathContains(["login", "signin", "signup", "auth", "password", "register", "account"]) ||
    titleContains(["login", "sign in", "sign up", "auth", "password"]);

  const isSearchPage = () =>
    pathContains(["search", "find"]);

  const isCheckoutPage = () =>
    pathContains(["checkout", "cart", "payment", "billing", "order"]);

  const isEditorPage = () =>
    pathContains(["editor", "compose", "write", "post", "publish"]) ||
    document.querySelector('[contenteditable], textarea[contenteditable="true"]') !== null;

  const isDashboardPage = () =>
    pathContains(["dashboard", "admin", "control", "settings", "profile"]);

  const isMapPage = () =>
    pathContains(["map", "maps", "directions"]);

  const isMediaPage = () => {
    const path = location.pathname.toLowerCase();
    const host = location.hostname.toLowerCase();
    const mediaHosts = ["youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "instagram.com", "twitter.com", "x.com", "twitch.tv"];
    return (
      mediaHosts.some((h) => host === h || host.endsWith("." + h)) ||
      path.includes("video") ||
      path.includes("watch") ||
      path.includes("media") ||
      path.includes("gallery")
    );
  };

  const isReaderEligible = () => {
    if (
      isAuthPage() ||
      isSearchPage() ||
      isCheckoutPage() ||
      isEditorPage() ||
      isDashboardPage() ||
      isMapPage() ||
      isMediaPage()
    ) {
      return false;
    }
    const paragraphs = document.querySelectorAll("article p, main p, [role=main] p, body p");
    let textLength = 0;
    let paragraphCount = 0;
    for (let i = 0; i < paragraphs.length; i++) {
      const text = paragraphs[i].textContent.trim();
      if (text.length > 20) {
        textLength += text.length;
        paragraphCount++;
      }
    }
    return textLength >= MIN_PROSE_LENGTH && paragraphCount >= MIN_PARAGRAPHS;
  };

  const hasSignificantInteractiveContent = (html) => {
    const temp = document.createElement("div");
    temp.innerHTML = html;
    const paragraphs = temp.querySelectorAll("p").length;
    const interactive = temp.querySelectorAll("form, input, textarea, select, button").length;
    return interactive > 0 && interactive >= paragraphs;
  };

  const reportReaderApplied = () => {
    const applied = !!window.__lightReaderApplied;
    if (window.__lightReaderBridge && window.__lightReaderBridge.onReaderApplied) {
      window.__lightReaderBridge.onReaderApplied(applied);
    }
  };

  const reportReaderError = (reason) => {
    if (window.__lightReaderBridge && window.__lightReaderBridge.onReaderError) {
      window.__lightReaderBridge.onReaderError(reason);
    }
  };

  const applyReaderMode = async (force = false) => {
    restoreOriginal();

    if (!force && !isReaderEligible()) {
      reportReaderError("NOT_ELIGIBLE");
      reportReaderApplied(false);
      return;
    }

    if (typeof Readability === "undefined" || typeof DOMPurify === "undefined") {
      reportReaderError("LIBRARY_MISSING");
      reportReaderApplied(false);
      return;
    }

    let article = null;
    try {
      let attempt = 0;
      while (attempt < MAX_ATTEMPTS) {
        article = new Readability(document.cloneNode(true)).parse();
        if (article && article.content) break;
        attempt++;
        if (attempt < MAX_ATTEMPTS) {
          await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
        }
      }

      if (!article || !article.content) {
        reportReaderError("NOT_ELIGIBLE");
        reportReaderApplied(false);
        return;
      }

      const clean = DOMPurify.sanitize(article.content, {
        ALLOWED_TAGS: [
          "h1", "h2", "h3", "h4", "h5", "h6",
          "p", "div", "span", "a", "ul", "ol", "li",
          "strong", "b", "em", "i", "blockquote", "pre", "code", "hr", "br",
          "img", "figure", "figcaption", "picture", "source",
          "table", "thead", "tbody", "tr", "th", "td",
          "sup", "sub", "small", "dl", "dt", "dd",
          "form", "input", "textarea", "select", "button"
        ],
        ALLOWED_ATTR: ["href", "src", "alt", "title", "id", "class", "name", "dir", "lang", "colspan", "rowspan", "start", "type", "value"],
        ALLOW_DATA_ATTR: false
      });

      const temp = document.createElement("div");
      temp.innerHTML = clean;
      const cleanText = (temp.textContent || "").trim();
      if (cleanText.length < MIN_PROSE_LENGTH) {
        reportReaderError("TOO_SHORT");
        reportReaderApplied(false);
        return;
      }

      if (hasSignificantInteractiveContent(clean)) {
        reportReaderError("INTERACTIVE");
        reportReaderApplied(false);
        return;
      }

      const root = document.createElement("div");
      root.id = READER_ROOT_ID;

      const header = document.createElement("div");
      header.className = "__light_reader_header";

      const title = document.createElement("h1");
      title.textContent = article.title || document.title;
      header.appendChild(title);

      if (article.byline) {
        const byline = document.createElement("p");
        byline.className = "__light_reader_byline";
        byline.textContent = article.byline;
        header.appendChild(byline);
      }

      const body = document.createElement("div");
      body.className = "__light_reader_body";
      body.innerHTML = clean;

      root.appendChild(header);
      root.appendChild(body);
      document.body.appendChild(root);
      document.documentElement.classList.add(HIDE_CLASS);
      window.__lightReaderApplied = true;
      reportReaderApplied();
    } catch (e) {
      reportReaderError("EXCEPTION");
      reportReaderApplied(false);
    }
  };

  const restoreOriginal = () => {
    const root = document.getElementById(READER_ROOT_ID);
    if (root) root.remove();
    document.documentElement.classList.remove(HIDE_CLASS);
    window.__lightReaderApplied = false;
    reportReaderApplied();
  };

  window.__lightSetReaderEnabled = (on) => {
    window.__lightReaderEnabled = !!on;
    window.__lightReaderForced = true;
    if (on) return applyReaderMode(true);
    restoreOriginal();
    return Promise.resolve();
  };

  const applySystemTheme = () => {
    const useDark = !!window.__lightUseDarkTheme;
    document.documentElement.classList.toggle("__light_dark_mode", useDark);
  };

  window.__lightApplySystemTheme = applySystemTheme;

  window.__lightSetCssInjectionEnabled = (on) => {
    window.__lightCssInjectionEnabled = !!on;
    const base = document.getElementById("__light_base_theme");
    const reader = document.getElementById("__light_reader_theme");
    if (on) {
      if (!base) ensureStyle("__light_base_theme", __BASE_CSS__);
      if (!reader) ensureStyle("__light_reader_theme", __READER_CSS__);
    } else {
      if (base) base.remove();
      if (reader) reader.remove();
    }
    window.__lightToolRefresh();
  };

  window.__lightToolRefresh = () => {
    applySystemTheme();
    if (window.__lightCssInjectionEnabled) {
      ensureStyle("__light_base_theme", __BASE_CSS__);
      ensureStyle("__light_reader_theme", __READER_CSS__);
    }
    if (window.__lightCssInjectionEnabled && window.__lightReaderEnabled) {
      return applyReaderMode(window.__lightReaderForced);
    }
    restoreOriginal();
    return Promise.resolve();
  };

  // Conservative open-shadow-root theming pass (M2.7).
  const themeShadowRoots = () => {
    if (!window.__lightCssInjectionEnabled) return;
    try {
      const all = document.querySelectorAll("*");
      for (let i = 0; i < all.length; i++) {
        const el = all[i];
        const shadow = el.shadowRoot;
        if (shadow && shadow.mode === "open") {
          el.classList.toggle("__light_dark_mode", window.__lightUseDarkTheme);
          let s = shadow.getElementById("__light_base_theme");
          if (!s) {
            s = document.createElement("style");
            s.id = "__light_base_theme";
            shadow.appendChild(s);
          }
          s.textContent = __BASE_CSS__;
        }
      }
    } catch (e) {
      // Shadow-root access is best-effort; never crash the page.
    }
  };

  const originalRefresh = window.__lightToolRefresh;
  window.__lightToolRefresh = () => {
    const result = originalRefresh();
    themeShadowRoots();
    return result;
  };

  let activeInputElement = null;

  const isTextInput = (el) => {
    const tag = el.tagName;
    if (tag === "TEXTAREA") return true;
    if (tag === "INPUT") {
      const type = el.type || "text";
      const skip = [
        "button", "submit", "reset", "image", "hidden", "checkbox", "radio",
        "file", "color", "date", "datetime-local", "month", "week", "time", "range"
      ];
      return !skip.includes(type.toLowerCase());
    }
    if (el.isContentEditable || el.getAttribute("contenteditable") === "true") return true;
    return false;
  };

  const getInputLabel = (el) => {
    let label = el.placeholder || "";
    if (!label && el.id) {
      const safeId = typeof CSS !== "undefined" && CSS.escape ? CSS.escape(el.id) : el.id.replace(/"/g, '\\"');
      const lab = document.querySelector('label[for="' + safeId + '"]');
      if (lab) label = lab.textContent || "";
    }
    if (!label) {
      const aria = el.getAttribute("aria-label");
      if (aria) label = aria;
    }
    return label.trim();
  };

  const getInputValue = (el) => {
    if (el.isContentEditable || el.getAttribute("contenteditable") === "true") {
      return el.innerText || el.textContent || "";
    }
    return el.value || "";
  };

  const setInputValue = (el, value) => {
    if (el.isContentEditable || el.getAttribute("contenteditable") === "true") {
      el.innerText = value;
      el.textContent = value;
    } else {
      el.value = value;
    }
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    el.blur();
  };

  document.addEventListener("focusin", (e) => {
    const el = e.target;
    if (!isTextInput(el)) return;
    if (!window.__lightInputBridge || !window.__lightInputBridge.onFocus) return;
    if (activeInputElement) return;
    activeInputElement = el;
    const value = getInputValue(el);
    const label = getInputLabel(el);
    el.blur();
    window.__lightInputBridge.onFocus(value, label);
  });

  window.__lightSetInputValue = (value) => {
    if (!activeInputElement) return;
    setInputValue(activeInputElement, value);
    activeInputElement = null;
  };

  window.__lightCancelInput = () => {
    if (activeInputElement) {
      activeInputElement.blur();
      activeInputElement = null;
    }
  };

  window.__lightSetState = (state) => {
    window.__lightReaderEnabled = state.readerRequested;
    window.__lightReaderForced = state.readerForced;
    window.__lightCssInjectionEnabled = state.cssInjectionEnabled;
    window.__lightUseDarkTheme = state.pageTheme === "DARK";
    window.__lightToolRefresh();
  };

  window.__lightToolRefresh();
})();
