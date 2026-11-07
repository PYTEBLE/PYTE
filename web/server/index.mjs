import http from "node:http";
import { readFile, stat } from "node:fs/promises";
import { createReadStream } from "node:fs";
import { resolve, extname, relative, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { Youtube, fetchAudio, boundedJson } from "./youtube.mjs";
import { VIDEO_ID, playlistId } from "./parsers.mjs";

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
const json = (res, status, value) => {
  if (res.headersSent || res.destroyed) return;
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  });
  res.end(JSON.stringify(value));
};
async function body(req) {
  if (!req.headers["content-type"]?.startsWith("application/json"))
    throw new HttpError(415, "JSON erforderlich.");
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 131072) throw new HttpError(413, "Anfrage zu groß.");
    chunks.push(chunk);
  }
  try {
    const parsed = JSON.parse(Buffer.concat(chunks).toString());
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
      throw new Error();
    return parsed;
  } catch {
    throw new HttpError(400, "Ungültiges JSON.");
  }
}
const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
};

export function createServer({
  youtube = new Youtube(),
  directory = resolve(import.meta.dirname, "../dist"),
  dev = false,
  fetcher = fetch,
} = {}) {
  let activeApi = 0,
    activeAudio = 0;
  return http.createServer(async (req, res) => {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader("Cross-Origin-Resource-Policy", "same-origin");
    res.setHeader(
      "Content-Security-Policy",
      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https://*.ytimg.com https://*.googleusercontent.com; media-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
    );
    let counted = false;
    try {
      const port = req.socket.localPort;
      const hosts = [`127.0.0.1:${port}`, `localhost:${port}`];
      if (!hosts.includes(req.headers.host))
        throw new HttpError(403, "Nur lokaler Zugriff erlaubt.");
      const origins = hosts.map((host) => `http://${host}`);
      if (dev) origins.push("http://127.0.0.1:5173");
      if (
        (req.headers.origin && !origins.includes(req.headers.origin)) ||
        req.headers["sec-fetch-site"] === "cross-site"
      )
        throw new HttpError(403, "Fremder Ursprung abgewiesen.");
      const url = new URL(req.url, `http://${hosts[0]}`);
      if (url.pathname.startsWith("/api/")) {
        if (activeApi >= 16)
          throw new HttpError(429, "Zu viele Anfragen. Bitte kurz warten.");
        activeApi++;
        counted = true;
        if (url.pathname === "/api/health" && req.method === "GET")
          return json(res, 200, {
            app: "pyte-web",
            status: "ok",
            localOnly: true,
          });
        if (
          url.pathname.startsWith("/api/audio/") &&
          ["GET", "HEAD"].includes(req.method)
        ) {
          const id = url.pathname.slice("/api/audio/".length);
          if (!VIDEO_ID.test(id))
            throw new HttpError(400, "Ungültige Song-ID.");
          const range = req.headers.range;
          if (range && !/^bytes=(\d+-\d*|-\d+)$/.test(range))
            throw new HttpError(
              416,
              "Nur einzelne Byte-Bereiche werden unterstützt.",
            );
          if (activeAudio >= 4)
            throw new HttpError(429, "Zu viele gleichzeitige Audiostreams.");
          activeAudio++;
          const abort = new AbortController();
          let deadline;
          res.on("close", () => abort.abort());
          try {
            const stream = await youtube.resolve(id);
            deadline = setTimeout(() => abort.abort(), 20000);
            const response = await fetchAudio(
              stream,
              range,
              abort.signal,
              fetcher,
            );
            clearTimeout(deadline); // Only headers have a deadline; do not cut long songs off mid-stream.
            if (![200, 206, 416].includes(response.status)) {
              await response.body?.cancel();
              youtube.streams?.delete(id);
              throw new HttpError(
                502,
                "Audiozugriff abgelehnt. Bitte den Song erneut starten.",
              );
            }
            const headers = {
              "Cache-Control": "no-store",
              "Content-Type":
                response.headers.get("content-type") || "audio/mp4",
            };
            for (const key of [
              "content-length",
              "content-range",
              "accept-ranges",
            ])
              if (response.headers.has(key))
                headers[key] = response.headers.get(key);
            res.writeHead(response.status, headers);
            if (req.method === "HEAD" || !response.body) {
              await response.body?.cancel();
              res.end();
            } else await pipeline(Readable.fromWeb(response.body), res);
          } finally {
            clearTimeout(deadline);
            activeAudio--;
            abort.abort();
          }
          return;
        }
        if (req.method !== "POST")
          throw new HttpError(405, "Methode nicht erlaubt.");
        const data = await body(req);
        if (url.pathname === "/api/search") {
          if (
            typeof data.query !== "string" ||
            !data.query.trim() ||
            data.query.length > 200 ||
            (data.continuation != null &&
              (typeof data.continuation !== "string" ||
                data.continuation.length > 12000))
          )
            throw new HttpError(400, "Ungültige Suche.");
          return json(
            res,
            200,
            await youtube.search(data.query, data.continuation),
          );
        }
        if (url.pathname === "/api/playlist") {
          let id;
          try {
            id = playlistId(data.input);
          } catch (error) {
            throw new HttpError(400, error.message);
          }
          return json(res, 200, await youtube.playlist(id));
        }
        if (url.pathname === "/api/recommendations") {
          if (
            !Array.isArray(data.seeds) ||
            !data.seeds.length ||
            data.seeds.length > 4 ||
            !Array.isArray(data.excluded) ||
            data.excluded.length > 5000 ||
            [...data.seeds, ...data.excluded].some(
              (id) => typeof id !== "string" || !VIDEO_ID.test(id),
            )
          )
            throw new HttpError(400, "Ungültige Song-Auswahl.");
          return json(
            res,
            200,
            await youtube.recommendations(data.seeds, data.excluded),
          );
        }
        if (url.pathname === "/api/lyrics") {
          if (
            typeof data.title !== "string" ||
            typeof data.artist !== "string" ||
            data.title.length > 500 ||
            data.artist.length > 500
          )
            throw new HttpError(400, "Ungültige Songdaten.");
          const query = new URLSearchParams({
            track_name: data.title,
            artist_name: data.artist,
          });
          if (Number.isFinite(data.durationMs) && data.durationMs > 0)
            query.set("duration", String(Math.round(data.durationMs / 1000)));
          const response = await fetcher(
            `https://lrclib.net/api/get?${query}`,
            {
              signal: AbortSignal.timeout(10000),
              headers: {
                "User-Agent": "PYTE-Web/0.2 (https://github.com/PYTEBLE/PYTE)",
              },
            },
          );
          if (response.status === 404) {
            await response.body?.cancel();
            return json(res, 200, { plainLyrics: null });
          }
          const lyrics = await boundedJson(response, 1024 * 1024);
          return json(res, 200, {
            plainLyrics: lyrics.plainLyrics ?? null,
            instrumental: !!lyrics.instrumental,
          });
        }
        throw new HttpError(404, "API nicht gefunden.");
      }
      if (!["GET", "HEAD"].includes(req.method))
        throw new HttpError(405, "Methode nicht erlaubt.");
      const pathname = decodeURIComponent(url.pathname);
      const file = resolve(
        directory,
        `.${pathname === "/" ? "/index.html" : pathname}`,
      );
      const traversal = relative(directory, file);
      if (traversal.startsWith("..") || isAbsolute(traversal))
        throw new HttpError(403, "Pfad nicht erlaubt.");
      let info;
      try {
        info = await stat(file);
      } catch {
        throw new HttpError(
          404,
          "Datei fehlt. Zuerst npm run build ausführen.",
        );
      }
      if (!info.isFile() || !types[extname(file)])
        throw new HttpError(404, "Datei nicht gefunden.");
      res.writeHead(200, {
        "Content-Type": types[extname(file)],
        "Content-Length": info.size,
        "Cache-Control": "no-cache",
      });
      if (req.method === "HEAD") res.end();
      else await pipeline(createReadStream(file), res);
    } catch (error) {
      if (res.headersSent) {
        if (!res.destroyed) res.destroy();
      } else
        json(res, error.status ?? 502, {
          error: error.status
            ? error.message
            : "Der Musikdienst ist gerade nicht erreichbar oder der Titel ist nicht verfügbar. Bitte erneut versuchen.",
        });
    } finally {
      if (counted) activeApi--;
    }
  });
}

if (
  process.argv[1] &&
  fileURLToPath(import.meta.url) === resolve(process.argv[1])
) {
  const port = Number(process.env.PORT || 3080);
  if (!Number.isInteger(port) || port < 1024 || port > 65535)
    throw new Error("PORT muss zwischen 1024 und 65535 liegen.");
  const server = createServer({ dev: process.argv.includes("--dev") });
  server.requestTimeout = 30000;
  server.on("error", (error) => {
    console.error(
      error.code === "EADDRINUSE"
        ? `Port ${port} ist belegt. Läuft PYTE schon?`
        : "Der lokale Server konnte nicht starten.",
    );
    process.exitCode = 1;
  });
  server.listen(port, "127.0.0.1", () =>
    console.log(
      `PYTE läuft lokal: http://127.0.0.1:${port}\nBeenden mit Strg+C. Bibliothek bleibt im Browser, nicht auf dem Server.`,
    ),
  );
  for (const signal of ["SIGTERM", "SIGINT"])
    process.on(signal, () => {
      server.close();
      server.closeAllConnections();
    });
}
