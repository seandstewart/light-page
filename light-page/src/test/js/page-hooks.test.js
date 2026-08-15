const fs = require("fs");
const path = require("path");
const assert = require("node:assert");
const test = require("node:test");
const { JSDOM } = require("jsdom");

const HOOKS_PATH = path.join(__dirname, "../../main/assets/page-hooks.js");
const CSS_PATH = path.join(__dirname, "../../main/assets/light-page-theme.css");

const baseCss = fs.readFileSync(CSS_PATH, "utf8");
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

function bootHooks() {
  const dom = new JSDOM("<!DOCTYPE html><html><head></head><body></body></html>", {
    url: "https://example.com/",
    runScripts: "dangerously",
  });
  const window = dom.window;
  const document = window.document;

  const payload = hooksTemplate
    .replace("__ENSURE_STYLE__", ensureStyleJs)
    .replace("__BASE_CSS__", JSON.stringify(baseCss));

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
    const originalRefresh = window.__lightPageThemeRefresh;
    window.__lightPageThemeRefresh = () => {
      refreshCount++;
      originalRefresh();
    };

    window.eval(
      hooksTemplate
        .replace("__ENSURE_STYLE__", ensureStyleJs)
        .replace("__BASE_CSS__", JSON.stringify(baseCss))
    );

    assert.strictEqual(window.__lightPageThemeInstalled, true, "flag should still be true");
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
    const originalRefresh = window.__lightPageThemeRefresh;
    window.__lightPageThemeRefresh = () => {
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
