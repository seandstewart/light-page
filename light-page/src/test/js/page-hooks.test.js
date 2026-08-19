const fs = require("fs");
const path = require("path");
const assert = require("node:assert");
const test = require("node:test");
const { JSDOM } = require("jsdom");
const { Readability } = require("@mozilla/readability");
const createDOMPurify = require("dompurify");

const HOOKS_PATH = path.join(__dirname, "../../main/assets/page-hooks.js");
const BASE_CSS_PATH = path.join(__dirname, "../../main/assets/light-page-theme.css");
const READER_CSS_PATH = path.join(__dirname, "../../main/assets/reader-theme.css");
const READABILITY_PATH = path.join(__dirname, "../../main/assets/readability.js");
const PURIFY_PATH = path.join(__dirname, "../../main/assets/purify.js");

const baseCss = fs.readFileSync(BASE_CSS_PATH, "utf8");
const readerCss = fs.readFileSync(READER_CSS_PATH, "utf8");
const hooksTemplate = fs.readFileSync(HOOKS_PATH, "utf8");
const readabilityJs = fs.readFileSync(READABILITY_PATH, "utf8");
const purifyJs = fs.readFileSync(PURIFY_PATH, "utf8");

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

  window.eval(readabilityJs);
  window.eval(purifyJs);
  window.DOMPurify = createDOMPurify(window);

  const payload = hooksTemplate
    .replaceAll("__ENSURE_STYLE__", ensureStyleJs)
    .replaceAll("__BASE_CSS__", JSON.stringify(baseCss))
    .replaceAll("__READER_CSS__", JSON.stringify(readerCss));

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

    window.eval(readabilityJs);
    window.eval(purifyJs);
    window.DOMPurify = createDOMPurify(window);
    window.eval(
      hooksTemplate
        .replaceAll("__ENSURE_STYLE__", ensureStyleJs)
        .replaceAll("__BASE_CSS__", JSON.stringify(baseCss))
        .replaceAll("__READER_CSS__", JSON.stringify(readerCss))
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
    }, 600);
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

test("reader and CSS injection are enabled by default", () => {
  const { window, close } = bootHooks();
  try {
    assert.strictEqual(window.__lightReaderEnabled, true, "reader should be enabled by default");
    assert.strictEqual(window.__lightCssInjectionEnabled, true, "CSS injection should be enabled by default");
  } finally {
    close();
  }
});

test("CSS injection can be toggled", () => {
  const { window, document, close } = bootHooks();
  try {
    assert.ok(document.getElementById("__light_base_theme"), "base theme should be present by default");
    window.__lightSetCssInjectionEnabled(false);
    assert.strictEqual(window.__lightCssInjectionEnabled, false, "CSS injection flag should be off");
    assert.ok(!document.getElementById("__light_base_theme"), "base theme should be removed");
    assert.ok(!document.getElementById("__light_reader_theme"), "reader theme should be removed");
    window.__lightSetCssInjectionEnabled(true);
    assert.strictEqual(window.__lightCssInjectionEnabled, true, "CSS injection flag should be on");
    assert.ok(document.getElementById("__light_base_theme"), "base theme should be restored");
    assert.ok(document.getElementById("__light_reader_theme"), "reader theme should be restored");
  } finally {
    close();
  }
});

test("reader is enabled by default and can be toggled", async () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Test</title></head><body>
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
    await window.__lightSetReaderEnabled(true);
    assert.strictEqual(window.__lightReaderEnabled, true, "reader should be re-enabled");
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), true, "reader should be applied");
  } finally {
    close();
  }
});

