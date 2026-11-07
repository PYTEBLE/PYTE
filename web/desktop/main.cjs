"use strict";

const {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  powerSaveBlocker,
  session,
  shell,
} = require("electron");
const path = require("node:path");
const { pathToFileURL } = require("node:url");

// Browser storage is bound to the origin, so the port must stay fixed or the library would look empty.
const PORT = 47380;
const APP_URL = `http://127.0.0.1:${PORT}/`;

let server = null;
let window = null;
let sleepBlocker = null;

if (!app.requestSingleInstanceLock()) {
  app.quit();
}

app.on("second-instance", () => {
  if (!window) return;
  if (window.isMinimized()) window.restore();
  window.focus();
});

async function startServer() {
  const { createServer } = await import(
    pathToFileURL(path.join(__dirname, "server", "index.mjs")).href
  );
  server = createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(PORT, "127.0.0.1", resolve);
  });
}

function createWindow() {
  window = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 900,
    minHeight: 620,
    title: "PYTE",
    backgroundColor: "#0b0812",
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // Keeps audio, timers and playback state running while the window is minimized or behind another app.
      backgroundThrottling: false,
      spellcheck: false,
    },
  });
  window.once("ready-to-show", () => window.show());
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith("https://")) shell.openExternal(url);
    return { action: "deny" };
  });
  window.webContents.on("will-navigate", (event, url) => {
    if (!url.startsWith(APP_URL)) event.preventDefault();
  });
  window.webContents.on("before-input-event", (event, input) => {
    if (input.type === "keyDown" && input.key === "F11") {
      event.preventDefault();
      window.setFullScreen(!window.isFullScreen());
    }
  });
  window.on("closed", () => {
    window = null;
  });
  window.loadURL(APP_URL);
}

ipcMain.on("pyte:playing", (event, playing) => {
  if (!event.senderFrame || !event.senderFrame.url.startsWith(APP_URL)) return;
  if (playing && !sleepBlocker) {
    sleepBlocker = powerSaveBlocker.start("prevent-app-suspension");
  } else if (!playing && sleepBlocker !== null) {
    powerSaveBlocker.stop(sleepBlocker);
    sleepBlocker = null;
  }
});

app.whenReady().then(async () => {
  session.defaultSession.setPermissionRequestHandler(
    (_webContents, _permission, callback) => callback(false),
  );
  try {
    await startServer();
  } catch (error) {
    const busy = error && error.code === "EADDRINUSE";
    dialog.showErrorBox(
      "PYTE kann nicht starten",
      busy
        ? `Port ${PORT} ist bereits belegt. Bitte eine laufende PYTE-Instanz schließen und erneut starten.`
        : "Der lokale Dienst konnte nicht gestartet werden.",
    );
    app.quit();
    return;
  }
  createWindow();
});

app.on("window-all-closed", () => {
  app.quit();
});

app.on("before-quit", () => {
  if (sleepBlocker !== null) powerSaveBlocker.stop(sleepBlocker);
  if (server) server.close();
});
