export const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;
export function videoId(value) {
  if (typeof value !== "string" || !VIDEO_ID.test(value))
    throw new Error("Ungültige Song-ID.");
  return value;
}
export function playlistId(input) {
  if (typeof input !== "string" || input.length > 2048)
    throw new Error("Ungültiger Playlist-Link.");
  let id = input.trim();
  if (id.includes("://")) {
    const url = new URL(id);
    if (
      url.protocol !== "https:" ||
      !["music.youtube.com", "www.youtube.com", "youtube.com"].includes(
        url.hostname,
      )
    ) {
      throw new Error(
        "Bitte einen öffentlichen YouTube-Music- oder YouTube-Playlist-Link verwenden.",
      );
    }
    id = url.searchParams.get("list") ?? "";
  }
  if (!/^[A-Za-z0-9_-]{10,160}$/.test(id))
    throw new Error("Dieser Link enthält keine gültige Playlist-ID.");
  return id;
}
export function objects(root, key, result = []) {
  if (!root || typeof root !== "object") return result;
  if (Array.isArray(root)) root.forEach((item) => objects(item, key, result));
  else
    for (const [name, value] of Object.entries(root)) {
      if (name === key && value && typeof value === "object")
        result.push(value);
      objects(value, key, result);
    }
  return result;
}
export const text = (value) =>
  value?.simpleText ?? value?.runs?.map((run) => run.text ?? "").join("") ?? "";
const runs = (column) =>
  (
    column?.musicResponsiveListItemFlexColumnRenderer ??
    column?.musicResponsiveListItemFixedColumnRenderer
  )?.text?.runs ?? [];
const duration = (value) =>
  /^\d{1,3}(:\d{2}){1,2}$/.test(value)
    ? value.split(":").reduce((sum, part) => sum * 60 + Number(part), 0) * 1000
    : 0;
const thumbnail = (value) =>
  value?.thumbnails
    ?.filter(
      (item) =>
        typeof item.url === "string" &&
        /^https:\/\/(?:[a-z0-9-]+\.)*(?:ytimg\.com|googleusercontent\.com)\//i.test(
          item.url,
        ),
    )
    .slice()
    .sort((a, b) => (b.width ?? 0) - (a.width ?? 0))[0]?.url;
export function continuation(root) {
  // Same order as the Android parser: the list's own continuation item comes first.
  return (
    objects(root, "continuationItemRenderer")
      .map((renderer) => renderer.continuationEndpoint?.continuationCommand?.token)
      .find((token) => typeof token === "string" && token.length > 0) ??
    objects(root, "nextContinuationData")[0]?.continuation ??
    objects(root, "continuationCommand")[0]?.token ??
    null
  );
}
export function listTrack(item) {
  if (
    item.musicItemRendererDisplayPolicy ===
    "MUSIC_ITEM_RENDERER_DISPLAY_POLICY_GREY_OUT"
  )
    return null;
  const columns = item.flexColumns ?? [];
  const id =
    item.playlistItemData?.videoId ??
    runs(columns[0])[0]?.navigationEndpoint?.watchEndpoint?.videoId;
  if (!VIDEO_ID.test(id ?? "")) return null;
  const title = runs(columns[0])
    .map((run) => run.text)
    .join("")
    .trim();
  if (!title) return null;
  const artistRuns = runs(columns[1]);
  const boundary = artistRuns.findIndex((run) => run.text?.trim() === "•");
  const artist = artistRuns
    .slice(0, boundary < 0 ? undefined : boundary)
    .map((run) => run.text)
    .join("")
    .trim();
  const times = [...columns.slice(1), ...(item.fixedColumns ?? [])]
    .flatMap(runs)
    .map((run) => run.text?.trim() ?? "");
  return {
    id,
    title: title.slice(0, 1000),
    artist: (artist || "Unbekannter Interpret").slice(0, 1000),
    durationMs: Math.min(86400000, times.map(duration).find(Boolean) ?? 0),
    artwork: thumbnail(objects(item, "musicThumbnailRenderer")[0]?.thumbnail),
  };
}
export function searchPage(root) {
  return {
    tracks: [
      ...new Map(
        objects(root, "musicResponsiveListItemRenderer")
          .map(listTrack)
          .filter(Boolean)
          .map((track) => [track.id, track]),
      ).values(),
    ],
    continuation: continuation(root),
  };
}
export function playlistPage(root) {
  const header = objects(root, "musicResponsiveHeaderRenderer")[0];
  return {
    title: text(header?.title),
    owner: text(header?.straplineTextOne),
    tracks: objects(root, "musicResponsiveListItemRenderer")
      .map(listTrack)
      .filter(Boolean),
    continuation: continuation(root),
  };
}
export function radioTracks(root) {
  return [
    ...new Map(
      objects(root, "playlistPanelVideoRenderer")
        .filter(
          (item) =>
            !item.unplayable &&
            VIDEO_ID.test(item.videoId ?? "") &&
            text(item.title),
        )
        .map((item) => {
          const track = {
            id: item.videoId,
            title: text(item.title).slice(0, 1000),
            artist: (
              text(item.longBylineText ?? item.shortBylineText).split(
                " • ",
              )[0] || "Unbekannter Interpret"
            ).slice(0, 1000),
            durationMs: Math.min(86400000, duration(text(item.lengthText))),
            artwork: thumbnail(item.thumbnail),
          };
          return [track.id, track];
        }),
    ).values(),
  ];
}
export function rankRadios(radios, excluded) {
  const candidates = new Map();
  for (const { seed, tracks } of radios)
    tracks.forEach((track, index) => {
      if (excluded.has(track.id)) return;
      const previous = candidates.get(track.id);
      if (previous) previous.score += 1 / (index + 5);
      else candidates.set(track.id, { track, seed, score: 1 / (index + 5) });
    });
  const artists = new Map(),
    versions = new Set();
  return [...candidates.values()]
    .sort((a, b) => b.score - a.score)
    .filter(({ track }) => {
      const artist = track.artist.toLowerCase().trim(),
        version = `${artist}:${track.title.toLowerCase().trim()}`;
      if ((artists.get(artist) ?? 0) >= 4 || versions.has(version))
        return false;
      artists.set(artist, (artists.get(artist) ?? 0) + 1);
      versions.add(version);
      return true;
    })
    .slice(0, 30)
    .map(({ track, seed }) => ({ track, seed }));
}

const ART_HOSTS = /^(?:[a-z0-9-]+\.)*(?:ytimg\.com|googleusercontent\.com)$/i;
export function artworkUrl(value) {
  if (typeof value !== "string" || value.length > 2048)
    throw new Error("Ungültige Cover-Adresse.");
  const url = new URL(value);
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    (url.port && url.port !== "443") ||
    !ART_HOSTS.test(url.hostname)
  )
    throw new Error("Cover-Quelle nicht erlaubt.");
  return url.href;
}
