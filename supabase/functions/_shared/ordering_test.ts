import {
  type AttemptStore,
  FoodOrdering,
  type OrderingConfig,
  paymentMethods,
  safeBridge,
} from "./ordering.ts";
import { base64 } from "./secrets.ts";
import { HungiiError, type Json } from "./errors.ts";
function assert(value: unknown, message = "Assertion failed") {
  if (!value) throw new Error(message);
}
const cfg: OrderingConfig = {
  enabled: true,
  unit: "rupees",
  itemIdField: "verified_id",
  quantityField: "verified_quantity",
};
class Memory implements AttemptStore {
  rows = new Map<string, Json>();
  async latest() {
    return [...this.rows.values()].at(-1) ?? null;
  }
  async get(id: string) {
    return this.rows.get(id) ?? null;
  }
  async begin(id: string, data: Json) {
    if (
      this.rows.has(id) ||
      [...this.rows.values()].some((a) =>
        ["placing", "pending", "unresolved"].includes(a.phase)
      )
    ) return false;
    this.rows.set(id, structuredClone(data));
    return true;
  }
  async save(id: string, data: Json) {
    this.rows.set(id, structuredClone(data));
  }
}
const key = base64(crypto.getRandomValues(new Uint8Array(32)));
function fixture() {
  const store = new Memory();
  let price = 230,
    placements = 0,
    confirms = 0,
    failure = false,
    terminal = false,
    refund = false,
    confirmed = false;
  let now = 1000000;
  const options = {
    allMethods: [{ id: "upi-x", displayName: "UPI", kind: "intent" }, {
      id: "cash",
      displayName: "Cash",
    }],
    platforms: { mobile: { methods: [{ id: "upi-x" }] } },
    cod: { id: "cash", available: true },
  };
  const calls: string[] = [];
  const call = async (name: string, args: Json) => {
    calls.push(name);
    switch (name) {
      case "get_food_cart":
        return {
          data: {
            cart_id: "basket",
            restaurant: { id: "r", name: "Restaurant" },
            items: [{
              menu_item_id: "dish",
              name: "Rice",
              quantity: 1,
              total: 200,
            }],
            pricing: {
              item_total: 200,
              delivery_charge: 20,
              taxes_and_charges: 10,
              to_pay: price,
            },
          },
        };
      case "get_addresses":
        return {
          addresses: [{ id: "addr", addressLine: "Test address, Hyderabad" }],
          pagination: { totalPages: 1 },
        };
      case "get_payment_options":
        return options;
      case "place_food_order":
        placements++;
        if (failure) throw new Error("timeout");
        return args.paymentMethod === "Cash"
          ? {
            orderId: "order",
            status: "CONFIRMED",
            normalizedStatus: "success",
          }
          : {
            orderId: "order",
            paasId: "paas",
            cartId: "basket",
            addressId: "addr",
            lat: 17.4,
            lng: 78.4,
            status: "PENDING_PAYMENT",
            bridgeUrl: "https://mcp.swiggy.com/pay/token",
            pollingIntervalInMs: 5000,
          };
      case "check_payment_status":
        assert(args.lat === 17.4 && args.lng === 78.4);
        return {
          orderId: "order",
          status: refund
            ? "refund-initiated"
            : terminal
            ? "success"
            : "pending",
          terminal: terminal || refund,
          isTerminalSuccess: terminal && !refund,
          isTerminalFailure: refund,
          confirmed,
        };
      case "confirm_order":
        assert(!("paasId" in args));
        confirms++;
        return { orderId: "order", result: "success" };
      case "search_menu":
        return {
          items: [{ menu_item_id: "dish", restaurant_id: "r", inStock: 1 }],
        };
      case "update_food_cart":
        assert(args.cartItems[0].verified_id === "dish");
        return {};
      default:
        throw new Error(name);
    }
  };
  const ordering = () =>
    new FoodOrdering(call, store, cfg, "owner", "addr", key, () => now);
  const quote = async () => {
    const result = await ordering().run("payment_options", {});
    return {
      confirm: true,
      requestId: crypto.randomUUID(),
      quote: result.cart.quote,
      payable: result.cart.payable,
      methodId: "upi-x",
    };
  };
  return {
    store,
    calls,
    ordering,
    quote,
    setPrice: (p: number) => price = p,
    setFailure: () => failure = true,
    setTerminal: () => terminal = true,
    setConfirmed: () => confirmed = true,
    setRefund: () => refund = true,
    tick: () => now += 6000,
    placements: () => placements,
    confirms: () => confirms,
  };
}
Deno.test("ordering remains gated before any provider call", async () => {
  let calls = 0;
  const o = new FoodOrdering(
    async () => {
      calls++;
      return {};
    },
    new Memory(),
    { ...cfg, enabled: false },
    "o",
    "a",
    key,
  );
  try {
    await o.run("checkout", {});
    throw new Error("enabled");
  } catch (e) {
    assert(e instanceof HungiiError && e.code === "HUNGII_ORDERING_PENDING");
  }
  assert(calls === 0);
});
Deno.test("changed price blocks placement; a forged or cross-user quote is rejected", async () => {
  const f = fixture(), q = await f.quote();
  f.setPrice(231);
  try {
    await f.ordering().run("checkout", q);
    throw new Error("placed");
  } catch (e) {
    assert(e instanceof HungiiError && e.code === "HUNGII_CART_CHANGED");
  }
  assert(f.placements() === 0);
  const other = new FoodOrdering(
    async () => ({}),
    new Memory(),
    cfg,
    "other",
    "addr",
    key,
  );
  let rejected = false;
  try {
    await other.run("checkout", q);
  } catch {
    rejected = true;
  }
  assert(rejected);
});
Deno.test("unknown placement survives a new coordinator and forbids another checkout", async () => {
  const f = fixture(), q = await f.quote();
  f.setFailure();
  let result = await f.ordering().run("checkout", q);
  assert(result.payment.status === "UNRESOLVED");
  result = await f.ordering().run("checkout", q);
  assert(f.placements() === 1 && result.payment.status === "UNRESOLVED");
  const q2 = { ...q, requestId: crypto.randomUUID() };
  try {
    await f.ordering().run("checkout", q2);
    throw new Error("replayed");
  } catch (e) {
    assert(e instanceof HungiiError && e.code === "HUNGII_CHECKOUT_UNRESOLVED");
  }
  assert(f.placements() === 1);
});
Deno.test("pending UPI is not success; only terminal payment triggers confirmation", async () => {
  const f = fixture(), q = await f.quote();
  let result = await f.ordering().run("checkout", q);
  assert(result.payment.status === "PENDING_PAYMENT");
  result = await f.ordering().run("payment_status", q);
  assert(!result.paymentStatus.confirmed && f.confirms() === 0);
  f.tick();
  f.setTerminal();
  result = await f.ordering().run("payment_status", q);
  assert(result.paymentStatus.confirmed && f.confirms() === 1);
  await f.ordering().run("payment_status", q);
  assert(f.confirms() === 1);
});
Deno.test("auto-confirmed payments skip confirm_order; refunds never unlock another charge", async () => {
  const f = fixture(), q = await f.quote();
  await f.ordering().run("checkout", q);
  f.setTerminal();
  f.setConfirmed();
  await f.ordering().run("payment_status", q);
  assert(f.confirms() === 0);
  const g = fixture(), q2 = await g.quote();
  await g.ordering().run("checkout", q2);
  g.setRefund();
  const result = await g.ordering().run("payment_status", q2);
  assert(
    result.payment.status === "UNRESOLVED" &&
      !result.paymentStatus.isTerminalFailure && g.confirms() === 0,
  );
});
Deno.test("COD is recorded before placement and repeated requests never order twice", async () => {
  const f = fixture(), q = { ...await f.quote(), methodId: "cash" };
  const result = await f.ordering().run("checkout", q);
  assert(result.paymentStatus.confirmed);
  await f.ordering().run("checkout", q);
  assert(f.placements() === 1);
});
Deno.test("payment choices use returned surface membership and reject untrusted links", () => {
  assert(
    paymentMethods({ allMethods: [{ id: "invented", kind: "intent" }] })
      .length === 0,
  );
  assert(safeBridge("https://swiggy.com.evil.test/pay") === null);
  assert(safeBridge("javascript:alert(1)") === null);
  assert(safeBridge("https://mcp.swiggy.com/pay") !== null);
});
