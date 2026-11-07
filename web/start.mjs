import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";
import { resolve } from "node:path";

if (Number(process.versions.node.split(".")[0]) < 22) {
  console.error("PYTE benötigt Node.js 22 oder neuer: https://nodejs.org/");
  process.exit(1);
}
const port = Number(process.env.PORT || 3080);
if (!Number.isInteger(port) || port < 1024 || port > 65535)
  throw new Error("Ungültiger PORT.");
const url = `http://127.0.0.1:${port}`;
async function ready() {
  try {
    const response = await fetch(`${url}/api/health`, {
      signal: AbortSignal.timeout(500),
    });
    const data = await response.json();
    return data.app === "pyte-web" && data.localOnly;
  } catch {
    return false;
  }
}
function browser() {
  if (process.env.PYTE_NO_BROWSER === "1") return;
  const command =
    process.platform === "win32"
      ? ["cmd", ["/c", "start", "", url]]
      : process.platform === "darwin"
        ? ["open", [url]]
        : ["xdg-open", [url]];
  const child = spawn(command[0], command[1], {
    stdio: "ignore",
    detached: true,
  });
  child.on("error", () => console.log(`Bitte im Browser öffnen: ${url}`));
  child.unref();
}
if (await ready()) {
  console.log(`PYTE läuft bereits: ${url}`);
  browser();
} else {
  const child = spawn(
    process.execPath,
    [resolve(import.meta.dirname, "server/index.mjs")],
    { stdio: "inherit" },
  );
  let exited = false;
  child.on("exit", (code) => {
    exited = true;
    process.exitCode = code || 0;
  });
  for (const signal of ["SIGINT", "SIGTERM", "SIGHUP"])
    process.on(signal, () => {
      child.kill("SIGTERM");
    });
  process.on("exit", () => {
    if (!exited) child.kill("SIGTERM");
  });
  let started = false;
  for (let i = 0; i < 40 && !exited; i++) {
    if (await ready()) {
      started = true;
      browser();
      break;
    }
    await delay(250);
  }
  if (!started && !exited) {
    console.error(`Start nicht bestätigt. Port ${port} prüfen.`);
    child.kill("SIGTERM");
    process.exitCode = 1;
  }
}
