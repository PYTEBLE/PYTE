import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const first = {
  id: "YGr2G2fc48U",
  title: "schönhauser",
  artist: "Zartmann",
  durationMs: 120000,
};
const last = {
  id: "dQw4w9WgXcQ",
  title: "Second song",
  artist: "Another artist",
  durationMs: 120000,
};
const extra = {
  id: "aaaaaaaaaaa",
  title: "Neue Richtung",
  artist: "Neue Stimme",
  durationMs: 120000,
};
function wav() {
  const samples = 44100 * 12,
    buffer = Buffer.alloc(44 + samples * 2);
  buffer.write("RIFF");
  buffer.writeUInt32LE(buffer.length - 8, 4);
  buffer.write("WAVEfmt ", 8);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(44100, 24);
  buffer.writeUInt32LE(88200, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write("data", 36);
  buffer.writeUInt32LE(samples * 2, 40);
  for (let i = 0; i < samples; i++)
    buffer.writeInt16LE(
      Math.round(Math.sin((i / 44100) * Math.PI * 2 * 440) * 1000),
      44 + i * 2,
    );
  return buffer;
}
async function fixtures(page) {
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.startsWith("/api/audio/"))
      return route.fulfill({
        status: 200,
        contentType: "audio/wav",
        body: wav(),
        headers: { "Accept-Ranges": "bytes" },
      });
    const value = path.endsWith("playlist")
      ? {
          sourceId: "PL1234567890",
          title: "Nachtfahrten",
          tracks: [first, last],
        }
      : path.endsWith("recommendations")
        ? { songs: [{ track: extra, seed: first.id }], partial: false }
        : path.endsWith("lyrics")
          ? {
              lines: [
                { timeMs: 0, text: "Ein neuer Tag" },
                { timeMs: 4000, text: "Ein neuer Klang" },
                { timeMs: 8000, text: "" },
              ],
              plainLyrics: "Ein neuer Tag\nEin neuer Klang",
              instrumental: false,
            }
          : { tracks: [first, last], continuation: null };
    return route.fulfill({ json: value });
  });
}
async function importPlaylist(page) {
  await page
    .getByRole("button", { name: "Playlist importieren", exact: true })
    .click();
  await page
    .getByLabel("Playlist-Link")
    .fill("https://music.youtube.com/playlist?list=PL1234567890");
  await page
    .getByRole("button", { name: "Lokal importieren", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Nachtfahrten öffnen" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Nachtfahrten öffnen" }).click();
}

