import { handler } from "../hungii-api/handler.ts";

Deno.test("broker welcome stays readable on shared Supabase hosts", async () => {
  const values = {
    SB_REGION: "ap-south-1",
    SUPABASE_URL: "https://example.supabase.co",
    SUPABASE_SERVICE_ROLE_KEY: "test-only",
    HUNGII_TOKEN_KEY: "test-only",
  };
  const previous = Object.fromEntries(
    Object.keys(values).map((key) => [key, Deno.env.get(key)]),
  );
  try {
    for (const [key, value] of Object.entries(values)) Deno.env.set(key, value);
    const shared = await handler(
      new Request(
        "https://example.supabase.co/functions/v1/hungii-api/welcome",
      ),
    );
    const message = await shared.text();
    if (
      shared.status !== 200 ||
      !shared.headers.get("Content-Type")?.startsWith("text/plain") ||
      message.includes("<") || !message.includes("return to the Hungii app")
    ) {
      throw new Error(
        "Shared host exposed markup or lost the return instruction",
      );
    }
    const custom = await handler(
      new Request("https://api.example.com/functions/v1/hungii-api/welcome"),
    );
    if (
      !custom.headers.get("Content-Type")?.startsWith("text/html") ||
      !(await custom.text()).includes('aria-label="Hungii"')
    ) throw new Error("Custom-host page lost accessible branding");
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) Deno.env.delete(key);
      else Deno.env.set(key, value);
    }
  }
});
