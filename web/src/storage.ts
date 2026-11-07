import { emptyLibrary, validateLibrary } from "./domain.ts";
import type { Library } from "./domain.ts";

let database: Promise<IDBDatabase> | undefined;
let revision = 0;
function open() {
  return (database ??= new Promise((resolve, reject) => {
    const request = indexedDB.open("pyte-desktop", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("library");
    request.onsuccess = () => {
      request.result.onversionchange = () => {
        request.result.close();
        database = undefined;
      };
      resolve(request.result);
    };
    request.onerror = () => {
      database = undefined;
      reject(
        new Error(
          "Der Browser erlaubt keinen Zugriff auf die lokale Bibliothek.",
        ),
      );
    };
    request.onblocked = () =>
      reject(new Error("Bitte andere PYTE-Tabs schließen und erneut laden."));
  }));
}
export async function loadLibrary(): Promise<Library> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const request = db
      .transaction("library")
      .objectStore("library")
      .get("state");
    request.onsuccess = () => {
      try {
        const stored = request.result;
        revision = stored?.revision ?? 0;
        resolve(
          stored === undefined
            ? emptyLibrary()
            : validateLibrary(stored.data ?? stored),
        );
      } catch (error) {
        reject(error);
      }
    };
    request.onerror = () =>
      reject(
        new Error("Die gespeicherte Bibliothek konnte nicht gelesen werden."),
      );
  });
}
let writes: Promise<unknown> = Promise.resolve();
export function saveLibrary(library: Library): Promise<void> {
  const snapshot = structuredClone(library);
  const operation = writes
    .catch(() => {})
    .then(async () => {
      const db = await open();
      return new Promise<void>((resolve, reject) => {
        const transaction = db.transaction("library", "readwrite");
        const store = transaction.objectStore("library");
        const read = store.get("state");
        let conflict = false;
        read.onsuccess = () => {
          if ((read.result?.revision ?? 0) !== revision) {
            conflict = true;
            transaction.abort();
          } else {
            try {
              store.put({ revision: revision + 1, data: snapshot }, "state");
            } catch {
              transaction.abort();
            }
          }
        };
        transaction.oncomplete = () => {
          revision++;
          resolve();
        };
        transaction.onerror = () =>
          reject(
            new Error(
              "Speichern fehlgeschlagen. Änderungen sind nur in diesem Tab verfügbar. Bitte eine Sicherung exportieren.",
            ),
          );
        transaction.onabort = () =>
          reject(
            new Error(
              conflict
                ? "Die Bibliothek wurde in einem anderen Tab verändert. Ungespeicherte Änderungen exportieren und diese Seite neu laden."
                : "Der Browser hat das Speichern abgebrochen. Bitte eine Sicherung exportieren.",
            ),
          );
      });
    });
  writes = operation;
  return operation;
}
