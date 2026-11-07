// 15 bands (Android devices typically expose 5). Gains in dB, −12…+6 in 0.5 dB steps.
export const BANDS_HZ = [
  25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000,
  16000,
];
export const MIN_DB = -12;
export const MAX_DB = 6;
export const STEP_DB = 0.5;
export const MAX_PROFILES = 12;

export interface Profile {
  id: string;
  name: string;
  gains: number[];
}

export interface EqualizerSettings {
  enabled: boolean;
  limiter: boolean;
  preset: string;
  gains: number[];
  profiles: Profile[];
}

const zeros = () => BANDS_HZ.map(() => 0);

export const PRESETS: Profile[] = [
  { id: "neutral", name: "Neutral", gains: zeros() },
  {
    id: "bass",
    name: "Bass",
    gains: [5, 4.5, 4, 3.5, 2.5, 1.5, 0.5, 0, 0, 0, 0, 0, 0, 0, 0],
  },
  {
    id: "tiefbass",
    name: "Tiefbass",
    gains: [6, 5.5, 5, 4.5, 3, 1.5, 0, -1, -1.5, -2, -2, -2, -2, -2, -2],
  },
  {
    id: "stimmen",
    name: "Stimmen",
    gains: [-3, -2.5, -2, -1, 0, 1.5, 3, 3.5, 3, 2, 1, 0.5, 0, -0.5, -1],
  },
  {
    id: "warm",
    name: "Warm",
    gains: [2, 2, 2, 1.5, 1, 0.5, 0, -0.5, -1, -1.5, -2, -2, -2.5, -3, -3],
  },
  {
    id: "brillanz",
    name: "Brillanz",
    gains: [0, 0, 0, 0, 0, 0, 0, 0.5, 1, 1.5, 2, 3, 3.5, 4, 4],
  },
  {
    id: "elektro",
    name: "Elektro",
    gains: [4, 3.5, 2, 0, -1.5, -1, 0, 1, 2, 3, 3, 2.5, 2, 3, 3.5],
  },
  {
    id: "rock",
    name: "Rock",
    gains: [3.5, 3, 2, 0.5, -1, -1.5, 0, 1.5, 2.5, 3, 3, 3, 2.5, 2, 1.5],
  },
  {
    id: "pop",
    name: "Pop",
    gains: [-1, -0.5, 0.5, 1.5, 2.5, 2.5, 2, 1, 0, -0.5, -1, -0.5, 0.5, 1, 1],
  },
  {
    id: "jazz",
    name: "Jazz",
    gains: [1.5, 1.5, 1, 1, -0.5, -1, 0, 0.5, 1, 1.5, 2, 2, 2, 1.5, 1],
  },
  {
    id: "klassik",
    name: "Klassik",
    gains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.5, 1, 1.5, 2, 2],
  },
  {
    id: "hiphop",
    name: "Hip-Hop",
    gains: [5, 4.5, 3.5, 2, 0.5, -1, -1, 0, 0.5, 1, 1.5, 1.5, 1.5, 1.5, 1.5],
  },
  {
    id: "akustik",
    name: "Akustik",
    gains: [1, 1, 1.5, 1.5, 1.5, 1, 0.5, 0.5, 1, 1.5, 2, 2.5, 2.5, 2, 1.5],
  },
  {
    id: "podcast",
    name: "Podcast",
    gains: [-6, -5, -4, -3, -1, 1, 3, 3.5, 3, 2, 0, -1, -2, -3, -4],
  },
  {
    id: "nacht",
    name: "Nachtmodus",
    gains: [3, 2.5, 2, 1.5, 1, 0.5, 0, -1, -2, -3, -4, -4.5, -5, -5, -5],
  },
  {
    id: "lautheit",
    name: "Lautheit",
    gains: [4, 3.5, 3, 2, 1, 0, -0.5, -1, -1, -0.5, 0, 0.5, 1, 1.5, 1.5],
  },
];

export function defaultEqualizer(): EqualizerSettings {
  return {
    enabled: false,
    limiter: true,
    preset: "neutral",
    gains: zeros(),
    profiles: [],
  };
}

export function clampGain(value: number): number {
  const stepped = Math.round(value / STEP_DB) * STEP_DB;
  return Math.min(MAX_DB, Math.max(MIN_DB, stepped));
}

export function normalizeGains(gains: unknown): number[] {
  if (
    !Array.isArray(gains) ||
    gains.length !== BANDS_HZ.length ||
    gains.some((value) => typeof value !== "number" || !Number.isFinite(value))
  )
    throw new Error("Ungültige Equalizer-Werte.");
  return gains.map(clampGain);
}

/** Pre-gain that leaves headroom for the largest boost, so boosted bands do not clip. */
export function preampDb(gains: number[]): number {
  return -(Math.max(0, ...gains) + 1.5);
}

