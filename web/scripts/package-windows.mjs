import { createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
import { cp, mkdir, readFile, readdir, rename, rm, writeFile, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { promisify } from 'node:util';
import { unzipSync, zipSync } from 'fflate';

const run = promisify(execFile);
const root = resolve(import.meta.dirname, '..');
const pkg = JSON.parse(await readFile(join(root, 'package.json'), 'utf8'));
const ELECTRON = '45.2.3';
const version = pkg.version;
const prefix = `PYTE-v${version}-windows`;
const cache = join(root, '.cache');
const staging = join(root, 'release', 'windows-staging');
const target = join(root, 'release', `${prefix}.zip`);
const base = `https://github.com/electron/electron/releases/download/v${ELECTRON}/`;
const archive = `electron-v${ELECTRON}-win32-x64.zip`;

async function download(url) {
  const response = await fetch(url, { redirect: 'follow' });
  if (!response.ok) throw new Error(`Download fehlgeschlagen: ${url} (${response.status})`);
  return new Uint8Array(await response.arrayBuffer());
}

await mkdir(cache, { recursive: true });
const zipPath = join(cache, archive);
if (!existsSync(zipPath)) {
  const sums = new TextDecoder().decode(await download(`${base}SHASUMS256.txt`));
  const expected = sums.split('\n').map(line => line.trim().split(/\s+/)).find(([, name]) => name?.replace(/^\*/, '') === archive)?.[0];
  if (!expected) throw new Error('Electron-Prüfsumme nicht gefunden.');
  const bytes = await download(base + archive);
  const actual = createHash('sha256').update(bytes).digest('hex');
  if (actual !== expected) throw new Error('Electron-Paket stimmt nicht mit der Prüfsumme überein. Abgebrochen.');
  await writeFile(zipPath, bytes);
}
const electron = unzipSync(new Uint8Array(await readFile(zipPath)));

await rm(staging, { recursive: true, force: true });
const app = join(staging, 'PYTE');
for (const [name, bytes] of Object.entries(electron)) {
  if (name.endsWith('/') || name.startsWith('resources/default_app')) continue;
  const path = join(app, name);
  await mkdir(join(path, '..'), { recursive: true });
  await writeFile(path, bytes);
}
await rename(join(app, 'electron.exe'), join(app, 'PYTE.exe'));

const appDir = join(app, 'resources', 'app');
await mkdir(join(appDir, 'server'), { recursive: true });
await cp(join(root, 'desktop', 'main.cjs'), join(appDir, 'main.cjs'));
await cp(join(root, 'desktop', 'preload.cjs'), join(appDir, 'preload.cjs'));
await cp(join(root, 'server'), join(appDir, 'server'), { recursive: true });
await cp(join(root, 'dist'), join(appDir, 'dist'), { recursive: true });
await writeFile(join(appDir, 'package.json'), JSON.stringify({ name: 'pyte', productName: 'PYTE', version, main: 'main.cjs', private: true }, null, 2));
await writeFile(join(app, 'LIESMICH.txt'), [
  'PYTE für Windows',
  '',
  '1. Ordner vollständig an einen Ort deiner Wahl entpacken.',
  '2. PYTE.exe starten. Die Oberfläche öffnet sich in einem eigenen Fenster.',
  '3. Bibliothek und Einstellungen liegen im Browserspeicher dieser App (Ordner-unabhängig für Port 47380).',
  '',
  'Diese Version ist nicht digital signiert. Windows SmartScreen kann deshalb eine Warnung zeigen:',
  '„Weitere Informationen“ → „Trotzdem ausführen“.',
  '',
  'Nur für persönlichen Gebrauch. YouTube-Nutzungsbedingungen beachten.',
  '',
].join('\r\n'));

const files = {};
async function collect(folder, into) {
  for (const entry of await readdir(folder, { withFileTypes: true })) {
    const path = join(folder, entry.name);
    if (entry.isDirectory()) await collect(path, into);
    else if (entry.isFile()) into[`${prefix}/${relative(staging, path).replaceAll('\\', '/')}`] = new Uint8Array(await readFile(path));
  }
}
await collect(app, files);
await mkdir(join(root, 'release'), { recursive: true });
await writeFile(target, zipSync(files, { level: 6 }));
const info = await stat(target);
console.log(`Windows-Paket: ${target} (${(info.size / 1024 / 1024).toFixed(1)} MB)`);
console.log(`SHA-256: ${createHash('sha256').update(await readFile(target)).digest('hex')}`);