test("desktop library, preview restore, local additions, persistence, lyrics and queue controls", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await importPlaylist(page);
  await page
    .locator(".playlist-actions")
    .getByRole("button", { name: "Abspielen", exact: true })
    .click();
  await expect(page.locator("audio")).toHaveJSProperty("paused", false);
  await page
    .getByRole("button", { name: "Ähnliche Songs", exact: true })
    .click();
  await page.getByRole("button", { name: "Anhören", exact: true }).click();
  await expect(page.locator(".preview-strip")).toContainText("Neue Richtung");
  await expect(page.locator(".now-playing")).toContainText("Neue Richtung");
  await page.getByRole("button", { name: "Hinzufügen", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Hinzugefügt", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Zurück zur Wiedergabe" }).click();
  await expect(page.locator(".now-playing")).toContainText("schönhauser");
  await expect(page.locator(".queue-list > li")).toHaveCount(2);
  await page.getByRole("button", { name: "Schließen", exact: true }).click();
  await page
    .getByRole("button", { name: "Second song nach oben", exact: true })
    .click();
  await expect(page.locator(".queue-list > li").first()).toContainText(
    "Second song",
  );
  await expect(page.locator(".now-playing")).toContainText("schönhauser");
  await page
    .getByRole("button", {
      name: "Second song aus Queue entfernen",
      exact: true,
    })
    .click();
  await expect(page.locator(".queue-list > li")).toHaveCount(1);
  await expect(page.locator("main")).toContainText("3 Songs");
  await page
    .getByRole("button", { name: "Lyrics anzeigen", exact: true })
    .click();
  await expect(page.locator(".lyric-line.active")).toContainText(
    "Ein neuer Tag",
  );
  await expect(
    page.locator(".lyric-line", { hasText: "Ein neuer Klang" }),
  ).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Nachtfahrten öffnen" }).click();
  await expect(page.locator("main")).toContainText("3 Songs");
  await page
    .getByRole("button", {
      name: "Als Favorit speichern: Neue Richtung",
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Favoriten", exact: true }).click();
  await expect(page.locator("main")).toContainText("Neue Richtung");
});

test("search, local playlist creation, rename, confirmation and reduced-motion persistence", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Entdecken", exact: true }).click();
  await page.getByLabel("Songs oder Künstler suchen").fill("Artist");
  await page.getByRole("button", { name: "Suchen", exact: true }).click();
  await page
    .getByRole("button", { name: "Zur Playlist: schönhauser", exact: true })
    .click();
  await page
    .getByLabel("Oder eine neue Playlist erstellen")
    .fill("Meine Auswahl");
  await page.getByRole("button", { name: "Erstellen & hinzufügen" }).click();
  await page
    .getByRole("button", { name: "Meine Auswahl", exact: true })
    .click();
  await page.getByRole("button", { name: "Playlist umbenennen" }).click();
  await page.getByLabel("Playlist-Name").fill("Mein neuer Name");
  await page.getByRole("button", { name: "Speichern", exact: true }).click();
  await page
    .getByRole("button", { name: "Playlist löschen", exact: true })
    .click();
  await page.getByRole("button", { name: "Abbrechen", exact: true }).click();
  await expect(page.locator("main h1")).toHaveText("Mein neuer Name");
  await page
    .getByRole("button", { name: "Einstellungen", exact: true })
    .click();
  await page.getByRole("checkbox", { name: "Animationen reduzieren" }).check();
  await expect
    .poll(() =>
      page.evaluate(async () => {
        const db = await new Promise((resolve) => {
          const r = indexedDB.open("pyte-desktop", 1);
          r.onsuccess = () => resolve(r.result);
        });
        return new Promise((resolve) => {
          const r = db
            .transaction("library")
            .objectStore("library")
            .get("state");
          r.onsuccess = () => {
            db.close();
            resolve(r.result?.data?.settings?.reducedMotion);
          };
        });
      }),
    )
    .toBe(true);
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-motion", "reduced");
  await page
    .getByRole("button", { name: "Einstellungen", exact: true })
    .click();
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Web-Sicherung exportieren" }).click();
  expect((await download).suggestedFilename()).toMatch(/^PYTE-Web-.*\.json$/);
});

