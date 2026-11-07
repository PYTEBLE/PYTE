// Port of the Android LrcParser: [mm:ss.xx] stamps, optional [offset:±ms], multi-stamp lines.
const STAMP = /\[(\d{1,3}):([0-5]\d)(?:[.:](\d{1,3}))?]/g;

export function parseLrc(text) {
  if (typeof text !== "string" || !text) return [];
  const offset = Number(/\[offset:([+-]?\d+)]/i.exec(text)?.[1] ?? 0) || 0;
  const lines = [];
  for (const raw of text.split(/\r?\n/)) {
    const stamps = [...raw.matchAll(STAMP)];
    if (!stamps.length) continue;
    const last = stamps.at(-1);
    const words = raw.slice(last.index + last[0].length).trim();
    for (const match of stamps) {
      const fraction = Number((match[3] ?? "").padEnd(3, "0").slice(0, 3));
      const timeMs = Math.max(
        0,
        Number(match[1]) * 60000 + Number(match[2]) * 1000 + fraction + offset,
      );
      lines.push({ timeMs, text: words.slice(0, 500) });
    }
  }
  return lines.sort((a, b) => a.timeMs - b.timeMs).slice(0, 3000);
}

export function activeLine(lines, positionMs) {
  let low = 0,
    high = lines.length - 1,
    found = -1;
  while (low <= high) {
    const middle = (low + high) >> 1;
    if (lines[middle].timeMs <= positionMs) {
      found = middle;
      low = middle + 1;
    } else high = middle - 1;
  }
  return found;
}
