import test from "node:test";
import assert from "node:assert/strict";
import {
  BANDS_HZ,
  Equalizer,
  MAX_DB,
  MAX_PROFILES,
  MIN_DB,
  PRESETS,
  clampGain,
  defaultEqualizer,
  preampDb,
  validateEqualizer,
} from "../src/equalizer.ts";
import { validateLibrary, emptyLibrary } from "../src/domain.ts";

test("every preset has one gain per band inside the supported range", () => {
  assert.equal(BANDS_HZ.length, 15);
  assert.ok(PRESETS.length >= 14, "more presets than the Android app's five");
  for (const preset of PRESETS) {
    assert.equal(preset.gains.length, BANDS_HZ.length, preset.id);
    for (const gain of preset.gains) assert.ok(gain >= MIN_DB && gain <= MAX_DB, `${preset.id}: ${gain}`);
  }
  assert.equal(new Set(PRESETS.map((preset) => preset.id)).size, PRESETS.length);
});

test("gains are clamped and snapped to half decibels", () => {
  assert.equal(clampGain(3.2), 3);
  assert.equal(clampGain(3.3), 3.5);
  assert.equal(clampGain(40), MAX_DB);
  assert.equal(clampGain(-40), MIN_DB);
});

test("pre-gain leaves headroom for the largest boost, never amplifies a flat curve", () => {
  assert.equal(preampDb(PRESETS[0].gains), -1.5);
  assert.ok(Math.abs(preampDb([6, ...Array(14).fill(0)]) - -7.5) < 1e-9);
});

test("settings validation accepts good data and rejects tampered or oversized profiles", () => {
  assert.deepEqual(validateEqualizer(undefined), defaultEqualizer());
  const good = { ...defaultEqualizer(), enabled: true, preset: "bass", gains: PRESETS[1].gains };
  assert.deepEqual(validateEqualizer(good).gains, PRESETS[1].gains);
  assert.throws(() => validateEqualizer({ ...good, gains: [1, 2, 3] }));
  assert.throws(() => validateEqualizer({ ...good, gains: [NaN, ...Array(14).fill(0)] }));
  assert.throws(() => validateEqualizer({ ...good, enabled: "yes" }));
  const profile = { id: "p", name: "Mein Kopfhörer", gains: Array(15).fill(1) };
  assert.equal(validateEqualizer({ ...good, profiles: [profile] }).profiles[0].name, "Mein Kopfhörer");
  const many = Array.from({ length: MAX_PROFILES + 1 }, (_, index) => ({ ...profile, id: `p${index}` }));
  assert.throws(() => validateEqualizer({ ...good, profiles: many }));
  assert.throws(() => validateEqualizer({ ...good, profiles: [{ ...profile, name: "   " }] }));
});

test("older libraries without an equalizer load with defaults", () => {
  const library = emptyLibrary();
  const legacy = { ...library, settings: { ...library.settings } };
  delete legacy.settings.equalizer;
  assert.deepEqual(validateLibrary(legacy).settings.equalizer, defaultEqualizer());
});

function fakeContext() {
  const log = [];
  const param = (name) => ({ name, value: 0 });
  const node = (kind, extra = {}) => ({
    kind,
    connect(target) { log.push([kind, target.kind]); return target; },
    gain: param("gain"),
    frequency: param("frequency"),
    Q: param("Q"),
    threshold: param("threshold"),
    knee: param("knee"),
    ratio: param("ratio"),
    attack: param("attack"),
    release: param("release"),
    ...extra,
  });
  const destination = node("destination");
  const context = {
    state: "running",
    destination,
    resumed: 0,
    resume() { this.resumed++; },
    createMediaElementSource() { return node("source"); },
    createGain() { return node("gain"); },
    createBiquadFilter() { return node("biquad", { type: "" }); },
    createDynamicsCompressor() { return node("limiter"); },
    createAnalyser() { return node("analyser", { fftSize: 0, frequencyBinCount: 128, getByteFrequencyData() {} }); },
  };
  return { context, log };
}

test("the graph is built once, wired source → pre-gain → 15 bands → limiter → volume → analyser → output", () => {
  const { context, log } = fakeContext();
  const element = { volume: 0.5 };
  const equalizer = new Equalizer(element, () => context);
  equalizer.ensure();
  equalizer.ensure();
  assert.equal(log.filter(([from]) => from === "source").length, 1);
  assert.deepEqual(log.at(-1), ["analyser", "destination"]);
  assert.ok(log.some(([from, to]) => from === "limiter" && to === "gain"));
  assert.equal(element.volume, 1, "element is handled by the graph; volume lives in the master gain");
});

test("disabled equalizer is neutral; enabled applies gains, pre-gain and limiter", () => {
  const { context } = fakeContext();
  const equalizer = new Equalizer({ volume: 1 }, () => context);
  equalizer.ensure();
  const boost = { ...defaultEqualizer(), enabled: true, gains: [6, ...Array(14).fill(0)] };
  equalizer.apply(boost);
  const bands = equalizer.bands;
  assert.equal(bands[0].gain.value, 6);
  assert.ok(Math.abs(equalizer.preamp.gain.value - 10 ** (-7.5 / 20)) < 1e-9);
  assert.equal(equalizer.limiter.ratio.value, 20);
  assert.equal(equalizer.limiter.threshold.value, -1);

  equalizer.apply({ ...boost, enabled: false });
  assert.ok(bands.every((band) => band.gain.value === 0));
  assert.equal(equalizer.preamp.gain.value, 1);
  assert.equal(equalizer.limiter.ratio.value, 1, "limiter is neutral when the equalizer is off");

  equalizer.apply({ ...boost, limiter: false });
  assert.equal(equalizer.limiter.ratio.value, 1, "limiter switch works even when enabled");
});

test("suspended audio contexts resume on the next user-started playback", () => {
  const { context } = fakeContext();
  const equalizer = new Equalizer({ volume: 1 }, () => context);
  equalizer.ensure();
  context.state = "suspended";
  equalizer.ensure();
  assert.equal(context.resumed, 1);
});

test("volume is applied once, to the master gain, after the graph exists", () => {
  const { context } = fakeContext();
  const element = { volume: 1 };
  const equalizer = new Equalizer(element, () => context);
  equalizer.setVolume(0.3);
  assert.equal(element.volume, 0.3);
  equalizer.ensure();
  assert.equal(element.volume, 1);
  equalizer.setVolume(2);
  assert.equal(equalizer.master.gain.value, 1);
});
