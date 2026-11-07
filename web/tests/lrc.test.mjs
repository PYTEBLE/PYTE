import test from "node:test";
import assert from "node:assert/strict";
import { activeLine, parseLrc } from "../server/lrc.mjs";

test("LRC parsing handles multi-stamp lines, fractions, offsets and sorting", () => {
  const lines = parseLrc(
    "[offset:500]\n[00:10.5]Second\n[00:01.00][00:20.250]Repeat\n[bad line]\n[01:02.1]Late",
  );
  assert.deepEqual(
    lines.map((line) => [line.timeMs, line.text]),
    [
      [1500, "Repeat"],
      [10500 + 500, "Second"],
      [20250 + 500, "Repeat"],
      [62100 + 500, "Late"],
    ].sort((a, b) => a[0] - b[0]),
  );
});

test("negative offsets never produce negative timestamps", () => {
  assert.equal(parseLrc("[offset:-5000]\n[00:01.00]Start")[0].timeMs, 0);
});

test("active line is found by binary search, before the first line it is -1", () => {
  const lines = [{ timeMs: 1000 }, { timeMs: 5000 }, { timeMs: 9000 }];
  assert.equal(activeLine(lines, 0), -1);
  assert.equal(activeLine(lines, 1000), 0);
  assert.equal(activeLine(lines, 4999), 0);
  assert.equal(activeLine(lines, 9001), 2);
  assert.equal(activeLine([], 100), -1);
});

import { artworkUrl } from "../server/parsers.mjs";

test("artwork proxy accepts only YouTube image hosts over https", () => {
  assert.equal(artworkUrl("https://i.ytimg.com/vi/x/hq720.jpg"), "https://i.ytimg.com/vi/x/hq720.jpg");
  assert.equal(artworkUrl("https://yt3.googleusercontent.com/a=w120"), "https://yt3.googleusercontent.com/a=w120");
  for (const bad of [
    "http://i.ytimg.com/x.jpg",
    "https://ytimg.com.evil.example/x.jpg",
    "https://127.0.0.1/x.jpg",
    "https://user@i.ytimg.com/x.jpg",
    "https://i.ytimg.com:8443/x.jpg",
    "file:///etc/passwd",
  ])
    assert.throws(() => artworkUrl(bad), undefined, bad);
});
