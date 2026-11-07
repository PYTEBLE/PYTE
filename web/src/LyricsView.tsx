import { useEffect, useRef, useState } from "react";
import type { Track } from "./domain.ts";
import { activeLine, useLyrics } from "./lyrics.ts";

interface Props {
  track: Track | undefined;
  position: number;
  playing: boolean;
  onSeek: (seconds: number) => void;
  variant: "panel" | "immersive";
}

function prefersReducedMotion() {
  return (
    document.documentElement.dataset.motion === "reduced" ||
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

export function LyricsView({
  track,
  position,
  playing,
  onSeek,
  variant,
}: Props) {
  const { state, retry } = useLyrics(track);
  const scroller = useRef<HTMLDivElement>(null);
  const [follow, setFollow] = useState(true);
  const lines = state.status === "ready" ? state.data.lines : [];
  const active = lines.length ? activeLine(lines, position * 1000) : -1;

  useEffect(() => {
    setFollow(true);
  }, [track?.id]);

  useEffect(() => {
    if (!follow || active < 0 || !scroller.current) return;
    const container = scroller.current;
    const line = container.querySelector<HTMLElement>(
      `[data-line="${active}"]`,
    );
    if (!line) return;
    const top =
      line.offsetTop - container.clientHeight / 2 + line.clientHeight / 2;
    container.scrollTo({
      top,
      behavior: prefersReducedMotion() ? "auto" : "smooth",
    });
  }, [active, follow]);

  const wrapper = `lyrics-view ${variant}`;
  const footer = <p className="lyrics-credit">Lyrics von LRCLIB</p>;

  if (!track) {
    return (
      <div className={wrapper}>
        <p className="lyrics-message">Starte einen Song, um Lyrics zu sehen.</p>
      </div>
    );
  }

  if (state.status === "loading" || state.status === "idle") {
    return (
      <div className={wrapper} role="status" aria-label="Lyrics werden geladen">
        <div className="lyrics-skeleton" aria-hidden="true">
          {[72, 58, 84, 64, 76].map((width, index) => (
            <span key={index} style={{ width: `${width}%` }} />
          ))}
        </div>
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <div className={wrapper}>
        <p className="lyrics-message" role="alert">
          {state.message}
        </p>
        <button className="secondary" onClick={retry}>
          Erneut versuchen
        </button>
      </div>
    );
  }

  const data = state.status === "ready" ? state.data : null;
  if (!data) return null;
  if (data.instrumental) {
    return (
      <div className={wrapper}>
        <p className="lyrics-message">Instrumental · keine Lyrics vorhanden.</p>
      </div>
    );
  }

  if (lines.length) {
    return (
      <div className={wrapper}>
        <div
          ref={scroller}
          className="lyrics-scroll"
          onWheel={() => setFollow(false)}
          onTouchStart={() => setFollow(false)}
          onPointerDown={() => setFollow(false)}
        >
          <div className="lyrics-spacer" aria-hidden="true" />
          {lines.map((line, index) => (
            <button
              key={`${index}:${line.timeMs}`}
              type="button"
              data-line={index}
              className={`lyric-line ${index === active ? "active" : index < active ? "past" : ""}`}
              onClick={() => {
                onSeek(line.timeMs / 1000);
                setFollow(true);
              }}
            >
              <span className="lyric-text">{line.text || "♪"}</span>
            </button>
          ))}
          <div className="lyrics-spacer" aria-hidden="true" />
        </div>
        {!follow && (
          <button className="lyrics-follow" onClick={() => setFollow(true)}>
            Folgen
          </button>
        )}
        <LyricWave active={playing} />
        {footer}
      </div>
    );
  }

  if (data.plainLyrics) {
    return (
      <div className={wrapper}>
        <p className="lyrics-plain-note">Ohne Zeitmarken – Text zum Mitlesen</p>
        <div className="lyrics-scroll lyrics-plain">
          <p>{data.plainLyrics}</p>
        </div>
        <LyricWave active={playing} />
        {footer}
      </div>
    );
  }

  return (
    <div className={wrapper}>
      <p className="lyrics-message">
        Für diesen Song sind keine Lyrics verfügbar.
      </p>
      <button className="secondary" onClick={retry}>
        Erneut prüfen
      </button>
    </div>
  );
}

function LyricWave({ active }: { active: boolean }) {
  return (
    <svg
      className={`lyric-wave ${active ? "playing" : ""}`}
      viewBox="0 0 400 60"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <path
        className="wave-back"
        d="M0 30 C 50 4, 100 56, 150 30 S 250 4, 300 30 S 380 56, 400 30"
      />
      <path
        className="wave-mid"
        d="M0 30 C 60 10, 110 50, 170 30 S 270 10, 330 30 S 380 50, 400 30"
      />
      <path
        className="wave-front"
        d="M0 30 C 40 18, 120 42, 180 30 S 280 18, 340 30 S 380 42, 400 30"
      />
    </svg>
  );
}
