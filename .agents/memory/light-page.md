# Light Page — Persistent Findings

## Project

- **Repo:** `seandstewart/light-page`
- **Issue tracker:** GitHub Issues, operated via `gh` CLI.
- **Build runner:** `just` (see `justfile`) or `./gradlew` directly.
- **Tool module:** `tool/src/main/kotlin/com/thelightphone/sample/`
- **SDK modules:** `sdk/client`, `sdk/ui`, `sdk/shared` (local composite build).

## Environment

- On this machine, Gradle requires `JAVA_HOME` set to the Homebrew OpenJDK 17 path:
  ```bash
  export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home
  ```
- Gradle emits many "configuration resolved during configuration time" warnings; these are noise and do not block the build.
- `:tool` builds depend on the local `:sdk:client` module, which transitively exposes `sdk:ui`.

## SDK / API Quirks

- `LightTextVariant` **does not** include `Body`. Use `LightTextVariant.Copy` (or `Paragraph`/`Detail`) for body text.
- `LightBottomBar` accepts up to 5 items (`LightBarButton` subclasses). Pass `onClick = null` to render a disabled item.
- `LightScreen<Unit, BrowserViewModel>` exposes `goBack()`; overriding it is the cleanest way to handle "WebView back or screen exit" behavior.
- The SDK plugin auto-generates the manifest and already injects `android.permission.INTERNET`; no manual manifest entry is needed for M1.
- The `WebView` reference should live in the screen, **never** in the `ViewModel`.

## Issue Scope Boundaries

- **Issue #3** = M1 shell tasks only: `BrowserUiState`, `BrowserViewModel` flow, `AndroidView` WebView host, nav/status rows (M1.1, M1.2, M1.6, M1.7).
- **Issue #4** = M1 security: hardened WebView settings, `BrowserPolicy`, scheme allowlist, TLS cancel, lint gate (M1.3, M1.4, M1.5, M1.8).
- Do not implement security/policy WebView behavior while working on #3.

## Implementation Notes

- Use a minimal state-reporting `WebViewClient` for #3; security/policy client comes in #4.
- Nav row for #3 should be Back / Forward / Reload. Reader toggle and URL modal are later milestones.
- `BrowserUiState` spec includes future fields (`readerRequested`, `readerApplied`, `urlEditorVisible`, `error`) so they can be wired later without another state rewrite.
- `AGENTS.md` currently has an uncommitted local modification unrelated to the M1 shell work.

## Recent Delivered Work

- Implemented issue #3 and pushed as `1d689fa feat(tool): implement M1 shell with WebView host and nav/status rows`.
- Closed GitHub issue #3 with a summary comment.
