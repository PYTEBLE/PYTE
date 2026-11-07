export async function api<T>(
  path: string,
  body: unknown,
  signal?: AbortSignal,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api/${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new Error(
      "Der lokale PYTE-Dienst ist nicht erreichbar. Bitte den Starter prüfen.",
    );
  }
  const data = await response.json().catch(() => ({
    error: "Der lokale PYTE-Dienst antwortet nicht. Bitte den Starter prüfen.",
  }));
  if (!response.ok)
    throw new Error(data.error || "Die Anfrage ist fehlgeschlagen.");
  return data as T;
}
