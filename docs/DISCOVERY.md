# Similar songs

## Play a mix

The full player has a pinned **Mix starten** action. It loads up to 30 ranked songs from the
current song's anonymous YouTube Music radio, not just the local library. The current track
continues while loading. Only a successful, non-empty result replaces upcoming queue items;
the current media item, position and play/pause intent remain unchanged. Shuffle and repeat
are turned off for the new mix. No songs are saved or downloaded automatically.

Loading can be cancelled. Duplicate taps are ignored; changing the current queue entry,
starting a preview or playing a new user queue invalidates an in-flight request. Errors and
empty results leave playback untouched. Starting a mix during a preview is intentionally
disabled: finish the preview first. Previews of a running mix restore that mix as usual.

**Vorherige Warteschlange wiederherstellen** restores the first pre-mix queue, its saved
position, play intent, shuffle and repeat settings, even after starting another mix. Explicitly
playing a new queue discards this snapshot. Mix sessions live in memory; they are not restored
after process death. This first version is a finite batch, without automatic radio pagination.

## Entry points

- Playlist: **Find similar songs** opens recommendations for the playlist's local contents.
- Full-screen player: **Similar to this song** opens recommendations for that specific song.
- Automatic: the final original queue entry starts playing, with shuffle and repeat off.
  The app must be resumed, focused, interactive and unlocked. This can be disabled under
  **Settings → Discover → Playlist recommendations** without disabling manual discovery.

Automatic prompts are once per playback run. Reaching the final song while the app is
backgrounded, another dialog is focused, or the feature is disabled consumes the prompt;
returning to the app does not produce a delayed popup. Search/favorites queues have no
playlist origin and do not trigger it. Appending unrelated queue items suppresses the
original end-of-playlist boundary rather than misidentifying those items as playlist songs.

## Recommendations and privacy

`RecommendationsRepository` samples up to four distinct songs, evenly spaced through the
playlist, and requests anonymous YouTube Music song radios. At most two requests are in
flight at once. Shared results rank higher; existing playlist songs, duplicate IDs and exact
artist/title duplicates are filtered. A per-artist cap keeps one artist from dominating.
Each card identifies an example seed song behind the suggestion.

Only seed song IDs are sent, not the playlist name or complete local library. No Google
account is used and no account playlists are modified. Fresh recommendations need network
access; source availability and similarity quality depend on YouTube's unofficial service.
One failed source does not discard successful recommendations from other seeds.

## Listening previews

Previews use the existing media session and audio focus, not a second audio player.
`PreviewPlayback` snapshots the current media items (including stable queue entry IDs and
playlist origin), item index, position, play/pause intent, shuffle flag and repeat mode.
Switching between suggestions does not overwrite that original snapshot.

**Return to playback**, closing the recommendations sheet, or reaching the end of a preview
restores the original queue and position. Starting a new normal queue discards the preview
snapshot so later cleanup cannot overwrite the user's new playback. Snapshots are in-memory;
they are not a persistent playback backup across process death. Restoring shuffle preserves
the flag and ordered queue, not a guarantee of identical randomized future traversal.

## Adding songs

Playlist recommendations add directly to that local playlist. Single-song recommendations
use the existing playlist chooser, which also supports creating a new local playlist.
Successful additions receive a visible checkmark. Existing duplicate protection and local
addition markers remain in use. Selected offline playlists retain their Wi-Fi/storage rules
when newly added tracks are scheduled for download. Adding never changes the current queue.

## UI and motion

The shared sheet uses semantic cover-aware colors, rounded cards, explicit Listen/Add actions,
an always-available close action, inline feedback and a preview return panel. Loading, partial
results, errors and empty results have separate states. Card/feedback animations use
`LocalLivingUi` so reduced animation and battery-saving settings remain respected.
The sheet uses its own dialog's focus/lifecycle for interactive feedback; the obscured
activity's decorative background can stay paused without disabling the sheet's animations.
