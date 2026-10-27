import {
  ENCRYPTED_FIELDS,
  resealValue,
  resolveAttempt,
  summarizeAttempt,
} from "./operator.ts";
import { attemptView } from "../supabase/functions/_shared/ordering.ts";
import {
  base64,
  seal,
  sealedKeyId,
  unseal,
} from "../supabase/functions/_shared/secrets.ts";

function assert(value: unknown, message = "Assertion failed") {
  if (!value) throw new Error(message);
}
function rejects(run: () => unknown, message: string) {
  try {
    run();
  } catch {
    return;
  }
  throw new Error(message);
}

const blocked = {
  requestId: "11111111-1111-1111-1111-111111111111",
  phase: "unresolved",
  createdAt: Date.parse("2026-10-27T09:00:00Z"),
  addressId: "secret-address",
  cart: {
    restaurant: "Kitchen",
    payable: 433,
    address: "Flat 4, Private Road",
    items: [{ quantity: 2 }],
  },
  provider: { paasId: "paas-1" },
};

Deno.test("a support-confirmed order becomes trackable and unblocks checkout", () => {
  const resolved = resolveAttempt(blocked, "confirmed", "order-77", 5);
  assert(resolved.phase === "confirmed");
  assert(resolved.provider.orderId === "order-77");
  assert(resolved.provider.paasId === "paas-1");
  assert(resolved.resolution.previousPhase === "unresolved");
  const view = attemptView(resolved);
  assert(
    view.payment.status === "CONFIRMED" && view.payment.orderId === "order-77",
  );
  assert(view.paymentStatus.confirmed === true);
});

Deno.test("a failed resolution lets the user order again", () => {
  const resolved = resolveAttempt(blocked, "failed", undefined, 5);
  assert(attemptView(resolved).payment.status === "FAILED");
  rejects(
    () => resolveAttempt(blocked, "confirmed", undefined, 5),
    "Confirmed without an order ID",
  );
  rejects(
    () =>
      resolveAttempt(
        { ...blocked, phase: "confirmed" },
        "failed",
        undefined,
        5,
      ),
    "Re-resolved a finished attempt",
  );
});

Deno.test("summaries leave out addresses", () => {
  const summary = summarizeAttempt(
    { user_id: "u", request_id: blocked.requestId },
    blocked,
  );
  assert(summary.items === 2 && summary.payable === 433);
  assert(!JSON.stringify(summary).includes("Private Road"));
  assert(!JSON.stringify(summary).includes("secret-address"));
});

Deno.test("resealing moves rows to the current key once", async () => {
  const oldKey = base64(crypto.getRandomValues(new Uint8Array(32)));
  const keys = {
    current: {
      id: "k2",
      key: base64(crypto.getRandomValues(new Uint8Array(32))),
    },
    previous: [{ id: "k1", key: oldKey }],
  };
  const legacy = await seal("token", "u:swiggy", oldKey);
  const next = await resealValue(legacy, "u:swiggy", keys);
  assert(next && sealedKeyId(next) === "k2");
  assert(await unseal(next!, "u:swiggy", keys) === "token");
  assert(await resealValue(next!, "u:swiggy", keys) === null);
});

Deno.test("every encrypted column is bound to the same data the backend uses", () => {
  const row = {
    user_id: "u",
    request_id: "r",
    generation: 4,
    state: { ciphertext: "c", consentVersion: "v" },
  };
  const bindings = Object.fromEntries(
    ENCRYPTED_FIELDS.map((f) => [`${f.table}.${f.match}`, f.aad(row)]),
  );
  const expected = {
    "swiggy_oauth_states.encrypted_verifier": "u:pkce",
    "swiggy_connections.encrypted_token": "u:swiggy",
    "swiggy_connections.encrypted_address_id": "u:address",
    "swiggy_mcp_sessions.encrypted_metadata": "u:mcp:4",
    "hungii_state.state->>ciphertext": "u:tracker",
    "hungii_checkouts.ciphertext": "u:checkout:r",
  };
  assert(
    JSON.stringify(bindings) === JSON.stringify(expected),
    JSON.stringify(bindings),
  );
  const tracker = ENCRYPTED_FIELDS.find((f) => f.table === "hungii_state")!;
  const written = tracker.write(row, "new");
  assert(
    written.state.ciphertext === "new" && written.state.consentVersion === "v",
  );
});
