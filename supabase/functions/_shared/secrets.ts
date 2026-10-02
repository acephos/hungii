const encoder = new TextEncoder();
export function base64(bytes: Uint8Array): string { return btoa(String.fromCharCode(...bytes)); }
export function unbase64(value: string): Uint8Array<ArrayBuffer> {
  const decoded = atob(value);
  const result = new Uint8Array(decoded.length);
  for (let i = 0; i < decoded.length; i++) result[i] = decoded.charCodeAt(i);
  return result;
}
export function randomSecret(): string { return base64(crypto.getRandomValues(new Uint8Array(32))).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", ""); }
export async function digest(value: string): Promise<string> {
  return base64(new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value)))).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}
async function key(secret: string): Promise<CryptoKey> {
  const bytes = unbase64(secret);
  if (bytes.length !== 32) throw new Error("Invalid token encryption key");
  return await crypto.subtle.importKey("raw", bytes, "AES-GCM", false, ["encrypt", "decrypt"]);
}
export async function seal(value: string, owner: string, secret: string): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: encoder.encode(owner) }, await key(secret), encoder.encode(value));
  return `${base64(iv)}.${base64(new Uint8Array(ciphertext))}`;
}
export async function unseal(value: string, owner: string, secret: string): Promise<string> {
  const [iv, ciphertext] = value.split(".");
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: unbase64(iv), additionalData: encoder.encode(owner) }, await key(secret), unbase64(ciphertext));
  return new TextDecoder().decode(plain);
}