test("reader extracts article content and drops page noise", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Site Title</title></head><body>
      <header><h1>Site Brand</h1><nav>Home | About</nav></header>
      <article>
        <h1>Real Article Headline</h1>
        <p class="byline">By Jane Doe</p>
        <p>${"Lorem ipsum dolor sit amet. ".repeat(30)}</p>
        <p>${"Consectetur adipiscing elit. ".repeat(30)}</p>
        <p>${"Sed do eiusmod tempor incididunt. ".repeat(30)}</p>
      </article>
      <aside class="sidebar">Ad</aside>
      <footer>Copyright</footer>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    const root = document.getElementById("__light_reader_root");
    assert.ok(root, "reader root should be created");

    const text = root.textContent;
    assert.ok(text.includes("Real Article Headline"), "reader should include the article headline");
    assert.ok(text.includes("Lorem ipsum dolor"), "reader should include article body");
    assert.ok(!text.includes("Site Brand"), "reader should exclude site header noise");
    assert.ok(!text.includes("Ad"), "reader should exclude sidebar ads");
    assert.ok(!text.includes("Copyright"), "reader should exclude footer noise");

    const title = root.querySelector(".__light_reader_header h1");
    assert.ok(title, "reader should render a title element");
    assert.ok(title.textContent.length > 0, "title should be non-empty");
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

test("forced reader mode bypasses eligibility heuristic", async () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Search</title></head><body>
      <h1>Search</h1>
      <input type="search" placeholder="Search...">
      <p>${"Lorem ipsum dolor sit amet. ".repeat(30)}</p>
      <p>${"Consectetur adipiscing elit. ".repeat(30)}</p>
      <p>${"Sed do eiusmod tempor incididunt. ".repeat(30)}</p>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html, "https://example.com/search");
  try {
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "auto-detect should skip ineligible page");
    await window.__lightSetReaderEnabled(true);
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), true, "forced reader should apply despite ineligible page");
  } finally {
    close();
  }
});

test("reader applies to article pages with a header search input", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Article</title></head><body>
      <header>
        <input type="search" placeholder="Search">
        <nav>Home</nav>
      </header>
      <article>
        <h1>Recipe Title</h1>
        <p>${"Lorem ipsum dolor sit amet. ".repeat(30)}</p>
        <p>${"Consectetur adipiscing elit. ".repeat(30)}</p>
        <p>${"Sed do eiusmod tempor incididunt. ".repeat(30)}</p>
      </article>
    </body></html>
  `;
  const { document, close } = bootHooks(html);
  try {
    assert.ok(document.getElementById("__light_reader_root"), "reader root should be created for article");
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), true, "reader should be applied");
  } finally {
    close();
  }
});

test("reader skips pages on search URLs", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Search</title></head><body>
      <h1>Search</h1>
      <input type="search" placeholder="Search...">
      <p>${"Lorem ipsum dolor sit amet. ".repeat(30)}</p>
      <p>${"Consectetur adipiscing elit. ".repeat(30)}</p>
      <p>${"Sed do eiusmod tempor incididunt. ".repeat(30)}</p>
    </body></html>
  `;
  const { document, close } = bootHooks(html, "https://example.com/search");
  try {
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "dedicated search page should not get reader");
    assert.ok(!document.getElementById("__light_reader_root"), "reader root should not be created");
  } finally {
    close();
  }
});

test("reader skips pages with forms", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Contact</title></head><body>
      <h1>Contact Us</h1>
      <p>${"Lorem ipsum dolor sit amet. ".repeat(30)}</p>
      <form>
        <label>Name <input type="text"></label>
        <label>Email <input type="email"></label>
        <label>Message <textarea></textarea></label>
        <button type="submit">Send</button>
      </form>
    </body></html>
  `;
  const { document, close } = bootHooks(html);
  try {
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "form page should not get reader");
    assert.ok(!document.getElementById("__light_reader_root"), "reader root should not be created");
  } finally {
    close();
  }
});

test("reader skips empty SPA shells", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>App</title></head><body>
      <div id="root"></div>
    </body></html>
  `;
  const { document, close } = bootHooks(html, "https://example.com/app");
  try {
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "empty SPA shell should not get reader");
    assert.ok(!document.getElementById("__light_reader_root"), "reader root should not be created");
  } finally {
    close();
  }
});

test("input bridge forwards focus and submit", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Form</title></head><body>
      <form>
        <label for="name">Name</label>
        <input id="name" type="text" value="initial">
      </form>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    let receivedValue = null;
    let receivedLabel = null;
    window.__lightInputBridge = {
      onFocus: (value, label) => {
        receivedValue = value;
        receivedLabel = label;
      }
    };

    const input = document.getElementById("name");
    input.focus();

    assert.strictEqual(receivedValue, "initial", "bridge should receive input value");
    assert.strictEqual(receivedLabel, "Name", "bridge should receive input label");
    assert.strictEqual(document.activeElement, document.body, "input should be blurred to hide system keyboard");

    window.__lightSetInputValue("updated");
    assert.strictEqual(input.value, "updated", "input value should be updated from editor");
  } finally {
    close();
  }
});