test("responsive layouts remain usable and homepage passes automated accessibility checks", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Deine Bibliothek" }),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/pyte-desktop-home.png",
    fullPage: true,
  });
  for (const width of [1440, 900, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await expect(
      page.getByRole("button", { name: "Entdecken", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Abspielen", exact: true }),
    ).toBeVisible();
    const accessibility = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(accessibility.violations).toEqual([]);
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
});

test("failed storage reads never overwrite data and network failures are visible", async ({
  page,
}) => {
  await fixtures(page);
  await page.addInitScript(() => {
    Object.defineProperty(window, "indexedDB", {
      get() {
        throw new Error("Storage blocked");
      },
    });
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Bibliothek nicht erreichbar" }),
  ).toBeVisible();
  await expect(
    page.getByText("Es wurden keine gespeicherten Daten überschrieben.", {
      exact: false,
    }),
  ).toBeVisible();
});

test("multiple browser tabs cannot silently overwrite each other’s library", async ({
  page,
  context,
}) => {
  await fixtures(page);
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Deine Bibliothek" }),
  ).toBeVisible();
  const second = await context.newPage();
  await fixtures(second);
  await second.goto("/");
  await expect(
    second.getByRole("heading", { name: "Deine Bibliothek" }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Einstellungen", exact: true })
    .click();
  await page
    .getByRole("checkbox", { name: "Automatische Empfehlungen" })
    .uncheck();
  // Confirm that the first transaction reached disk before the other tab writes its old snapshot.
  await expect
    .poll(() =>
      page.evaluate(async () => {
        const db = await new Promise((resolve) => {
          const r = indexedDB.open("pyte-desktop", 1);
          r.onsuccess = () => resolve(r.result);
        });
        return new Promise((resolve) => {
          const r = db
            .transaction("library")
            .objectStore("library")
            .get("state");
          r.onsuccess = () => {
            db.close();
            resolve(r.result?.revision ?? 0);
          };
        });
      }),
    )
    .toBeGreaterThan(0);
  await second
    .getByRole("button", { name: "Einstellungen", exact: true })
    .click();
  await second
    .getByRole("checkbox", { name: "Hörverlauf speichern" })
    .uncheck();
  await expect(second.getByRole("alert")).toContainText("anderen Tab");
  await page.reload();
  await page
    .getByRole("button", { name: "Einstellungen", exact: true })
    .click();
  await expect(
    page.getByRole("checkbox", { name: "Hörverlauf speichern" }),
  ).toBeChecked();
  await second.close();
});

test("automatic recommendations open only on the final playlist song with repeat off", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await page.bringToFront();
  await importPlaylist(page);
  await page
    .getByRole("button", { name: "Second song abspielen", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("dialog")).toContainText("Nachtfahrten");
  await page.getByRole("button", { name: "Schließen", exact: true }).click();
  await page
    .getByRole("button", { name: "Wiederholung: aus", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Second song abspielen", exact: true })
    .click();
  await expect
    .poll(() =>
      page
        .locator("audio")
        .evaluate((audio) => !audio.paused && audio.currentTime > 0.2),
    )
    .toBe(true);
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("quota failures leave the in-memory library usable and offer an export", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Deine Bibliothek" }),
  ).toBeVisible();
  await page.evaluate(() => {
    IDBObjectStore.prototype.put = () => {
      throw new DOMException("Full", "QuotaExceededError");
    };
  });
  await page
    .getByRole("button", { name: "Neue Playlist", exact: true })
    .click();
  await page.getByLabel("Playlist-Name").fill("Noch nicht gespeichert");
  await page
    .getByRole("button", { name: "Playlist erstellen", exact: true })
    .click();
  await expect(page.locator("main h1")).toHaveText("Noch nicht gespeichert");
  await expect(page.getByRole("alert")).toContainText("Speichern abgebrochen");
  await expect(
    page.getByRole("button", { name: "Sicherung exportieren", exact: true }),
  ).toBeVisible();
});

test("real YouTube audio plays in Chromium @live", async ({ page }) => {
  test.skip(process.env.PYTE_WEB_LIVE !== "true", "Opt-in real network test");
  test.setTimeout(150000);
  await page.goto("/");
  await page.getByRole("button", { name: "Entdecken", exact: true }).click();
  await page
    .getByLabel("Songs oder Künstler suchen")
    .fill("schönhauser Gustav");
  await page.getByRole("button", { name: "Suchen", exact: true }).click();
  const row = page
    .locator(".track-row")
    .filter({ hasText: "schönhauser" })
    .first();
  await row.locator(".track-main").click();
  await expect
    .poll(
      () =>
        page
          .locator("audio")
          .evaluate((audio) => !audio.paused && audio.currentTime > 0.5),
      { timeout: 100000 },
    )
    .toBe(true);
  await page
    .getByRole("button", { name: "Ähnliche Songs zum aktuellen Titel" })
    .click();
  await expect(page.locator(".recommendation-card").first()).toBeVisible({
    timeout: 30000,
  });
  await expect
    .poll(
      () =>
        page
          .locator(".recommendation-card img")
          .evaluateAll(
            (images) =>
              images.filter((image) => image.complete && image.naturalWidth > 0)
                .length,
          ),
      { timeout: 20000 },
    )
    .toBeGreaterThan(0);
  await page.screenshot({
    path: "test-results/pyte-desktop-live.png",
    fullPage: true,
  });
});

test("synchronized lyrics highlight the current line and tap-to-seek moves playback", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Entdecken", exact: true }).click();
  await page.getByLabel("Songs oder Künstler suchen").fill("Artist");
  await page.getByRole("button", { name: "Suchen", exact: true }).click();
  await page.locator(".track-main").first().click();
  await page
    .getByRole("button", { name: "Lyrics anzeigen", exact: true })
    .click();
  await expect(page.locator(".lyric-line.active")).toContainText(
    "Ein neuer Tag",
  );
  await page.locator(".lyric-line", { hasText: "Ein neuer Klang" }).click();
  await expect
    .poll(() => page.locator("audio").evaluate((audio) => audio.currentTime))
    .toBeGreaterThanOrEqual(3.9);
  await expect(page.locator(".lyric-line.active")).toContainText(
    "Ein neuer Klang",
  );
});

test("immersive player shows cover, controls and lyrics, closes with Escape and keeps keyboard playback", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Entdecken", exact: true }).click();
  await page.getByLabel("Songs oder Künstler suchen").fill("Artist");
  await page.getByRole("button", { name: "Suchen", exact: true }).click();
  await page.locator(".track-main").first().click();
  await page
    .getByRole("button", { name: "Vollbild-Player öffnen", exact: true })
    .click();
  const immersive = page.getByRole("dialog", { name: "Vollbild-Player" });
  await expect(immersive).toBeVisible();
  await expect(immersive.locator("h2")).toHaveText("schönhauser");
  await expect(immersive.locator(".lyric-line.active")).toContainText(
    "Ein neuer Tag",
  );
  await page.keyboard.press("Space");
  await expect
    .poll(() => page.locator("audio").evaluate((audio) => audio.paused))
    .toBe(true);
  await page.keyboard.press("Escape");
  await expect(immersive).toHaveCount(0);
});

test("immersive player and lyrics pass automated accessibility checks", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Entdecken", exact: true }).click();
  await page.getByLabel("Songs oder Künstler suchen").fill("Artist");
  await page.getByRole("button", { name: "Suchen", exact: true }).click();
  await page.locator(".track-main").first().click();
  await page
    .getByRole("button", { name: "Vollbild-Player öffnen", exact: true })
    .click();
  await expect(page.locator(".lyric-line.active")).toContainText(
    "Ein neuer Tag",
  );
  const accessibility = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .include(".immersive")
    .analyze();
  expect(accessibility.violations).toEqual([]);
});

test("equalizer: presets persist, keyboard bands work, custom profiles save, and playback keeps running with the graph", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Entdecken", exact: true }).click();
  await page.getByLabel("Songs oder Künstler suchen").fill("Artist");
  await page.getByRole("button", { name: "Suchen", exact: true }).click();
  await page.locator(".track-main").first().click();
  await expect
    .poll(() =>
      page
        .locator("audio")
        .evaluate((audio) => !audio.paused && audio.currentTime > 0.2),
    )
    .toBe(true);

  await page
    .getByRole("button", { name: "Equalizer öffnen", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Equalizer" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("slider")).toHaveCount(15);
  await dialog.getByRole("checkbox", { name: /Equalizer aktiv/ }).check();
  await dialog.getByRole("radio", { name: "Bass", exact: true }).click();
  await expect(
    dialog.getByRole("radio", { name: "Bass", exact: true }),
  ).toHaveAttribute("aria-checked", "true");

  // The graph is live: audio keeps advancing with the equalizer enabled and boosted.
  const before = await page
    .locator("audio")
    .evaluate((audio) => audio.currentTime);
  await page.waitForTimeout(600);
  expect(
    await page.locator("audio").evaluate((audio) => audio.currentTime),
  ).toBeGreaterThan(before);

  const band = dialog.getByRole("slider", { name: "Frequenzband 25 Hz" });
  await band.focus();
  await page.keyboard.press("ArrowDown");
  await expect(
    dialog.getByRole("radio", { name: "Bass", exact: true }),
  ).toHaveAttribute("aria-checked", "false");

  await dialog.getByPlaceholder("Profilname").fill("Mein Kopfhörer");
  await dialog
    .getByRole("button", { name: "Profil speichern", exact: true })
    .click();
  await expect(
    dialog.getByRole("radio", { name: "Mein Kopfhörer" }),
  ).toHaveAttribute("aria-checked", "true");
  await dialog.getByRole("button", { name: "Schließen", exact: true }).click();

  await page.reload();
  await page
    .getByRole("button", { name: "Equalizer öffnen", exact: true })
    .click();
  await expect(
    page.getByRole("checkbox", { name: /Equalizer aktiv/ }),
  ).toBeChecked();
  await expect(
    page.getByRole("radio", { name: "Mein Kopfhörer" }),
  ).toHaveAttribute("aria-checked", "true");
  await expect(
    page.getByRole("slider", { name: "Frequenzband 25 Hz" }),
  ).toHaveAttribute("aria-valuetext", /\+/);
});
