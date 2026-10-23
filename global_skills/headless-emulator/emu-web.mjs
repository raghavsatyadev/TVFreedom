// Browser view of a headless emulator: node emu-web.mjs <serial> <port> <stamp-dir>
// The page shows the screen (a new screenshot as soon as the last one arrives) and sends clicks as taps,
// drags as swipes and keys as key events. Only on 127.0.0.1. While a page is open the emulator counts as used,
// so `emu idle` does not pause it.
import http from "node:http";
import { execFile } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const [serial, port = "8790", stampDir] = process.argv.slice(2);
if (!serial) { console.error("usage: node emu-web.mjs <serial> [port] [stamp-dir]"); process.exit(2); }

const adb = (args, opts = {}) => new Promise((ok, fail) =>
  execFile("adb", ["-s", serial, ...args], { encoding: "buffer", maxBuffer: 64 << 20, ...opts },
    (e, out) => (e ? fail(e) : ok(out))));
const used = () => stampDir && fs.writeFileSync(path.join(stampDir, `${serial}.last`), `${Math.floor(Date.now() / 1000)}\n`);

// Remote and keyboard keys to Android key codes (TV: arrows are the D-pad, Enter is select).
const KEYS = { ArrowUp: 19, ArrowDown: 20, ArrowLeft: 21, ArrowRight: 22, Enter: 23, Escape: 4, Backspace: 4,
  Home: 3, " ": 85, PageUp: 24, PageDown: 25, m: 82, Tab: 61 };

const PAGE = `<!doctype html><meta charset=utf-8><title>${serial}</title>
<style>body{margin:0;background:#111;color:#aaa;font:13px system-ui;display:flex;flex-direction:column;align-items:center}
canvas{max-width:100vw;max-height:calc(100vh - 28px);cursor:crosshair;user-select:none;touch-action:none}p{margin:4px}</style>
<p id=err style="color:#f88"></p><p>${serial}: click to tap (on a TV: moves to the item and selects it), drag to swipe, arrow keys and Enter to move, Esc for Back, type to enter text</p>
<canvas id=s></canvas>
<script>
const s = document.getElementById("s");
const g = s.getContext("2d");
const err = document.getElementById("err");
// Draw each shot into one canvas: swapping an <img> source many times a second makes the cursor flicker.
const load = async () => {
  try {
    const r = await fetch("/shot.png?" + Date.now());
    if (!r.ok) throw new Error((await r.text()).trim().split(String.fromCharCode(10)).pop());
    const b = await createImageBitmap(await r.blob());
    if (s.width !== b.width) { s.width = b.width; s.height = b.height; }
    g.drawImage(b, 0, 0); b.close(); err.textContent = ""; setTimeout(load, 150);
  } catch (e) { err.textContent = "No picture: " + (e.message || "server not reachable"); setTimeout(load, 1000); }
};
load();
const at = (e) => { const r = s.getBoundingClientRect();
  return [Math.round((e.clientX - r.left) * s.width / r.width), Math.round((e.clientY - r.top) * s.height / r.height)]; };
const send = (q) => fetch("/input?" + new URLSearchParams(q), { method: "POST" });
let down = null;
s.onpointerdown = (e) => { s.setPointerCapture(e.pointerId); down = [at(e), Date.now()]; };
s.onpointerup = (e) => { if (!down) return; const [[x1, y1], t] = down, [x2, y2] = at(e); down = null;
  if (Math.hypot(x2 - x1, y2 - y1) < 10) send({ t: "tap", x: x1, y: y1 });
  else send({ t: "swipe", x: x1, y: y1, x2, y2, ms: Math.max(100, Date.now() - t) }); };
const KEYS = ${JSON.stringify(Object.keys(KEYS))};
document.onkeydown = (e) => {
  if (KEYS.includes(e.key)) { send({ t: "key", k: e.key }); e.preventDefault(); }
  else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) send({ t: "text", s: e.key });
};
</script>`;

const num = (v) => String(Math.max(0, Math.round(Number(v) || 0)));

// TV emulators have no touchscreen and TV apps ignore taps. There a click walks the D-pad focus to the item under
// the pointer (reading the screen layout after each step) and presses Enter.
const touch = adb(["shell", "dumpsys", "input"]).then((o) => /TOUCHSCREEN/.test(String(o))).catch(() => true);
const box = (b) => { const m = b.match(/\d+/g).map(Number); return { l: m[0], t: m[1], r: m[2], b: m[3] }; };
const inside = (x, y, n) => x >= n.l && x < n.r && y >= n.t && y < n.b;
async function nodes() {
  const xml = String(await adb(["exec-out", "uiautomator", "dump", "/dev/tty"], { timeout: 10000 }));
  return [...xml.matchAll(/<node [^>]*>/g)].map(([n]) => ({ ...box(n.match(/bounds="([^"]+)"/)[1]),
    focusable: /focusable="true"/.test(n) || /clickable="true"/.test(n), focused: /focused="true"/.test(n) }));
}
async function focusClick(x, y) {
  for (let step = 0; step < 15; step++) {
    const all = await nodes();
    const hit = all.filter((n) => n.focusable && inside(x, y, n))
      .sort((a, b) => (a.r - a.l) * (a.b - a.t) - (b.r - b.l) * (b.b - b.t))[0];
    if (!hit) return;
    const f = all.filter((n) => n.focused).pop();
    if (!f || (inside((f.l + f.r) / 2, (f.t + f.b) / 2, hit) || inside((hit.l + hit.r) / 2, (hit.t + hit.b) / 2, f)))
      return adb(["shell", "input", "keyevent", "23"]);
    const dx = (hit.l + hit.r - f.l - f.r) / 2, dy = (hit.t + hit.b - f.t - f.b) / 2;
    const k = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 22 : 21) : (dy > 0 ? 20 : 19);
    await adb(["shell", "input", "keyevent", String(k)]);
  }
}

http.createServer(async (req, res) => {
  used();
  const url = new URL(req.url, "http://x");
  try {
    if (url.pathname === "/") { res.writeHead(200, { "content-type": "text/html" }); return res.end(PAGE); }
    if (url.pathname === "/shot.png") {
      const png = await adb(["exec-out", "screencap", "-p"], { timeout: 15000 });
      res.writeHead(200, { "content-type": "image/png", "cache-control": "no-store" }); return res.end(png);
    }
    if (url.pathname === "/input" && req.method === "POST") {
      const q = url.searchParams;
      const t = q.get("t");
      if (t === "tap" && !(await touch)) await focusClick(Number(q.get("x")), Number(q.get("y")));
      else if (t === "tap") await adb(["shell", "input", "tap", num(q.get("x")), num(q.get("y"))]);
      else if (t === "swipe") await adb(["shell", "input", "swipe", num(q.get("x")), num(q.get("y")),
        num(q.get("x2")), num(q.get("y2")), num(q.get("ms"))]);
      else if (t === "key" && KEYS[q.get("k")]) await adb(["shell", "input", "keyevent", String(KEYS[q.get("k")])]);
      // Text: letters and digits only, one at a time (input text needs shell escaping for the rest).
      else if (t === "text" && /^[A-Za-z0-9]$/.test(q.get("s") || "")) await adb(["shell", "input", "text", q.get("s")]);
      res.writeHead(204); return res.end();
    }
    res.writeHead(404); res.end();
  } catch (e) {
    res.writeHead(502, { "content-type": "text/plain" }); res.end(String(e.message || e));
  }
}).listen(Number(port), "127.0.0.1", () => console.log(`emu web: http://127.0.0.1:${port} (${serial}), Ctrl+C ends`));
