const encoder = new TextEncoder();

export function base64(bytes: Uint8Array): string {
  // Chunked so large ciphertexts never exceed the engine's argument limit.
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary);
}

export function unbase64(value: string): Uint8Array<ArrayBuffer> {
  const decoded = atob(value);
  const result = new Uint8Array(decoded.length);
  for (let i = 0; i < decoded.length; i++) result[i] = decoded.charCodeAt(i);
  return result;
}

export function randomSecret(): string {
  return base64(crypto.getRandomValues(new Uint8Array(32))).replaceAll("+", "-")
    .replaceAll("/", "_").replaceAll("=", "");
}

export async function digest(value: string): Promise<string> {
  return base64(
    new Uint8Array(
      await crypto.subtle.digest("SHA-256", encoder.encode(value)),
    ),
  ).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

/**
 * Application encryption keys. Ciphertext written under [LEGACY_KEY_ID] keeps the
 * original "iv.ciphertext" form; any other current key writes "id.iv.ciphertext",
 * so rotation needs no data migration and a rollback can still read old rows.
 */
export type Keyring = {
  current: { id: string; key: string };
  previous: { id: string; key: string }[];
};
/** A bare base64 key is shorthand for a keyring holding only the original key. */
export type Secret = string | Keyring;

export const LEGACY_KEY_ID = "k1";
const KEY_ID = /^[a-z0-9]{1,16}$/;

/**
 * Reads HUNGII_TOKEN_KEY (current), HUNGII_TOKEN_KEY_ID (default k1) and
 * HUNGII_TOKEN_KEYS_PREVIOUS ("id:base64key,id:base64key") for rotation.
 */
export function keyringFromEnv(
  get: (name: string) => string | undefined,
): Keyring | null {
  const key = get("HUNGII_TOKEN_KEY");
  if (!key) return null;
  const id = get("HUNGII_TOKEN_KEY_ID") || LEGACY_KEY_ID;
  const previous = (get("HUNGII_TOKEN_KEYS_PREVIOUS") ?? "").split(",").map((
    entry,
  ) => entry.trim()).filter(Boolean).map((entry) => {
    const split = entry.indexOf(":");
    if (split < 1) throw new Error("Invalid previous key entry");
    return { id: entry.slice(0, split), key: entry.slice(split + 1) };
  });
  const ring = { current: { id, key }, previous };
  const ids = [id, ...previous.map((p) => p.id)];
  if (
    ids.some((value) => !KEY_ID.test(value)) || new Set(ids).size !== ids.length
  ) {
    throw new Error("Invalid key identifiers");
  }
  return ring;
}

function ring(secret: Secret): Keyring {
  return typeof secret === "string"
    ? { current: { id: LEGACY_KEY_ID, key: secret }, previous: [] }
    : secret;
}

function keyFor(secret: Secret, id: string): string {
  const keys = ring(secret);
  const found = [keys.current, ...keys.previous].find((k) => k.id === id);
  if (!found) throw new Error("Unknown encryption key");
  return found.key;
}

async function importKey(secret: string): Promise<CryptoKey> {
  const bytes = unbase64(secret);
  if (bytes.length !== 32) throw new Error("Invalid token encryption key");
  return await crypto.subtle.importKey("raw", bytes, "AES-GCM", false, [
    "encrypt",
    "decrypt",
  ]);
}

/** AES-256-GCM, bound to [owner] as additional data so ciphertext cannot move between rows. */
export async function seal(
  value: string,
  owner: string,
  secret: Secret,
): Promise<string> {
  const { id, key } = ring(secret).current;
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv, additionalData: encoder.encode(owner) },
    await importKey(key),
    encoder.encode(value),
  );
  const sealed = `${base64(iv)}.${base64(new Uint8Array(ciphertext))}`;
  return id === LEGACY_KEY_ID ? sealed : `${id}.${sealed}`;
}

export async function unseal(
  value: string,
  owner: string,
  secret: Secret,
): Promise<string> {
  const parts = value.split(".");
  if (parts.length !== 2 && parts.length !== 3) {
    throw new Error("Invalid ciphertext");
  }
  const [id, iv, ciphertext] = parts.length === 3
    ? parts
    : [LEGACY_KEY_ID, ...parts];
  const plain = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: unbase64(iv),
      additionalData: encoder.encode(owner),
    },
    await importKey(keyFor(secret, id)),
    unbase64(ciphertext),
  );
  return new TextDecoder().decode(plain);
}

/** The key that sealed [value], so rotation tooling can find rows still on an old key. */
export function sealedKeyId(value: string): string {
  return value.split(".").length === 3 ? value.split(".")[0] : LEGACY_KEY_ID;
}
