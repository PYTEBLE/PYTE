import { useEffect, useRef, useState } from "react";
import {
  LibraryBig,
  Search,
  Heart,
  Settings2,
  Plus,
  ArrowUpRight,
  ArrowLeft,
  ArrowUp,
  ArrowDown,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
  Repeat1,
  Volume2,
  ListMusic,
  X,
  LockKeyhole,
  Sparkles,
  Download,
  Upload,
  RefreshCw,
  Trash2,
  Pencil,
  Check,
  Music2,
  Headphones,
  FileText,
  ChevronRight,
  Globe2,
} from "lucide-react";
import type { Library, Playlist, Track } from "./domain.ts";
import {
  addTrack,
  refreshPlaylist,
  seedsFor,
  validateLibrary,
  formatTime,
} from "./domain.ts";
import { loadLibrary, saveLibrary } from "./storage.ts";
import { WebPlayer } from "./player.ts";
import { api } from "./api.ts";
import { Artwork, Empty, IconButton, Modal, TrackRows } from "./components.tsx";

type View = "library" | "search" | "favorites" | "settings" | "playlist";
type Editor =
  | { kind: "import" }
  | { kind: "create" }
  | { kind: "rename"; id: string }
  | { kind: "delete"; id: string }
  | { kind: "history" }
  | { kind: "restore"; library: Library };
type Discovery = {
  label: string;
  playlistId?: string;
  seeds: Track[];
  tracks: Track[];
  reasons: Record<string, string>;
  loading: boolean;
  error?: string;
  partial?: boolean;
  added: Set<string>;
};
function message(error: unknown) {
  return error instanceof Error
    ? error.message
    : "Etwas ist schiefgelaufen. Bitte erneut versuchen.";
}