test("system theme applies correct CSS class", () => {
  const { window, document, close } = bootHooks();
  try {
    // Standard mapping: system dark mode uses the dark CSS class.
    assert.strictEqual(document.documentElement.classList.contains("__light_dark_mode"), false, "default should be light");
    window.__lightUseDarkTheme = true;
    window.__lightApplySystemTheme();
    assert.strictEqual(document.documentElement.classList.contains("__light_dark_mode"), true, "dark class should be applied");
    window.__lightUseDarkTheme = false;
    window.__lightApplySystemTheme();
    assert.strictEqual(document.documentElement.classList.contains("__light_dark_mode"), false, "dark class should be removed");
  } finally {
    close();
  }
});

test("input bridge cancel closes editor without updating", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Form</title></head><body>
      <input id="name" type="text" value="initial">
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    let received = false;
    window.__lightInputBridge = {
      onFocus: () => { received = true; }
    };

    const input = document.getElementById("name");
    input.focus();
    assert.strictEqual(received, true, "bridge should receive focus");

    window.__lightCancelInput();
    assert.strictEqual(input.value, "initial", "input value should not change");
  } finally {
    close();
  }
});

test("contenteditable input is bridged", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Edit</title></head><body>
      <div id="editor" contenteditable="true">hello</div>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    let receivedValue = null;
    window.__lightInputBridge = {
      onFocus: (value) => { receivedValue = value; }
    };

    const editor = document.getElementById("editor");
    editor.dispatchEvent(new window.Event("focusin", { bubbles: true }));
    assert.strictEqual(receivedValue, "hello", "contenteditable value should be bridged");

    window.__lightSetInputValue("updated");
    assert.strictEqual(editor.textContent, "updated", "contenteditable should be updated");
  } finally {
    close();
  }
});

test("input label uses aria-label", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Form</title></head><body>
      <input id="a" type="text" aria-label="Aria Label" value="x">
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    let receivedLabel = null;
    window.__lightInputBridge = {
      onFocus: (value, label) => { receivedLabel = label; }
    };

    document.getElementById("a").focus();
    assert.strictEqual(receivedLabel, "Aria Label", "aria-label should be used");
  } finally {
    close();
  }
});

test("input label falls back to placeholder", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Form</title></head><body>
      <input id="b" type="text" placeholder="Placeholder Hint" value="y">
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    let receivedLabel = null;
    window.__lightInputBridge = {
      onFocus: (value, label) => { receivedLabel = label; }
    };

    document.getElementById("b").focus();
    assert.strictEqual(receivedLabel, "Placeholder Hint", "placeholder should be used as fallback");
  } finally {
    close();
  }
});

test("reader reports NOT_ELIGIBLE for ineligible pages", async () => {
  const html = `<!DOCTYPE html><html><head><title>App</title></head><body><div id="root"></div></body></html>`;
  const { window, document, close } = bootHooks(html, "https://example.com/search");
  try {
    const errors = [];
    const appliedStates = [];
    window.__lightReaderBridge = {
      onReaderError: (reason) => errors.push(reason),
      onReaderApplied: (applied) => appliedStates.push(applied),
    };

    await window.__lightToolRefresh();
    errors.length = 0;
    appliedStates.length = 0;

    await window.__lightSetReaderEnabled(true);

    assert.strictEqual(errors[errors.length - 1], "NOT_ELIGIBLE", "should report NOT_ELIGIBLE");
    assert.strictEqual(appliedStates[appliedStates.length - 1], false, "should report applied false");
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "original DOM should be visible");
  } finally {
    close();
  }
});

test("reader reports TOO_SHORT for short content", async () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Short</title></head><body>
      <article>
        <h1>Short Article</h1>
        <p>Only a little text.</p>
      </article>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    const errors = [];
    const appliedStates = [];
    window.__lightReaderBridge = {
      onReaderError: (reason) => errors.push(reason),
      onReaderApplied: (applied) => appliedStates.push(applied),
    };

    await window.__lightToolRefresh();
    errors.length = 0;
    appliedStates.length = 0;

    await window.__lightSetReaderEnabled(true);

    assert.strictEqual(errors[errors.length - 1], "TOO_SHORT", "should report TOO_SHORT");
    assert.strictEqual(appliedStates[appliedStates.length - 1], false, "should report applied false");
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "original DOM should be visible");
  } finally {
    close();
  }
});

