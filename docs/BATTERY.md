# Battery mode and song startup

Settings → Battery → Configure opens a scrollable popup. Changes are drafts until **Apply**;
Cancel leaves settings unchanged. The master switch defaults to off. Individual choices remain
saved when disabled, but none of the battery-saving policies apply with the master off.

## Choices

- **Lower mobile-data quality:** on metered networks, prefer the highest available audio bitrate
  at or below 64 kbps. If none is offered, use the lowest advertised bitrate; this is not a
  guaranteed hard cap. Unmetered playback keeps the existing AAC-first selection. Normal and
  reduced-quality URLs use separate resolver caches; the media byte cache is format-specific.
  A quality/network change is applied at the next media request, not by restarting playback.
- **Buffer in larger bursts:** load until 90 seconds, then wait until under 30 seconds before
  refilling, adjusted for playback speed. The media allocator is capped around 8 MiB, with a
  near-empty-buffer safety exception. Startup and rebuffer thresholds remain Media3 defaults.
  This can waste data when skipping. Fewer network bursts are a strategy, not a proven saving
  on every carrier/modem; hardware profiling is still needed.
- **Fast bypass for disabled EQ:** bulk-copy PCM instead of processing every sample when EQ
  and spectrum collection are disabled. Disabling an active EQ still finishes its short
  crossfade. This does not enable hardware offload or bypass an enabled equalizer.
- **Reduce visual effects:** use the existing reduced-motion policy without overwriting the
  selected effect level. Decorative animation and spectrum capture stop. System battery saver
  and reduced motion remain authoritative regardless of this app setting.
- **Reduce background updates:** don't poll player position with the screen off, and reduce
  the controller's loop frequency. Media-session controls and service playback stay active.
- **Pause offline downloads:** cancel queued/running offline workers and defer rescheduling
  while this method is active. In-flight transfers also check the policy at chunk boundaries.
  Completed downloaded songs stay playable. Turning this choice off resumes scheduling.
- **Disable next-song lookahead:** suppress/cancel speculative URL lookups. May make track
  transitions slower, but avoids those additional requests.
- **Screen-off sleep timer:** optional Off/15/30/60 minutes (default **Off**). Pauses after
  continuous screen-off listening. Screen-on, pause, mode-off and service destruction cancel
  the timer. Resuming starts a fresh interval. It does not acquire an alarm or extra wake lock
  and is not intended as an exact alarm across process death or system suspension.

## Song startup

Normal mode now looks up only the next queued song's validated stream URL while playing.
It does not pre-download audio, change the queue, or start playback. Repeated events reuse
the same lookup; queue changes, pause, service shutdown, or the battery-mode suppression
cancel it. When the next song becomes current, a pending lookup is shared with the foreground
request. URL validation remains enabled: a successful player response is not proof that the
audio URL is accessible. Audio probes use cancellable OkHttp calls.

First-song startup can still take seconds on mobile networks. The data-source resolve remains
on Media3's loader thread, not the main thread. Debug logs record URL resolution time and
buffering-to-ready time, never signed URLs. These are diagnostic timings, not a demonstrated
latency reduction. No audio-payload warming or reduced startup threshold is included.

## Validation and measurement

Unit tests cover persistence/master gating, bitrate fallback and cache isolation, burst
hysteresis/byte limits/default startup behavior, PCM bypass/spectrum preservation and sleep
timer reset/cancellation. Compose tests cover Apply/Cancel and enabling draft choices; they
must be executed on a device before claiming UI qualification.

For a useful battery comparison, repeat a 30–60 minute screen-off session with the same
playlist, mobile signal conditions, volume and Bluetooth setup, first with mode off and then
on. Record absolute battery-level changes (not just the app's percentage of total consumption),
mobile data usage and time to start each song. No on-device savings percentage is claimed.