export default function App() {
  const [library, setLibrary] = useState<Library | null>(null),
    [bootError, setBootError] = useState(""),
    [storageError, setStorageError] = useState("");
  const [view, setView] = useState<View>("library"),
    [playlistId, setPlaylistId] = useState("");
  const [query, setQuery] = useState(""),
    [scope, setScope] = useState<"online" | "local">("online");
  const [results, setResults] = useState<Track[]>([]),
    [continuation, setContinuation] = useState<string | null>(null);
  const [searching, setSearching] = useState(false),
    [searched, setSearched] = useState(false),
    [searchError, setSearchError] = useState("");
  const [editor, setEditor] = useState<Editor | null>(null),
    [input, setInput] = useState(""),
    [busy, setBusy] = useState(false),
    [formError, setFormError] = useState("");
  const [picker, setPicker] = useState<Track | null>(null),
    [discovery, setDiscovery] = useState<Discovery | null>(null);
  const [panel, setPanel] = useState<"queue" | "lyrics" | null>(() =>
      window.innerWidth >= 1190 ? "queue" : null,
    ),
    [lyrics, setLyrics] = useState(""),
    [lyricsLoading, setLyricsLoading] = useState(false);
  const [notice, setNotice] = useState(""),
    [player, setPlayer] = useState<WebPlayer | null>(null);
  const audioRef = useRef<HTMLAudioElement>(null),
    libraryRef = useRef(library);
  const searchRequest = useRef<AbortController | null>(null),
    discoveryRequest = useRef<AbortController | null>(null);
  libraryRef.current = library;
  const [playback, setPlayback] = useState<ReturnType<
    WebPlayer["getSnapshot"]
  > | null>(null);
  const current = playback?.queue[playback.index]?.track;
  const pendingWrites = useRef(0),
    failedWrite = useRef(false);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (pendingWrites.current || failedWrite.current) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, []);
  useEffect(() => {
    let alive = true;
    loadLibrary()
      .then((value) => {
        if (alive) setLibrary(value);
      })
      .catch((error) => {
        if (alive) setBootError(message(error));
      });
    const instance = new WebPlayer(
      audioRef.current!,
      () =>
        document.visibilityState === "visible" &&
        document.hasFocus() &&
        !document.querySelector("dialog[open]"),
    );
    setPlayer(instance);
    setPlayback(instance.getSnapshot());
    const unsubscribe = instance.subscribe(() =>
      setPlayback(instance.getSnapshot()),
    );
    return () => {
      alive = false;
      unsubscribe();
      instance.dispose();
      searchRequest.current?.abort();
      discoveryRequest.current?.abort();
    };
  }, []);
  const loaded = useRef(false);
  useEffect(() => {
    if (!library) return;
    if (!loaded.current) {
      loaded.current = true;
      return;
    }
    pendingWrites.current++;
    setSaving(true);
    saveLibrary(library)
      .then(() => {
        failedWrite.current = false;
        setStorageError("");
      })
      .catch((error) => {
        failedWrite.current = true;
        setStorageError(message(error));
      })
      .finally(() => {
        pendingWrites.current--;
        setSaving(pendingWrites.current > 0);
      });
  }, [library]);
  useEffect(() => {
    if (!library || !player) return;
    player.volume(library.settings.volume);
    player.automatic = library.settings.autoRecommendations;
    document.documentElement.dataset.motion = library.settings.reducedMotion
      ? "reduced"
      : "full";
  }, [library?.settings, player]);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(""), 5500);
    return () => clearTimeout(timer);
  }, [notice]);
  useEffect(() => {
    const breakpoint = matchMedia("(max-width: 1190px)");
    const change = () => {
      if (breakpoint.matches) setPanel(null);
    };
    breakpoint.addEventListener("change", change);
    return () => breakpoint.removeEventListener("change", change);
  }, []);
  const playedEntry = useRef("");
  useEffect(() => {
    const entry = playback?.queue[playback.index];
    if (!entry || !playback?.playing || playedEntry.current === entry.uid)
      return;
    playedEntry.current = entry.uid;
    setLibrary((previous) =>
      !previous || !previous.settings.recordHistory
        ? previous
        : {
            ...previous,
            history: [
              entry.track,
              ...previous.history.filter((t) => t.id !== entry.track.id),
            ].slice(0, 20),
          },
    );
  }, [playback?.playing, playback?.queue, playback?.index]);
  useEffect(() => {
    if (!playback?.suggestion) return;
    const source = libraryRef.current?.playlists.find(
      (p) => p.id === playback.suggestion!.playlistId,
    );
    player?.consumeSuggestion();
    if (source) void discover(source.tracks, source.title, source.id);
  }, [playback?.suggestion]);
  useEffect(() => {
    if (!player) return;
    const keydown = (event: KeyboardEvent) => {
      if (
        event.target instanceof Element &&
        event.target.closest(
          "input,textarea,select,button,a,[contenteditable],dialog",
        )
      )
        return;
      if (
        document.querySelector("dialog[open]") ||
        event.ctrlKey ||
        event.metaKey ||
        event.altKey
      )
        return;
      if (event.code === "Space") {
        event.preventDefault();
        player.toggle();
      }
      if (event.key.toLowerCase() === "j")
        player.seek(player.getSnapshot().position - 10);
      if (event.key.toLowerCase() === "l")
        player.seek(player.getSnapshot().position + 10);
      if (event.key.toLowerCase() === "n") player.next();
      if (event.key.toLowerCase() === "p") player.previous();
    };
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [player]);
  useEffect(() => {
    if (!("mediaSession" in navigator) || !player) return;
    navigator.mediaSession.metadata = current
      ? new MediaMetadata({
          title: current.title,
          artist: current.artist,
          album: "PYTE",
          artwork: current.artwork ? [{ src: current.artwork }] : [],
        })
      : null;
    const actions: [MediaSessionAction, MediaSessionActionHandler][] = [
      [
        "play",
        () => {
          if (!player.getSnapshot().playing) player.toggle();
        },
      ],
      [
        "pause",
        () => {
          if (player.getSnapshot().playing) player.toggle();
        },
      ],
      ["nexttrack", () => player.next()],
      ["previoustrack", () => player.previous()],
      [
        "seekto",
        (details) => {
          if (details.seekTime !== undefined) player.seek(details.seekTime);
        },
      ],
    ];
    for (const [action, handler] of actions) {
      try {
        navigator.mediaSession.setActionHandler(action, handler);
      } catch {
        /* Browser capability, not a fatal error. */
      }
    }
    return () => {
      for (const [action] of actions) {
        try {
          navigator.mediaSession.setActionHandler(action, null);
        } catch {}
      }
    };
  }, [current?.id, player]);
  useEffect(() => {
    if (panel !== "lyrics" || !current) {
      setLyrics("");
      return;
    }
    const abort = new AbortController();
    setLyricsLoading(true);
    setLyrics("");
    api<{ plainLyrics: string | null; instrumental?: boolean }>(
      "lyrics",
      current,
      abort.signal,
    )
      .then((value) =>
        setLyrics(
          value.instrumental
            ? "Instrumental · keine Lyrics vorhanden."
            : value.plainLyrics ||
                "Für diesen Song sind keine Lyrics verfügbar.",
        ),
      )
      .catch((error) => {
        if (!abort.signal.aborted) setLyrics(message(error));
      })
      .finally(() => {
        if (!abort.signal.aborted) setLyricsLoading(false);
      });
    return () => abort.abort();
  }, [panel, current?.id]);

  function openEditor(value: Editor, text = "") {
    setFormError("");
    setInput(text);
    setEditor(value);
  }
  function toggleFavorite(track: Track) {
    setLibrary((old) =>
      !old
        ? old
        : {
            ...old,
            favorites: old.favorites.some((t) => t.id === track.id)
              ? old.favorites.filter((t) => t.id !== track.id)
              : [track, ...old.favorites],
          },
    );
  }
  function openPlaylist(playlist: Playlist) {
    setPlaylistId(playlist.id);
    setView("playlist");
  }
  function saveSong(track: Track, id?: string, title?: string) {
    const old = libraryRef.current;
    if (!old) return;
    try {
      const destination = id
        ? old.playlists.find((p) => p.id === id)
        : ({
            id: crypto.randomUUID(),
            title: (title ?? "").trim(),
            tracks: [],
            additions: [],
            createdAt: Date.now(),
          } as Playlist);
      if (!destination || !destination.title || destination.title.length > 100)
        throw new Error("Bitte eine gültige Playlist wählen oder benennen.");
      const updated = addTrack(destination, track);
      setLibrary({
        ...old,
        playlists: id
          ? old.playlists.map((p) => (p.id === id ? updated : p))
          : [updated, ...old.playlists],
      });
      setPicker(null);
      setInput("");
      setFormError("");
      setNotice(
        updated === destination
          ? "Dieser Song ist bereits in der Playlist."
          : `Zu „${destination.title}“ hinzugefügt.`,
      );
      setDiscovery((previous) =>
        previous
          ? { ...previous, added: new Set([...previous.added, track.id]) }
          : previous,
      );
    } catch (error) {
      setFormError(message(error));
    }
  }
  function addRecommendation(track: Track) {
    if (discovery?.playlistId) saveSong(track, discovery.playlistId);
    else {
      setInput("");
      setFormError("");
      setPicker(track);
    }
  }
  async function runSearch(more = false) {
    if (!query.trim() || searching) return;
    searchRequest.current?.abort();
    const abort = new AbortController();
    searchRequest.current = abort;
    setSearching(true);
    setSearched(true);
    setSearchError("");
    if (!more) setResults([]);
    try {
      const page = await api<{ tracks: Track[]; continuation: string | null }>(
        "search",
        { query, ...(more ? { continuation } : {}) },
        abort.signal,
      );
      if (!abort.signal.aborted) {
        setResults((old) => [
          ...new Map(
            [...(more ? old : []), ...page.tracks].map((t) => [t.id, t]),
          ).values(),
        ]);
        setContinuation(
          more && page.continuation === continuation ? null : page.continuation,
        );
      }
    } catch (error) {
      if (!abort.signal.aborted) setSearchError(message(error));
    } finally {
      if (!abort.signal.aborted) setSearching(false);
    }
  }
  function changeQuery(value: string) {
    searchRequest.current?.abort();
    setQuery(value);
    setResults([]);
    setContinuation(null);
    setSearched(false);
    setSearching(false);
    setSearchError("");
  }
  async function discover(tracks: Track[], label: string, source?: string) {
    if (source) {
      const latest = libraryRef.current?.playlists.find((p) => p.id === source);
      if (!latest) {
        setNotice("Diese Playlist wurde bereits gelöscht.");
        return;
      }
      tracks = latest.tracks;
      label = latest.title;
    }
    if (!tracks.length) {
      setNotice("Füge dieser Playlist zuerst einen Song hinzu.");
      return;
    }
    discoveryRequest.current?.abort();
    const abort = new AbortController();
    discoveryRequest.current = abort;
    const seeds = seedsFor(tracks);
    setDiscovery({
      label,
      playlistId: source,
      seeds: tracks,
      tracks: [],
      reasons: {},
      loading: true,
      added: new Set(),
    });
    try {
      const data = await api<{
        songs: { track: Track; seed: string }[];
        partial: boolean;
      }>(
        "recommendations",
        {
          seeds: seeds.map((t) => t.id),
          excluded: source ? tracks.map((t) => t.id) : seeds.map((t) => t.id),
        },
        abort.signal,
      );
      if (!abort.signal.aborted)
        setDiscovery((old) =>
          old
            ? {
                ...old,
                tracks: data.songs.map((s) => s.track),
                reasons: Object.fromEntries(
                  data.songs.map((s) => [
                    s.track.id,
                    tracks.find((t) => t.id === s.seed)?.title ?? label,
                  ]),
                ),
                partial: data.partial,
                loading: false,
              }
            : null,
        );
    } catch (error) {
      if (!abort.signal.aborted)
        setDiscovery((old) =>
          old ? { ...old, loading: false, error: message(error) } : null,
        );
    }
  }
  async function submitEditor(event: React.FormEvent) {
    event.preventDefault();
    if (!editor || !library || busy) return;
    setBusy(true);
    setFormError("");
    try {
      if (editor.kind === "import") {
        const remote = await api<{
          sourceId: string;
          title: string;
          tracks: Track[];
        }>("playlist", { input });
        setLibrary((old) => {
          if (!old) return old;
          const previous = old.playlists.find(
            (p) => p.sourceId === remote.sourceId,
          );
          const playlist = previous
            ? refreshPlaylist(previous, remote.tracks)
            : {
                id: crypto.randomUUID(),
                sourceId: remote.sourceId,
                title: remote.title.slice(0, 100),
                tracks: remote.tracks,
                additions: [],
                createdAt: Date.now(),
              };
          return {
            ...old,
            playlists: previous
              ? old.playlists.map((p) => (p.id === previous.id ? playlist : p))
              : [playlist, ...old.playlists],
          };
        });
        setView("library");
        setNotice(
          "Playlist lokal importiert. Dein YouTube-Konto wurde nicht verändert.",
        );
      } else if (editor.kind === "create") {
        const title = input.trim();
        if (!title || title.length > 100)
          throw new Error(
            "Bitte einen Namen zwischen 1 und 100 Zeichen eingeben.",
          );
        const playlist: Playlist = {
          id: crypto.randomUUID(),
          title,
          tracks: [],
          additions: [],
          createdAt: Date.now(),
        };
        setLibrary((old) =>
          old ? { ...old, playlists: [playlist, ...old.playlists] } : old,
        );
        openPlaylist(playlist);
      } else if (editor.kind === "rename") {
        const title = input.trim();
        if (!title || title.length > 100)
          throw new Error(
            "Bitte einen Namen zwischen 1 und 100 Zeichen eingeben.",
          );
        setLibrary((old) =>
          old
            ? {
                ...old,
                playlists: old.playlists.map((p) =>
                  p.id === editor.id ? { ...p, title } : p,
                ),
              }
            : old,
        );
      } else if (editor.kind === "delete") {
        setLibrary((old) =>
          old
            ? {
                ...old,
                playlists: old.playlists.filter((p) => p.id !== editor.id),
              }
            : old,
        );
        setView("library");
      } else if (editor.kind === "history")
        setLibrary((old) => (old ? { ...old, history: [] } : old));
      else if (editor.kind === "restore") {
        setLibrary(editor.library);
        setView("library");
        setNotice("Web-Bibliothek wiederhergestellt.");
      }
      setEditor(null);
    } catch (error) {
      setFormError(message(error));
    } finally {
      setBusy(false);
    }
  }
  async function refreshCurrent(playlist: Playlist) {
    if (busy || !playlist.sourceId) return;
    setBusy(true);
    try {
      const remote = await api<{ tracks: Track[] }>("playlist", {
        input: playlist.sourceId,
      });
      setLibrary((old) =>
        old
          ? {
              ...old,
              playlists: old.playlists.map((p) =>
                p.id === playlist.id ? refreshPlaylist(p, remote.tracks) : p,
              ),
            }
          : old,
      );
      setNotice(
        "Playlist aktualisiert. Lokaler Name und Ergänzungen bleiben erhalten.",
      );
    } catch (error) {
      setNotice(message(error));
    } finally {
      setBusy(false);
    }
  }
  function exportLibrary() {
    if (!library) return;
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(library, null, 2)], {
        type: "application/json",
      }),
    );
    const link = document.createElement("a");
    link.href = url;
    link.download = `PYTE-Web-${new Date().toISOString().slice(0, 10)}.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  const selected = library?.playlists.find((p) => p.id === playlistId);
  const localTracks = library
    ? [
        ...new Map(
          [
            ...library.playlists.flatMap((p) => p.tracks),
            ...library.favorites,
            ...library.history,
          ].map((t) => [t.id, t]),
        ).values(),
      ].filter((t) =>
        `${t.title} ${t.artist}`
          .toLocaleLowerCase()
          .includes(query.toLocaleLowerCase().trim()),
      )
    : [];
  const tracks =
    view === "favorites"
      ? (library?.favorites ?? [])
      : view === "playlist"
        ? (selected?.tracks ?? [])
        : scope === "online"
          ? results
          : localTracks;
  const rowProps = {
    currentId: current?.id,
    favorites: library?.favorites ?? [],
    onFavorite: toggleFavorite,
    onAdd: (track: Track) => {
      setInput("");
      setFormError("");
      setPicker(track);
    },
    onQueue: (track: Track) => {
      player?.enqueue(track, false);
      setNotice("Zur Queue hinzugefügt.");
    },
  };
  const title =
    view === "library"
      ? "Deine Bibliothek"
      : view === "search"
        ? "Finde deinen nächsten Lieblingssong."
        : view === "favorites"
          ? "Immer wieder gut."
          : view === "settings"
            ? "Dein Raum. Deine Regeln."
            : (selected?.title ?? "Playlist nicht gefunden");

  return (
    <div className="app-shell">
      <audio ref={audioRef} preload="none" />
      <a href="#main" className="skip-link">
        Zum Inhalt springen
      </a>
      <aside className="sidebar" aria-label="Hauptnavigation">
        <button
          className="brand"
          aria-label="PYTE Bibliothek"
          onClick={() => setView("library")}
        >
          <img src="/brand/symbol.png" alt="" />
          <img src="/brand/wordmark.png" alt="PYTE" />
        </button>
        <p className="nav-label">DEIN MUSIKRAUM</p>
        <nav>
          {(
            [
              { id: "library", label: "Bibliothek", Icon: LibraryBig },
              { id: "search", label: "Entdecken", Icon: Search },
              { id: "favorites", label: "Favoriten", Icon: Heart },
            ] as const
          ).map((item) => (
            <button
              key={item.id}
              aria-label={item.label}
              aria-current={
                view === item.id ||
                (view === "playlist" && item.id === "library")
                  ? "page"
                  : undefined
              }
              className={`nav-item ${view === item.id || (view === "playlist" && item.id === "library") ? "selected" : ""}`}
              onClick={() => setView(item.id)}
            >
              <item.Icon />
              <span>{item.label}</span>
              {item.id === "favorites" && (
                <small>{library?.favorites.length ?? 0}</small>
              )}
            </button>
          ))}
        </nav>
        <div className="sidebar-heading">
          <span>DEINE PLAYLISTS</span>
          <IconButton
            label="Neue Playlist"
            disabled={!library}
            onClick={() => openEditor({ kind: "create" })}
          >
            <Plus />
          </IconButton>
        </div>
        <div className="sidebar-playlists">
          {library?.playlists.map((p) => (
            <button
              key={p.id}
              className={
                view === "playlist" && selected?.id === p.id ? "selected" : ""
              }
              onClick={() => openPlaylist(p)}
            >
              <Music2 />
              <span>{p.title}</span>
            </button>
          ))}
          {library?.playlists.length === 0 && (
            <p>
              Ein Link. Deine ganze Playlist.
              <br />
              Oder fang ganz neu an.
            </p>
          )}
        </div>
        <div className="sidebar-bottom">
          <button
            aria-label="Einstellungen"
            className={`nav-item ${view === "settings" ? "selected" : ""}`}
            onClick={() => setView("settings")}
          >
            <Settings2 />
            <span>Einstellungen</span>
          </button>
          <div className="local-note">
            <LockKeyhole />
            <span>
              Ohne Konto. Ohne Cloud.
              <small>
                {saving
                  ? "Wird lokal gespeichert …"
                  : storageError
                    ? "Ungespeicherte Änderungen"
                    : "Lokal gespeichert."}
              </small>
            </span>
          </div>
        </div>
      </aside>
      <main id="main" className="main-view" tabIndex={-1}>
        <header className="topbar">
          <span className="eyebrow">PYTE / DESKTOP</span>
          <div className="topbar-right">
            <span className="local-badge">
              <span /> LOCAL FIRST
            </span>
            <IconButton label="Suche öffnen" onClick={() => setView("search")}>
              <Search />
            </IconButton>
          </div>
        </header>
        {storageError && (
          <div role="alert" className="error-banner">
            {storageError}
            <button onClick={exportLibrary}>Sicherung exportieren</button>
          </div>
        )}
        {bootError ? (
          <Empty
            title="Bibliothek nicht erreichbar"
            action={
              <button className="primary" onClick={() => location.reload()}>
                Erneut laden
              </button>
            }
          >
            {bootError} Es wurden keine gespeicherten Daten überschrieben.
          </Empty>
        ) : !library ? (
          <div className="loading" role="status">
            Dein Musikraum wird geöffnet …
          </div>
        ) : (
          <>
            <div className="page-heading">
              <div>
                <p className="eyebrow">
                  {view === "library"
                    ? "MUSIK, DIE ZU DIR GEHÖRT"
                    : view === "search"
                      ? "NEUE SOUNDS. KEINE GRENZEN."
                      : view === "favorites"
                        ? "DEINE LIEBLINGE"
                        : view === "settings"
                          ? "LOCAL BY DESIGN"
                          : "DEINE LOKALE PLAYLIST"}
                </p>
                <h1>{title}</h1>
              </div>
              {view === "library" && (
                <button
                  className="secondary"
                  onClick={() => openEditor({ kind: "import" })}
                >
                  <Plus />
                  Playlist importieren
                </button>
              )}
            </div>
            {view === "library" && (
              <>
                <section className="welcome-hero">
                  <div className="hero-copy">
                    <span className="pill">
                      <Headphones />
                      DEIN SOUND. DEIN RAUM.
                    </span>
                    <h2>
                      Weniger Ablenkung.
                      <br />
                      <em>Mehr deine Musik.</em>
                    </h2>
                    <p>
                      Bring deine YouTube-Music-Playlists mit oder entdecke neue
                      Songs. Deine Sammlung bleibt in deinem Browser. Ganz ohne
                      Konto.
                    </p>
                    <div className="hero-actions">
                      <button
                        className="primary"
                        onClick={() => {
                          setView("search");
                        }}
                      >
                        <Search />
                        Musik entdecken
                        <ArrowUpRight />
                      </button>
                      <button
                        className="text-button"
                        onClick={() => openEditor({ kind: "import" })}
                      >
                        Playlist mitbringen
                        <ChevronRight />
                      </button>
                    </div>
                  </div>
                  <img className="hero-mark" src="/brand/symbol.png" alt="" />
                </section>
                <div className="section-heading">
                  <h2>
                    Deine Playlists <span>{library.playlists.length}</span>
                  </h2>
                  <button
                    className="text-button"
                    onClick={() => openEditor({ kind: "create" })}
                  >
                    <Plus />
                    Neu erstellen
                  </button>
                </div>
                <div className="playlist-grid">
                  {library.playlists.map((p) => (
                    <article className="playlist-card" key={p.id}>
                      <button
                        className="playlist-cover"
                        onClick={() => openPlaylist(p)}
                        aria-label={`${p.title} öffnen`}
                      >
                        <Artwork track={p.tracks.find((t) => t.artwork)} />
                        <span>
                          <Play fill="currentColor" />
                        </span>
                      </button>
                      <button
                        className="playlist-title"
                        onClick={() => openPlaylist(p)}
                      >
                        {p.title}
                      </button>
                      <p>
                        {p.tracks.length} Songs ·{" "}
                        {p.sourceId
                          ? "Lokal importiert"
                          : "Nur auf diesem Gerät"}
                      </p>
                    </article>
                  ))}
                  <button
                    className="new-playlist-card"
                    onClick={() => openEditor({ kind: "create" })}
                  >
                    <span>
                      <Plus />
                    </span>
                    <strong>Deine nächste Sammlung</strong>
                    <small>Eine Playlist ganz nach dir.</small>
                  </button>
                </div>
                {library.history.length > 0 && (
                  <>
                    <div className="section-heading">
                      <h2>Zuletzt gehört</h2>
                      <span className="muted">Nur auf diesem Gerät</span>
                    </div>
                    <TrackRows
                      tracks={library.history}
                      {...rowProps}
                      onPlay={(i) => player?.playQueue(library.history, i)}
                    />
                  </>
                )}
                <div className="privacy-strip">
                  <LockKeyhole />
                  <p>
                    <strong>Deine Musikbibliothek gehört dir.</strong> Keine
                    Anmeldung, kein Cloud-Sync. Sichere deine Sammlung jederzeit
                    in den Einstellungen.
                  </p>
                  <ArrowUpRight />
                </div>
              </>
            )}
            {view === "search" && (
              <>
                <div className="segmented">
                  <button
                    className={scope === "online" ? "selected" : ""}
                    onClick={() => setScope("online")}
                  >
                    <Globe2 />
                    YouTube Music
                  </button>
                  <button
                    className={scope === "local" ? "selected" : ""}
                    onClick={() => setScope("local")}
                  >
                    <LibraryBig />
                    Meine Bibliothek
                  </button>
                </div>
                <form
                  className="search-form"
                  onSubmit={(event) => {
                    event.preventDefault();
                    if (scope === "online") void runSearch();
                  }}
                >
                  <Search />
                  <input
                    aria-label="Songs oder Künstler suchen"
                    placeholder={
                      scope === "online"
                        ? "Welcher Song geht dir nicht aus dem Kopf?"
                        : "Suche in deiner lokalen Sammlung …"
                    }
                    value={query}
                    onChange={(e) => changeQuery(e.target.value.slice(0, 200))}
                  />
                  <button
                    className="primary"
                    disabled={!query.trim() || searching}
                  >
                    Suchen
                    <ArrowUpRight />
                  </button>
                </form>
                {searching && (
                  <div className="loading" role="status">
                    Wir suchen deinen Sound …
                  </div>
                )}
                {searchError && (
                  <div role="alert" className="error-banner">
                    {searchError}
                    <button onClick={() => void runSearch(results.length > 0)}>
                      Erneut versuchen
                    </button>
                  </div>
                )}
                {!query.trim() || (scope === "online" && !searched) ? (
                  <Empty title="Da draußen wartet dein nächster Lieblingssong.">
                    Suche nach Titel oder Interpret. Hör direkt rein oder füge
                    Songs deinen lokalen Playlists hinzu.
                  </Empty>
                ) : !tracks.length && !searching && !searchError ? (
                  <Empty title="Noch kein Treffer">
                    Versuche einen anderen Titel oder Künstler.
                  </Empty>
                ) : (
                  <TrackRows
                    tracks={tracks}
                    {...rowProps}
                    onPlay={(i) => player?.playQueue(tracks, i)}
                  />
                )}
                {scope === "online" && continuation && (
                  <button
                    className="load-more secondary"
                    disabled={searching}
                    onClick={() => void runSearch(true)}
                  >
                    Weitere Songs laden
                  </button>
                )}
              </>
            )}
            {view === "favorites" &&
              (tracks.length ? (
                <>
                  <p className="page-description">
                    Deine {tracks.length} Lieblingssongs, an einem Ort.
                  </p>
                  <button
                    className="primary"
                    onClick={() => player?.playQueue(tracks)}
                  >
                    <Play fill="currentColor" />
                    Alle abspielen
                  </button>
                  <TrackRows
                    tracks={tracks}
                    {...rowProps}
                    onPlay={(i) => player?.playQueue(tracks, i)}
                  />
                </>
              ) : (
                <Empty title="Platz für deine Lieblingssongs">
                  Tippe bei einem Song auf das Herz. Hier findest du ihn wieder.
                </Empty>
              ))}
            {view === "playlist" &&
              (selected ? (
                <>
                  <section className="playlist-hero">
                    <Artwork track={selected.tracks.find((t) => t.artwork)} />
                    <div>
                      <button
                        className="text-button"
                        onClick={() => setView("library")}
                      >
                        <ArrowLeft />
                        Zur Bibliothek
                      </button>
                      <p className="eyebrow">
                        {selected.sourceId
                          ? "DEINE LOKALE KOPIE"
                          : "NUR AUF DIESEM GERÄT"}
                      </p>
                      <h2>{selected.title}</h2>
                      <p className="muted">
                        {selected.tracks.length} Songs ·{" "}
                        {formatTime(
                          selected.tracks.reduce(
                            (sum, t) => sum + t.durationMs / 1000,
                            0,
                          ),
                        )}{" "}
                        Musik
                      </p>
                      <div className="playlist-actions">
                        <button
                          className="primary"
                          disabled={!selected.tracks.length}
                          onClick={() =>
                            player?.playQueue(selected.tracks, 0, selected.id)
                          }
                        >
                          <Play fill="currentColor" />
                          Abspielen
                        </button>
                        <IconButton
                          label="Playlist mischen"
                          disabled={!selected.tracks.length}
                          onClick={() =>
                            player?.playQueue(
                              selected.tracks,
                              Math.floor(
                                Math.random() * selected.tracks.length,
                              ),
                              selected.id,
                              true,
                            )
                          }
                        >
                          <Shuffle />
                        </IconButton>
                        <IconButton
                          label="Playlist umbenennen"
                          onClick={() =>
                            openEditor(
                              { kind: "rename", id: selected.id },
                              selected.title,
                            )
                          }
                        >
                          <Pencil />
                        </IconButton>
                        {selected.sourceId && (
                          <IconButton
                            label="Playlist aktualisieren"
                            disabled={busy}
                            onClick={() => void refreshCurrent(selected)}
                          >
                            <RefreshCw className={busy ? "spin" : ""} />
                          </IconButton>
                        )}
                        <IconButton
                          label="Playlist löschen"
                          onClick={() =>
                            openEditor({ kind: "delete", id: selected.id })
                          }
                        >
                          <Trash2 />
                        </IconButton>
                      </div>
                    </div>
                  </section>
                  <div className="discovery-banner">
                    <div>
                      <Sparkles />
                      <span>
                        <strong>Dein Sound. Neue Richtungen.</strong>
                        <small>
                          Finde Songs, die zu dieser Playlist passen.
                        </small>
                      </span>
                    </div>
                    <button
                      className="secondary"
                      disabled={!selected.tracks.length}
                      onClick={() =>
                        void discover(
                          selected.tracks,
                          selected.title,
                          selected.id,
                        )
                      }
                    >
                      Ähnliche Songs
                      <ArrowUpRight />
                    </button>
                  </div>
                  {selected.tracks.length ? (
                    <TrackRows
                      tracks={selected.tracks}
                      {...rowProps}
                      onPlay={(i) =>
                        player?.playQueue(selected.tracks, i, selected.id)
                      }
                    />
                  ) : (
                    <Empty
                      title="Der erste Song wartet schon"
                      action={
                        <button
                          className="primary"
                          onClick={() => setView("search")}
                        >
                          <Search />
                          Songs entdecken
                        </button>
                      }
                    >
                      Füge Songs aus der Suche zu dieser Playlist hinzu.
                    </Empty>
                  )}
                </>
              ) : (
                <Empty title="Playlist nicht gefunden">
                  Vielleicht wurde sie bereits gelöscht.
                </Empty>
              ))}
            {view === "settings" && (
              <div className="settings-grid">
                <section className="settings-card">
                  <h2>Wiedergabe & Entdecken</h2>
                  <label className="field-label">
                    Lautstärke
                    <input
                      type="range"
                      min={0}
                      max={1}
                      step={0.01}
                      value={library.settings.volume}
                      onChange={(e) =>
                        setLibrary({
                          ...library,
                          settings: {
                            ...library.settings,
                            volume: Number(e.target.value),
                          },
                        })
                      }
                    />
                  </label>
                  <label className="switch-row">
                    <span>
                      <strong>Automatische Empfehlungen</strong>
                      <small>
                        Beim Start des letzten Playlist-Songs, ohne Shuffle oder
                        Repeat. Nicht im Hintergrund.
                      </small>
                    </span>
                    <input
                      type="checkbox"
                      checked={library.settings.autoRecommendations}
                      onChange={(e) =>
                        setLibrary({
                          ...library,
                          settings: {
                            ...library.settings,
                            autoRecommendations: e.target.checked,
                          },
                        })
                      }
                    />
                  </label>
                  <label className="switch-row">
                    <span>
                      <strong>Hörverlauf speichern</strong>
                      <small>
                        Deine letzten 20 Songs. Ausschalten löscht vorhandene
                        Einträge nicht.
                      </small>
                    </span>
                    <input
                      type="checkbox"
                      checked={library.settings.recordHistory}
                      onChange={(e) =>
                        setLibrary({
                          ...library,
                          settings: {
                            ...library.settings,
                            recordHistory: e.target.checked,
                          },
                        })
                      }
                    />
                  </label>
                  <button
                    className="text-button danger"
                    onClick={() => openEditor({ kind: "history" })}
                  >
                    <Trash2 />
                    Hörverlauf löschen
                  </button>
                </section>
                <section className="settings-card">
                  <h2>Bewegung & Bedienung</h2>
                  <label className="switch-row">
                    <span>
                      <strong>Animationen reduzieren</strong>
                      <small>
                        Systemeinstellungen für reduzierte Bewegung haben immer
                        Vorrang.
                      </small>
                    </span>
                    <input
                      type="checkbox"
                      checked={library.settings.reducedMotion}
                      onChange={(e) =>
                        setLibrary({
                          ...library,
                          settings: {
                            ...library.settings,
                            reducedMotion: e.target.checked,
                          },
                        })
                      }
                    />
                  </label>
                  <p className="shortcut-help">
                    <kbd>Leertaste</kbd> Play / Pause
                    <br />
                    <kbd>J</kbd> / <kbd>L</kbd> 10 Sekunden zurück / vor
                    <br />
                    <kbd>P</kbd> / <kbd>N</kbd> Vorheriger / nächster Song
                  </p>
                </section>
                <section className="settings-card">
                  <h2>Deine Bibliothek sichern</h2>
                  <p>
                    Playlists, Favoriten und Einstellungen liegen in diesem
                    Browserprofil. Browserdaten löschen, privater Modus oder
                    eine andere Adresse können diese Sammlung entfernen oder
                    verbergen.
                  </p>
                  <button className="secondary" onClick={exportLibrary}>
                    <Download />
                    Web-Sicherung exportieren
                  </button>
                  <label className="secondary file-label">
                    <Upload />
                    Web-Sicherung importieren
                    <input
                      type="file"
                      accept="application/json,.json"
                      onChange={async (event) => {
                        const file = event.target.files?.[0];
                        event.target.value = "";
                        if (!file) return;
                        try {
                          if (file.size > 20 * 1024 * 1024)
                            throw new Error(
                              "Sicherung zu groß (maximal 20 MB).",
                            );
                          const restored = validateLibrary(
                            JSON.parse(await file.text()),
                          );
                          openEditor({ kind: "restore", library: restored });
                        } catch (error) {
                          setNotice(message(error));
                        }
                      }}
                    />
                  </label>
                  <p className="small muted">
                    Dieses Format ist nur für PYTE Web. Kein automatischer
                    Android-Abgleich.
                  </p>
                </section>
                <section className="settings-card">
                  <h2>Transparent statt Platzhalter</h2>
                  <p>
                    Diese erste Web-Version streamt Musik.{" "}
                    <strong>
                      Offline-Downloads, Equalizer, Stille überspringen und
                      synchronisierte Lyrics sind hier noch nicht umgesetzt.
                    </strong>{" "}
                    Normale Lyrics sind verfügbar.
                  </p>
                  <p>
                    Der lokale Dienst verbindet sich für Suche, Empfehlungen,
                    Import und Audio mit YouTube. Lyrics senden Titel, Interpret
                    und Dauer an LRCLIB. Kein Google-Login, keine serverseitige
                    Bibliothek.
                  </p>
                  <p className="small muted">
                    Browser müssen geöffnet bleiben. Schlafmodus und
                    Browser-Energiesparen können Wiedergabe unterbrechen.
                    Inoffizielle YouTube-Schnittstellen können ausfallen.
                  </p>
                </section>
              </div>
            )}
          </>
        )}
        <footer className="page-footer">
          <span>PYTE · YOUR MUSIC. YOUR SPACE.</span>
          <span>Desktop preview · Lokal auf deinem Computer</span>
        </footer>
      </main>
      {panel && (
        <aside className="right-panel" aria-label="Queue und Lyrics">
          <header>
            <div className="panel-tabs">
              <button
                className={panel === "queue" ? "selected" : ""}
                onClick={() => setPanel("queue")}
              >
                Queue <small>{playback?.queue.length ?? 0}</small>
              </button>
              <button
                className={panel === "lyrics" ? "selected" : ""}
                onClick={() => setPanel("lyrics")}
              >
                Lyrics
              </button>
            </div>
            <IconButton
              label="Seitenleiste schließen"
              onClick={() => setPanel(null)}
            >
              <X />
            </IconButton>
          </header>
          {panel === "queue" ? (
            playback?.queue.length ? (
              <>
                <p className="queue-caption">
                  {playback.previewing
                    ? "Vorschau · Original-Queue bleibt erhalten"
                    : "ALS NÄCHSTES IN DEINEM RAUM"}
                </p>
                <ol className="queue-list">
                  {playback.queue.map((entry, i) => (
                    <li
                      className={i === playback.index ? "current" : ""}
                      key={entry.uid}
                    >
                      <button
                        className="queue-song"
                        onClick={() => player?.playIndex(i)}
                      >
                        <Artwork track={entry.track} />
                        <span>
                          <strong>{entry.track.title}</strong>
                          <small>{entry.track.artist}</small>
                        </span>
                      </button>
                      {!playback.previewing && (
                        <div className="queue-tools">
                          <IconButton
                            label={`${entry.track.title} nach oben`}
                            disabled={i === 0}
                            onClick={() => player?.move(i, i - 1)}
                          >
                            <ArrowUp />
                          </IconButton>
                          <IconButton
                            label={`${entry.track.title} nach unten`}
                            disabled={i === playback.queue.length - 1}
                            onClick={() => player?.move(i, i + 1)}
                          >
                            <ArrowDown />
                          </IconButton>
                          <IconButton
                            label={`${entry.track.title} aus Queue entfernen`}
                            onClick={() => player?.remove(i)}
                          >
                            <X />
                          </IconButton>
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              </>
            ) : (
              <div className="queue-empty">
                <div className="queue-illustration">
                  <ListMusic />
                  <span />
                  <span />
                  <span />
                </div>
                <h2>Raum für deinen Sound.</h2>
                <p>
                  Starte einen Song oder eine Playlist. Deine nächsten Titel
                  warten dann hier auf dich.
                </p>
                <button
                  className="text-button"
                  onClick={() => setView("search")}
                >
                  Musik entdecken
                  <ArrowUpRight />
                </button>
              </div>
            )
          ) : (
            <div className="lyrics-panel">
              {current ? (
                <>
                  <Artwork track={current} />
                  <h2>{current.title}</h2>
                  <p className="muted">{current.artist}</p>
                  <p className="lyrics-text" role="status">
                    {lyricsLoading ? "Lyrics werden geladen …" : lyrics}
                  </p>
                  <span className="small muted">
                    Lyrics von LRCLIB · nicht synchronisiert
                  </span>
                </>
              ) : (
                <Empty title="Worte zu deinem Sound">
                  Starte zuerst einen Song.
                </Empty>
              )}
            </div>
          )}
        </aside>
      )}
      <section className="player-bar" aria-label="Musikplayer">
        <div className="now-playing">
          <Artwork track={current} />
          <div>
            <strong>
              {current?.title || "Dein nächster Lieblingssong wartet."}
            </strong>
            <span>
              {current?.artist || "Wähle einen Song und drück auf Play."}
            </span>
          </div>
          {current && (
            <IconButton
              label="Aktuellen Song favorisieren"
              active={!!library?.favorites.some((t) => t.id === current.id)}
              onClick={() => toggleFavorite(current)}
            >
              <Heart
                fill={
                  library?.favorites.some((t) => t.id === current.id)
                    ? "currentColor"
                    : "none"
                }
              />
            </IconButton>
          )}
        </div>
        <div className="transport">
          <div className="transport-buttons">
            <IconButton
              label="Shuffle"
              disabled={!current || playback?.previewing}
              active={playback?.shuffle}
              onClick={() => player?.setShuffle(!playback?.shuffle)}
            >
              <Shuffle />
            </IconButton>
            <IconButton
              label="Vorheriger Song"
              disabled={!current}
              onClick={() => player?.previous()}
            >
              <SkipBack fill="currentColor" />
            </IconButton>
            <IconButton
              className="play-button"
              label={playback?.playing ? "Pause" : "Abspielen"}
              disabled={!current}
              onClick={() => player?.toggle()}
            >
              {playback?.playing ? (
                <Pause fill="currentColor" />
              ) : (
                <Play fill="currentColor" />
              )}
            </IconButton>
            <IconButton
              label="Nächster Song"
              disabled={!current}
              onClick={() => player?.next()}
            >
              <SkipForward fill="currentColor" />
            </IconButton>
            <IconButton
              label={`Wiederholung: ${playback?.repeat === "one" ? "ein Song" : playback?.repeat === "all" ? "alle" : "aus"}`}
              disabled={!current || playback?.previewing}
              active={playback?.repeat !== "off"}
              onClick={() => player?.setRepeat()}
            >
              {playback?.repeat === "one" ? <Repeat1 /> : <Repeat />}
            </IconButton>
          </div>
          <div className="timeline">
            <span>{formatTime(playback?.position ?? 0)}</span>
            <input
              type="range"
              aria-label="Wiedergabeposition"
              min={0}
              max={playback?.duration || 1}
              step={0.1}
              value={Math.min(playback?.position ?? 0, playback?.duration || 1)}
              disabled={!current}
              onChange={(e) => player?.seek(Number(e.target.value))}
            />
            <span>{formatTime(playback?.duration ?? 0)}</span>
          </div>
        </div>
        <div className="player-extras">
          <Volume2 aria-hidden="true" />
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            aria-label="Lautstärke"
            value={library?.settings.volume ?? 0.75}
            onChange={(e) =>
              setLibrary((old) =>
                old
                  ? {
                      ...old,
                      settings: {
                        ...old.settings,
                        volume: Number(e.target.value),
                      },
                    }
                  : old,
              )
            }
          />
          <IconButton
            label="Ähnliche Songs zum aktuellen Titel"
            disabled={!current}
            onClick={() => current && void discover([current], current.title)}
          >
            <Sparkles />
          </IconButton>
          <IconButton
            label="Lyrics anzeigen"
            active={panel === "lyrics"}
            onClick={() => setPanel(panel === "lyrics" ? null : "lyrics")}
          >
            <FileText />
          </IconButton>
          <IconButton
            label="Queue anzeigen"
            active={panel === "queue"}
            onClick={() => setPanel(panel === "queue" ? null : "queue")}
          >
            <ListMusic />
          </IconButton>
        </div>
      </section>
      {playback?.error && (
        <div className="playback-error" role="alert">
          {playback.error}
          <button onClick={() => player?.playIndex(playback.index)}>
            Erneut versuchen
          </button>
        </div>
      )}
      {playback?.loading && (
        <div className="buffering" role="status">
          Audio wird geladen …
        </div>
      )}
      {notice && (
        <div className="toast" role="status">
          <Check />
          {notice}
        </div>
      )}
      {editor && (
        <Modal
          title={
            editor.kind === "import"
              ? "Bring deine Playlist mit."
              : editor.kind === "create"
                ? "Ein neuer Raum für Musik."
                : editor.kind === "rename"
                  ? "Playlist umbenennen"
                  : editor.kind === "delete"
                    ? "Lokale Playlist löschen?"
                    : editor.kind === "history"
                      ? "Hörverlauf löschen?"
                      : "Web-Bibliothek ersetzen?"
          }
          busy={busy}
          onClose={() => setEditor(null)}
        >
          <form onSubmit={(event) => void submitEditor(event)}>
            {["import", "create", "rename"].includes(editor.kind) ? (
              <>
                <p className="muted">
                  {editor.kind === "import"
                    ? "Ein öffentlicher YouTube-Music- oder YouTube-Playlist-Link genügt. Kein Login. Keine Änderungen an deinem YouTube-Konto."
                    : "Nur in deinem Browser gespeichert. Ganz nach deinem Geschmack."}
                </p>
                <label className="field-label">
                  {editor.kind === "import" ? "Playlist-Link" : "Playlist-Name"}
                  <input
                    autoFocus
                    required
                    maxLength={editor.kind === "import" ? 2048 : 100}
                    placeholder={
                      editor.kind === "import"
                        ? "https://music.youtube.com/playlist?list=…"
                        : "Zum Beispiel: Nachts unterwegs"
                    }
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    disabled={busy}
                  />
                </label>
              </>
            ) : (
              <p className="muted">
                {editor.kind === "delete"
                  ? "Die lokale Playlist wird entfernt. Favoriten, Verlauf und die aktuelle Queue bleiben erhalten. Das kann nicht rückgängig gemacht werden."
                  : editor.kind === "history"
                    ? "Deine zuletzt gehörten Songs werden entfernt. Playlists und Favoriten bleiben erhalten."
                    : "Diese Sicherung ersetzt deine vorhandenen Web-Playlists, Favoriten und Einstellungen. Exportiere vorher eine Sicherung, wenn du sie behalten möchtest."}
              </p>
            )}
            {formError && (
              <p role="alert" className="form-error">
                {formError}
              </p>
            )}
            <div className="modal-actions">
              <button
                type="button"
                className="secondary"
                disabled={busy}
                onClick={() => setEditor(null)}
              >
                Abbrechen
              </button>
              <button
                className={`primary ${editor.kind === "delete" ? "destructive" : ""}`}
                disabled={busy}
              >
                {busy
                  ? "Wird geladen …"
                  : editor.kind === "import"
                    ? "Lokal importieren"
                    : editor.kind === "create"
                      ? "Playlist erstellen"
                      : editor.kind === "rename"
                        ? "Speichern"
                        : editor.kind === "restore"
                          ? "Sicherung übernehmen"
                          : "Jetzt löschen"}
              </button>
            </div>
          </form>
        </Modal>
      )}
      {discovery && (
        <Modal
          title="Dein Sound. Neue Entdeckungen."
          wide
          onClose={() => {
            discoveryRequest.current?.abort();
            player?.restore();
            setDiscovery(null);
          }}
        >
          <div className="discovery-intro">
            <span className="pill">
              <Sparkles />
              INSPIRIERT VON DEINER MUSIK
            </span>
            <p>
              Ähnlich zu <strong>{discovery.label}</strong>. Hör rein, ohne
              deine Queue zu verlieren.
            </p>
            <button
              className="text-button"
              disabled={discovery.loading}
              onClick={() =>
                void discover(
                  discovery.seeds,
                  discovery.label,
                  discovery.playlistId,
                )
              }
            >
              <RefreshCw />
              Neue Vorschläge
            </button>
          </div>
          {playback?.previewing && (
            <div className="preview-strip">
              <span>
                <Headphones />
                <strong>Vorschau: {current?.title}</strong>
              </span>
              <button className="secondary" onClick={() => player?.restore()}>
                Zurück zur Wiedergabe
              </button>
            </div>
          )}
          {playback?.previewing && playback.error && (
            <p className="form-error" role="alert">
              {playback.error}
            </p>
          )}
          {discovery.loading && (
            <div className="loading" role="status">
              Wir finden neue Richtungen für deinen Sound …
            </div>
          )}
          {discovery.error && (
            <div role="alert" className="error-banner">
              {discovery.error}
              <button
                onClick={() =>
                  void discover(
                    discovery.seeds,
                    discovery.label,
                    discovery.playlistId,
                  )
                }
              >
                Erneut versuchen
              </button>
            </div>
          )}
          {discovery.partial && (
            <p className="muted small">
              Einige Quellen waren nicht erreichbar. Diese Vorschläge sind
              bereit für dich.
            </p>
          )}
          <div className="recommendation-grid">
            {discovery.tracks.map((track) => (
              <article
                className={`recommendation-card ${current?.id === track.id && playback?.previewing ? "listening" : ""}`}
                key={track.id}
              >
                <Artwork track={track} />
                <div>
                  <h3>{track.title}</h3>
                  <p>{track.artist}</p>
                  <small>Weil du „{discovery.reasons[track.id]}“ magst.</small>
                </div>
                <div className="recommendation-actions">
                  <button
                    className="secondary"
                    onClick={() => player?.preview(track)}
                  >
                    {playback?.previewing &&
                    current?.id === track.id &&
                    playback.playing ? (
                      <Pause />
                    ) : (
                      <Play />
                    )}
                    {playback?.previewing &&
                    current?.id === track.id &&
                    playback.playing
                      ? "Pause"
                      : "Anhören"}
                  </button>
                  <button
                    className={`primary ${discovery.added.has(track.id) ? "saved" : ""}`}
                    disabled={discovery.added.has(track.id)}
                    onClick={() => addRecommendation(track)}
                  >
                    {discovery.added.has(track.id) ? <Check /> : <Plus />}
                    {discovery.added.has(track.id)
                      ? "Hinzugefügt"
                      : "Hinzufügen"}
                  </button>
                </div>
              </article>
            ))}
          </div>
          {!discovery.loading &&
            !discovery.error &&
            !discovery.tracks.length && (
              <Empty title="Gerade keine neuen Vorschläge">
                Versuche einen anderen Song oder eine andere Playlist.
                Vorhandene Titel werden herausgefiltert.
              </Empty>
            )}
        </Modal>
      )}
      {picker && (
        <Modal
          title="Zu deiner lokalen Playlist"
          onClose={() => setPicker(null)}
        >
          <div className="picker-track">
            <Artwork track={picker} />
            <div>
              <strong>{picker.title}</strong>
              <p>{picker.artist}</p>
            </div>
          </div>
          <p className="muted small">
            Nur deine Web-Bibliothek wird verändert, nicht dein YouTube-Konto.
          </p>
          <div className="playlist-picker">
            {library?.playlists.map((p) => (
              <button key={p.id} onClick={() => saveSong(picker, p.id)}>
                <ListMusic />
                <span>
                  <strong>{p.title}</strong>
                  <small>{p.tracks.length} Songs</small>
                </span>
                <Plus />
              </button>
            ))}
          </div>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              saveSong(picker, undefined, input);
            }}
          >
            <label className="field-label">
              Oder eine neue Playlist erstellen
              <input
                autoFocus
                maxLength={100}
                required
                placeholder="Name der neuen Playlist"
                value={input}
                onChange={(e) => setInput(e.target.value)}
              />
            </label>
            {formError && (
              <p className="form-error" role="alert">
                {formError}
              </p>
            )}
            <button className="primary full-width" disabled={!input.trim()}>
              <Plus />
              Erstellen & hinzufügen
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
