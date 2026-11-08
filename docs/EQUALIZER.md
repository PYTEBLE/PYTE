# Android Sound Studio

The Android player uses a Media3 PCM16 audio processor, rather than the device's native
five-band equalizer. It processes only PYTE's decoded audio and never attaches to session 0,
records the microphone, or modifies another app's sound.

## Controls and migration

- 15 independent peaking filters: 25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500,
  4000, 6300, 10000 and 16000 Hz. Gains range from −12 to +6 dB; editing steps are 0.5 dB.
- The 16 preset curves match `web/src/equalizer.ts`. Up to 12 custom profiles can be saved.
  Existing five-band points and names are retained and interpolated logarithmically.
- Drag a graph point or a vertical fader; accessible range actions and up/down keys allow
  precise adjustments. Cancelling a gesture restores the previous preview. Profile deletion
  still requires confirmation. Preview changes are reverted when the editor is disposed.
- High bands beyond 45% of a low-rate source's sample rate are disabled, not falsely presented
  as effective filters. Their saved values remain in the profile for other sources.

## Audio processing

The shared `EqualizerRenderersFactory` installs `EqualizerAudioProcessor` in the service's
audio sink. Float output is disabled so Media3's PCM16 conversion path includes the custom
processor. The normal silence-skipping and playback-speed processor chain remains present.
The processor stays installed during bypass, so changing presets or enabling EQ does not
require resetting the queue or playback position. This software path is not a hardware
offload/battery-efficiency qualification.

Each channel has independent biquad state, with Q=1.1 as in the web implementation.
Changes crossfade over 10 ms. Format changes rebuild the filter state only at flush. Positive
gains reserve headroom inside the processor; player volume is not reduced a second time.
The optional protection uses stereo-linked instantaneous sample-peak reduction with a
50 ms release and a 0.98 ceiling. PCM conversion always saturates safely. This is **not** a
true-peak/inter-sample limiter or an exact replica of the browser's DynamicsCompressor.

The graph line displays editable **band gains**, not a certified combined frequency response.
Bars are measured from downmixed output PCM with a Hann window and 48 Goertzel measurements,
at most 12 updates/second. Analysis only runs when the editor is focused, playing and ambient
motion is allowed. No synthetic beat/spectrum animation is used. Pause, backgrounding,
reduced motion and battery saver stop collection; the static curve remains editable.

## Verification

Unit tests exercise every band's center gain at 44.1/48 kHz, stereo isolation, byte-exact
bypass, frame counts, limiter bounds, settings transitions, pending format changes, spectral
capture gating, legacy profile parsing, persistence, and exact preset parity with the web.

Instrumentation tests cover the loading-spinner geometry, accessible EQ editing and
profile deletion. `EqualizerPlaybackTest` feeds a local WAV through the same renderer factory
used by the service. `PlayerVisualUiTest` uses an explicitly synthetic artwork/metadata
fixture for screenshots; it does not claim live remote playback. Device results and limits
are recorded in `TESTING.md`; compilation alone is not proof of successful device execution.

Physical-device listening, Bluetooth changes, long playback, CPU/battery impact and
low-end rendering still require qualification before a production release.
