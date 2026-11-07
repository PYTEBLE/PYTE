import { readFile, readdir, mkdir, writeFile } from "node:fs/promises";
import { resolve, relative } from "node:path";
import { zipSync, strToU8 } from "fflate";

const root = resolve(import.meta.dirname, "..");
const pkg = JSON.parse(await readFile(resolve(root, "package.json"), "utf8"));
const gradle = await readFile(resolve(root, "../app/build.gradle.kts"), "utf8");
const version = gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
if (version !== pkg.version)
  throw new Error(
    "Android- und Web-Version müssen für ein gemeinsames Release übereinstimmen.",
  );
const prefix = `PYTE-v${version}-web`;
const files = {};
async function collect(folder) {
  for (const entry of await readdir(folder, { withFileTypes: true })) {
    const path = resolve(folder, entry.name);
    if (entry.isDirectory()) await collect(path);
    else if (entry.isFile())
      files[`${prefix}/${relative(root, path).replaceAll("\\", "/")}`] =
        new Uint8Array(await readFile(path));
  }
}
await collect(resolve(root, "dist"));
await collect(resolve(root, "server"));
for (const name of [
  "start.mjs",
  "Start-PYTE.sh",
  "Start-PYTE.bat",
  "README.md",
]) {
  const content = new Uint8Array(await readFile(resolve(root, name)));
  files[`${prefix}/${name}`] = name.endsWith(".sh")
    ? [content, { os: 3, attrs: 0o100755 << 16 }]
    : content;
}
files[`${prefix}/package.json`] = strToU8(
  JSON.stringify(
    {
      name: "pyte-web",
      version,
      private: true,
      type: "module",
      engines: { node: ">=22" },
      scripts: { start: "node start.mjs" },
    },
    null,
    2,
  ),
);
const output = resolve(root, "release");
await mkdir(output, { recursive: true });
const target = resolve(output, `${prefix}.zip`);
await writeFile(target, zipSync(files, { level: 6 }));
console.log(
  `Web-Release: ${target}\nEnthält nur statische App, lokalen Server und Starter. Node.js 22+ muss installiert sein.`,
);
