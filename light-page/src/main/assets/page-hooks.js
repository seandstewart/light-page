(() => {
  if (window.__lightToolInstalled) {
    window.__lightToolRefresh && window.__lightToolRefresh();
    return;
  }
  window.__lightToolInstalled = true;
  window.__lightReaderEnabled = true;
  window.__lightReaderApplied = false;

  __ENSURE_STYLE__;

  const READER_ROOT_ID = "__light_reader_root";
  const HIDE_CLASS = "__light_reader_hidden";
  const MIN_PROSE_LENGTH = 400;
  const MIN_PARAGRAPHS = 3;

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
    pathContains(["search", "find"]) ||
    document.querySelector('input[type="search"], [role="search"]') !== null;

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

  const scoreCandidate = (el) => {
    if (!el) return -1;
    const text = (el.innerText || el.textContent || "").trim();
    if (text.length < 200) return -1;
    const paragraphs = el.querySelectorAll("p").length;
    const links = el.querySelectorAll("a").length;
    const linkRatio = text.length > 0 ? links / text.length : 0;
    const headings = el.querySelectorAll("h1, h2, h3, h4, h5, h6").length;
    return text.length + paragraphs * 60 + headings * 120 - linkRatio * 2000;
  };

  const findBestCandidate = () => {
    const selectors = [
      "article",
      "main",
      '[role="main"]',
      '.content[data-testid="tweetDetail"]',
      ".content",
      ".post",
      ".entry",
      ".article",
      "[itemprop='articleBody']",
    ];
    let best = null;
    let bestScore = -1;
    for (const selector of selectors) {
      const el = document.querySelector(selector);
      if (el) {
        const score = scoreCandidate(el);
        if (score > bestScore) {
          best = el;
          bestScore = score;
        }
      }
    }
    if (!best || bestScore < 100) {
      const candidates = document.querySelectorAll("div, section");
      for (let i = 0; i < candidates.length; i++) {
        const score = scoreCandidate(candidates[i]);
        if (score > bestScore) {
          best = candidates[i];
          bestScore = score;
        }
      }
    }
    return best || document.body;
  };

  const sanitize = (clone) => {
    const removeSelectors = [
      "script",
      "style",
      "noscript",
      "iframe",
      "canvas",
      "nav",
      "header",
      "footer",
      "aside",
      '[role="navigation"]',
      '[role="banner"]',
      '[role="complementary"]',
      ".ad",
      ".advertisement",
      ".sidebar",
      ".comments",
      ".comment",
      ".related",
      ".share",
      ".social",
      ".newsletter",
      ".subscribe",
      ".popup",
      ".modal",
      ".cookie",
      ".consent",
    ];
    removeSelectors.forEach((sel) => {
      clone.querySelectorAll(sel).forEach((el) => el.remove());
    });
    // Remove inline event handlers that could interfere with the reader. Keep
    // onload/onerror on media elements so image lazy-loading and error reporting
    // continue to work.
    clone.querySelectorAll("*").forEach((el) => {
      el.removeAttribute("onclick");
      const tag = el.tagName.toLowerCase();
      if (tag !== "img" && tag !== "video" && tag !== "audio" && tag !== "picture") {
        el.removeAttribute("onload");
        el.removeAttribute("onerror");
      }
      // Strip inline styles from non-media elements; media sizing styles are
      // preserved because the reader CSS limits max-width anyway.
      if (tag !== "img" && tag !== "video" && tag !== "audio" && tag !== "picture") {
        el.removeAttribute("style");
      }
    });
    // Remove empty text containers that carry no semantic content. Divs and
    // sections are kept because they often provide layout spacing.
    clone.querySelectorAll("p, span").forEach((el) => {
      if (!el.textContent.trim() && !el.querySelector("img, video, svg, table, iframe, canvas, audio, picture")) {
        el.remove();
      }
    });
    return clone;
  };

  const reportReaderApplied = () => {
    const applied = !!window.__lightReaderApplied;
    if (window.__lightReaderBridge && window.__lightReaderBridge.onReaderApplied) {
      window.__lightReaderBridge.onReaderApplied(applied);
    }
  };

  const applyReaderMode = () => {
    if (!isReaderEligible()) {
      restoreOriginal();
      return;
    }
    restoreOriginal();
    const candidate = findBestCandidate();
    if (!candidate || candidate === document.body) {
      restoreOriginal();
      return;
    }
    const clone = candidate.cloneNode(true);
    sanitize(clone);
    const root = document.createElement("div");
    root.id = READER_ROOT_ID;
    root.appendChild(clone);
    document.body.appendChild(root);
    document.documentElement.classList.add(HIDE_CLASS);
    window.__lightReaderApplied = true;
    reportReaderApplied();
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
    if (on) applyReaderMode();
    else restoreOriginal();
  };

  window.__lightToolRefresh = () => {
    ensureStyle("__light_base_theme", __BASE_CSS__);
    ensureStyle("__light_reader_theme", __READER_CSS__);
    if (window.__lightReaderEnabled) {
      applyReaderMode();
    } else {
      restoreOriginal();
    }
  };

  // Conservative open-shadow-root theming pass (M2.7).
  const themeShadowRoots = () => {
    try {
      const all = document.querySelectorAll("*");
      for (let i = 0; i < all.length; i++) {
        const el = all[i];
        const shadow = el.shadowRoot;
        if (shadow && shadow.mode === "open") {
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
    originalRefresh();
    themeShadowRoots();
  };

  window.__lightToolRefresh();
})();