test("reader reports INTERACTIVE for form-heavy content", async () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Interactive</title></head><body>
      <article>
        <h1>Survey</h1>
        <form>
          <label for="q1">${"Question one with a lot of text so the article is long. ".repeat(15)}</label>
          <input id="q1" type="text">
          <label for="q2">${"Question two with a lot of text so the article is long. ".repeat(15)}</label>
          <input id="q2" type="text">
          <label for="q3">${"Question three with a lot of text so the article is long. ".repeat(15)}</label>
          <textarea id="q3"></textarea>
          <button type="submit">Send</button>
        </form>
      </article>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    const errors = [];
    const appliedStates = [];
    window.__lightReaderBridge = {
      onReaderError: (reason) => errors.push(reason),
      onReaderApplied: (applied) => appliedStates.push(applied),
    };

    await window.__lightToolRefresh();
    errors.length = 0;
    appliedStates.length = 0;

    await window.__lightSetReaderEnabled(true);

    assert.strictEqual(errors[errors.length - 1], "INTERACTIVE", "should report INTERACTIVE");
    assert.strictEqual(appliedStates[appliedStates.length - 1], false, "should report applied false");
    assert.strictEqual(document.documentElement.classList.contains("__light_reader_hidden"), false, "original DOM should be visible");
  } finally {
    close();
  }
});

test("reader retries when parse initially returns null", async () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Retry</title></head><body>
      <article>
        <h1>Retry Article</h1>
        <p>${"Lorem ipsum dolor sit amet. ".repeat(30)}</p>
        <p>${"Consectetur adipiscing elit. ".repeat(30)}</p>
        <p>${"Sed do eiusmod tempor incididunt. ".repeat(30)}</p>
      </article>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    let parseCount = 0;
    const realReadability = window.Readability;
    window.Readability = class MockReadability {
      constructor(doc) {
        this.doc = doc;
      }
      parse() {
        parseCount++;
        if (parseCount === 1) return null;
        return new realReadability(this.doc).parse();
      }
    };

    await window.__lightSetReaderEnabled(true);

    assert.strictEqual(parseCount, 2, "should retry after first null parse");
    assert.ok(document.getElementById("__light_reader_root"), "reader should be applied after retry");
  } finally {
    close();
  }
});

test("__lightSetState updates state and refreshes", async () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>State</title></head><body>
      <article>
        <p>${"Lorem ipsum dolor sit amet. ".repeat(30)}</p>
        <p>${"Consectetur adipiscing elit. ".repeat(30)}</p>
        <p>${"Sed do eiusmod tempor incididunt. ".repeat(30)}</p>
      </article>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    window.__lightSetState({
      readerRequested: true,
      readerForced: true,
      cssInjectionEnabled: true,
      pageTheme: "DARK",
    });
    await window.__lightToolRefresh();

    assert.strictEqual(window.__lightUseDarkTheme, true, "dark theme flag should be set");
    assert.strictEqual(document.documentElement.classList.contains("__light_dark_mode"), true, "dark mode class should be applied");
    assert.ok(document.getElementById("__light_reader_root"), "reader should be applied after state refresh");
  } finally {
    close();
  }
});

test("shadow roots are not themed when CSS injection is disabled", () => {
  const html = `
    <!DOCTYPE html>
    <html><head><title>Shadow</title></head><body>
      <div id="host"></div>
    </body></html>
  `;
  const { window, document, close } = bootHooks(html);
  try {
    const host = document.getElementById("host");
    const shadow = host.attachShadow({ mode: "open" });
    const shadowRoot = document.createElement("div");
    shadow.appendChild(shadowRoot);

    window.__lightSetCssInjectionEnabled(false);
    window.__lightToolRefresh();

    assert.ok(!shadow.getElementById("__light_base_theme"), "shadow root should not get base theme when CSS injection is disabled");
  } finally {
    close();
  }
});
