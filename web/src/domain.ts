import { defaultEqualizer, validateEqualizer } from "./equalizer.ts";
import type { EqualizerSettings } from "./equalizer.ts";
export interface Track {
  id: string;
  title: string;
  artist: string;
  durationMs: number;
  artwork?: string;
}
export interface Playlist {
  id: string;
  title: string;
  sourceId?: string;
  tracks: Track[];
  additions: string[];
  createdAt: number;
}
export interface Settings {
  volume: number;
  recordHistory: boolean;
  autoRecommendations: boolean;
  reducedMotion: boolean;
  equalizer: EqualizerSettings;
}
export interface Library {
  version: 1;
  playlists: Playlist[];
  favorites: Track[];
  history: Track[];
  settings: Settings;
}
export const emptyLibrary = (): Library => ({
  version: 1,
  playlists: [],
  favorites: [],
  history: [],
  settings: {
    volume: 0.75,
    recordHistory: true,
    autoRecommendations: true,
    reducedMotion: false,
    equalizer: defaultEqualizer(),
  },
});
export const isTrack = (value: unknown): value is Track => {
  if (!value || typeof value !== "object") return false;
  const t = value as Track;
  return (
    typeof t.id === "string" &&
    /^[A-Za-z0-9_-]{11}$/.test(t.id) &&
    typeof t.title === "string" &&
    t.title.length <= 1000 &&
    typeof t.artist === "string" &&
    t.artist.length <= 1000 &&
    Number.isFinite(t.durationMs) &&
    t.durationMs >= 0 &&
    t.durationMs <= 86400000 &&
    (t.artwork === undefined ||
      (typeof t.artwork === "string" &&
        t.artwork.length < 2048 &&
        /^https:\/\/(?:[a-z0-9-]+\.)*(?:ytimg\.com|googleusercontent\.com)\//i.test(
          t.artwork,
        )))
  );
};
export function validateLibrary(value: unknown): Library {
  const library = value as Library;
  if (
    !library ||
    library.version !== 1 ||
    !Array.isArray(library.playlists) ||
    library.playlists.length > 500 ||
    !Array.isArray(library.favorites) ||
    !Array.isArray(library.history) ||
    !library.favorites.every(isTrack) ||
    !library.history.every(isTrack)
  )
    throw new Error("Keine gültige PYTE-Web-Sicherung.");
  const ids = new Set<string>();
  const playlists = library.playlists.map((p) => {
    if (
      !p ||
      typeof p.id !== "string" ||
      !p.id ||
      p.id.length > 200 ||
      ids.has(p.id) ||
      typeof p.title !== "string" ||
      !p.title.trim() ||
      p.title.length > 100 ||
      !Array.isArray(p.tracks) ||
      p.tracks.length > 5000 ||
      !p.tracks.every(isTrack) ||
      !Array.isArray(p.additions) ||
      p.additions.some(
        (id) => typeof id !== "string" || !/^[A-Za-z0-9_-]{11}$/.test(id),
      ) ||
      (p.sourceId !== undefined &&
        (typeof p.sourceId !== "string" ||
          !/^[A-Za-z0-9_-]{10,160}$/.test(p.sourceId))) ||
      !Number.isFinite(p.createdAt)
    )
      throw new Error("Ungültige Playlist in der Sicherung.");
    ids.add(p.id);
    return {
      id: p.id,
      title: p.title,
      sourceId: p.sourceId,
      tracks: p.tracks.map(cleanTrack),
      additions: [...p.additions],
      createdAt: p.createdAt,
    };
  });
  const settings = library.settings;
  if (
    !settings ||
    !Number.isFinite(settings.volume) ||
    settings.volume < 0 ||
    settings.volume > 1 ||
    ["recordHistory", "autoRecommendations", "reducedMotion"].some(
      (key) => typeof settings[key as keyof Settings] !== "boolean",
    )
  )
    throw new Error("Ungültige Einstellungen in der Sicherung.");
  return {
    version: 1,
    playlists,
    favorites: library.favorites.map(cleanTrack),
    history: library.history.slice(0, 20).map(cleanTrack),
    settings: {
      volume: settings.volume,
      recordHistory: settings.recordHistory,
      autoRecommendations: settings.autoRecommendations,
      reducedMotion: settings.reducedMotion,
      equalizer: validateEqualizer(settings.equalizer),
    },
  };
}
const cleanTrack = (t: Track): Track => ({
  id: t.id,
  title: t.title,
  artist: t.artist,
  durationMs: t.durationMs,
  ...(t.artwork ? { artwork: t.artwork } : {}),
});
export function addTrack(playlist: Playlist, track: Track): Playlist {
  if (playlist.tracks.some((t) => t.id === track.id)) return playlist;
  if (playlist.tracks.length >= 5000)
    throw new Error("Maximal 5.000 Songs pro Playlist.");
  return {
    ...playlist,
    tracks: [...playlist.tracks, track],
    additions: [...new Set([...playlist.additions, track.id])],
  };
}
export function refreshPlaylist(playlist: Playlist, remote: Track[]): Playlist {
  const additions = playlist.tracks.filter((t) =>
    playlist.additions.includes(t.id),
  );
  const ids = new Set(additions.map((t) => t.id));
  return {
    ...playlist,
    tracks: [...remote.filter((t) => !ids.has(t.id)), ...additions],
  };
}
export function seedsFor(tracks: Track[]): Track[] {
  const unique = [...new Map(tracks.map((t) => [t.id, t])).values()];
  return unique.length <= 4
    ? unique
    : [0, 1, 2, 3].map(
        (i) => unique[Math.floor((i * (unique.length - 1)) / 3)],
      );
}
export function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const value = Math.floor(seconds),
    hours = Math.floor(value / 3600);
  return `${hours ? `${hours}:` : ""}${hours ? String(Math.floor(value / 60) % 60).padStart(2, "0") : Math.floor(value / 60)}:${String(value % 60).padStart(2, "0")}`;
}
