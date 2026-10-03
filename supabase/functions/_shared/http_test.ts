import { readJsonObject } from "./http.ts";
import { HungiiError } from "./errors.ts";

const assert = (value: unknown) => {
  if (!value) throw new Error("Assertion failed");
};
Deno.test("JSON boundary rejects oversized chunked UTF-8 and cancels the source", async () => {
  let cancelled = false;
  const body = new ReadableStream<Uint8Array>({
    pull(controller) {
      controller.enqueue(new TextEncoder().encode("é".repeat(30)));
    },
    cancel() {
      cancelled = true;
    },
  });
  const request = new Request("http://localhost/api", { method: "POST", body });
  let error: unknown;
  try {
    await readJsonObject(request, 50);
  } catch (e) {
    error = e;
  }
  assert(error instanceof HungiiError && error.status === 413);
  assert(cancelled);
});
Deno.test("JSON boundary accepts split UTF-8 and rejects malformed objects and encoding", async () => {
  const bytes = new TextEncoder().encode('{"message":"é"}');
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const byte of bytes) controller.enqueue(new Uint8Array([byte]));
      controller.close();
    },
  });
  assert(
    (await readJsonObject(
      new Request("http://localhost", { method: "POST", body }),
      100,
    )).message === "é",
  );
  for (const body of ["null", "[]", "{", new Uint8Array([0xff])]) {
    let error: unknown;
    try {
      await readJsonObject(
        new Request("http://localhost", { method: "POST", body }),
        100,
      );
    } catch (e) {
      error = e;
    }
    assert(error instanceof HungiiError && error.status === 400);
  }
});
