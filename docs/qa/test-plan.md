# Light Page Test Plan

Testing strategy for the `:light-page` browser/reader tool. The plan splits tests into three layers: unit (fast, JVM-only), functional (JS/headless), and integration (Android runtime).

## 1. Unit tests

Goal: verify pure Kotlin logic without an Android device or WebView.

### Where
`light-page/src/test/kotlin/com/thelightphone/lightpage/`

### Setup
- Add `src/test/kotlin` to the module.
- Add dependencies to `light-page/build.gradle.kts`:
  - `testImplementation(libs.kotlin.test)` (already on the classpath)
  - `testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")` (matches the plugin module)
  - `testImplementation("io.mockk:mockk:1.13.8")` for Android classes (`AssetManager`, `WebView`, `Uri`)
  - `testImplementation("org.robolectric:robolectric:4.12.2")` only if a test truly needs the Android framework; prefer mockk for this layer.
- Configure `tasks.test { useJUnitPlatform() }`.

### What to test

| Class | Focus | Notes |
| ----- | ----- | ----- |
| `BrowserPolicy` | Scheme allowlist: `https`/`about` always allowed; `http` allowed only when `BuildConfig.DEBUG` is `true`. | Mock or shadow `BuildConfig.DEBUG` via test build variant. |
| `BrowserViewModel` | `onWebState` updates the right fields; `toggleReader`, `submitUrl`, `showUrlEditor` mutate state; `defaultStartUrl()` is fixture URL in debug, `example.com` in release. | Use `kotlinx.coroutines.test` to collect `StateFlow` values. |
| `ScriptInjection` | Loads `light-page-theme.css` and `page-hooks.js` from `AssetManager`; replaces `__ENSURE_STYLE__` and `__BASE_CSS__`; produces a syntactically valid JS payload. | Provide a fake `AssetManager` that reads from `src/test/resources/assets/`. |
| `BrowserError.message()` | Each sealed variant renders the exact status text. | No Android dependencies. |

### Excluded from this layer
- `BrowserScreen` Composable UI (covered by screenshot/compose tests if needed later).
- `LightWebViewClient` lifecycle (needs WebView/RenderProcess; see integration).

## 2. Functional tests

Goal: verify the injected JS and CSS behavior in a controlled browser environment.

### Where
`light-page/src/test/js/` or `fixtures/tests/` — a small Node.js test harness.

### Setup
- Use `node` + `jsdom` (or `happy-dom`) to simulate a DOM.
- Add a `package.json` with `jsdom` and a test runner (`node --test` is enough for small suites).
- Run via a `just` recipe: `just test-js`.

### What to test

| Asset | Test | How |
| ----- | ---- | --- |
| `page-hooks.js` | Idempotent install: second run only calls `__lightPageThemeRefresh`. | Load script twice, assert `__lightPageThemeInstalled` is set once and `ensureStyle` invocation count is 2. |
| `page-hooks.js` | SPA hooks trigger debounced refresh. | Call `history.pushState`, `history.replaceState`, dispatch `popstate`/`hashchange`, assert refresh runs after 250 ms and only once per burst. |
| `page-hooks.js` | MutationObserver triggers debounced refresh. | Append a new node, assert refresh scheduled after 250 ms. |
| `page-hooks.js` | `ensureStyle` creates and updates the style element. | Run boot script, assert `#__light_base_theme` exists and textContent equals the injected CSS. |
| `light-page-theme.css` | No `all: unset`, no blanket overlay hiding. | Static CSS parse / string assertions. |
| `page-hooks.js` + `ScriptInjection` | Placeholder replacement yields valid JS. | Reproduce `ScriptInjection.injectBootScript` replacement in a test and `node --check` the result. |

### Fixtures
Reuse the existing `fixtures/m2/` HTML files as visual QA targets, but do not try to automate visual assertions in this layer. Functional tests target the JS contract, not pixel-perfect rendering.

## 3. Integration tests

Goal: verify the full Android runtime path: WebView + lifecycle + JS injection + asset loading.

### Where
`light-page/src/androidTest/kotlin/com/thelightphone/lightpage/`

### Setup
- Add `src/androidTest/kotlin` to the module.
- Add dependencies to `light-page/build.gradle.kts`:
  - `androidTestImplementation("androidx.test:runner:1.6.2")`
  - `androidTestImplementation("androidx.test:rules:1.6.1")`
  - `androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")`
  - `androidTestImplementation("androidx.test.espresso:espresso-web:3.6.1")` (optional; WebView assertions are easier with raw `evaluateJavascript`)
- Use `AndroidJUnitRunner`.
- Serve fixtures from the host during tests, or embed simple HTML in `src/androidTest/assets/`.

### What to test

| Scenario | Test | Notes |
| -------- | ---- | ----- |
| Lifecycle wiring | `onPageCommitVisible` calls `injectBaseTheme`; `onPageFinished` calls `injectBootScript`. | Use a test `WebViewClient` subclass or spy the `ScriptInjection` callbacks. |
| Scheme filtering | `http://` loads in debug; `http://` is blocked in release; `intent://` and `file://` are always blocked. | Build two APK variants and run the same test class, or assert `BrowserPolicy.isAllowed` with the build variant. |
| Base theme application | Load a fixture page with hostile baseline colors and assert the computed style is overridden. | Run `WebView.evaluateJavascript` to read `getComputedStyle(document.body).backgroundColor`. |
| SPA re-theme | Load `spa-navigation.html`, trigger a route change, and assert the new view is themed within 300 ms. | Wait for the debounce window, then query computed styles. |
| Delayed render | Load `delayed-render.html`, wait 2 s, then assert injected nodes are themed. | Simple sleep + JS evaluation. |
| Form controls | Load `form-controls.html`, interact with inputs/checkboxes/radios/selects, assert they remain functional. | Tap events via Espresso Web or `evaluateJavascript` to set values and read them back. |
| Idempotency | Reload the same page and assert no duplicate style elements or multiple observers. | Count `#__light_base_theme` and monkey-patched `history.pushState` wrappers. |
| Error handling | TLS failure cancels the request and surfaces `BrowserError.Tls`. | Use a bad-cert local server or mock `SslErrorHandler`. |

### Test server
For tests that need the `fixtures/m2/` pages, start a small Ktor or Python HTTP server on the host. On the emulator, `10.0.2.2` maps to host loopback. Provide a test helper that waits for the server to be ready before loading the URL.

## Running the layers

| Layer | Command | When |
| ----- | ------- | ---- |
| Unit | `./gradlew :light-page:test` | Every local commit / CI gate. |
| Functional | `just test-js` | Every local commit / CI gate. |
| Integration | `./gradlew :light-page:connectedDebugAndroidTest` (requires emulator/device) | Before merging, nightly, or QA milestone. |

## CI wiring

- Add `lint`, `test`, and `test-js` to the existing `dev-check` recipe.
- Keep `connectedDebugAndroidTest` as a separate CI job because it needs an emulator and is slower.
- Run integration tests on the Light Phone III AVD (API 34, no Play Services) when possible.

## Priority order

1. Unit tests for `BrowserPolicy` and `ScriptInjection` — highest ROI, no device needed.
2. Functional JS tests for `page-hooks.js` — catches SPA/observer regressions cheaply.
3. Integration tests for the WebView lifecycle — needed before declaring M2 done, but require emulator setup.
