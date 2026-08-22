// scripts/ui-check.mjs — headless mobile-viewport UI verification via CDP
// Usage: node scripts/ui-check.mjs <url> <width> [--screenshot /tmp/shot.png] [--wait ms]
import { spawn } from 'node:child_process';
import { writeFileSync } from 'node:fs';
const BIN = process.env.CHROME_BIN ?? '/root/nas_docker/dsh-mobile/.browser-cache/chrome-headless-shell';
const url = process.argv[2];
const width = Number(process.argv[3] ?? 390);
const shotIdx = process.argv.indexOf('--screenshot');
const shotPath = shotIdx >= 0 ? process.argv[shotIdx + 1] : null;
const waitIdx = process.argv.indexOf('--wait');
const waitMs = waitIdx >= 0 ? Number(process.argv[waitIdx + 1]) : 9000;
const port = 9400 + Math.floor(Math.random() * 500);
const proc = spawn(BIN, [
  '--headless', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage',
  '--hide-scrollbars', '--remote-debugging-port=' + port, '--window-size=' + width + ',844', 'about:blank',
], { stdio: 'ignore' });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
let pages = null;
for (let i = 0; i < 30; i++) {
  try {
    const res = await fetch('http://127.0.0.1:' + port + '/json');
    pages = await res.json();
    if (pages.length) break;
  } catch {}
  await sleep(500);
}
if (!pages) { proc.kill(); throw new Error('CDP not ready'); }
async function cdp(wsUrl, method, params = {}) {
  const ws = new WebSocket(wsUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = () => rej(new Error('ws err')); });
  const id = Math.floor(Math.random() * 1e9);
  const result = new Promise((res) => {
    ws.onmessage = (ev) => { const m = JSON.parse(ev.data); if (m.id === id) res(m.result); };
  });
  ws.send(JSON.stringify({ id, method, params }));
  const r = await result;
  ws.close();
  return r;
}
const page = pages.find(p => p.type === 'page');
const wsUrl = page.webSocketDebuggerUrl;
const c = (m, p) => cdp(wsUrl, m, p);
await c('Page.enable');
await c('Runtime.enable');
await c('Emulation.setDeviceMetricsOverride', { width, height: 844, deviceScaleFactor: 2, mobile: true });
await c('Page.navigate', { url });
await sleep(waitMs);
const evalJs = async (expr) => {
  const r = await c('Runtime.evaluate', { expression: expr, returnByValue: true });
  return r?.result?.value;
};
const report = {
  url, width,
  title: await evalJs('document.title'),
  readyState: await evalJs('document.readyState'),
  overflow: await evalJs('document.documentElement.scrollWidth > window.innerWidth'),
  scrollWidth: await evalJs('document.documentElement.scrollWidth'),
  innerWidth: await evalJs('window.innerWidth'),
  bodyTextLen: await evalJs('document.body ? document.body.innerText.length : -1'),
  mobileNav: await evalJs('!!document.querySelector("[data-mobile-nav]")'),
  frame: await evalJs('!!document.querySelector("[data-dsh-frame]")'),
  sidebar: await evalJs('!!document.querySelector("[data-sidebar]")'),
  hasDrawerScrim: await evalJs('!!document.querySelector(".mobile-nav-backdrop, [data-mobile-backdrop], .drawer-scrim")'),
  consoleErrors: await evalJs('window.__errs ? window.__errs.length : 0'),
  sampleText: await evalJs('document.body ? document.body.innerText.slice(0, 200) : ""'),
};
if (shotPath) {
  const shot = await c('Page.captureScreenshot', { format: 'png' });
  if (shot?.data) writeFileSync(shotPath, Buffer.from(shot.data, 'base64'));
  report.screenshot = shotPath;
}
console.log(JSON.stringify(report, null, 2));
proc.kill();