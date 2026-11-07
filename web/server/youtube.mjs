import { randomBytes } from "node:crypto";
import {
  videoId,
  playlistId,
  playlistPage,
  searchPage,
  radioTracks,
  rankRadios,
} from "./parsers.mjs";

const WEB = {
  clientName: "WEB_REMIX",
  clientVersion: "1.20250101.01.00",
  hl: "en",
  gl: "US",
};
const UA =
  "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36";
const CLIENTS = [
  {
    clientName: "VISIONOS",
    clientVersion: "1.04",
    clientScreen: "WATCH",
    platform: "MOBILE",
    deviceMake: "Apple",
    deviceModel: "RealityDevice17,1",
    osName: "visionOS",
    osVersion: "26.6.0.23O770",
    host: "https://youtubei.googleapis.com",
    ua: "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; US)",
  },
  {
    clientName: "ANDROID_VR",
    clientVersion: "1.65.10",
    androidSdkVersion: 32,
    deviceMake: "Oculus",
    deviceModel: "Quest 3",
    osName: "Android",
    osVersion: "12L",
    host: "https://www.youtube.com",
    ua: "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
  },
  {
    clientName: "IOS",
    clientVersion: "20.10.4",
    deviceMake: "Apple",
    deviceModel: "iPhone16,2",
    osName: "iPhone",
    osVersion: "18.3.2.22D82",
    host: "https://www.youtube.com",
    ua: "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
  },
];
const nonce = () => randomBytes(12).toString("base64url");
export async function boundedJson(response, limit = 8 * 1024 * 1024) {
  if (!response.ok) {
    await response.body?.cancel();
    throw new Error(`Dienst nicht verfügbar (HTTP ${response.status}).`);
  }
  let size = 0;
  const chunks = [];
  for await (const chunk of response.body) {
    size += chunk.length;
    if (size > limit) throw new Error("Antwort des Dienstes ist zu groß.");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}
export function allowedAudioUrl(value) {
  const url = new URL(value);
  if (
    url.protocol !== "https:" ||
    !url.hostname.endsWith(".googlevideo.com") ||
    url.username ||
    url.password ||
    (url.port && url.port !== "443")
  ) {
    throw new Error("Nicht unterstützte Audioquelle.");
  }
  return url;
}
export async function fetchAudio(stream, range, signal, fetcher = fetch) {
  let url = allowedAudioUrl(stream.url);
  for (let redirect = 0; redirect < 4; redirect++) {
    const response = await fetcher(url, {
      headers: { "User-Agent": stream.ua, ...(range ? { Range: range } : {}) },
      redirect: "manual",
      signal,
    });
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      await response.body?.cancel();
      url = allowedAudioUrl(
        new URL(response.headers.get("location"), url).href,
      );
    } else return response;
  }
  throw new Error("Zu viele Audio-Weiterleitungen.");
}
export class Youtube {
  constructor(fetcher = fetch) {
    this.fetch = fetcher;
    this.streams = new Map();
    this.pending = new Map();
    this.visitor = null;
  }
  async post(
    endpoint,
    body,
    client = WEB,
    host = "https://music.youtube.com",
    ua = UA,
    v2 = false,
  ) {
    const url = new URL(`${host}/youtubei/v1/${endpoint}?prettyPrint=false`);
    if (endpoint === "player" && client.clientName === "VISIONOS") {
      url.searchParams.set("id", body.videoId);
      url.searchParams.set("t", nonce());
    }
    const response = await this.fetch(url, {
      method: "POST",
      signal: AbortSignal.timeout(18000),
      headers: {
        "Content-Type": "application/json",
        "User-Agent": ua,
        ...(v2 ? { "X-Goog-Api-Format-Version": "2" } : {}),
      },
      body: JSON.stringify({ context: { client }, ...body }),
    });
    const data = await boundedJson(response);
    if (data.error) throw new Error("YouTube hat diese Anfrage abgelehnt.");
    return data;
  }
  async search(query, token) {
    if (
      typeof query !== "string" ||
      !query.trim() ||
      query.length > 200 ||
      (token && (typeof token !== "string" || token.length > 12000))
    )
      throw new Error("Ungültige Suche.");
    return searchPage(
      await this.post(
        "search",
        token
          ? { continuation: token }
          : { query: query.trim(), params: "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D" },
      ),
    );
  }
  async playlist(input) {
    const id = playlistId(input);
    let page = playlistPage(await this.post("browse", { browseId: `VL${id}` }));
    const title = page.title,
      tracks = [...page.tracks];
    const seen = new Set();
    for (let i = 0; page.continuation && i < 50; i++) {
      if (seen.has(page.continuation))
        throw new Error(
          "Die Playlist konnte nicht vollständig geladen werden.",
        );
      seen.add(page.continuation);
      page = playlistPage(
        await this.post("browse", { continuation: page.continuation }),
      );
      tracks.push(...page.tracks);
      if (tracks.length > 5000)
        throw new Error(
          "Diese Playlist überschreitet das Limit von 5.000 Songs.",
        );
    }
    if (page.continuation)
      throw new Error("Die Playlist konnte nicht vollständig geladen werden.");
    if (!tracks.length)
      throw new Error("Playlist leer, privat oder nicht verfügbar.");
    return { sourceId: id, title: title || "Importierte Playlist", tracks };
  }
  async recommendations(seeds, excluded = []) {
    if (
      !Array.isArray(seeds) ||
      !seeds.length ||
      seeds.length > 4 ||
      !Array.isArray(excluded) ||
      excluded.length > 5000
    )
      throw new Error("Ungültige Empfehlungsanfrage.");
    seeds.forEach(videoId);
    excluded.forEach(videoId);
    const jobs = [...new Set(seeds)],
      radios = [];
    let failed = 0;
    await Promise.all(
      [0, 1].map(async () => {
        while (jobs.length) {
          const seed = jobs.shift();
          try {
            radios.push({
              seed,
              tracks: radioTracks(
                await this.post("next", {
                  videoId: seed,
                  playlistId: `RDAMVM${seed}`,
                  isAudioOnly: true,
                  enablePersistentPlaylistPanel: true,
                }),
              ),
            });
          } catch {
            failed++;
          }
        }
      }),
    );
    if (!radios.length)
      throw new Error(
        "Empfehlungen nicht verfügbar. Bitte Verbindung prüfen und erneut versuchen.",
      );
    return {
      songs: rankRadios(radios, new Set([...excluded, ...seeds])),
      partial: failed > 0,
    };
  }
  async resolve(id) {
    videoId(id);
    const cached = this.streams.get(id);
    if (cached && cached.expires > Date.now() + 60000) return cached;
    if (this.pending.has(id)) return this.pending.get(id);
    const promise = this.resolveFresh(id).finally(() =>
      this.pending.delete(id),
    );
    this.pending.set(id, promise);
    return promise;
  }
  async resolveFresh(id) {
    for (const candidate of CLIENTS) {
      try {
        const { host, ua, ...attributes } = candidate;
        const client = {
          ...attributes,
          hl: "en",
          gl: "US",
          utcOffsetMinutes: 0,
        };
        let cpn;
        if (client.clientName === "VISIONOS") {
          if (!this.visitor || this.visitor.expires < Date.now()) {
            const data = await this.post(
              "visitor_id",
              {},
              client,
              "https://www.youtube.com",
              ua,
              true,
            );
            const value = data.responseContext?.visitorData;
            if (!value) throw new Error("Keine anonyme Sitzung.");
            this.visitor = { value, expires: Date.now() + 25 * 60000 };
          }
          client.visitorData = this.visitor.value;
          cpn = nonce();
        }
        const response = await this.post(
          "player",
          {
            videoId: id,
            contentCheckOk: true,
            racyCheckOk: true,
            ...(cpn ? { cpn } : {}),
          },
          client,
          host,
          ua,
          client.clientName === "VISIONOS",
        );
        if (response.playabilityStatus?.status !== "OK") continue;
        const formats = (response.streamingData?.adaptiveFormats ?? []).filter(
          (f) => f.url && f.mimeType?.startsWith("audio/"),
        );
        const format =
          formats.find((f) => f.itag === 140) ??
          formats.sort((a, b) => (b.bitrate ?? 0) - (a.bitrate ?? 0))[0];
        if (!format) continue;
        const url = allowedAudioUrl(format.url);
        if (cpn) url.searchParams.set("cpn", cpn);
        const stream = {
          url: url.href,
          ua,
          expires:
            Number(url.searchParams.get("expire")) * 1000 ||
            Date.now() + 1800000,
        };
        const probe = await fetchAudio(
          stream,
          "bytes=0-0",
          AbortSignal.timeout(15000),
          this.fetch,
        );
        if (![200, 206].includes(probe.status)) {
          await probe.body?.cancel();
          continue;
        }
        const reader = probe.body?.getReader();
        const first = await reader?.read();
        await reader?.cancel();
        if (!first?.value?.length) continue;
        if (this.streams.size >= 128)
          this.streams.delete(this.streams.keys().next().value);
        this.streams.set(id, stream);
        return stream;
      } catch {
        /* Try the next supported anonymous client; never log signed media URLs. */
      }
    }
    throw new Error(
      "Dieser Song ist über die verfügbaren YouTube-Clients nicht abspielbar.",
    );
  }
}
