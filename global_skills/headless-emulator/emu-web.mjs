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
img{max-width:100vw;max-height:calc(100vh - 28px);cursor:crosshair;user-select:none}p{margin:4px}</style>
<p>${serial}: click = tap, drag = swipe, arrows/Enter = D-pad, Esc = back, Home = home, m = menu, type to send text</p>
<img id=s draggable=false>
<script>
const s = document.getElementById("s");
const load = () => { s.src = "/shot.png?" + Date.now(); };
s.onload = () => setTimeout(load, 150); s.onerror = () => setTimeout(load, 1000); load();
const at = (e) => { const r = s.getBoundingClientRect();
  return [Math.round((e.clientX - r.left) * s.naturalWidth / r.width), Math.round((e.clientY - r.top) * s.naturalHeight / r.height)]; };
const send = (q) => fetch("/input?" + new URLSearchParams(q), { method: "POST" });
let down = null;
s.onmousedown = (e) => { down = [at(e), Date.now()]; };
s.onmouseup = (e) => { if (!down) return; const [[x1, y1], t] = down, [x2, y2] = at(e); down = null;
  if (Math.hypot(x2 - x1, y2 - y1) < 10) send({ t: "tap", x: x1, y: y1 });
  else send({ t: "swipe", x: x1, y: y1, x2, y2, ms: Math.max(100, Date.now() - t) }); };
const KEYS = ${JSON.stringify(Object.keys(KEYS))};
document.onkeydown = (e) => {
  if (KEYS.includes(e.key)) { send({ t: "key", k: e.key }); e.preventDefault(); }
  else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) send({ t: "text", s: e.key });
};
</script>`;

const num = (v) => String(Math.max(0, Math.round(Number(v) || 0)));

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
      if (t === "tap") await adb(["shell", "input", "tap", num(q.get("x")), num(q.get("y"))]);
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
