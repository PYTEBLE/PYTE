# Android UI/UX refresh

**Milestone 1 candidate:** an isolated, device-tested design laboratory is documented in
[design/LISTENING-SPACE.md](design/LISTENING-SPACE.md). Its screenshots and UI-test results
do not retroactively qualify the production screens described below.

**Milestone 2 (alpha.13):** the approved tokens now drive the production `PyteTheme`; the
library gained Playlists/Favoriten/Offline filters, sort and local playlist creation. The bottom
bar is Start/Suchen/Bibliothek, and Einstellungen is reached by a labeled gear on each top-level
screen. The player uses the approved hierarchy (artwork, plain metadata, transport, primary
"Zur Playlist", secondary text actions). Production migration passed build, lint and unit tests;
the migrated screens were not re-photographed on a device because the wireless ADB session
ended before installation. The prototype screenshots remain the visual reference.

## Discovery feeds

The empty online Search view now shows recent listening and new song suggestions, with
an explicit “Passt zu …” explanation for each result. Up to four recent artist-diverse
seeds contribute in round-robin order; shared tracks are emitted once and artist/version
limits still apply. Playlist discovery and mixes use the same ranking. Turning off
history prevents personalized suggestion requests; loading/error/retry states never
block the ordinary search field.

Below the user's library collections, “Charts & Playlist-Tipps” presents current
anonymous YouTube Music charts and editorial selections. Tapping an unsaved playlist
opens confirmation, not playback or an immediate write. Confirmation imports metadata
and song links locally without modifying a YouTube account or enabling audio downloads.
Saved cards open the existing collection. The Offline and Favorites filters stay focused
on the user's own content.

Playlist photos use the primary header image, not the owner's avatar. Expired signed
cover URLs can be refreshed independently of songs, with bounded/rate-limited lookups;
offline/unavailable artwork keeps an explicit image fallback instead of a blank card.

## Common song actions

`PyteRoot` provides a `LocalSongActions` entry point. Song rows use a visible playlist-add
button and an overflow button. Long-press opens the same action sheet without playing the
song. The same entry point is wired into recently played cards, the mini-player, full player,
lyrics view, queue and recommendation cards. The shared sheet offers playlist saving, play
next, enqueue, favorite/unfavorite and similar-song discovery. It explains that offline
availability is configured on playlists; it does not pretend to offer individual downloads.

The player always exposes **Zur Playlist**, not only during a mix. Search, favorites and
playlist rows no longer depend on a screen-specific save callback being present. Gestures
are supplementary: visible buttons remain available. The mini-player accepts left/right
swipes for next/previous, but never stops or dismisses playback as a swipe side effect.

## Saving and undo

The main application routes saves through the root `PlayerViewModel` and one playlist picker.
The selected `Track` is captured at selection time, rather than read again from live playback.
Choosing a destination or saving does not seek, play, or modify the playback queue.

The picker searches existing playlist names, prioritizes the last successfully used destination,
and can create a local playlist with the selected song even before any imports. The last
destination is persisted independently of other preferences; a deleted destination is harmless.
Save controls are disabled while a write is in progress, and failures stay in the picker.

A successful new addition exposes **Rückgängig**. Duplicate additions don't receive an undo
token. Undo removes only a locally added link inside a database transaction, preserves order
and counts, and never removes source-playlist songs, favorites, history, or the playlist itself.
New empty playlists remain accessible and explain how to add their first song. An obsolete
async undo result must not replace a newly opened picker. Queue and discovery sheets also
display the result inline, since their separate windows can cover the main snackbar.

## Language and layout

Application labels, controls, help texts and user-facing view-model messages are German.
Common text is resource-backed in `res/values/{actions,screens,settings,messages}.xml`.
Song/artist/playlist names and technical diagnostics remain source content, not translations.
Existing English download status codes stay unchanged in storage and are translated for display.
Singular/plural song and playlist counts use quantity resources.

Four labeled navigation destinations remain: Bibliothek, Suchen, Favoriten, Einstellungen.
There is no unlabelled navigation gesture required to reach a destination. Empty library,
favorites and playlists offer search as the next action. Library search/import shortcuts are
visible above the collections. Playlist play/shuffle/discover actions precede offline options.
Settings keep privacy/about information below interactive settings instead of burying sound
and battery controls beneath explanatory text. Player and queue action rows can wrap; the
battery timer choices scroll horizontally rather than overflow on narrow layouts.

## Motion

- Finite radial touch blooms on song rows, collection cards and primary/action-sheet buttons.
  They run only in response to presses, never as an idle particle loop.
- Coordinated slide/fade navigation and existing interruptible artwork/play-control transitions.
- Clipped, draw-layer cover parallax in playlist headers; it cannot cover the title beneath it.
- A real playback-position arc behind the player cover. It is not a spectrum or beat animation.
- Real loading states use shimmer skeletons. Mini-player swipes have restrained displacement
  feedback and a distance threshold before invoking a playback command.

`LivingUiPolicy` remains authoritative. Lebendig stays the default; Intensiv increases visual
strength. Reduced system motion, system battery saver, app battery-mode effect reduction,
window focus and lifecycle gate decorative effects. Modal sheets use their own window focus
without dropping the inherited power/motion restrictions. No synthetic audio spectrum was added.

## Validation limits

Unit tests cover picker filtering/recency, persisted recency isolation, German resource fallback
on English devices, and safe transactional undo including duplicate/source-song protection,
favorites/history preservation and deleted playlists. Compose tests cover direct saving and
long-press without playback, shared sheet actions, picker creation and searching. Existing UI
tests were updated for German labels and the common recommendation picker.

Build/lint/unit results belong in `TESTING.md`. Instrumentation APK compilation is not a passed
device UI test. No device is connected for this refresh; screenshots, TalkBack, large fonts,
touch gestures, clipping, contrast in motion and battery/CPU behavior still need real-device
qualification before treating this as a visually approved redesign.
