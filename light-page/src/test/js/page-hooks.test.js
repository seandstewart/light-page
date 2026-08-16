const fs = require("fs");
const path = require("path");
const assert = require("node:assert");
const test = require("node:test");
const { JSDOM } = require("jsdom");

const HOOKS_PATH = path.join(__dirname, "../../main/assets/page-hooks.js");
const BASE_CSS_PATH = path.join(__dirname, "../../main/assets/light-page-theme.css");
const READER_CSS_PATH = path.join(__dirname, "../../main/assets/reader-theme.css");

const baseCss = fs.readFileSync(BASE_CSS_PATH, "utf8");
const readerCss = fs.readFileSync(READER_CSS_PATH, "utf8");
const hooksTemplate = fs.readFileSync(HOOKS_PATH, "utf8");

const ensureStyleJs = `
const ensureStyle = (id, css) => {
  let s = document.getElementById(id);
  if (!s) {
    s = document.createElement("style");
    s.id = id;
    (document.head || document.documentElement).appendChild(s);
  }
  s.textContent = css;
};
`;

function bootHooks(html = "<!DOCTYPE html><html><head></head><body></body></html>", url = "https://example.com/") {
  const dom = new JSDOM(html, {
    url,
    runScripts: "dangerously",
  });
  const window = dom.window;
  const document = window.document;

  const payload = hooksTemplate
    .replace("__ENSURE_STYLE__", ensureStyleJs)
    .replace("__BASE_CSS__", JSON.stringify(baseCss))
    .replace("__READER_CSS__", JSON.stringify(readerCss));

  window.eval(payload);

  return { window, document, close: () => window.close() };
}

test("ensureStyle creates and updates the base theme style element", () => {
  const { document, close } = bootHooks();
  try {
    const style = document.getElementById("__light_base_theme");
    assert.ok(style, "style element should be created");
    assert.ok(style.textContent.includes("--light-bg"), "CSS should be applied");
  } finally {
    close();
  }
});

test("install is idempotent", () => {
  const { window, close } = bootHooks();
  try {
    let refreshCount = 0;
    const originalRefresh = window.__lightToolRefresh;
    window.__lightToolRefresh = () => {
      refreshCount++;
      originalRefresh();
    };

    window.eval(
      hooksTemplate
        .replace("__ENSURE_STYLE__", ensureStyleJs)
        .replace("__BASE_CSS__", JSON.stringify(baseCss))
        .replace("__READER_CSS__", JSON.stringify(readerCss))
    );

    assert.strictEqual(window.__lightToolInstalled, true, "flag should still be true");
    assert.strictEqual(
      refreshCount,
      1,
      "second install should only call refresh once"
    );
  } finally {
    close();
  }
});

function debounceTest(name, act) {
  test(name, (t, done) => {
    const { window, document, close } = bootHooks();
    let refreshCount = 0;
    const originalRefresh = window.__lightToolRefresh;
    window.__lightToolRefresh = () => {
      refreshCount++;
      originalRefresh();
    };

    act(window, document);

    setTimeout(() => {
      try {
        assert.strictEqual(refreshCount, 1, "burst should coalesce into one refresh");
        done();
      } finally {
        close();
      }
    }, 300);
  });
}

debounceTest("pushState triggers a debounced refresh", (window) => {
  window.history.pushState({}, "", "/route-a");
  window.history.pushState({}, "", "/route-b");
});

debounceTest("hashchange triggers a debounced refresh", (window) => {
  window.location.hash = "#section-1";
  window.location.hash = "#section-2";
});

debounceTest("MutationObserver schedules a refresh on late DOM changes", (window, document) => {
  const div = document.createElement("div");
  document.body.appendChild(div);
});

test("reader is enabled by default and can be toggled", () => {
  const html = `
    <!DOCTYPE html>
    <html><head></head><body>
      <article>
        <p>${"Lorem ipsum dolor sit amet. ".repeat(30)}</p>
        <p>${"Consectetur adipiscing elit. ".repeat(30)}</p>
        <p>${"Sed do eiusmod tempor incididunt. ".repeat(30)}</p>
      </article>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    assert.strictEqual(window.__lightReaderEnabled, true, "reader should be enabled by default");
    window.__lightSetReaderEnabled(false);
    assert.strictEqual(window.__lightReaderEnabled, false, "reader should be disabled");
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "original should be visible");
    window.__lightSetReaderEnabled(true);
    assert.strictEqual(window.__lightReaderEnabled, true, "reader should be re-enabled");
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), true, "reader should be applied");
  } finally {
    close();
  }
});

test("reader skips ineligible pages", () => {
  const html = `<!DOCTYPE html><html><head></head><body><p>short</p></body></html>`;
  const { window, document, close } = bootHooks(html, "https://example.com/search");
  try {
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "search page should not get reader");
  } finally {
    close();
  }
});

test("system dark mode applies dark theme class", () => {
  const { window, document, close } = bootHooks();
  try {
    assert.strictEqual(document.documentElement.classList.contains("__light_dark_mode"), false, "default should be light");
    window.__lightSystemDarkMode = true;
    window.__lightApplySystemTheme();
    assert.strictEqual(document.documentElement.classList.contains("__light_dark_mode"), true, "dark class should be applied");
    window.__lightSystemDarkMode = false;
    window.__lightApplySystemTheme();
    assert.strictEqual(document.documentElement.classList.contains("__light_dark_mode"), false, "dark class should be removed");
  } finally {
    close();
  }
});
