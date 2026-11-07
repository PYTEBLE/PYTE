export interface CoverPalette {
  accent: string;
  glow: string;
}

const cache = new Map<string, CoverPalette | null>();

function rgb(r: number, g: number, b: number): string {
  return `rgb(${r}, ${g}, ${b})`;
}

/** Quantized dominant saturated color, lifted until it reads on the dark background. */
export function extractPalette(pixels: Uint8ClampedArray): CoverPalette | null {
  const bins = new Map<number, number>();
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i + 3] < 128) continue;
    const r = pixels[i],
      g = pixels[i + 1],
      b = pixels[i + 2];
    const high = Math.max(r, g, b),
      low = Math.min(r, g, b);
    if (high < 24 || low > 235 || high - low < 24) continue;
    const key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
    bins.set(key, (bins.get(key) ?? 0) + 1);
  }
  let best: number | null = null,
    bestScore = 0;
  for (const [key, count] of bins) {
    const parts = [(key >> 8) & 15, (key >> 4) & 15, key & 15];
    const score =
      count * (0.5 + (Math.max(...parts) - Math.min(...parts)) / 15);
    if (score > bestScore) {
      bestScore = score;
      best = key;
    }
  }
  if (best === null) return null;
  let [r, g, b] = [(best >> 8) & 15, (best >> 4) & 15, best & 15].map(
    (value) => (value / 15) * 255,
  );
  // Blend toward white until the accent has enough contrast on the dark surface (≈ 4.8:1 target).
  for (let i = 0; i < 16; i++) {
    const luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
    if (luminance > 0.42) break;
    r += (255 - r) * 0.12;
    g += (255 - g) * 0.12;
    b += (255 - b) * 0.12;
  }
  return {
    accent: rgb(Math.round(r), Math.round(g), Math.round(b)),
    glow: `rgba(${Math.round(r)}, ${Math.round(g)}, ${Math.round(b)}, 0.32)`,
  };
}

export function coverPalette(source: string): Promise<CoverPalette | null> {
  if (cache.has(source)) return Promise.resolve(cache.get(source)!);
  return new Promise((resolve) => {
    const image = new Image();
    image.onload = () => {
      try {
        const canvas = document.createElement("canvas");
        canvas.width = canvas.height = 40;
        const context = canvas.getContext("2d", { willReadFrequently: true });
        if (!context) return resolve(null);
        context.drawImage(image, 0, 0, 40, 40);
        const palette = extractPalette(context.getImageData(0, 0, 40, 40).data);
        cache.set(source, palette);
        resolve(palette);
      } catch {
        resolve(null);
      }
    };
    image.onerror = () => resolve(null);
    image.src = source;
  });
}