export function validateEqualizer(value: unknown): EqualizerSettings {
  if (value === undefined || value === null) return defaultEqualizer();
  if (typeof value !== "object")
    throw new Error("Ungültige Equalizer-Einstellungen.");
  const settings = value as Partial<EqualizerSettings>;
  if (
    typeof settings.enabled !== "boolean" ||
    typeof settings.limiter !== "boolean" ||
    typeof settings.preset !== "string" ||
    settings.preset.length > 60 ||
    !Array.isArray(settings.profiles) ||
    settings.profiles.length > MAX_PROFILES
  )
    throw new Error("Ungültige Equalizer-Einstellungen.");
  const profiles = settings.profiles.map((profile) => {
    if (
      !profile ||
      typeof profile.id !== "string" ||
      !profile.id ||
      profile.id.length > 60 ||
      typeof profile.name !== "string" ||
      !profile.name.trim() ||
      profile.name.trim().length > 48
    )
      throw new Error("Ungültiges Equalizer-Profil.");
    return {
      id: profile.id,
      name: profile.name.trim(),
      gains: normalizeGains(profile.gains),
    };
  });
  return {
    enabled: settings.enabled,
    limiter: settings.limiter,
    preset: settings.preset,
    gains: normalizeGains(settings.gains),
    profiles,
  };
}

/** Minimal surface of the Web Audio API that the equalizer uses (also what tests fake). */
export interface AudioGraphContext {
  readonly destination: AudioNode;
  readonly state: string;
  resume(): Promise<void> | void;
  createMediaElementSource(element: HTMLMediaElement): AudioNode;
  createGain(): GainNode;
  createBiquadFilter(): BiquadFilterNode;
  createDynamicsCompressor(): DynamicsCompressorNode;
  createAnalyser(): AnalyserNode;
}

/**
 * Chain: source → pre-gain → 15 bands → optional limiter → volume → analyser → output.
 * Built once on the first user-started playback; disabled effects are neutral values, not a rewired graph.
 */
export class Equalizer {
  private context?: AudioGraphContext;
  private preamp?: GainNode;
  private bands: BiquadFilterNode[] = [];
  private limiter?: DynamicsCompressorNode;
  private master?: GainNode;
  private analyser?: AnalyserNode;
  private settings: EqualizerSettings = defaultEqualizer();
  private volume = 1;

  private element: HTMLMediaElement;
  private create: () => AudioGraphContext;

  constructor(
    element: HTMLMediaElement,
    create: () => AudioGraphContext = () => new AudioContext() as unknown as AudioGraphContext,
  ) {
    this.element = element;
    this.create = create;
  }

  get built(): boolean {
    return Boolean(this.context);
  }

  ensure(): void {
    if (this.context) {
      if (this.context.state === "suspended") void this.context.resume();
      return;
    }
    const context = this.create();
    const source = context.createMediaElementSource(this.element);
    const preamp = context.createGain();
    const bands = BANDS_HZ.map((hz, index) => {
      const filter = context.createBiquadFilter();
      filter.type =
        index === 0
          ? "lowshelf"
          : index === BANDS_HZ.length - 1
            ? "highshelf"
            : "peaking";
      filter.frequency.value = hz;
      filter.Q.value = 1.1;
      filter.gain.value = 0;
      return filter;
    });
    const limiter = context.createDynamicsCompressor();
    const master = context.createGain();
    const analyser = context.createAnalyser();
    analyser.fftSize = 256;
    let node: AudioNode = source;
    node.connect(preamp);
    node = preamp;
    for (const band of bands) {
      node.connect(band);
      node = band;
    }
    node.connect(limiter);
    limiter.connect(master);
    master.connect(analyser);
    analyser.connect(context.destination);
    this.context = context;
    this.preamp = preamp;
    this.bands = bands;
    this.limiter = limiter;
    this.master = master;
    this.analyser = analyser;
    this.apply(this.settings);
    this.setVolume(this.volume);
  }

  apply(settings: EqualizerSettings): void {
    this.settings = settings;
    if (!this.context || !this.preamp || !this.limiter) return;
    const active = settings.enabled;
    const limit = active && settings.limiter;
    this.bands.forEach((band, index) => {
      band.gain.value = active ? settings.gains[index] : 0;
    });
    this.preamp.gain.value = active ? 10 ** (preampDb(settings.gains) / 20) : 1;
    this.limiter.threshold.value = limit ? -1 : 0;
    this.limiter.knee.value = 0;
    this.limiter.ratio.value = limit ? 20 : 1;
    this.limiter.attack.value = 0.003;
    this.limiter.release.value = 0.25;
  }

  /** Once the graph owns the audio, the element stays at full level and the master gain sets volume. */
  setVolume(value: number): void {
    this.volume = Math.min(1, Math.max(0, value));
    if (this.master) this.master.gain.value = this.volume;
    this.element.volume = this.context ? 1 : this.volume;
  }

  frequencyData(): Uint8Array | null {
    if (!this.analyser) return null;
    const data = new Uint8Array(this.analyser.frequencyBinCount);
    this.analyser.getByteFrequencyData(data);
    return data;
  }
}
