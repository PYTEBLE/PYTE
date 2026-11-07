import test from "node:test";
import assert from "node:assert/strict";
import {
  addTrack,
  refreshPlaylist,
  seedsFor,
  validateLibrary,
  emptyLibrary,
  formatTime,
} from "../src/domain.ts";
import { WebPlayer } from "../src/player.ts";

const first = {
  id: "YGr2G2fc48U",
  title: "First",
  artist: "Artist",
  durationMs: 120000,
};
const last = { ...first, id: "dQw4w9WgXcQ", title: "Last" };
const extra = { ...first, id: "aaaaaaaaaaa", title: "Extra" };
const playlist = () => ({
  id: "local:one",
  title: "My name",
  sourceId: "PL1234567890",
  tracks: [first],
  additions: [],
  createdAt: 1,
});
class Audio extends EventTarget {
  src = "";
  currentTime = 0;
  duration = 120;
  volume = 1;
  paused = true;
  load() {
    this.currentTime = 0;
    this.dispatchEvent(new Event("loadedmetadata"));
  }
  play() {
    this.paused = false;
    this.dispatchEvent(new Event("playing"));
    return Promise.resolve();
  }
  pause() {
    if (!this.paused) {
      this.paused = true;
      this.dispatchEvent(new Event("pause"));
    }
  }
}
test("refresh preserves local additions through appearance and disappearance upstream", () => {
  const added = addTrack(playlist(), extra);
  assert.equal(addTrack(added, extra), added);
  const refreshed = refreshPlaylist(refreshPlaylist(added, [first, extra]), [
    first,
  ]);
  assert.deepEqual(refreshed.tracks, [first, extra]);
  assert.equal(refreshed.title, "My name");
});
test("backups are validated and unknown object keys never enter application state", () => {
  const library = { ...emptyLibrary(), playlists: [playlist()] };
  assert.deepEqual(validateLibrary(library), library);
  assert.throws(() =>
    validateLibrary({ ...library, playlists: [playlist(), playlist()] }),
  );
  assert.throws(() =>
    validateLibrary({ ...library, settings: { volume: -1 } }),
  );
  assert.throws(() =>
    validateLibrary({
      ...library,
      history: [{ ...first, artwork: "http://localhost/private" }],
    }),
  );
  const sanitized = validateLibrary(
    JSON.parse(JSON.stringify({ ...library, unwanted: true })),
  );
  assert.equal(sanitized.unwanted, undefined);
});
test("seed sampling spans a playlist and time formatting handles long audio", () => {
  const tracks = Array.from({ length: 13 }, (_, i) => ({
    ...first,
    id: String(i).padStart(11, "0"),
  }));
  assert.deepEqual(seedsFor([...tracks, ...tracks]), [
    tracks[0],
    tracks[4],
    tracks[8],
    tracks[12],
  ]);
  assert.equal(formatTime(3723), "1:02:03");
  assert.equal(formatTime(NaN), "0:00");
});
test("preview restores queue identities, position, paused intent and modes", () => {
  const audio = new Audio(),
    player = new WebPlayer(audio);
  player.playQueue([first, last], 1, "local:one");
  player.setShuffle(true);
  player.setRepeat();
  player.toggle();
  audio.currentTime = 42;
  const before = player.getSnapshot();
  player.preview(first);
  player.preview(extra);
  player.restore();
  assert.deepEqual(player.getSnapshot().queue, before.queue);
  assert.deepEqual(player.getSnapshot().origin, before.origin);
  assert.equal(player.getSnapshot().index, 1);
  assert.equal(audio.currentTime, 42);
  assert.equal(audio.paused, true);
  assert.equal(player.getSnapshot().shuffle, true);
  assert.equal(player.getSnapshot().repeat, "all");
  player.dispose();
});
test("preview ending restores a playing source; a new queue discards old snapshots", () => {
  const audio = new Audio(),
    player = new WebPlayer(audio);
  player.playQueue([first, last]);
  audio.currentTime = 15;
  player.preview(extra);
  audio.dispatchEvent(new Event("ended"));
  assert.equal(player.getSnapshot().queue.length, 2);
  assert.equal(audio.currentTime, 15);
  assert.equal(audio.paused, false);
  player.preview(extra);
  player.playQueue([last]);
  player.restore();
  assert.equal(player.getSnapshot().queue[0].track.id, last.id);
  player.dispose();
});
test("automatic discovery is once per run and never reappears after a blocked foreground", () => {
  for (const mode of ["background", "shuffle", "repeat", "disabled"]) {
    const audio = new Audio();
    let foreground = mode !== "background";
    const player = new WebPlayer(audio, () => foreground);
    if (mode === "repeat") player.setRepeat();
    if (mode === "disabled") player.automatic = false;
    player.playQueue([first, last], 1, "local:one", mode === "shuffle");
    assert.equal(player.getSnapshot().suggestion, undefined);
    foreground = true;
    player.automatic = true;
    player.setShuffle(false);
    audio.dispatchEvent(new Event("playing"));
    assert.equal(player.getSnapshot().suggestion, undefined);
    player.dispose();
  }
  const audio = new Audio(),
    player = new WebPlayer(audio);
  player.playQueue([first, last], 1, "local:one");
  assert.equal(player.getSnapshot().suggestion.playlistId, "local:one");
  player.consumeSuggestion();
  audio.dispatchEvent(new Event("playing"));
  assert.equal(player.getSnapshot().suggestion, undefined);
  player.dispose();
});
test("queue moves preserve current identity, duplicate entries remain distinct", () => {
  const audio = new Audio(),
    player = new WebPlayer(audio);
  player.playQueue([first, first, last]);
  audio.currentTime = 17;
  const id = player.getSnapshot().queue[0].uid;
  assert.notEqual(id, player.getSnapshot().queue[1].uid);
  player.move(0, 2);
  assert.equal(player.getSnapshot().queue[player.getSnapshot().index].uid, id);
  assert.equal(audio.currentTime, 17);
  player.remove(0);
  assert.equal(player.getSnapshot().queue.length, 2);
  player.remove(-1);
  assert.equal(player.getSnapshot().queue.length, 2);
  player.dispose();
});
test("empty-origin previews restore silence and repeat-one never advances automatically", () => {
  const audio = new Audio(),
    player = new WebPlayer(audio);
  player.preview(first);
  player.restore();
  assert.equal(player.getSnapshot().queue.length, 0);
  assert.equal(audio.paused, true);
  player.playQueue([first, last]);
  player.setRepeat();
  player.setRepeat();
  audio.dispatchEvent(new Event("ended"));
  assert.equal(player.getSnapshot().index, 0);
  assert.equal(audio.currentTime, 0);
  player.dispose();
});
