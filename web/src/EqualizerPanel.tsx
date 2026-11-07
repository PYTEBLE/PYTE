import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import {
  BANDS_HZ,
  MAX_DB,
  MAX_PROFILES,
  MIN_DB,
  PRESETS,
  STEP_DB,
  defaultEqualizer,
  preampDb,
} from "./equalizer.ts";
import type { Equalizer, EqualizerSettings } from "./equalizer.ts";
import { Modal } from "./components.tsx";

interface Props {
  settings: EqualizerSettings;
  onChange: (next: EqualizerSettings) => void;
  equalizer: Equalizer | null;
  playing: boolean;
  reducedMotion: boolean;
  onClose: () => void;
}

const formatDb = (value: number) =>
  `${value > 0 ? "+" : ""}${value.toFixed(1).replace(".", ",")} dB`;
const formatHz = (hz: number) =>
  hz >= 1000 ? `${hz / 1000} kHz`.replace(".", ",") : `${hz} Hz`;

export function EqualizerPanel({
  settings,
  onChange,
  equalizer,
  playing,
  reducedMotion,
  onClose,
}: Props) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [name, setName] = useState("");
  const gains = settings.gains;

  useEffect(() => {
    const element = canvas.current;
    if (!element || !equalizer) return;
    const context = element.getContext("2d");
    if (!context) return;
    let frame = 0;
    const draw = () => {
      const { width, height } = element;
      context.clearRect(0, 0, width, height);
      const data = equalizer.frequencyData();
      const color =
        getComputedStyle(element).getPropertyValue("--accent").trim() ||
        "#c4a0ff";
      context.fillStyle = color;
      if (data) {
        const bars = 48;
        const step = Math.floor(data.length / bars);
        const barWidth = width / bars;
        for (let i = 0; i < bars; i++) {
          let peak = 0;
          for (let j = 0; j < step; j++)
            peak = Math.max(peak, data[i * step + j] ?? 0);
          const barHeight = (peak / 255) * height;
          context.globalAlpha = 0.14 + (peak / 255) * 0.3;
          context.fillRect(
            i * barWidth + 1,
            height - barHeight,
            barWidth - 2,
            barHeight,
          );
        }
      }
      context.globalAlpha = 1;
      if (playing && !reducedMotion) frame = requestAnimationFrame(draw);
    };
    draw();
    return () => cancelAnimationFrame(frame);
  }, [equalizer, playing, reducedMotion]);

  const choose = (id: string, values: number[]) =>
    onChange({ ...settings, preset: id, gains: [...values] });
  const setGain = (index: number, value: number) =>
    onChange({
      ...settings,
      preset: "custom",
      gains: gains.map((gain, position) => (position === index ? value : gain)),
    });
  const saveProfile = (event: React.FormEvent) => {
    event.preventDefault();
    const title = name.trim();
    if (!title || settings.profiles.length >= MAX_PROFILES) return;
    const id = crypto.randomUUID();
    onChange({
      ...settings,
      preset: id,
      profiles: [
        ...settings.profiles,
        { id, name: title.slice(0, 48), gains: [...gains] },
      ],
    });
    setName("");
  };
  const deleteProfile = (id: string) =>
    onChange({
      ...settings,
      preset: settings.preset === id ? "custom" : settings.preset,
      profiles: settings.profiles.filter((profile) => profile.id !== id),
    });

  return (
    <Modal title="Equalizer" wide onClose={onClose}>
      <div className="eq-toolbar">
        <label className="switch-row compact">
          <span>
            <strong>Equalizer aktiv</strong>
            <small>
              Wirkt nur auf die Musik in PYTE, nicht auf andere Programme.
            </small>
          </span>
          <input
            type="checkbox"
            checked={settings.enabled}
            onChange={(event) =>
              onChange({ ...settings, enabled: event.target.checked })
            }
          />
        </label>
        <label className="switch-row compact">
          <span>
            <strong>Schutzbegrenzer</strong>
            <small>Verhindert Übersteuern bei kräftigen Anhebungen.</small>
          </span>
          <input
            type="checkbox"
            checked={settings.limiter}
            onChange={(event) =>
              onChange({ ...settings, limiter: event.target.checked })
            }
          />
        </label>
      </div>

      <div className="eq-presets" role="radiogroup" aria-label="Klangprofile">
        {PRESETS.map((preset) => (
          <button
            key={preset.id}
            type="button"
            role="radio"
            aria-checked={settings.preset === preset.id}
            className={`eq-chip ${settings.preset === preset.id ? "selected" : ""}`}
            onClick={() => choose(preset.id, preset.gains)}
          >
            {preset.name}
          </button>
        ))}
        {settings.profiles.map((profile) => (
          <span key={profile.id} className="eq-chip-group">
            <button
              type="button"
              role="radio"
              aria-checked={settings.preset === profile.id}
              className={`eq-chip ${settings.preset === profile.id ? "selected" : ""}`}
              onClick={() => choose(profile.id, profile.gains)}
            >
              {profile.name}
            </button>
            <button
              type="button"
              className="eq-chip-remove"
              aria-label={`Profil ${profile.name} löschen`}
              onClick={() => deleteProfile(profile.id)}
            >
              <X />
            </button>
          </span>
        ))}
      </div>

      <div className={`eq-stage ${settings.enabled ? "" : "disabled"}`}>
        <canvas
          ref={canvas}
          className="eq-spectrum"
          width={960}
          height={260}
          aria-hidden="true"
        />
        <div className="eq-bands">
          {BANDS_HZ.map((hz, index) => (
            <div className="eq-band" key={hz}>
              <output>{formatDb(gains[index])}</output>
              <input
                type="range"
                className="eq-slider"
                aria-label={`Frequenzband ${formatHz(hz)}`}
                aria-valuetext={formatDb(gains[index])}
                min={MIN_DB}
                max={MAX_DB}
                step={STEP_DB}
                value={gains[index]}
                onChange={(event) => setGain(index, Number(event.target.value))}
                onDoubleClick={() => setGain(index, 0)}
              />
              <span>{formatHz(hz)}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="eq-footer">
        <span className="muted small">
          Vorverstärkung {formatDb(preampDb(gains))} · Doppelklick setzt ein
          Band zurück
        </span>
        <div className="eq-actions">
          <button
            type="button"
            className="secondary"
            onClick={() =>
              onChange({
                ...defaultEqualizer(),
                enabled: settings.enabled,
                limiter: settings.limiter,
                profiles: settings.profiles,
              })
            }
          >
            Zurücksetzen
          </button>
          <form className="eq-save" onSubmit={saveProfile}>
            <input
              aria-label="Name des Profils"
              placeholder="Profilname"
              maxLength={48}
              value={name}
              onChange={(event) => setName(event.target.value)}
              disabled={settings.profiles.length >= MAX_PROFILES}
            />
            <button
              className="primary"
              disabled={
                !name.trim() || settings.profiles.length >= MAX_PROFILES
              }
            >
              Profil speichern
            </button>
          </form>
        </div>
      </div>
      {settings.profiles.length >= MAX_PROFILES && (
        <p className="small muted">
          Maximal {MAX_PROFILES} eigene Profile. Lösche eins, um ein neues zu
          speichern.
        </p>
      )}
    </Modal>
  );
}
