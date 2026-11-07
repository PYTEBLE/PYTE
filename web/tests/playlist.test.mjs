import test from "node:test";
import assert from "node:assert/strict";
import { Youtube } from "../server/youtube.mjs";

const row = (id) => ({
  musicResponsiveListItemRenderer: {
    playlistItemData: { videoId: id },
    flexColumns: [
      { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: `Song ${id}` }] } } },
      { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: "Artist" }] } } },
    ],
  },
});
const ids = (start, count) =>
  Array.from({ length: count }, (_, i) => `v${String(start + i).padStart(10, "0")}`);
const next = (token) => ({
  continuationItemRenderer: { continuationEndpoint: { continuationCommand: { token } } },
});

test("playlist import follows the list continuation across pages, not a decoy menu token", async () => {
  const first = {
    header: { musicResponsiveHeaderRenderer: { title: { simpleText: "Big list" } } },
    menu: { continuationCommand: { token: "DECOY-MENU-TOKEN" } },
    contents: [...ids(0, 100).map(row), next("PAGE-2")],
  };
  const pages = {
    "PAGE-2": { contents: [...ids(100, 100).map(row), next("PAGE-3")] },
    "PAGE-3": { contents: ids(200, 16).map(row) },
  };
  const sent = [];
  const youtube = new Youtube();
  youtube.post = async (_endpoint, body) => {
    sent.push(body);
    if (body.browseId) return first;
    if (!pages[body.continuation]) throw new Error("unexpected token");
    return pages[body.continuation];
  };
  const result = await youtube.playlist("PL1234567890");
  assert.equal(result.tracks.length, 216);
  assert.equal(new Set(result.tracks.map((track) => track.id)).size, 216);
  assert.deepEqual(sent.slice(1).map((body) => body.continuation), ["PAGE-2", "PAGE-3"]);
});

test("an empty continuation page is an error, never a silent cut at 100 songs", async () => {
  const youtube = new Youtube();
  youtube.post = async (_endpoint, body) =>
    body.browseId
      ? { contents: [row("YGr2G2fc48U"), next("NEXT")] }
      : { contents: [] };
  await assert.rejects(youtube.playlist("PL1234567890"), /nicht vollständig/);
});
