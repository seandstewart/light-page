# M2 Fixture Visual QA

Covers M2.5 (SPA hooks), M2.6 (debounced MutationObserver), and M2.8 (fixture QA) for the base theme injection pipeline.

## Fixtures

All fixtures live in `fixtures/m2/` and are designed to be served over HTTP (the app sets `allowFileAccess = false`).

| Fixture        | File                  | What it exercises                                                                                                                                               |
| -------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Form controls  | `form-controls.html`  | Buttons, text/email/password/search/number/url inputs, select, textarea, checkbox, radio, range, disabled state. Hostile baseline styles verify theme override. |
| Content types  | `content-types.html`  | Tables, code blocks, images, SVG. Verifies media scales and monochrome theme applies.                                                                           |
| SPA navigation | `spa-navigation.html` | `pushState`, `popstate`, and `hashchange` route changes. Verifies debounced re-theme.                                                                           |
| Delayed render | `delayed-render.html` | Content injected 2 seconds after `onPageFinished`. Verifies MutationObserver picks it up.                                                                       |

## How to run

1. Serve the fixtures:

   ```bash
   just serve-fixtures
   ```

   The default port is `8000`. Use a custom port with `just serve-fixtures 8080`.

2. Determine the host IP reachable from the Android emulator or device. On the emulator, `10.0.2.2` usually maps to the host loopback.

3. Launch Light Page, open the URL entry modal, and load:
   - `http://10.0.2.2:8000/form-controls.html`
   - `http://10.0.2.2:8000/content-types.html`
   - `http://10.0.2.2:8000/spa-navigation.html`
   - `http://10.0.2.2:8000/delayed-render.html`

## Pass criteria

- Page renders in monochrome (black/white/grey) regardless of the fixture's hostile baseline colors.
- Focus-visible ring is visible on every focusable control (button, input, select, textarea, link).
- Checkboxes, radios, selects, and disabled buttons remain functional and visually distinguishable.
- SPA fixture: route changes switch views and re-theme within one debounce window (≤250 ms); no unstyled flash.
- Delayed-render fixture: the injected heading, paragraph, button, and input appear themed without manual reload.

## Automated / static checks

- `page-hooks.js` passes `node --check` syntax validation.
- `./gradlew :light-page:assembleDebug` builds successfully.

## Live run status

Live visual QA on the emulator/device was not executed in this agent session. The fixtures and pass criteria are ready for the next manual QA pass.
