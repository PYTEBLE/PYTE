import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { createServer } from "../server/index.mjs";
import {
  playlistId,
  searchPage,
  radioTracks,
  rankRadios,
} from "../server/parsers.mjs";
import { allowedAudioUrl, boundedJson, Youtube } from "../server/youtube.mjs";

const track = {
  id: "YGr2G2fc48U",
  title: "Song",
  artist: "Artist",
  durationMs: 121000,
};
test("playlist input accepts IDs/official links, not arbitrary URLs", () => {
  assert.equal(
    playlistId("https://music.youtube.com/playlist?list=PL1234567890"),
    "PL1234567890",
  );
  assert.throws(() =>
    playlistId(
      "https://music.youtube.com.evil.example/playlist?list=PL1234567890",
    ),
  );
  assert.throws(() => playlistId("file:///etc/passwd"));
  assert.throws(() => playlistId("short"));
});
test("media URL allowlist rejects IPs, misleading suffixes, userinfo and redirects to local services", () => {
  assert.equal(
    allowedAudioUrl("https://rr1.googlevideo.com/videoplayback?sig=secret")
      .hostname,
    "rr1.googlevideo.com",
  );
  for (const url of [
    "http://rr1.googlevideo.com/x",
    "https://googlevideo.com.evil/x",
    "https://127.0.0.1/x",
    "https://user:pass@rr1.googlevideo.com/x",
    "https://rr1.googlevideo.com:8080/x",
  ])
    assert.throws(() => allowedAudioUrl(url));
});
test("parsers skip invalid tracks, parse radio and combine duplicate recommendations", () => {
  assert.equal(searchPage({}).tracks.length, 0);
  const radio = radioTracks({
    playlistPanelVideoRenderer: {
      videoId: track.id,
      title: { runs: [{ text: "Song" }] },
      longBylineText: { simpleText: "Artist • Album" },
      lengthText: { simpleText: "2:01" },
    },
  });
  assert.equal(radio[0].durationMs, 121000);
  assert.equal(radio[0].artist, "Artist");
  assert.equal(
    rankRadios(
      [
        { seed: "one", tracks: radio },
        { seed: "two", tracks: radio },
      ],
      new Set(),
    ).length,
    1,
  );
  assert.equal(
    rankRadios([{ seed: "one", tracks: radio }], new Set([track.id])).length,
    0,
  );
});
test("bounded JSON rejects oversized and failed responses", async () => {
  await assert.rejects(boundedJson(new Response("123456789"), 3));
  await assert.rejects(
    boundedJson(new Response("secret", { status: 503 })),
    /HTTP 503/,
  );
});
test("local server guards host/origin, validates inputs and forwards only constrained media ranges", async () => {
  let requests = 0;
  const youtube = {
    search: async () => ({ tracks: [track], continuation: null }),
    resolve: async () => ({
      url: "https://rr1.googlevideo.com/audio",
      ua: "expected-agent",
    }),
  };
  const server = createServer({
    youtube,
    fetcher: async (url, options) => {
      requests++;
      assert.equal(url.hostname, "rr1.googlevideo.com");
      assert.equal(options.headers.Range, "bytes=0-2");
      return new Response(new Uint8Array([1, 2, 3]), {
        status: 206,
        headers: {
          "content-type": "audio/mp4",
          "content-range": "bytes 0-2/100",
          "content-length": "3",
        },
      });
    },
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(base + "/api/health")).status, 200);
    assert.equal(
      (
        await fetch(base + "/api/health", {
          headers: { Origin: "https://evil.example" },
        })
      ).status,
      403,
    );
    const hostileHost = await new Promise((resolve) => {
      http.get(
        base + "/api/health",
        { headers: { Host: "evil.example" } },
        (response) => {
          response.resume();
          resolve(response.statusCode);
        },
      );
    });
    assert.equal(hostileHost, 403);
    assert.equal(
      (
        await fetch(base + "/api/health", {
          headers: { "Sec-Fetch-Site": "cross-site" },
        })
      ).status,
      403,
    );
    assert.equal(
      (
        await fetch(base + "/api/search", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: "{}",
        })
      ).status,
      400,
    );
    const search = await fetch(base + "/api/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query: "Song" }),
    });
    assert.deepEqual((await search.json()).tracks, [track]);
    assert.equal(
      (
        await fetch(base + "/api/audio/YGr2G2fc48U", {
          headers: { Range: "bytes=0-2,4-5" },
        })
      ).status,
      416,
    );
    assert.equal(requests, 0);
    const audio = await fetch(base + "/api/audio/YGr2G2fc48U", {
      headers: { Range: "bytes=0-2" },
    });
    assert.equal(audio.status, 206);
    assert.equal(audio.headers.get("content-range"), "bytes 0-2/100");
    assert.deepEqual([...new Uint8Array(await audio.arrayBuffer())], [1, 2, 3]);
    assert.equal(requests, 1);
    assert.equal((await fetch(base + "/server/youtube.mjs")).status, 404);
  } finally {
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});
test("client-bound errors do not contain signed URLs or upstream exception details", async () => {
  const server = createServer({
    youtube: {
      resolve: async () => {
        throw new Error(
          "private signed URL https://rr1.googlevideo.com/?sig=SECRET",
        );
      },
    },
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const result = await fetch(
      `http://127.0.0.1:${server.address().port}/api/audio/YGr2G2fc48U`,
    );
    assert.equal(result.status, 502);
    assert.ok(!(await result.text()).includes("SECRET"));
  } finally {
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});
test(
  "optional live search, radio and actual media bytes",
  { skip: process.env.PYTE_WEB_LIVE !== "true" },
  async () => {
    const youtube = new Youtube();
    const result = await youtube.search("schönhauser Gustav");
    assert.ok(result.tracks.some((t) => t.id === track.id));
    const radio = await youtube.recommendations([track.id], []);
    assert.ok(radio.songs.length);
    assert.ok(radio.songs.every((s) => s.track.id !== track.id));
    const stream = await youtube.resolve(track.id);
    assert.equal(allowedAudioUrl(stream.url).protocol, "https:");
  },
);
