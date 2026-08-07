#!/usr/bin/env node
// Minimal chromium-cli-like driver for the mobile app's web preview
// (Expo web / react-native-web), since `chromium-cli` is not installed
// on this machine. Reads a tiny line-oriented script from stdin.
//
// Commands (one per line):
//   nav <url>                 goto — waits for `load`
//   wait-for text=<substr>    poll body innerText until it contains <substr> (max 15s)
//   wait-for ms=<n>           fixed wait, only for things wait-for text can't catch
//   click text=<substr>       click first element whose text matches (exact-ish)
//   fill placeholder=<substr> value=<val>   type <val> into the input with that placeholder
//   screenshot [name]         PNG to SCREENSHOT_DIR (env, default /tmp/mobile-web-shots)
//   console                   print console.error/pageerror seen so far
//   title                     print document.title
//
// Usage:
//   node driver.js <<'EOF'
//   nav http://localhost:8081
//   wait-for text=Sou Cliente
//   screenshot splash
//   click text=Sou Cliente
//   wait-for text=Criar conta
//   screenshot cadastro
//   console
//   EOF
//
// Playwright is borrowed from ../../../../admin/node_modules (mobile has
// no browser-testing deps of its own — see SKILL.md Gotchas).
const path = require('path');
const fs = require('fs');
const readline = require('readline');
const { chromium } = require(path.resolve(__dirname, '../../../../admin/node_modules/playwright'));

const SHOT_DIR = process.env.SCREENSHOT_DIR || '/tmp/mobile-web-shots';
fs.mkdirSync(SHOT_DIR, { recursive: true });

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 430, height: 932 } });

  const consoleLog = [];
  page.on('console', (msg) => { if (msg.type() === 'error') consoleLog.push(msg.text()); });
  page.on('pageerror', (err) => consoleLog.push('pageerror: ' + err.message));

  const rl = readline.createInterface({ input: process.stdin });
  let shotN = 0;

  for await (const raw of rl) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const [cmd, ...rest] = line.split(' ');
    const arg = rest.join(' ');
    try {
      if (cmd === 'nav') {
        await page.goto(arg, { waitUntil: 'load', timeout: 30000 });
        console.log(`[nav] ${arg}`);
      } else if (cmd === 'wait-for' && arg.startsWith('text=')) {
        const needle = arg.slice(5);
        await page.waitForFunction(
          (n) => document.body && document.body.innerText.includes(n),
          needle,
          { timeout: 15000 }
        );
        console.log(`[wait-for] found "${needle}"`);
      } else if (cmd === 'wait-for' && arg.startsWith('ms=')) {
        await page.waitForTimeout(Number(arg.slice(3)));
        console.log(`[wait-for] slept ${arg.slice(3)}ms`);
      } else if (cmd === 'click' && arg.startsWith('text=')) {
        const needle = arg.slice(5);
        await page.getByText(needle, { exact: true }).first().click();
        console.log(`[click] "${needle}"`);
      } else if (cmd === 'fill' && arg.startsWith('placeholder=')) {
        const rest2 = arg.slice('placeholder='.length);
        const sep = rest2.indexOf(' value=');
        const needle = rest2.slice(0, sep);
        const val = rest2.slice(sep + ' value='.length);
        await page.getByPlaceholder(needle).first().fill(val);
        console.log(`[fill] placeholder="${needle}" <- "${val}"`);
      } else if (cmd === 'screenshot') {
        shotN += 1;
        const name = arg || String(shotN).padStart(2, '0');
        const file = path.join(SHOT_DIR, `${name}.png`);
        await page.screenshot({ path: file });
        console.log(`[screenshot] ${file}`);
      } else if (cmd === 'console') {
        console.log('[console]', JSON.stringify(consoleLog, null, 2));
      } else if (cmd === 'title') {
        console.log('[title]', await page.title());
      } else {
        console.log(`[skip] unknown command: ${line}`);
      }
    } catch (e) {
      console.error(`[error] "${line}" ->`, e.message);
    }
  }

  await browser.close();
})();
