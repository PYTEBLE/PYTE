import { useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import { X, Music2, Heart, Play, ListPlus, Plus, Check } from "lucide-react";
import type { Track } from "./domain.ts";
import { formatTime } from "./domain.ts";

export function IconButton({
  label,
  onClick,
  children,
  disabled,
  active = false,
  className = "",
}: {
  label: string;
  onClick: () => void;
  children: ReactNode;
  disabled?: boolean;
  active?: boolean;
  className?: string;
}) {
  return (
    <button
      type="button"
      className={`icon-button ${active ? "active" : ""} ${className}`}
      aria-label={label}
      title={label}
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}
export function Artwork({
  track,
  size = "",
  className = "",
}: {
  track?: Track;
  size?: string;
  className?: string;
}) {
  return (
    <div className={`artwork ${size} ${className}`}>
      <Music2 aria-hidden="true" />
      {track?.artwork && (
        <img
          src={track.artwork}
          alt=""
          loading="lazy"
          referrerPolicy="no-referrer"
          onLoad={(event) => {
            event.currentTarget.style.visibility = "";
          }}
          onError={(event) => {
            event.currentTarget.style.visibility = "hidden";
          }}
        />
      )}
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
  busy = false,
  wide = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null),
    id = useId();
  useEffect(() => {
    const dialog = ref.current!;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className={`modal ${wide ? "wide" : ""}`}
      aria-labelledby={id}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(event) => {
        if (
          !busy &&
          event.target === event.currentTarget &&
          event.clientX &&
          (event.clientX < event.currentTarget.getBoundingClientRect().left ||
            event.clientX > event.currentTarget.getBoundingClientRect().right ||
            event.clientY < event.currentTarget.getBoundingClientRect().top ||
            event.clientY > event.currentTarget.getBoundingClientRect().bottom)
        )
          onClose();
      }}
    >
      <header className="modal-header">
        <h2 id={id}>{title}</h2>
        <IconButton label="Schließen" disabled={busy} onClick={onClose}>
          <X />
        </IconButton>
      </header>
      {children}
    </dialog>
  );
}
export function Empty({
  title,
  children,
  action,
}: {
  title: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <section className="empty">
      <div className="empty-icon">
        <Music2 />
      </div>
      <h2>{title}</h2>
      <p>{children}</p>
      {action}
    </section>
  );
}
export function TrackRows({
  tracks,
  currentId,
  favorites,
  onPlay,
  onFavorite,
  onAdd,
  onQueue,
  added = new Set<string>(),
}: {
  tracks: Track[];
  currentId?: string;
  favorites: Track[];
  onPlay: (index: number) => void;
  onFavorite: (track: Track) => void;
  onAdd: (track: Track) => void;
  onQueue: (track: Track) => void;
  added?: Set<string>;
}) {
  return (
    <div className="track-list" role="list" aria-label="Songs">
      {tracks.map((track, index) => (
        <div
          role="listitem"
          className={`track-row ${currentId === track.id ? "current" : ""}`}
          key={`${index}:${track.id}`}
        >
          <span className="track-number" aria-hidden="true">
            {String(index + 1).padStart(2, "0")}
          </span>
          <button
            className="track-main"
            onClick={() => onPlay(index)}
            aria-label={`${track.title} abspielen`}
          >
            <Artwork track={track} />
            <span className="track-info">
              <strong>{track.title}</strong>
              <span>{track.artist}</span>
            </span>
            <Play className="row-play" aria-hidden="true" />
          </button>
          <span className="track-time">
            {track.durationMs ? formatTime(track.durationMs / 1000) : "—"}
          </span>
          <IconButton
            label={`${favorites.some((t) => t.id === track.id) ? "Favorit entfernen" : "Als Favorit speichern"}: ${track.title}`}
            active={favorites.some((t) => t.id === track.id)}
            onClick={() => onFavorite(track)}
          >
            <Heart
              fill={
                favorites.some((t) => t.id === track.id)
                  ? "currentColor"
                  : "none"
              }
            />
          </IconButton>
          <IconButton
            label={`Zur Queue: ${track.title}`}
            onClick={() => onQueue(track)}
          >
            <ListPlus />
          </IconButton>
          <IconButton
            label={
              added.has(track.id)
                ? `${track.title} hinzugefügt`
                : `Zur Playlist: ${track.title}`
            }
            disabled={added.has(track.id)}
            onClick={() => onAdd(track)}
          >
            {added.has(track.id) ? <Check /> : <Plus />}
          </IconButton>
        </div>
      ))}
    </div>
  );
}
