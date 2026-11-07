import type { Track } from "./domain.ts";
import type { Equalizer, EqualizerSettings } from "./equalizer.ts";

export interface Entry {
  uid: string;
  track: Track;
}
export interface Origin {
  playlistId: string;
  runId: string;
  lastUid: string;
}
export interface Playback {
  queue: Entry[];
  index: number;
  playing: boolean;
  loading: boolean;
  position: number;
  duration: number;
  shuffle: boolean;
  repeat: "off" | "all" | "one";
  error: string | null;
  previewing: boolean;
  origin?: Origin;
  suggestion?: { playlistId: string; runId: string };
}
type AudioPort = Pick<
  HTMLAudioElement,
  | "src"
  | "currentTime"
  | "duration"
  | "volume"
  | "paused"
  | "play"
  | "pause"
  | "load"
  | "addEventListener"
  | "removeEventListener"
>;
const initial = (): Playback => ({
  queue: [],
  index: -1,
  playing: false,
  loading: false,
  position: 0,
  duration: 0,
  shuffle: false,
  repeat: "off",
  error: null,
  previewing: false,
});
export class WebPlayer {
  private state = initial();
  private listeners = new Set<() => void>();
  private events: [string, EventListener][] = [];
  private saved?: {
    state: Playback;
    position: number;
    intent: boolean;
    order: string[];
  };
  private visited = new Set<string>();
  private order: string[] = [];
  private generation = 0;
  private equalizer?: Equalizer;
  private pendingSeek = 0;
  private audio: AudioPort;
  private foreground: () => boolean;
  automatic = true;
  constructor(audio: AudioPort, foreground: () => boolean = () => true) {
    this.audio = audio;
    this.foreground = foreground;
    const on = (name: string, action: () => void) => {
      this.events.push([name, action]);
      audio.addEventListener(name, action);
    };
    on("playing", () => {
      this.update({ playing: true, loading: false, error: null });
      this.considerDiscovery();
    });
    on("pause", () => this.update({ playing: false }));
    on("waiting", () => this.update({ loading: true }));
    on("timeupdate", () => this.update({ position: audio.currentTime }));
    on("loadedmetadata", () => {
      if (this.pendingSeek) {
        audio.currentTime = this.pendingSeek;
        this.pendingSeek = 0;
      }
      this.update({
        duration: Number.isFinite(audio.duration) ? audio.duration : 0,
      });
    });
    on("error", () =>
      this.update({
        loading: false,
        playing: false,
        error:
          "Dieser Song konnte nicht geladen werden. Bitte erneut starten oder einen anderen Titel wählen.",
      }),
    );
    on("ended", () => {
      if (this.saved) this.restore();
      else if (this.state.repeat === "one") {
        this.seek(0);
        void this.play();
      } else this.next(false);
    });
  }
  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  private update(patch: Partial<Playback>) {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((listener) => listener());
  }
  private considerDiscovery() {
    const { origin, index, queue, previewing, shuffle, repeat } = this.state;
    if (
      !origin ||
      previewing ||
      index !== queue.length - 1 ||
      queue[index]?.uid !== origin.lastUid ||
      this.visited.has(origin.runId)
    )
      return;
    this.visited.add(origin.runId);
    if (this.visited.size > 64)
      this.visited.delete(this.visited.values().next().value!);
    if (this.automatic && this.foreground() && !shuffle && repeat === "off")
      this.update({
        suggestion: { playlistId: origin.playlistId, runId: origin.runId },
      });
  }
  consumeSuggestion() {
    this.update({ suggestion: undefined });
  }
  private async play() {
    const generation = this.generation;
    try {
      // Web Audio may only start from a user gesture; playback is one, so build the graph here.
      if (this.equalizer) {
        try {
          this.equalizer.ensure();
        } catch {
          this.equalizer = undefined;
        }
      }
      await this.audio.play();
    } catch (error) {
      if (
        generation !== this.generation ||
        (error instanceof DOMException && error.name === "AbortError")
      )
        return;
      this.update({
        loading: false,
        playing: false,
        error:
          error instanceof DOMException && error.name === "NotAllowedError"
            ? "Zum Starten bitte auf Play klicken – der Browser benötigt eine Benutzeraktion."
            : "Wiedergabe fehlgeschlagen. Bitte erneut starten.",
      });
    }
  }
  private load(index: number, intent = true, position = 0) {
    const entry = this.state.queue[index];
    if (!entry) return;
    this.generation++;
    this.audio.pause();
    this.pendingSeek = position;
    this.update({
      index,
      position,
      duration: entry.track.durationMs / 1000,
      playing: false,
      loading: intent,
      error: null,
    });
    this.audio.src = `/api/audio/${entry.track.id}`;
    this.audio.load();
    if (intent) void this.play();
  }
  playQueue(tracks: Track[], index = 0, playlistId?: string, shuffle = false) {
    if (!tracks.length) return;
    this.saved = undefined;
    const queue = tracks.map((track) => ({ uid: crypto.randomUUID(), track }));
    index = Math.max(0, Math.min(queue.length - 1, index));
    this.update({
      queue,
      previewing: false,
      origin: playlistId
        ? { playlistId, runId: crypto.randomUUID(), lastUid: queue.at(-1)!.uid }
        : undefined,
      suggestion: undefined,
    });
    this.order = queue.map((e) => e.uid);
    this.update({ index });
    this.setShuffle(shuffle);
    this.load(index);
  }
  toggle() {
    if (!this.state.queue.length) return;
    if (!this.audio.paused) this.audio.pause();
    else {
      if (
        this.state.duration &&
        this.audio.currentTime >= this.state.duration - 0.1
      )
        this.seek(0);
      void this.play();
    }
  }
  preview(track: Track) {
    if (
      this.state.previewing &&
      this.state.queue[this.state.index]?.track.id === track.id
    ) {
      this.toggle();
      return;
    }
    this.saved ??= {
      state: this.state,
      position: this.audio.currentTime,
      intent: !this.audio.paused,
      order: [...this.order],
    };
    const queue = [{ uid: crypto.randomUUID(), track }];
    this.order = [queue[0].uid];
    this.update({
      queue,
      index: 0,
      previewing: true,
      shuffle: false,
      repeat: "off",
      origin: undefined,
      suggestion: undefined,
    });
    this.load(0);
  }
  restore() {
    if (!this.saved) return;
    const saved = this.saved;
    this.saved = undefined;
    this.order = saved.order;
    this.audio.pause();
    this.generation++;
    this.update({ ...saved.state, previewing: false, suggestion: undefined });
    if (saved.state.queue.length)
      this.load(saved.state.index, saved.intent, saved.position);
    else {
      this.audio.src = "";
      this.audio.load();
      this.update({
        ...initial(),
        shuffle: saved.state.shuffle,
        repeat: saved.state.repeat,
      });
    }
  }
  seek(position: number) {
    const value = Math.max(
      0,
      Math.min(this.state.duration || Number.MAX_SAFE_INTEGER, position),
    );
    this.audio.currentTime = value;
    this.update({ position: value });
  }
  volume(value: number) {
    const clamped = Math.min(1, Math.max(0, value));
    if (this.equalizer) this.equalizer.setVolume(clamped);
    else this.audio.volume = clamped;
  }
  attachEqualizer(equalizer: Equalizer) {
    this.equalizer = equalizer;
  }
  setEqualizer(settings: EqualizerSettings) {
    this.equalizer?.apply(settings);
  }
  setRepeat() {
    this.update({
      repeat:
        this.state.repeat === "off"
          ? "all"
          : this.state.repeat === "all"
            ? "one"
            : "off",
    });
  }
  setShuffle(value: boolean) {
    const current = this.state.queue[this.state.index]?.uid;
    const others = this.state.queue
      .map((e) => e.uid)
      .filter((uid) => uid !== current);
    for (let i = others.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [others[i], others[j]] = [others[j], others[i]];
    }
    this.order =
      value && current
        ? [current, ...others]
        : this.state.queue.map((e) => e.uid);
    this.update({ shuffle: value });
  }
  next(manual = true) {
    if (this.saved) {
      this.restore();
      return;
    }
    const current = this.order.indexOf(this.state.queue[this.state.index]?.uid),
      next = current + 1;
    if (next < this.order.length)
      this.load(this.state.queue.findIndex((e) => e.uid === this.order[next]));
    else if (this.state.repeat === "all" && this.order.length)
      this.load(this.state.queue.findIndex((e) => e.uid === this.order[0]));
    else if (!manual) this.update({ playing: false, loading: false });
  }
  previous() {
    if (this.saved) {
      this.restore();
      return;
    }
    if (this.audio.currentTime > 3) {
      this.seek(0);
      return;
    }
    const current = this.order.indexOf(this.state.queue[this.state.index]?.uid);
    if (current > 0)
      this.load(
        this.state.queue.findIndex((e) => e.uid === this.order[current - 1]),
      );
    else this.seek(0);
  }
  playIndex(index: number) {
    if (this.saved) this.restore();
    this.load(index);
  }
  enqueue(track: Track, next: boolean) {
    if (this.saved) this.restore();
    if (!this.state.queue.length) {
      this.playQueue([track]);
      return;
    }
    const queue = [...this.state.queue];
    queue.splice(next ? this.state.index + 1 : queue.length, 0, {
      uid: crypto.randomUUID(),
      track,
    });
    this.update({ queue });
    this.setShuffle(false);
  }
  move(from: number, to: number) {
    if (
      this.saved ||
      from < 0 ||
      to < 0 ||
      from >= this.state.queue.length ||
      to >= this.state.queue.length
    )
      return;
    const current = this.state.queue[this.state.index].uid,
      queue = [...this.state.queue];
    queue.splice(to, 0, queue.splice(from, 1)[0]);
    this.update({ queue, index: queue.findIndex((e) => e.uid === current) });
    if (!this.state.shuffle) this.order = queue.map((e) => e.uid);
  }
  remove(index: number) {
    if (this.saved || index < 0 || index >= this.state.queue.length) return;
    const current = this.state.queue[this.state.index]?.uid,
      removed = this.state.queue[index].uid;
    const queue = this.state.queue.filter((_, i) => i !== index);
    this.order = this.order.filter((uid) => uid !== removed);
    this.update({
      queue,
      index: Math.max(
        0,
        queue.findIndex((e) => e.uid === current),
      ),
    });
    if (!queue.length) {
      this.generation++;
      this.audio.pause();
      this.audio.src = "";
      this.audio.load();
      this.update({
        index: -1,
        duration: 0,
        position: 0,
        loading: false,
        playing: false,
        origin: undefined,
      });
    } else if (removed === current)
      this.load(Math.min(index, queue.length - 1));
  }
  dispose() {
    this.generation++;
    this.audio.pause();
    for (const [event, action] of this.events)
      this.audio.removeEventListener(event, action);
    this.listeners.clear();
  }
}
