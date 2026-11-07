import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import {
  Heart,
  Maximize,
  Minimize,
  Pause,
  Play,
  Repeat,
  Repeat1,
  Shuffle,
  SkipBack,
  SkipForward,
  X,
} from "lucide-react";
import type { Track } from "./domain.ts";
import { formatTime } from "./domain.ts";
import { coverPalette } from "./palette.ts";
import type { CoverPalette } from "./palette.ts";
import { LyricsView } from "./LyricsView.tsx";
import { Artwork, IconButton } from "./components.tsx";

export interface ImmersiveProps {
  track: Track;
  position: number;
  duration: number;
  playing: boolean;
  loading: boolean;
  shuffle: boolean;
  repeat: "off" | "all" | "one";
  favorite: boolean;
  onClose: () => void;
  onToggle: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onSeek: (seconds: number) => void;
  onShuffle: () => void;
  onRepeat: () => void;
  onFavorite: () => void;
}

export function ImmersivePlayer(props: ImmersiveProps) {
  const { track } = props;
  const [palette, setPalette] = useState<CoverPalette | null>(null);
  const [fullscreen, setFullscreen] = useState(
    Boolean(document.fullscreenElement),
  );
  const root = useRef<HTMLElement>(null);
  const artwork = track.artwork
    ? `/api/art?url=${encodeURIComponent(track.artwork)}`
    : null;

  useEffect(() => {
    // Focus the overlay itself so keyboard shortcuts (Space, J/L) work immediately.
    root.current?.focus();
  }, []);

  useEffect(() => {
    let alive = true;
    setPalette(null);
    if (artwork)
      coverPalette(artwork).then((value) => alive && setPalette(value));
    return () => {
      alive = false;
    };
  }, [artwork]);

  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !document.querySelector("dialog[open]"))
        props.onClose();
    };
    const change = () => setFullscreen(Boolean(document.fullscreenElement));
    window.addEventListener("keydown", keydown);
    document.addEventListener("fullscreenchange", change);
    return () => {
      window.removeEventListener("keydown", keydown);
      document.removeEventListener("fullscreenchange", change);
    };
    // Handlers come from the parent on every render; the listeners only need to stay attached.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else
      void document.documentElement
        .requestFullscreen?.()
        .catch(() => undefined);
  };
  const close = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    props.onClose();
  };
  const style = palette
    ? ({
        "--cover-accent": palette.accent,
        "--cover-glow": palette.glow,
      } as CSSProperties)
    : undefined;

  return (
    <section
      ref={root}
      tabIndex={-1}
      className="immersive"
      role="dialog"
      aria-modal="false"
      aria-label="Vollbild-Player"
      style={style}
    >
      {artwork && <img className="immersive-backdrop" src={artwork} alt="" />}
      <div className="immersive-scrim" aria-hidden="true" />
      <header className="immersive-top">
        <IconButton label="Vollbild-Player schließen" onClick={close}>
          <X />
        </IconButton>
        <span className="eyebrow">JETZT LÄUFT</span>
        <IconButton
          label={fullscreen ? "Vollbild beenden" : "Bildschirm füllen"}
          onClick={toggleFullscreen}
        >
          {fullscreen ? <Minimize /> : <Maximize />}
        </IconButton>
      </header>
      <div className="immersive-main">
        <div className="immersive-now">
          <div className="immersive-cover">
            <Artwork track={track} size="immersive-art" />
          </div>
          <div className="immersive-meta">
            <h2>{track.title}</h2>
            <p>{track.artist}</p>
          </div>
          <div className="immersive-progress">
            <span>{formatTime(props.position)}</span>
            <input
              type="range"
              aria-label="Wiedergabeposition"
              min={0}
              max={props.duration || 1}
              step={0.1}
              value={Math.min(props.position, props.duration || 1)}
              onChange={(event) => props.onSeek(Number(event.target.value))}
            />
            <span>{formatTime(props.duration)}</span>
          </div>
          <div className="immersive-controls">
            <IconButton
              label="Shuffle"
              active={props.shuffle}
              onClick={props.onShuffle}
            >
              <Shuffle />
            </IconButton>
            <IconButton label="Vorheriger Song" onClick={props.onPrevious}>
              <SkipBack fill="currentColor" />
            </IconButton>
            <button
              type="button"
              className="immersive-play"
              aria-label={props.playing ? "Pause" : "Abspielen"}
              onClick={props.onToggle}
              disabled={props.loading && !props.playing}
            >
              {props.playing ? (
                <Pause fill="currentColor" />
              ) : (
                <Play fill="currentColor" />
              )}
            </button>
            <IconButton label="Nächster Song" onClick={props.onNext}>
              <SkipForward fill="currentColor" />
            </IconButton>
            <IconButton
              label={`Wiederholung: ${props.repeat === "one" ? "ein Song" : props.repeat === "all" ? "alle" : "aus"}`}
              active={props.repeat !== "off"}
              onClick={props.onRepeat}
            >
              {props.repeat === "one" ? <Repeat1 /> : <Repeat />}
            </IconButton>
            <IconButton
              label={
                props.favorite ? "Favorit entfernen" : "Als Favorit speichern"
              }
              active={props.favorite}
              onClick={props.onFavorite}
            >
              <Heart fill={props.favorite ? "currentColor" : "none"} />
            </IconButton>
          </div>
        </div>
        <div className="immersive-lyrics">
          <LyricsView
            track={track}
            position={props.position}
            playing={props.playing}
            onSeek={props.onSeek}
            variant="immersive"
          />
        </div>
      </div>
    </section>
  );
}
