import { HungiiError, type Json } from "./errors.ts";

/** Bound actual UTF-8 bytes, including chunked bodies without Content-Length. */
export async function readJsonObject(
  request: Request,
  limit: number,
): Promise<Json> {
  const tooLarge = () =>
    new HungiiError("HUNGII_BAD_INPUT", "This request is too large.", 413);
  if (Number(request.headers.get("Content-Length") ?? 0) > limit) {
    throw tooLarge();
  }
  const reader = request.body?.getReader();
  if (!reader) throw new HungiiError("HUNGII_BAD_INPUT", "Invalid request.");
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > limit) throw tooLarge();
      chunks.push(value);
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  let body: unknown;
  try {
    body = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
  } catch {
    throw new HungiiError("HUNGII_BAD_INPUT", "Invalid request.");
  }
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    throw new HungiiError("HUNGII_BAD_INPUT", "Invalid request.");
  }
  return body as Json;
}
