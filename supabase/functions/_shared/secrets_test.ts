import {
  base64,
  keyringFromEnv,
  LEGACY_KEY_ID,
  seal,
  sealedKeyId,
  unbase64,
  unseal,
} from "./secrets.ts";

function assert(value: unknown, message = "Assertion failed") {
  if (!value) throw new Error(message);
}
async function rejects(promise: Promise<unknown>, message: string) {
  try {
    await promise;
  } catch {
    return;
  }
  throw new Error(message);
}
const newKey = () => base64(crypto.getRandomValues(new Uint8Array(32)));

Deno.test("an unrotated key keeps the original ciphertext format", async () => {
  const key = newKey();
  const sealed = await seal("token", "user:swiggy", key);
  assert(sealed.split(".").length === 2, "Legacy format changed");
  assert(sealedKeyId(sealed) === LEGACY_KEY_ID);
  const ring = keyringFromEnv((name) =>
    ({ HUNGII_TOKEN_KEY: key } as Record<string, string>)[name]
  )!;
  assert(await unseal(sealed, "user:swiggy", ring) === "token");
  assert((await seal("token", "user:swiggy", ring)).split(".").length === 2);
});

Deno.test("rotation writes the new key and still reads the previous one", async () => {
  const oldKey = newKey(), currentKey = newKey();
  const legacy = await seal("tracker", "user:tracker", oldKey);
  const ring = keyringFromEnv((name) =>
    ({
      HUNGII_TOKEN_KEY: currentKey,
      HUNGII_TOKEN_KEY_ID: "k2",
      HUNGII_TOKEN_KEYS_PREVIOUS: `k1:${oldKey}`,
    } as Record<string, string>)[name]
  )!;
  assert(await unseal(legacy, "user:tracker", ring) === "tracker");
  const rotated = await seal("tracker", "user:tracker", ring);
  assert(sealedKeyId(rotated) === "k2" && rotated.startsWith("k2."));
  assert(await unseal(rotated, "user:tracker", ring) === "tracker");
  // Without the previous key, rows it sealed are unreadable rather than misread.
  const withoutPrevious = {
    current: { id: "k2", key: currentKey },
    previous: [],
  };
  await rejects(
    unseal(legacy, "user:tracker", withoutPrevious),
    "Retired key still read",
  );
});

Deno.test("ciphertext stays bound to its row", async () => {
  const key = newKey();
  const sealed = await seal("token", "user-a:swiggy", key);
  await rejects(unseal(sealed, "user-b:swiggy", key), "AAD not enforced");
  await rejects(unseal("not-ciphertext", "user-a:swiggy", key), "Garbage read");
});

Deno.test("key configuration is validated", () => {
  const key = newKey();
  const env = (values: Record<string, string>) => (name: string) =>
    values[name];
  assert(keyringFromEnv(env({})) === null);
  const invalid: Record<string, string>[] = [
    { HUNGII_TOKEN_KEY: key, HUNGII_TOKEN_KEY_ID: "Key 2" },
    {
      HUNGII_TOKEN_KEY: key,
      HUNGII_TOKEN_KEY_ID: "k2",
      HUNGII_TOKEN_KEYS_PREVIOUS: "k2:" + key,
    },
    { HUNGII_TOKEN_KEY: key, HUNGII_TOKEN_KEYS_PREVIOUS: "missing-colon" },
  ];
  for (const bad of invalid) {
    let rejected = false;
    try {
      keyringFromEnv(env(bad));
    } catch {
      rejected = true;
    }
    assert(rejected, `Accepted ${JSON.stringify(Object.keys(bad))}`);
  }
});

Deno.test("large values encode without exceeding argument limits", async () => {
  const bytes = crypto.getRandomValues(new Uint8Array(65_536)).map((b, i) =>
    i % 3 ? b : 7
  );
  const big = new Uint8Array(300_000);
  for (let i = 0; i < big.length; i += bytes.length) {
    big.set(bytes.subarray(0, Math.min(bytes.length, big.length - i)), i);
  }
  assert(unbase64(base64(big)).every((b, i) => b === big[i]));
  const key = newKey();
  const value = "x".repeat(250_000);
  assert(await unseal(await seal(value, "owner", key), "owner", key) === value);
});
