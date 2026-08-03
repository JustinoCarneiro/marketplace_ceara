---
name: run-mobile-web
description: Start the mobile app's web preview (Expo/react-native-web) and drive it headlessly to check screens, navigation, and design without a physical phone or USB. Use when asked to run, start, preview, or screenshot the mobile app, or to check its UI/design/functionality quickly.
---

Runs the Expo app in **web mode** (`expo start --web`) and drives it with a
small Playwright-based CLI (`driver.js`, next to this file) — `chromium-cli`
is not installed on this machine, so this driver is the substitute. This is
a **design/navigation preview**, not full native fidelity: `react-native-web`
renders most screens correctly but native-only behavior (gestures, some
`react-native-reanimated`/`worklets` animations) can differ from device.

All paths below are relative to `mobile/` (this skill's unit).

## Setup (one-time)

Expo web deps aren't installed by default in this project:

```bash
npx expo install react-dom react-native-web
```

`driver.js` itself borrows the `playwright` package from the sibling
`admin/` project (`../admin/node_modules/playwright`) — mobile has no
browser-testing deps of its own. See Gotchas if that path breaks.

## Run (agent path)

1. Start the dev server in the background and wait for it to actually serve:

```bash
npm run web > /tmp/expo-web.log 2>&1 &
timeout 60 bash -c 'until grep -q "Waiting on http://localhost:8081" /tmp/expo-web.log 2>/dev/null; do sleep 2; done'
```

Stop it later with: `lsof -ti:8081 -sTCP:LISTEN | xargs -r kill`

2. Drive it by piping a script to `driver.js`:

```bash
node .claude/skills/run-mobile-web/driver.js <<'EOF'
nav http://localhost:8081
wait-for text=Sou Cliente
screenshot 01-splash
click text=Sou Cliente
wait-for text=Criar conta
screenshot 02-cadastro-cliente
console
title
EOF
```

Screenshots → `/tmp/mobile-web-shots/<name>.png` (override with
`SCREENSHOT_DIR=...`). **Look at the screenshot file** — a blank or
error-page capture means it didn't actually render.

| command | what it does |
|---|---|
| `nav <url>` | goto, waits for `load` |
| `wait-for text=<substr>` | polls `document.body.innerText` up to 15s |
| `wait-for ms=<n>` | fixed wait — only when `wait-for text` can't target it |
| `click text=<substr>` | clicks the first element with matching text (exact) |
| `screenshot [name]` | PNG to `SCREENSHOT_DIR` |
| `console` | prints collected `console.error`/`pageerror` so far |
| `title` | prints `document.title` (each screen sets a distinct one) |

## Run (human path)

```bash
npm run web   # opens http://localhost:8081, Ctrl-C to stop
```

## Test

No unit/component tests exist for mobile (no Jest configured, no
`test` script in `package.json`). Native-level checks are Maestro E2E
(`mobile/e2e/*.yaml`, `mobile/e2e/run-maestro.sh`) — separate, heavy
(Android emulator, ~30–60 min), not part of this skill.

---

## Gotchas

- **`expo start --web` fails with `CommandError: ... don't have the
  required dependencies`** on a clean checkout — `react-dom` and
  `react-native-web` aren't in `package.json` yet. Run the Setup step
  first; it's a real `npm install` (modifies `package.json`/lockfile),
  not a transient fix.
- **`chromium-cli` is not installed on this machine.** `driver.js`
  exists specifically to fill that gap via the `playwright` package
  already present in `admin/node_modules` (installed there for the
  admin panel's own Playwright E2E suite). If that directory is ever
  removed, either install Playwright inside `mobile/` (`npm i -D
  playwright && npx playwright install chromium`) or repoint the
  `require()` path at the top of `driver.js`.
- **Backend-dependent screens won't fully function.** `EXPO_PUBLIC_API_URL`
  (from `mobile/.env`) points at the backend port (`8082` in this
  project's `docker-compose.homolog.yml`) — screens render and navigate
  fine without it, but login/register submissions will fail/hang. That
  port has also been observed occupied by an unrelated local project
  ("Projeto Lucas") on this machine — check `curl -s
  http://localhost:8082/actuator/health` before relying on real API
  calls.
- **`getByText(..., { exact: true })` can time out on the wrong node**
  if two elements share the same visible text (e.g. a label and a
  button). Prefer text unique to the element you're targeting, or
  add `.first()`.
- **Browser `history.back()` (`page.goBack()`) does not map cleanly onto
  React Navigation's web routing** — it can leave the driver on neither
  the expected previous screen nor a usable one. Prefer a fresh `nav`
  to the base URL over `goBack` when moving between unrelated flows in
  the same script.
- **A `useState('')` value used as the left operand of `&&` directly as
  a JSX child of `<View>` throws `"Unexpected text node: . A text node
  cannot be a child of a <View>"` on web** — harmless on native (RN
  silently drops empty-string children of `View`), but `react-native-web`
  doesn't. Fixed instance: `mobile/src/screens/auth/RegisterClientScreen.tsx`
  used `{error && ...}` where `error` starts as `''`; changed to
  `{!!error && ...}`. Worth a quick grep (`{<var> &&` where `<var>` is a
  string state) if this warning reappears on another screen.

## Troubleshooting

- **`Waiting on http://localhost:8081` never appears in the log**: check
  `/tmp/expo-web.log` directly — a stale Metro process on 8081 makes the
  new one hang. Free the port first: `lsof -ti:8081 -sTCP:LISTEN | xargs -r kill`.
- **`driver.js` throws `Cannot find module '.../playwright'`**: the
  relative path assumes this skill lives at
  `mobile/.claude/skills/run-mobile-web/driver.js` (four `..` up to
  reach the repo root, then into `admin/node_modules`). If the skill
  moves, update the `path.resolve` call at the top of `driver.js`.
