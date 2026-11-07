import { useEffect, useState } from "react";
import type { Track } from "./domain.ts";
import { api } from "./api.ts";

export interface LyricLine {
  timeMs: number;
  text: string;
}

export interface LyricsData {
  lines: LyricLine[];
  plainLyrics: string | null;
  instrumental: boolean;
}

export type LyricsState =
  | { status: "idle" | "loading" }
  | { status: "ready"; data: LyricsData }
  | { status: "error"; message: string };

/** Index of the last line that has started at positionMs, or -1 before the first line. */
export function activeLine(lines: LyricLine[], positionMs: number): number {
  let low = 0,
    high = lines.length - 1,
    found = -1;
  while (low <= high) {
    const middle = (low + high) >> 1;
    if (lines[middle].timeMs <= positionMs) {
      found = middle;
      low = middle + 1;
    } else high = middle - 1;
  }
  return found;
}

const cache = new Map<string, LyricsData>();

function remember(id: string, data: LyricsData) {
  cache.delete(id);
  cache.set(id, data);
  while (cache.size > 60) cache.delete(cache.keys().next().value!);
}

export function useLyrics(track: Track | undefined) {
  const [state, setState] = useState<LyricsState>({ status: "idle" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!track) {
      setState({ status: "idle" });
      return;
    }
    const hit = cache.get(track.id);
    if (hit) {
      setState({ status: "ready", data: hit });
      return;
    }
    const abort = new AbortController();
    setState({ status: "loading" });
    api<LyricsData>(
      "lyrics",
      {
        title: track.title,
        artist: track.artist,
        durationMs: track.durationMs,
      },
      abort.signal,
    )
      .then((data) => {
        remember(track.id, data);
        setState({ status: "ready", data });
      })
      .catch((error: unknown) => {
        if (abort.signal.aborted) return;
        setState({
          status: "error",
          message:
            error instanceof Error
              ? error.message
              : "Lyrics konnten nicht geladen werden.",
        });
      });
    return () => abort.abort();
    // The track identity is its id; retry bumps attempt.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [track?.id, attempt]);
  return { state, retry: () => setAttempt((value) => value + 1) };
}
