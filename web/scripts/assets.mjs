import { mkdir, copyFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "../..");
const destination = resolve(root, "web/public/brand");
await mkdir(destination, { recursive: true });
for (const [source, target] of [
  ["pyte_logo.png", "logo.png"],
  ["pyte_symbol.png", "symbol.png"],
  ["pyte_wordmark.png", "wordmark.png"],
  ["pyte_background.png", "background.png"],
]) {
  await copyFile(
    resolve(root, "app/src/main/res/drawable-nodpi", source),
    resolve(destination, target),
  );
}
